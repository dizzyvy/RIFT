package com.dizzyvy.sjmusicapp.music.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Artwork(val bitmap: Bitmap, val encodedBytes: ByteArray)

interface ArtworkRepository {
    suspend fun load(uri: Uri): Artwork?
}

class EmbeddedArtworkRepository(context: Context) : ArtworkRepository {
    private val resolver = context.applicationContext.contentResolver
    private val cache = object : LruCache<String, Artwork>(16 * 1024) {
        override fun sizeOf(key: String, value: Artwork): Int = ((value.bitmap.byteCount.toLong() + value.encodedBytes.size) / 1024L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1)
    }

    override suspend fun load(uri: Uri): Artwork? = withContext(Dispatchers.IO) {
        cache.get(uri.toString())?.let { return@withContext it }
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
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val sample = calculateSample(bounds.outWidth, bounds.outHeight, 512)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@runCatching null
            Artwork(bitmap, bytes).also { cache.put(uri.toString(), it) }
        }.getOrNull()
    }

    private fun calculateSample(width: Int, height: Int, max: Int): Int {
        var sample = 1
        while (width / sample > max || height / sample > max) sample *= 2
        return sample
    }
}
