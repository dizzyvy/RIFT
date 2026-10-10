package com.dizzyvy.rift.music.library

import com.dizzyvy.rift.music.model.AudioTrack

private val metadataWhitespace = Regex("""\s+""")
private val artistTitlePrefix = Regex("""^(.{1,100}?)\s+[-–—]\s+(.+)$""")
private val bracketedTitleTag = Regex(
    """(?i)\s*[\[(]\s*(?:(?:official\s+)?(?:music\s+)?(?:video|mv)|(?:official\s+)?audio|(?:official\s+)?lyrics?(?:\s+video)?|visuali[sz]er|4k|hd)\s*[\])]\s*""",
)
private val trailingTitleTag = Regex(
    """(?i)\s*(?:[-–—|:]\s*)?(?:(?:official\s+)?(?:music\s+)?(?:video|mv)|(?:official\s+)?audio|(?:official\s+)?lyrics?(?:\s+video)?|visuali[sz]er|4k|hd)\s*$""",
)
private val hashStyleName = Regex("""(?i)^(?:[a-f0-9]{8,}|[a-f0-9]{8,}[-_][a-f0-9-]{4,})$""")
private val unknownMetadata = setOf("", "unknown", "<unknown>", "unknown album", "unknown artist", "n/a", "null", "none", "?")
private val uploaderChannels = setOf("ea4kfilms", "flexxbfilmz", "raptv")

private val genreMappings = mapOf(
    "hip-hop/rap" to "Hip-Hop/Rap",
    "hip-hop" to "Hip-Hop/Rap",
    "hip hop" to "Hip-Hop/Rap",
    "rap" to "Hip-Hop/Rap",
    "rap gangsta" to "Hip-Hop/Rap",
    "gangsta rap" to "Hip-Hop/Rap",
)

private val ignoredVideoGenres = setOf("music", "people & blogs", "entertainment")

fun cleanTrackMetadata(track: AudioTrack): AudioTrack {
    val originalArtist = cleanMetadataValue(track.artist)
    val originalAlbumArtist = cleanMetadataValue(track.albumArtist)
    val noTags = isUnknownMetadata(originalArtist) && isUnknownMetadata(originalAlbumArtist) &&
        isUnknownMetadata(cleanAlbumName(track.album, track.relativePath, track.filePath))
    val filenameTitle = filenameTitle(track)
    val rawTitle = track.title.trim().ifBlank { filenameTitle }
    val baseTitle = cleanTitleTags(rawTitle)
    val titleNeedsFilenameFallback = baseTitle.isBlank() || isPlaceholderTitle(baseTitle) ||
        (noTags && hashStyleName.matches(baseTitle))
    val titleToParse = if (titleNeedsFilenameFallback) filenameTitle.ifBlank { "Untitled" } else baseTitle
    val prefixMatch = artistTitlePrefix.matchEntire(titleToParse)
    var title = titleToParse
    var artist = originalArtist
    var albumArtist = originalAlbumArtist
    if (prefixMatch != null) {
        val prefix = cleanMetadataValue(prefixMatch.groupValues[1])
        if (prefix.isNotBlank()) {
            title = cleanTitleTags(prefixMatch.groupValues[2])
            artist = prefix
            if (isUnknownMetadata(albumArtist) || isUploaderChannel(albumArtist)) albumArtist = prefix
        }
    }

    return track.copy(
        title = title.ifBlank { "Untitled" },
        artist = artist,
        albumArtist = albumArtist,
        album = cleanAlbumName(track.album, track.relativePath, track.filePath),
        genre = canonicalGenre(track.genre).orEmpty(),
    )
}

fun cleanTitleTags(value: String): String = value
    .replace(bracketedTitleTag, " ")
    .replace(trailingTitleTag, "")
    .replace(metadataWhitespace, " ")
    .trim()
    .trimEnd('-', '–', '—', '|', ':')
    .trim()

fun canonicalGenre(value: String): String? {
    val normalized = value.trim().replace(metadataWhitespace, " ").lowercase()
    if (normalized in ignoredVideoGenres || isUnknownMetadata(normalized)) return null
    return genreMappings[normalized] ?: normalized.split(' ').joinToString(" ") { word ->
        when (word) {
            "r&b" -> "R&B"
            "edm" -> "EDM"
            else -> word.replaceFirstChar { it.uppercase() }
        }
    }
}

fun genreGroupLabel(value: String): String = canonicalGenre(value) ?: "Unknown"

fun albumGroupKey(track: AudioTrack, aliases: Map<String, String> = emptyMap()): String {
    val cleaned = cleanTrackMetadata(track)
    val albumArtist = cleanMetadataValue(cleaned.albumArtist)
        .takeUnless(::isUnknownMetadata)
        ?.let { resolveArtistAlias(it, aliases) }
        ?: artistNamesForTrack(cleaned, aliases).joinToString(", ")
    return "${normalizeAlbumKey(cleaned.album)}|${normalizeArtistName(albumArtist)}"
}

private fun cleanAlbumName(value: String, relativePath: String, filePath: String): String {
    val album = cleanMetadataValue(value)
    if (isUnknownMetadata(album) || album.any { it == '?' || it == '\uFFFD' }) return "Unknown album"
    val folderNames = buildSet {
        relativePath.replace('\\', '/').trimEnd('/').substringAfterLast('/')
            .takeIf(String::isNotBlank)?.let(::add)
        filePath.replace('\\', '/').substringBeforeLast('/').trimEnd('/').substringAfterLast('/')
            .takeIf(String::isNotBlank)?.let(::add)
    }
    return if (folderNames.any { normalizeAlbumKey(it) == normalizeAlbumKey(album) }) "Unknown album" else album
}

private fun filenameTitle(track: AudioTrack): String {
    val filename = track.displayName.ifBlank {
        track.filePath.substringAfterLast('/').substringAfterLast('\\')
    }
    val stem = filename.substringBeforeLast('.', filename).trim()
    return if (isPlaceholderTitle(stem) || hashStyleName.matches(stem)) "" else stem
}

private fun cleanMetadataValue(value: String): String =
    value.trim().replace(metadataWhitespace, " ").takeUnless(::isUnknownMetadata).orEmpty()

private fun isUnknownMetadata(value: String): Boolean = value.trim().lowercase() in unknownMetadata

private fun isPlaceholderTitle(value: String): Boolean =
    isUnknownMetadata(value) || value.equals("Untitled track", ignoreCase = true) ||
        value.equals("Audio", ignoreCase = true)

private fun isUploaderChannel(value: String): Boolean =
    value.filter(Char::isLetterOrDigit).lowercase() in uploaderChannels

private fun normalizeAlbumKey(value: String): String = value.trim().replace(metadataWhitespace, " ").lowercase()
