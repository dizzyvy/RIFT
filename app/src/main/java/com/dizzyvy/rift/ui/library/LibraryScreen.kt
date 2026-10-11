package com.dizzyvy.rift.ui.library

import android.content.ClipData
import android.content.Intent
import android.media.RingtoneManager
import android.view.HapticFeedbackConstants
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
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
import com.dizzyvy.rift.music.library.AlbumBrowseItem
import com.dizzyvy.rift.music.library.ArtistBrowseItem
import com.dizzyvy.rift.music.library.DevicePlaylist
import com.dizzyvy.rift.music.library.LibraryCollectionItem
import com.dizzyvy.rift.music.library.librarySection
import com.dizzyvy.rift.music.library.isValidLibraryYear
import com.dizzyvy.rift.music.library.artistGroupKeys
import com.dizzyvy.rift.music.library.albumGroupKey
import com.dizzyvy.rift.music.library.normalizeArtistName
import com.dizzyvy.rift.music.library.resolveArtistAlias
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
    onCreatePlaylist: (String, List<AudioTrack>) -> Unit,
    onRenamePlaylist: (DevicePlaylist, String) -> Unit,
    onDeletePlaylist: (DevicePlaylist) -> Unit,
    onImportM3u: () -> Unit,
    onClearPlaylistImportReport: () -> Unit,
    onExportM3u: (DevicePlaylist) -> Unit,
    onImportBackup: () -> Unit,
    onExportBackup: () -> Unit,
    onAddTrackToPlaylist: (DevicePlaylist, AudioTrack) -> Unit,
    onAddTracksToPlaylist: (DevicePlaylist, List<AudioTrack>) -> Unit,
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
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val tabScrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val isTablet = LocalConfiguration.current.screenWidthDp >= 700
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
    val songs = state.browseTracks ?: state.visibleTracks
    val selectedTracks = remember(songs, selectedUris) {
        songs.filter { it.uri.toString() in selectedUris }
    }
    BackHandler(enabled = selectedUris.isNotEmpty() || state.searchQuery.isNotEmpty()) {
        if (selectedUris.isNotEmpty()) {
            selectedUris = emptySet()
        } else {
            onSearch("")
            focusManager.clearFocus()
        }
    }
    LaunchedEffect(state.category, state.searchQuery.isBlank(), isTablet) {
        if (!isTablet) {
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
    }
    LaunchedEffect(state.category, state.browseTitle) { selectedUris = emptySet() }
    LaunchedEffect(state.category) {
        focusManager.clearFocus()
        keyboardController?.hide()
    }
    LaunchedEffect(state.searchQuery.isNotBlank()) {
        if (state.searchQuery.isNotBlank()) searchExpanded = true
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (scrolling) {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
            }
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
                    val duration = songs.sumOf { it.durationMs.coerceAtLeast(0L) }
                    val albumDetails = if (state.category == "Albums") {
                        val representative = songs.firstOrNull()
                        val year = songs.map { it.year }.filter(::isValidLibraryYear).maxOrNull()
                        val artist = representative?.let {
                            displayValue(it.albumArtist.ifBlank { it.artist }, "Unknown artist")
                        }.orEmpty()
                        listOfNotNull(year?.toString(), artist.takeIf(String::isNotBlank)).joinToString(" · ")
                            .takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()
                    } else ""
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
                ShrikhandHeading(
                    "Your music",
                    MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
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
                    Modifier.fillMaxWidth().then(if (isTablet) Modifier else Modifier.horizontalScroll(tabScrollState)),
                    horizontalArrangement = Arrangement.spacedBy(if (isTablet) 2.dp else 4.dp),
                ) {
                    val libraryFilters = listOf("Songs", "Artists", "Albums", "Playlists", "Folders", "Genres", "Years", "Duplicates")
                    val searchFilters = if (state.searchQuery.isBlank()) libraryFilters else listOf("All") + libraryFilters
                    searchFilters.forEach { tab ->
                        val category = if (tab == "All") "Search" else tab
                        val selected = state.category == category
                        val tabModifier = Modifier
                            .onGloballyPositioned { coordinates ->
                                val left = coordinates.positionInRoot().x
                                tabBounds[category] = left to (left + coordinates.size.width)
                            }
                            .graphicsLayer {
                                val bounds = tabBounds[category]
                                val viewport = tabViewportBounds
                                alpha = if (selected || isTablet || bounds == null || viewport == null) 1f else {
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
                            modifier = tabModifier.then(if (isTablet) Modifier.weight(1f) else Modifier),
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
                            onClick = { tracksToAddOnCreate = emptyList(); playlistNameInput = ""; showCreateDialog = true },
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
        when {
            state.permissionRequired -> EmptyPanel(Icons.Default.PlayArrow, "Let your music in", "RIFT scans audio stored on your phone and SD card. Enable Audio and music in App info if Android no longer shows the prompt.", "Set up audio access", onRequestPermission)
            state.isLoading && state.tracks.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
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
                    if (state.browseTitle == null && state.category == "Playlists") ({ tracksToAddOnCreate = emptyList(); playlistNameInput = ""; showCreateDialog = true })
                    else if (state.browseTitle == null && state.category != "Search") onRetry else null,
                )
                else PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = onRetry, modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                                    val aliasesForArtist = state.artistAliases.filterValues { canonical ->
                                        normalizeArtistName(resolveArtistAlias(canonical, state.artistAliases)) == item.id
                                    }.keys
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
                                FastScrollRail(state.visibleArtists.map { it.name }, listState, Modifier.align(Alignment.CenterEnd))
                            }
                        }
                        state.category == "Folders" -> {
                            val folderItems = if (showingHiddenFolders) state.visibleHiddenFolders else state.visibleFolders
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
                                    FastScrollRail(folderItems.map { it.title }, listState, Modifier.align(Alignment.CenterEnd))
                                }
                            }
                        }
                        state.category in listOf("Genres", "Years") -> {
                            val collections = if (state.category == "Genres") state.visibleGenres else state.visibleYears
                            Box(Modifier.fillMaxSize()) {
                                CollectionList(
                                    items = collections,
                                    artworkRepository = artworkRepository,
                                    listState = listState,
                                    onOpen = { onOpenCollection(it, state.category) },
                                )
                                if (state.category == "Genres" && state.searchQuery.isBlank() && collections.size > 30) {
                                    FastScrollRail(collections.map { it.title }, listState, Modifier.align(Alignment.CenterEnd))
                                }
                            }
                        }
                        state.category == "Duplicates" -> LazyColumn(state = listState, contentPadding = PaddingValues(end = 26.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            val lowerQualityTracks = state.visibleDuplicateGroups
                                .flatMap { group ->
                                    val best = recommendedDuplicateCopy(group)
                                    group.tracks.filterNot { it.uri == best?.uri }
                                }
                                .distinctBy { it.uri }
                            val reclaimableBytes = state.visibleDuplicateGroups.sumOf(::reclaimableDuplicateBytes)
                            item {
                                Column(Modifier.fillMaxWidth().padding(start = 5.dp, top = 7.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "${state.visibleDuplicateGroups.size} groups · ~${formatAudioSize(reclaimableBytes)} reclaimable",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (lowerQualityTracks.isNotEmpty()) {
                                        Button(
                                            onClick = { pendingBulkDuplicateDelete = lowerQualityTracks },
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                        ) {
                                            Text("Remove ${lowerQualityTracks.size} lower-quality copies (frees ~${formatAudioSize(reclaimableBytes)})")
                                        }
                                    }
                                }
                            }
                            items(state.visibleDuplicateGroups, key = { "duplicate-group:${it.id}" }) { group ->
                                var menuOpen by remember(group.id) { mutableStateOf(false) }
                                var fullPathTrack by remember(group.id) { mutableStateOf<AudioTrack?>(null) }
                                val bestCopy = recommendedDuplicateCopy(group.tracks)
                                val groupReclaimableBytes = reclaimableDuplicateBytes(group)
                                Column(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(group.tracks.first().title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${group.tracks.size} copies · can free ~${formatAudioSize(groupReclaimableBytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Box {
                                            IconButton(
                                                onClick = { menuOpen = true },
                                                modifier = Modifier.size(48.dp).semantics { contentDescription = "Duplicate group actions" },
                                            ) { Icon(Icons.Default.MoreVert, contentDescription = null) }
                                            DropdownMenu(
                                                expanded = menuOpen,
                                                onDismissRequest = { menuOpen = false },
                                                containerColor = MaterialTheme.colorScheme.surface,
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Not a duplicate") },
                                                    onClick = {
                                                        menuOpen = false
                                                        pendingDuplicateAction = PendingDuplicateAction.NotDuplicate(group)
                                                    },
                                                )
                                            }
                                        }
                                    }
                                    Text(group.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    group.tracks.forEach { track ->
                                        Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(
                                                "${track.title} · ${track.mimeType.substringAfter('/', "").uppercase().ifBlank { track.displayName.substringAfterLast('.', "").uppercase().ifBlank { "Unknown format" } }} · ${if (track.bitrate > 0) "${track.bitrate} kbps" else "Unknown bitrate"} · ${formatAudioSize(track.sizeBytes)} · ${formatTime(track.durationMs)} · ${formatTrackDate(track.dateAddedSeconds)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                            )
                                            if (track.uri == bestCopy?.uri) {
                                                Text("Best quality", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                            }
                                            val shortPath = track.filePath.replace('\\', '/').split('/').filter(String::isNotBlank).takeLast(2).joinToString("/")
                                            Text(
                                                shortPath.ifBlank { track.uri.toString() },
                                                Modifier.combinedClickable(
                                                    onClick = {},
                                                    onLongClick = { fullPathTrack = track },
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.MiddleEllipsis,
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = { pendingDuplicateAction = PendingDuplicateAction.Keep(group, track) },
                                                    modifier = Modifier.heightIn(min = 48.dp),
                                                ) { Text("Keep this one") }
                                                OutlinedButton(
                                                    onClick = { pendingDuplicateAction = PendingDuplicateAction.Delete(group, track) },
                                                    modifier = Modifier.heightIn(min = 48.dp),
                                                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                                ) { Text("Delete this one") }
                                            }
                                        }
                                        if (track != group.tracks.last()) HorizontalDivider()
                                    }
                                }
                                fullPathTrack?.let { selectedTrack ->
                                    AlertDialog(
                                        onDismissRequest = { fullPathTrack = null },
                                        title = { ShrikhandHeading("File location", MaterialTheme.typography.headlineSmall) },
                                        text = { Text(selectedTrack.filePath.ifBlank { selectedTrack.uri.toString() }) },
                                        confirmButton = { TextButton(onClick = { fullPathTrack = null }) { Text("Done") } },
                                    )
                                }
                            }
                        }
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
                                FastScrollRail(state.visibleAlbums.map { it.title }, listState, Modifier.align(Alignment.CenterEnd))
                            }
                        }
                        state.category == "Playlists" -> {
                            val smartPlaylists = state.visiblePlaylists.filter(DevicePlaylist::isAuto)
                            val userPlaylists = state.visiblePlaylists.filterNot(DevicePlaylist::isAuto)
                            LazyColumn(state = listState, contentPadding = PaddingValues(end = 26.dp, bottom = 12.dp)) {
                                if (smartPlaylists.isNotEmpty()) {
                                    item(key = "smart-playlist-header") {
                                        Text("Smart playlists", Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    items(smartPlaylists, key = { "smart:${it.volumeName}:${it.id}" }) { item ->
                                        PlaylistBrowseRow(
                                            playlist = item,
                                            artworkRepository = artworkRepository,
                                            onOpen = { onOpenPlaylist(item) },
                                            onRename = { playlistToRename = item; playlistNameInput = item.name },
                                            onDelete = { playlistToDelete = item },
                                            onExport = { onExportM3u(item) },
                                        )
                                    }
                                }
                                if (userPlaylists.isNotEmpty()) {
                                    item(key = "user-playlist-header") {
                                        Text("Your playlists", Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    items(userPlaylists, key = { "user:${it.volumeName}:${it.id}" }) { item ->
                                        PlaylistBrowseRow(
                                            playlist = item,
                                            artworkRepository = artworkRepository,
                                            onOpen = { onOpenPlaylist(item) },
                                            onRename = { playlistToRename = item; playlistNameInput = item.name },
                                            onDelete = { playlistToDelete = item },
                                            onExport = { onExportM3u(item) },
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            val headerCount = 2
                            LazyColumn(state = listState, contentPadding = PaddingValues(end = 26.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                item { Text(countLabel(songs.size, "song").uppercase(), Modifier.padding(start = 5.dp, top = 7.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                item {
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        TextButton(
                                            onClick = { filtersSheetOpen = true },
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                        ) { Text("Sort: ${state.sortOrder}") }
                                        TextButton(
                                            onClick = { filtersSheetOpen = true },
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                        ) { Text(if (state.hideShortTracks || state.hideLongTracks) "Filters •" else "Filters") }
                                        Button(
                                            onClick = onShuffleAll,
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                        ) {
                                            Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Shuffle", maxLines = 1)
                                        }
                                    }
                                }
                                item {
                                    Text(
                                        "Shuffle skips tracks longer than 20 minutes.",
                                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                items(songs, key = { it.uri.toString() }) { track ->
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
                                        onShareFailure = { scope.launch { snackbarHostState.showSnackbar("Couldn't share this song.") } },
                                    )
                                }
                            }
                            if (state.searchQuery.isBlank() && state.sortOrder in setOf("Title", "Artist")) {
                                val sectionNames = if (state.sortOrder == "Artist") {
                                    songs.map { it.albumArtist.ifBlank { it.artist } }
                                } else songs.map { it.title }
                                FastScrollRail(
                                    sectionNames,
                                    listState,
                                    Modifier.align(Alignment.CenterEnd),
                                    descending = !state.sortAscending,
                                    firstItemOffset = headerCount,
                                )
                            }
                        }
                    }
                    }
                }
                if (playback.currentTrack != null) MiniPlayer(playback, artworkRepository, onOpenPlayer, onPlayPause, onPreviousTrack, onNextTrack, Modifier.padding(vertical = 7.dp))
            }
        }
        SnackbarHost(hostState = snackbarHostState)
    }
    trackToDelete?.let { track ->
        AlertDialog(
            onDismissRequest = { trackToDelete = null },
            title = { ShrikhandHeading("Delete from device?", MaterialTheme.typography.headlineSmall) },
            text = { Text("Delete ${track.title} from this device? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteTrack(track)
                    trackToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { trackToDelete = null }) { Text("Cancel") } },
        )
    }
    folderToHide?.let { path ->
        AlertDialog(
            onDismissRequest = { folderToHide = null },
            title = { ShrikhandHeading("Hide folder?", MaterialTheme.typography.headlineSmall) },
            text = { Text("Hide $path and its songs from the library? You can unhide it later from Hidden folders.") },
            confirmButton = {
                TextButton(onClick = {
                    folderToHide = null
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
                }) { Text("Hide") }
            },
            dismissButton = { TextButton(onClick = { folderToHide = null }) { Text("Cancel") } },
        )
    }
    pendingDuplicateAction?.let { action ->
        val title = when (action) {
            is PendingDuplicateAction.Keep -> "Keep this track?"
            is PendingDuplicateAction.Delete -> "Delete this track?"
            is PendingDuplicateAction.NotDuplicate -> "Mark as not a duplicate?"
        }
        val message = when (action) {
            is PendingDuplicateAction.Keep -> "Keep “${action.track.title}” and delete the other ${countLabel(action.group.tracks.size - 1, "track")} in this group from the device? This cannot be undone."
            is PendingDuplicateAction.Delete -> "Delete “${action.track.title}” from this device? This cannot be undone."
            is PendingDuplicateAction.NotDuplicate -> "Remove this group from duplicate results? This choice will be saved in the app database."
        }
        AlertDialog(
            onDismissRequest = { pendingDuplicateAction = null },
            title = { ShrikhandHeading(title, MaterialTheme.typography.headlineSmall) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    when (action) {
                        is PendingDuplicateAction.Keep -> onDeleteTracks(action.group.tracks.filterNot { it.uri == action.track.uri })
                        is PendingDuplicateAction.Delete -> onDeleteTracks(listOf(action.track))
                        is PendingDuplicateAction.NotDuplicate -> onNotDuplicate(action.group.tracks.map { it.uri })
                    }
                    pendingDuplicateAction = null
                }) {
                    Text(if (action is PendingDuplicateAction.NotDuplicate) "Not a duplicate" else if (action is PendingDuplicateAction.Keep) "Keep" else "Delete")
                }
            },
            dismissButton = { TextButton(onClick = { pendingDuplicateAction = null }) { Text("Cancel") } },
        )
    }
    pendingBulkDuplicateDelete?.let { tracks ->
        AlertDialog(
            onDismissRequest = { pendingBulkDuplicateDelete = null },
            title = { ShrikhandHeading("Remove lower-quality copies?", MaterialTheme.typography.headlineSmall) },
            text = {
                Text("Delete ${tracks.size} lower-quality copies (${formatAudioSize(tracks.sumOf { it.sizeBytes.coerceAtLeast(0L) })}) from this device? This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTracks(tracks)
                        pendingBulkDuplicateDelete = null
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Review and delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingBulkDuplicateDelete = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Cancel")
                }
            },
        )
    }
    if (showDeleteSelectionConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectionConfirmation = false },
            title = { ShrikhandHeading("Delete selected songs?", MaterialTheme.typography.headlineSmall) },
            text = { Text("Delete ${selectedTracks.size} selected songs from this device? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTracks(selectedTracks)
                        selectedUris = emptySet()
                        showDeleteSelectionConfirmation = false
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectionConfirmation = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Cancel")
                }
            },
        )
    }
    artistToMerge?.let { artist ->
        AlertDialog(
            onDismissRequest = { artistToMerge = null },
            title = { ShrikhandHeading("Merge artist names", MaterialTheme.typography.headlineSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Treat this name as ${artist.name} in your library. The audio tags will not be changed.")
                    OutlinedTextField(value = artistAliasInput, onValueChange = { artistAliasInput = it }, singleLine = true, label = { Text("Other artist name") })
                }

            },
            confirmButton = {
                TextButton(
                    enabled = artistAliasInput.isNotBlank(),
                    onClick = {
                        onMergeArtistAlias(artistAliasInput, artist.name)
                        artistToMerge = null
                    },
                ) { Text("Merge") }
            },
            dismissButton = { TextButton(onClick = { artistToMerge = null }) { Text("Cancel") } },
        )
    }
    state.playlistImportReport?.let { report ->
        AlertDialog(
            onDismissRequest = onClearPlaylistImportReport,
            title = { ShrikhandHeading("Playlist imported", MaterialTheme.typography.headlineSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${report.matchedCount} of ${report.totalCount} tracks matched in “${report.playlistName}”.")
                    if (report.unmatchedEntries.isNotEmpty()) {
                        Text("Not matched:", fontWeight = FontWeight.SemiBold)
                        Column(
                            Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            report.unmatchedEntries.forEach { entry ->
                                Text(entry, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onClearPlaylistImportReport) { Text("Done") } },
        )
    }
    state.backupRestoreReport?.let { report ->
        AlertDialog(
            onDismissRequest = onClearBackupRestoreReport,
            title = { ShrikhandHeading("Backup restored", MaterialTheme.typography.headlineSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${report.playlistCount} playlists and ${report.favoriteCount} favorites restored.")
                    if (report.missingTrackCount > 0) {
                        Text("${report.missingTrackCount} track(s) could not be matched in the current library.")
                    }
                }
            },
            confirmButton = { TextButton(onClick = onClearBackupRestoreReport) { Text("Done") } },
        )
    }

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            ShrikhandHeading(
                if (pendingTracks.size == 1) "Add ${pendingTracks.first().title}" else "Add ${pendingTracks.size} songs",
                MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
            TextButton(onClick = { onPlayNext(pendingTracks); showAddSheet = false; pendingTracks = emptyList(); selectedUris = emptySet() }, modifier = Modifier.fillMaxWidth()) { Text("Play next") }
            TextButton(onClick = { pendingTracks.forEach(onAddToQueue); showAddSheet = false; pendingTracks = emptyList(); selectedUris = emptySet() }, modifier = Modifier.fillMaxWidth()) { Text("Add to queue") }
            TextButton(onClick = { tracksToAddOnCreate = pendingTracks; playlistNameInput = ""; showAddSheet = false; showCreateDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Create new playlist") }
            state.playlists.filter { it.isLocal && (!it.isAuto || it.autoKind == "favorites") }.forEach { playlist ->
                TextButton(onClick = {
                    onAddTracksToPlaylist(playlist, pendingTracks)
                    showAddSheet = false
                    pendingTracks = emptyList(); selectedUris = emptySet()
                }, modifier = Modifier.fillMaxWidth()) { Text("Add to ${playlist.name}") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false; tracksToAddOnCreate = emptyList() },
            title = { ShrikhandHeading("Create playlist", MaterialTheme.typography.headlineSmall) },
            text = { OutlinedTextField(value = playlistNameInput, onValueChange = { playlistNameInput = it }, singleLine = true, label = { Text("Playlist name") }) },
            confirmButton = {
                TextButton(enabled = playlistNameInput.isNotBlank(), onClick = {
                    onCreatePlaylist(playlistNameInput.trim(), tracksToAddOnCreate)
                    showCreateDialog = false
                    tracksToAddOnCreate = emptyList()
                    pendingTracks = emptyList()
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false; tracksToAddOnCreate = emptyList() }) { Text("Cancel") } },
        )
    }
    playlistToRename?.let { playlist ->
        AlertDialog(
            onDismissRequest = { playlistToRename = null },
            title = { ShrikhandHeading("Rename playlist", MaterialTheme.typography.headlineSmall) },
            text = { OutlinedTextField(value = playlistNameInput, onValueChange = { playlistNameInput = it }, singleLine = true, label = { Text("Playlist name") }) },
            confirmButton = { TextButton(enabled = playlistNameInput.isNotBlank(), onClick = { onRenamePlaylist(playlist, playlistNameInput.trim()); playlistToRename = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { playlistToRename = null }) { Text("Cancel") } },
        )
    }
    playlistToDelete?.let { playlist ->
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { ShrikhandHeading("Delete playlist?", MaterialTheme.typography.headlineSmall) },
            text = { Text("Delete ${playlist.name}? Songs on your device will not be deleted.") },
            confirmButton = { TextButton(onClick = { onDeletePlaylist(playlist); playlistToDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { playlistToDelete = null }) { Text("Cancel") } },
        )
    }
    if (settingsSheetOpen) {
        ModalBottomSheet(onDismissRequest = { settingsSheetOpen = false }) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ShrikhandHeading("Settings", MaterialTheme.typography.titleLarge)
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (mode, label) ->
                        FilterChip(
                            selected = themeMode.equals(mode, ignoreCase = true),
                            onClick = { onThemeModeChange(mode) },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("Click wheel", style = MaterialTheme.typography.titleMedium)
                Text("Sensitivity · ${"%.2f".format(java.util.Locale.ROOT, clickWheelSensitivity)}×")
                Slider(
                    value = clickWheelSensitivity,
                    onValueChange = onClickWheelSensitivityChange,
                    valueRange = 0.5f..2f,
                    steps = 5,
                    modifier = Modifier.semantics { contentDescription = "Click wheel sensitivity" },
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Haptic ticks", Modifier.weight(1f))
                    Switch(
                        checked = clickWheelHaptics,
                        onCheckedChange = onClickWheelHapticsChange,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("Library", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { settingsSheetOpen = false; onRetry() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Rescan now") }
                TextButton(
                    onClick = { onCategory("Folders"); showingHiddenFolders = true; settingsSheetOpen = false },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Manage hidden folders (${state.hiddenFolders.size})") }
                TextButton(
                    onClick = { settingsSheetOpen = false; onCategory("Duplicates") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(
                        if (state.duplicateGroupsLoaded) "Clean up duplicates (${state.duplicateGroups.size})"
                        else "Clean up duplicates",
                    )
                }
                if (state.artistAliases.isNotEmpty()) {
                    Text("Artist name merges", style = MaterialTheme.typography.titleSmall)
                    state.artistAliases.forEach { (alias, canonical) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("$alias → $canonical", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { onRemoveArtistAlias(alias) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Remove") }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("Lyrics", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onChooseLyricsDirectory, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (lyricsDirectoryUri == null) "Choose lyrics folder" else "Change lyrics folder")
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("Backup", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onExportBackup, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Back up playlists") }
                TextButton(onClick = onImportBackup, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Restore from backup") }
                TextButton(onClick = onImportM3u, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Import M3U") }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("About", style = MaterialTheme.typography.titleMedium)
                val versionName = remember(context) {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
                }
                Text("Version $versionName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(
                    onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/dizzyvy/RIFT"))) }
                            .onFailure { scope.launch { snackbarHostState.showSnackbar("Couldn't open the repository link.") } }
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("RIFT on GitHub") }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
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

private sealed interface PendingDuplicateAction {
    val group: com.dizzyvy.rift.ui.library.DuplicateGroup

    data class Keep(override val group: DuplicateGroup, val track: AudioTrack) : PendingDuplicateAction
    data class Delete(override val group: DuplicateGroup, val track: AudioTrack) : PendingDuplicateAction
    data class NotDuplicate(override val group: DuplicateGroup) : PendingDuplicateAction
}

@Composable
private fun FolderList(
    items: List<LibraryCollectionItem>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    hidden: Boolean,
    onHide: (LibraryCollectionItem) -> Unit,
    onUnhide: (LibraryCollectionItem) -> Unit,
    onOpen: (LibraryCollectionItem) -> Unit,
) {
    LazyColumn(state = listState, contentPadding = PaddingValues(end = 26.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items(items, key = { "folder:${it.id}" }) { item ->
            var menuOpen by remember(item.id) { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = !hidden) { onOpen(item) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val parentPath = item.id.substringBeforeLast('/', "Root").ifBlank { "Root" }
                    Text(parentPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                    Text(countLabel(item.trackCount, "song"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (hidden) {
                        Text("Subfolders are hidden too", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "Folder actions for ${item.title}" },
                    ) { Icon(Icons.Default.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        if (!hidden) {
                            DropdownMenuItem(text = { Text("Open") }, onClick = { menuOpen = false; onOpen(item) })
                        }
                        DropdownMenuItem(
                            text = { Text(if (hidden) "Unhide" else "Hide") },
                            onClick = {
                                menuOpen = false
                                if (hidden) onUnhide(item) else onHide(item)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionList(
    items: List<LibraryCollectionItem>,
    artworkRepository: ArtworkRepository,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onOpen: (LibraryCollectionItem) -> Unit,
) {
    LazyColumn(state = listState, contentPadding = PaddingValues(end = 26.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items(items, key = { it.id }) { item ->
            val subtitle = when {
                item.subtitle == "Year" -> countLabel(item.trackCount, "song")
                item.subtitle == item.id -> countLabel(item.trackCount, "song")
                else -> item.subtitle + " · " + countLabel(item.trackCount, "song")
            }
            BrowseRow(
                artworkUri = item.artworkUri,
                title = item.title,
                subtitle = subtitle,
                artworkRepository = artworkRepository,
            ) { onOpen(item) }
        }
    }
}

private fun formatTrackDate(dateAddedSeconds: Long): String =
    if (dateAddedSeconds > 0L) DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(dateAddedSeconds * 1_000L)) else "Unknown date"

@Composable
private fun SearchResults(
    listState: androidx.compose.foundation.lazy.LazyListState,
    tracks: List<AudioTrack>,
    artists: List<ArtistBrowseItem>,
    albums: List<AlbumBrowseItem>,
    playlists: List<DevicePlaylist>,
    artworkRepository: ArtworkRepository,
    onPlayTrack: (AudioTrack) -> Unit,
    onOpenArtist: (ArtistBrowseItem) -> Unit,
    onOpenAlbum: (AlbumBrowseItem) -> Unit,
    onOpenPlaylist: (DevicePlaylist) -> Unit,
    onRequestRename: (DevicePlaylist) -> Unit,
    onRequestDelete: (DevicePlaylist) -> Unit,
    onExportM3u: (DevicePlaylist) -> Unit,
) {
    LazyColumn(state = listState, contentPadding = PaddingValues(end = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (tracks.isNotEmpty()) {
            item { Text("SONGS · ${tracks.size}", Modifier.padding(start = 5.dp, top = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(tracks, key = { "search-song:${it.uri}" }) { track ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onPlayTrack(track) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AlbumTile(track, artworkRepository, Modifier.size(48.dp))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(displayValue(track.artist, "Unknown artist"), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (artists.isNotEmpty()) {
            item { Text("ARTISTS · ${artists.size}", Modifier.padding(start = 5.dp, top = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(artists, key = { "search-artist:${it.id}" }) { item ->
                BrowseRow(item.artworkUri, item.name, countLabel(item.trackCount, "song"), artworkRepository, artistInitialFallback = true) { onOpenArtist(item) }
            }
        }
        if (albums.isNotEmpty()) {
            item { Text("ALBUMS · ${albums.size}", Modifier.padding(start = 5.dp, top = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(albums, key = { "search-album:${it.id}" }) { item ->
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
        if (playlists.isNotEmpty()) {
            item { Text("PLAYLISTS · ${playlists.size}", Modifier.padding(start = 5.dp, top = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(playlists, key = { "search-playlist:${it.volumeName}:${it.id}" }) { playlist ->
                PlaylistBrowseRow(
                    playlist = playlist,
                    artworkRepository = artworkRepository,
                    onOpen = { onOpenPlaylist(playlist) },
                    onRename = { onRequestRename(playlist) },
                    onDelete = { onRequestDelete(playlist) },
                    onExport = { onExportM3u(playlist) },
                )
            }
        }
    }
}

@Composable
private fun PlaylistBrowseRow(
    playlist: DevicePlaylist,
    artworkRepository: ArtworkRepository,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpen).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        PlaylistCoverMosaic(playlist, artworkRepository)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(playlist.name, fontWeight = FontWeight.SemiBold)
            Text(
                if (playlist.isAuto && playlist.trackCount == 0) "Play a few songs to fill this"
                else countLabel(playlist.trackCount, "song"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Playlist actions for ${playlist.name}" }) {
                Icon(Icons.Default.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Open playlist") }, onClick = { menuOpen = false; onOpen() })
                DropdownMenuItem(text = { Text("Export M3U") }, onClick = { menuOpen = false; onExport() })
                if (playlist.isLocal && !playlist.isAuto) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; onRename() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun PlaylistCoverMosaic(playlist: DevicePlaylist, artworkRepository: ArtworkRepository) {
    Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))) {
        if (playlist.artworkUris.isEmpty()) {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                PlaylistKindIcon(playlist, Modifier.size(24.dp))
            }
        } else {
            Column {
                Row {
                    (0..1).forEach { index -> PlaylistCoverCell(playlist.artworkUris.getOrNull(index), playlist, artworkRepository) }
                }
                Row {
                    (2..3).forEach { index -> PlaylistCoverCell(playlist.artworkUris.getOrNull(index), playlist, artworkRepository) }
                }
            }
        }
        if (playlist.isAuto && playlist.artworkUris.isNotEmpty()) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(18.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                PlaylistKindIcon(playlist, Modifier.size(12.dp), MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun PlaylistCoverCell(uri: Uri?, playlist: DevicePlaylist, artworkRepository: ArtworkRepository) {
    Box(
        Modifier.size(24.dp)
            .background(MaterialTheme.colorScheme.tertiaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (uri != null) {
            AlbumArtwork(uri, playlist.name, artworkRepository, Modifier.fillMaxSize())
        } else {
            PlaylistKindIcon(playlist, Modifier.size(16.dp))
        }
    }
}

@Composable
private fun PlaylistKindIcon(
    playlist: DevicePlaylist,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onTertiaryContainer,
) {
    val icon = when (playlist.autoKind) {
        "favorites" -> Icons.Default.Favorite
        "recently_played" -> Icons.Default.AccessTime
        "most_played" -> Icons.Default.Whatshot
        "recently_added" -> Icons.Default.NewReleases
        "never_played" -> Icons.Default.VisibilityOff
        else -> if (playlist.isAuto) Icons.Default.Refresh else Icons.Default.MusicNote
    }
    Icon(icon, contentDescription = null, modifier = modifier, tint = tint)
}

@Composable
private fun TrackRow(
    track: AudioTrack,
    artworkRepository: ArtworkRepository,
    playing: Boolean,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onLongPress: () -> Unit = {},
    onClick: () -> Unit,
    onAdd: () -> Unit,
    isFavorite: Boolean = false,
    onFavorite: () -> Unit = {},
    onPlayNext: () -> Unit = {},
    onQueue: () -> Unit = {},
    onGoArtist: () -> Unit = {},
    onGoAlbum: () -> Unit = {},
    onDelete: () -> Unit = {},
    showRemove: Boolean = false,
    onRemove: () -> Unit = {},
    showReorder: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onShareFailure: () -> Unit = {},
) {
    val context = LocalContext.current
    val rowShape = RoundedCornerShape(15.dp)
    var moreMenuOpen by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(rowShape)
            .background(if (playing) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), rowShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(vertical = 6.dp, horizontal = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) Checkbox(checked = selected, onCheckedChange = { onClick() }, modifier = Modifier.size(48.dp)) else AlbumTile(track, artworkRepository, Modifier.size(48.dp))
        Column(Modifier.weight(1f).padding(start = 11.dp)) {
            Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(displayValue(track.artist, "Unknown artist"), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(formatTime(track.durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp))
        if (playing) AnimatedEqualizer(Modifier.padding(horizontal = 4.dp))
        if (showReorder) {
            Box(
                Modifier.size(48.dp).semantics { contentDescription = "Drag to reorder ${track.title}" }
                    .pointerInput(canMoveUp, canMoveDown) {
                        var dragDistance = 0f
                        detectDragGesturesAfterLongPress(onDragEnd = { dragDistance = 0f }, onDragCancel = { dragDistance = 0f }) { change, dragAmount ->
                            change.consume()
                            dragDistance += dragAmount.y
                            if (dragDistance > 48.dp.toPx() && canMoveDown) { onMoveDown(); dragDistance = 0f }
                            if (dragDistance < -48.dp.toPx() && canMoveUp) { onMoveUp(); dragDistance = 0f }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Menu, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box {
            IconButton(onClick = { moreMenuOpen = true }, modifier = Modifier.size(48.dp).semantics { contentDescription = "More actions for ${track.title}" }) {
                Icon(Icons.Default.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            }
            DropdownMenu(expanded = moreMenuOpen, onDismissRequest = { moreMenuOpen = false }) {
                DropdownMenuItem(text = { Text("Play next") }, onClick = { moreMenuOpen = false; onPlayNext() })
                DropdownMenuItem(text = { Text("Add to queue") }, onClick = { moreMenuOpen = false; onQueue() })
                DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { moreMenuOpen = false; onAdd() })
                if (showReorder || showRemove) DropdownMenuItem(text = { Text("Remove from playlist") }, onClick = { moreMenuOpen = false; onRemove() })
                if (!showReorder) DropdownMenuItem(text = { Text(if (isFavorite) "Remove from favorites" else "Add to favorites") }, onClick = { moreMenuOpen = false; onFavorite() })
                DropdownMenuItem(text = { Text("Go to artist") }, onClick = { moreMenuOpen = false; onGoArtist() })
                DropdownMenuItem(text = { Text("Go to album") }, onClick = { moreMenuOpen = false; onGoAlbum() })
                DropdownMenuItem(text = { Text("Delete from device") }, onClick = { moreMenuOpen = false; onDelete() })
                DropdownMenuItem(text = { Text("Song details") }, onClick = { moreMenuOpen = false; showDetails = true })
                DropdownMenuItem(text = { Text("Set as ringtone") }, onClick = {
                    moreMenuOpen = false
                    if (!Settings.System.canWrite(context)) {
                        Toast.makeText(context, "Allow RIFT to modify system settings, then choose Set as ringtone again.", Toast.LENGTH_LONG).show()
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            })
                        }
                    } else {
                        runCatching { RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, track.uri) }
                            .onSuccess { Toast.makeText(context, "Ringtone set.", Toast.LENGTH_SHORT).show() }
                            .onFailure { Toast.makeText(context, "Could not set this song as a ringtone.", Toast.LENGTH_LONG).show() }
                    }
                })
                DropdownMenuItem(text = { Text("Share") }, onClick = {
                    moreMenuOpen = false
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "audio/*"
                        putExtra(Intent.EXTRA_STREAM, track.uri)
                        clipData = ClipData.newRawUri("", track.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(Intent.createChooser(send, "Share song")) }
                        .onFailure { onShareFailure() }
                })
            }
        }
    }
    if (showDetails) {
        val format = track.mimeType.substringAfter('/', "").takeIf { it.isNotBlank() }?.uppercase() ?: track.displayName.substringAfterLast('.', "").uppercase().ifBlank { "Unknown" }
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { ShrikhandHeading("Song details", MaterialTheme.typography.headlineSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(track.title, fontWeight = FontWeight.SemiBold)
                    Text("Artist: ${displayValue(track.artist, "Unknown artist")}")
                    Text("Album: ${displayValue(track.album, "Unknown album")}")
                    Text("Format: $format")
                    Text("Bitrate: ${if (track.bitrate > 0) "${track.bitrate} kbps" else "Unknown"}")
                    Text("Sample rate: ${if (track.sampleRateHz > 0) "${track.sampleRateHz} Hz" else "Unknown"}")
                    Text("Size: ${formatAudioSize(track.sizeBytes)}")
                    Text("Path: ${track.filePath.ifBlank { track.uri.toString() }}", maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text("Duration: ${formatTime(track.durationMs)}")
                }
            },
            confirmButton = { TextButton(onClick = { showDetails = false }) { Text("Done") } },
        )
    }
}

@Composable
private fun AnimatedEqualizer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "playing-indicator")
    val color = MaterialTheme.colorScheme.primary
    val first by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "bar-one",
    )
    val second by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(560), RepeatMode.Reverse),
        label = "bar-two",
    )
    val third by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(360), RepeatMode.Reverse),
        label = "bar-three",
    )
    Canvas(
        modifier.size(18.dp, 18.dp).semantics { contentDescription = "Now playing" },
    ) {
        val barWidth = 3.dp.toPx()
        val gap = 2.dp.toPx()
        val heights = listOf(first, second, third)
        heights.forEachIndexed { index, fraction ->
            val barHeight = size.height * fraction
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (barWidth + gap), size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f),
            )
        }
    }
}

private fun formatAudioSize(bytes: Long): String = when {
    bytes <= 0 -> "Unknown"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
}

@Composable
fun MiniPlayer(playback: PlaybackSnapshot, artworkRepository: ArtworkRepository, onClick: () -> Unit, onPlayPause: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    val track = playback.currentTrack ?: return
    val playerShape = RoundedCornerShape(18.dp)
    var horizontalDrag by remember(track.uri) { mutableStateOf(0f) }
    Surface(
        modifier.fillMaxWidth().clip(playerShape)
            .graphicsLayer { translationX = horizontalDrag.coerceIn(-48f, 48f) }
            .pointerInput(track.uri) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (horizontalDrag > 48f) onNext()
                        else if (horizontalDrag < -48f) onPrevious()
                        horizontalDrag = 0f
                    },
                    onHorizontalDrag = { change, amount -> change.consume(); horizontalDrag += amount },
                )
            },
        color = MaterialTheme.colorScheme.surface,
        shape = playerShape,
        tonalElevation = 4.dp,
    ) {
        Column {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
                    AlbumTile(track, artworkRepository, Modifier.size(46.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(track.title, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(displayValue(track.artist, "Unknown artist"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Previous track" },
                ) { Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = onPlayPause, modifier = Modifier.size(48.dp).semantics {
                    contentDescription = if (playback.isPlaying) "Pause playback" else "Resume playback"
                }) {
                    Icon(if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Next track" },
                ) { Icon(Icons.Default.SkipNext, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            LinearProgressIndicator(
                progress = { if (playback.durationMs > 0L) (playback.positionMs.toFloat() / playback.durationMs.toFloat()).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            )
        }
    }
}

@Composable
fun AlbumTile(track: AudioTrack, artworkRepository: ArtworkRepository, modifier: Modifier = Modifier) {
    AlbumArtwork(
        track.uri,
        track.title,
        artworkRepository,
        modifier,
        sourceDateModifiedSeconds = track.dateModifiedSeconds,
    )
}

@Composable
private fun BrowseRow(
    artworkUri: android.net.Uri?,
    title: String,
    subtitle: String,
    artworkRepository: ArtworkRepository,
    artistInitialFallback: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null,
    titleMaxLines: Int = 1,
    onClick: () -> Unit,
) {
    val rowShape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(rowShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (artworkUri != null) AlbumArtwork(artworkUri, title, artworkRepository, Modifier.size(48.dp), fallbackInitial = artistInitialFallback)
        else Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.tertiary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiary)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = titleMaxLines, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailingContent?.invoke()
    }
}

@Composable
private fun FastScrollRail(
    names: List<String>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
    descending: Boolean = false,
    firstItemOffset: Int = 0,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val firstIndexByLetter = remember(names) {
        names.indices.groupBy { librarySection(names[it]) }.mapValues { (_, indices) -> indices.first() }
    }
    val letters = remember(descending) {
        (listOf('#') + ('A'..'Z').toList()).let { if (descending) it.reversed() else it }
    }
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    var previousLetter by remember { mutableStateOf<Char?>(null) }

    Box(
        modifier.fillMaxHeight().width(26.dp)
            .pointerInput(letters, firstIndexByLetter, firstItemOffset) {
                fun scrollFor(positionY: Float) {
                    val letterIndex = ((positionY / size.height.coerceAtLeast(1)) * letters.size)
                        .toInt().coerceIn(0, letters.lastIndex)
                    val letter = letters[letterIndex]
                    activeLetter = letter
                    if (previousLetter != letter) {
                        firstIndexByLetter[letter]?.let { target ->
                            scope.launch { listState.scrollToItem(target + firstItemOffset) }
                        }
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        previousLetter = letter
                    }
                }
                detectDragGestures(
                    onDragStart = { scrollFor(it.y) },
                    onDragEnd = { activeLetter = null; previousLetter = null },
                    onDragCancel = { activeLetter = null; previousLetter = null },
                ) { change, _ ->
                    change.consume()
                    scrollFor(change.position.y)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            letters.forEach { letter ->
                val exists = letter in firstIndexByLetter
                Text(
                    letter.toString(),
                    modifier = Modifier.weight(1f).fillMaxWidth().clickable(enabled = exists) {
                        firstIndexByLetter[letter]?.let { target ->
                            scope.launch { listState.animateScrollToItem(target + firstItemOffset) }
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (exists) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        activeLetter?.let { letter ->
            Surface(
                modifier = Modifier.align(Alignment.CenterStart).offset(x = (-38).dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    letter.toString(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

private fun countLabel(count: Int, singular: String): String = if (count == 1) "1 $singular" else "$count ${singular}s"

private fun displayValue(value: String, fallback: String): String =
    value.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) && it.none { char -> char == '?' || char == '\uFFFD' } } ?: fallback

@Composable
private fun EmptyPanel(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, button: String? = null, onClick: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.tertiary).padding(24.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onTertiary)
        }
        Spacer(Modifier.height(16.dp)); Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp)); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (button != null && onClick != null) { Spacer(Modifier.height(16.dp)); Button(onClick = onClick) { Text(button) } }
    }
}
