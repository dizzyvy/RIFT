package com.dizzyvy.sjmusicapp.music.library

import com.dizzyvy.sjmusicapp.music.model.AudioTrack

interface AudioLibraryRepository {
    suspend fun loadTracks(): List<AudioTrack>
}
