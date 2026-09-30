package com.dizzyvy.sjmusicapp.music.library

import com.dizzyvy.sjmusicapp.music.model.AudioTrack

interface AudioLibraryRepository {
    suspend fun loadTracks(): List<AudioTrack>
    suspend fun loadArtists(): List<ArtistBrowseItem>
    suspend fun loadAlbums(): List<AlbumBrowseItem>
    suspend fun loadPlaylists(): List<DevicePlaylist>
    suspend fun loadPlaylistTracks(playlist: DevicePlaylist): List<AudioTrack>
}
