package com.dizzyvy.rift.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dizzyvy.rift.music.library.AudioLibraryRepository
import com.dizzyvy.rift.music.library.BackupTrackRef
import com.dizzyvy.rift.music.library.BackupPlaylist
import com.dizzyvy.rift.music.library.LibraryBackupSnapshot
import com.dizzyvy.rift.music.library.PlaylistStore
import com.dizzyvy.rift.music.library.TrackPlayHistory
import com.dizzyvy.rift.music.library.M3uPlaylistFormat
import com.dizzyvy.rift.music.library.AlbumBrowseItem
import com.dizzyvy.rift.music.library.LibraryCollectionItem
import com.dizzyvy.rift.music.library.normalizeArtistName
import com.dizzyvy.rift.music.library.ArtistBrowseItem
import com.dizzyvy.rift.music.library.DevicePlaylist
import com.dizzyvy.rift.music.library.librarySection
import com.dizzyvy.rift.music.library.artistGroupKey
import com.dizzyvy.rift.music.library.artistGroupKeys
import com.dizzyvy.rift.music.library.artistNamesForTrack
import com.dizzyvy.rift.music.library.resolveArtistAlias
import com.dizzyvy.rift.music.library.albumGroupKey
import com.dizzyvy.rift.music.model.AudioTrack
import com.dizzyvy.rift.music.backup.LibraryBackupCodec
import com.dizzyvy.rift.music.playback.PlaybackController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private fun folderPath(track: AudioTrack): String = track.relativePath.trimEnd('/').ifBlank {
    track.filePath.substringBeforeLast('/', "")
}

private fun folderIsHidden(track: AudioTrack, hiddenFolderPaths: Set<String>): Boolean {
    val path = folderPath(track)
    return hiddenFolderPaths.any { hidden ->
        path.equals(hidden, ignoreCase = true) || path.startsWith("$hidden/", ignoreCase = true)
    }
}

private fun buildArtists(tracks: List<AudioTrack>, aliases: Map<String, String> = emptyMap()): List<ArtistBrowseItem> = tracks
    .flatMap { track -> artistNamesForTrack(track, aliases).map { it to track } }
    .groupBy { (name, _) -> normalizeArtistName(name) }
    .map { (id, entries) -> ArtistBrowseItem(id, entries.first().first, entries.size, entries.first().second.uri) }
    .sortedWith(compareBy<ArtistBrowseItem> { if (librarySection(it.name) == '#') 0 else 1 }.thenBy { librarySection(it.name) }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })

private fun buildAlbums(tracks: List<AudioTrack>, aliases: Map<String, String> = emptyMap()): List<AlbumBrowseItem> = tracks.groupBy { albumGroupKey(it, aliases) }
    .map { (id, items) ->
        val first = items.first()
        val title = first.album.takeUnless { it.isBlank() || it.equals("<unknown>", true) } ?: "Unknown album"
        val artist = artistNamesForTrack(first, aliases).joinToString(", ").ifBlank { "Unknown artist" }
        AlbumBrowseItem(id, title, artist, items.size, first.uri)
    }
    .sortedWith(compareBy<AlbumBrowseItem> { if (librarySection(it.title) == '#') 0 else 1 }.thenBy { librarySection(it.title) }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })

private fun buildDuplicateTracks(tracks: List<AudioTrack>, aliases: Map<String, String>): List<AudioTrack> = tracks.groupBy { track ->
    "${track.title.trim().lowercase()}|${normalizeArtistName(artistNamesForTrack(track, aliases).first())}|${track.durationMs / 1000L}"
}.filterValues { it.size > 1 }.values.flatten().distinctBy { it.uri }
    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

