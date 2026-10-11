package com.dizzyvy.rift.music.playback

import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.dizzyvy.rift.music.library.SqlitePlaylistStore
import java.util.concurrent.Executors

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private data class SavedQueueEntry(
        val uri: String,
        val title: String,
        val artist: String,
        val album: String,
    )

    private var mediaSession: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private val playbackIo = Executors.newSingleThreadExecutor()
    private val playbackStore by lazy { SqlitePlaylistStore(applicationContext) }
    @Volatile private var destroyed = false
    @Volatile private var savedQueueJson = "[]"
    private val checkpoint = object : Runnable {
        override fun run() {
            savePlaybackState()
            handler.postDelayed(this, CHECKPOINT_MS)
        }
    }
    private val playerListener = object : androidx.media3.common.Player.Listener {
        override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) {
            if (!destroyed) savePlaybackState(events.contains(Player.EVENT_TIMELINE_CHANGED))
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            setHandleAudioBecomingNoisy(true)
            addListener(playerListener)
        }
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                override fun onPlaybackResumption(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    isForPlayback: Boolean,
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
                    playbackIo.execute {
                        runCatching { loadOrMigratePlaybackState() }
                            .onSuccess { state ->
                                savedQueueJson = state[KEY_QUEUE].orEmpty().ifBlank { "[]" }
                                val items = readSavedQueue(state)
                                if (items.isEmpty()) {
                                    result.setException(IllegalStateException("No saved local track"))
                                } else {
                                    val index = state[KEY_INDEX]?.toIntOrNull()?.coerceIn(0, items.lastIndex) ?: 0
                                    val position = state[KEY_POSITION]?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                                    handler.post {
                                        if (isForPlayback) {
                                            mediaSession.player.shuffleModeEnabled = state[KEY_SHUFFLE].toBoolean()
                                            mediaSession.player.repeatMode = state[KEY_REPEAT]?.toIntOrNull()
                                                ?: androidx.media3.common.Player.REPEAT_MODE_OFF
                                        }
                                        result.set(MediaSession.MediaItemsWithStartPosition(items, index, position))
                                    }
                                }
                            }
                            .onFailure(result::setException)
                    }
                    return result
                }
            })
            .build()
        playbackIo.execute {
            runCatching { loadOrMigratePlaybackState() }
                .onSuccess { state ->
                    savedQueueJson = state[KEY_QUEUE].orEmpty().ifBlank { "[]" }
                    handler.post {
                        if (!destroyed && player.mediaItemCount == 0) restorePlaybackState(player, state)
                    }
                }
                .onFailure { android.util.Log.e(TAG, "Could not restore saved playback state", it) }
        }
        handler.postDelayed(checkpoint, CHECKPOINT_MS)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacks(checkpoint)
        savePlaybackState()
        mediaSession?.player?.release()
        mediaSession?.release()
        mediaSession = null
        playbackIo.execute { playbackStore.close() }
        playbackIo.shutdown()
        super.onDestroy()
    }

    private fun restorePlaybackState(player: ExoPlayer, state: Map<String, String>) {
        val items = readSavedQueue(state)
        if (items.isEmpty()) return
        player.repeatMode = state[KEY_REPEAT]?.toIntOrNull() ?: androidx.media3.common.Player.REPEAT_MODE_OFF
        player.shuffleModeEnabled = state[KEY_SHUFFLE].toBoolean()
        val index = state[KEY_INDEX]?.toIntOrNull()?.coerceIn(0, items.lastIndex) ?: 0
        val position = state[KEY_POSITION]?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        player.setMediaItems(items, index, position)
        player.prepare()
    }

    private fun savePlaybackState(queueChanged: Boolean = false) {
        val player = mediaSession?.player ?: return
        if (player.mediaItemCount == 0) {
            playbackIo.execute {
                savedQueueJson = "[]"
                playbackStore.savePlaybackStateSync(emptyMap())
            }
            return
        }
        val item = player.currentMediaItem ?: return
        val uri = item.localConfiguration?.uri ?: return
        val metadata = item.mediaMetadata
        val queueSnapshot = if (queueChanged) {
            (0 until player.mediaItemCount).mapNotNull { index ->
                val queuedItem = player.getMediaItemAt(index)
                val queuedUri = queuedItem.localConfiguration?.uri ?: return@mapNotNull null
                val queuedMetadata = queuedItem.mediaMetadata
                SavedQueueEntry(
                    uri = queuedUri.toString(),
                    title = queuedMetadata.title?.toString().orEmpty(),
                    artist = queuedMetadata.artist?.toString().orEmpty(),
                    album = queuedMetadata.albumTitle?.toString().orEmpty(),
                )
            }
        } else null
        val playbackFields = mapOf(
            KEY_URI to uri.toString(),
            KEY_TITLE to metadata.title?.toString().orEmpty(),
            KEY_ARTIST to metadata.artist?.toString().orEmpty(),
            KEY_ALBUM to metadata.albumTitle?.toString().orEmpty(),
            KEY_INDEX to player.currentMediaItemIndex.coerceAtLeast(0).toString(),
            KEY_POSITION to player.currentPosition.coerceAtLeast(0L).toString(),
            KEY_SHUFFLE to player.shuffleModeEnabled.toString(),
            KEY_REPEAT to player.repeatMode.toString(),
        )
        playbackIo.execute {
            if (queueSnapshot != null) {
                savedQueueJson = JSONArray().apply {
                    queueSnapshot.forEach { queued ->
                        put(JSONObject()
                            .put("uri", queued.uri)
                            .put("title", queued.title)
                            .put("artist", queued.artist)
                            .put("album", queued.album))
                    }
                }.toString()
            }
            playbackStore.savePlaybackStateSync(playbackFields + (KEY_QUEUE to savedQueueJson))
        }
    }

    private fun readSavedQueue(state: Map<String, String>): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        runCatching {
            val queue = JSONArray(state[KEY_QUEUE] ?: "[]")
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
            state[KEY_URI]?.let { rawUri ->
                val metadata = MediaMetadata.Builder()
                    .setTitle(state[KEY_TITLE] ?: "Untitled track")
                    .setArtist(state[KEY_ARTIST].orEmpty())
                    .setAlbumTitle(state[KEY_ALBUM].orEmpty())
                    .build()
                result += MediaItem.Builder().setUri(Uri.parse(rawUri)).setMediaMetadata(metadata).build()
            }
        }
        return result
    }

    private fun loadOrMigratePlaybackState(): Map<String, String> {
        val preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
        val legacy = buildMap {
            preferences.getString(KEY_URI, null)?.let { put(KEY_URI, it) }
            preferences.getString(KEY_QUEUE, null)?.let { put(KEY_QUEUE, it) }
            preferences.getString(KEY_TITLE, null)?.let { put(KEY_TITLE, it) }
            preferences.getString(KEY_ARTIST, null)?.let { put(KEY_ARTIST, it) }
            preferences.getString(KEY_ALBUM, null)?.let { put(KEY_ALBUM, it) }
            if (preferences.contains(KEY_INDEX)) put(KEY_INDEX, preferences.getInt(KEY_INDEX, 0).toString())
            if (preferences.contains(KEY_POSITION)) put(KEY_POSITION, preferences.getLong(KEY_POSITION, 0L).toString())
            if (preferences.contains(KEY_SHUFFLE)) put(KEY_SHUFFLE, preferences.getBoolean(KEY_SHUFFLE, false).toString())
            if (preferences.contains(KEY_REPEAT)) put(KEY_REPEAT, preferences.getInt(KEY_REPEAT, 0).toString())
        }
        val savedState = playbackStore.migratePlaybackStateSync(legacy)
        if (savedState.isNotEmpty() && preferences.all.isNotEmpty()) {
            check(preferences.edit().clear().commit()) { "Could not clear migrated playback preferences." }
        }
        return savedState
    }

    private companion object {
        const val TAG = "RIFTPlaybackService"
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
