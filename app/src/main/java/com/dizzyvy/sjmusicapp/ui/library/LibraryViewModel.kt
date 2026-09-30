package com.dizzyvy.sjmusicapp.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dizzyvy.sjmusicapp.music.library.AudioLibraryRepository
import com.dizzyvy.sjmusicapp.music.library.AlbumBrowseItem
import com.dizzyvy.sjmusicapp.music.library.ArtistBrowseItem
import com.dizzyvy.sjmusicapp.music.library.DevicePlaylist
import com.dizzyvy.sjmusicapp.music.library.librarySection
import com.dizzyvy.sjmusicapp.music.library.artistGroupKey
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
    val albums: List<AlbumBrowseItem> = emptyList(),
    val playlists: List<DevicePlaylist> = emptyList(),
    val browseTitle: String? = null,
    val browseTracks: List<AudioTrack>? = null,
)

class LibraryViewModel(
    private val repository: AudioLibraryRepository,
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
                val playlists = repository.loadPlaylists()
                val artists = tracks.groupBy(::artistGroupKey)
                    .map { (id, items) -> ArtistBrowseItem(id, items.first().artist.ifBlank { "Unknown artist" }, items.size) }
                    .sortedWith(compareBy<ArtistBrowseItem> { if (librarySection(it.name) == '#') 0 else 1 }.thenBy { librarySection(it.name) }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                val albums = tracks.groupBy(::albumGroupKey)
                    .map { (id, items) -> AlbumBrowseItem(id, items.first().album.ifBlank { "Unknown album" }, items.first().artist, items.size) }
                    .sortedWith(compareBy<AlbumBrowseItem> { if (librarySection(it.title) == '#') 0 else 1 }.thenBy { librarySection(it.title) }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                _state.value = _state.value.copy(
                    tracks = tracks,
                    visibleTracks = filterTracks(tracks, _state.value.searchQuery),
                    artists = artists,
                    albums = albums,
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
        )
    }

    fun playTrack(track: AudioTrack) {
        val current = _state.value
        val tracks = current.browseTracks ?: current.visibleTracks
        val index = tracks.indexOf(track)
        if (index >= 0) playback.setQueue(tracks, index)
    }

    fun selectCategory(category: String) {
        _state.value = _state.value.copy(category = category, browseTitle = null, browseTracks = null)
    }

    fun closeGroup() {
        _state.value = _state.value.copy(browseTitle = null, browseTracks = null)
    }

    fun openArtist(item: ArtistBrowseItem) = openGroup(item.name) { artistGroupKey(it) == item.id }
    fun openAlbum(item: AlbumBrowseItem) = openGroup(item.title) { albumGroupKey(it) == item.id }

    fun openPlaylist(item: DevicePlaylist) {
        viewModelScope.launch {
            val tracks = repository.loadPlaylistTracks(item)
            _state.value = _state.value.copy(browseTitle = item.name, browseTracks = tracks)
        }
    }

    private fun openGroup(title: String, predicate: (AudioTrack) -> Boolean) {
        val tracks = _state.value.tracks.filter(predicate)
        _state.value = _state.value.copy(browseTitle = title, browseTracks = tracks)
    }

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
        private val playback: PlaybackController,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(LibraryViewModel::class.java))
            return LibraryViewModel(repository, playback) as T
        }
    }
}
