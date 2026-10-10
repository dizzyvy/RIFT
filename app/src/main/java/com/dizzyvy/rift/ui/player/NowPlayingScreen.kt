package com.dizzyvy.rift.ui.player

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dizzyvy.rift.music.artwork.ArtworkRepository
import com.dizzyvy.rift.music.library.DevicePlaylist
import com.dizzyvy.rift.music.model.AudioTrack
import com.dizzyvy.rift.music.lyrics.LocalLyrics
import com.dizzyvy.rift.music.lyrics.LocalLyricsRepository
import com.dizzyvy.rift.music.playback.PlaybackSnapshot
import com.dizzyvy.rift.ui.components.AlbumArtwork
import com.dizzyvy.rift.ui.theme.RiftBackgroundBrush
import com.dizzyvy.rift.ui.theme.ShrikhandHeading
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalMaterial3Api::class)
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
    onRestoreQueueItem: (AudioTrack, Int) -> Unit,
    playlists: List<DevicePlaylist>,
    onAddTrackToPlaylist: (DevicePlaylist, AudioTrack) -> Unit,
    onCreatePlaylist: (String, List<AudioTrack>) -> Unit,
    onClearQueue: () -> Unit,
    isFavorite: Boolean,
    onFavorite: (AudioTrack, Boolean) -> Unit,
    onSetSleepTimer: (Long?, Boolean) -> Unit,
    onPlaybackSpeed: (Float) -> Unit,
    lyricsRepository: LocalLyricsRepository,
    lyricsDirectoryUri: Uri?,
    onChooseLyricsDirectory: () -> Unit,
    clickWheelSensitivity: Float,
    clickWheelHaptics: Boolean,
    onClickWheelSensitivityChange: (Float) -> Unit,
    onClickWheelHapticsChange: (Boolean) -> Unit,
) {
    val track = playback.currentTrack
    var loopStartMs by remember(track?.uri) { mutableStateOf<Long?>(null) }
    var loopEndMs by remember(track?.uri) { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    val queueSnackbarState = remember { SnackbarHostState() }
    var queueOpen by remember { mutableStateOf(false) }
    var confirmClearQueue by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var tracksToCreate by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var moreSheetOpen by remember { mutableStateOf(false) }
    var sleepTimerMinutes by remember { mutableStateOf(30) }
    var finishCurrentSong by remember { mutableStateOf(false) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var wheelSettingsOpen by remember { mutableStateOf(false) }
    var localWheelSensitivity by remember(clickWheelSensitivity) { mutableStateOf(clickWheelSensitivity) }
    if (track == null) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 22.dp)) {
            TextButton(onClick = onBack, modifier = Modifier.padding(top = 2.dp)) { Text("‹  LIBRARY") }
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                ShrikhandHeading("Nothing playing", MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onBack) { Text("Browse your music") }
            }
        }
        return
    }

    LaunchedEffect(playback.positionMs, playback.isPlaying, loopStartMs, loopEndMs) {
        val start = loopStartMs
        val end = loopEndMs
        if (playback.isPlaying && start != null && end != null && end > start && playback.positionMs >= end) {
            onSeek(start)
        }
    }

    var lyrics by remember(track.uri, lyricsDirectoryUri) { mutableStateOf<LocalLyrics?>(null) }
    var loadingLyrics by remember(track.uri, lyricsDirectoryUri) { mutableStateOf(false) }
    var lyricsError by remember(track.uri, lyricsDirectoryUri) { mutableStateOf(false) }
    var artworkAccent by remember(track.uri) { mutableStateOf<Color?>(null) }
    LaunchedEffect(track.uri) {
        artworkAccent = try {
            val bitmap = artworkRepository.load(track.uri)?.bitmap
            if (bitmap == null) null else sampleArtworkColor(bitmap)
        } catch (_: Exception) {
            null
        }
    }
    LaunchedEffect(track.uri, lyricsDirectoryUri) {
        lyrics = null
        lyricsError = false
        loadingLyrics = lyricsDirectoryUri != null
        if (lyricsDirectoryUri != null) {
            runCatching { lyricsRepository.load(track, lyricsDirectoryUri) }
                .onSuccess { lyrics = it }
                .onFailure { lyricsError = true }
            loadingLyrics = false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
    val artworkHeight = (maxHeight * 0.2f).coerceIn(100.dp, 160.dp)
    val topGradient = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.background,
        ),
    )
    Column(
        Modifier.fillMaxSize()
            .background(topGradient)
            .padding(horizontal = 22.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("‹  LIBRARY") }
            TextButton(onClick = { moreSheetOpen = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("More") }
        }
        ShrikhandHeading(
            "NOW PLAYING",
            MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        AlbumArtwork(track.uri, track.title, artworkRepository, Modifier.fillMaxWidth().height(artworkHeight).pointerInput(track.uri) {
            var drag = 0f
            detectHorizontalDragGestures(
                onDragEnd = { if (drag > 48f) onNext() else if (drag < -48f) onPrevious(); drag = 0f },
                onHorizontalDrag = { change, amount -> change.consume(); drag += amount },
            )
        })
        ShrikhandHeading(
            track.title,
            MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            maxLines = 2,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 22.sp),
        )
        val album = displayMetadata(track.album, "Unknown album")
        val artist = displayMetadata(track.artist, "Unknown artist")
        val metadata = if (album.equals(track.title.trim(), ignoreCase = true)) artist else "$artist  ·  $album"
        Text(metadata, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = { showPlaylistSheet = true }, label = { Text("Add to playlist") }, modifier = Modifier.heightIn(min = 48.dp))
            AssistChip(onClick = { onFavorite(track, !isFavorite) }, label = { Text(if (isFavorite) "♥ Favorite" else "♡ Favorite", color = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }, modifier = Modifier.heightIn(min = 48.dp).semantics {
                contentDescription = if (isFavorite) "Remove ${track.title} from favorites" else "Add ${track.title} to favorites"
            })
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Slider(value = playback.positionMs.toFloat().coerceIn(0f, playback.durationMs.coerceAtLeast(1L).toFloat()), onValueChange = { onSeek(it.toLong()) }, enabled = playback.durationMs > 0L, valueRange = 0f..playback.durationMs.coerceAtLeast(1L).toFloat(), modifier = Modifier.fillMaxWidth().height(36.dp), colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(playback.positionMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("-${formatTime((playback.durationMs - playback.positionMs).coerceAtLeast(0L))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = playback.shuffleEnabled, onClick = { onShuffle(!playback.shuffleEnabled) }, label = { Text(if (playback.shuffleEnabled) "Shuffle on" else "Shuffle off") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary))
            FilterChip(selected = playback.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF, onClick = { onRepeat(if (playback.repeatMode == androidx.media3.common.Player.REPEAT_MODE_OFF) androidx.media3.common.Player.REPEAT_MODE_ALL else if (playback.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ALL) androidx.media3.common.Player.REPEAT_MODE_ONE else androidx.media3.common.Player.REPEAT_MODE_OFF) }, label = {
                Text(when (playback.repeatMode) { androidx.media3.common.Player.REPEAT_MODE_ALL -> "Repeat all"; androidx.media3.common.Player.REPEAT_MODE_ONE -> "Repeat one"; else -> "Repeat off" })
            }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary))
        }
        ClickWheel(playing = playback.isPlaying, queueOpen = queueOpen, onMenu = onBack, onPrevious = onPrevious, onTogglePlayback = onPlayPause, onNext = onNext, onSelect = { queueOpen = true; moreSheetOpen = true }, sensitivity = clickWheelSensitivity, hapticsEnabled = clickWheelHaptics, compact = true, onRotate = { delta ->
            onSeek((playback.positionMs + (delta * 120_000).roundToInt()).coerceIn(0L, playback.durationMs.coerceAtLeast(0L)))
        })
    }
    SnackbarHost(hostState = queueSnackbarState, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
    if (moreSheetOpen) {
        ModalBottomSheet(onDismissRequest = { moreSheetOpen = false }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
                ShrikhandHeading("More playback options", MaterialTheme.typography.titleLarge)
                TextButton(onClick = { queueOpen = !queueOpen }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (queueOpen) "Hide Up Next" else "Show Up Next") }
                if (queueOpen) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("UP NEXT · ${playback.queue.size}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { playlistName = ""; tracksToCreate = playback.queue; showCreatePlaylistDialog = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Save as playlist") }
                        TextButton(onClick = { confirmClearQueue = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Clear") }
                    }
                    playback.queue.forEachIndexed { index, queued ->
                        QueueRow(track = queued, current = index == playback.currentIndex, canMoveUp = index > 0, canMoveDown = index < playback.queue.lastIndex, onSelect = { onPlayQueueItem(index) }, onRemove = {
                            onRemoveQueueItem(index)
                            scope.launch {
                                val result = queueSnackbarState.showSnackbar("Removed ${queued.title} from queue", "Undo", withDismissAction = true)
                                if (result == SnackbarResult.ActionPerformed) onRestoreQueueItem(queued, index)
                            }
                        }, onMoveUp = { if (index > 0) onMoveQueueItem(index, index - 1) }, onMoveDown = { if (index < playback.queue.lastIndex) onMoveQueueItem(index, index + 1) })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    FilterChip(selected = loopStartMs != null, onClick = { loopStartMs = playback.positionMs; loopEndMs = null }, label = { Text(loopStartMs?.let { "A · ${formatTime(it)}" } ?: "Set A") })
                    FilterChip(selected = loopEndMs != null, enabled = loopStartMs != null && playback.positionMs > (loopStartMs ?: Long.MAX_VALUE), onClick = { loopEndMs = playback.positionMs }, label = { Text(loopEndMs?.let { "B · ${formatTime(it)}" } ?: "Set B") })
                    if (loopStartMs != null || loopEndMs != null) AssistChip(onClick = { loopStartMs = null; loopEndMs = null }, label = { Text("Clear") })
                }
                Text("A–B repeat · Set A, then Set B to loop that section", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Playback speed", Modifier.weight(1f))
                    Box {
                        TextButton(onClick = { speedMenuOpen = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("${playback.playbackSpeed}×") }
                        DropdownMenu(expanded = speedMenuOpen, onDismissRequest = { speedMenuOpen = false }) {
                            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { speed ->
                                DropdownMenuItem(text = { Text("${speed}×") }, onClick = { onPlaybackSpeed(speed); speedMenuOpen = false })
                            }
                        }
                        if (confirmClearQueue) {
                            AlertDialog(
                                onDismissRequest = { confirmClearQueue = false },
                                title = { ShrikhandHeading("Clear the queue?", MaterialTheme.typography.headlineSmall) },
                                text = { Text("Remove all ${playback.queue.size} tracks from Up Next?") },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            onClearQueue()
                                            confirmClearQueue = false
                                        },
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    ) { Text("Clear queue") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { confirmClearQueue = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
                                },
                            )
                        }
                    }
                }
                AssistChip(onClick = { showSleepTimerDialog = true }, label = { Text(when {
                    playback.sleepTimerFinishingTrack -> "Sleep timer · finishing this song"
                    playback.sleepTimerRemainingMs != null -> "Sleep timer · ${((playback.sleepTimerRemainingMs + 59_999L) / 60_000L)} min"
                    else -> "Sleep timer"
                }) }, modifier = Modifier.heightIn(min = 48.dp))
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                TextButton(onClick = { wheelSettingsOpen = !wheelSettingsOpen }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (wheelSettingsOpen) "Hide click wheel settings" else "Click wheel settings") }
                if (wheelSettingsOpen) {
                    Text("Sensitivity · ${"%.2f".format(localWheelSensitivity)}×", style = MaterialTheme.typography.labelMedium)
                    Slider(value = localWheelSensitivity, onValueChange = { localWheelSensitivity = it }, onValueChangeFinished = { onClickWheelSensitivityChange(localWheelSensitivity) }, valueRange = 0.5f..2f, steps = 5, modifier = Modifier.semantics { contentDescription = "Click wheel sensitivity" })
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Haptic ticks", modifier = Modifier.weight(1f))
                        Switch(checked = clickWheelHaptics, onCheckedChange = onClickWheelHapticsChange, modifier = Modifier.semantics { contentDescription = "Click wheel haptic ticks" })
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                if (lyricsDirectoryUri != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("LYRICS", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onChooseLyricsDirectory) { Text("Change folder") }
                } else TextButton(onClick = onChooseLyricsDirectory, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Set up lyrics") }
                when {
                    lyricsDirectoryUri == null -> Unit
                    loadingLyrics -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    lyricsError -> Text("Could not read lyrics from that folder.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    lyrics?.timedLines?.isNotEmpty() == true -> {
                        val lines = lyrics!!.timedLines
                        val active = lines.indexOfLast { it.timeMs <= playback.positionMs }.coerceAtLeast(0)
                        (active - 2).coerceAtLeast(0).rangeTo((active + 2).coerceAtMost(lines.lastIndex)).forEach { index ->
                            Text(lines[index].text, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), textAlign = TextAlign.Center, style = if (index == active) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium, color = if (index == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    !lyrics?.plainText.isNullOrBlank() -> Text(lyrics!!.plainText, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, maxLines = 12, overflow = TextOverflow.Ellipsis)
                    else -> Text("No lyrics found for this song.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (playback.errorMessage != null) Text(playback.errorMessage, Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (showPlaylistSheet) {
        ModalBottomSheet(onDismissRequest = { showPlaylistSheet = false }) {
            ShrikhandHeading(
                "Add ${track.title} to playlist",
                MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
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
            title = { ShrikhandHeading("Sleep timer", MaterialTheme.typography.headlineSmall) },
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
            title = { ShrikhandHeading("Create playlist", MaterialTheme.typography.headlineSmall) },
            text = { OutlinedTextField(value = playlistName, onValueChange = { playlistName = it }, singleLine = true, label = { Text("Playlist name") }) },
            confirmButton = { TextButton(enabled = playlistName.isNotBlank(), onClick = { onCreatePlaylist(playlistName.trim(), tracksToCreate); tracksToCreate = emptyList(); showCreatePlaylistDialog = false }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun QueueRow(
    track: AudioTrack,
    current: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Surface(
        color = if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .pointerInput(track.uri) {
                var horizontalDrag = 0f
                var handled = false
                detectHorizontalDragGestures(
                    onDragEnd = { horizontalDrag = 0f; handled = false },
                    onDragCancel = { horizontalDrag = 0f; handled = false },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        horizontalDrag += amount
                        if (!handled && kotlin.math.abs(horizontalDrag) >= 100.dp.toPx()) {
                            handled = true
                            onRemove()
                        }
                    },
                )
            }
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 2.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f).heightIn(min = 48.dp)
                    .clickable(onClick = onSelect)
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center,
            ) {
                    Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface, fontWeight = if (current) FontWeight.Bold else FontWeight.Medium)
                    val artist = track.artist.takeIf { it.isNotBlank() && !it.equals("<unknown>", true) } ?: "Unknown artist"
                    Text(if (current) "NOW PLAYING · $artist" else artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                Modifier.size(48.dp)
                    .semantics { contentDescription = "Drag to reorder ${track.title}" }
                    .pointerInput(track.uri, canMoveUp, canMoveDown) {
                        var accumulatedY = 0f
                        detectDragGesturesAfterLongPress(
                            onDragEnd = { accumulatedY = 0f },
                            onDragCancel = { accumulatedY = 0f },
                        ) { change, amount ->
                            change.consume()
                            accumulatedY += amount.y
                            if (accumulatedY > 52.dp.toPx() && canMoveDown) {
                                onMoveDown()
                                accumulatedY = 0f
                            } else if (accumulatedY < -52.dp.toPx() && canMoveUp) {
                                onMoveUp()
                                accumulatedY = 0f
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Text("≡", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            TextButton(
                onClick = onRemove,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = "Remove ${track.title} from queue" },
            ) { Text("×") }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1_000L
    return if (seconds >= 3_600L) {
        "%d:%02d:%02d".format(seconds / 3_600L, seconds / 60L % 60L, seconds % 60L)
    } else {
        "%d:%02d".format(seconds / 60L, seconds % 60L)
    }
}

private fun displayMetadata(value: String, fallback: String): String =
    value.takeIf { it.isNotBlank() && !it.equals("<unknown>", true) && it.none { char -> char == '?' || char == '\uFFFD' } } ?: fallback

private suspend fun sampleArtworkColor(bitmap: android.graphics.Bitmap): Color? = withContext(Dispatchers.Default) {
    if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return@withContext null
    val stepX = (bitmap.width / 16).coerceAtLeast(1)
    val stepY = (bitmap.height / 16).coerceAtLeast(1)
    var red = 0L
    var green = 0L
    var blue = 0L
    var count = 0L
    for (y in 0 until bitmap.height step stepY) {
        for (x in 0 until bitmap.width step stepX) {
            val pixel = bitmap.getPixel(x, y)
            val r = android.graphics.Color.red(pixel)
            val g = android.graphics.Color.green(pixel)
            val b = android.graphics.Color.blue(pixel)
            if (maxOf(r, g, b) < 36 || maxOf(r, g, b) - minOf(r, g, b) < 18) continue
            red += r
            green += g
            blue += b
            count++
        }
    }
    if (count == 0L) null else Color(
        red.toFloat() / count / 255f,
        green.toFloat() / count / 255f,
        blue.toFloat() / count / 255f,
    )
}
