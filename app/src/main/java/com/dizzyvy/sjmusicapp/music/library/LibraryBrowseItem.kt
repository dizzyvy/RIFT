package com.dizzyvy.sjmusicapp.music.library

data class ArtistBrowseItem(val id: String, val name: String, val trackCount: Int)
data class AlbumBrowseItem(val id: String, val title: String, val artist: String, val trackCount: Int)
data class DevicePlaylist(val id: Long, val volumeName: String, val name: String)

fun librarySection(title: String): Char = title.trim().firstOrNull()
    ?.uppercaseChar()
    ?.takeIf { it in 'A'..'Z' }
    ?: '#'

fun artistGroupKey(track: com.dizzyvy.sjmusicapp.music.model.AudioTrack): String =
    if (track.artistId >= 0) "${track.volumeName}:${track.artistId}" else track.artist.ifBlank { "Unknown artist" }.lowercase()

fun albumGroupKey(track: com.dizzyvy.sjmusicapp.music.model.AudioTrack): String =
    if (track.albumId >= 0) "${track.volumeName}:${track.albumId}" else "${track.album.lowercase()}|${track.artist.lowercase()}"
