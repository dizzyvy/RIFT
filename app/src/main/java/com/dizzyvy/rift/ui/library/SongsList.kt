package com.dizzyvy.rift.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dizzyvy.rift.music.artwork.ArtworkRepository
import com.dizzyvy.rift.music.library.albumGroupKey
import com.dizzyvy.rift.music.library.artistGroupKeys
import com.dizzyvy.rift.music.model.AudioTrack
import com.dizzyvy.rift.music.playback.PlaybackSnapshot
import com.dizzyvy.rift.ui.theme.RiftTextButton as TextButton

@Composable
internal fun SongsList(
    state: LibraryUiState,
    songs: List<AudioTrack>,
    listState: LazyListState,
    playback: PlaybackSnapshot,
    artworkRepository: ArtworkRepository,
    selectedUris: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onFiltersClick: () -> Unit,
    onAddTracksToPlaylist: (List<AudioTrack>) -> Unit,
    onShareFailure: () -> Unit,
    libraryActions: LibraryActions,
    playbackActions: PlaybackActions,
) {
    val headerCount = 2
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(end = 26.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            item {
                Text(
                    countLabel(songs.size, "song").uppercase(),
                    Modifier.padding(start = 5.dp, top = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onFiltersClick,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text("Sort: ${state.sortOrder}") }
                    TextButton(
                        onClick = onFiltersClick,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Text(if (state.hideShortTracks || state.hideLongTracks) "Filters •" else "Filters")
                    }
                    Button(
                        onClick = playbackActions.shuffleAll,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Shuffle", maxLines = 1)
                    }
                }
            }
            item {
                Text(
                    "Shuffle skips tracks longer than 20 minutes.",
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(songs, key = { it.uri.toString() }) { track ->
                val uri = track.uri.toString()
                TrackRow(
                    track = track,
                    artworkRepository = artworkRepository,
                    playing = playback.currentTrack?.uri == track.uri && playback.isPlaying,
                    selected = uri in selectedUris,
                    selectionMode = selectedUris.isNotEmpty(),
                    onLongPress = { onSelectionChange(selectedUris + uri) },
                    onClick = {
                        if (selectedUris.isNotEmpty()) {
                            onSelectionChange(if (uri in selectedUris) selectedUris - uri else selectedUris + uri)
                        } else {
                            libraryActions.playTrack(track)
                        }
                    },
                    onAdd = { onAddTracksToPlaylist(listOf(track)) },
                    isFavorite = uri in state.favoriteUris,
                    onFavorite = { libraryActions.favorite(track, uri !in state.favoriteUris) },
                    onPlayNext = { libraryActions.playNext(listOf(track)) },
                    onQueue = { libraryActions.addToQueue(track) },
                    onGoArtist = {
                        state.artists.firstOrNull { it.id in artistGroupKeys(track, state.artistAliases) }
                            ?.let(libraryActions.openArtist)
                    },
                    onGoAlbum = {
                        state.albums.firstOrNull { it.id == albumGroupKey(track, state.artistAliases) }
                            ?.let(libraryActions.openAlbum)
                    },
                    onDelete = { libraryActions.deleteTrack(track) },
                    onShareFailure = onShareFailure,
                )
            }
        }
        if (state.searchQuery.isBlank() && state.sortOrder in setOf("Title", "Artist")) {
            val sectionNames = if (state.sortOrder == "Artist") {
                songs.map { it.albumArtist.ifBlank { it.artist } }
            } else {
                songs.map { it.title }
            }
            FastScrollRail(
                sectionNames,
                listState,
                Modifier.align(Alignment.CenterEnd),
                descending = !state.sortAscending,
                firstItemOffset = headerCount,
            )
        }
    }
}
