package com.dizzyvy.sjmusicapp.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dizzyvy.sjmusicapp.music.library.AudioLibraryRepository
import com.dizzyvy.sjmusicapp.music.library.PlaylistStore
import com.dizzyvy.sjmusicapp.music.library.M3uPlaylistFormat
import com.dizzyvy.sjmusicapp.music.library.AlbumBrowseItem
import com.dizzyvy.sjmusicapp.music.library.ArtistBrowseItem
import com.dizzyvy.sjmusicapp.music.library.DevicePlaylist
import com.dizzyvy.sjmusicapp.music.library.librarySection
import com.dizzyvy.sjmusicapp.music.library.artistGroupKey
import com.dizzyvy.sjmusicapp.music.library.artistGroupKeys
import com.dizzyvy.sjmusicapp.music.library.artistNamesForTrack
import com.dizzyvy.sjmusicapp.music.library.albumGroupKey
import com.dizzyvy.sjmusicapp.music.model.AudioTrack
import com.dizzyvy.sjmusicapp.music.playback.PlaybackController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LibraryUiState(
    val tracks: List<AudioTrack> = emptyList(),
    val visibleTracks: List<AudioTrack> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val permissionRequired: Boolean = false,
    val message: String? = null,
    val category: String = "Songs",
    val artists: List<ArtistBrowseItem> = emptyList(),
    val visibleArtists: List<ArtistBrowseItem> = emptyList(),
    val albums: List<AlbumBrowseItem> = emptyList(),
    val visibleAlbums: List<AlbumBrowseItem> = emptyList(),
    val playlists: List<DevicePlaylist> = emptyList(),
    val browseTitle: String? = null,
    val browseTracks: List<AudioTrack>? = null,
    val activePlaylist: DevicePlaylist? = null,
    val actionMessage: String? = null,
)

