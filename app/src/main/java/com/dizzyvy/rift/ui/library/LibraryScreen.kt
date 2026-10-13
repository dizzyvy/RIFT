package com.dizzyvy.rift.ui.library

import android.content.ClipData
import android.content.Intent
import android.media.RingtoneManager
import android.view.HapticFeedbackConstants
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import com.dizzyvy.rift.music.artwork.ArtworkRepository
import com.dizzyvy.rift.R
import com.dizzyvy.rift.ui.theme.RiftPalette
import com.dizzyvy.rift.music.library.AlbumBrowseItem
import com.dizzyvy.rift.music.library.ArtistBrowseItem
import com.dizzyvy.rift.music.library.DevicePlaylist
import com.dizzyvy.rift.music.library.LibraryCollectionItem
import com.dizzyvy.rift.music.library.librarySection
import com.dizzyvy.rift.music.library.isValidLibraryYear
import com.dizzyvy.rift.music.library.artistGroupKeys
import com.dizzyvy.rift.music.library.albumGroupKey
import com.dizzyvy.rift.music.model.AudioTrack
import com.dizzyvy.rift.music.playback.PlaybackSnapshot
import com.dizzyvy.rift.ui.components.AlbumArtwork
import com.dizzyvy.rift.ui.format.formatTime
import com.dizzyvy.rift.ui.theme.RiftOutlinedButton as OutlinedButton
import com.dizzyvy.rift.ui.theme.RiftTextButton as TextButton
import com.dizzyvy.rift.ui.theme.RiftBackgroundBrush
import com.dizzyvy.rift.ui.theme.ShrikhandHeading
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LibraryScreen(
    state: LibraryUiState,
    playback: PlaybackSnapshot,
    artworkRepository: ArtworkRepository,
    onSearch: (String) -> Unit,
    onFavorite: (AudioTrack, Boolean) -> Unit,
    onSortOrder: (String) -> Unit,
    onSortAscending: (Boolean) -> Unit,
    onHideShortTracks: (Boolean) -> Unit,
    onHideLongTracks: (Boolean) -> Unit,
    onHideLongTracksAfterMinutes: (Int) -> Unit,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
    onPlayTrack: (AudioTrack) -> Unit,
    onAddToQueue: (AudioTrack) -> Unit,
    onPlayNext: (List<AudioTrack>) -> Unit,
    onDeleteTrack: (AudioTrack) -> Unit,
    onDeleteTracks: (List<AudioTrack>) -> Unit,
    onNotDuplicate: (List<Uri>) -> Unit,
    onClearActionMessage: () -> Unit,
    onClearBackupRestoreReport: () -> Unit,
    onOpenPlayer: () -> Unit,
    onPlayPause: () -> Unit,
    onShuffleAll: () -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onCategory: (String) -> Unit,
    onOpenArtist: (ArtistBrowseItem) -> Unit,
    onMergeArtistAlias: (String, String) -> Unit,
    onRemoveArtistAlias: (String) -> Unit,
    onOpenAlbum: (AlbumBrowseItem) -> Unit,
    onOpenCollection: (LibraryCollectionItem, String) -> Unit,
    onOpenPlaylist: (DevicePlaylist) -> Unit,
    onCreatePlaylist: (String, List<AudioTrack>, (Boolean) -> Unit) -> Unit,
    onRenamePlaylist: (DevicePlaylist, String) -> Unit,
    onDeletePlaylist: (DevicePlaylist) -> Unit,
    onImportM3u: () -> Unit,
    onClearPlaylistImportReport: () -> Unit,
    onExportM3u: (DevicePlaylist) -> Unit,
    onImportBackup: () -> Unit,
    onExportBackup: () -> Unit,
    onAddTrackToPlaylist: (DevicePlaylist, AudioTrack) -> Unit,
    onAddTracksToPlaylist: (DevicePlaylist, List<AudioTrack>, (Boolean) -> Unit) -> Unit,
    onPlayBrowseTracks: (Boolean) -> Unit,
    onRemoveTrackFromPlaylist: (AudioTrack) -> Unit,
    onMovePlaylistTrack: (Int, Int) -> Unit,
    onBackFromGroup: () -> Unit,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    clickWheelSensitivity: Float,
    clickWheelHaptics: Boolean,
    onClickWheelSensitivityChange: (Float) -> Unit,
    onClickWheelHapticsChange: (Boolean) -> Unit,
    lyricsDirectoryUri: Uri?,
    onChooseLyricsDirectory: () -> Unit,
    onToggleFolderHidden: (String, Boolean, () -> Unit) -> Unit,
    onEnsureDuplicatesLoaded: () -> Unit,
    onOpenDownloader: () -> Unit,
) {
    val listStateHolder = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    val tabScrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    var searchExpanded by remember { mutableStateOf(state.searchQuery.isNotBlank()) }
    var settingsSheetOpen by remember { mutableStateOf(false) }
    var filtersSheetOpen by remember { mutableStateOf(false) }
    var longTrackMenuOpen by remember { mutableStateOf(false) }
    var playlistActionsMenuOpen by remember { mutableStateOf(false) }
    var tabViewportBounds by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    val tabBounds = remember { mutableStateMapOf<String, Pair<Float, Float>>() }
    var pendingTracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var selectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteSelectionConfirmation by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var tracksToAddOnCreate by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var tracksRequiredOnCreate by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<DevicePlaylist?>(null) }
    var playlistToDelete by remember { mutableStateOf<DevicePlaylist?>(null) }
    var playlistNameInput by remember { mutableStateOf("") }
    var trackToDelete by remember { mutableStateOf<AudioTrack?>(null) }
    var folderToHide by remember { mutableStateOf<String?>(null) }
    var showingHiddenFolders by remember { mutableStateOf(false) }
    var pendingDuplicateAction by remember { mutableStateOf<PendingDuplicateAction?>(null) }
    var pendingBulkDuplicateDelete by remember { mutableStateOf<List<AudioTrack>?>(null) }
    var artistToMerge by remember { mutableStateOf<ArtistBrowseItem?>(null) }
    var artistAliasInput by remember { mutableStateOf("") }
    val visibleCategoryTabs = visibleLibraryCategoryTabs(state.searchQuery)
    val artistAliasesGrouped = remember(state.artistAliases) { groupArtistAliasesByGroup(state.artistAliases) }
    val latestCategory = rememberUpdatedState(state.category)
    val latestBrowseTitle = rememberUpdatedState(state.browseTitle)
    val latestOnCategory = rememberUpdatedState(onCategory)
    val songs = state.browseTracks ?: state.visibleTracks
    val libraryActions = LibraryActions(
        playTrack = onPlayTrack,
        favorite = onFavorite,
        playNext = onPlayNext,
        addToQueue = onAddToQueue,
        deleteTrack = { trackToDelete = it },
        openArtist = onOpenArtist,
        openAlbum = onOpenAlbum,
        openCollection = onOpenCollection,
        openPlaylist = onOpenPlaylist,
        renamePlaylist = onRenamePlaylist,
        deletePlaylist = onDeletePlaylist,
        exportM3u = onExportM3u,
    )
    val playbackActions = PlaybackActions(
        shuffleAll = onShuffleAll,
        openPlayer = onOpenPlayer,
        playPause = onPlayPause,
        previousTrack = onPreviousTrack,
        nextTrack = onNextTrack,
    )
    val selectedTracks = remember(songs, selectedUris) { selectedTracksInLibraryOrder(songs, selectedUris) }
    BackHandler(enabled = selectedUris.isNotEmpty() || state.searchQuery.isNotEmpty()) {
        if (selectedUris.isNotEmpty()) {
            selectedUris = emptySet()
        } else {
            onSearch("")
            focusManager.clearFocus()
        }
    }
    LaunchedEffect(state.category, state.searchQuery.isBlank()) {
        val bounds = snapshotFlow { tabBounds[state.category] to tabViewportBounds }
            .first { (tab, viewport) -> tab != null && viewport != null }
        val (tabLeft, tabRight) = requireNotNull(bounds.first)
        val (viewportLeft, viewportRight) = requireNotNull(bounds.second)
        val delta = when {
            tabLeft < viewportLeft -> tabLeft - viewportLeft
            tabRight > viewportRight -> tabRight - viewportRight
            else -> 0f
        }
        if (delta != 0f) tabScrollState.scrollTo(
            (tabScrollState.value + delta.toInt()).coerceIn(0, tabScrollState.maxValue),
        )
    }
    LaunchedEffect(state.category, state.browseTitle) { selectedUris = emptySet() }
    LaunchedEffect(state.category) {
        focusManager.clearFocus()
        keyboardController?.hide()
    }
    LaunchedEffect(state.searchQuery.isNotBlank()) {
        if (state.searchQuery.isNotBlank()) searchExpanded = true
    }
    LaunchedEffect(state.category) { if (state.category != "Folders") showingHiddenFolders = false }
    LaunchedEffect(state.actionMessage) {
        val message = state.actionMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onClearActionMessage()
    }
    Column(
        Modifier.fillMaxSize()
            .background(RiftBackgroundBrush())
            .padding(horizontal = 18.dp),
    ) {
        if (state.browseTitle != null) {
            TextButton(onClick = onBackFromGroup, modifier = Modifier.padding(top = 2.dp)) { Text("‹  ${state.category.uppercase()}") }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                songs.firstOrNull()?.let { AlbumTile(it, artworkRepository, Modifier.size(104.dp)) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    ShrikhandHeading(
                        state.browseTitle,
                        MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val duration = remember(songs) { songs.sumOf { it.durationMs.coerceAtLeast(0L) } }
                    val albumDetails = remember(songs, state.category) {
                        if (state.category == "Albums") {
                            val representative = songs.firstOrNull()
                            val year = songs.asSequence().map { it.year }.filter(::isValidLibraryYear).maxOrNull()
                            val artist = representative?.let {
                                displayValue(it.albumArtist.ifBlank { it.artist }, "Unknown artist")
                            }.orEmpty()
                            listOfNotNull(year?.toString(), artist.takeIf(String::isNotBlank)).joinToString(" · ")
                                .takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()
                        } else ""
                    }
                    Text(
                        "${countLabel(songs.size, "song")} · ${formatTime(duration)}$albumDetails",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (songs.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onPlayBrowseTracks(false) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Play") }
                    OutlinedButton(onClick = { onPlayBrowseTracks(true) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Shuffle") }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).heightIn(min = 56.dp), contentAlignment = Alignment.CenterStart) {
                    Image(
                        painter = painterResource(R.drawable.rift_logo),
                        contentDescription = "RIFT logo",
                        modifier = Modifier
                            .size(width = 92.dp, height = 56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(RiftPalette.lightSurface)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
                IconButton(
                    onClick = { searchExpanded = true },
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Search library" },
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(
                    onClick = { settingsSheetOpen = true; onEnsureDuplicatesLoaded() },
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Settings" },
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        if (state.browseTitle == null) {
            Box(
                Modifier.fillMaxWidth().onGloballyPositioned { coordinates ->
                    val left = coordinates.positionInRoot().x
                    tabViewportBounds = left to (left + coordinates.size.width)
                },
            ) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(tabScrollState),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    visibleCategoryTabs.forEach { category ->
                        val tab = if (category == "Search") "All" else category
                        val selected = state.category == category
                        val tabModifier = Modifier
                            .onGloballyPositioned { coordinates ->
                                val left = coordinates.positionInRoot().x
                                tabBounds[category] = left to (left + coordinates.size.width)
                            }
                            .graphicsLayer {
                                val bounds = tabBounds[category]
                                val viewport = tabViewportBounds
                                alpha = if (selected || bounds == null || viewport == null) 1f else {
                                    val fadeWidth = 36.dp.toPx()
                                    val center = (bounds.first + bounds.second) / 2f
                                    minOf(
                                        ((center - viewport.first) / fadeWidth).coerceIn(0.25f, 1f),
                                        ((viewport.second - center) / fadeWidth).coerceIn(0.25f, 1f),
                                    )
                                }
                            }
                        FilterChip(
                            selected = selected,
                            onClick = { onCategory(category) },
                            label = { Text(tab, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = tabModifier,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
            }
            if (selectedTracks.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("${selectedTracks.size} selected", style = MaterialTheme.typography.labelLarge)
                    TextButton(
                        onClick = {
                            selectedUris = if (selectedTracks.size == songs.size) emptySet()
                            else songs.mapTo(mutableSetOf()) { it.uri.toString() }
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(if (selectedTracks.size == songs.size) "Deselect all" else "Select all") }
                    TextButton(
                        onClick = { onPlayNext(selectedTracks); selectedUris = emptySet() },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Play next") }
                    TextButton(
                        onClick = { selectedTracks.forEach(onAddToQueue); selectedUris = emptySet() },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Add to queue") }
                    TextButton(
                        onClick = { pendingTracks = selectedTracks; showAddSheet = true },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Add to playlist") }
                    TextButton(
                        onClick = {
                            tracksToAddOnCreate = selectedTracks
                            tracksRequiredOnCreate = true
                            playlistNameInput = ""
                            showCreateDialog = true
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Create playlist") }
                    TextButton(
                        onClick = { showDeleteSelectionConfirmation = true },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Delete") }
                    IconButton(
                        onClick = { selectedUris = emptySet() },
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "Exit selection mode" },
                    ) { Icon(Icons.Default.Close, contentDescription = null) }
                }
            }
            if (state.category in listOf("Songs", "Artists", "Albums", "Playlists", "Folders", "Genres", "Years", "Duplicates", "Search") &&
                (searchExpanded || state.searchQuery.isNotBlank())
            ) {
                val placeholder = when (state.category) {
                    "Artists" -> "Search artists"
                    "Albums" -> "Search albums or artists"
                    "Playlists" -> "Search playlists"
                    "Folders" -> if (showingHiddenFolders) "Search hidden folders" else "Search folders"
                    "Genres" -> "Search genres"
                    "Years" -> "Search years"
                    "Duplicates" -> "Search duplicate tracks"
                    "Search" -> "Search songs, artists, albums or playlists"
                    else -> "Search songs, artists or albums"
                }
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearch,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(15.dp),
                    placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (state.searchQuery.isNotBlank()) onSearch("")
                                else searchExpanded = false
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            },
                            modifier = Modifier.size(48.dp).semantics { contentDescription = if (state.searchQuery.isNotBlank()) "Clear search" else "Close search" },
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
                if (state.category == "Playlists") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                tracksToAddOnCreate = emptyList()
                                tracksRequiredOnCreate = false
                                playlistNameInput = ""
                                showCreateDialog = true
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { Text("New playlist") }
                        Box {
                            IconButton(
                                onClick = { playlistActionsMenuOpen = true },
                                modifier = Modifier.size(48.dp).semantics { contentDescription = "Playlist actions" },
                            ) { Icon(Icons.Default.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                            DropdownMenu(
                                expanded = playlistActionsMenuOpen,
                                onDismissRequest = { playlistActionsMenuOpen = false },
                                containerColor = MaterialTheme.colorScheme.surface,
                            ) {
                                DropdownMenuItem(text = { Text("Import M3U") }, onClick = { playlistActionsMenuOpen = false; onImportM3u() })
                                DropdownMenuItem(text = { Text("Back up playlists") }, onClick = { playlistActionsMenuOpen = false; onExportBackup() })
                                DropdownMenuItem(text = { Text("Restore from backup") }, onClick = { playlistActionsMenuOpen = false; onImportBackup() })
                            }
                        }
                    }
                }
            }
        }
        if (state.isLoading && (state.scanTotal != null || state.tracks.isNotEmpty())) {
            val total = state.scanTotal?.coerceAtLeast(state.scanProcessed)
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    total?.let { "Updating library · ${state.scanProcessed} of $it" } ?: "Updating library",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                if (total != null) {
                    LinearProgressIndicator(
                        progress = if (total > 0) (state.scanProcessed.toFloat() / total).coerceIn(0f, 1f) else 0f,
                        modifier = Modifier.width(72.dp),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.width(72.dp))
                }
            }
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().pointerInput(visibleCategoryTabs) {
                var horizontalDrag = 0f
                val switchThreshold = 80.dp.toPx()
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val drag = horizontalDrag
                        horizontalDrag = 0f
                        if (latestBrowseTitle.value == null && kotlin.math.abs(drag) >= switchThreshold) {
                            adjacentCategoryForSwipe(
                                visibleCategoryTabs,
                                latestCategory.value,
                                swipeRight = drag > 0f,
                            )?.let(latestOnCategory.value)
                        }
                    },
                    onDragCancel = { horizontalDrag = 0f },
                ) { _, dragAmount ->
                    horizontalDrag += dragAmount
                }
            },
        ) {
            val listStateKey = state.browseTitle?.let { title ->
                val detailIdentity = state.activePlaylist?.let { "${it.volumeName}:${it.id}" }
                    ?: state.browseTracks?.firstOrNull()?.uri?.toString()
                    ?: title
                "browse:${state.category}:$detailIdentity"
            } ?: state.category
            listStateHolder.SaveableStateProvider(
                key = listStateKey,
            ) {
                val listState = rememberLazyListState()
                LaunchedEffect(listState) {
                    snapshotFlow { listState.isScrollInProgress }
                        .collect { scrolling ->
                            if (scrolling) {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        }
                }
                when {
            state.permissionRequired -> EmptyPanel(Icons.Default.PlayArrow, "Let your music in", "RIFT scans audio stored on your phone and SD card. Enable Audio and music in App info if Android no longer shows the prompt.", "Set up audio access", onRequestPermission)
            state.isLoading && state.tracks.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.message != null -> EmptyPanel(Icons.Default.Warning, "Library unavailable", state.message, "Scan again", onRetry)
            else -> {
                val hasRows = if (state.browseTitle != null) !state.browseTracks.isNullOrEmpty() else when (state.category) {
                    "Artists" -> state.visibleArtists.isNotEmpty()
                    "Albums" -> state.visibleAlbums.isNotEmpty()
                    "Playlists" -> state.visiblePlaylists.isNotEmpty()
                    "Folders" -> if (showingHiddenFolders) state.visibleHiddenFolders.isNotEmpty() else state.visibleFolders.isNotEmpty()
                    "Genres" -> state.visibleGenres.isNotEmpty()
                    "Years" -> state.visibleYears.isNotEmpty()
                    "Duplicates" -> state.visibleDuplicateGroups.isNotEmpty()
                    "Search" -> state.visibleTracks.isNotEmpty() || state.visibleArtists.isNotEmpty() || state.visibleAlbums.isNotEmpty() || state.visiblePlaylists.isNotEmpty()
                    else -> songs.isNotEmpty()
                }
                if (!hasRows) EmptyPanel(
                    Icons.Default.PlayArrow,
                    if (state.browseTitle != null) "No tracks found"
                    else if (state.category == "Duplicates") "No duplicates found"
                    else if (state.category == "Folders" && showingHiddenFolders) "No hidden folders"
                    else if (state.category == "Playlists") "No playlists yet"
                    else if (state.category == "Search") "No search results"
                    else "No music found",
                    if (state.browseTitle != null) "This collection has no available tracks."
                    else if (state.category == "Duplicates") "No matching duplicate groups are in your library."
                    else if (state.category == "Folders" && showingHiddenFolders) "Folders you hide from the library will appear here."
                    else if (state.category == "Playlists") "Create your first playlist to keep songs together."
                    else if (state.category == "Search") "Try a different search or select another filter."
                    else "Add audio files to your phone or SD card, then scan again.",
                    if (state.browseTitle == null && state.category == "Playlists") "Create" else if (state.browseTitle == null && state.category != "Search" && state.category != "Duplicates" && !(state.category == "Folders" && showingHiddenFolders)) "Scan again" else null,
                    if (state.browseTitle == null && state.category == "Playlists") ({
                        tracksToAddOnCreate = emptyList()
                        tracksRequiredOnCreate = false
                        playlistNameInput = ""
                        showCreateDialog = true
                    })
                    else if (state.browseTitle == null && state.category != "Search") onRetry else null,
                )
                else PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = onRetry, modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                    when {
                        state.browseTitle == null && state.category == "Search" -> SearchResults(
                            listState = listState,
                            tracks = state.visibleTracks,
                            artists = state.visibleArtists,
                            albums = state.visibleAlbums,
                            playlists = state.visiblePlaylists,
                            artworkRepository = artworkRepository,
                            onPlayTrack = onPlayTrack,
                            onOpenArtist = onOpenArtist,
                            onOpenAlbum = onOpenAlbum,
                            onOpenPlaylist = onOpenPlaylist,
                            onRequestRename = { playlist -> playlistToRename = playlist; playlistNameInput = playlist.name },
                            onRequestDelete = { playlist -> playlistToDelete = playlist },
                            onExportM3u = onExportM3u,
                        )
                        state.browseTitle != null -> {
                            LazyColumn(state = listState, contentPadding = PaddingValues(end = 26.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                itemsIndexed(songs, key = { _, track -> track.uri.toString() }) { index, track ->
                                    TrackRow(
                                        track = track,
                                        artworkRepository = artworkRepository,
                                        playing = playback.currentTrack?.uri == track.uri && playback.isPlaying,
                                        selected = track.uri.toString() in selectedUris,
                                        selectionMode = selectedUris.isNotEmpty(),
                                        onLongPress = { selectedUris = selectedUris + track.uri.toString() },
                                        onClick = {
                                            if (selectedUris.isNotEmpty()) selectedUris = if (track.uri.toString() in selectedUris) selectedUris - track.uri.toString() else selectedUris + track.uri.toString()
                                            else onPlayTrack(track)
                                        },
                                        onAdd = { pendingTracks = listOf(track); showAddSheet = true },
                                        isFavorite = track.uri.toString() in state.favoriteUris,
                                        onFavorite = { onFavorite(track, track.uri.toString() !in state.favoriteUris) },
                                        onPlayNext = { onPlayNext(listOf(track)) },
                                        onQueue = { onAddToQueue(track) },
                                        onGoArtist = { state.artists.firstOrNull { it.id in artistGroupKeys(track, state.artistAliases) }?.let(onOpenArtist) },
                                        onGoAlbum = { state.albums.firstOrNull { it.id == albumGroupKey(track, state.artistAliases) }?.let(onOpenAlbum) },
                                        onDelete = { trackToDelete = track },
                                        showRemove = state.activePlaylist?.isLocal == true,
                                        onRemove = { onRemoveTrackFromPlaylist(track) },
                                        showReorder = state.activePlaylist?.isLocal == true && state.activePlaylist?.isAuto != true,
                                        onMoveUp = { if (index > 0) onMovePlaylistTrack(index, index - 1) },
                                        onMoveDown = { if (index < songs.lastIndex) onMovePlaylistTrack(index, index + 1) },
                                        canMoveUp = index > 0,
                                        canMoveDown = index < songs.lastIndex,
                                        onShareFailure = { scope.launch { snackbarHostState.showSnackbar("Couldn't share this song.") } },
                                    )
                                }
                            }
                        }
                        state.category == "Artists" -> Box(Modifier.fillMaxSize()) {
                            LazyColumn(state = listState, contentPadding = PaddingValues(end = 26.dp, bottom = 12.dp)) {
                                items(state.visibleArtists, key = { it.id }) { item ->
                                    val aliasesForArtist = artistAliasesGrouped[item.id].orEmpty()
                                    var artistMenuOpen by remember(item.id) { mutableStateOf(false) }
                                    BrowseRow(
                                        item.artworkUri,
                                        item.name,
                                        countLabel(item.trackCount, "song"),
                                        artworkRepository,
                                        artistInitialFallback = true,
                                        trailingContent = {
                                            Box {
                                                IconButton(onClick = { artistMenuOpen = true }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Artist name options for ${item.name}" }) {
                                                    Icon(Icons.Default.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                                                }
                                                DropdownMenu(expanded = artistMenuOpen, onDismissRequest = { artistMenuOpen = false }) {
                                                    DropdownMenuItem(text = { Text("Merge another name into ${item.name}") }, onClick = { artistAliasInput = ""; artistToMerge = item; artistMenuOpen = false })
                                                    aliasesForArtist.forEach { alias ->
                                                        DropdownMenuItem(text = { Text("Remove alias: $alias") }, onClick = { onRemoveArtistAlias(alias); artistMenuOpen = false })
                                                    }
                                                }
                                            }
                                        },
                                    ) { onOpenArtist(item) }
                                }
                            }
                            if (state.searchQuery.isBlank()) {
                                FastScrollRail(
                                    remember(state.visibleArtists) { state.visibleArtists.map { it.name } },
                                    listState,
                                    Modifier.align(Alignment.CenterEnd),
                                )
                            }
                        }
                        state.category == "Folders" -> {
                            val folderItems = if (showingHiddenFolders) state.visibleHiddenFolders else state.visibleFolders
                            val folderTitles = remember(folderItems) { folderItems.map { it.title } }
                            Box(Modifier.fillMaxSize()) {
                                FolderList(
                                    items = folderItems,
                                    listState = listState,
                                    hidden = showingHiddenFolders,
                                    onHide = { folderToHide = it.id },
                                    onUnhide = { onToggleFolderHidden(it.id, false) {} },
                                    onOpen = { onOpenCollection(it, "Folders") },
                                )
                                if (!showingHiddenFolders && state.searchQuery.isBlank() && folderItems.size > 30) {
                                    FastScrollRail(folderTitles, listState, Modifier.align(Alignment.CenterEnd))
                                }
                            }
                        }
                        state.category in listOf("Genres", "Years") -> {
                            val collections = if (state.category == "Genres") state.visibleGenres else state.visibleYears
                            val collectionTitles = remember(collections) { collections.map { it.title } }
                            Box(Modifier.fillMaxSize()) {
                                CollectionList(
                                    items = collections,
                                    artworkRepository = artworkRepository,
                                    listState = listState,
                                    onOpen = { onOpenCollection(it, state.category) },
                                )
                                if (state.category == "Genres" && state.searchQuery.isBlank() && collections.size > 30) {
                                    FastScrollRail(collectionTitles, listState, Modifier.align(Alignment.CenterEnd))
                                }
                            }
                        }
                        state.category == "Duplicates" -> DuplicatesScreen(
                            groups = state.visibleDuplicateGroups,
                            listState = listState,
                            onAction = { pendingDuplicateAction = it },
                            onBulkDelete = { pendingBulkDuplicateDelete = it },
                        )
                        state.category == "Albums" -> Box(Modifier.fillMaxSize()) {
                            LazyColumn(state = listState, contentPadding = PaddingValues(end = 26.dp, bottom = 12.dp)) {
                                items(state.visibleAlbums, key = { it.id }) { item ->
                                    val year = item.year.takeIf(::isValidLibraryYear)?.let { " · $it" }.orEmpty()
                                    BrowseRow(
                                        item.artworkUri,
                                        item.title,
                                        "${displayValue(item.artist, "Unknown artist")}$year · ${countLabel(item.trackCount, "song")}",
                                        artworkRepository,
                                        titleMaxLines = 2,
                                    ) { onOpenAlbum(item) }
                                }
                            }
                            if (state.searchQuery.isBlank()) {
                                FastScrollRail(
                                    remember(state.visibleAlbums) { state.visibleAlbums.map { it.title } },
                                    listState,
                                    Modifier.align(Alignment.CenterEnd),
                                )
                            }
                        }
                        state.category == "Playlists" -> PlaylistsTab(
                            playlists = state.visiblePlaylists,
                            listState = listState,
                            artworkRepository = artworkRepository,
                            actions = PlaylistActions(
                                open = onOpenPlaylist,
                                rename = { playlist -> playlistToRename = playlist; playlistNameInput = playlist.name },
                                delete = { playlistToDelete = it },
                                exportM3u = onExportM3u,
                            ),
                        )
                        else -> SongsList(
                            state = state,
                            songs = songs,
                            listState = listState,
                            playback = playback,
                            artworkRepository = artworkRepository,
                            selectedUris = selectedUris,
                            onSelectionChange = { selectedUris = it },
                            onFiltersClick = { filtersSheetOpen = true },
                            onAddTracksToPlaylist = { tracks -> pendingTracks = tracks; showAddSheet = true },
                            onShareFailure = { scope.launch { snackbarHostState.showSnackbar("Couldn't share this song.") } },
                            libraryActions = libraryActions,
                            playbackActions = playbackActions,
                        )
                    }
                    }
                }
            }
        }
            }
        }
        if (playback.currentTrack != null) MiniPlayer(playback, artworkRepository, onOpenPlayer, onPlayPause, onPreviousTrack, onNextTrack, Modifier.padding(vertical = 7.dp))
        SnackbarHost(hostState = snackbarHostState)
    }
    LibraryDialogs(
        state = state,
        selectedTracks = selectedTracks,
        trackToDelete = trackToDelete,
        onDismissTrackDelete = { trackToDelete = null },
        folderToHide = folderToHide,
        onDismissFolderHide = { folderToHide = null },
        pendingDuplicateAction = pendingDuplicateAction,
        onDismissDuplicateAction = { pendingDuplicateAction = null },
        pendingBulkDuplicateDelete = pendingBulkDuplicateDelete,
        onDismissBulkDuplicateDelete = { pendingBulkDuplicateDelete = null },
        showDeleteSelectionConfirmation = showDeleteSelectionConfirmation,
        onDismissSelectionDelete = { showDeleteSelectionConfirmation = false },
        artistToMerge = artistToMerge,
        onDismissArtistMerge = { artistToMerge = null },
        artistAliasInput = artistAliasInput,
        onArtistAliasInputChange = { artistAliasInput = it },
        showAddSheet = showAddSheet,
        onDismissAddSheet = { showAddSheet = false },
        pendingTracks = pendingTracks,
        onPendingTracksChange = { pendingTracks = it },
        showCreateDialog = showCreateDialog,
        onShowCreateDialogChange = { showCreateDialog = it },
        tracksToAddOnCreate = tracksToAddOnCreate,
        tracksRequiredOnCreate = tracksRequiredOnCreate,
        onTracksRequiredOnCreateChange = { tracksRequiredOnCreate = it },
        onTracksToAddOnCreateChange = { tracksToAddOnCreate = it },
        playlistNameInput = playlistNameInput,
        onPlaylistNameInputChange = { playlistNameInput = it },
        playlistToRename = playlistToRename,
        onPlaylistToRenameChange = { playlistToRename = it },
        playlistToDelete = playlistToDelete,
        onPlaylistToDeleteChange = { playlistToDelete = it },
        onSelectionChange = { selectedUris = it },
        onFolderHiddenUndo = { path ->
            onToggleFolderHidden(path, true) {
                scope.launch {
                    if (snackbarHostState.showSnackbar(
                            message = "Folder hidden",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Short,
                        ) == SnackbarResult.ActionPerformed
                    ) {
                        onToggleFolderHidden(path, false) {}
                    }
                }
            }
        },
        actions = LibraryDialogActions(
            deleteTrack = onDeleteTrack,
            deleteTracks = onDeleteTracks,
            markNotDuplicate = onNotDuplicate,
            mergeArtistAlias = onMergeArtistAlias,
            clearPlaylistImportReport = onClearPlaylistImportReport,
            clearBackupRestoreReport = onClearBackupRestoreReport,
            playNext = onPlayNext,
            addToQueue = onAddToQueue,
            addTracksToPlaylist = onAddTracksToPlaylist,
            createPlaylist = onCreatePlaylist,
            renamePlaylist = onRenamePlaylist,
            deletePlaylist = onDeletePlaylist,
        ),
    )
    SettingsSheet(
        open = settingsSheetOpen,
        state = state,
        themeMode = themeMode,
        clickWheelSensitivity = clickWheelSensitivity,
        clickWheelHaptics = clickWheelHaptics,
        lyricsDirectoryUri = lyricsDirectoryUri,
        onDismiss = { settingsSheetOpen = false },
        actions = SettingsActions(
            changeThemeMode = onThemeModeChange,
            changeWheelSensitivity = onClickWheelSensitivityChange,
            changeWheelHaptics = onClickWheelHapticsChange,
            rescan = onRetry,
            manageHiddenFolders = { onCategory("Folders"); showingHiddenFolders = true; settingsSheetOpen = false },
            openDuplicates = { onCategory("Duplicates") },
            removeArtistAlias = onRemoveArtistAlias,
            chooseLyricsDirectory = onChooseLyricsDirectory,
            openDownloader = onOpenDownloader,
            exportBackup = onExportBackup,
            importBackup = onImportBackup,
            importM3u = onImportM3u,
            showMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
        ),
    )
    if (filtersSheetOpen) {
        ModalBottomSheet(onDismissRequest = { filtersSheetOpen = false }) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ShrikhandHeading("Filter & sort", MaterialTheme.typography.titleLarge)
                Text("Sort by", style = MaterialTheme.typography.titleMedium)
                listOf("Title", "Artist", "Date added", "Duration").chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { order ->
                            FilterChip(
                                selected = state.sortOrder == order,
                                onClick = { onSortOrder(order) },
                                label = { Text(order) },
                                leadingIcon = {
                                    if (state.sortOrder == order) Icon(Icons.Default.Check, contentDescription = null)
                                },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.sortAscending,
                        onClick = { onSortAscending(true) },
                        label = { Text("Ascending") },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    )
                    FilterChip(
                        selected = !state.sortAscending,
                        onClick = { onSortAscending(false) },
                        label = { Text("Descending") },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Hide songs under 30s", Modifier.weight(1f))
                    Switch(
                        checked = state.hideShortTracks,
                        onCheckedChange = onHideShortTracks,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Hide songs over", Modifier.weight(1f))
                    Switch(
                        checked = state.hideLongTracks,
                        onCheckedChange = onHideLongTracks,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    )
                    Box {
                        TextButton(
                            onClick = { longTrackMenuOpen = true },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("${state.hideLongTracksAfterMinutes} min ▾") }
                        DropdownMenu(
                            expanded = longTrackMenuOpen,
                            onDismissRequest = { longTrackMenuOpen = false },
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                        ) {
                            listOf(20, 30, 45, 60, 90, 120).forEach { minutes ->
                                DropdownMenuItem(
                                    text = { Text("$minutes minutes") },
                                    onClick = {
                                        onHideLongTracksAfterMinutes(minutes)
                                        longTrackMenuOpen = false
                                    },
                                    leadingIcon = {
                                        if (minutes == state.hideLongTracksAfterMinutes) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

internal sealed interface PendingDuplicateAction {
    val group: com.dizzyvy.rift.ui.library.DuplicateGroup

    data class Keep(override val group: DuplicateGroup, val track: AudioTrack) : PendingDuplicateAction
    data class Delete(override val group: DuplicateGroup, val track: AudioTrack) : PendingDuplicateAction
    data class NotDuplicate(override val group: DuplicateGroup) : PendingDuplicateAction
}

internal fun formatTrackDate(dateAddedSeconds: Long): String =
    if (dateAddedSeconds > 0L) DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(dateAddedSeconds * 1_000L)) else "Unknown date"

internal fun formatAudioSize(bytes: Long): String = when {
    bytes <= 0 -> "Unknown"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
}

internal fun countLabel(count: Int, singular: String): String = if (count == 1) "1 $singular" else "$count ${singular}s"

internal fun displayValue(value: String, fallback: String): String =
    value.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) && it.none { char -> char == '?' || char == '\uFFFD' } } ?: fallback
