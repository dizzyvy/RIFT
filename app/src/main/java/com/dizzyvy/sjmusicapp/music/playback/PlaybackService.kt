package com.dizzyvy.sjmusicapp.music.playback

import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private val checkpoint = object : Runnable {
        override fun run() {
            savePlaybackState()
            handler.postDelayed(this, CHECKPOINT_MS)
        }
    }
    private val playerListener = object : androidx.media3.common.Player.Listener {
        override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) {
            savePlaybackState()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            addListener(playerListener)
            restorePlaybackState(this)
        }
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                override fun onPlaybackResumption(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    isForPlayback: Boolean,
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    val prefs = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                    val savedUri = prefs.getString(KEY_URI, null)?.let(Uri::parse)
                        ?: return Futures.immediateFailedFuture(IllegalStateException("No saved local track"))
                    val metadata = MediaMetadata.Builder()
                        .setTitle(prefs.getString(KEY_TITLE, "Untitled track"))
                        .setArtist(prefs.getString(KEY_ARTIST, ""))
                        .setAlbumTitle(prefs.getString(KEY_ALBUM, ""))
                        .build()
                    val item = MediaItem.Builder().setUri(savedUri).setMediaMetadata(metadata).build()
                    if (isForPlayback) {
                        mediaSession.player.shuffleModeEnabled = prefs.getBoolean(KEY_SHUFFLE, false)
                        mediaSession.player.repeatMode = prefs.getInt(KEY_REPEAT, androidx.media3.common.Player.REPEAT_MODE_OFF)
                    }
                    return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(listOf(item), 0, prefs.getLong(KEY_POSITION, 0L).coerceAtLeast(0L)))
                }
            })
            .build()
        handler.postDelayed(checkpoint, CHECKPOINT_MS)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        handler.removeCallbacks(checkpoint)
        savePlaybackState()
        mediaSession?.player?.release()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private fun restorePlaybackState(player: ExoPlayer) {
        val prefs = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
        val uri = prefs.getString(KEY_URI, null)?.let(Uri::parse) ?: return
        val metadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(prefs.getString(KEY_TITLE, "Untitled track"))
            .setArtist(prefs.getString(KEY_ARTIST, ""))
            .setAlbumTitle(prefs.getString(KEY_ALBUM, ""))
            .build()
        player.setMediaItem(androidx.media3.common.MediaItem.Builder().setUri(uri).setMediaMetadata(metadata).build())
        player.repeatMode = prefs.getInt(KEY_REPEAT, androidx.media3.common.Player.REPEAT_MODE_OFF)
        player.shuffleModeEnabled = prefs.getBoolean(KEY_SHUFFLE, false)
        player.prepare()
        player.seekTo(prefs.getLong(KEY_POSITION, 0L).coerceAtLeast(0L))
    }

    private fun savePlaybackState() {
        val player = mediaSession?.player ?: return
        val item = player.currentMediaItem ?: return
        val uri = item.localConfiguration?.uri ?: return
        val metadata = item.mediaMetadata
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
            .putString(KEY_URI, uri.toString())
            .putString(KEY_TITLE, metadata.title?.toString().orEmpty())
            .putString(KEY_ARTIST, metadata.artist?.toString().orEmpty())
            .putString(KEY_ALBUM, metadata.albumTitle?.toString().orEmpty())
            .putLong(KEY_POSITION, player.currentPosition.coerceAtLeast(0L))
            .putBoolean(KEY_SHUFFLE, player.shuffleModeEnabled)
            .putInt(KEY_REPEAT, player.repeatMode)
            .apply()
    }

    private companion object {
        const val PREFERENCES = "last_playback"
        const val KEY_URI = "uri"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_ALBUM = "album"
        const val KEY_POSITION = "position"
        const val KEY_SHUFFLE = "shuffle"
        const val KEY_REPEAT = "repeat"
        const val CHECKPOINT_MS = 5_000L
    }
}
