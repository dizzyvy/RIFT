package com.dizzyvy.rift.music.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import com.dizzyvy.rift.music.library.PlaylistStore
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Artwork(val bitmap: Bitmap, val encodedBytes: ByteArray)

interface ArtworkRepository {
    suspend fun load(uri: Uri): Artwork?
}

class EmbeddedArtworkRepository(
    context: Context,
    private val playlistStore: PlaylistStore? = null,
) : ArtworkRepository {
    private val resolver = context.applicationContext.contentResolver
    private val cache = object : LruCache<String, Artwork>(16 * 1024) {
        override fun sizeOf(key: String, value: Artwork): Int = ((value.bitmap.byteCount.toLong() + value.encodedBytes.size) / 1024L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1)
    }

    override suspend fun load(uri: Uri): Artwork? = withContext(Dispatchers.IO) {
        val cachedThumbnail = playlistStore?.loadCachedArtwork(uri)
        if (cachedThumbnail != null) {
            cache.get(uri.toString())?.takeIf { it.encodedBytes.contentEquals(cachedThumbnail) }
                ?.let { return@withContext it }
            val bitmap = BitmapFactory.decodeByteArray(cachedThumbnail, 0, cachedThumbnail.size)
            if (bitmap != null) {
                return@withContext Artwork(bitmap, cachedThumbnail).also { cache.put(uri.toString(), it) }
            }
            android.util.Log.w(TAG, "Ignoring invalid cached artwork for $uri")
        } else {
            cache.remove(uri.toString())
        }
        val retriever = MediaMetadataRetriever()
        val bytes = try {
            resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                retriever.setDataSource(descriptor.fileDescriptor)
                retriever.embeddedPicture
            } ?: return@withContext null
        } catch (_: Exception) {
            return@withContext null
        } finally {
            runCatching { retriever.release() }
        }
        val artwork = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val sample = calculateSample(bounds.outWidth, bounds.outHeight, 512)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@runCatching null
            val thumbnail = ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) { "Could not encode artwork thumbnail." }
                output.toByteArray()
            }
            Artwork(bitmap, thumbnail)
        }.onFailure { android.util.Log.w(TAG, "Could not extract artwork for $uri", it) }
            .getOrNull() ?: return@withContext null
        playlistStore?.cacheArtwork(uri, artwork.encodedBytes)
        cache.put(uri.toString(), artwork)
        artwork
    }

    private fun calculateSample(width: Int, height: Int, max: Int): Int {
        var sample = 1
        while (width / sample > max || height / sample > max) sample *= 2
        return sample
    }

    private companion object {
        const val TAG = "RIFTArtwork"
    }
}
