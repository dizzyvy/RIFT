package com.dizzyvy.rift.music.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import android.util.Log
import com.dizzyvy.rift.music.library.CachedArtwork
import com.dizzyvy.rift.music.library.PlaylistStore
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext

data class Artwork(val bitmap: Bitmap, val encodedBytes: ByteArray)

interface ArtworkRepository {
    suspend fun load(uri: Uri, maxDimensionPx: Int = 512): Artwork?
}

class EmbeddedArtworkRepository(
    context: Context,
    private val playlistStore: PlaylistStore? = null,
) : ArtworkRepository {
    private val resolver = context.applicationContext.contentResolver
    private val decodeSemaphore = Semaphore(MAX_CONCURRENT_DECODES)
    private val cache = object : LruCache<String, CachedArtworkValue>(MEMORY_CACHE_KB) {
        override fun sizeOf(key: String, value: CachedArtworkValue): Int {
            val artwork = value.artwork ?: return 1
            return ((artwork.bitmap.byteCount.toLong() + artwork.encodedBytes.size) / 1024L)
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1)
        }
    }

    override suspend fun load(uri: Uri, maxDimensionPx: Int): Artwork? = withContext(Dispatchers.IO) {
        val sizePx = sizeBucket(maxDimensionPx)
        val trackKey = trackKey(uri)
        val artworkKey = albumKey(uri) ?: trackKey
        memoryArtwork(artworkKey, sizePx)?.let { return@withContext it }
        val cached = findCachedArtwork(artworkKey, trackKey, sizePx)
        if (cached != null) {
            if (cached.missing) {
                rememberMissing(trackKey)
                return@withContext null
            }
            val artwork = cached.thumbnail?.let { decodeArtwork(it, sizePx) }
            if (artwork != null) {
                cache.put(memoryKey(artworkKey, sizePx), CachedArtworkValue(artwork))
                return@withContext artwork
            }
            Log.w(TAG, "Ignoring invalid cached artwork for $uri")
        }
        if (isKnownMissing(trackKey)) return@withContext null

        decodeSemaphore.acquire()
        try {
            memoryArtwork(artworkKey, sizePx)?.let { return@withContext it }
            if (isKnownMissing(trackKey)) return@withContext null
            extractArtwork(uri, trackKey, artworkKey, sizePx)
        } finally {
            decodeSemaphore.release()
        }
    }

    private suspend fun findCachedArtwork(artworkKey: String, trackKey: String, sizePx: Int): CachedArtwork? {
        val store = playlistStore ?: return null
        store.loadCachedArtwork(artworkKey, sizePx)?.let { return it }
        if (artworkKey != trackKey) {
            store.loadCachedArtwork(trackKey, sizePx)?.let { return it }
        }
        if (sizePx < MAX_ARTWORK_SIZE_PX) {
            store.loadCachedArtwork(trackKey, MAX_ARTWORK_SIZE_PX)?.let { return it }
        }
        return store.loadCachedArtwork(trackKey, MISSING_ARTWORK_SIZE_PX)
    }

    private suspend fun extractArtwork(uri: Uri, trackKey: String, artworkKey: String, sizePx: Int): Artwork? {
        val retriever = MediaMetadataRetriever()
        val embeddedPicture = try {
            resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                retriever.setDataSource(descriptor.fileDescriptor)
                retriever.embeddedPicture
            }
        } catch (exception: Exception) {
            Log.w(TAG, "Could not read embedded artwork for $uri", exception)
            null
        } finally {
            try {
                retriever.release()
            } catch (exception: RuntimeException) {
                Log.w(TAG, "Could not release artwork retriever for $uri", exception)
            }
        }

        val artwork = embeddedPicture?.let { decodeArtwork(it, sizePx) }
        if (artwork == null) {
            playlistStore?.cacheArtwork(trackKey, uri, MISSING_ARTWORK_SIZE_PX, null)
            rememberMissing(trackKey)
            return null
        }
        playlistStore?.cacheArtwork(artworkKey, uri, sizePx, artwork.encodedBytes)
        cache.put(memoryKey(artworkKey, sizePx), CachedArtworkValue(artwork))
        return artwork
    }

    private fun decodeArtwork(bytes: ByteArray, maxDimensionPx: Int): Artwork? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val bitmap = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = calculateSample(bounds.outWidth, bounds.outHeight, maxDimensionPx) },
        ) ?: return null
        val thumbnail = ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                "Could not encode artwork thumbnail."
            }
            output.toByteArray()
        }
        return Artwork(bitmap, thumbnail)
    }

    private fun calculateSample(width: Int, height: Int, maxDimensionPx: Int): Int {
        var sample = 1
        while (width / sample > maxDimensionPx || height / sample > maxDimensionPx) sample *= 2
        return sample
    }

    private suspend fun albumKey(uri: Uri): String? {
        val track = playlistStore?.loadCachedTrack(uri) ?: return null
        return track.albumId.takeIf { it >= 0L }?.let { "album:${track.volumeName}:$it" }
    }

    private fun trackKey(uri: Uri): String = "track:$uri"

    private fun memoryArtwork(key: String, sizePx: Int): Artwork? {
        val value = cache.get(memoryKey(key, sizePx)) ?: return null
        return value.artwork
    }

    private fun isKnownMissing(trackKey: String): Boolean =
        cache.get(memoryKey(trackKey, MISSING_ARTWORK_SIZE_PX)) != null

    private fun rememberMissing(trackKey: String) {
        cache.put(memoryKey(trackKey, MISSING_ARTWORK_SIZE_PX), CachedArtworkValue(null))
    }

    private fun memoryKey(key: String, sizePx: Int): String = "$key@$sizePx"

    private data class CachedArtworkValue(val artwork: Artwork?)

    private fun sizeBucket(requestedPx: Int): Int = when {
        requestedPx <= SMALL_ARTWORK_SIZE_PX -> SMALL_ARTWORK_SIZE_PX
        requestedPx <= MEDIUM_ARTWORK_SIZE_PX -> MEDIUM_ARTWORK_SIZE_PX
        else -> MAX_ARTWORK_SIZE_PX
    }

    private companion object {
        const val TAG = "RIFTArtwork"
        const val MEMORY_CACHE_KB = 16 * 1024
        const val MAX_CONCURRENT_DECODES = 2
        const val SMALL_ARTWORK_SIZE_PX = 128
        const val MEDIUM_ARTWORK_SIZE_PX = 256
        const val MAX_ARTWORK_SIZE_PX = 512
        const val MISSING_ARTWORK_SIZE_PX = 0
        const val JPEG_QUALITY = 88
    }
}
