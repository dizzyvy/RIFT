package com.dizzyvy.sjmusicapp.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.provider.OpenableColumns
import android.app.Activity
import android.app.RecoverableSecurityException
import androidx.activity.result.IntentSenderRequest
import android.provider.MediaStore
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.Build
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dizzyvy.sjmusicapp.music.library.AudioLibraryRepository
import com.dizzyvy.sjmusicapp.music.library.PlaylistStore
import com.dizzyvy.sjmusicapp.music.artwork.ArtworkRepository
import com.dizzyvy.sjmusicapp.music.playback.PlaybackController
import com.dizzyvy.sjmusicapp.ui.library.LibraryScreen
import com.dizzyvy.sjmusicapp.ui.library.LibraryViewModel
import com.dizzyvy.sjmusicapp.ui.player.NowPlayingScreen
import com.dizzyvy.sjmusicapp.ui.theme.SJMusicTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SJMusicApp(
    repository: AudioLibraryRepository,
    playlistStore: PlaylistStore,
    playbackController: PlaybackController,
    artworkRepository: ArtworkRepository,
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    val factory = remember(repository, playlistStore, playbackController) { LibraryViewModel.Factory(repository, playlistStore, playbackController) }
    val libraryViewModel: LibraryViewModel = viewModel(factory = factory)
    val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()
    val playback by playbackController.snapshot.collectAsStateWithLifecycle()
    var showPlayer by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val refreshScope = rememberCoroutineScope()
    var pendingExportText by remember { mutableStateOf("") }
    var pendingDeleteUri by remember { mutableStateOf<Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        if (uri != null) runCatching {
            val output = requireNotNull(context.contentResolver.openOutputStream(uri))
            output.bufferedWriter(Charsets.UTF_8).use { it.write(pendingExportText) }
        }.onFailure { libraryViewModel.reportActionError(it.message ?: "Could not write the playlist file.") }
    }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val uriToDelete = pendingDeleteUri
        pendingDeleteUri = null
        if (result.resultCode == Activity.RESULT_OK) {
            runCatching { uriToDelete?.let { context.contentResolver.delete(it, null, null) } }
                .onFailure { libraryViewModel.reportActionError(it.message ?: "Could not delete this song.") }
            libraryViewModel.loadLibrary(hasAudioPermission, forceRefresh = true)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val contents = requireNotNull(context.contentResolver.openInputStream(uri)).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "Imported playlist.m3u"
            libraryViewModel.importM3u(name, contents)
        }.onFailure { libraryViewModel.reportActionError(it.message ?: "Could not read the playlist file.") }
    }


    DisposableEffect(hasAudioPermission, context, libraryViewModel) {
        if (!hasAudioPermission) {
            onDispose { }
        } else {
            var refreshJob: Job? = null
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
                    refreshJob?.cancel()
                    refreshJob = refreshScope.launch {
                        delay(400)
                        libraryViewModel.loadLibrary(hasAudioPermission = true, forceRefresh = true)
                    }
                }
            }
            val audioUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.getExternalVolumeNames(context).map { volume -> MediaStore.Audio.Media.getContentUri(volume) }
            } else {
                listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
            }
            audioUris.forEach { context.contentResolver.registerContentObserver(it, true, observer) }
            onDispose {
                refreshJob?.cancel()
                context.contentResolver.unregisterContentObserver(observer)
            }
        }
    }

    LaunchedEffect(hasAudioPermission) {
        libraryViewModel.loadLibrary(hasAudioPermission, forceRefresh = hasAudioPermission)
    }

    SJMusicTheme {
      androidx.compose.foundation.layout.Box(Modifier.safeDrawingPadding()) {
        if (showPlayer) {
            NowPlayingScreen(
                playback = playback,
                artworkRepository = artworkRepository,
                onBack = { showPlayer = false },
                onPlayPause = playbackController::playPause,
                onNext = playbackController::skipNext,
                onPrevious = playbackController::skipPrevious,
                onSeek = playbackController::seekTo,
                onPlayQueueItem = playbackController::playQueueItem,
                onShuffle = playbackController::setShuffleEnabled,
                onRepeat = playbackController::setRepeatMode,
                onRemoveQueueItem = playbackController::removeQueueItem,
                onMoveQueueItem = playbackController::moveQueueItem,
                playlists = libraryState.playlists,
                onAddTrackToPlaylist = libraryViewModel::addTrackToPlaylist,
                onCreatePlaylist = { name, tracks -> libraryViewModel.createPlaylist(name, tracks) },
                onClearQueue = playbackController::clearQueue,
                isFavorite = playback.currentTrack?.uri?.toString() in libraryState.favoriteUris,
                onFavorite = libraryViewModel::setFavorite,
                onSetSleepTimer = playbackController::setSleepTimer,
                onPlaybackSpeed = playbackController::setPlaybackSpeed,
            )
        } else {
            LibraryScreen(
                state = libraryState,
                playback = playback,
                artworkRepository = artworkRepository,
                onSearch = libraryViewModel::setSearchQuery,
                onFavorite = libraryViewModel::setFavorite,
                onSortOrder = libraryViewModel::setSortOrder,
                onHideShortTracks = libraryViewModel::setHideShortTracks,
                onRequestPermission = onRequestPermission,
                onRetry = { libraryViewModel.loadLibrary(hasAudioPermission, forceRefresh = true) },
                onPlayTrack = { track ->
                    libraryViewModel.playTrack(track)
                    showPlayer = true
                },
                onOpenPlayer = { showPlayer = true },
                onPlayPause = playbackController::playPause,
                onAddToQueue = playbackController::addQueueItem,
                onPlayNext = playbackController::playNext,
                onDeleteTrack = { track ->
                    runCatching {
                        when {
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                                val request = MediaStore.createDeleteRequest(context.contentResolver, listOf(track.uri))
                                deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                            }
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                                try {
                                    context.contentResolver.delete(track.uri, null, null)
                                    libraryViewModel.loadLibrary(hasAudioPermission, forceRefresh = true)
                                } catch (recoverable: RecoverableSecurityException) {
                                    pendingDeleteUri = track.uri
                                    deleteLauncher.launch(IntentSenderRequest.Builder(recoverable.userAction.actionIntent.intentSender).build())
                                }
                            }
                            else -> {
                                context.contentResolver.delete(track.uri, null, null)
                                libraryViewModel.loadLibrary(hasAudioPermission, forceRefresh = true)
                            }
                        }
                    }.onFailure { libraryViewModel.reportActionError(it.message ?: "Could not delete this song.") }
                },
                onCategory = libraryViewModel::selectCategory,
                onOpenArtist = libraryViewModel::openArtist,
                onOpenAlbum = libraryViewModel::openAlbum,
                onOpenPlaylist = libraryViewModel::openPlaylist,
                onCreatePlaylist = libraryViewModel::createPlaylist,
                onRenamePlaylist = libraryViewModel::renamePlaylist,
                onDeletePlaylist = libraryViewModel::deletePlaylist,
                onImportM3u = { importLauncher.launch(arrayOf("audio/x-mpegurl", "application/vnd.apple.mpegurl", "text/plain")) },
                onExportM3u = { playlist -> libraryViewModel.exportM3u(playlist) { contents -> pendingExportText = contents; exportLauncher.launch(playlist.name + ".m3u") } },
                onAddTrackToPlaylist = libraryViewModel::addTrackToPlaylist,
                onAddTracksToPlaylist = libraryViewModel::addTracksToPlaylist,
                onPlayPlaylist = libraryViewModel::playPlaylist,
                onRemoveTrackFromPlaylist = libraryViewModel::removeTrackFromPlaylist,
                onMovePlaylistTrack = libraryViewModel::movePlaylistTrack,
                onBackFromGroup = libraryViewModel::closeGroup,
            )
        }
      }
    }
}