data class LibraryUiState(
    val tracks: List<AudioTrack> = emptyList(),
    val visibleTracks: List<AudioTrack> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val scanProcessed: Int = 0,
    val scanTotal: Int? = null,
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
    val clickWheelSensitivity: Float = 1f,
    val clickWheelHaptics: Boolean = true,
    val lyricsTreeUri: String? = null,
    val hiddenFolderPaths: Set<String> = emptySet(),
    val favoriteUris: Set<String> = emptySet(),
    val playHistory: Map<String, TrackPlayHistory> = emptyMap(),
    val artistAliases: Map<String, String> = emptyMap(),
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
    private var refreshAfterCurrentLoad = false
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { playlistStore.loadSettings() }.onSuccess { settings ->
                val current = _state.value
                val sortOrder = settings[SETTING_SORT_ORDER]?.takeIf { it in SORT_ORDERS } ?: current.sortOrder
                val hideShortTracks = settings[SETTING_HIDE_SHORT_TRACKS]?.toBooleanStrictOrNull() ?: current.hideShortTracks
                val wheelSensitivity = settings[SETTING_WHEEL_SENSITIVITY]?.toFloatOrNull()?.coerceIn(0.5f, 2f) ?: current.clickWheelSensitivity
                val wheelHaptics = settings[SETTING_WHEEL_HAPTICS]?.toBooleanStrictOrNull() ?: current.clickWheelHaptics
                _state.value = current.copy(
                    sortOrder = sortOrder,
                    hideShortTracks = hideShortTracks,
                    clickWheelSensitivity = wheelSensitivity,
                    clickWheelHaptics = wheelHaptics,
                    lyricsTreeUri = settings[SETTING_LYRICS_TREE],
                    visibleTracks = filterTracks(availableTracks(current), current.searchQuery, sortOrder, hideShortTracks),
                )
            }
        }
        viewModelScope.launch {
            playback.snapshot
                .map { it.currentTrack }
                .filterNotNull()
                .distinctUntilChangedBy { it.uri }
                .collect { track ->
                    runCatching {
                        playlistStore.recordPlay(track.uri, System.currentTimeMillis())
                        val history = playlistStore.loadPlayHistory()
                        val current = _state.value
                        val active = current.activePlaylist
                        val browseTracks = if (active?.isAuto == true && active.autoKind != "favorites") {
                            automaticPlaylistTracks(active, availableTracks(current), history)
                        } else current.browseTracks
                        _state.value = current.copy(playHistory = history, browseTracks = browseTracks)
                    }
                }
        }
    }

    fun loadLibrary(hasAudioPermission: Boolean, forceRefresh: Boolean = false) {
        if (!hasAudioPermission) {
            _state.value = _state.value.copy(isLoading = false, permissionRequired = true)
            return
        }
        if (_state.value.isLoading || (!forceRefresh && _state.value.tracks.isNotEmpty())) return

        _state.value = _state.value.copy(isLoading = true, permissionRequired = false, message = null, scanProcessed = 0, scanTotal = null)
        viewModelScope.launch {
            try {
                val cachedTracks = playlistStore.loadCachedTracks()
                val settings = playlistStore.loadSettings()
                val mediaVersion = repository.libraryVersion()
                val artistAliases = playlistStore.loadArtistAliases()
                if (_state.value.tracks.isEmpty() && cachedTracks.isNotEmpty()) {
                    val current = _state.value
                    val cachedHidden = playlistStore.loadHiddenFolderPaths()
                    val availableCachedTracks = cachedTracks.filterNot { folderIsHidden(it, cachedHidden) }
                    val cachedArtists = buildArtists(availableCachedTracks, artistAliases)
                    val cachedAlbums = buildAlbums(availableCachedTracks, artistAliases)
                    val cachedFolders = availableCachedTracks.mapNotNull { track -> folderPath(track).takeIf(String::isNotBlank)?.let { it to track } }
                        .groupBy({ it.first }, { it.second }).map { (path, items) -> LibraryCollectionItem(path, path.substringAfterLast('/').ifBlank { path }, path, items.size, items.firstOrNull()?.uri) }
                    val cachedGenres = availableCachedTracks.filter { it.genre.isNotBlank() && !it.genre.equals("<unknown>", true) }
                        .groupBy { it.genre.trim().lowercase() }.map { (_, items) -> LibraryCollectionItem(items.first().genre.trim().lowercase(), items.first().genre.trim(), "Genre", items.size, items.first().uri) }
                    val cachedYears = availableCachedTracks.filter { it.year in 1000..9999 }.groupBy { it.year.toString() }
                        .map { (year, items) -> LibraryCollectionItem(year, year, "Year", items.size, items.first().uri) }
                    val cachedDuplicates = buildDuplicateTracks(availableCachedTracks, artistAliases)
                    val cachedFavorites = playlistStore.loadFavoriteUris().map { it.toString() }.toSet()
                    val cachedHistory = playlistStore.loadPlayHistory()
                    val cachedPlaylists = withPlaylistCounts(repository.loadPlaylists() + playlistStore.loadPlaylists(), availableCachedTracks, cachedFavorites, cachedHistory)
                    _state.value = current.copy(
                        artistAliases = artistAliases,
                        tracks = cachedTracks,
                        visibleTracks = filterTracks(availableCachedTracks, current.searchQuery, current.sortOrder, current.hideShortTracks),
                        artists = cachedArtists,
                        visibleArtists = filterArtists(cachedArtists, current.searchQuery),
                        albums = cachedAlbums,
                        visibleAlbums = filterAlbums(cachedAlbums, current.searchQuery),
                        hiddenFolderPaths = cachedHidden,
                        folders = cachedFolders,
                        visibleFolders = filterCollections(cachedFolders, current.searchQuery),
                        genres = cachedGenres,
                        visibleGenres = filterCollections(cachedGenres, current.searchQuery),
                        years = cachedYears,
                        visibleYears = filterCollections(cachedYears, current.searchQuery),
                        duplicateTracks = cachedDuplicates,
                        visibleDuplicateTracks = filterTracks(cachedDuplicates, current.searchQuery, current.sortOrder, current.hideShortTracks),
                        favoriteUris = cachedFavorites,
                        playHistory = cachedHistory,
                        playlists = cachedPlaylists,
                        visiblePlaylists = filterPlaylists(cachedPlaylists, current.searchQuery),
                    )
                }
                if (!forceRefresh && cachedTracks.isNotEmpty() && settings[SETTING_MEDIA_VERSION] == mediaVersion) {
                    val current = _state.value
                    _state.value = current.copy(isLoading = false, scanProcessed = cachedTracks.size, scanTotal = cachedTracks.size)
                    return@launch
                }
                val generation = playlistStore.beginTrackCacheRefresh()
                val scan = repository.scanTracks { processed, total ->
                    val current = _state.value
                    if (processed == total || processed % 96 == 0) {
                        _state.value = current.copy(scanProcessed = processed, scanTotal = total)
                    }
                }
                if (scan.tracks.isNotEmpty()) playlistStore.cacheTracks(generation, scan.tracks)
                if (scan.complete) playlistStore.finishTrackCacheRefresh(generation)
                if (scan.complete) playlistStore.saveSettings(mapOf(SETTING_MEDIA_VERSION to mediaVersion))
                val tracks = if (scan.complete || cachedTracks.isEmpty()) scan.tracks else cachedTracks
                val hiddenFolderPaths = playlistStore.loadHiddenFolderPaths()
                val availableTracks = tracks.filterNot { folderIsHidden(it, hiddenFolderPaths) }
                val playlists = repository.loadPlaylists() + playlistStore.loadPlaylists()
                val favoriteUris = playlistStore.loadFavoriteUris().map { it.toString() }.toSet()
                val playHistory = playlistStore.loadPlayHistory()
                val playlistsWithCounts = withPlaylistCounts(playlists, availableTracks, favoriteUris, playHistory)
                val resolvedAliases = playlistStore.loadArtistAliases()
                val artists = buildArtists(availableTracks, resolvedAliases)
                val albums = buildAlbums(availableTracks, resolvedAliases)
                val folders = tracks.mapNotNull { track ->
                    folderPath(track).takeIf(String::isNotBlank)?.let { it to track }
                }.groupBy({ it.first }, { it.second }).map { (path, items) ->
                    LibraryCollectionItem(path, path.substringAfterLast('/').ifBlank { path }, path, items.size, items.firstOrNull()?.uri)
                }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                val genres = availableTracks.filter { it.genre.isNotBlank() && !it.genre.equals("<unknown>", true) }
                    .groupBy { it.genre.trim().lowercase() }.map { (_, items) ->
                        LibraryCollectionItem(items.first().genre.trim().lowercase(), items.first().genre.trim(), "Genre", items.size, items.first().uri)
                    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                val years = availableTracks.filter { it.year in 1000..9999 }.groupBy { it.year.toString() }
                    .map { (year, items) -> LibraryCollectionItem(year, year, "Year", items.size, items.first().uri) }
                    .sortedByDescending { it.title.toIntOrNull() ?: 0 }
                val duplicateTracks = buildDuplicateTracks(availableTracks, resolvedAliases)
                _state.value = _state.value.copy(
                    tracks = tracks,
                    artistAliases = resolvedAliases,
                    visibleTracks = filterTracks(availableTracks, _state.value.searchQuery, _state.value.sortOrder, _state.value.hideShortTracks),
                    artists = artists,
                    visibleArtists = filterArtists(artists, _state.value.searchQuery),
                    albums = albums,
                    visibleAlbums = filterAlbums(albums, _state.value.searchQuery),
                    playlists = playlistsWithCounts,
                    favoriteUris = favoriteUris,
                    playHistory = playHistory,
                    folders = folders,
                    hiddenFolderPaths = hiddenFolderPaths,
                    visibleFolders = filterCollections(folders, _state.value.searchQuery),
                    genres = genres,
                    visibleGenres = filterCollections(genres, _state.value.searchQuery),
                    years = years,
                    visibleYears = filterCollections(years, _state.value.searchQuery),
                    duplicateTracks = duplicateTracks,
                    visibleDuplicateTracks = filterTracks(duplicateTracks, _state.value.searchQuery, _state.value.sortOrder, _state.value.hideShortTracks),
                    visiblePlaylists = filterPlaylists(playlistsWithCounts, _state.value.searchQuery),
                    isLoading = false,
                    scanProcessed = if (scan.complete) tracks.size else _state.value.scanProcessed,
                    scanTotal = if (scan.complete) tracks.size else _state.value.scanTotal,
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
            } finally {
                if (refreshAfterCurrentLoad) {
                    refreshAfterCurrentLoad = false
                    loadLibrary(hasAudioPermission = !_state.value.permissionRequired, forceRefresh = true)
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        val current = _state.value
        _state.value = current.copy(
            category = if (query.isBlank() && current.category == "Search") "Songs" else current.category,
            searchQuery = query,
            visibleTracks = filterTracks(availableTracks(current), query, current.sortOrder, current.hideShortTracks),
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
                "Folders" -> folderPath(track) == item.id
                "Genres" -> track.genre.equals(item.title, ignoreCase = true)
                "Years" -> track.year.toString() == item.id
                else -> false
            }
        }
    }

    fun setFolderHidden(path: String, hidden: Boolean) {
        viewModelScope.launch {
            runCatching { playlistStore.setFolderHidden(path, hidden) }
                .onSuccess {
                    if (_state.value.isLoading) refreshAfterCurrentLoad = true
                    else loadLibrary(hasAudioPermission = !_state.value.permissionRequired, forceRefresh = true)
                }
                .onFailure { _state.value = _state.value.copy(actionMessage = it.message ?: "Could not update hidden folders.") }
        }
    }

    private fun availableTracks(state: LibraryUiState): List<AudioTrack> =
        state.tracks.filterNot { folderIsHidden(it, state.hiddenFolderPaths) }

    fun setSortOrder(order: String) {
        if (order !in SORT_ORDERS) return
        val current = _state.value
        _state.value = current.copy(sortOrder = order, visibleTracks = filterTracks(availableTracks(current), current.searchQuery, order, current.hideShortTracks))
        viewModelScope.launch { runCatching { playlistStore.saveSettings(mapOf(SETTING_SORT_ORDER to order)) } }
    }

    fun setHideShortTracks(hide: Boolean) {
        val current = _state.value
        _state.value = current.copy(hideShortTracks = hide, visibleTracks = filterTracks(availableTracks(current), current.searchQuery, current.sortOrder, hide))
        viewModelScope.launch { runCatching { playlistStore.saveSettings(mapOf(SETTING_HIDE_SHORT_TRACKS to hide.toString())) } }
    }

    fun setClickWheelSensitivity(sensitivity: Float) {
        val value = sensitivity.coerceIn(0.5f, 2f)
        _state.value = _state.value.copy(clickWheelSensitivity = value)
        saveAppSettings(mapOf(SETTING_WHEEL_SENSITIVITY to value.toString()))
    }

    fun setClickWheelHaptics(enabled: Boolean) {
        _state.value = _state.value.copy(clickWheelHaptics = enabled)
        saveAppSettings(mapOf(SETTING_WHEEL_HAPTICS to enabled.toString()))
    }

    fun saveAppSettings(settings: Map<String, String>) {
        viewModelScope.launch { runCatching { playlistStore.saveSettings(settings) } }
    }

    fun setLyricsDirectory(uri: Uri) {
        saveAppSettings(mapOf(SETTING_LYRICS_TREE to uri.toString()))
        _state.value = _state.value.copy(lyricsTreeUri = uri.toString())
    }

    fun exportBackup(onReady: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { LibraryBackupCodec.encode(playlistStore.createBackupSnapshot(_state.value.tracks)) }
                .onSuccess(onReady)
                .onFailure { _state.value = _state.value.copy(actionMessage = it.message ?: "Could not create backup.") }
        }
    }

    fun importBackup(contents: String, onSettingsRestored: (Map<String, String>) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val snapshot = LibraryBackupCodec.decode(contents)
                val tracks = _state.value.tracks
                val references = snapshot.playlists.sumOf { it.tracks.size } + snapshot.favorites.size
                require(references == 0 || tracks.isNotEmpty()) { "Scan the music library before restoring playlists and favorites." }
                val byUri = tracks.associateBy { it.uri.toString() }
                val byMetadata = tracks.groupBy(::trackFingerprint)
                fun resolve(ref: BackupTrackRef): AudioTrack? = byUri[ref.uri] ?: byMetadata[backupFingerprint(ref)]?.firstOrNull()
                val resolvedPlaylists = snapshot.playlists.map { playlist -> playlist.name to playlist.tracks.mapNotNull { resolve(it)?.uri } }
                val resolvedFavorites = snapshot.favorites.mapNotNull { resolve(it)?.uri }
                val totalTrackRefs = snapshot.playlists.sumOf { it.tracks.size } + snapshot.favorites.size
                val resolvedTrackRefs = resolvedPlaylists.sumOf { it.second.size } + resolvedFavorites.size
                playlistStore.restoreBackupSnapshot(snapshot, resolvedPlaylists, resolvedFavorites)
                val saved = playlistStore.loadSettings()
                val current = _state.value
                val sortOrder = saved[SETTING_SORT_ORDER]?.takeIf { it in SORT_ORDERS } ?: "Title"
                val hideShortTracks = saved[SETTING_HIDE_SHORT_TRACKS]?.toBooleanStrictOrNull() ?: false
                val wheelSensitivity = saved[SETTING_WHEEL_SENSITIVITY]?.toFloatOrNull()?.coerceIn(0.5f, 2f) ?: 1f
                val wheelHaptics = saved[SETTING_WHEEL_HAPTICS]?.toBooleanStrictOrNull() ?: true
                val hiddenFolderPaths = playlistStore.loadHiddenFolderPaths()
                _state.value = current.copy(
                    sortOrder = sortOrder,
                    hideShortTracks = hideShortTracks,
                    clickWheelSensitivity = wheelSensitivity,
                    clickWheelHaptics = wheelHaptics,
                    lyricsTreeUri = saved[SETTING_LYRICS_TREE],
                    hiddenFolderPaths = hiddenFolderPaths,
                    visibleTracks = filterTracks(current.tracks.filterNot { folderIsHidden(it, hiddenFolderPaths) }, current.searchQuery, sortOrder, hideShortTracks),
                    actionMessage = "Backup restored. ${totalTrackRefs - resolvedTrackRefs} track(s) were not found.",
                )
                onSettingsRestored(saved)
                refreshPlaylists()
            }
                .onFailure { _state.value = _state.value.copy(actionMessage = it.message ?: "Could not restore backup.") }
        }
    }

    private fun trackFingerprint(track: AudioTrack): String = listOf(
        track.title.trim().lowercase(), normalizeArtistName(track.artist),
        track.album.trim().lowercase(), (track.durationMs / 1000L).toString(),
    ).joinToString("|")

    private fun backupFingerprint(track: BackupTrackRef): String = listOf(
        track.title.trim().lowercase(), normalizeArtistName(track.artist), track.album.trim().lowercase(),
        (track.durationMs / 1000L).toString(),
    ).joinToString("|")

    fun setFavorite(track: AudioTrack, favorite: Boolean) {
        viewModelScope.launch {
            runCatching { playlistStore.setFavorite(track.uri, favorite) }
                .onSuccess {
                    val current = _state.value
                    val favorites = current.favoriteUris.toMutableSet().apply {
                        if (favorite) add(track.uri.toString()) else remove(track.uri.toString())
                    }
                    val active = current.activePlaylist
                    val browseTracks = when {
                        active?.autoKind == "favorites" -> loadLocalTracks(active.id)
                        active?.isAuto == true -> automaticPlaylistTracks(active, availableTracks(current), current.playHistory)
                        else -> current.browseTracks
                    }
                    _state.value = current.copy(favoriteUris = favorites, browseTracks = browseTracks)
                    refreshPlaylists()
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
        playback.setQueue(if (index >= 0) tracks else listOf(track), if (index >= 0) index else 0)
    }

    fun selectCategory(category: String) {
        _state.value = _state.value.copy(category = category, browseTitle = null, browseTracks = null, activePlaylist = null, actionMessage = null)
    }

    fun closeGroup() {
        _state.value = _state.value.copy(browseTitle = null, browseTracks = null, activePlaylist = null)
    }

    fun openArtist(item: ArtistBrowseItem) = openGroup(item.name) { item.id in artistGroupKeys(it, _state.value.artistAliases) }
    fun openAlbum(item: AlbumBrowseItem) = openGroup(item.title) { albumGroupKey(it, _state.value.artistAliases) == item.id }

    fun setArtistAlias(alias: String, canonicalName: String) {
        viewModelScope.launch {
            runCatching {
                val aliases = _state.value.artistAliases.toMutableMap()
                val aliasKey = normalizeArtistName(alias)
                aliases[aliasKey] = canonicalName.trim()
                require(normalizeArtistName(resolveArtistAlias(canonicalName, aliases)) != aliasKey) { "That merge would create an artist alias loop." }
                playlistStore.setArtistAlias(alias, canonicalName)
                aliases
            }.onSuccess(::refreshArtistGroups)
                .onFailure { _state.value = _state.value.copy(actionMessage = it.message ?: "Could not merge the artist name.") }
        }
    }

    fun removeArtistAlias(alias: String) {
        viewModelScope.launch {
            runCatching {
                playlistStore.removeArtistAlias(alias)
                playlistStore.loadArtistAliases()
            }.onSuccess(::refreshArtistGroups)
                .onFailure { _state.value = _state.value.copy(actionMessage = it.message ?: "Could not remove the artist alias.") }
        }
    }

    private fun refreshArtistGroups(aliases: Map<String, String>) {
        val current = _state.value
        val tracks = availableTracks(current)
        val artists = buildArtists(tracks, aliases)
        val albums = buildAlbums(tracks, aliases)
        _state.value = current.copy(
            artistAliases = aliases,
            artists = artists,
            visibleArtists = filterArtists(artists, current.searchQuery),
            albums = albums,
            visibleAlbums = filterAlbums(albums, current.searchQuery),
        )
    }

    fun openPlaylist(item: DevicePlaylist) {
        viewModelScope.launch {
            val tracks = loadPlaylistTracks(item)
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
                val tracks = loadPlaylistTracks(playlist)
                M3uPlaylistFormat.encode(playlist.name, tracks)
            }.onSuccess(onReady)
                .onFailure { _state.value = _state.value.copy(actionMessage = it.message ?: "Could not export playlist.") }
        }
    }

    fun renamePlaylist(playlist: DevicePlaylist, name: String) {
        if (!playlist.isLocal || playlist.isAuto) return
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
        if (!playlist.isLocal || playlist.isAuto) return
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
        if (!playlist.isLocal || (playlist.isAuto && playlist.autoKind != "favorites")) return
        viewModelScope.launch {
            runCatching { tracks.forEach { playlistStore.addTrack(playlist.id, it.uri) } }
                .onSuccess {
                    val current = _state.value
                    val favorites = if (playlist.autoKind == "favorites") current.favoriteUris + tracks.map { it.uri.toString() } else current.favoriteUris
                    val browseTracks = if (current.activePlaylist?.id == playlist.id) loadPlaylistTracks(playlist) else current.browseTracks
                    _state.value = current.copy(favoriteUris = favorites, browseTracks = browseTracks, actionMessage = null)
                }
                .onFailure { _state.value = _state.value.copy(actionMessage = it.message ?: "Could not add songs to playlist.") }
        }
    }

    fun addTrackToPlaylist(playlist: DevicePlaylist, track: AudioTrack) {
        if (!playlist.isLocal || (playlist.isAuto && playlist.autoKind != "favorites")) return
        viewModelScope.launch {
            runCatching { playlistStore.addTrack(playlist.id, track.uri) }
                .onSuccess {
                    val current = _state.value
                    val favorites = if (playlist.autoKind == "favorites") current.favoriteUris + track.uri.toString() else current.favoriteUris
                    val browseTracks = if (current.activePlaylist?.id == playlist.id) loadLocalTracks(playlist.id) else current.browseTracks
                    _state.value = current.copy(favoriteUris = favorites, browseTracks = browseTracks, actionMessage = null)
                }
                .onFailure { _state.value = _state.value.copy(actionMessage = it.message ?: "Could not add song to playlist.") }
        }
    }

    fun removeTrackFromPlaylist(track: AudioTrack) {
        val playlist = _state.value.activePlaylist ?: return
        if (!playlist.isLocal || (playlist.isAuto && playlist.autoKind != "favorites")) return
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

    private suspend fun loadPlaylistTracks(playlist: DevicePlaylist): List<AudioTrack> = when {
        playlist.autoKind == "favorites" -> loadLocalTracks(playlist.id)
        playlist.autoKind != null -> automaticPlaylistTracks(playlist, availableTracks(_state.value), _state.value.playHistory)
        playlist.isLocal -> loadLocalTracks(playlist.id)
        else -> repository.loadPlaylistTracks(playlist)
    }

    private fun automaticPlaylistTracks(
        playlist: DevicePlaylist,
        tracks: List<AudioTrack>,
        history: Map<String, TrackPlayHistory>,
    ): List<AudioTrack> = when (playlist.autoKind) {
        "recently_added" -> tracks.sortedWith(compareByDescending<AudioTrack> { it.dateAddedSeconds }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        "recently_played" -> tracks.filter { it.uri.toString() in history }
            .sortedByDescending { history[it.uri.toString()]?.lastPlayedAtMs ?: 0L }
        "most_played" -> tracks.filter { it.uri.toString() in history }
            .sortedWith(compareByDescending<AudioTrack> { history[it.uri.toString()]?.playCount ?: 0 }.thenByDescending { history[it.uri.toString()]?.lastPlayedAtMs ?: 0L })
        "never_played" -> tracks.filter { it.uri.toString() !in history }
            .sortedWith(compareByDescending<AudioTrack> { it.dateAddedSeconds }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        else -> emptyList()
    }

    private fun withPlaylistCounts(
        playlists: List<DevicePlaylist>,
        tracks: List<AudioTrack>,
        favorites: Set<String>,
        history: Map<String, TrackPlayHistory>,
    ): List<DevicePlaylist> = playlists.map { playlist ->
        val count = when {
            playlist.autoKind == "favorites" -> favorites.size
            playlist.autoKind != null -> automaticPlaylistTracks(playlist, tracks, history).size
            else -> playlist.trackCount
        }
        playlist.copy(trackCount = count)
    }

    private suspend fun loadLocalTracks(playlistId: Long): List<AudioTrack> {
        val tracksByUri = _state.value.tracks.associateBy { it.uri }
        return playlistStore.loadTrackUris(playlistId).mapNotNull(tracksByUri::get)
    }

    private suspend fun refreshPlaylists() {
        val current = _state.value
        val playlists = withPlaylistCounts(
            repository.loadPlaylists() + playlistStore.loadPlaylists(),
            availableTracks(current),
            current.favoriteUris,
            current.playHistory,
        )
        _state.value = current.copy(playlists = playlists, visiblePlaylists = filterPlaylists(playlists, current.searchQuery))
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
    private companion object {
        const val SETTING_SORT_ORDER = "sortOrder"
        const val SETTING_HIDE_SHORT_TRACKS = "hideShortTracks"
        const val SETTING_WHEEL_SENSITIVITY = "clickWheelSensitivity"
        const val SETTING_WHEEL_HAPTICS = "clickWheelHaptics"
        const val SETTING_MEDIA_VERSION = "mediaStoreVersion"
        const val SETTING_LYRICS_TREE = "lyricsTreeUri"
        val SORT_ORDERS = setOf("Title", "Artist", "Date added", "Duration")
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
