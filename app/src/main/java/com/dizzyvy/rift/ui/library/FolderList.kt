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
internal fun FolderList(
    items: List<LibraryCollectionItem>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    hidden: Boolean,
    onHide: (LibraryCollectionItem) -> Unit,
    onUnhide: (LibraryCollectionItem) -> Unit,
    onOpen: (LibraryCollectionItem) -> Unit,
) {
    LazyColumn(state = listState, contentPadding = PaddingValues(end = 26.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items(items, key = { "folder:${it.id}" }) { item ->
            var menuOpen by remember(item.id) { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = !hidden) { onOpen(item) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val parentPath = item.id.substringBeforeLast('/', "Root").ifBlank { "Root" }
                    Text(parentPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                    Text(countLabel(item.trackCount, "song"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (hidden) {
                        Text("Subfolders are hidden too", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "Folder actions for ${item.title}" },
                    ) { Icon(Icons.Default.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        if (!hidden) {
                            DropdownMenuItem(text = { Text("Open") }, onClick = { menuOpen = false; onOpen(item) })
                        }
                        DropdownMenuItem(
                            text = { Text(if (hidden) "Unhide" else "Hide") },
                            onClick = {
                                menuOpen = false
                                if (hidden) onUnhide(item) else onHide(item)
                            },
                        )
                    }
                }
            }
        }
    }
}
