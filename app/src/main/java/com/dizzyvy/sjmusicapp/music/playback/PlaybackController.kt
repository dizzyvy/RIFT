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
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = androidx.media3.common.Player.REPEAT_MODE_OFF,
    val sleepTimerRemainingMs: Long? = null,
    val sleepTimerFinishingTrack: Boolean = false,
    val playbackSpeed: Float = 1f,
)

interface PlaybackController {
    val snapshot: StateFlow<PlaybackSnapshot>

    fun setQueue(tracks: List<AudioTrack>, startIndex: Int)
    fun playQueueItem(index: Int)
    fun playPause()
    fun skipNext()
    fun skipPrevious()
    fun seekTo(positionMs: Long)
    fun setShuffleEnabled(enabled: Boolean)
    fun setRepeatMode(mode: Int)
    fun moveQueueItem(fromIndex: Int, toIndex: Int)
    fun removeQueueItem(index: Int)
    fun addQueueItem(track: AudioTrack)
    fun playNext(tracks: List<AudioTrack>)
    fun setSleepTimer(durationMs: Long?, finishCurrentTrack: Boolean)
    fun setPlaybackSpeed(speed: Float)
    fun clearQueue()
}
