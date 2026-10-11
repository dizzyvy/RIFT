package com.dizzyvy.rift.ui.library

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dizzyvy.rift.music.library.ArtistBrowseItem
import com.dizzyvy.rift.music.library.DevicePlaylist
import com.dizzyvy.rift.music.model.AudioTrack
import com.dizzyvy.rift.ui.theme.RiftTextButton as TextButton
import com.dizzyvy.rift.ui.theme.ShrikhandHeading

@Stable
internal class LibraryDialogActions(
    val deleteTrack: (AudioTrack) -> Unit,
    val deleteTracks: (List<AudioTrack>) -> Unit,
    val markNotDuplicate: (List<Uri>) -> Unit,
    val mergeArtistAlias: (String, String) -> Unit,
    val clearPlaylistImportReport: () -> Unit,
    val clearBackupRestoreReport: () -> Unit,
    val playNext: (List<AudioTrack>) -> Unit,
    val addToQueue: (AudioTrack) -> Unit,
    val addTracksToPlaylist: (DevicePlaylist, List<AudioTrack>) -> Unit,
    val createPlaylist: (String, List<AudioTrack>) -> Unit,
    val renamePlaylist: (DevicePlaylist, String) -> Unit,
    val deletePlaylist: (DevicePlaylist) -> Unit,
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun LibraryDialogs(
    state: LibraryUiState,
    selectedTracks: List<AudioTrack>,
    trackToDelete: AudioTrack?,
    onDismissTrackDelete: () -> Unit,
    folderToHide: String?,
    onDismissFolderHide: () -> Unit,
    pendingDuplicateAction: PendingDuplicateAction?,
    onDismissDuplicateAction: () -> Unit,
    pendingBulkDuplicateDelete: List<AudioTrack>?,
    onDismissBulkDuplicateDelete: () -> Unit,
    showDeleteSelectionConfirmation: Boolean,
    onDismissSelectionDelete: () -> Unit,
    artistToMerge: ArtistBrowseItem?,
    onDismissArtistMerge: () -> Unit,
    artistAliasInput: String,
    onArtistAliasInputChange: (String) -> Unit,
    showAddSheet: Boolean,
    onDismissAddSheet: () -> Unit,
    pendingTracks: List<AudioTrack>,
    onPendingTracksChange: (List<AudioTrack>) -> Unit,
    showCreateDialog: Boolean,
    onShowCreateDialogChange: (Boolean) -> Unit,
    tracksToAddOnCreate: List<AudioTrack>,
    onTracksToAddOnCreateChange: (List<AudioTrack>) -> Unit,
    playlistNameInput: String,
    onPlaylistNameInputChange: (String) -> Unit,
    playlistToRename: DevicePlaylist?,
    onPlaylistToRenameChange: (DevicePlaylist?) -> Unit,
    playlistToDelete: DevicePlaylist?,
    onPlaylistToDeleteChange: (DevicePlaylist?) -> Unit,
    onSelectionChange: (Set<String>) -> Unit,
    onFolderHiddenUndo: (String) -> Unit,
    actions: LibraryDialogActions,
) {
    trackToDelete?.let { track ->
        AlertDialog(
            onDismissRequest = onDismissTrackDelete,
            title = { ShrikhandHeading("Delete from device?", MaterialTheme.typography.headlineSmall) },
            text = { Text("Delete ${track.title} from this device? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { actions.deleteTrack(track); onDismissTrackDelete() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = onDismissTrackDelete) { Text("Cancel") } },
        )
    }
    folderToHide?.let { path ->
        AlertDialog(
            onDismissRequest = onDismissFolderHide,
            title = { ShrikhandHeading("Hide folder?", MaterialTheme.typography.headlineSmall) },
            text = { Text("Hide $path and its songs from the library? You can unhide it later from Hidden folders.") },
            confirmButton = {
                TextButton(onClick = { onDismissFolderHide(); onFolderHiddenUndo(path) }) { Text("Hide") }
            },
            dismissButton = { TextButton(onClick = onDismissFolderHide) { Text("Cancel") } },
        )
    }
    pendingDuplicateAction?.let { action ->
        val title = when (action) {
            is PendingDuplicateAction.Keep -> "Keep this track?"
            is PendingDuplicateAction.Delete -> "Delete this track?"
            is PendingDuplicateAction.NotDuplicate -> "Mark as not a duplicate?"
        }
        val message = when (action) {
            is PendingDuplicateAction.Keep -> "Keep “${action.track.title}” and delete the other ${countLabel(action.group.tracks.size - 1, "track")} in this group from the device? This cannot be undone."
            is PendingDuplicateAction.Delete -> "Delete “${action.track.title}” from this device? This cannot be undone."
            is PendingDuplicateAction.NotDuplicate -> "Remove this group from duplicate results? This choice will be saved in the app database."
        }
        AlertDialog(
            onDismissRequest = onDismissDuplicateAction,
            title = { ShrikhandHeading(title, MaterialTheme.typography.headlineSmall) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    when (action) {
                        is PendingDuplicateAction.Keep -> actions.deleteTracks(action.group.tracks.filterNot { it.uri == action.track.uri })
                        is PendingDuplicateAction.Delete -> actions.deleteTracks(listOf(action.track))
                        is PendingDuplicateAction.NotDuplicate -> actions.markNotDuplicate(action.group.tracks.map { it.uri })
                    }
                    onDismissDuplicateAction()
                }) {
                    Text(if (action is PendingDuplicateAction.NotDuplicate) "Not a duplicate" else if (action is PendingDuplicateAction.Keep) "Keep" else "Delete")
                }
            },
            dismissButton = { TextButton(onClick = onDismissDuplicateAction) { Text("Cancel") } },
        )
    }
    pendingBulkDuplicateDelete?.let { tracks ->
        AlertDialog(
            onDismissRequest = onDismissBulkDuplicateDelete,
            title = { ShrikhandHeading("Remove lower-quality copies?", MaterialTheme.typography.headlineSmall) },
            text = {
                Text("Delete ${tracks.size} lower-quality copies (${formatAudioSize(tracks.sumOf { it.sizeBytes.coerceAtLeast(0L) })}) from this device? This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = { actions.deleteTracks(tracks); onDismissBulkDuplicateDelete() },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Review and delete") }
            },
            dismissButton = {
                TextButton(onClick = onDismissBulkDuplicateDelete, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Cancel")
                }
            },
        )
    }
    if (showDeleteSelectionConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissSelectionDelete,
            title = { ShrikhandHeading("Delete selected songs?", MaterialTheme.typography.headlineSmall) },
            text = { Text("Delete ${selectedTracks.size} selected songs from this device? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { actions.deleteTracks(selectedTracks); onSelectionChange(emptySet()); onDismissSelectionDelete() },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = onDismissSelectionDelete, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Cancel")
                }
            },
        )
    }
    artistToMerge?.let { artist ->
        AlertDialog(
            onDismissRequest = onDismissArtistMerge,
            title = { ShrikhandHeading("Merge artist names", MaterialTheme.typography.headlineSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Treat this name as ${artist.name} in your library. The audio tags will not be changed.")
                    OutlinedTextField(
                        value = artistAliasInput,
                        onValueChange = onArtistAliasInputChange,
                        singleLine = true,
                        label = { Text("Other artist name") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = artistAliasInput.isNotBlank(),
                    onClick = { actions.mergeArtistAlias(artistAliasInput, artist.name); onDismissArtistMerge() },
                ) { Text("Merge") }
            },
            dismissButton = { TextButton(onClick = onDismissArtistMerge) { Text("Cancel") } },
        )
    }
    state.playlistImportReport?.let { report ->
        AlertDialog(
            onDismissRequest = actions.clearPlaylistImportReport,
            title = { ShrikhandHeading("Playlist imported", MaterialTheme.typography.headlineSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${report.matchedCount} of ${report.totalCount} tracks matched in “${report.playlistName}”.")
                    if (report.unmatchedEntries.isNotEmpty()) {
                        Text("Not matched:", fontWeight = FontWeight.SemiBold)
                        Column(
                            Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            report.unmatchedEntries.forEach { entry ->
                                Text(entry, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = actions.clearPlaylistImportReport) { Text("Done") } },
        )
    }
    state.backupRestoreReport?.let { report ->
        AlertDialog(
            onDismissRequest = actions.clearBackupRestoreReport,
            title = { ShrikhandHeading("Backup restored", MaterialTheme.typography.headlineSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${report.playlistCount} playlists and ${report.favoriteCount} favorites restored.")
                    if (report.missingTrackCount > 0) {
                        Text("${report.missingTrackCount} track(s) could not be matched in the current library.")
                    }
                }
            },
            confirmButton = { TextButton(onClick = actions.clearBackupRestoreReport) { Text("Done") } },
        )
    }
    if (showAddSheet) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismissAddSheet) {
            ShrikhandHeading(
                if (pendingTracks.size == 1) "Add ${pendingTracks.first().title}" else "Add ${pendingTracks.size} songs",
                MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
            TextButton(onClick = {
                actions.playNext(pendingTracks)
                onDismissAddSheet()
                onPendingTracksChange(emptyList())
                onSelectionChange(emptySet())
            }, modifier = Modifier.fillMaxWidth()) { Text("Play next") }
            TextButton(onClick = {
                pendingTracks.forEach(actions.addToQueue)
                onDismissAddSheet()
                onPendingTracksChange(emptyList())
                onSelectionChange(emptySet())
            }, modifier = Modifier.fillMaxWidth()) { Text("Add to queue") }
            TextButton(onClick = {
                onTracksToAddOnCreateChange(pendingTracks)
                onPlaylistNameInputChange("")
                onDismissAddSheet()
                onShowCreateDialogChange(true)
            }, modifier = Modifier.fillMaxWidth()) { Text("Create new playlist") }
            state.playlists.filter { it.isLocal && (!it.isAuto || it.autoKind == "favorites") }.forEach { playlist ->
                TextButton(onClick = {
                    actions.addTracksToPlaylist(playlist, pendingTracks)
                    onDismissAddSheet()
                    onPendingTracksChange(emptyList())
                    onSelectionChange(emptySet())
                }, modifier = Modifier.fillMaxWidth()) { Text("Add to ${playlist.name}") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { onShowCreateDialogChange(false); onTracksToAddOnCreateChange(emptyList()) },
            title = { ShrikhandHeading("Create playlist", MaterialTheme.typography.headlineSmall) },
            text = {
                OutlinedTextField(
                    value = playlistNameInput,
                    onValueChange = onPlaylistNameInputChange,
                    singleLine = true,
                    label = { Text("Playlist name") },
                )
            },
            confirmButton = {
                TextButton(enabled = playlistNameInput.isNotBlank(), onClick = {
                    actions.createPlaylist(playlistNameInput.trim(), tracksToAddOnCreate)
                    onShowCreateDialogChange(false)
                    onTracksToAddOnCreateChange(emptyList())
                    onPendingTracksChange(emptyList())
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { onShowCreateDialogChange(false); onTracksToAddOnCreateChange(emptyList()) }) {
                    Text("Cancel")
                }
            },
        )
    }
    playlistToRename?.let { playlist ->
        AlertDialog(
            onDismissRequest = { onPlaylistToRenameChange(null) },
            title = { ShrikhandHeading("Rename playlist", MaterialTheme.typography.headlineSmall) },
            text = {
                OutlinedTextField(
                    value = playlistNameInput,
                    onValueChange = onPlaylistNameInputChange,
                    singleLine = true,
                    label = { Text("Playlist name") },
                )
            },
            confirmButton = {
                TextButton(enabled = playlistNameInput.isNotBlank(), onClick = {
                    actions.renamePlaylist(playlist, playlistNameInput.trim())
                    onPlaylistToRenameChange(null)
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { onPlaylistToRenameChange(null) }) { Text("Cancel") } },
        )
    }
    playlistToDelete?.let { playlist ->
        AlertDialog(
            onDismissRequest = { onPlaylistToDeleteChange(null) },
            title = { ShrikhandHeading("Delete playlist?", MaterialTheme.typography.headlineSmall) },
            text = { Text("Delete ${playlist.name}? Songs on your device will not be deleted.") },
            confirmButton = {
                TextButton(onClick = { actions.deletePlaylist(playlist); onPlaylistToDeleteChange(null) }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { onPlaylistToDeleteChange(null) }) { Text("Cancel") } },
        )
    }
}
