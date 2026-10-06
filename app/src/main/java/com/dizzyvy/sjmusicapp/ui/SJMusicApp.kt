package com.dizzyvy.sjmusicapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
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

@Composable
fun SJMusicApp(
    repository: AudioLibraryRepository,
    playlistStore: PlaylistStore,
    playbackController: PlaybackController,
    artworkRepository: ArtworkRepository,
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    val factory = remember(repository, playbackController) { LibraryViewModel.Factory(repository, playlistStore, playbackController) }
    val libraryViewModel: LibraryViewModel = viewModel(factory = factory)
    val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()
    val playback by playbackController.snapshot.collectAsStateWithLifecycle()
    var showPlayer by remember { mutableStateOf(false) }

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
            )
        } else {
            LibraryScreen(
                state = libraryState,
                playback = playback,
                artworkRepository = artworkRepository,
                onSearch = libraryViewModel::setSearchQuery,
                onRequestPermission = onRequestPermission,
                onRetry = { libraryViewModel.loadLibrary(hasAudioPermission, forceRefresh = true) },
                onPlayTrack = { track ->
                    libraryViewModel.playTrack(track)
                    showPlayer = true
                },
                onOpenPlayer = { showPlayer = true },
                onPlayPause = playbackController::playPause,
                onAddToQueue = playbackController::addQueueItem,
                onCategory = libraryViewModel::selectCategory,
                onOpenArtist = libraryViewModel::openArtist,
                onOpenAlbum = libraryViewModel::openAlbum,
                onOpenPlaylist = libraryViewModel::openPlaylist,
                onCreatePlaylist = libraryViewModel::createPlaylist,
                onRenamePlaylist = libraryViewModel::renamePlaylist,
                onDeletePlaylist = libraryViewModel::deletePlaylist,
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
