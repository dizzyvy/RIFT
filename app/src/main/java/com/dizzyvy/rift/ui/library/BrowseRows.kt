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
internal fun CollectionList(
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

@Composable
internal fun SearchResults(
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
internal fun PlaylistBrowseRow(
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
internal fun PlaylistCoverMosaic(playlist: DevicePlaylist, artworkRepository: ArtworkRepository) {
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
internal fun PlaylistCoverCell(uri: Uri?, playlist: DevicePlaylist, artworkRepository: ArtworkRepository) {
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
internal fun PlaylistKindIcon(
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
internal fun BrowseRow(
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
internal fun FastScrollRail(
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

@Composable
internal fun EmptyPanel(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, button: String? = null, onClick: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.tertiary).padding(24.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onTertiary)
        }
        Spacer(Modifier.height(16.dp)); Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp)); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (button != null && onClick != null) { Spacer(Modifier.height(16.dp)); Button(onClick = onClick) { Text(button) } }
    }
}
