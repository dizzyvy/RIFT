package com.dizzyvy.sjmusicapp.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dizzyvy.sjmusicapp.music.artwork.ArtworkRepository
import com.dizzyvy.sjmusicapp.music.library.AlbumBrowseItem
import com.dizzyvy.sjmusicapp.music.library.ArtistBrowseItem
import com.dizzyvy.sjmusicapp.music.library.DevicePlaylist
import com.dizzyvy.sjmusicapp.music.library.librarySection
import com.dizzyvy.sjmusicapp.music.model.AudioTrack
import com.dizzyvy.sjmusicapp.music.playback.PlaybackSnapshot
import com.dizzyvy.sjmusicapp.ui.components.AlbumArtwork
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    playback: PlaybackSnapshot,
    artworkRepository: ArtworkRepository,
    onSearch: (String) -> Unit,
    onSortOrder: (String) -> Unit,
    onHideShortTracks: (Boolean) -> Unit,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
    onPlayTrack: (AudioTrack) -> Unit,
    onAddToQueue: (AudioTrack) -> Unit,
    onOpenPlayer: () -> Unit,
    onPlayPause: () -> Unit,
    onShuffleAll: () -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onCategory: (String) -> Unit,
    onOpenArtist: (ArtistBrowseItem) -> Unit,
    onOpenAlbum: (AlbumBrowseItem) -> Unit,
    onOpenPlaylist: (DevicePlaylist) -> Unit,
    onCreatePlaylist: (String, List<AudioTrack>) -> Unit,
    onRenamePlaylist: (DevicePlaylist, String) -> Unit,
    onDeletePlaylist: (DevicePlaylist) -> Unit,
    onImportM3u: () -> Unit,
    onExportM3u: (DevicePlaylist) -> Unit,
    onAddTrackToPlaylist: (DevicePlaylist, AudioTrack) -> Unit,
    onAddTracksToPlaylist: (DevicePlaylist, List<AudioTrack>) -> Unit,
    onPlayPlaylist: (Boolean) -> Unit,
    onRemoveTrackFromPlaylist: (AudioTrack) -> Unit,
    onMovePlaylistTrack: (Int, Int) -> Unit,
    onBackFromGroup: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val isScrolled = listState.firstVisibleItemIndex > 0
    var pendingTracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var selectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var tracksToAddOnCreate by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var playlistToRename by remember { mutableStateOf<DevicePlaylist?>(null) }
    var playlistToDelete by remember { mutableStateOf<DevicePlaylist?>(null) }
    var playlistNameInput by remember { mutableStateOf("") }
    val songs = state.browseTracks ?: state.visibleTracks
    val selectedTracks = state.tracks.filter { it.uri.toString() in selectedUris }
    LaunchedEffect(state.category, state.browseTitle) { selectedUris = emptySet() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 18.dp)) {
        if (state.browseTitle != null) {
            TextButton(onClick = onBackFromGroup, modifier = Modifier.padding(top = 2.dp)) { Text("‹  ${state.category.uppercase()}") }
            Text(state.browseTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
            if (state.activePlaylist?.isLocal == true) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onPlayPlaylist(false) }, modifier = Modifier.weight(1f)) { Text("Play all") }
                    OutlinedButton(onClick = { onPlayPlaylist(true) }, modifier = Modifier.weight(1f)) { Text("Shuffle") }
                }
            }
        } else if (!isScrolled) {
            Spacer(Modifier.height(5.dp))
            Text("SJ MUSIC", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("Your music", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
        if (state.browseTitle == null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Songs", "Artists", "Albums", "Playlists").forEach { tab ->
                    FilterChip(selected = state.category == tab, onClick = { onCategory(tab) }, label = { Text(tab) })
                }
                if (state.category == "Playlists") {
                    TextButton(onClick = { tracksToAddOnCreate = emptyList(); playlistNameInput = ""; showCreateDialog = true }) { Text("+ New") }
                    TextButton(onClick = onImportM3u) { Text("Import") }
                }
            }
            if (selectedTracks.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${selectedTracks.size} selected", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { pendingTracks = selectedTracks; showAddSheet = true }) { Text("Add to playlist") }
                    TextButton(onClick = { selectedUris = emptySet() }) { Text("Clear") }
                }
            }
            if (state.category in listOf("Songs", "Artists", "Albums", "Playlists")) {
                val placeholder = when (state.category) {
                    "Artists" -> "Search artists"
                    "Albums" -> "Search albums or artists"
                    "Playlists" -> "Search playlists"
                    else -> "Search songs, artists or albums"
                }
                OutlinedTextField(value = state.searchQuery, onValueChange = onSearch, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(15.dp), placeholder = { Text(placeholder) }, leadingIcon = { Text("⌕", style = MaterialTheme.typography.headlineSmall) })
                if (state.category == "Songs") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        var sortMenuOpen by remember { mutableStateOf(false) }
                        Box(Modifier.weight(1f)) {
                            TextButton(onClick = { sortMenuOpen = true }) { Text("Sort: ${state.sortOrder} ▾") }
                            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                listOf("Title", "Artist", "Date added", "Duration").forEach { order ->
                                    DropdownMenuItem(text = { Text(order) }, onClick = { onSortOrder(order); sortMenuOpen = false })
                                }
                            }
                        }
                        FilterChip(selected = state.hideShortTracks, onClick = { onHideShortTracks(!state.hideShortTracks) }, label = { Text("Hide under 30s") })
                    }
                    if (state.visibleTracks.isNotEmpty()) TextButton(onClick = onShuffleAll, modifier = Modifier.fillMaxWidth()) { Text("Shuffle all") }
                }
            }
        }
        when {
            state.permissionRequired -> EmptyPanel("♫", "Let your music in", "SJ Music scans audio stored on your phone and SD card. Enable Audio and music in App info if Android no longer shows the prompt.", "Set up audio access", onRequestPermission)
            state.isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.message != null -> EmptyPanel("!", "Library unavailable", state.message, "Scan again", onRetry)
            else -> {
                val hasRows = if (state.browseTitle != null) !state.browseTracks.isNullOrEmpty() else when (state.category) {
                    "Artists" -> state.visibleArtists.isNotEmpty()
                    "Albums" -> state.visibleAlbums.isNotEmpty()
                    "Playlists" -> state.visiblePlaylists.isNotEmpty()
                    else -> songs.isNotEmpty()
                }
                if (!hasRows) EmptyPanel(
                    "♫",
                    if (state.browseTitle != null) "No tracks found" else if (state.category == "Playlists") "No playlists yet" else "No music found",
                    if (state.browseTitle != null) "This collection has no available tracks."
                    else if (state.category == "Playlists") "Create your first playlist to keep songs together."
                    else "Add audio files to your phone or SD card, then scan again.",
                    if (state.browseTitle == null && state.category == "Playlists") "Create" else if (state.browseTitle == null) "Scan again" else null,
                    if (state.browseTitle == null && state.category == "Playlists") ({ tracksToAddOnCreate = emptyList(); playlistNameInput = ""; showCreateDialog = true })
                    else if (state.browseTitle == null) onRetry else null,
                )
                else Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
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
                                        showRemove = state.activePlaylist?.isLocal == true,
                                        onRemove = { onRemoveTrackFromPlaylist(track) },
                                        showReorder = state.activePlaylist?.isLocal == true,
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
                                items(state.visibleArtists, key = { it.id }) { item -> BrowseRow(item.artworkUri, item.name, countLabel(item.trackCount, "song"), artworkRepository, artistInitialFallback = true) { onOpenArtist(item) } }
                            }
                            if (state.searchQuery.isBlank()) AlphaIndexRail(state.visibleArtists.map { it.name }, listState, Modifier.align(Alignment.CenterEnd))
                        }
                        state.category == "Albums" -> Box(Modifier.fillMaxSize()) {
                            LazyColumn(state = listState, contentPadding = PaddingValues(end = 26.dp, bottom = 90.dp)) {
                                items(state.visibleAlbums, key = { it.id }) { item -> BrowseRow(item.artworkUri, item.title, "${displayValue(item.artist, "Unknown artist")} · ${countLabel(item.trackCount, "song")}", artworkRepository) { onOpenAlbum(item) } }
                            }
                            if (state.searchQuery.isBlank()) AlphaIndexRail(state.visibleAlbums.map { it.title }, listState, Modifier.align(Alignment.CenterEnd))
                        }
                        state.category == "Playlists" -> LazyColumn(state = listState, contentPadding = PaddingValues(end = 26.dp, bottom = 90.dp)) {
                            items(state.visiblePlaylists, key = { "${it.volumeName}:${it.id}" }) { item ->
                                PlaylistBrowseRow(
                                    playlist = item,
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
                if (playback.currentTrack != null) MiniPlayer(playback, artworkRepository, onOpenPlayer, onPlayPause, onPreviousTrack, onNextTrack, Modifier.padding(vertical = 7.dp))
            }
        }
    }
    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            Text(if (pendingTracks.size == 1) "Add ${pendingTracks.first().title}" else "Add ${pendingTracks.size} songs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 22.dp))
            TextButton(onClick = { pendingTracks.forEach(onAddToQueue); showAddSheet = false; pendingTracks = emptyList(); selectedUris = emptySet() }, modifier = Modifier.fillMaxWidth()) { Text("Add to queue") }
            TextButton(onClick = { tracksToAddOnCreate = pendingTracks; playlistNameInput = ""; showAddSheet = false; showCreateDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Create new playlist") }
            state.playlists.filter { it.isLocal }.forEach { playlist ->
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

@Composable
private fun PlaylistBrowseRow(
    playlist: DevicePlaylist,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpen).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
            Text(playlist.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "♫", style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(playlist.name, fontWeight = FontWeight.SemiBold)
            Text(if (playlist.isLocal) "Playlist" else "On this device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (playlist.isLocal) {
            Box {
                TextButton(onClick = { menuOpen = true }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("⋮") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; onRename() })
                    DropdownMenuItem(text = { Text("Export M3U") }, onClick = { menuOpen = false; onExport() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; onDelete() })
                }
            }
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
    showRemove: Boolean = false,
    onRemove: () -> Unit = {},
    showReorder: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).combinedClickable(onClick = onClick, onLongClick = onLongPress).padding(vertical = 6.dp, horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        if (selectionMode) Checkbox(checked = selected, onCheckedChange = { onClick() }, modifier = Modifier.size(52.dp)) else AlbumTile(track, artworkRepository, Modifier.size(52.dp).padding(end = 0.dp))
        Column(Modifier.weight(1f).padding(start = 11.dp)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(displayValue(track.artist, "Unknown artist"), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (playing) Text("♫", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 10.dp))
        if (showReorder) {
            Text("⠿", modifier = Modifier.pointerInput(canMoveUp, canMoveDown) {
                var dragDistance = 0f
                detectDragGesturesAfterLongPress(onDragEnd = { dragDistance = 0f }, onDragCancel = { dragDistance = 0f }) { change, dragAmount ->
                    change.consume()
                    dragDistance += dragAmount.y
                    if (dragDistance > 48f && canMoveDown) { onMoveDown(); dragDistance = 0f }
                    if (dragDistance < -48f && canMoveUp) { onMoveUp(); dragDistance = 0f }
                }
            }.padding(horizontal = 6.dp))
            TextButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.sizeIn(minWidth = 40.dp, minHeight = 48.dp)) { Text("↑") }
            TextButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.sizeIn(minWidth = 40.dp, minHeight = 48.dp)) { Text("↓") }
        }
        if (showRemove) TextButton(onClick = onRemove, modifier = Modifier.sizeIn(minWidth = 40.dp, minHeight = 48.dp).semantics { contentDescription = "Remove ${track.title} from playlist" }) { Text("×") }
        TextButton(onClick = onAdd, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = "Add ${track.title} to playlist or queue" }) { Text("+") }
    }
}