class LibraryViewModel(
    private val repository: AudioLibraryRepository,
    private val playlistStore: PlaylistStore,
    private val playback: PlaybackController,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    fun loadLibrary(hasAudioPermission: Boolean, forceRefresh: Boolean = false) {
        if (!hasAudioPermission) {
            _state.value = _state.value.copy(isLoading = false, permissionRequired = true)
            return
        }
        if (_state.value.isLoading || (!forceRefresh && _state.value.tracks.isNotEmpty())) return

        _state.value = _state.value.copy(isLoading = true, permissionRequired = false, message = null)
        viewModelScope.launch {
            try {
                val tracks = repository.loadTracks()
                val playlists = repository.loadPlaylists() + playlistStore.loadPlaylists()
                val artistTracks = tracks.flatMap { track -> artistNamesForTrack(track).map { name -> name to track } }
                val artists = artistTracks.groupBy { (name, _) -> name.trim().replace(Regex("\\s+"), "").lowercase() }
                    .map { (id, entries) -> ArtistBrowseItem(id, entries.first().first, entries.size, entries.first().second.uri) }
                    .sortedWith(compareBy<ArtistBrowseItem> { if (librarySection(it.name) == '#') 0 else 1 }.thenBy { librarySection(it.name) }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                val albums = tracks.groupBy(::albumGroupKey)
                    .map { (id, items) ->
                        val first = items.first()
                        val title = first.album.takeUnless { it.isBlank() || it.equals("<unknown>", true) } ?: "Unknown album"
                        val artist = first.albumArtist.ifBlank { artistNamesForTrack(first).joinToString(", ") }
                        AlbumBrowseItem(id, title, artist, items.size, first.uri)
                    }
                    .sortedWith(compareBy<AlbumBrowseItem> { if (librarySection(it.title) == '#') 0 else 1 }.thenBy { librarySection(it.title) }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                _state.value = _state.value.copy(
                    tracks = tracks,
                    visibleTracks = filterTracks(tracks, _state.value.searchQuery),
                    artists = artists,
                    visibleArtists = filterArtists(artists, _state.value.searchQuery),
                    albums = albums,
                    visibleAlbums = filterAlbums(albums, _state.value.searchQuery),
                    playlists = playlists,
                    isLoading = false,
                    permissionRequired = false,
                    message = null,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                _state.value = _state.value.copy(isLoading = false, permissionRequired = true)
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    message = "Music storage is currently unavailable.",
                )
            }
        }
    }

    fun setSearchQuery(query: String) {
        val current = _state.value
        _state.value = current.copy(
            searchQuery = query,
            visibleTracks = filterTracks(current.tracks, query),
            visibleArtists = filterArtists(current.artists, query),
            visibleAlbums = filterAlbums(current.albums, query),
        )
    }

    fun playPlaylist(shuffle: Boolean) {
        val tracks = _state.value.browseTracks.orEmpty()
        if (tracks.isEmpty()) return
        playback.setQueue(tracks, 0)
        playback.setShuffleEnabled(shuffle)
    }

    fun playTrack(track: AudioTrack) {
        val current = _state.value
        val tracks = current.browseTracks ?: current.visibleTracks
        val index = tracks.indexOf(track)
        if (index >= 0) playback.setQueue(tracks, index)
    }

    fun selectCategory(category: String) {
        _state.value = _state.value.copy(category = category, browseTitle = null, browseTracks = null, activePlaylist = null, actionMessage = null)
    }

    fun closeGroup() {
        _state.value = _state.value.copy(browseTitle = null, browseTracks = null, activePlaylist = null)
    }

    fun openArtist(item: ArtistBrowseItem) = openGroup(item.name) { item.id in artistGroupKeys(it) }
    fun openAlbum(item: AlbumBrowseItem) = openGroup(item.title) { albumGroupKey(it) == item.id }

    fun openPlaylist(item: DevicePlaylist) {
        viewModelScope.launch {
            val tracks = if (item.isLocal) loadLocalTracks(item.id) else repository.loadPlaylistTracks(item)
            _state.value = _state.value.copy(browseTitle = item.name, browseTracks = tracks, activePlaylist = item)
        }
    }

    fun createPlaylist(name: String, tracksToAdd: List<AudioTrack> = emptyList()) {
        viewModelScope.launch {
            runCatching {
                playlistStore.createPlaylist(name).also { playlist -> tracksToAdd.forEach { playlistStore.addTrack(playlist.id, it.uri) } }
            }
                .onSuccess { refreshPlaylists() }
                .onFailure { _state.value = _state.value.copy(actionMessage = it.message ?: "Could not create playlist.") }
        }
    }

    fun importM3u(name: String, contents: String) {
        val tracksByUri = _state.value.tracks.associateBy { it.uri.toString() }
        val tracksByFileName = _state.value.tracks
            .filter { it.displayName.isNotBlank() }
            .associateBy { it.displayName.lowercase() }
        val tracks = M3uPlaylistFormat.entries(contents).mapNotNull { entry ->
            tracksByUri[entry] ?: tracksByFileName[M3uPlaylistFormat.fileName(entry).lowercase()]
        }.distinctBy { it.uri }
        createPlaylist(name.substringBeforeLast('.', name).ifBlank { name }, tracks)
    }

    fun exportM3u(playlist: DevicePlaylist, onReady: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val tracks = if (playlist.isLocal) loadLocalTracks(playlist.id) else repository.loadPlaylistTracks(playlist)
                M3uPlaylistFormat.encode(playlist.name, tracks)
            }.onSuccess(onReady)
                .onFailure { _state.value = _state.value.copy(actionMessage = it.message ?: "Could not export playlist.") }
        }
    }

    fun renamePlaylist(playlist: DevicePlaylist, name: String) {
        if (!playlist.isLocal) return
        viewModelScope.launch {
            runCatching { playlistStore.renamePlaylist(playlist.id, name) }
                .onSuccess {
                    refreshPlaylists()
                    if (_state.value.activePlaylist?.id == playlist.id) {
                        _state.value = _state.value.copy(browseTitle = name.trim(), activePlaylist = playlist.copy(name = name.trim()))
                    }
                }
                .onFailure { _state.value = _state.value.copy(actionMessage = it.message ?: "Could not rename playlist.") }
        }
    }

    fun deletePlaylist(playlist: DevicePlaylist) {
        if (!playlist.isLocal) return
        viewModelScope.launch {
            runCatching { playlistStore.deletePlaylist(playlist.id) }
                .onSuccess {
                    refreshPlaylists()
                    if (_state.value.activePlaylist?.id == playlist.id) closeGroup()
                }
                .onFailure { _state.value = _state.value.copy(actionMessage = it.message ?: "Could not delete playlist.") }
        }
    }

    fun addTracksToPlaylist(playlist: DevicePlaylist, tracks: List<AudioTrack>) {
        if (!playlist.isLocal) return
        viewModelScope.launch {
            runCatching { tracks.forEach { playlistStore.addTrack(playlist.id, it.uri) } }
                .onSuccess {
                    if (_state.value.activePlaylist?.id == playlist.id) _state.value = _state.value.copy(browseTracks = loadLocalTracks(playlist.id))
                    _state.value = _state.value.copy(actionMessage = null)
                }
                .onFailure { _state.value = _state.value.copy(actionMessage = it.message ?: "Could not add songs to playlist.") }
        }
    }

    fun addTrackToPlaylist(playlist: DevicePlaylist, track: AudioTrack) {
        if (!playlist.isLocal) return
        viewModelScope.launch {
            runCatching { playlistStore.addTrack(playlist.id, track.uri) }
                .onSuccess {
                    if (_state.value.activePlaylist?.id == playlist.id) _state.value = _state.value.copy(browseTracks = loadLocalTracks(playlist.id))
                    _state.value = _state.value.copy(actionMessage = null)
                }
                .onFailure { _state.value = _state.value.copy(actionMessage = it.message ?: "Could not add song to playlist.") }
        }
    }

    fun removeTrackFromPlaylist(track: AudioTrack) {
        val playlist = _state.value.activePlaylist ?: return
        if (!playlist.isLocal) return
        viewModelScope.launch {
            playlistStore.removeTrack(playlist.id, track.uri)
            _state.value = _state.value.copy(browseTracks = loadLocalTracks(playlist.id))
        }
    }

    fun movePlaylistTrack(fromIndex: Int, toIndex: Int) {
        val playlist = _state.value.activePlaylist ?: return
        if (!playlist.isLocal) return
        viewModelScope.launch {
            playlistStore.reorderTrack(playlist.id, fromIndex, toIndex)
            _state.value = _state.value.copy(browseTracks = loadLocalTracks(playlist.id))
        }
    }

    private suspend fun loadLocalTracks(playlistId: Long): List<AudioTrack> {
        val tracksByUri = _state.value.tracks.associateBy { it.uri }
        return playlistStore.loadTrackUris(playlistId).mapNotNull(tracksByUri::get)
    }

    private suspend fun refreshPlaylists() {
        _state.value = _state.value.copy(playlists = repository.loadPlaylists() + playlistStore.loadPlaylists())
    }

    private fun openGroup(title: String, predicate: (AudioTrack) -> Boolean) {
        val tracks = _state.value.tracks.filter(predicate)
        _state.value = _state.value.copy(browseTitle = title, browseTracks = tracks)
    }

    private fun filterArtists(artists: List<ArtistBrowseItem>, query: String): List<ArtistBrowseItem> =
        artists.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }

    private fun filterAlbums(albums: List<AlbumBrowseItem>, query: String): List<AlbumBrowseItem> =
        albums.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) || it.artist.contains(query.trim(), ignoreCase = true) }

    private fun filterTracks(tracks: List<AudioTrack>, query: String): List<AudioTrack> {
        val needle = query.trim()
        if (needle.isEmpty()) return tracks
        return tracks.filter { track ->
            track.title.contains(needle, ignoreCase = true) ||
                track.artist.contains(needle, ignoreCase = true) ||
                track.album.contains(needle, ignoreCase = true)
        }
    }

    class Factory(
        private val repository: AudioLibraryRepository,
        private val playlistStore: PlaylistStore,
        private val playback: PlaybackController,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(LibraryViewModel::class.java))
            return LibraryViewModel(repository, playlistStore, playback) as T
        }
    }
}
