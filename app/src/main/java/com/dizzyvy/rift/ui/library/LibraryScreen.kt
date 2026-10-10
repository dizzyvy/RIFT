package com.dizzyvy.rift.ui.library

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import com.dizzyvy.rift.music.library.artistGroupKeys
import com.dizzyvy.rift.music.library.albumGroupKey
import com.dizzyvy.rift.music.library.normalizeArtistName
import com.dizzyvy.rift.music.library.resolveArtistAlias
import com.dizzyvy.rift.music.model.AudioTrack
import com.dizzyvy.rift.music.playback.PlaybackSnapshot
import com.dizzyvy.rift.ui.components.AlbumArtwork
import com.dizzyvy.rift.ui.theme.NanoBackground
import com.dizzyvy.rift.ui.theme.NanoBlue
import com.dizzyvy.rift.ui.theme.NanoCyan
import com.dizzyvy.rift.ui.theme.NanoGreen
import com.dizzyvy.rift.ui.theme.NanoMuted
import com.dizzyvy.rift.ui.theme.NanoOutline
import com.dizzyvy.rift.ui.theme.NanoPink
import com.dizzyvy.rift.ui.theme.NanoPurple
import com.dizzyvy.rift.ui.theme.NanoSurface
import com.dizzyvy.rift.ui.theme.NanoSurfaceRaised
import com.dizzyvy.rift.ui.theme.NanoText
import com.dizzyvy.rift.ui.theme.RiftBackgroundBrush
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private val NanoAccentBrush = Brush.linearGradient(listOf(NanoCyan, NanoBlue, NanoPurple, NanoPink))
private val NanoScreenGlow = Brush.verticalGradient(listOf(Color(0xFF0B1B2B), NanoBackground, Color(0xFF050A10)))

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
    onPlayPlaylist: (Boolean) -> Unit,
    onRemoveTrackFromPlaylist: (AudioTrack) -> Unit,
    onMovePlaylistTrack: (Int, Int) -> Unit,
    onBackFromGroup: () -> Unit,
    themeMode: String,
    accentName: String,
    onThemeModeChange: (String) -> Unit,
    onAccentChange: (String) -> Unit,
    onToggleFolderHidden: (String, Boolean, () -> Unit) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val tabScrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val nanoMode = themeMode.equals("nano", ignoreCase = true)
    val isTablet = LocalConfiguration.current.screenWidthDp >= 700
    var headerCollapsed by remember { mutableStateOf(false) }
    var appearanceMenuOpen by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var longTrackMenuOpen by remember { mutableStateOf(false) }
    var tabViewportBounds by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    val tabBounds = remember { mutableStateMapOf<String, Pair<Float, Float>>() }
    var pendingTracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var selectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
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
    var artistToMerge by remember { mutableStateOf<ArtistBrowseItem?>(null) }
    var artistAliasInput by remember { mutableStateOf("") }
    val songs = state.browseTracks ?: state.visibleTracks
    val selectedTracks = state.tracks.filter { it.uri.toString() in selectedUris }
    LaunchedEffect(listState, isTablet, state.browseTitle) {
        var previousPosition = 0L
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val position = index.toLong() * 1_000_000L + offset
                if (!isTablet && state.browseTitle == null) {
                    if (position > previousPosition) headerCollapsed = true
                    else if (position < previousPosition) headerCollapsed = false
                } else {
                    headerCollapsed = false
                }
                previousPosition = position
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
    LaunchedEffect(state.category) { showingHiddenFolders = false }
    LaunchedEffect(state.actionMessage) {
        val message = state.actionMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onClearActionMessage()
    }
    Column(
        Modifier.fillMaxSize()
            .background(if (nanoMode) NanoScreenGlow else RiftBackgroundBrush())
            .padding(horizontal = 18.dp),
    ) {
        if (state.browseTitle != null) {
            TextButton(onClick = onBackFromGroup, modifier = Modifier.padding(top = 2.dp)) { Text("‹  ${state.category.uppercase()}") }
            Text(state.browseTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
            if (state.activePlaylist?.isLocal == true) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onPlayPlaylist(false) }, modifier = Modifier.weight(1f)) { Text("Play all") }
                    OutlinedButton(onClick = { onPlayPlaylist(true) }, modifier = Modifier.weight(1f)) { Text("Shuffle") }
                }
            }
        } else if (!headerCollapsed || isTablet) {
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("RIFT", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("Your music", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                }
                Box {
                    TextButton(onClick = { appearanceMenuOpen = true }) { Text("Appearance ▾") }
                }
            }
        }
        if (state.browseTitle == null && (!headerCollapsed || isTablet)) {
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
                        if (nanoMode) NanoFilterChip(
                            tab,
                            selected,
                            { onCategory(category) },
                            tabModifier.then(if (isTablet) Modifier.weight(1f) else Modifier),
                        )
                        else FilterChip(
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
            if (state.category == "Folders") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showingHiddenFolders = !showingHiddenFolders }) {
                        Text(if (showingHiddenFolders) "‹ Folders" else "Hidden folders")
                    }
                }
            }
            if (appearanceMenuOpen) AppearanceOptions(
                themeMode = themeMode,
                accentName = accentName,
                onThemeModeChange = onThemeModeChange,
                onAccentChange = onAccentChange,
                onDismiss = { appearanceMenuOpen = false },
            )
            if (selectedTracks.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${selectedTracks.size} selected", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { pendingTracks = selectedTracks; showAddSheet = true }) { Text("Add to playlist") }
                    TextButton(onClick = { selectedUris = emptySet() }) { Text("Clear") }
                }
            }
            if (state.category in listOf("Songs", "Artists", "Albums", "Playlists", "Folders", "Genres", "Years", "Duplicates", "Search")) {
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
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(if (nanoMode) 18.dp else 15.dp),
                    placeholder = { Text(placeholder, color = if (nanoMode) NanoMuted else MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingIcon = { Text("⌕", color = if (nanoMode) NanoCyan else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall) },
                    colors = if (nanoMode) OutlinedTextFieldDefaults.colors(
                        focusedTextColor = NanoText,
                        unfocusedTextColor = NanoText,
                        focusedBorderColor = NanoBlue,
                        unfocusedBorderColor = NanoOutline,
                        cursorColor = NanoCyan,
                        focusedContainerColor = Color(0xFF0B1827),
                        unfocusedContainerColor = Color(0xFF0B1827),
                    ) else OutlinedTextFieldDefaults.colors(),
                )
                if (state.category == "Playlists") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { tracksToAddOnCreate = emptyList(); playlistNameInput = ""; showCreateDialog = true },
                            modifier = Modifier.weight(1f),
                        ) { Text("+ New") }
                        TextButton(onClick = onImportM3u, modifier = Modifier.weight(1f)) { Text("Import") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onImportBackup, modifier = Modifier.weight(1f)) { Text("Restore") }
                        TextButton(onClick = onExportBackup, modifier = Modifier.weight(1f)) { Text("Backup") }
                    }
                }
                if (state.category == "Songs") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            TextButton(onClick = { sortMenuOpen = true }) { Text("Sort: ${state.sortOrder} ▾") }
                            DropdownMenu(
                                expanded = sortMenuOpen,
                                onDismissRequest = { sortMenuOpen = false },
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 0.dp,
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Ascending") },
                                    onClick = { onSortAscending(true); sortMenuOpen = false },
                                    leadingIcon = { if (state.sortAscending) Text("✓") },
                                )
                                DropdownMenuItem(
                                    text = { Text("Descending") },
                                    onClick = { onSortAscending(false); sortMenuOpen = false },
                                    leadingIcon = { if (!state.sortAscending) Text("✓") },
                                )
                                HorizontalDivider()
                                listOf("Title", "Artist", "Date added", "Duration").forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order) },
                                        onClick = { onSortOrder(order); sortMenuOpen = false },
                                        leadingIcon = { if (state.sortOrder == order) Text("✓") },
                                    )
                                }
                            }
                        }
                        FilterChip(
                            selected = state.hideShortTracks,
                            onClick = { onHideShortTracks(!state.hideShortTracks) },
                            label = { Text("Hide under 30s: ${if (state.hideShortTracks) "On" else "Off"}") },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = state.hideLongTracks,
                            onClick = { onHideLongTracks(!state.hideLongTracks) },
                            label = { Text("Hide over ${state.hideLongTracksAfterMinutes} min: ${if (state.hideLongTracks) "On" else "Off"}") },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
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
                                        onClick = { onHideLongTracksAfterMinutes(minutes); longTrackMenuOpen = false },
                                        leadingIcon = { if (minutes == state.hideLongTracksAfterMinutes) Text("✓") },
                                    )
                                }
                            }
                        }
                    }
                    if (state.visibleTracks.isNotEmpty()) {
                        Button(onClick = onShuffleAll, modifier = Modifier.fillMaxWidth()) { Text("Shuffle all") }
                    }
                }
            }
        }
        if (state.isLoading && state.scanTotal != null) {
            val total = state.scanTotal.coerceAtLeast(state.scanProcessed)
            Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp)) {
                LinearProgressIndicator(
                    progress = if (total > 0) (state.scanProcessed.toFloat() / total).coerceIn(0f, 1f) else 0f,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Scanning music · ${state.scanProcessed} of $total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when {
            state.permissionRequired -> EmptyPanel("♫", "Let your music in", "RIFT scans audio stored on your phone and SD card. Enable Audio and music in App info if Android no longer shows the prompt.", "Set up audio access", onRequestPermission)
            state.isLoading && state.tracks.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.message != null -> EmptyPanel("!", "Library unavailable", state.message, "Scan again", onRetry)
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
                    "♫",
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
                            LazyColumn(state = listState, contentPadding = PaddingValues(end = 26.dp, bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
                                    )
                                }
                            }
                        }
                        state.category == "Artists" -> Box(Modifier.fillMaxSize()) {
                            LazyColumn(state = listState, contentPadding = PaddingValues(end = 26.dp, bottom = 90.dp)) {
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
                                                TextButton(onClick = { artistMenuOpen = true }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = "Artist name options for ${item.name}" }) { Text("⋮") }
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
                            if (state.searchQuery.isBlank() && state.sortOrder == "Title") AlphaIndexRail(state.visibleArtists.map { it.name }, listState, Modifier.align(Alignment.CenterEnd))
                        }
                        state.category == "Folders" -> FolderList(
                            items = if (showingHiddenFolders) state.visibleHiddenFolders else state.visibleFolders,
                            listState = listState,
                            hidden = showingHiddenFolders,
                            onHide = { folderToHide = it.id },
                            onUnhide = { onToggleFolderHidden(it.id, false) {} },
                            onOpen = { onOpenCollection(it, "Folders") },
                        )
                        state.category in listOf("Genres", "Years") -> CollectionList(
                            items = if (state.category == "Genres") state.visibleGenres else state.visibleYears,
                            artworkRepository = artworkRepository,
                            listState = listState,
                            onOpen = { onOpenCollection(it, state.category) },
                        )
                        state.category == "Duplicates" -> LazyColumn(state = listState, contentPadding = PaddingValues(end = 26.dp, bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            item { Text("${countLabel(state.visibleDuplicateGroups.size, "group").uppercase()}", Modifier.padding(start = 5.dp, top = 7.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            items(state.visibleDuplicateGroups, key = { "duplicate-group:${it.id}" }) { group ->
                                Column(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(group.tracks.first().title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                            Text("${countLabel(group.tracks.size, "track")} in this group", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        TextButton(onClick = { pendingDuplicateAction = PendingDuplicateAction.NotDuplicate(group) }) {
                                            Text("Not a duplicate")
                                        }
                                    }
                                    Text("Matched because: ${group.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    group.tracks.forEach { track ->
                                        Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(track.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                            Text(track.filePath.ifBlank { track.uri.toString() }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                "Size ${formatAudioSize(track.sizeBytes)} · ${if (track.bitrate > 0) "${track.bitrate} kbps" else "Unknown bitrate"} · ${formatTime(track.durationMs)} · ${formatTrackDate(track.dateAddedSeconds)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                TextButton(onClick = { pendingDuplicateAction = PendingDuplicateAction.Keep(group, track) }) { Text("Keep") }
                                                TextButton(onClick = { pendingDuplicateAction = PendingDuplicateAction.Delete(group, track) }) { Text("Delete") }
                                            }
                                        }
                                        if (track != group.tracks.last()) HorizontalDivider()
                                    }
                                }
                            }
                        }
                        state.category == "Albums" -> Box(Modifier.fillMaxSize()) {
                            LazyColumn(state = listState, contentPadding = PaddingValues(end = 26.dp, bottom = 90.dp)) {
                                items(state.visibleAlbums, key = { it.id }) { item -> BrowseRow(item.artworkUri, item.title, "${displayValue(item.artist, "Unknown artist")} · ${countLabel(item.trackCount, "song")}", artworkRepository) { onOpenAlbum(item) } }
                            }
                            if (state.searchQuery.isBlank() && state.sortOrder == "Title") AlphaIndexRail(state.visibleAlbums.map { it.title }, listState, Modifier.align(Alignment.CenterEnd))
                        }
                        state.category == "Playlists" -> LazyColumn(state = listState, contentPadding = PaddingValues(end = 26.dp, bottom = 90.dp)) {
                            items(state.visiblePlaylists, key = { "${it.volumeName}:${it.id}" }) { item ->
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
                        else -> {
                            val headerCount = 1
                            LazyColumn(state = listState, contentPadding = PaddingValues(end = 26.dp, bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                item { Text(countLabel(songs.size, "song").uppercase(), Modifier.padding(start = 5.dp, top = 7.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                                    )
                                }
                            }
                            if (state.searchQuery.isBlank()) {
                                Column(Modifier.align(Alignment.CenterEnd).padding(end = 0.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    (listOf('#') + ('A'..'Z').toList()).forEach { letter ->
                                        Text(letter.toString(), modifier = Modifier.clickable {
                                            val target = songs.indexOfFirst { librarySection(it.title) == letter }
                                            if (target >= 0) scope.launch { listState.animateScrollToItem(target + headerCount) }
                                        }.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
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
            title = { Text("Delete from device?") },
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
            title = { Text("Hide folder?") },
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
            title = { Text(title) },
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
    artistToMerge?.let { artist ->
        AlertDialog(
            onDismissRequest = { artistToMerge = null },
            title = { Text("Merge artist names") },
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
            title = { Text("Playlist imported") },
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

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            Text(if (pendingTracks.size == 1) "Add ${pendingTracks.first().title}" else "Add ${pendingTracks.size} songs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 22.dp))
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
            title = { Text("Create playlist") },
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
            title = { Text("Rename playlist") },
            text = { OutlinedTextField(value = playlistNameInput, onValueChange = { playlistNameInput = it }, singleLine = true, label = { Text("Playlist name") }) },
            confirmButton = { TextButton(enabled = playlistNameInput.isNotBlank(), onClick = { onRenamePlaylist(playlist, playlistNameInput.trim()); playlistToRename = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { playlistToRename = null }) { Text("Cancel") } },
        )
    }
    playlistToDelete?.let { playlist ->
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("Delete playlist?") },
            text = { Text("Delete ${playlist.name}? Songs on your device will not be deleted.") },
            confirmButton = { TextButton(onClick = { onDeletePlaylist(playlist); playlistToDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { playlistToDelete = null }) { Text("Cancel") } },
        )
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
    LazyColumn(state = listState, contentPadding = PaddingValues(end = 26.dp, bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items(items, key = { "folder:${it.id}" }) { item ->
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
                    Text("📁", style = MaterialTheme.typography.titleLarge)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(item.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(countLabel(item.trackCount, "song"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { if (hidden) onUnhide(item) else onHide(item) }) {
                    Text(if (hidden) "Unhide" else "Hide")
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
    LazyColumn(state = listState, contentPadding = PaddingValues(end = 26.dp, bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
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

@Composable
private fun AppearanceOptions(
    themeMode: String,
    accentName: String,
    onThemeModeChange: (String) -> Unit,
    onAccentChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val darkTheme = themeMode.equals("dark", ignoreCase = true) ||
        themeMode.equals("amoled", ignoreCase = true) ||
        themeMode.equals("nano", ignoreCase = true) ||
        (themeMode.equals("system", ignoreCase = true) && isSystemInDarkTheme())
    val themeOptions = listOf(
        "system" to "Follow system",
        "light" to "Light",
        "dark" to "Dark",
        "amoled" to "AMOLED black",
        "nano" to "Nano Chromatic",
    )
    val accentOptions = listOf(
        "Cyan" to if (darkTheme) NanoCyan else Color(0xFF006B78),
        "Coral" to Color(0xFFE64A5D),
        "Red" to Color(0xFFD92338),
        "Orange" to Color(0xFFE66B1E),
        "Yellow" to Color(0xFFF2C230),
        "Green" to Color(0xFF54A96A),
        "Blue" to Color(0xFF208BCE),
        "Purple" to Color(0xFF8758B8),
        "Pink" to Color(0xFFD95791),
        "Silver" to Color(0xFF9AA4AE),
        "Graphite" to Color(0xFF454B54),
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            Modifier.heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
        ) {
            Text(
                "THEME",
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            themeOptions.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onThemeModeChange(value); onDismiss() },
                    trailingIcon = {
                        if (themeMode.equals(value, ignoreCase = true)) Text("✓", color = MaterialTheme.colorScheme.primary)
                    },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(
                "TEXT COLOR",
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            accentOptions.forEach { (name, swatch) ->
                val selected = accentName.equals(name, ignoreCase = true) ||
                    (name == "Cyan" && accentName.equals("Chromatic", ignoreCase = true))
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onAccentChange(name); onDismiss() },
                    leadingIcon = {
                        Box(
                            Modifier.size(18.dp).clip(CircleShape)
                                .background(swatch)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                        )
                    },
                    trailingIcon = {
                        if (selected) Text("✓", color = MaterialTheme.colorScheme.primary)
                    },
                )
            }
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
    LazyColumn(state = listState, contentPadding = PaddingValues(end = 8.dp, bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
                BrowseRow(item.artworkUri, item.title, "${displayValue(item.artist, "Unknown artist")} · ${countLabel(item.trackCount, "song")}", artworkRepository) { onOpenAlbum(item) }
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
            Text(countLabel(playlist.trackCount, "song"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            TextButton(onClick = { menuOpen = true }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("⋮") }
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
                Text(if (playlist.isAuto) "↻" else "♫", style = MaterialTheme.typography.titleLarge)
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
                Text("↻", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
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
            Text(if (playlist.isAuto) "↻" else "♫", style = MaterialTheme.typography.labelSmall)
        }
    }
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
) {
    val context = LocalContext.current
    val nanoMode = MaterialTheme.colorScheme.background == NanoBackground
    val rowShape = RoundedCornerShape(15.dp)
    var moreMenuOpen by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(rowShape)
            .background(if (nanoMode) if (playing) Color(0xFF122A40) else NanoSurface else Color.Transparent)
            .then(if (nanoMode) Modifier.border(1.dp, if (playing) NanoBlue.copy(alpha = 0.55f) else NanoOutline.copy(alpha = 0.45f), rowShape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(vertical = if (nanoMode) 8.dp else 6.dp, horizontal = if (nanoMode) 8.dp else 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) Checkbox(checked = selected, onCheckedChange = { onClick() }, modifier = Modifier.size(52.dp)) else AlbumTile(track, artworkRepository, Modifier.size(52.dp).padding(end = 0.dp))
        Column(Modifier.weight(1f).padding(start = 11.dp)) {
            Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(displayValue(track.artist, "Unknown artist"), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (playing) Text("♫", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 10.dp))
        if (showReorder) {
            Text("⠿", modifier = Modifier.pointerInput(canMoveUp, canMoveDown) {
                var dragDistance = 0f
                detectDragGesturesAfterLongPress(onDragEnd = { dragDistance = 0f }, onDragCancel = { dragDistance = 0f }) { change, dragAmount ->
                    change.consume()
                    dragDistance += dragAmount.y
                    if (dragDistance > 48.dp.toPx() && canMoveDown) { onMoveDown(); dragDistance = 0f }
                    if (dragDistance < -48.dp.toPx() && canMoveUp) { onMoveUp(); dragDistance = 0f }
                }
            }.padding(horizontal = 6.dp))
        }
        Box {
            TextButton(onClick = { moreMenuOpen = true }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = "More actions for ${track.title}" }) { Text("⋮") }
            DropdownMenu(expanded = moreMenuOpen, onDismissRequest = { moreMenuOpen = false }) {
                DropdownMenuItem(text = { Text("Play next") }, onClick = { moreMenuOpen = false; onPlayNext() })
                DropdownMenuItem(text = { Text("Add to queue") }, onClick = { moreMenuOpen = false; onQueue() })
                DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { moreMenuOpen = false; onAdd() })
                if (showReorder || showRemove) DropdownMenuItem(text = { Text("Remove from playlist") }, onClick = { moreMenuOpen = false; onRemove() })
                if (!showReorder) DropdownMenuItem(text = { Text(if (isFavorite) "Remove from favorites" else "Add to favorites") }, onClick = { moreMenuOpen = false; onFavorite() })
                DropdownMenuItem(text = { Text("Go to artist") }, onClick = { moreMenuOpen = false; onGoArtist() })
                DropdownMenuItem(text = { Text("Go to album") }, onClick = { moreMenuOpen = false; onGoAlbum() })
                DropdownMenuItem(text = { Text("Edit tags") }, onClick = {
                    moreMenuOpen = false
                    Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show()
                })
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
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(send, "Share song"))
                })
            }
        }
    }
    if (showDetails) {
        val format = track.mimeType.substringAfter('/', "").takeIf { it.isNotBlank() }?.uppercase() ?: track.displayName.substringAfterLast('.', "").uppercase().ifBlank { "Unknown" }
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text("Song details") },
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

private fun formatAudioSize(bytes: Long): String = when {
    bytes <= 0 -> "Unknown"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
    return if (totalSeconds >= 3_600) {
        "%d:%02d:%02d".format(totalSeconds / 3_600, totalSeconds / 60 % 60, totalSeconds % 60)
    } else {
        "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }
}

@Composable
fun MiniPlayer(playback: PlaybackSnapshot, artworkRepository: ArtworkRepository, onClick: () -> Unit, onPlayPause: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    val track = playback.currentTrack ?: return
    val nanoMode = MaterialTheme.colorScheme.background == NanoBackground
    val playerShape = RoundedCornerShape(18.dp)
    var horizontalDrag by remember(track.uri) { mutableStateOf(0f) }
    Surface(modifier.fillMaxWidth().clip(playerShape).then(if (nanoMode) Modifier.border(2.dp, NanoAccentBrush, playerShape) else Modifier).pointerInput(track.uri) {
        detectHorizontalDragGestures(
            onDragEnd = {
                if (horizontalDrag > 48f) onNext()
                else if (horizontalDrag < -48f) onPrevious()
                horizontalDrag = 0f
            },
            onHorizontalDrag = { change, amount -> change.consume(); horizontalDrag += amount },
        )
    }, color = MaterialTheme.colorScheme.surface, shape = playerShape, tonalElevation = 4.dp) {
        Column {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
                    AlbumTile(track, artworkRepository, Modifier.size(46.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(track.title, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(displayValue(track.artist, "Unknown artist"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
                TextButton(onClick = onPlayPause) { Text(if (playback.isPlaying) "❚❚" else "▶", color = MaterialTheme.colorScheme.primary) }
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
    AlbumArtwork(track.uri, track.title, artworkRepository, modifier)
}

@Composable
private fun BrowseRow(
    artworkUri: android.net.Uri?,
    title: String,
    subtitle: String,
    artworkRepository: ArtworkRepository,
    artistInitialFallback: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val nanoMode = MaterialTheme.colorScheme.background == NanoBackground
    val rowShape = RoundedCornerShape(if (nanoMode) 16.dp else 14.dp)
    Row(
        Modifier.fillMaxWidth().clip(rowShape)
            .background(if (nanoMode) NanoSurface else Color.Transparent)
            .then(if (nanoMode) Modifier.border(1.dp, NanoOutline.copy(alpha = 0.65f), rowShape) else Modifier)
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (artworkUri != null) AlbumArtwork(artworkUri, title, artworkRepository, Modifier.size(48.dp), fallbackInitial = artistInitialFallback)
        else Box(
            Modifier.size(if (nanoMode) 50.dp else 48.dp).clip(RoundedCornerShape(if (nanoMode) 14.dp else 12.dp))
                .then(if (nanoMode) Modifier.background(NanoAccentBrush) else Modifier.background(MaterialTheme.colorScheme.tertiaryContainer)),
            contentAlignment = Alignment.Center,
        ) {
            Text(title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "♫", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailingContent?.invoke()
    }
}

@Composable
private fun AlphaIndexRail(names: List<String>, listState: androidx.compose.foundation.lazy.LazyListState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        (listOf('#') + ('A'..'Z').toList()).forEach { letter ->
            Text(letter.toString(), modifier = Modifier.clickable {
                val target = names.indexOfFirst { librarySection(it) == letter }
                if (target >= 0) scope.launch { listState.animateScrollToItem(target) }
            }.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun countLabel(count: Int, singular: String): String = if (count == 1) "1 $singular" else "$count ${singular}s"

private fun displayValue(value: String, fallback: String): String =
    value.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) && it.none { char -> char == '?' || char == '\uFFFD' } } ?: fallback

@Composable
private fun EmptyPanel(icon: String, title: String, subtitle: String, button: String? = null, onClick: (() -> Unit)? = null) {
    val nanoMode = MaterialTheme.colorScheme.background == NanoBackground
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        if (nanoMode) {
            Box(Modifier.size(96.dp).clip(RoundedCornerShape(30.dp)).background(NanoAccentBrush).padding(2.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(NanoSurface), contentAlignment = Alignment.Center) {
                    Text(icon, style = MaterialTheme.typography.displaySmall, color = NanoText)
                }
            }
        } else {
            Box(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.tertiary).padding(24.dp)) { Text(icon, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onTertiary) }
        }
        Spacer(Modifier.height(16.dp)); Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp)); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (button != null && onClick != null) { Spacer(Modifier.height(16.dp)); Button(onClick = onClick) { Text(button) } }
    }
}

@Composable
private fun NanoFilterChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier.clip(shape)
            .then(if (selected) Modifier.background(NanoAccentBrush) else Modifier.background(NanoSurfaceRaised))
            .then(if (selected) Modifier else Modifier.border(1.dp, NanoOutline, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp),
    ) {
        Text(label, color = if (selected) MaterialTheme.colorScheme.onPrimary else NanoMuted, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}
