package com.dizzyvy.sjmusicapp.music.library

import com.dizzyvy.sjmusicapp.music.model.AudioTrack
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object M3uPlaylistFormat {
    fun encode(name: String, tracks: List<AudioTrack>): String = buildString {
        appendLine("#EXTM3U")
        tracks.forEach { track ->
            val artist = track.artist.takeIf { it.isNotBlank() && !it.equals("<unknown>", true) } ?: "Unknown artist"
            val title = track.title.takeIf { it.isNotBlank() && !it.equals("<unknown>", true) } ?: "Untitled track"
            appendLine("#EXTINF:${(track.durationMs / 1_000L).coerceAtLeast(0L)},$artist - $title")
            appendLine(track.uri.toString())
        }
    }

    fun entries(contents: String): List<String> = contents.lineSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .toList()

    fun fileName(entry: String): String = runCatching {
        val decoded = URLDecoder.decode(entry.removePrefix("file://"), StandardCharsets.UTF_8.name())
        File(decoded).name
    }.getOrDefault(entry.substringAfterLast('/'))
}
