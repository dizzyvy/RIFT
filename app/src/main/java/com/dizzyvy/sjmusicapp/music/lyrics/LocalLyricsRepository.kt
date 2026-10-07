package com.dizzyvy.sjmusicapp.music.lyrics

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.dizzyvy.sjmusicapp.music.model.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

data class TimedLyricLine(val timeMs: Long, val text: String)
data class LocalLyrics(val timedLines: List<TimedLyricLine> = emptyList(), val plainText: String = "")

class LocalLyricsRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    suspend fun load(track: AudioTrack, treeUri: Uri?): LocalLyrics? = withContext(Dispatchers.IO) {
        if (treeUri == null) return@withContext null
        val relative = track.relativePath.trim('/').split('/').filter(String::isNotBlank).toMutableList()
        val rootPath = runCatching { DocumentsContract.getTreeDocumentId(treeUri).substringAfter(':', "").trim('/') }.getOrDefault("")
        if (rootPath.isNotEmpty()) {
            val rootSegments = rootPath.split('/').filter(String::isNotBlank)
            if (relative.take(rootSegments.size).equalsIgnoreCaseSegments(rootSegments)) repeat(rootSegments.size) { relative.removeAt(0) }
        }
        val trackDirectory = walkDirectories(resolver, treeUri, relative) ?: return@withContext null
        val stem = (track.displayName.ifBlank { track.title }).substringBeforeLast('.', track.title)
        val lrc = findChild(resolver, treeUri, trackDirectory, "$stem.lrc")
        val text = lrc?.let { readText(resolver, it) }
        if (text != null) {
            val timed = parseLrc(text)
            if (timed.isNotEmpty()) return@withContext LocalLyrics(timedLines = timed)
            if (text.isNotBlank()) return@withContext LocalLyrics(plainText = text.trim())
        }
        val txt = findChild(resolver, treeUri, trackDirectory, "$stem.txt") ?: return@withContext null
        readText(resolver, txt)?.takeIf(String::isNotBlank)?.let { LocalLyrics(plainText = it.trim()) }
    }

    private fun walkDirectories(resolver: ContentResolver, tree: Uri, segments: List<String>): Uri? {
        var documentId = DocumentsContract.getTreeDocumentId(tree)
        for (segment in segments) {
            val children = runCatching { DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId) }.getOrNull() ?: return null
            val match = resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                var found: String? = null
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameColumn).equals(segment, ignoreCase = true) && cursor.getString(mimeColumn) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        found = cursor.getString(idColumn)
                        break
                    }
                }
                found
            } ?: return null
            documentId = match
        }
        return runCatching { DocumentsContract.buildDocumentUriUsingTree(tree, documentId) }.getOrNull()
    }

    private fun findChild(resolver: ContentResolver, tree: Uri, directory: Uri, name: String): Uri? {
        val documentId = runCatching { DocumentsContract.getDocumentId(directory) }.getOrNull() ?: return null
        val children = runCatching { DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId) }.getOrNull() ?: return null
        val foundId = resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            var found: String? = null
            while (cursor.moveToNext()) if (cursor.getString(nameColumn).equals(name, ignoreCase = true)) { found = cursor.getString(idColumn); break }
            found
        } ?: return null
        return runCatching { DocumentsContract.buildDocumentUriUsingTree(tree, foundId) }.getOrNull()
    }

    private fun readText(resolver: ContentResolver, uri: Uri): String? = runCatching {
        resolver.openInputStream(uri)?.use { stream ->
            InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
                val output = StringBuilder()
                val buffer = CharArray(4096)
                while (output.length <= MAX_CHARS) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    output.append(buffer, 0, count)
                }
                require(output.length <= MAX_CHARS) { "Lyrics file is too large." }
                output.toString()
            }
        }
    }.getOrNull()

    private fun parseLrc(contents: String): List<TimedLyricLine> = contents.lineSequence().flatMap { line ->
        val timestamps = TIMESTAMP.findAll(line).mapNotNull { match ->
            val minutes = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val seconds = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
            val fractionText = match.groupValues[3]
            val millis = when (fractionText.length) { 0 -> 0L; 1 -> (fractionText.toLongOrNull() ?: 0L) * 100L; 2 -> (fractionText.toLongOrNull() ?: 0L) * 10L; else -> (fractionText.take(3).toLongOrNull() ?: 0L) }
            (minutes * 60L + seconds) * 1000L + millis
        }.toList()
        val text = TIMESTAMP.replace(line, "").trim()
        timestamps.asSequence().map { TimedLyricLine(it, text) }
    }.filter { it.text.isNotEmpty() }.sortedBy { it.timeMs }.toList()

    private fun List<String>.equalsIgnoreCaseSegments(other: List<String>): Boolean = size == other.size && indices.all { this[it].equals(other[it], ignoreCase = true) }

    private companion object {
        const val MAX_CHARS = 512 * 1024
        val TIMESTAMP = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    }
}
