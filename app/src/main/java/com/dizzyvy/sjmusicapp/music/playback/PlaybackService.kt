package com.dizzyvy.sjmusicapp.music.playback

import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
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
                    val items = readSavedQueue(prefs)
                    if (items.isEmpty()) return Futures.immediateFailedFuture(IllegalStateException("No saved local track"))
                    val index = prefs.getInt(KEY_INDEX, 0).coerceIn(0, items.lastIndex)
                    if (isForPlayback) {
                        mediaSession.player.shuffleModeEnabled = prefs.getBoolean(KEY_SHUFFLE, false)
                        mediaSession.player.repeatMode = prefs.getInt(KEY_REPEAT, androidx.media3.common.Player.REPEAT_MODE_OFF)
                    }
                    return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(items, index, prefs.getLong(KEY_POSITION, 0L).coerceAtLeast(0L)))
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
        val items = readSavedQueue(prefs)
        if (items.isEmpty()) return
        player.repeatMode = prefs.getInt(KEY_REPEAT, androidx.media3.common.Player.REPEAT_MODE_OFF)
        player.shuffleModeEnabled = prefs.getBoolean(KEY_SHUFFLE, false)
        val index = prefs.getInt(KEY_INDEX, 0).coerceIn(0, items.lastIndex)
        player.setMediaItems(items, index, prefs.getLong(KEY_POSITION, 0L).coerceAtLeast(0L))
        player.prepare()
    }

    private fun savePlaybackState() {
        val player = mediaSession?.player ?: return
        val item = player.currentMediaItem ?: return
        val uri = item.localConfiguration?.uri ?: return
        val metadata = item.mediaMetadata
        val queue = JSONArray()
        for (index in 0 until player.mediaItemCount) {
            val queuedItem = player.getMediaItemAt(index)
            val queuedUri = queuedItem.localConfiguration?.uri ?: continue
            val queuedMetadata = queuedItem.mediaMetadata
            queue.put(JSONObject()
                .put("uri", queuedUri.toString())
                .put("title", queuedMetadata.title?.toString().orEmpty())
                .put("artist", queuedMetadata.artist?.toString().orEmpty())
                .put("album", queuedMetadata.albumTitle?.toString().orEmpty()))
        }
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
            .putString(KEY_URI, uri.toString())
            .putString(KEY_TITLE, metadata.title?.toString().orEmpty())
            .putString(KEY_ARTIST, metadata.artist?.toString().orEmpty())
            .putString(KEY_ALBUM, metadata.albumTitle?.toString().orEmpty())
            .putString(KEY_QUEUE, queue.toString())
            .putInt(KEY_INDEX, player.currentMediaItemIndex.coerceAtLeast(0))
            .putLong(KEY_POSITION, player.currentPosition.coerceAtLeast(0L))
            .putBoolean(KEY_SHUFFLE, player.shuffleModeEnabled)
            .putInt(KEY_REPEAT, player.repeatMode)
            .apply()
    }

    private fun readSavedQueue(prefs: android.content.SharedPreferences): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        runCatching {
            val queue = JSONArray(prefs.getString(KEY_QUEUE, "[]"))
            for (index in 0 until queue.length()) {
                val entry = queue.optJSONObject(index) ?: continue
                val uriString = entry.optString("uri").takeIf(String::isNotBlank) ?: continue
                val metadata = MediaMetadata.Builder()
                    .setTitle(entry.optString("title", "Untitled track"))
                    .setArtist(entry.optString("artist", ""))
                    .setAlbumTitle(entry.optString("album", ""))
                    .build()
                result += MediaItem.Builder().setUri(Uri.parse(uriString)).setMediaMetadata(metadata).build()
            }
        }
        if (result.isEmpty()) {
            prefs.getString(KEY_URI, null)?.let { rawUri ->
                val metadata = MediaMetadata.Builder()
                    .setTitle(prefs.getString(KEY_TITLE, "Untitled track"))
                    .setArtist(prefs.getString(KEY_ARTIST, ""))
                    .setAlbumTitle(prefs.getString(KEY_ALBUM, ""))
                    .build()
                result += MediaItem.Builder().setUri(Uri.parse(rawUri)).setMediaMetadata(metadata).build()
            }
        }
        return result
    }

    private companion object {
        const val PREFERENCES = "last_playback"
        const val KEY_URI = "uri"
        const val KEY_QUEUE = "queue"
        const val KEY_INDEX = "queue_index"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_ALBUM = "album"
        const val KEY_POSITION = "position"
        const val KEY_SHUFFLE = "shuffle"
        const val KEY_REPEAT = "repeat"
        const val CHECKPOINT_MS = 5_000L
    }
}
