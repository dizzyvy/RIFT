package com.dizzyvy.rift.ui.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dizzyvy.rift.music.artwork.ArtworkRepository
import com.dizzyvy.rift.music.library.DevicePlaylist

@Composable
internal fun PlaylistsTab(
    playlists: List<DevicePlaylist>,
    listState: LazyListState,
    artworkRepository: ArtworkRepository,
    actions: PlaylistActions,
) {
    val smartPlaylists = playlists.filter(DevicePlaylist::isAuto)
    val userPlaylists = playlists.filterNot(DevicePlaylist::isAuto)
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(end = 26.dp, bottom = 12.dp),
    ) {
        if (smartPlaylists.isNotEmpty()) {
            item(key = "smart-playlist-header") {
                Text(
                    "Smart playlists",
                    Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(smartPlaylists, key = { "smart:${it.volumeName}:${it.id}" }) { playlist ->
                PlaylistBrowseRow(
                    playlist = playlist,
                    artworkRepository = artworkRepository,
                    onOpen = { actions.open(playlist) },
                    onRename = { actions.rename(playlist) },
                    onDelete = { actions.delete(playlist) },
                    onExport = { actions.exportM3u(playlist) },
                )
            }
        }
        if (userPlaylists.isNotEmpty()) {
            item(key = "user-playlist-header") {
                Text(
                    "Your playlists",
                    Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(userPlaylists, key = { "user:${it.volumeName}:${it.id}" }) { playlist ->
                PlaylistBrowseRow(
                    playlist = playlist,
                    artworkRepository = artworkRepository,
                    onOpen = { actions.open(playlist) },
                    onRename = { actions.rename(playlist) },
                    onDelete = { actions.delete(playlist) },
                    onExport = { actions.exportM3u(playlist) },
                )
            }
        }
    }
}
