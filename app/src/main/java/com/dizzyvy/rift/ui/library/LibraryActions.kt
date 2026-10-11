package com.dizzyvy.rift.ui.library

import androidx.compose.runtime.Stable
import com.dizzyvy.rift.music.library.AlbumBrowseItem
import com.dizzyvy.rift.music.library.ArtistBrowseItem
import com.dizzyvy.rift.music.library.DevicePlaylist
import com.dizzyvy.rift.music.library.LibraryCollectionItem
import com.dizzyvy.rift.music.model.AudioTrack

internal fun selectedTracksInLibraryOrder(
    tracks: List<AudioTrack>,
    selectedUris: Set<String>,
): List<AudioTrack> = tracks.filter { it.uri.toString() in selectedUris }

@Stable
internal class LibraryActions(
    val playTrack: (AudioTrack) -> Unit,
    val favorite: (AudioTrack, Boolean) -> Unit,
    val playNext: (List<AudioTrack>) -> Unit,
    val addToQueue: (AudioTrack) -> Unit,
    val deleteTrack: (AudioTrack) -> Unit,
    val openArtist: (ArtistBrowseItem) -> Unit,
    val openAlbum: (AlbumBrowseItem) -> Unit,
    val openCollection: (LibraryCollectionItem, String) -> Unit,
    val openPlaylist: (DevicePlaylist) -> Unit,
    val renamePlaylist: (DevicePlaylist, String) -> Unit,
    val deletePlaylist: (DevicePlaylist) -> Unit,
    val exportM3u: (DevicePlaylist) -> Unit,
)

@Stable
internal class PlaybackActions(
    val shuffleAll: () -> Unit,
    val openPlayer: () -> Unit,
    val playPause: () -> Unit,
    val previousTrack: () -> Unit,
    val nextTrack: () -> Unit,
)

@Stable
internal class PlaylistActions(
    val open: (DevicePlaylist) -> Unit,
    val rename: (DevicePlaylist) -> Unit,
    val delete: (DevicePlaylist) -> Unit,
    val exportM3u: (DevicePlaylist) -> Unit,
)
