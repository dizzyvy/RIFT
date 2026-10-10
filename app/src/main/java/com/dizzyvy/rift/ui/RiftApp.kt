package com.dizzyvy.rift.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import android.provider.OpenableColumns
import android.media.MediaMetadataRetriever
import android.net.Uri
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
import com.dizzyvy.rift.music.library.AudioLibraryRepository
import com.dizzyvy.rift.music.library.PlaylistStore
import com.dizzyvy.rift.music.artwork.ArtworkRepository
import com.dizzyvy.rift.music.lyrics.LocalLyricsRepository
import com.dizzyvy.rift.music.playback.PlaybackController
import com.dizzyvy.rift.ui.library.LibraryScreen
import com.dizzyvy.rift.ui.library.LibraryViewModel
import com.dizzyvy.rift.ui.player.NowPlayingScreen
import com.dizzyvy.rift.ui.theme.RiftTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RiftApp(
    repository: AudioLibraryRepository,
    playlistStore: PlaylistStore,
    playbackController: PlaybackController,
    artworkRepository: ArtworkRepository,
    hasAudioPermission: Boolean,
    themeMode: String,
    accentName: String,
    externalAudioUri: Uri?,
    onExternalAudioHandled: () -> Unit,
    onThemeModeChange: (String) -> Unit,
    onAccentChange: (String) -> Unit,
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
    var pendingBackupText by remember { mutableStateOf("") }
    var pendingDeleteUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var continuePendingDelete by remember { mutableIntStateOf(0) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        if (uri != null) runCatching {
            val output = requireNotNull(context.contentResolver.openOutputStream(uri))
            output.bufferedWriter(Charsets.UTF_8).use { it.write(pendingExportText) }
        }.onFailure { libraryViewModel.reportActionError(it.message ?: "Could not write the playlist file.") }
    }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val queuedUris = pendingDeleteUris
        pendingDeleteUris = emptyList()
        if (result.resultCode == Activity.RESULT_OK && Build.VERSION.SDK_INT < Build.VERSION_CODES.R && queuedUris.isNotEmpty()) {
            runCatching { context.contentResolver.delete(queuedUris.first(), null, null) }
                .onSuccess {
                    pendingDeleteUris = queuedUris.drop(1)
                    continuePendingDelete += 1
                }
                .onFailure { libraryViewModel.reportActionError(it.message ?: "Could not delete this song.") }
        }
        libraryViewModel.loadLibrary(hasAudioPermission, forceRefresh = true)
    }
    fun requestDeleteUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val request = MediaStore.createDeleteRequest(context.contentResolver, uris)
                deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            }.onFailure { libraryViewModel.reportActionError(it.message ?: "Could not request permission to delete these songs.") }
            return
        }
        val remaining = uris.toMutableList()
        while (remaining.isNotEmpty()) {
            val uri = remaining.first()
            try {
                context.contentResolver.delete(uri, null, null)
                remaining.removeAt(0)
            } catch (recoverable: RecoverableSecurityException) {
                pendingDeleteUris = remaining.toList()
                deleteLauncher.launch(IntentSenderRequest.Builder(recoverable.userAction.actionIntent.intentSender).build())
                return
            } catch (exception: Exception) {
                libraryViewModel.reportActionError(exception.message ?: "Could not delete this song.")
                return
            }
        }
        libraryViewModel.loadLibrary(hasAudioPermission, forceRefresh = true)
    }
    LaunchedEffect(continuePendingDelete) {
        if (continuePendingDelete > 0 && pendingDeleteUris.isNotEmpty()) {
            val remaining = pendingDeleteUris
            pendingDeleteUris = emptyList()
            requestDeleteUris(remaining)
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
    val backupExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) refreshScope.launch(Dispatchers.IO) {
            runCatching {
                val output = requireNotNull(context.contentResolver.openOutputStream(uri))
                output.bufferedWriter(Charsets.UTF_8).use { it.write(pendingBackupText) }
            }.onFailure { libraryViewModel.reportActionError(it.message ?: "Could not write the backup file.") }
        }
    }
    val backupImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) refreshScope.launch(Dispatchers.IO) {
            runCatching {
                val contents = requireNotNull(context.contentResolver.openInputStream(uri)).bufferedReader(Charsets.UTF_8).use { reader ->
                    val output = StringBuilder()
                    val buffer = CharArray(8192)
                    while (output.length <= 16 * 1024 * 1024) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        output.append(buffer, 0, count)
                    }
                    require(output.length <= 16 * 1024 * 1024) { "Backup file is too large." }
                    output.toString()
                }
                libraryViewModel.importBackup(contents) { settings ->
                    settings["themeMode"]?.let(onThemeModeChange)
                    settings["accentName"]?.let(onAccentChange)
                }
            }.onFailure { libraryViewModel.reportActionError(it.message ?: "Could not read the backup file.") }
        }
    }
    val lyricsDirectoryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            libraryViewModel.setLyricsDirectory(uri)
        }.onFailure { libraryViewModel.reportActionError(it.message ?: "Could not access that lyrics folder.") }
    }

    LaunchedEffect(themeMode, accentName) {
        libraryViewModel.saveAppSettings(mapOf("themeMode" to themeMode, "accentName" to accentName))
    }

    LaunchedEffect(externalAudioUri) {
        val uri = externalAudioUri ?: return@LaunchedEffect
        runCatching {
            val track = withContext(Dispatchers.IO) { externalTrack(context, uri) }
            playbackController.setQueue(listOf(track), 0)
            showPlayer = true
        }.onFailure { libraryViewModel.reportActionError(it.message ?: "Could not open this audio file.") }
        onExternalAudioHandled()
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
        libraryViewModel.loadLibrary(hasAudioPermission, forceRefresh = false)
    }

    RiftTheme(mode = themeMode, accent = accentName) {
      CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
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
                onRestoreQueueItem = playbackController::restoreQueueItem,
                onMoveQueueItem = playbackController::moveQueueItem,
                playlists = libraryState.playlists,
                onAddTrackToPlaylist = libraryViewModel::addTrackToPlaylist,
                onCreatePlaylist = { name, tracks -> libraryViewModel.createPlaylist(name, tracks) },
                onClearQueue = playbackController::clearQueue,
                isFavorite = playback.currentTrack?.uri?.toString() in libraryState.favoriteUris,
                onFavorite = libraryViewModel::setFavorite,
                onSetSleepTimer = playbackController::setSleepTimer,
                onPlaybackSpeed = playbackController::setPlaybackSpeed,
                lyricsRepository = remember(context) { LocalLyricsRepository(context) },
                lyricsDirectoryUri = libraryState.lyricsTreeUri?.let(Uri::parse),
                onChooseLyricsDirectory = { lyricsDirectoryLauncher.launch(null) },
                clickWheelSensitivity = libraryState.clickWheelSensitivity,
                clickWheelHaptics = libraryState.clickWheelHaptics,
                onClickWheelSensitivityChange = libraryViewModel::setClickWheelSensitivity,
                onClickWheelHapticsChange = libraryViewModel::setClickWheelHaptics,
            )
        } else {
            LibraryScreen(
                state = libraryState,
                playback = playback,
                artworkRepository = artworkRepository,
                onSearch = libraryViewModel::setSearchQuery,
                onFavorite = libraryViewModel::setFavorite,
                onSortOrder = libraryViewModel::setSortOrder,
                onSortAscending = libraryViewModel::setSortAscending,
                onHideShortTracks = libraryViewModel::setHideShortTracks,
                onRequestPermission = onRequestPermission,
                onRetry = { libraryViewModel.loadLibrary(hasAudioPermission, forceRefresh = true) },
                onPlayTrack = { track ->
                    libraryViewModel.playTrack(track)
                    showPlayer = true
                },
                onOpenPlayer = { showPlayer = true },
                onPlayPause = playbackController::playPause,
                onShuffleAll = libraryViewModel::shuffleAll,
                onPreviousTrack = playbackController::skipPrevious,
                onNextTrack = playbackController::skipNext,
                onAddToQueue = playbackController::addQueueItem,
                onPlayNext = playbackController::playNext,
                onDeleteTrack = { track -> requestDeleteUris(listOf(track.uri)) },
                onDeleteTracks = { tracks -> requestDeleteUris(tracks.map { it.uri }) },
                onNotDuplicate = { uris -> libraryViewModel.setTracksNotDuplicate(uris, true) },
                onClearActionMessage = libraryViewModel::clearActionMessage,
                onCategory = libraryViewModel::selectCategory,
                onOpenArtist = libraryViewModel::openArtist,
                onMergeArtistAlias = libraryViewModel::setArtistAlias,
                onRemoveArtistAlias = libraryViewModel::removeArtistAlias,
                onOpenAlbum = libraryViewModel::openAlbum,
                onOpenCollection = libraryViewModel::openCollection,
                onOpenPlaylist = libraryViewModel::openPlaylist,
                onCreatePlaylist = libraryViewModel::createPlaylist,
                onRenamePlaylist = libraryViewModel::renamePlaylist,
                onDeletePlaylist = libraryViewModel::deletePlaylist,
                onImportM3u = { importLauncher.launch(arrayOf("audio/x-mpegurl", "application/vnd.apple.mpegurl", "text/plain")) },
                onClearPlaylistImportReport = libraryViewModel::clearPlaylistImportReport,
                onExportM3u = { playlist -> libraryViewModel.exportM3u(playlist) { contents -> pendingExportText = contents; exportLauncher.launch(playlist.name + ".m3u") } },
                onImportBackup = { backupImportLauncher.launch(arrayOf("application/json", "text/plain")) },
                onExportBackup = { libraryViewModel.exportBackup { contents -> pendingBackupText = contents; backupExportLauncher.launch("RIFT-Backup.json") } },
                onAddTrackToPlaylist = libraryViewModel::addTrackToPlaylist,
                onAddTracksToPlaylist = libraryViewModel::addTracksToPlaylist,
                onPlayPlaylist = libraryViewModel::playPlaylist,
                onRemoveTrackFromPlaylist = libraryViewModel::removeTrackFromPlaylist,
                onMovePlaylistTrack = libraryViewModel::movePlaylistTrack,
                onBackFromGroup = libraryViewModel::closeGroup,
                themeMode = themeMode,
                accentName = accentName,
                onThemeModeChange = onThemeModeChange,
                onAccentChange = onAccentChange,
                onToggleFolderHidden = { path, hidden, onComplete ->
                    libraryViewModel.setFolderHidden(path, hidden, onComplete)
                },
            )
        }
      }
      }
    }
}

private fun externalTrack(context: android.content.Context, uri: Uri): com.dizzyvy.rift.music.model.AudioTrack {
    var name = uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank) ?: "Audio file"
    var size = 0L
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameColumn >= 0) name = cursor.getString(nameColumn)?.takeIf(String::isNotBlank) ?: name
                if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) size = cursor.getLong(sizeColumn)
            }
        }
    }
    var title = name.substringBeforeLast('.', name)
    var artist = ""
    var album = ""
    var durationMs = 0L
    val retriever = MediaMetadataRetriever()
    runCatching {
        retriever.setDataSource(context, uri)
        title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf(String::isNotBlank) ?: title
        artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty()
        album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
        durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    }
    runCatching { retriever.release() }
    return com.dizzyvy.rift.music.model.AudioTrack(
        id = uri.toString().hashCode().toLong(),
        uri = uri,
        title = title,
        displayName = name,
        artist = artist,
        album = album,
        durationMs = durationMs,
        sizeBytes = size,
        mimeType = context.contentResolver.getType(uri).orEmpty(),
    )
}
