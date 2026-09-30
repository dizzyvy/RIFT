package com.dizzyvy.sjmusicapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dizzyvy.sjmusicapp.music.library.AudioLibraryRepository
import com.dizzyvy.sjmusicapp.music.playback.PlaybackController
import com.dizzyvy.sjmusicapp.ui.library.LibraryScreen
import com.dizzyvy.sjmusicapp.ui.library.LibraryViewModel
import com.dizzyvy.sjmusicapp.ui.player.NowPlayingScreen
import com.dizzyvy.sjmusicapp.ui.theme.SJMusicTheme

@Composable
fun SJMusicApp(
    repository: AudioLibraryRepository,
    playbackController: PlaybackController,
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    val factory = remember(repository, playbackController) { LibraryViewModel.Factory(repository, playbackController) }
    val libraryViewModel: LibraryViewModel = viewModel(factory = factory)
    val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()
    val playback by playbackController.snapshot.collectAsStateWithLifecycle()
    var showPlayer by remember { mutableStateOf(false) }

    LaunchedEffect(hasAudioPermission) {
        libraryViewModel.loadLibrary(hasAudioPermission, forceRefresh = hasAudioPermission)
    }

    SJMusicTheme {
        if (showPlayer) {
            NowPlayingScreen(
                playback = playback,
                onBack = { showPlayer = false },
                onPlayPause = playbackController::playPause,
                onNext = playbackController::skipNext,
                onPrevious = playbackController::skipPrevious,
                onSeek = playbackController::seekTo,
                onPlayQueueItem = playbackController::playQueueItem,
            )
        } else {
            LibraryScreen(
                state = libraryState,
                playback = playback,
                onSearch = libraryViewModel::setSearchQuery,
                onRequestPermission = onRequestPermission,
                onRetry = { libraryViewModel.loadLibrary(hasAudioPermission, forceRefresh = true) },
                onPlayTrack = { track ->
                    libraryViewModel.playTrack(track)
                    showPlayer = true
                },
                onOpenPlayer = { showPlayer = true },
            )
        }
    }
}
