package com.dizzyvy.rift.music.playback

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.dizzyvy.rift.R
import com.dizzyvy.rift.music.model.AudioTrack
import com.dizzyvy.rift.music.library.cleanTrackMetadata
import com.dizzyvy.rift.music.artwork.ArtworkRepository
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class Media3PlaybackController(context: Context, private val artworkRepository: ArtworkRepository) : PlaybackController {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { command -> mainHandler.post(command) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _snapshot = MutableStateFlow(EMPTY_SNAPSHOT)
    private val controllerFuture: ListenableFuture<MediaController>
    private var mediaController: MediaController? = null
    private var queuedTracks = emptyList<AudioTrack>()
    private var pendingQueue: Pair<List<AudioTrack>, Int>? = null
    private var sleepTimerJob: Job? = null
    private var finishCurrentTrackAfterTimer = false
    private val pendingAddedTracks = mutableListOf<AudioTrack>()
    private val pendingPlayNextTracks = mutableListOf<AudioTrack>()
    private var positionJob: Job? = null
    private var released = false

    override val snapshot: StateFlow<PlaybackSnapshot> = _snapshot.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refreshSnapshot(player, events.contains(Player.EVENT_TIMELINE_CHANGED))
            updatePositionPolling()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (finishCurrentTrackAfterTimer) {
                finishCurrentTrackAfterTimer = false
                mediaController?.pause()
                _snapshot.value = _snapshot.value.copy(sleepTimerFinishingTrack = false)
            }
            _snapshot.value = _snapshot.value.copy(errorMessage = null)
            mediaController?.let { player ->
                refreshSnapshot(player, queueChanged = true)
                player.currentMediaItem?.toAudioTrack()?.let(::loadSessionArtwork)
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _snapshot.value = _snapshot.value.copy(
                isPlaying = false,
                errorMessage = appContext.getString(R.string.playback_error),
            )
        }
    }

    init {
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture.addListener(
            {
                if (released) return@addListener
                runCatching { controllerFuture.get() }
                    .onSuccess { controller ->
                        mediaController = controller
                        controller.addListener(playerListener)
                        pendingQueue?.let { (tracks, startIndex) ->
                            pendingQueue = null
                            applyQueue(controller, tracks, startIndex)
                        }
                        if (pendingPlayNextTracks.isNotEmpty()) {
                            insertNext(controller, pendingPlayNextTracks.toList())
                            pendingPlayNextTracks.clear()
                        }
                        pendingAddedTracks.toList().forEach { controller.addMediaItem(toMediaItem(it)) }
                        pendingAddedTracks.clear()
                        refreshSnapshot(controller, queueChanged = true)
                        updatePositionPolling()
                    }
                    .onFailure {
                        _snapshot.value = _snapshot.value.copy(
                            errorMessage = appContext.getString(R.string.playback_error),
                        )
                    }
            },
            mainExecutor,
        )
    }

    override fun setQueue(tracks: List<AudioTrack>, startIndex: Int) {
        if (tracks.isEmpty() || startIndex !in tracks.indices) return
        queuedTracks = tracks.toList()
        val controller = mediaController
        if (controller == null) {
            pendingQueue = queuedTracks to startIndex
            val currentTrack = queuedTracks[startIndex]
            _snapshot.value = _snapshot.value.copy(
                queue = queuedTracks,
                currentIndex = startIndex,
                currentTrack = currentTrack,
                isPlaying = mediaController?.isPlaying == true,
                positionMs = 0L,
                durationMs = currentTrack.durationMs,
                errorMessage = null,
            )
        } else {
            applyQueue(controller, queuedTracks, startIndex)
        }
    }

    override fun playQueueItem(index: Int) {
        mediaController?.takeIf { index in 0 until it.mediaItemCount }?.let { controller ->
            controller.seekToDefaultPosition(index)
            controller.play()
            _snapshot.value = _snapshot.value.copy(errorMessage = null)
        }
    }

    override fun playPause() {
        mediaController?.let { controller ->
            if (controller.isPlaying) controller.pause() else controller.play()
        }
    }

    override fun skipNext() {
        mediaController?.seekToNextMediaItem()
    }

    override fun skipPrevious() {
        mediaController?.seekToPreviousMediaItem()
    }

    override fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs.coerceAtLeast(0L))
    }

    override fun setShuffleEnabled(enabled: Boolean) { mediaController?.shuffleModeEnabled = enabled }
    override fun setPlaybackSpeed(speed: Float) { mediaController?.setPlaybackSpeed(speed.coerceIn(0.5f, 2f)) }
    override fun setRepeatMode(mode: Int) { mediaController?.repeatMode = mode }
    override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        mediaController?.takeIf { fromIndex in 0 until it.mediaItemCount && toIndex in 0 until it.mediaItemCount }?.moveMediaItem(fromIndex, toIndex)
    }
    override fun removeQueueItem(index: Int) {
        mediaController?.takeIf { index in 0 until it.mediaItemCount }?.removeMediaItem(index)
    }
    override fun restoreQueueItem(track: AudioTrack, index: Int) {
        val controller = mediaController
        if (controller != null) {
            controller.addMediaItem(index.coerceIn(0, controller.mediaItemCount), toMediaItem(track))
            return
        }
        val queue = (pendingQueue?.first ?: _snapshot.value.queue).toMutableList()
        val targetIndex = index.coerceIn(0, queue.size)
        queue.add(targetIndex, track)
        val currentIndex = pendingQueue?.second ?: _snapshot.value.currentIndex
        val restoredCurrentIndex = if (_snapshot.value.currentTrack != null && targetIndex <= currentIndex) currentIndex + 1 else currentIndex
        pendingQueue = queue to restoredCurrentIndex.coerceIn(0, queue.lastIndex)
        pendingAddedTracks.clear()
        pendingPlayNextTracks.clear()
        _snapshot.value = _snapshot.value.copy(queue = queue)
    }

    override fun clearQueue() {
        queuedTracks = emptyList()
        pendingQueue = null
        pendingAddedTracks.clear()
        pendingPlayNextTracks.clear()
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        finishCurrentTrackAfterTimer = false
        mediaController?.clearMediaItems()
        _snapshot.value = EMPTY_SNAPSHOT
    }

    override fun addQueueItem(track: AudioTrack) {
        val controller = mediaController
        if (controller != null) controller.addMediaItem(toMediaItem(track))
        else {
            pendingAddedTracks += track
            _snapshot.value = _snapshot.value.copy(queue = _snapshot.value.queue + track)
        }
    }

    override fun playNext(tracks: List<AudioTrack>) {
        if (tracks.isEmpty()) return
        val controller = mediaController
        if (controller != null) {
            insertNext(controller, tracks)
        } else {
            val pending = pendingQueue
            if (pending != null) {
                val insertAt = (pending.second + 1).coerceIn(0, pending.first.size)
                val updated = pending.first.toMutableList().apply { addAll(insertAt, tracks) }
                pendingQueue = updated to pending.second
                _snapshot.value = _snapshot.value.copy(queue = updated)
            } else {
                pendingPlayNextTracks += tracks
                _snapshot.value = _snapshot.value.copy(queue = _snapshot.value.queue + tracks)
            }
        }
    }

    override fun setSleepTimer(durationMs: Long?, finishCurrentTrack: Boolean) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        finishCurrentTrackAfterTimer = false
        if (durationMs == null || durationMs <= 0L) {
            _snapshot.value = _snapshot.value.copy(sleepTimerRemainingMs = null, sleepTimerFinishingTrack = false)
            return
        }

        val expiresAt = SystemClock.elapsedRealtime() + durationMs
        sleepTimerJob = scope.launch {
            while (true) {
                val remaining = (expiresAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                _snapshot.value = _snapshot.value.copy(sleepTimerRemainingMs = remaining, sleepTimerFinishingTrack = false)
                if (remaining == 0L) break
                delay(minOf(1_000L, remaining))
            }
            sleepTimerJob = null
            if (finishCurrentTrack && _snapshot.value.currentTrack != null) {
                finishCurrentTrackAfterTimer = true
                _snapshot.value = _snapshot.value.copy(sleepTimerRemainingMs = null, sleepTimerFinishingTrack = true)
            } else {
                mediaController?.pause()
                _snapshot.value = _snapshot.value.copy(sleepTimerRemainingMs = null, sleepTimerFinishingTrack = false, isPlaying = false)
            }
        }
    }

    fun release() {
        if (released) return
        released = true
        positionJob?.cancel()
        mediaController?.removeListener(playerListener)
        mediaController = null
        MediaController.releaseFuture(controllerFuture)
        scope.cancel()
    }

    private fun insertNext(controller: MediaController, tracks: List<AudioTrack>) {
        if (controller.mediaItemCount == 0) {
            applyQueue(controller, tracks, 0)
            return
        }
        val insertAt = (controller.currentMediaItemIndex + 1).coerceIn(0, controller.mediaItemCount)
        tracks.forEachIndexed { offset, track -> controller.addMediaItem(insertAt + offset, toMediaItem(track)) }
    }

    private fun applyQueue(controller: MediaController, tracks: List<AudioTrack>, startIndex: Int) {
        controller.stop()
        controller.clearMediaItems()
        controller.setMediaItems(tracks.map(::toMediaItem), startIndex, C.TIME_UNSET)
        controller.prepare()
        controller.play()
        refreshSnapshot(controller, queueChanged = true)
        updatePositionPolling()
        tracks.getOrNull(startIndex)?.let(::loadSessionArtwork)
    }

    private fun toMediaItem(track: AudioTrack): MediaItem {
        val cleanedTrack = cleanTrackMetadata(track)
        val metadata = MediaMetadata.Builder()
            .setTitle(cleanedTrack.title)
            .setArtist(cleanedTrack.artist)
            .setAlbumTitle(cleanedTrack.album)
            .build()

        return MediaItem.Builder()
            .setMediaId(track.uri.toString())
            .setUri(track.uri)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun refreshSnapshot(player: Player, queueChanged: Boolean = false) {
        val queue = if (queueChanged || _snapshot.value.queue.size != player.mediaItemCount) {
            (0 until player.mediaItemCount).mapNotNull { index -> player.getMediaItemAt(index).toAudioTrack() }
        } else _snapshot.value.queue
        val currentIndex = player.currentMediaItemIndex.takeIf { it in queue.indices } ?: -1
        val currentTrack = queue.getOrNull(currentIndex)
        val duration = player.duration.takeIf { it >= 0L && it != C.TIME_UNSET } ?: currentTrack?.durationMs ?: 0L

        _snapshot.value = _snapshot.value.copy(
            queue = queue,
            currentIndex = currentIndex,
            currentTrack = currentTrack,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            playbackSpeed = player.playbackParameters.speed,
        )
    }

    private fun loadSessionArtwork(track: AudioTrack) {
        scope.launch(Dispatchers.IO) {
            val image = artworkRepository.load(track.uri) ?: return@launch
            launch(Dispatchers.Main.immediate) {
                val controller = mediaController ?: return@launch
                val index = (0 until controller.mediaItemCount).firstOrNull { controller.getMediaItemAt(it).localConfiguration?.uri == track.uri } ?: return@launch
                val item = controller.getMediaItemAt(index)
                if (item.mediaMetadata.artworkData != null) return@launch
                val metadata = item.mediaMetadata.buildUpon().setArtworkData(image.encodedBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER).build()
                controller.replaceMediaItem(index, item.buildUpon().setMediaMetadata(metadata).build())
            }
        }
    }

    private fun MediaItem.toAudioTrack(): AudioTrack? {
        val uri = localConfiguration?.uri ?: return null
        return cleanTrackMetadata(AudioTrack(
            id = mediaId.toLongOrNull() ?: uri.toString().hashCode().toLong(),
            uri = uri,
            title = mediaMetadata.title?.toString()?.takeIf(String::isNotBlank)
                ?: uri.lastPathSegment.orEmpty().ifBlank { "Untitled track" },
            artist = mediaMetadata.artist?.toString().orEmpty(),
            album = mediaMetadata.albumTitle?.toString().orEmpty(),
            durationMs = 0L,
        ))
    }

    private fun updatePositionPolling() {
        if (mediaController?.isPlaying != true) {
            positionJob?.cancel()
            positionJob = null
            return
        }
        if (positionJob?.isActive == true) return

        positionJob = scope.launch {
            while (isActive && mediaController?.isPlaying == true) {
                mediaController?.let { refreshSnapshot(it) }
                delay(POSITION_UPDATE_INTERVAL_MS)
            }
            positionJob = null
        }
    }

    private companion object {
        const val POSITION_UPDATE_INTERVAL_MS = 1_000L
        val EMPTY_SNAPSHOT = PlaybackSnapshot(
            queue = emptyList(),
            currentIndex = -1,
            currentTrack = null,
            isPlaying = false,
            positionMs = 0L,
            durationMs = 0L,
            errorMessage = null,
            shuffleEnabled = false,
            repeatMode = Player.REPEAT_MODE_OFF,
        )
    }
}
