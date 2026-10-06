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
    val dateAddedSeconds: Long = 0L,
    val sizeBytes: Long = 0L,
    val mimeType: String = "",
    val bitrate: Int = -1,
    val sampleRateHz: Int = -1,
    val filePath: String = "",
    val artistId: Long = -1L,
    val albumId: Long = -1L,
    val volumeName: String = "external",
)
