package com.dizzyvy.rift.music.library

import com.dizzyvy.rift.music.model.AudioTrack

interface AudioLibraryRepository {
    suspend fun loadTracks(): List<AudioTrack>
    suspend fun scanTracks(onProgress: (processed: Int, total: Int) -> Unit): LibraryScanResult {
        val tracks = loadTracks()
        onProgress(tracks.size, tracks.size)
        return LibraryScanResult(tracks, complete = true)
    }
    suspend fun loadArtists(): List<ArtistBrowseItem>
    suspend fun loadAlbums(): List<AlbumBrowseItem>
    suspend fun loadPlaylists(): List<DevicePlaylist>
    suspend fun loadPlaylistTracks(playlist: DevicePlaylist): List<AudioTrack>
}

data class LibraryScanResult(val tracks: List<AudioTrack>, val complete: Boolean)
