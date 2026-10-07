package com.dizzyvy.sjmusicapp.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dizzyvy.sjmusicapp.music.artwork.ArtworkRepository
import com.dizzyvy.sjmusicapp.music.library.AlbumBrowseItem
import com.dizzyvy.sjmusicapp.music.library.ArtistBrowseItem
import com.dizzyvy.sjmusicapp.music.library.DevicePlaylist
import com.dizzyvy.sjmusicapp.music.library.librarySection
import com.dizzyvy.sjmusicapp.music.model.AudioTrack
import com.dizzyvy.sjmusicapp.music.playback.PlaybackSnapshot
import com.dizzyvy.sjmusicapp.ui.components.AlbumArtwork
import com.dizzyvy.sjmusicapp.ui.theme.NanoBackground
import com.dizzyvy.sjmusicapp.ui.theme.NanoBlue
import com.dizzyvy.sjmusicapp.ui.theme.NanoCyan
import com.dizzyvy.sjmusicapp.ui.theme.NanoGreen
import com.dizzyvy.sjmusicapp.ui.theme.NanoMuted
import com.dizzyvy.sjmusicapp.ui.theme.NanoOutline
import com.dizzyvy.sjmusicapp.ui.theme.NanoPink
import com.dizzyvy.sjmusicapp.ui.theme.NanoPurple
import com.dizzyvy.sjmusicapp.ui.theme.NanoSurface
import com.dizzyvy.sjmusicapp.ui.theme.NanoSurfaceRaised
import com.dizzyvy.sjmusicapp.ui.theme.NanoText
import kotlinx.coroutines.launch

