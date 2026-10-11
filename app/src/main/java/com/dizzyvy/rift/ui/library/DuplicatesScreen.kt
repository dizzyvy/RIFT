package com.dizzyvy.rift.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dizzyvy.rift.music.model.AudioTrack
import com.dizzyvy.rift.ui.theme.RiftOutlinedButton
import com.dizzyvy.rift.ui.theme.RiftTextButton
import com.dizzyvy.rift.ui.theme.ShrikhandHeading
import com.dizzyvy.rift.ui.format.formatTime

@Composable
internal fun DuplicatesScreen(
    groups: List<DuplicateGroup>,
    listState: LazyListState,
    onAction: (PendingDuplicateAction) -> Unit,
    onBulkDelete: (List<AudioTrack>) -> Unit,
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(end = 26.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        val lowerQualityTracks = groups
            .flatMap { group ->
                val best = recommendedDuplicateCopy(group.tracks)
                group.tracks.filterNot { it.uri == best?.uri }
            }
            .distinctBy { it.uri }
        val reclaimableBytes = groups.sumOf(::reclaimableDuplicateBytes)
        item {
            Column(
                Modifier.fillMaxWidth().padding(start = 5.dp, top = 7.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "${groups.size} groups · ~${formatAudioSize(reclaimableBytes)} reclaimable",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (lowerQualityTracks.isNotEmpty()) {
                    Button(
                        onClick = { onBulkDelete(lowerQualityTracks) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text("Remove ${lowerQualityTracks.size} lower-quality copies (frees ~${formatAudioSize(reclaimableBytes)})")
                    }
                }
            }
        }
        items(groups, key = { "duplicate-group:${it.id}" }) { group ->
            var menuOpen by remember(group.id) { mutableStateOf(false) }
            var fullPathTrack by remember(group.id) { mutableStateOf<AudioTrack?>(null) }
            val bestCopy = recommendedDuplicateCopy(group.tracks)
            val groupReclaimableBytes = reclaimableDuplicateBytes(group)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            group.tracks.first().title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${group.tracks.size} copies · can free ~${formatAudioSize(groupReclaimableBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { menuOpen = true },
                            modifier = Modifier.size(48.dp).semantics { contentDescription = "Duplicate group actions" },
                        ) { Icon(Icons.Default.MoreVert, contentDescription = null) }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                            containerColor = MaterialTheme.colorScheme.surface,
                        ) {
                            DropdownMenuItem(
                                text = { Text("Not a duplicate") },
                                onClick = {
                                    menuOpen = false
                                    onAction(PendingDuplicateAction.NotDuplicate(group))
                                },
                            )
                        }
                    }
                }
                Text(group.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                group.tracks.forEach { track ->
                    Column(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            "${track.title} · ${track.mimeType.substringAfter('/', "").uppercase().ifBlank { track.displayName.substringAfterLast('.', "").uppercase().ifBlank { "Unknown format" } }} · ${if (track.bitrate > 0) "${track.bitrate} kbps" else "Unknown bitrate"} · ${formatAudioSize(track.sizeBytes)} · ${formatTime(track.durationMs)} · ${formatTrackDate(track.dateAddedSeconds)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                        if (track.uri == bestCopy?.uri) {
                            Text("Best quality", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        val shortPath = track.filePath.replace('\\', '/').split('/').filter(String::isNotBlank).takeLast(2).joinToString("/")
                        Text(
                            shortPath.ifBlank { track.uri.toString() },
                            Modifier.combinedClickable(onClick = {}, onLongClick = { fullPathTrack = track }),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onAction(PendingDuplicateAction.Keep(group, track)) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text("Keep this one") }
                            RiftOutlinedButton(
                                onClick = { onAction(PendingDuplicateAction.Delete(group, track)) },
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) { Text("Delete this one") }
                        }
                    }
                    if (track != group.tracks.last()) HorizontalDivider()
                }
            }
            fullPathTrack?.let { selectedTrack ->
                AlertDialog(
                    onDismissRequest = { fullPathTrack = null },
                    title = { ShrikhandHeading("File location", MaterialTheme.typography.headlineSmall) },
                    text = { Text(selectedTrack.filePath.ifBlank { selectedTrack.uri.toString() }) },
                    confirmButton = { RiftTextButton(onClick = { fullPathTrack = null }) { Text("Done") } },
                )
            }
        }
    }
}
