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
internal fun TrackRow(
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
    onShareFailure: () -> Unit = {},
) {
    val context = LocalContext.current
    val rowShape = RoundedCornerShape(15.dp)
    var moreMenuOpen by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(rowShape)
            .background(if (playing) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), rowShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(vertical = 6.dp, horizontal = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) Checkbox(checked = selected, onCheckedChange = { onClick() }, modifier = Modifier.size(48.dp)) else AlbumTile(track, artworkRepository, Modifier.size(48.dp))
        Column(Modifier.weight(1f).padding(start = 11.dp)) {
            Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(displayValue(track.artist, "Unknown artist"), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(formatTime(track.durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp))
        if (playing) AnimatedEqualizer(Modifier.padding(horizontal = 4.dp))
        if (showReorder) {
            Box(
                Modifier.size(48.dp).semantics { contentDescription = "Drag to reorder ${track.title}" }
                    .pointerInput(canMoveUp, canMoveDown) {
                        var dragDistance = 0f
                        detectDragGesturesAfterLongPress(onDragEnd = { dragDistance = 0f }, onDragCancel = { dragDistance = 0f }) { change, dragAmount ->
                            change.consume()
                            dragDistance += dragAmount.y
                            if (dragDistance > 48.dp.toPx() && canMoveDown) { onMoveDown(); dragDistance = 0f }
                            if (dragDistance < -48.dp.toPx() && canMoveUp) { onMoveUp(); dragDistance = 0f }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Menu, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box {
            IconButton(onClick = { moreMenuOpen = true }, modifier = Modifier.size(48.dp).semantics { contentDescription = "More actions for ${track.title}" }) {
                Icon(Icons.Default.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            }
            DropdownMenu(expanded = moreMenuOpen, onDismissRequest = { moreMenuOpen = false }) {
                DropdownMenuItem(text = { Text("Play next") }, onClick = { moreMenuOpen = false; onPlayNext() })
                DropdownMenuItem(text = { Text("Add to queue") }, onClick = { moreMenuOpen = false; onQueue() })
                DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { moreMenuOpen = false; onAdd() })
                if (showReorder || showRemove) DropdownMenuItem(text = { Text("Remove from playlist") }, onClick = { moreMenuOpen = false; onRemove() })
                if (!showReorder) DropdownMenuItem(text = { Text(if (isFavorite) "Remove from favorites" else "Add to favorites") }, onClick = { moreMenuOpen = false; onFavorite() })
                DropdownMenuItem(text = { Text("Go to artist") }, onClick = { moreMenuOpen = false; onGoArtist() })
                DropdownMenuItem(text = { Text("Go to album") }, onClick = { moreMenuOpen = false; onGoAlbum() })
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
                        clipData = ClipData.newRawUri("", track.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(Intent.createChooser(send, "Share song")) }
                        .onFailure { onShareFailure() }
                })
            }
        }
    }
    if (showDetails) {
        val format = track.mimeType.substringAfter('/', "").takeIf { it.isNotBlank() }?.uppercase() ?: track.displayName.substringAfterLast('.', "").uppercase().ifBlank { "Unknown" }
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { ShrikhandHeading("Song details", MaterialTheme.typography.headlineSmall) },
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

@Composable
internal fun AnimatedEqualizer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "playing-indicator")
    val color = MaterialTheme.colorScheme.primary
    val first by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "bar-one",
    )
    val second by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(560), RepeatMode.Reverse),
        label = "bar-two",
    )
    val third by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(360), RepeatMode.Reverse),
        label = "bar-three",
    )
    Canvas(
        modifier.size(18.dp, 18.dp).semantics { contentDescription = "Now playing" },
    ) {
        val barWidth = 3.dp.toPx()
        val gap = 2.dp.toPx()
        val heights = listOf(first, second, third)
        heights.forEachIndexed { index, fraction ->
            val barHeight = size.height * fraction
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (barWidth + gap), size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f),
            )
        }
    }
}

@Composable
fun AlbumTile(track: AudioTrack, artworkRepository: ArtworkRepository, modifier: Modifier = Modifier) {
    AlbumArtwork(
        track.uri,
        track.title,
        artworkRepository,
        modifier,
        sourceDateModifiedSeconds = track.dateModifiedSeconds,
    )
}
