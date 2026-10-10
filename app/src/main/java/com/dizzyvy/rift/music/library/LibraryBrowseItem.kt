package com.dizzyvy.rift.music.library

import android.net.Uri
import com.dizzyvy.rift.music.model.AudioTrack

data class ArtistBrowseItem(val id: String, val name: String, val trackCount: Int, val artworkUri: Uri? = null)
data class AlbumBrowseItem(val id: String, val title: String, val artist: String, val trackCount: Int, val artworkUri: Uri? = null)
data class LibraryCollectionItem(val id: String, val title: String, val subtitle: String, val trackCount: Int, val artworkUri: Uri? = null)
data class DevicePlaylist(val id: Long, val volumeName: String, val name: String, val isLocal: Boolean = false, val isAuto: Boolean = false, val autoKind: String? = null, val trackCount: Int = 0)

fun librarySection(title: String): Char = title.trim().firstOrNull()
    ?.uppercaseChar()
    ?.takeIf { it in 'A'..'Z' }
    ?: '#'

private val artistSplitPattern = Regex("""\s*(?:,|&|\bfeat\.?)\s*""", RegexOption.IGNORE_CASE)
private val artistWhitespacePattern = Regex("""\s+""")

fun artistNamesForTrack(track: AudioTrack, aliases: Map<String, String> = emptyMap()): List<String> {
    val cleaned = cleanTrackMetadata(track)
    val preferred = cleaned.albumArtist.trim().takeIf { it.isNotBlank() && !it.equals("<unknown>", true) }
        ?: cleaned.artist
    return preferred.split(artistSplitPattern)
        .map { it.trim().replace(artistWhitespacePattern, " ") }
        .filter { it.isNotBlank() && !it.equals("<unknown>", true) }
        .map { resolveArtistAlias(it, aliases) }
        .distinctBy { normalizeArtistName(it) }
        .ifEmpty { listOf("Unknown artist") }
}

fun normalizeArtistName(name: String): String = name.trim().replace(artistWhitespacePattern, "").lowercase()

fun resolveArtistAlias(name: String, aliases: Map<String, String>): String {
    var current = name.trim().replace(artistWhitespacePattern, " ")
    val seen = mutableSetOf<String>()
    while (true) {
        val key = normalizeArtistName(current)
        if (!seen.add(key)) return current
        current = aliases[key]?.trim()?.replace(artistWhitespacePattern, " ")?.takeIf { it.isNotBlank() } ?: return current
    }
}

fun artistGroupKeys(track: AudioTrack, aliases: Map<String, String> = emptyMap()): List<String> =
    artistNamesForTrack(track, aliases).map(::normalizeArtistName).distinct()

fun artistGroupKey(track: AudioTrack, aliases: Map<String, String> = emptyMap()): String = artistGroupKeys(track, aliases).first()
