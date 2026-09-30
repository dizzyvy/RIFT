package com.dizzyvy.sjmusicapp.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dizzyvy.sjmusicapp.music.artwork.ArtworkRepository
import com.dizzyvy.sjmusicapp.music.model.AudioTrack
import com.dizzyvy.sjmusicapp.music.playback.PlaybackSnapshot
import com.dizzyvy.sjmusicapp.ui.components.AlbumArtwork
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun NowPlayingScreen(
    playback: PlaybackSnapshot,
    artworkRepository: ArtworkRepository,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onPlayQueueItem: (Int) -> Unit,
    onShuffle: (Boolean) -> Unit,
    onRepeat: (Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
) {
    val track = playback.currentTrack
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var queueOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(scrollState).padding(horizontal = 22.dp)) {
        TextButton(onClick = onBack, modifier = Modifier.padding(top = 2.dp)) { Text("‹  LIBRARY") }
        if (track == null) {
            Column(Modifier.fillMaxWidth().height(400.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nothing playing", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                TextButton(onClick = onBack) { Text("Browse your music") }
            }
            return
        }
        Text("NOW PLAYING", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        AlbumArtwork(track.uri, track.title, artworkRepository, Modifier.fillMaxWidth().height(280.dp))
        Spacer(Modifier.height(18.dp))
        Text(track.title, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text("${track.artist.ifBlank { "Unknown artist" }}  ·  ${track.album.ifBlank { "Unknown album" }}", modifier = Modifier.fillMaxWidth().padding(top = 4.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        Slider(value = playback.positionMs.toFloat().coerceIn(0f, playback.durationMs.coerceAtLeast(1L).toFloat()), onValueChange = { onSeek(it.toLong()) }, valueRange = 0f..playback.durationMs.coerceAtLeast(1L).toFloat(), modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(playback.positionMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("-${formatTime((playback.durationMs - playback.positionMs).coerceAtLeast(0L))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onShuffle(!playback.shuffleEnabled) }) { Text(if (playback.shuffleEnabled) "🔀 ON" else "🔀") }
            TextButton(onClick = { onRepeat(if (playback.repeatMode == androidx.media3.common.Player.REPEAT_MODE_OFF) androidx.media3.common.Player.REPEAT_MODE_ALL else if (playback.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ALL) androidx.media3.common.Player.REPEAT_MODE_ONE else androidx.media3.common.Player.REPEAT_MODE_OFF) }) {
                Text(when (playback.repeatMode) { androidx.media3.common.Player.REPEAT_MODE_ALL -> "REPEAT ALL"; androidx.media3.common.Player.REPEAT_MODE_ONE -> "REPEAT ONE"; else -> "REPEAT OFF" })
            }
        }
        ClickWheel(playing = playback.isPlaying, queueOpen = queueOpen, onMenu = onBack, onPrevious = onPrevious, onTogglePlayback = onPlayPause, onNext = onNext, onSelect = { queueOpen = !queueOpen }, onRotate = { delta ->
            if (queueOpen) scope.launch { scrollState.scrollTo((scrollState.value + (delta * 2400).roundToInt()).coerceIn(0, scrollState.maxValue)) }
            else onSeek((playback.positionMs + (delta * 120_000).roundToInt()).coerceIn(0L, playback.durationMs.coerceAtLeast(0L)))
        })
        if (playback.errorMessage != null) Text(playback.errorMessage, Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        if (queueOpen) {
            Spacer(Modifier.height(4.dp))
            Text("UP NEXT  ·  ${playback.queue.size} TRACKS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            playback.queue.forEachIndexed { index, queued ->
                QueueRow(track = queued, current = index == playback.currentIndex, canMoveUp = index > 0, canMoveDown = index < playback.queue.lastIndex, onSelect = { onPlayQueueItem(index) }, onRemove = { onRemoveQueueItem(index) }, onMoveUp = { if (index > 0) onMoveQueueItem(index, index - 1) }, onMoveDown = { if (index < playback.queue.lastIndex) onMoveQueueItem(index, index + 1) })
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun QueueRow(track: AudioTrack, current: Boolean, canMoveUp: Boolean, canMoveDown: Boolean, onSelect: () -> Unit, onRemove: () -> Unit, onMoveUp: () -> Unit, onMoveDown: () -> Unit) {
    Surface(color = if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onSelect, modifier = Modifier.weight(1f)) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface, fontWeight = if (current) FontWeight.Bold else FontWeight.Medium)
                    Text(if (current) "NOW PLAYING · ${track.artist.ifBlank { "Unknown artist" }}" else track.artist.ifBlank { "Unknown artist" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.semantics { contentDescription = "Move ${track.title} earlier in queue" }) { Text("↑") }
            TextButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.semantics { contentDescription = "Move ${track.title} later in queue" }) { Text("↓") }
            TextButton(onClick = onRemove, modifier = Modifier.semantics { contentDescription = "Remove ${track.title} from queue" }) { Text("×") }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1_000L
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}
