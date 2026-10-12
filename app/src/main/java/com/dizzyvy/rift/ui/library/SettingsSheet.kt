package com.dizzyvy.rift.ui.library

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dizzyvy.rift.music.library.LibraryCollectionItem
import com.dizzyvy.rift.ui.theme.RiftTextButton
import com.dizzyvy.rift.ui.theme.ShrikhandHeading

@Stable
internal class SettingsActions(
    val changeThemeMode: (String) -> Unit,
    val changeWheelSensitivity: (Float) -> Unit,
    val changeWheelHaptics: (Boolean) -> Unit,
    val rescan: () -> Unit,
    val manageHiddenFolders: () -> Unit,
    val openDuplicates: () -> Unit,
    val removeArtistAlias: (String) -> Unit,
    val chooseLyricsDirectory: () -> Unit,
    val openDownloader: () -> Unit,
    val exportBackup: () -> Unit,
    val importBackup: () -> Unit,
    val importM3u: () -> Unit,
    val showMessage: (String) -> Unit,
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SettingsSheet(
    open: Boolean,
    state: LibraryUiState,
    themeMode: String,
    clickWheelSensitivity: Float,
    clickWheelHaptics: Boolean,
    lyricsDirectoryUri: Uri?,
    actions: SettingsActions,
    onDismiss: () -> Unit,
) {
    if (!open) return
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShrikhandHeading("Settings", MaterialTheme.typography.titleLarge)
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (mode, label) ->
                    FilterChip(
                        selected = themeMode.equals(mode, ignoreCase = true),
                        onClick = { actions.changeThemeMode(mode) },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("Click wheel", style = MaterialTheme.typography.titleMedium)
            Text("Sensitivity · ${"%.2f".format(java.util.Locale.ROOT, clickWheelSensitivity)}×")
            Slider(
                value = clickWheelSensitivity,
                onValueChange = actions.changeWheelSensitivity,
                valueRange = 0.5f..2f,
                steps = 5,
                modifier = Modifier.semantics { contentDescription = "Click wheel sensitivity" },
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Haptic ticks", Modifier.weight(1f))
                Switch(
                    checked = clickWheelHaptics,
                    onCheckedChange = actions.changeWheelHaptics,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("Downloads", style = MaterialTheme.typography.titleMedium)
            RiftTextButton(
                onClick = { onDismiss(); actions.openDownloader() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Open Downloader") }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("Library", style = MaterialTheme.typography.titleMedium)
            RiftTextButton(onClick = { onDismiss(); actions.rescan() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Rescan now")
            }
            RiftTextButton(
                onClick = { actions.manageHiddenFolders(); onDismiss() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Manage hidden folders (${state.hiddenFolders.size})") }
            RiftTextButton(
                onClick = { onDismiss(); actions.openDuplicates() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(if (state.duplicateGroupsLoaded) "Clean up duplicates (${state.duplicateGroups.size})" else "Clean up duplicates")
            }
            if (state.artistAliases.isNotEmpty()) {
                Text("Artist name merges", style = MaterialTheme.typography.titleSmall)
                state.artistAliases.forEach { (alias, canonical) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("$alias → $canonical", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        RiftTextButton(
                            onClick = { actions.removeArtistAlias(alias) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Remove") }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("Lyrics", style = MaterialTheme.typography.titleMedium)
            RiftTextButton(
                onClick = actions.chooseLyricsDirectory,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(if (lyricsDirectoryUri == null) "Choose lyrics folder" else "Change lyrics folder") }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("Backup", style = MaterialTheme.typography.titleMedium)
            RiftTextButton(onClick = actions.exportBackup, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Back up playlists")
            }
            RiftTextButton(onClick = actions.importBackup, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Restore from backup")
            }
            RiftTextButton(onClick = actions.importM3u, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Import M3U")
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("About", style = MaterialTheme.typography.titleMedium)
            val versionName = remember(context) {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
            }
            Text("Version $versionName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            RiftTextButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/dizzyvy/RIFT")))
                    }.onFailure { actions.showMessage("Couldn't open the repository link.") }
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("RIFT on GitHub") }
            Spacer(Modifier.height(24.dp))
        }
    }
}
