package com.dizzyvy.sjmusicapp.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
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
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
    onPlayTrack: (AudioTrack) -> Unit,
    onAddToQueue: (AudioTrack) -> Unit,
    onOpenPlayer: () -> Unit,
    onPlayPause: () -> Unit,
    onCategory: (String) -> Unit,
    onOpenArtist: (ArtistBrowseItem) -> Unit,
    onOpenAlbum: (AlbumBrowseItem) -> Unit,
    onOpenPlaylist: (DevicePlaylist) -> Unit,
    onBackFromGroup: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val songs = state.browseTracks ?: state.visibleTracks
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 18.dp)) {
        if (state.browseTitle != null) TextButton(onClick = onBackFromGroup, modifier = Modifier.padding(top = 2.dp)) { Text("‹  ${state.category.uppercase()}") }
        else {
            Spacer(Modifier.height(5.dp))
            Text("SJ MUSIC", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("Your music", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("ON THIS DEVICE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(state.browseTitle ?: "${state.category} on this device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 5.dp, bottom = 8.dp))
        if (state.browseTitle == null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Songs", "Artists", "Albums", "Playlists").forEach { tab ->
                    FilterChip(selected = state.category == tab, onClick = { onCategory(tab) }, label = { Text(tab) })
                }
            }
            if (state.category == "Songs") {
                OutlinedTextField(value = state.searchQuery, onValueChange = onSearch, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(15.dp), placeholder = { Text("Search songs or artists") }, leadingIcon = { Text("⌕", style = MaterialTheme.typography.headlineSmall) })
            }
        }
        when {
            state.permissionRequired -> EmptyPanel("♫", "Let your music in", "SJ Music scans audio stored on your phone and SD card. Enable Audio and music in App info if Android no longer shows the prompt.", "Set up audio access", onRequestPermission)
            state.isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.message != null -> EmptyPanel("!", "Library unavailable", state.message, "Scan again", onRetry)
            else -> {
                val hasRows = if (state.browseTitle != null) !state.browseTracks.isNullOrEmpty() else when (state.category) {
                    "Artists" -> state.artists.isNotEmpty()
                    "Albums" -> state.albums.isNotEmpty()
                    "Playlists" -> state.playlists.isNotEmpty()
                    else -> songs.isNotEmpty()
                }
                if (!hasRows) EmptyPanel("♫", if (state.browseTitle != null) "No tracks found" else if (state.category == "Playlists") "No playlists found" else "No music found", "Add audio files to your phone or SD card, then scan again.", if (state.browseTitle == null) "Scan again" else null, if (state.browseTitle == null) onRetry else null)
                else Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        state.browseTitle != null -> {
                            LazyColumn(state = listState, contentPadding = PaddingValues(end = 22.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                items(songs, key = { it.uri.toString() }) { track -> TrackRow(track, artworkRepository, playback.currentTrack?.uri == track.uri && playback.isPlaying, { onPlayTrack(track) }, { onAddToQueue(track) }) }
                            }
                        }
                        state.category == "Artists" -> LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 12.dp)) { items(state.artists, key = { it.id }) { item -> BrowseRow("◉", item.name, "${item.trackCount} songs") { onOpenArtist(item) } } }
                        state.category == "Albums" -> LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 12.dp)) { items(state.albums, key = { it.id }) { item -> BrowseRow("▣", item.title, "${item.artist.ifBlank { "Unknown artist" }} · ${item.trackCount} songs") { onOpenAlbum(item) } } }
                        state.category == "Playlists" -> LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 12.dp)) { items(state.playlists, key = { "${it.volumeName}:${it.id}" }) { item -> BrowseRow("♫", item.name, "On this device") { onOpenPlaylist(item) } } }
                        else -> {
                            val headerCount = 1
                            LazyColumn(state = listState, contentPadding = PaddingValues(end = 22.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                item { Text("${songs.size} SONGS", Modifier.padding(start = 5.dp, top = 7.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                items(songs, key = { it.uri.toString() }) { track -> TrackRow(track, artworkRepository, playback.currentTrack?.uri == track.uri && playback.isPlaying, { onPlayTrack(track) }, { onAddToQueue(track) }) }
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
                if (playback.currentTrack != null) MiniPlayer(playback, artworkRepository, onOpenPlayer, onPlayPause, Modifier.padding(vertical = 7.dp))
            }
        }
    }
}

@Composable
private fun TrackRow(track: AudioTrack, artworkRepository: ArtworkRepository, playing: Boolean, onClick: () -> Unit, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).clickable(onClick = onClick).padding(vertical = 6.dp, horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        AlbumTile(track, artworkRepository, Modifier.size(52.dp).padding(end = 0.dp))
        Column(Modifier.weight(1f).padding(start = 11.dp)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(track.artist.ifBlank { "Unknown artist" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (playing) Text("♫", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 10.dp))
        TextButton(onClick = onAdd, modifier = Modifier.semantics { contentDescription = "Add ${track.title} to queue" }) { Text("+") }
    }
}

@Composable
fun MiniPlayer(playback: PlaybackSnapshot, artworkRepository: ArtworkRepository, onClick: () -> Unit, onPlayPause: () -> Unit, modifier: Modifier = Modifier) {
    val track = playback.currentTrack ?: return
    Surface(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)), color = Color(0xFF252A33), shape = RoundedCornerShape(18.dp), tonalElevation = 4.dp) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
                AlbumTile(track, artworkRepository, Modifier.size(46.dp))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(track.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(track.artist.ifBlank { "Unknown artist" }, color = Color(0xFFB7BEC8), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
            TextButton(onClick = onPlayPause) { Text(if (playback.isPlaying) "❚❚" else "▶", color = Color.White) }
        }
    }
}

@Composable
fun AlbumTile(track: AudioTrack, artworkRepository: ArtworkRepository, modifier: Modifier = Modifier) {
    AlbumArtwork(track.uri, track.title, artworkRepository, modifier)
}

@Composable
private fun BrowseRow(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) { Text(icon, style = MaterialTheme.typography.titleLarge) }
        Column(Modifier.padding(start = 12.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun EmptyPanel(icon: String, title: String, subtitle: String, button: String? = null, onClick: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.tertiary).padding(24.dp)) { Text(icon, style = MaterialTheme.typography.headlineLarge, color = Color(0xFF34302A)) }
        Spacer(Modifier.height(16.dp)); Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp)); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (button != null && onClick != null) { Spacer(Modifier.height(16.dp)); Button(onClick = onClick) { Text(button) } }
    }
}
