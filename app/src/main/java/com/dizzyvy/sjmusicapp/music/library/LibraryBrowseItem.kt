package com.dizzyvy.sjmusicapp.music.library

import android.net.Uri
import com.dizzyvy.sjmusicapp.music.model.AudioTrack

data class ArtistBrowseItem(val id: String, val name: String, val trackCount: Int, val artworkUri: Uri? = null)
data class AlbumBrowseItem(val id: String, val title: String, val artist: String, val trackCount: Int, val artworkUri: Uri? = null)
data class DevicePlaylist(val id: Long, val volumeName: String, val name: String, val isLocal: Boolean = false, val isAuto: Boolean = false)

fun librarySection(title: String): Char = title.trim().firstOrNull()
    ?.uppercaseChar()
    ?.takeIf { it in 'A'..'Z' }
    ?: '#'

private val artistSplitPattern = Regex("""\s*(?:,|&|\bfeat\.?)\s*""", RegexOption.IGNORE_CASE)
private val artistWhitespacePattern = Regex("""\s+""")

fun artistNamesForTrack(track: AudioTrack): List<String> {
    val preferred = track.albumArtist.trim().takeIf { it.isNotBlank() && !it.equals("<unknown>", true) }
        ?: track.artist
    return preferred.split(artistSplitPattern)
        .map { it.trim().replace(artistWhitespacePattern, " ") }
        .filter { it.isNotBlank() && !it.equals("<unknown>", true) }
        .distinctBy { normalizeArtistName(it) }
        .ifEmpty { listOf("Unknown artist") }
}

fun normalizeArtistName(name: String): String = name.trim().replace(artistWhitespacePattern, "").lowercase()

fun artistGroupKeys(track: AudioTrack): List<String> =
    artistNamesForTrack(track).map(::normalizeArtistName).distinct()

fun artistGroupKey(track: AudioTrack): String = artistGroupKeys(track).first()

fun albumGroupKey(track: AudioTrack): String {
    if (track.albumId >= 0) return "${track.volumeName}:${track.albumId}"
    val albumArtist = track.albumArtist.ifBlank { artistNamesForTrack(track).joinToString(", ") }
    return "${track.album.trim().lowercase()}|${normalizeArtistName(albumArtist)}"
}