@Composable
fun MiniPlayer(playback: PlaybackSnapshot, artworkRepository: ArtworkRepository, onClick: () -> Unit, onPlayPause: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    val track = playback.currentTrack ?: return
    var horizontalDrag by remember(track.uri) { mutableStateOf(0f) }
    Surface(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).pointerInput(track.uri) {
        detectHorizontalDragGestures(
            onDragEnd = {
                if (horizontalDrag > 48f) onNext()
                else if (horizontalDrag < -48f) onPrevious()
                horizontalDrag = 0f
            },
            onHorizontalDrag = { change, amount -> change.consume(); horizontalDrag += amount },
        )
    }, color = Color(0xFF252A33), shape = RoundedCornerShape(18.dp), tonalElevation = 4.dp) {
        Column {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
                    AlbumTile(track, artworkRepository, Modifier.size(46.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(track.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(displayValue(track.artist, "Unknown artist"), color = Color(0xFFB7BEC8), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
                TextButton(onClick = onPlayPause) { Text(if (playback.isPlaying) "❚❚" else "▶", color = Color.White) }
            }
            LinearProgressIndicator(
                progress = { (playback.positionMs.toFloat() / playback.durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
        }
    }
}

@Composable
fun AlbumTile(track: AudioTrack, artworkRepository: ArtworkRepository, modifier: Modifier = Modifier) {
    AlbumArtwork(track.uri, track.title, artworkRepository, modifier)
}

@Composable
private fun BrowseRow(artworkUri: android.net.Uri?, title: String, subtitle: String, artworkRepository: ArtworkRepository, artistInitialFallback: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (artworkUri != null) AlbumArtwork(artworkUri, title, artworkRepository, Modifier.size(48.dp), fallbackInitial = artistInitialFallback)
        else Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
            Text(title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "♫", style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
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
    value.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) } ?: fallback

@Composable
private fun EmptyPanel(icon: String, title: String, subtitle: String, button: String? = null, onClick: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.tertiary).padding(24.dp)) { Text(icon, style = MaterialTheme.typography.headlineLarge, color = Color(0xFF34302A)) }
        Spacer(Modifier.height(16.dp)); Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp)); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (button != null && onClick != null) { Spacer(Modifier.height(16.dp)); Button(onClick = onClick) { Text(button) } }
    }
}
