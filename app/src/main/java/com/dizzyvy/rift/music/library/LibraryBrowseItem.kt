package com.dizzyvy.rift.music.library

import android.net.Uri
import com.dizzyvy.rift.music.model.AudioTrack

data class ArtistBrowseItem(val id: String, val name: String, val trackCount: Int, val artworkUri: Uri? = null)
data class AlbumBrowseItem(val id: String, val title: String, val artist: String, val trackCount: Int, val artworkUri: Uri? = null, val year: Int = 0)
data class LibraryCollectionItem(val id: String, val title: String, val subtitle: String, val trackCount: Int, val artworkUri: Uri? = null)
data class DevicePlaylist(
    val id: Long,
    val volumeName: String,
    val name: String,
    val isLocal: Boolean = false,
    val isAuto: Boolean = false,
    val autoKind: String? = null,
    val trackCount: Int = 0,
    val artworkUris: List<Uri> = emptyList(),
)

private val leadingTitleNoise = Regex("""^[\p{P}\p{S}\s]+""")
private val leadingTitleArticle = Regex("""^the\b[\s\p{P}\p{S}]*""", RegexOption.IGNORE_CASE)

fun sortableTitle(title: String): String = title.trim()
    .replace(leadingTitleNoise, "")
    .replace(leadingTitleArticle, "")
    .replace(leadingTitleNoise, "")

fun librarySection(title: String): Char = sortableTitle(title).firstOrNull()
    ?.uppercaseChar()
    ?.takeIf { it in 'A'..'Z' }
    ?: '#'

fun isValidLibraryYear(year: Int): Boolean =
    year in 1900..(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) + 1)

private val artistSplitPattern = Regex("""\s*(?:,|&|\bfeat\.?)\s*""", RegexOption.IGNORE_CASE)
private val artistWhitespacePattern = Regex("""\s+""")

fun artistNamesForTrack(track: AudioTrack, aliases: Map<String, String> = emptyMap()): List<String> {
    val preferred = track.albumArtist.trim().takeIf { it.isNotBlank() && !it.equals("<unknown>", true) }
        ?: track.artist
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