private val NanoAccent = Brush.linearGradient(listOf(NanoCyan, NanoBlue, NanoPurple, NanoPink))
private val ScreenGlow = Brush.verticalGradient(listOf(Color(0xFF0B1B2B), NanoBackground, Color(0xFF050A10)))

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

    Column(Modifier.fillMaxSize().background(ScreenGlow).padding(horizontal = 18.dp)) {
        if (state.browseTitle != null) {
            TextButton(onClick = onBackFromGroup, colors = ButtonDefaults.textButtonColors(contentColor = NanoCyan)) {
                Text("‹  " + state.category.uppercase())
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Text("SJ MUSIC", color = NanoGreen, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Your music", color = NanoText, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                    Text(state.tracks.size.toString() + " tracks on this device", color = NanoMuted, style = MaterialTheme.typography.labelMedium)
                }
                Text("Appearance ▾", color = NanoGreen, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }

        if (state.browseTitle == null) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("Songs", "Artists", "Albums", "Playlists").forEach { tab ->
                    NanoChip(tab, state.category == tab) { onCategory(tab) }
                }
            }

            if (state.category == "Songs") {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearch,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    placeholder = { Text("Search songs, artists or albums", color = NanoMuted) },
                    leadingIcon = { Text("⌕", color = NanoCyan, style = MaterialTheme.typography.headlineSmall) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = NanoText,
                        unfocusedTextColor = NanoText,
                        focusedBorderColor = NanoBlue,
                        unfocusedBorderColor = NanoOutline,
                        cursorColor = NanoCyan,
                        focusedContainerColor = Color(0xFF0B1827),
                        unfocusedContainerColor = Color(0xFF0B1827),
                    ),
                )
                Spacer(Modifier.height(9.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Sort: Title ▾", color = NanoGreen, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Surface(shape = RoundedCornerShape(12.dp), color = NanoSurfaceRaised, border = BorderStroke(1.dp, NanoOutline)) {
                        Text("Hide under 30s", color = NanoMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Text(state.browseTitle, color = NanoText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        when {
            state.permissionRequired -> Box(Modifier.weight(1f).fillMaxWidth()) {
                EmptyPanel("♫", "Let your music in", "SJ Music scans audio stored on your phone and SD card. Enable Audio and music in App info if Android no longer shows the prompt.", "Set up audio access", onRequestPermission)
            }
            state.isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NanoCyan)
            }
            state.message != null -> Box(Modifier.weight(1f).fillMaxWidth()) {
                EmptyPanel("!", "Library unavailable", state.message, "Scan again", onRetry)
            }
            else -> {
                val hasRows = if (state.browseTitle != null) !state.browseTracks.isNullOrEmpty() else when (state.category) {
                    "Artists" -> state.artists.isNotEmpty()
                    "Albums" -> state.albums.isNotEmpty()
                    "Playlists" -> state.playlists.isNotEmpty()
                    else -> songs.isNotEmpty()
                }

                if (!hasRows) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        EmptyPanel(
                            "♫",
                            if (state.browseTitle != null) "No tracks found" else if (state.category == "Playlists") "No playlists found" else "No music found",
                            "Add audio files to your phone or SD card, then scan again.",
                            if (state.browseTitle == null) "Scan again" else null,
                            if (state.browseTitle == null) onRetry else null,
                        )
                    }
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            state.browseTitle != null -> LazyColumn(state = listState, contentPadding = PaddingValues(top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                items(songs, key = { it.uri.toString() }) { track ->
                                    TrackRow(track, artworkRepository, playback.currentTrack?.uri == track.uri && playback.isPlaying, { onPlayTrack(track) }, { onAddToQueue(track) })
                                }
                            }
                            state.category == "Artists" -> LazyColumn(state = listState, contentPadding = PaddingValues(top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                items(state.artists, key = { it.id }) { item -> BrowseRow("◉", item.name, item.trackCount.toString() + " songs") { onOpenArtist(item) } }
                            }
                            state.category == "Albums" -> LazyColumn(state = listState, contentPadding = PaddingValues(top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                items(state.albums, key = { it.id }) { item -> BrowseRow("▣", item.title, item.artist.ifBlank { "Unknown artist" } + " · " + item.trackCount + " songs") { onOpenAlbum(item) } }
                            }
                            state.category == "Playlists" -> LazyColumn(state = listState, contentPadding = PaddingValues(top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                items(state.playlists, key = { it.volumeName + ":" + it.id }) { item -> BrowseRow("♫", item.name, "On this device") { onOpenPlaylist(item) } }
                            }
                            else -> {
                                val headerCount = 1
                                LazyColumn(state = listState, contentPadding = PaddingValues(top = 8.dp, end = 22.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    item {
                                        Text(songs.size.toString() + " SONGS", Modifier.padding(start = 5.dp, top = 4.dp, bottom = 3.dp), color = NanoMuted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                    items(songs, key = { it.uri.toString() }) { track ->
                                        TrackRow(track, artworkRepository, playback.currentTrack?.uri == track.uri && playback.isPlaying, { onPlayTrack(track) }, { onAddToQueue(track) })
                                    }
                                }
                                if (state.searchQuery.isBlank()) {
                                    Column(Modifier.align(Alignment.CenterEnd), horizontalAlignment = Alignment.CenterHorizontally) {
                                        (listOf('#') + ('A'..'Z').toList()).forEach { letter ->
                                            Text(letter.toString(), modifier = Modifier.clickable {
                                                val target = songs.indexOfFirst { librarySection(it.title) == letter }
                                                if (target >= 0) scope.launch { listState.animateScrollToItem(target + headerCount) }
                                            }.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = NanoBlue)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (playback.currentTrack != null) {
                        MiniPlayer(playback, artworkRepository, onOpenPlayer, onPlayPause, Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NanoChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(13.dp)
    Box(Modifier.clip(shape).background(if (selected) NanoAccent else NanoSurfaceRaised).border(1.dp, if (selected) Color.Transparent else NanoOutline, shape).clickable(onClick = onClick).padding(horizontal = 15.dp, vertical = 9.dp)) {
        Text(label, color = if (selected) Color.White else NanoMuted, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun TrackRow(track: AudioTrack, artworkRepository: ArtworkRepository, playing: Boolean, onClick: () -> Unit, onAdd: () -> Unit) {
    val shape = RoundedCornerShape(15.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(if (playing) Color(0xFF122A40) else NanoSurface).border(1.dp, if (playing) NanoBlue.copy(alpha = 0.55f) else Color.Transparent, shape).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AlbumTile(track, artworkRepository, Modifier.size(54.dp))
        Column(Modifier.weight(1f).padding(start = 11.dp)) {
            Text(track.title, color = NanoText, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(track.artist.ifBlank { "Unknown artist" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = NanoMuted)
        }
        if (playing) Text("♫", color = NanoCyan, modifier = Modifier.padding(horizontal = 8.dp))
        TextButton(onClick = onAdd, modifier = Modifier.semantics { contentDescription = "Add " + track.title + " to queue" }, colors = ButtonDefaults.textButtonColors(contentColor = NanoPurple)) { Text("+") }
    }
}

@Composable
fun MiniPlayer(playback: PlaybackSnapshot, artworkRepository: ArtworkRepository, onClick: () -> Unit, onPlayPause: () -> Unit, modifier: Modifier = Modifier) {
    val track = playback.currentTrack ?: return
    val shape = RoundedCornerShape(20.dp)
    Box(modifier.fillMaxWidth().clip(shape).background(NanoAccent).padding(1.dp)) {
        Surface(Modifier.fillMaxWidth(), color = NanoSurface, shape = shape, tonalElevation = 8.dp) {
            Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
                    AlbumTile(track, artworkRepository, Modifier.size(48.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(track.title, color = NanoText, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(track.artist.ifBlank { "Unknown artist" }, color = NanoMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
                Box(Modifier.size(42.dp).clip(CircleShape).background(NanoAccent).clickable(onClick = onPlayPause), contentAlignment = Alignment.Center) {
                    Text(if (playback.isPlaying) "❚❚" else "▶", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AlbumTile(track: AudioTrack, artworkRepository: ArtworkRepository, modifier: Modifier = Modifier) {
    AlbumArtwork(track.uri, track.title, artworkRepository, modifier.clip(RoundedCornerShape(12.dp)))
}

@Composable
private fun BrowseRow(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(NanoSurface).border(1.dp, NanoOutline.copy(alpha = 0.65f), shape).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(NanoBlue.copy(alpha = 0.8f), NanoPurple.copy(alpha = 0.8f)))), contentAlignment = Alignment.Center) {
            Text(icon, color = Color.White, style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, color = NanoText, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = NanoMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EmptyPanel(icon: String, title: String, subtitle: String, button: String? = null, onClick: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).clip(RoundedCornerShape(30.dp)).background(NanoAccent).padding(2.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(NanoSurface), contentAlignment = Alignment.Center) {
                Text(icon, style = MaterialTheme.typography.displaySmall, color = NanoText)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = NanoText)
        Spacer(Modifier.height(7.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = NanoMuted, textAlign = TextAlign.Center)
        if (button != null && onClick != null) {
            Spacer(Modifier.height(18.dp))
            Box(Modifier.clip(RoundedCornerShape(16.dp)).background(NanoAccent).clickable(onClick = onClick).padding(horizontal = 25.dp, vertical = 12.dp)) {
                Text(button, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
