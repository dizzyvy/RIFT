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
fun MiniPlayer(playback: PlaybackSnapshot, artworkRepository: ArtworkRepository, onClick: () -> Unit, onPlayPause: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    val track = playback.currentTrack ?: return
    val playerShape = RoundedCornerShape(18.dp)
    var horizontalDrag by remember(track.uri) { mutableStateOf(0f) }
    Surface(
        modifier.fillMaxWidth().clip(playerShape)
            .graphicsLayer { translationX = horizontalDrag.coerceIn(-48f, 48f) }
            .pointerInput(track.uri) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (horizontalDrag > 48f) onNext()
                        else if (horizontalDrag < -48f) onPrevious()
                        horizontalDrag = 0f
                    },
                    onHorizontalDrag = { change, amount -> change.consume(); horizontalDrag += amount },
                )
            },
        color = MaterialTheme.colorScheme.surface,
        shape = playerShape,
        tonalElevation = 4.dp,
    ) {
        Column {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
                    AlbumTile(track, artworkRepository, Modifier.size(46.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(track.title, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(displayValue(track.artist, "Unknown artist"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Previous track" },
                ) { Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = onPlayPause, modifier = Modifier.size(48.dp).semantics {
                    contentDescription = if (playback.isPlaying) "Pause playback" else "Resume playback"
                }) {
                    Icon(if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Next track" },
                ) { Icon(Icons.Default.SkipNext, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            LinearProgressIndicator(
                progress = { if (playback.durationMs > 0L) (playback.positionMs.toFloat() / playback.durationMs.toFloat()).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            )
        }
    }
}
