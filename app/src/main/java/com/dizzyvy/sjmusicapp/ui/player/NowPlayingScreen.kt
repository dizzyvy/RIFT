package com.dizzyvy.sjmusicapp.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dizzyvy.sjmusicapp.music.artwork.ArtworkRepository
import com.dizzyvy.sjmusicapp.music.library.DevicePlaylist
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
    playlists: List<DevicePlaylist>,
    onAddTrackToPlaylist: (DevicePlaylist, AudioTrack) -> Unit,
    onCreatePlaylist: (String, List<AudioTrack>) -> Unit,
    onClearQueue: () -> Unit,
    isFavorite: Boolean,
    onFavorite: (AudioTrack, Boolean) -> Unit,
    onSetSleepTimer: (Long?, Boolean) -> Unit,
    onPlaybackSpeed: (Float) -> Unit,
) {
    val track = playback.currentTrack
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var queueOpen by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var tracksToCreate by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var sleepTimerMinutes by remember { mutableStateOf(30) }
    var finishCurrentSong by remember { mutableStateOf(false) }
    var speedMenuOpen by remember { mutableStateOf(false) }
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
        AlbumArtwork(track.uri, track.title, artworkRepository, Modifier.fillMaxWidth().height(280.dp).pointerInput(track.uri) {
            var drag = 0f
            detectHorizontalDragGestures(
                onDragEnd = { if (drag > 48f) onNext() else if (drag < -48f) onPrevious(); drag = 0f },
                onHorizontalDrag = { change, amount -> change.consume(); drag += amount },
            )
        })
        Spacer(Modifier.height(18.dp))
        Text(track.title, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text("${track.artist.takeIf { it.isNotBlank() && !it.equals("<unknown>", true) } ?: "Unknown artist"}  ·  ${track.album.takeIf { it.isNotBlank() && !it.equals("<unknown>", true) } ?: "Unknown album"}", modifier = Modifier.fillMaxWidth().padding(top = 4.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { showPlaylistSheet = true }) { Text("Add to playlist") }
            TextButton(onClick = { onFavorite(track, !isFavorite) }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics {
                contentDescription = if (isFavorite) "Remove ${track.title} from favorites" else "Add ${track.title} to favorites"
            }) {
                Text(if (isFavorite) "♥ Favorite" else "♡ Favorite", color = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
            Box {
                TextButton(onClick = { speedMenuOpen = true }) { Text("${playback.playbackSpeed}×") }
                DropdownMenu(expanded = speedMenuOpen, onDismissRequest = { speedMenuOpen = false }) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { speed ->
                        DropdownMenuItem(text = { Text("${speed}×") }, onClick = { onPlaybackSpeed(speed); speedMenuOpen = false })
                    }
                }
            }
        }
        TextButton(onClick = { showSleepTimerDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text(when {
                playback.sleepTimerFinishingTrack -> "Sleep timer · finishing this song"
                playback.sleepTimerRemainingMs != null -> "Sleep timer · ${((playback.sleepTimerRemainingMs + 59_999L) / 60_000L)} min"
                else -> "Sleep timer"
            })
        }
        ClickWheel(playing = playback.isPlaying, queueOpen = queueOpen, onMenu = onBack, onPrevious = onPrevious, onTogglePlayback = onPlayPause, onNext = onNext, onSelect = { queueOpen = !queueOpen }, onRotate = { delta ->
            if (queueOpen) scope.launch { scrollState.scrollTo((scrollState.value + (delta * 2400).roundToInt()).coerceIn(0, scrollState.maxValue)) }
            else onSeek((playback.positionMs + (delta * 120_000).roundToInt()).coerceIn(0L, playback.durationMs.coerceAtLeast(0L)))
        })
        if (playback.errorMessage != null) Text(playback.errorMessage, Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        if (queueOpen) {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("QUEUE · ${playback.queue.size}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { playlistName = ""; tracksToCreate = playback.queue; showCreatePlaylistDialog = true }) { Text("Save as playlist") }
                TextButton(onClick = onClearQueue) { Text("Clear") }
            }
            playback.queue.forEachIndexed { index, queued ->
                QueueRow(track = queued, current = index == playback.currentIndex, canMoveUp = index > 0, canMoveDown = index < playback.queue.lastIndex, onSelect = { onPlayQueueItem(index) }, onRemove = { onRemoveQueueItem(index) }, onMoveUp = { if (index > 0) onMoveQueueItem(index, index - 1) }, onMoveDown = { if (index < playback.queue.lastIndex) onMoveQueueItem(index, index + 1) }, dragModifier = Modifier.pointerInput(index, playback.queue.size) {
                    var accumulatedY = 0f
                    detectDragGesturesAfterLongPress(
                        onDragEnd = { accumulatedY = 0f },
                        onDragCancel = { accumulatedY = 0f },
                        onDrag = { change, amount ->
                            change.consume()
                            accumulatedY += amount.y
                            if (accumulatedY > 52f && index < playback.queue.lastIndex) {
                                onMoveQueueItem(index, index + 1)
                                accumulatedY = 0f
                            } else if (accumulatedY < -52f && index > 0) {
                                onMoveQueueItem(index, index - 1)
                                accumulatedY = 0f
                            }
                        },
                    )
                })
            }
        }
        Spacer(Modifier.height(20.dp))
    }
    if (showPlaylistSheet) {
        ModalBottomSheet(onDismissRequest = { showPlaylistSheet = false }) {
            Text("Add ${track.title} to playlist", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 22.dp))
            TextButton(onClick = { playlistName = ""; tracksToCreate = listOf(track); showPlaylistSheet = false; showCreatePlaylistDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Create new playlist") }
            playlists.filter { it.isLocal && (!it.isAuto || it.autoKind == "favorites") }.forEach { playlist ->
                TextButton(onClick = { onAddTrackToPlaylist(playlist, track); showPlaylistSheet = false }, modifier = Modifier.fillMaxWidth()) { Text("Add to ${playlist.name}") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (showSleepTimerDialog) {
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = { Text("Sleep timer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Stop playback after")
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(5, 10, 15).forEach { minutes ->
                            FilterChip(selected = sleepTimerMinutes == minutes, onClick = { sleepTimerMinutes = minutes }, label = { Text("$minutes") })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(30, 45, 60).forEach { minutes ->
                            FilterChip(selected = sleepTimerMinutes == minutes, onClick = { sleepTimerMinutes = minutes }, label = { Text("$minutes") })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = finishCurrentSong, onCheckedChange = { finishCurrentSong = it })
                        Text("Finish the current song")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSetSleepTimer(sleepTimerMinutes * 60_000L, finishCurrentSong)
                    showSleepTimerDialog = false
                }) { Text("Start timer") }
            },
            dismissButton = {
                Row {
                    if (playback.sleepTimerRemainingMs != null || playback.sleepTimerFinishingTrack) {
                        TextButton(onClick = {
                            onSetSleepTimer(null, false)
                            showSleepTimerDialog = false
                        }) { Text("Cancel timer") }
                    }
                    TextButton(onClick = { showSleepTimerDialog = false }) { Text("Close") }
                }
            },
        )
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Create playlist") },
            text = { OutlinedTextField(value = playlistName, onValueChange = { playlistName = it }, singleLine = true, label = { Text("Playlist name") }) },
            confirmButton = { TextButton(enabled = playlistName.isNotBlank(), onClick = { onCreatePlaylist(playlistName.trim(), tracksToCreate); tracksToCreate = emptyList(); showCreatePlaylistDialog = false }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun QueueRow(track: AudioTrack, current: Boolean, canMoveUp: Boolean, canMoveDown: Boolean, onSelect: () -> Unit, onRemove: () -> Unit, onMoveUp: () -> Unit, onMoveDown: () -> Unit) {
    Surface(color = if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onSelect, modifier = Modifier.weight(1f)) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface, fontWeight = if (current) FontWeight.Bold else FontWeight.Medium)
                    val artist = track.artist.takeIf { it.isNotBlank() && !it.equals("<unknown>", true) } ?: "Unknown artist"
                    Text(if (current) "NOW PLAYING · $artist" else artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
