package com.dizzyvy.sjmusicapp.music.playback

import com.dizzyvy.sjmusicapp.music.model.AudioTrack
import kotlinx.coroutines.flow.StateFlow

data class PlaybackSnapshot(
    val queue: List<AudioTrack>,
    val currentIndex: Int,
    val currentTrack: AudioTrack?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val errorMessage: String?,
)

interface PlaybackController {
    val snapshot: StateFlow<PlaybackSnapshot>

    fun setQueue(tracks: List<AudioTrack>, startIndex: Int)
    fun playQueueItem(index: Int)
    fun playPause()
    fun skipNext()
    fun skipPrevious()
    fun seekTo(positionMs: Long)
}
