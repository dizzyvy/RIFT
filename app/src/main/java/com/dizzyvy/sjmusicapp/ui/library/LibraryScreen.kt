package com.dizzyvy.sjmusicapp.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dizzyvy.sjmusicapp.music.model.AudioTrack
import com.dizzyvy.sjmusicapp.music.playback.PlaybackSnapshot

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    playback: PlaybackSnapshot,
    onSearch: (String) -> Unit,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
    onPlayTrack: (AudioTrack) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))
        Text("SJ MUSIC", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text("Your music", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("ON THIS DEVICE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            placeholder = { Text("Search songs or artists") },
            leadingIcon = { Text("⌕", style = MaterialTheme.typography.headlineSmall) },
        )
        Spacer(Modifier.height(12.dp))
        when {
            state.permissionRequired -> EmptyPanel(
                icon = "♫",
                title = "Let your music in",
                subtitle = "SJ Music scans audio stored on your phone and SD card. If Android stops showing this prompt, enable Audio and music in App info.",
                button = "Set up audio access",
                onClick = onRequestPermission,
            )
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            state.message != null -> EmptyPanel("!", "Library unavailable", state.message, "Scan again", onRetry)
            state.tracks.isEmpty() -> EmptyPanel("♫", "No music found", "Add audio files to your phone or SD card, then reopen SJ Music.")
            state.visibleTracks.isEmpty() -> EmptyPanel("⌕", "No matches", "Try a different title or artist.")
            else -> {
                Text("${state.visibleTracks.size} SONGS", Modifier.padding(start = 4.dp, bottom = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    items(state.visibleTracks, key = { it.id to it.uri.toString() }) { track ->
                        TrackRow(track = track, playing = playback.currentTrack?.uri == track.uri && playback.isPlaying, onClick = { onPlayTrack(track) })
                    }
                }
                if (playback.currentTrack != null) {
                    MiniPlayer(playback, onOpenPlayer, Modifier.padding(vertical = 10.dp))
                }
            }
        }
    }
}

@Composable
private fun TrackRow(track: AudioTrack, playing: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumTile(track.title, Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(track.artist.ifBlank { "Unknown artist" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (playing) Text("♫", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 12.dp))
    }
}

@Composable
fun MiniPlayer(playback: PlaybackSnapshot, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val track = playback.currentTrack ?: return
    Surface(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick), color = Color(0xFF252A33), shape = RoundedCornerShape(18.dp), tonalElevation = 4.dp) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AlbumTile(track.title)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(track.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(track.artist.ifBlank { "Unknown artist" }, color = Color(0xFFB7BEC8), style = MaterialTheme.typography.bodySmall)
            }
            Text(if (playback.isPlaying) "❚❚" else "▶", color = Color.White)
        }
    }
}

@Composable
fun AlbumTile(title: String, modifier: Modifier = Modifier) {
    val colors = listOf(Color(0xFFE95865), Color(0xFF438CCD), Color(0xFFFFC833), Color(0xFF50A982), Color(0xFF9B75BC))
    val color = colors[(title.hashCode().toUInt().toLong() % colors.size).toInt()]
    Box(modifier.padding(2.dp).clip(RoundedCornerShape(13.dp)).background(color).padding(12.dp), contentAlignment = Alignment.Center) {
        Text("♫", color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyPanel(icon: String, title: String, subtitle: String, button: String? = null, onClick: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.tertiary).padding(24.dp)) {
            Text(icon, style = MaterialTheme.typography.headlineLarge, color = Color(0xFF34302A))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (button != null && onClick != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onClick) { Text(button) }
        }
    }
}
