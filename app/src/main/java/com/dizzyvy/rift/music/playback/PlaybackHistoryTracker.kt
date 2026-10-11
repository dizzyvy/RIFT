package com.dizzyvy.rift.music.playback

internal class PlaybackHistoryTracker {
    private var currentUri: String? = null
    private var currentTrackRecorded = false

    fun onPlaybackState(
        uri: String?,
        isPlaying: Boolean,
        mediaItemTransition: Boolean = false,
        repeatOneLoop: Boolean = false,
        stopped: Boolean = false,
    ): String? {
        if (uri != currentUri) {
            currentUri = uri
            currentTrackRecorded = false
        }
        if (stopped) {
            currentTrackRecorded = false
            return null
        }
        if (mediaItemTransition && !repeatOneLoop) currentTrackRecorded = false
        if (!isPlaying || uri == null || currentTrackRecorded) return null
        currentTrackRecorded = true
        return uri
    }
}
