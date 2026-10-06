package com.dizzyvy.sjmusicapp.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dizzyvy.sjmusicapp.music.library.AudioLibraryRepository
import com.dizzyvy.sjmusicapp.music.library.PlaylistStore
import com.dizzyvy.sjmusicapp.music.library.M3uPlaylistFormat
import com.dizzyvy.sjmusicapp.music.library.AlbumBrowseItem
import com.dizzyvy.sjmusicapp.music.library.LibraryCollectionItem
import com.dizzyvy.sjmusicapp.music.library.normalizeArtistName
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
    val visiblePlaylists: List<DevicePlaylist> = emptyList(),
    val browseTitle: String? = null,
    val browseTracks: List<AudioTrack>? = null,
    val activePlaylist: DevicePlaylist? = null,
    val actionMessage: String? = null,
    val sortOrder: String = "Title",
    val hideShortTracks: Boolean = false,
    val favoriteUris: Set<String> = emptySet(),
    val folders: List<LibraryCollectionItem> = emptyList(),
    val visibleFolders: List<LibraryCollectionItem> = emptyList(),
    val genres: List<LibraryCollectionItem> = emptyList(),
    val visibleGenres: List<LibraryCollectionItem> = emptyList(),
    val years: List<LibraryCollectionItem> = emptyList(),
    val visibleYears: List<LibraryCollectionItem> = emptyList(),
    val duplicateTracks: List<AudioTrack> = emptyList(),
    val visibleDuplicateTracks: List<AudioTrack> = emptyList(),
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
                val favoriteUris = playlistStore.loadFavoriteUris().map { it.toString() }.toSet()
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
                val folders = tracks.mapNotNull { track ->
                    track.filePath.substringBeforeLast('/', "").takeIf(String::isNotBlank)?.let { it to track }
                }.groupBy({ it.first }, { it.second }).map { (path, items) ->
                    LibraryCollectionItem(path, path.substringAfterLast('/').ifBlank { path }, path, items.size, items.firstOrNull()?.uri)
                }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                val genres = tracks.filter { it.genre.isNotBlank() && !it.genre.equals("<unknown>", true) }
                    .groupBy { it.genre.trim().lowercase() }.map { (_, items) ->
                        LibraryCollectionItem(items.first().genre.trim().lowercase(), items.first().genre.trim(), "Genre", items.size, items.first().uri)
                    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                val years = tracks.filter { it.year in 1000..9999 }.groupBy { it.year.toString() }
                    .map { (year, items) -> LibraryCollectionItem(year, year, "Year", items.size, items.first().uri) }
                    .sortedByDescending { it.title.toIntOrNull() ?: 0 }
                val duplicateTracks = tracks.groupBy { track ->
                    "${track.title.trim().lowercase()}|${normalizeArtistName(artistNamesForTrack(track).first())}|${track.durationMs / 1000L}"
                }.filterValues { it.size > 1 }.values.flatten().distinctBy { it.uri }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                _state.value = _state.value.copy(
                    tracks = tracks,
                    visibleTracks = filterTracks(tracks, _state.value.searchQuery, _state.value.sortOrder, _state.value.hideShortTracks),
                    artists = artists,
                    visibleArtists = filterArtists(artists, _state.value.searchQuery),
                    albums = albums,
                    visibleAlbums = filterAlbums(albums, _state.value.searchQuery),
                    playlists = playlists,
                    favoriteUris = favoriteUris,
                    folders = folders,
                    visibleFolders = filterCollections(folders, _state.value.searchQuery),
                    genres = genres,
                    visibleGenres = filterCollections(genres, _state.value.searchQuery),
                    years = years,
                    visibleYears = filterCollections(years, _state.value.searchQuery),
                    duplicateTracks = duplicateTracks,
                    visibleDuplicateTracks = filterTracks(duplicateTracks, _state.value.searchQuery, _state.value.sortOrder, _state.value.hideShortTracks),
                    visiblePlaylists = filterPlaylists(playlists, _state.value.searchQuery),
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
            category = if (query.isBlank() && current.category == "Search") "Songs" else current.category,
            searchQuery = query,
            visibleTracks = filterTracks(current.tracks, query, current.sortOrder, current.hideShortTracks),
            visibleArtists = filterArtists(current.artists, query),
            visibleAlbums = filterAlbums(current.albums, query),
            visiblePlaylists = filterPlaylists(current.playlists, query),
            visibleFolders = filterCollections(current.folders, query),
            visibleGenres = filterCollections(current.genres, query),
            visibleYears = filterCollections(current.years, query),
            visibleDuplicateTracks = filterTracks(current.duplicateTracks, query, current.sortOrder, current.hideShortTracks),
        )
    }

    fun openCollection(item: LibraryCollectionItem, kind: String) {
        openGroup(item.title) { track ->
            when (kind) {
                "Folders" -> track.filePath.substringBeforeLast('/', "") == item.id
                "Genres" -> track.genre.equals(item.title, ignoreCase = true)
                "Years" -> track.year.toString() == item.id
                else -> false
            }
        }
    }

    fun setSortOrder(order: String) {
        val current = _state.value
        _state.value = current.copy(sortOrder = order, visibleTracks = filterTracks(current.tracks, current.searchQuery, order, current.hideShortTracks))
    }

    fun setHideShortTracks(hide: Boolean) {
        val current = _state.value
        _state.value = current.copy(hideShortTracks = hide, visibleTracks = filterTracks(current.tracks, current.searchQuery, current.sortOrder, hide))
    }

    fun setFavorite(track: AudioTrack, favorite: Boolean) {
        viewModelScope.launch {
            runCatching { playlistStore.setFavorite(track.uri, favorite) }
                .onSuccess {
                    val current = _state.value
                    val favorites = current.favoriteUris.toMutableSet().apply {
                        if (favorite) add(track.uri.toString()) else remove(track.uri.toString())
                    }
                    val active = current.activePlaylist
                    val browseTracks = if (active?.isAuto == true) loadLocalTracks(active.id) else current.browseTracks
                    _state.value = current.copy(favoriteUris = favorites, browseTracks = browseTracks)
                }
                .onFailure { _state.value = _state.value.copy(actionMessage = it.message ?: "Could not update favorite.") }
        }
    }

    fun shuffleAll() {
        val tracks = _state.value.visibleTracks
        if (tracks.isEmpty()) return
        playback.setQueue(tracks, 0)
        playback.setShuffleEnabled(true)
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

    fun reportActionError(message: String) {
        _state.value = _state.value.copy(actionMessage = message)
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
                    val current = _state.value
                    val favorites = if (playlist.isAuto) current.favoriteUris + tracks.map { it.uri.toString() } else current.favoriteUris
                    val browseTracks = if (current.activePlaylist?.id == playlist.id) loadLocalTracks(playlist.id) else current.browseTracks
                    _state.value = current.copy(favoriteUris = favorites, browseTracks = browseTracks, actionMessage = null)
                }
                .onFailure { _state.value = _state.value.copy(actionMessage = it.message ?: "Could not add songs to playlist.") }
        }
    }

    fun addTrackToPlaylist(playlist: DevicePlaylist, track: AudioTrack) {
        if (!playlist.isLocal) return
        viewModelScope.launch {
            runCatching { playlistStore.addTrack(playlist.id, track.uri) }
                .onSuccess {
                    val current = _state.value
                    val favorites = if (playlist.isAuto) current.favoriteUris + track.uri.toString() else current.favoriteUris
                    val browseTracks = if (current.activePlaylist?.id == playlist.id) loadLocalTracks(playlist.id) else current.browseTracks
                    _state.value = current.copy(favoriteUris = favorites, browseTracks = browseTracks, actionMessage = null)
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
        if (!playlist.isLocal || playlist.isAuto) return
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
        val playlists = repository.loadPlaylists() + playlistStore.loadPlaylists()
        _state.value = _state.value.copy(playlists = playlists, visiblePlaylists = filterPlaylists(playlists, _state.value.searchQuery))
    }

    private fun openGroup(title: String, predicate: (AudioTrack) -> Boolean) {
        val tracks = _state.value.tracks.filter(predicate)
        _state.value = _state.value.copy(browseTitle = title, browseTracks = tracks)
    }

    private fun filterCollections(items: List<LibraryCollectionItem>, query: String): List<LibraryCollectionItem> =
        items.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) || it.subtitle.contains(query.trim(), ignoreCase = true) }

    private fun filterPlaylists(playlists: List<DevicePlaylist>, query: String): List<DevicePlaylist> =
        playlists.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }

    private fun filterArtists(artists: List<ArtistBrowseItem>, query: String): List<ArtistBrowseItem> =
        artists.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }

    private fun filterAlbums(albums: List<AlbumBrowseItem>, query: String): List<AlbumBrowseItem> =
        albums.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) || it.artist.contains(query.trim(), ignoreCase = true) }

    private fun filterTracks(tracks: List<AudioTrack>, query: String, sortOrder: String, hideShortTracks: Boolean): List<AudioTrack> {
        val needle = query.trim()
        val filtered = tracks.filter { track ->
            (!hideShortTracks || track.durationMs >= 30_000L) &&
                (needle.isEmpty() || track.title.contains(needle, ignoreCase = true) ||
                    track.artist.contains(needle, ignoreCase = true) || track.album.contains(needle, ignoreCase = true))
        }
        return when (sortOrder) {
            "Artist" -> filtered.sortedWith(compareBy<AudioTrack, String>(String.CASE_INSENSITIVE_ORDER) { it.albumArtist.ifBlank { it.artist } }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            "Date added" -> filtered.sortedWith(compareByDescending<AudioTrack> { it.dateAddedSeconds }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            "Duration" -> filtered.sortedWith(compareBy<AudioTrack> { it.durationMs }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            else -> filtered.sortedWith(compareBy<AudioTrack, String>(String.CASE_INSENSITIVE_ORDER) { it.title })
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
