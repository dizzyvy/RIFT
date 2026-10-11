package com.dizzyvy.rift.ui.player

import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
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
import com.dizzyvy.rift.ui.format.formatTime
import com.dizzyvy.rift.ui.theme.RiftBackgroundBrush
import com.dizzyvy.rift.ui.theme.ShrikhandHeading
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val WHEEL_SEEK_INTERVAL_MS = 80L

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
    onSetHighFrequencyPosition: (Boolean) -> Unit,
    onSetAbLoop: (Long?, Long?) -> Unit,
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
    var scrubPositionMs by remember(track?.uri) { mutableStateOf<Float?>(null) }
    val wheelSeekPositionMs = remember { longArrayOf(0L) }
    val lastWheelSeekAtMs = remember { longArrayOf(0L) }
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
    var lyricsPanelOpen by remember(track?.uri) { mutableStateOf(false) }
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

    DisposableEffect(onSetHighFrequencyPosition, onSetAbLoop) {
        onSetHighFrequencyPosition(true)
        onDispose {
            onSetHighFrequencyPosition(false)
            onSetAbLoop(null, null)
        }
    }
    LaunchedEffect(track?.uri) {
        loopStartMs = null
        loopEndMs = null
        onSetAbLoop(null, null)
    }

    var lyrics by remember(track.uri, lyricsDirectoryUri) { mutableStateOf<LocalLyrics?>(null) }
    var loadingLyrics by remember(track.uri, lyricsDirectoryUri) { mutableStateOf(false) }
    var lyricsError by remember(track.uri, lyricsDirectoryUri) { mutableStateOf(false) }
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
    val shortLayout = maxHeight < 640.dp
    val artworkSize = minOf((maxWidth - 48.dp).coerceAtLeast(96.dp), maxHeight * 0.4f, 340.dp)
    val wheelSize = if (shortLayout) 160.dp else minOf(maxWidth * 0.78f, 320.dp, maxHeight * 0.35f)
    val lyricsListState = rememberLazyListState()
    val topGradient = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.background,
        ),
    )
    Column(
        Modifier.fillMaxSize()
            .background(topGradient)
            .then(if (shortLayout) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = 22.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("‹ Library") }
            if (!shortLayout) Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { moreSheetOpen = true },
                modifier = Modifier.size(48.dp).semantics { contentDescription = "More playback options" },
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (!shortLayout) {
            Text(
                "NOW PLAYING",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (lyricsPanelOpen) {
                Surface(
                    modifier = Modifier.size(artworkSize).clickable { lyricsPanelOpen = false },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    when {
                        loadingLyrics -> Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        lyricsError -> Box(contentAlignment = Alignment.Center) {
                            Text("Could not read lyrics from that folder.", color = MaterialTheme.colorScheme.error)
                        }
                        lyrics?.timedLines?.isNotEmpty() == true -> {
                            val lines = lyrics!!.timedLines
                            val active = lines.indexOfLast { it.timeMs <= playback.positionMs }.coerceAtLeast(0)
                            LaunchedEffect(active) {
                                lyricsListState.animateScrollToItem((active - 2).coerceAtLeast(0))
                            }
                            LazyColumn(
                                state = lyricsListState,
                                modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                itemsIndexed(lines) { index, line ->
                                    Text(
                                        line.text,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        textAlign = TextAlign.Center,
                                        style = if (index == active) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                                        color = if (index == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        !lyrics?.plainText.isNullOrBlank() -> Text(
                            lyrics!!.plainText,
                            modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        else -> Box(contentAlignment = Alignment.Center) {
                            Text("No lyrics found for this song.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                AlbumArtwork(
                    track.uri,
                    track.title,
                    artworkRepository,
                    Modifier.size(artworkSize)
                        .shadow(10.dp, RoundedCornerShape(20.dp))
                        .clickable { lyricsPanelOpen = true }
                        .pointerInput(track.uri) {
                            var drag = 0f
                            detectHorizontalDragGestures(
                                onDragEnd = { if (drag > 48f) onNext() else if (drag < -48f) onPrevious(); drag = 0f },
                                onHorizontalDrag = { change, amount -> change.consume(); drag += amount },
                            )
                        },
                    contentScale = ContentScale.Fit,
                    sourceDateModifiedSeconds = track.dateModifiedSeconds,
                    cornerRadius = 20.dp,
                )
            }
        }
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
        Text(metadata, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onFavorite(track, !isFavorite) }, modifier = Modifier.size(48.dp).semantics {
                contentDescription = if (isFavorite) "Remove ${track.title} from favorites" else "Add ${track.title} to favorites"
            }) {
                Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null, tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = { showPlaylistSheet = true }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Add to playlist" }) {
                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = { queueOpen = true }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Open Up Next" }) {
                Icon(Icons.Default.QueueMusic, contentDescription = null, tint = if (queueOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = { onShuffle(!playback.shuffleEnabled) }, modifier = Modifier.size(48.dp).semantics { contentDescription = if (playback.shuffleEnabled) "Turn shuffle off" else "Turn shuffle on" }) {
                Icon(Icons.Default.Shuffle, contentDescription = null, tint = if (playback.shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            IconButton(
                onClick = {
                    onRepeat(
                        when (playback.repeatMode) {
                            androidx.media3.common.Player.REPEAT_MODE_OFF -> androidx.media3.common.Player.REPEAT_MODE_ALL
                            androidx.media3.common.Player.REPEAT_MODE_ALL -> androidx.media3.common.Player.REPEAT_MODE_ONE
                            else -> androidx.media3.common.Player.REPEAT_MODE_OFF
                        },
                    )
                },
                modifier = Modifier.size(48.dp).semantics {
                    contentDescription = when (playback.repeatMode) {
                        androidx.media3.common.Player.REPEAT_MODE_ALL -> "Repeat all"
                        androidx.media3.common.Player.REPEAT_MODE_ONE -> "Repeat one"
                        else -> "Repeat off"
                    }
                },
            ) {
                Icon(Icons.Default.Repeat, contentDescription = null, tint = if (playback.repeatMode == androidx.media3.common.Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary)
            }
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Slider(
                value = (scrubPositionMs ?: playback.positionMs.toFloat())
                    .coerceIn(0f, playback.durationMs.coerceAtLeast(1L).toFloat()),
                onValueChange = { scrubPositionMs = it },
                onValueChangeFinished = {
                    scrubPositionMs?.let { onSeek(it.toLong()) }
                    scrubPositionMs = null
                },
                enabled = playback.durationMs > 0L,
                valueRange = 0f..playback.durationMs.coerceAtLeast(1L).toFloat(),
                modifier = Modifier.fillMaxWidth().height(36.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                ),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val displayedPosition = (scrubPositionMs ?: playback.positionMs.toFloat()).toLong()
            Text(formatTime(displayedPosition), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("-${formatTime((playback.durationMs - displayedPosition).coerceAtLeast(0L))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        playback.errorMessage?.let { Text(it, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        Spacer(Modifier.weight(1f))
        ClickWheel(
            playing = playback.isPlaying,
            queueOpen = queueOpen,
            onMenu = onBack,
            onPrevious = onPrevious,
            onTogglePlayback = onPlayPause,
            onNext = onNext,
            onSelect = { queueOpen = true },
            sensitivity = clickWheelSensitivity,
            hapticsEnabled = clickWheelHaptics,
            compact = shortLayout,
            size = wheelSize,
            onRotateStart = {
                wheelSeekPositionMs[0] = playback.positionMs
                lastWheelSeekAtMs[0] = 0L
            },
            onRotate = { delta ->
                val rotationMs = (playback.durationMs / 4L).coerceIn(20_000L, 120_000L)
                wheelSeekPositionMs[0] = (wheelSeekPositionMs[0] + (delta * rotationMs).roundToInt())
                    .coerceIn(0L, playback.durationMs.coerceAtLeast(0L))
                val now = SystemClock.elapsedRealtime()
                if (now - lastWheelSeekAtMs[0] >= WHEEL_SEEK_INTERVAL_MS) {
                    onSeek(wheelSeekPositionMs[0])
                    lastWheelSeekAtMs[0] = now
                }
            },
            onRotateEnd = {
                onSeek(wheelSeekPositionMs[0])
                lastWheelSeekAtMs[0] = SystemClock.elapsedRealtime()
            },
        )
        if (shortLayout) Spacer(Modifier.height(8.dp)) else Spacer(Modifier.weight(1f))
    }
    SnackbarHost(hostState = queueSnackbarState, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
    if (moreSheetOpen) {
        ModalBottomSheet(onDismissRequest = { moreSheetOpen = false }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
                ShrikhandHeading("More playback options", MaterialTheme.typography.titleLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    FilterChip(
                        selected = loopStartMs != null,
                        onClick = {
                            loopStartMs = playback.positionMs
                            loopEndMs = null
                            onSetAbLoop(loopStartMs, null)
                        },
                        label = { Text(loopStartMs?.let { "A · ${formatTime(it)}" } ?: "Set A") },
                    )
                    FilterChip(
                        selected = loopEndMs != null,
                        enabled = loopStartMs != null && playback.positionMs > (loopStartMs ?: Long.MAX_VALUE),
                        onClick = {
                            loopEndMs = playback.positionMs
                            onSetAbLoop(loopStartMs, loopEndMs)
                        },
                        label = { Text(loopEndMs?.let { "B · ${formatTime(it)}" } ?: "Set B") },
                    )
                    if (loopStartMs != null || loopEndMs != null) AssistChip(
                        onClick = {
                            loopStartMs = null
                            loopEndMs = null
                            onSetAbLoop(null, null)
                        },
                        label = { Text("Clear") },
                    )
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
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (queueOpen) {
        val queueListState = rememberLazyListState()
        LaunchedEffect(playback.currentIndex, playback.queue.size) {
            if (playback.currentIndex >= 0) {
                queueListState.animateScrollToItem((playback.currentIndex + 1).coerceAtMost(playback.queue.size))
            }
        }
        ModalBottomSheet(onDismissRequest = { queueOpen = false }) {
            val queueDurationMs = playback.queue.sumOf { it.durationMs.coerceAtLeast(0L) }
            LazyColumn(
                state = queueListState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
            ) {
                item(key = "queue-header") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            ShrikhandHeading("Up Next", MaterialTheme.typography.titleLarge)
                            Text(
                                "${playback.queue.size} tracks · ${formatTime(queueDurationMs)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(
                            onClick = { playlistName = ""; tracksToCreate = playback.queue; showCreatePlaylistDialog = true },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Save as playlist") }
                        TextButton(
                            onClick = { confirmClearQueue = true },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Clear") }
                    }
                }
                itemsIndexed(playback.queue, key = { index, queued -> "queue:${queued.uri}:$index" }) { index, queued ->
                    QueueRow(
                        track = queued,
                        current = index == playback.currentIndex,
                        canMoveUp = index > 0,
                        canMoveDown = index < playback.queue.lastIndex,
                        onSelect = { onPlayQueueItem(index) },
                        onRemove = {
                            onRemoveQueueItem(index)
                            scope.launch {
                                val result = queueSnackbarState.showSnackbar(
                                    "Removed ${queued.title} from queue",
                                    "Undo",
                                    withDismissAction = true,
                                )
                                if (result == SnackbarResult.ActionPerformed) onRestoreQueueItem(queued, index)
                            }
                        },
                        onMoveUp = { if (index > 0) onMoveQueueItem(index, index - 1) },
                        onMoveDown = { if (index < playback.queue.lastIndex) onMoveQueueItem(index, index + 1) },
                    )
                }
            }
        }
    }
    if (confirmClearQueue) {
        AlertDialog(
            onDismissRequest = { confirmClearQueue = false },
            title = { ShrikhandHeading("Clear the queue?", MaterialTheme.typography.headlineSmall) },
            text = { Text("Remove all ${playback.queue.size} tracks from Up Next?") },
            confirmButton = {
                TextButton(onClick = {
                    onClearQueue()
                    confirmClearQueue = false
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Clear queue") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearQueue = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
            },
        )
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (current) "Playing next · $artist" else artist,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            formatTime(track.durationMs),
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
            ) { Icon(Icons.Default.Menu, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "Remove ${track.title} from queue" },
            ) { Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
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
