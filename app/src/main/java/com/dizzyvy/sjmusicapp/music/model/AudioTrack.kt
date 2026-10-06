package com.dizzyvy.sjmusicapp.music.model

import android.net.Uri

data class AudioTrack(
    val id: Long,
    val uri: Uri,
    val title: String,
    val displayName: String = "",
    val artist: String,
    val albumArtist: String = "",
    val album: String,
    val durationMs: Long,
    val artistId: Long = -1L,
    val albumId: Long = -1L,
    val volumeName: String = "external",
)
