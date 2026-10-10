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
import com.dizzyvy.rift.music.library.cleanTrackMetadata
import com.dizzyvy.rift.music.library.genreGroupLabel
import com.dizzyvy.rift.music.library.duplicatePairKey
import com.dizzyvy.rift.music.model.AudioTrack
import com.dizzyvy.rift.music.backup.LibraryBackupCodec
import com.dizzyvy.rift.music.playback.PlaybackController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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

private fun buildFolderItems(tracks: List<AudioTrack>): List<LibraryCollectionItem> = tracks
    .mapNotNull { track -> folderPath(track).takeIf(String::isNotBlank)?.let { it to track } }
    .groupBy({ it.first }, { it.second })
    .map { (path, items) -> LibraryCollectionItem(path, path.substringAfterLast('/').ifBlank { path }, path, items.size) }
    .sortedWith(compareBy<LibraryCollectionItem, String>(String.CASE_INSENSITIVE_ORDER) { it.title }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.id })

private fun buildHiddenFolderItems(tracks: List<AudioTrack>, hiddenPaths: Set<String>): List<LibraryCollectionItem> =
    hiddenPaths.map { path ->
        val matchingTracks = tracks.filter { track ->
            val trackPath = folderPath(track)
            trackPath.equals(path, ignoreCase = true) || trackPath.startsWith("$path/", ignoreCase = true)
        }
        LibraryCollectionItem(path, path.substringAfterLast('/').ifBlank { path }, path, matchingTracks.size)
    }.sortedWith(compareBy<LibraryCollectionItem, String>(String.CASE_INSENSITIVE_ORDER) { it.title }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.id })

private fun buildArtists(tracks: List<AudioTrack>, aliases: Map<String, String> = emptyMap()): List<ArtistBrowseItem> = tracks
    .flatMap { track -> artistNamesForTrack(track, aliases).map { it to track } }
    .groupBy { (name, _) -> normalizeArtistName(name) }
    .map { (id, entries) -> ArtistBrowseItem(id, entries.first().first, entries.size) }
    .sortedWith(compareBy<ArtistBrowseItem> { if (librarySection(it.name) == '#') 0 else 1 }.thenBy { librarySection(it.name) }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })

private fun buildAlbums(tracks: List<AudioTrack>, aliases: Map<String, String> = emptyMap()): List<AlbumBrowseItem> = tracks.groupBy { albumGroupKey(it, aliases) }
    .map { (id, items) ->
        val first = items.first()
        val title = first.album
        val artist = first.albumArtist.ifBlank { artistNamesForTrack(first, aliases).joinToString(", ") }
        AlbumBrowseItem(id, title, artist, items.size, first.uri)
    }
    .sortedWith(compareBy<AlbumBrowseItem> { if (librarySection(it.title) == '#') 0 else 1 }.thenBy { librarySection(it.title) }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })

private fun buildGenres(tracks: List<AudioTrack>): List<LibraryCollectionItem> = tracks
    .groupBy { genreGroupLabel(it.genre) }
    .map { (genre, items) -> LibraryCollectionItem(genre.lowercase(), genre, "Genre", items.size, items.first().uri) }
    .sortedWith(compareByDescending<LibraryCollectionItem> { it.trackCount }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })

private fun buildYears(tracks: List<AudioTrack>): List<LibraryCollectionItem> = tracks
    .groupBy { it.year.takeIf { year -> year in 1000..9999 }?.toString() ?: "Unknown" }
    .map { (year, items) -> LibraryCollectionItem(year, year, "Year", items.size, items.first().uri) }
    .sortedWith(compareBy<LibraryCollectionItem> { it.title == "Unknown" }.thenByDescending { it.title.toIntOrNull() ?: 0 })

data class DuplicateGroup(val id: String, val tracks: List<AudioTrack>, val reason: String)

private fun buildDuplicateGroups(
    tracks: List<AudioTrack>,
    aliases: Map<String, String>,
    notDuplicatePairs: Set<String>,
): List<DuplicateGroup> {
    val matches = mutableListOf<Triple<AudioTrack, AudioTrack, String>>()
    val candidates = tracks.groupBy { track ->
        val compactTitle = track.title.lowercase().filter(Char::isLetterOrDigit)
        normalizeArtistName(artistNamesForTrack(track, aliases).first()) to compactTitle
    }
    candidates.values.forEach { candidatesInBucket ->
        for (firstIndex in candidatesInBucket.indices) {
            val first = candidatesInBucket[firstIndex]
            for (secondIndex in firstIndex + 1 until candidatesInBucket.size) {
                val second = candidatesInBucket[secondIndex]
                val durationMatches = first.durationMs > 0L && second.durationMs > 0L &&
                    kotlin.math.abs(first.durationMs - second.durationMs) <= 1_000L
                if (!durationMatches) continue
                val exactTitle = first.title.trim().replace(Regex("\\s+"), " ")
                    .equals(second.title.trim().replace(Regex("\\s+"), " "), ignoreCase = true)
                val sameFileSize = first.sizeBytes > 0L && first.sizeBytes == second.sizeBytes
                val reason = when {
                    exactTitle -> "Same title and artist; duration matches within 1 second"
                    sameFileSize -> "Similar title and artist; duration and file size match"
                    else -> null
                } ?: continue
                if (duplicatePairKey(first.uri.toString(), second.uri.toString()) !in notDuplicatePairs) {
                    matches += Triple(first, second, reason)
                }
            }
        }
    }

    val adjacency = mutableMapOf<String, MutableSet<String>>()
    val reasonsByPair = mutableMapOf<String, String>()
    matches.forEach { (first, second, reason) ->
        val firstUri = first.uri.toString()
        val secondUri = second.uri.toString()
        adjacency.getOrPut(firstUri) { mutableSetOf() }.add(secondUri)
        adjacency.getOrPut(secondUri) { mutableSetOf() }.add(firstUri)
        reasonsByPair[duplicatePairKey(firstUri, secondUri)] = reason
    }
    val tracksByUri = tracks.associateBy { it.uri.toString() }
    val unvisited = adjacency.keys.toMutableSet()
    return buildList {
        while (unvisited.isNotEmpty()) {
            val pending = ArrayDeque<String>()
            val component = linkedSetOf<String>()
            pending.add(unvisited.first())
            while (pending.isNotEmpty()) {
                val uri = pending.removeFirst()
                if (!component.add(uri)) continue
                unvisited.remove(uri)
                adjacency[uri].orEmpty().forEach(pending::addLast)
            }
            val groupTracks = component.mapNotNull(tracksByUri::get)
                .sortedWith(compareBy<AudioTrack, String>(String.CASE_INSENSITIVE_ORDER) { it.title }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.filePath })
            if (groupTracks.size < 2) continue
            val reasons = groupTracks.indices.flatMap { firstIndex ->
                (firstIndex + 1 until groupTracks.size).mapNotNull { secondIndex ->
                    reasonsByPair[duplicatePairKey(groupTracks[firstIndex].uri.toString(), groupTracks[secondIndex].uri.toString())]
                }
            }.distinct()
            add(
                DuplicateGroup(
                    id = component.sorted().joinToString("|"),
                    tracks = groupTracks,
                    reason = reasons.joinToString("; ").ifBlank { "Matching title, artist, and audio details" },
                ),
            )
        }
    }.sortedWith(compareBy<DuplicateGroup, String>(String.CASE_INSENSITIVE_ORDER) { it.tracks.first().title }.thenBy { it.id })
}

private fun filterDuplicateGroups(groups: List<DuplicateGroup>, query: String): List<DuplicateGroup> =
    groups.filter { group ->
        query.isBlank() || group.tracks.any { track ->
            listOf(track.title, track.artist, track.album, track.filePath).any { it.contains(query, ignoreCase = true) }
        }
    }

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
    val playlistImportReport: PlaylistImportReport? = null,
    val sortOrder: String = "Title",
    val sortAscending: Boolean = true,
    val hideShortTracks: Boolean = false,
    val hideLongTracks: Boolean = false,
    val hideLongTracksAfterMinutes: Int = 20,
    val clickWheelSensitivity: Float = 1f,
    val clickWheelHaptics: Boolean = true,
    val lyricsTreeUri: String? = null,
    val hiddenFolderPaths: Set<String> = emptySet(),
    val favoriteUris: Set<String> = emptySet(),
    val playHistory: Map<String, TrackPlayHistory> = emptyMap(),
    val artistAliases: Map<String, String> = emptyMap(),
    val folders: List<LibraryCollectionItem> = emptyList(),
    val visibleFolders: List<LibraryCollectionItem> = emptyList(),
    val hiddenFolders: List<LibraryCollectionItem> = emptyList(),
    val visibleHiddenFolders: List<LibraryCollectionItem> = emptyList(),
    val genres: List<LibraryCollectionItem> = emptyList(),
    val visibleGenres: List<LibraryCollectionItem> = emptyList(),
    val years: List<LibraryCollectionItem> = emptyList(),
    val visibleYears: List<LibraryCollectionItem> = emptyList(),
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
    val visibleDuplicateGroups: List<DuplicateGroup> = emptyList(),
)

data class PlaylistImportReport(
    val playlistName: String,
    val matchedCount: Int,
    val totalCount: Int,
    val unmatchedEntries: List<String>,
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
                val sortAscending = settings[SETTING_SORT_ASCENDING]?.toBooleanStrictOrNull() ?: (sortOrder != "Date added")
                val hideShortTracks = settings[SETTING_HIDE_SHORT_TRACKS]?.toBooleanStrictOrNull() ?: current.hideShortTracks
                val hideLongTracks = settings[SETTING_HIDE_LONG_TRACKS]?.toBooleanStrictOrNull() ?: current.hideLongTracks
                val hideLongTracksAfterMinutes = settings[SETTING_HIDE_LONG_TRACKS_AFTER_MINUTES]?.toIntOrNull()?.takeIf { it in LONG_TRACK_FILTER_MINUTES }
                    ?: current.hideLongTracksAfterMinutes
                val wheelSensitivity = settings[SETTING_WHEEL_SENSITIVITY]?.toFloatOrNull()?.coerceIn(0.5f, 2f) ?: current.clickWheelSensitivity
                val wheelHaptics = settings[SETTING_WHEEL_HAPTICS]?.toBooleanStrictOrNull() ?: current.clickWheelHaptics
                _state.value = current.copy(
                    sortOrder = sortOrder,
                    sortAscending = sortAscending,
                    hideShortTracks = hideShortTracks,
                    hideLongTracks = hideLongTracks,
                    hideLongTracksAfterMinutes = hideLongTracksAfterMinutes,
                    clickWheelSensitivity = wheelSensitivity,
                    clickWheelHaptics = wheelHaptics,
                    lyricsTreeUri = settings[SETTING_LYRICS_TREE],
                    visibleTracks = filterTracks(availableTracks(current), current.searchQuery, sortOrder, hideShortTracks, sortAscending, hideLongTracks, hideLongTracksAfterMinutes),
                )
            }
        }
        viewModelScope.launch {
        var currentUri: String? = null
        var listenedMs = 0L
        var lastPositionMs = 0L
        var wasPlaying = false
        var countedCurrentPlay = false
        playback.snapshot.collect { snapshot ->
            val track = snapshot.currentTrack
            val uri = track?.uri?.toString()
            if (uri != currentUri) {
                currentUri = uri
                listenedMs = 0L
                countedCurrentPlay = false
                lastPositionMs = snapshot.positionMs
            } else {
                val positionDelta = snapshot.positionMs - lastPositionMs
                if (snapshot.isPlaying && wasPlaying && positionDelta in 0L..5_000L) {
                    listenedMs += positionDelta
                }
                lastPositionMs = snapshot.positionMs
            }
            wasPlaying = snapshot.isPlaying
            if (track == null || countedCurrentPlay) return@collect
            val knownDuration = _state.value.tracks.firstOrNull { it.uri == track.uri }?.durationMs
                ?.takeIf { it > 0L } ?: track.durationMs
            val thresholdMs = knownDuration.takeIf { it > 0L }
                ?.let { minOf(30_000L, (it + 1L) / 2L) } ?: 30_000L
            if (listenedMs < thresholdMs) return@collect
            countedCurrentPlay = true
            val playedAtMs = System.currentTimeMillis()
            runCatching {
                playlistStore.recordPlay(track.uri, playedAtMs)
                val history = playlistStore.loadPlayHistory()
                val current = _state.value
                val active = current.activePlaylist
                val browseTracks = if (active?.isAuto == true && active.autoKind != "favorites") {
                    automaticPlaylistTracks(active, availableTracks(current), history)
                } else current.browseTracks
                val playlists = withPlaylistCounts(current.playlists, availableTracks(current), current.favoriteUris, history)
                _state.value = current.copy(
                    playHistory = history,
                    browseTracks = browseTracks,
                    playlists = playlists,
                    visiblePlaylists = filterPlaylists(playlists, current.searchQuery),
                )
            }
                .onFailure { countedCurrentPlay = false; _state.value = _state.value.copy(actionMessage = it.message ?: "Could not save play history.") }
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
                val cachedTracks = playlistStore.loadCachedTracks().map(::cleanTrackMetadata)
                val settings = playlistStore.loadSettings()
                val mediaVersion = repository.libraryVersion()
                val artistAliases = playlistStore.loadArtistAliases()
                if (_state.value.tracks.isEmpty() && cachedTracks.isNotEmpty()) {
                    val current = _state.value
                    val cachedHidden = playlistStore.loadHiddenFolderPaths()
                    val availableCachedTracks = cachedTracks.filterNot { folderIsHidden(it, cachedHidden) }
                    val cachedArtists = buildArtists(availableCachedTracks, artistAliases)
                    val cachedAlbums = buildAlbums(availableCachedTracks, artistAliases)
                    val cachedNotDuplicatePairs = playlistStore.loadNotDuplicatePairs()
                    val cachedFolders = buildFolderItems(availableCachedTracks)
                    val cachedHiddenFolders = buildHiddenFolderItems(cachedTracks, cachedHidden)
                    val cachedGenres = buildGenres(availableCachedTracks)
                    val cachedYears = buildYears(availableCachedTracks)
                    val cachedDuplicateGroups = buildDuplicateGroups(availableCachedTracks, artistAliases, cachedNotDuplicatePairs)
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
                        hiddenFolders = cachedHiddenFolders,
                        visibleHiddenFolders = filterCollections(cachedHiddenFolders, current.searchQuery),
                        genres = cachedGenres,
                        visibleGenres = filterCollections(cachedGenres, current.searchQuery),
                        years = cachedYears,
                        visibleYears = filterCollections(cachedYears, current.searchQuery),
                        duplicateGroups = cachedDuplicateGroups,
                        visibleDuplicateGroups = filterDuplicateGroups(cachedDuplicateGroups, current.searchQuery),
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
                val cleanedScannedTracks = scan.tracks.map(::cleanTrackMetadata)
                if (scan.tracks.isNotEmpty()) playlistStore.cacheTracks(generation, scan.tracks)
                if (scan.complete) playlistStore.finishTrackCacheRefresh(generation)
                if (scan.complete) playlistStore.saveSettings(mapOf(SETTING_MEDIA_VERSION to mediaVersion))
                val tracks = if (scan.complete || cachedTracks.isEmpty()) cleanedScannedTracks else cachedTracks
                val hiddenFolderPaths = playlistStore.loadHiddenFolderPaths()
                val availableTracks = tracks.filterNot { folderIsHidden(it, hiddenFolderPaths) }
                val playlists = repository.loadPlaylists() + playlistStore.loadPlaylists()
                val favoriteUris = playlistStore.loadFavoriteUris().map { it.toString() }.toSet()
                val playHistory = playlistStore.loadPlayHistory()
                val playlistsWithCounts = withPlaylistCounts(playlists, availableTracks, favoriteUris, playHistory)
                val resolvedAliases = playlistStore.loadArtistAliases()
                val notDuplicatePairs = playlistStore.loadNotDuplicatePairs()
                val artists = buildArtists(availableTracks, resolvedAliases)
                val albums = buildAlbums(availableTracks, resolvedAliases)
                val folders = buildFolderItems(availableTracks)
                val hiddenFolders = buildHiddenFolderItems(tracks, hiddenFolderPaths)
                val genres = buildGenres(availableTracks)
                val years = buildYears(availableTracks)
                val duplicateGroups = buildDuplicateGroups(availableTracks, resolvedAliases, notDuplicatePairs)
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
                    hiddenFolders = hiddenFolders,
                    visibleHiddenFolders = filterCollections(hiddenFolders, _state.value.searchQuery),
                    genres = genres,
                    visibleGenres = filterCollections(genres, _state.value.searchQuery),
                    years = years,
                    visibleYears = filterCollections(years, _state.value.searchQuery),
                    duplicateGroups = duplicateGroups,
                    visibleDuplicateGroups = filterDuplicateGroups(duplicateGroups, _state.value.searchQuery),
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
            visibleHiddenFolders = filterCollections(current.hiddenFolders, query),
            visibleGenres = filterCollections(current.genres, query),
            visibleYears = filterCollections(current.years, query),
            visibleDuplicateGroups = filterDuplicateGroups(current.duplicateGroups, query),
        )
    }

    fun openCollection(item: LibraryCollectionItem, kind: String) {
        openGroup(item.title) { track ->
            when (kind) {
                "Folders" -> folderPath(track) == item.id
                "Genres" -> genreGroupLabel(track.genre) == item.title
                "Years" -> (track.year.takeIf { it in 1000..9999 }?.toString() ?: "Unknown") == item.id
                else -> false
            }
        }
    }

    fun setFolderHidden(path: String, hidden: Boolean, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { playlistStore.setFolderHidden(path, hidden) }
                .onSuccess {
                    if (_state.value.isLoading) refreshAfterCurrentLoad = true
                    else loadLibrary(hasAudioPermission = !_state.value.permissionRequired, forceRefresh = true)
                    onComplete()
                }
                .onFailure { _state.value = _state.value.copy(actionMessage = it.message ?: "Could not update hidden folders.") }
        }
    }

    fun setTracksNotDuplicate(uris: List<Uri>, notDuplicate: Boolean) {
        viewModelScope.launch {
            runCatching {
                playlistStore.setTracksNotDuplicate(uris, notDuplicate)
                playlistStore.loadNotDuplicatePairs()
            }.onSuccess { pairs ->
                val current = _state.value
                val tracks = availableTracks(current)
                val duplicates = buildDuplicateGroups(tracks, current.artistAliases, pairs)
                _state.value = current.copy(
                    duplicateGroups = duplicates,
                    visibleDuplicateGroups = filterDuplicateGroups(duplicates, current.searchQuery),
                )
            }.onFailure {
                _state.value = _state.value.copy(actionMessage = it.message ?: "Could not update duplicate exclusions.")
            }
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

    fun setSortAscending(ascending: Boolean) {
        val current = _state.value
        _state.value = current.copy(
            sortAscending = ascending,
            visibleTracks = filterTracks(availableTracks(current), current.searchQuery, current.sortOrder, current.hideShortTracks, ascending),
        )
        viewModelScope.launch { runCatching { playlistStore.saveSettings(mapOf(SETTING_SORT_ASCENDING to ascending.toString())) } }
    }

    fun setHideShortTracks(hide: Boolean) {
        val current = _state.value
        _state.value = current.copy(hideShortTracks = hide, visibleTracks = filterTracks(availableTracks(current), current.searchQuery, current.sortOrder, hide))
        viewModelScope.launch { runCatching { playlistStore.saveSettings(mapOf(SETTING_HIDE_SHORT_TRACKS to hide.toString())) } }
    }

    fun setHideLongTracks(hide: Boolean) {
        val current = _state.value
        _state.value = current.copy(
            hideLongTracks = hide,
            visibleTracks = filterTracks(
                availableTracks(current),
                current.searchQuery,
                current.sortOrder,
                current.hideShortTracks,
                current.sortAscending,
                hide,
                current.hideLongTracksAfterMinutes,
            ),
        )
        viewModelScope.launch { runCatching { playlistStore.saveSettings(mapOf(SETTING_HIDE_LONG_TRACKS to hide.toString())) } }
    }

    fun setHideLongTracksAfterMinutes(minutes: Int) {
        if (minutes !in LONG_TRACK_FILTER_MINUTES) return
        val current = _state.value
        _state.value = current.copy(
            hideLongTracksAfterMinutes = minutes,
            visibleTracks = filterTracks(
                availableTracks(current),
                current.searchQuery,
                current.sortOrder,
                current.hideShortTracks,
                current.sortAscending,
                current.hideLongTracks,
                minutes,
            ),
        )
        viewModelScope.launch {
            runCatching { playlistStore.saveSettings(mapOf(SETTING_HIDE_LONG_TRACKS_AFTER_MINUTES to minutes.toString())) }
        }
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
                val sortAscending = saved[SETTING_SORT_ASCENDING]?.toBooleanStrictOrNull() ?: (sortOrder != "Date added")
                val hideShortTracks = saved[SETTING_HIDE_SHORT_TRACKS]?.toBooleanStrictOrNull() ?: false
                val hideLongTracks = saved[SETTING_HIDE_LONG_TRACKS]?.toBooleanStrictOrNull() ?: false
                val hideLongTracksAfterMinutes = saved[SETTING_HIDE_LONG_TRACKS_AFTER_MINUTES]?.toIntOrNull()
                    ?.takeIf { it in LONG_TRACK_FILTER_MINUTES } ?: 20
                val wheelSensitivity = saved[SETTING_WHEEL_SENSITIVITY]?.toFloatOrNull()?.coerceIn(0.5f, 2f) ?: 1f
                val wheelHaptics = saved[SETTING_WHEEL_HAPTICS]?.toBooleanStrictOrNull() ?: true
                val hiddenFolderPaths = playlistStore.loadHiddenFolderPaths()
                _state.value = current.copy(
                    sortOrder = sortOrder,
                    sortAscending = sortAscending,
                    hideShortTracks = hideShortTracks,
                    hideLongTracks = hideLongTracks,
                    hideLongTracksAfterMinutes = hideLongTracksAfterMinutes,
                    clickWheelSensitivity = wheelSensitivity,
                    clickWheelHaptics = wheelHaptics,
                    lyricsTreeUri = saved[SETTING_LYRICS_TREE],
                    hiddenFolderPaths = hiddenFolderPaths,
                    visibleTracks = filterTracks(
                        current.tracks.filterNot { folderIsHidden(it, hiddenFolderPaths) },
                        current.searchQuery,
                        sortOrder,
                        hideShortTracks,
                        sortAscending,
                        hideLongTracks,
                        hideLongTracksAfterMinutes,
                    ),
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
        val tracks = _state.value.visibleTracks.filter { it.durationMs <= SHUFFLE_MAX_TRACK_DURATION_MS }
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

    fun clearActionMessage() {
        _state.value = _state.value.copy(actionMessage = null)
    }

    fun importM3u(name: String, contents: String) {
        val playlistName = name.substringBeforeLast('.', name).ifBlank { name }
        val availableTracks = _state.value.tracks
        val tracksByUri = availableTracks.associateBy { it.uri.toString() }
        val tracksByFileName = availableTracks
            .filter { it.displayName.isNotBlank() }
            .associateBy { it.displayName.lowercase() }
        var pendingDescription: String? = null
        val entries = contents.lineSequence().mapNotNull { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    pendingDescription = line.substringAfter(',', "").trim()
                    null
                }
                line.isBlank() || line.startsWith("#") -> null
                else -> line to pendingDescription.also { pendingDescription = null }
            }
        }.toList()
        val matches = entries.map { (entry, description) ->
            val uriMatch = tracksByUri[entry]
            val fileNameMatch = tracksByFileName[M3uPlaylistFormat.fileName(entry).lowercase()]
            val metadataMatch = description?.let {
                val (artist, title) = it.split(" - ", limit = 2).let { parts ->
                    if (parts.size == 2) parts[0].trim() to parts[1].trim() else "" to parts[0].trim()
                }
                availableTracks.firstOrNull { track ->
                    track.title.equals(title, ignoreCase = true) &&
                        (artist.isBlank() || track.artist.equals(artist, ignoreCase = true))
                }
            }
            uriMatch ?: fileNameMatch ?: metadataMatch
        }
        val matchedTracks = matches.filterNotNull().distinctBy { it.uri }
        val unmatchedEntries = entries.filterIndexed { index, _ -> matches[index] == null }.map { it.first }
        viewModelScope.launch {
            runCatching {
                playlistStore.createPlaylist(playlistName).also { playlist ->
                    matchedTracks.forEach { playlistStore.addTrack(playlist.id, it.uri) }
                }
            }.onSuccess {
                refreshPlaylists()
                _state.value = _state.value.copy(
                    playlistImportReport = PlaylistImportReport(playlistName, matchedTracks.size, entries.size, unmatchedEntries),
                )
            }.onFailure { _state.value = _state.value.copy(actionMessage = it.message ?: "Could not import playlist.") }
        }
    }

    fun clearPlaylistImportReport() {
        _state.value = _state.value.copy(playlistImportReport = null)
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
        "recently_added" -> {
            val cutoff = (System.currentTimeMillis() / 1_000L) - RECENTLY_ADDED_DAYS * SECONDS_PER_DAY
            tracks.asSequence()
                .filter { it.dateAddedSeconds >= cutoff }
                .sortedWith(compareByDescending<AudioTrack> { it.dateAddedSeconds }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                .take(RECENTLY_ADDED_LIMIT)
                .toList()
        }
        "recently_played" -> tracks.filter { it.uri.toString() in history }
            .sortedByDescending { history[it.uri.toString()]?.lastPlayedAtMs ?: 0L }
        "most_played" -> tracks.filter { it.uri.toString() in history }
            .sortedWith(compareByDescending<AudioTrack> { history[it.uri.toString()]?.playCount ?: 0 }.thenByDescending { history[it.uri.toString()]?.lastPlayedAtMs ?: 0L })
        "never_played" -> tracks.filter { it.uri.toString() !in history }
            .sortedWith(compareByDescending<AudioTrack> { it.dateAddedSeconds }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        else -> emptyList()
    }

    private suspend fun withPlaylistCounts(
        playlists: List<DevicePlaylist>,
        tracks: List<AudioTrack>,
        favorites: Set<String>,
        history: Map<String, TrackPlayHistory>,
    ): List<DevicePlaylist> {
        val tracksByUri = tracks.associateBy { it.uri }
        return playlists.map { playlist ->
            val playlistTracks = when {
                playlist.autoKind == "favorites" -> tracks.filter { it.uri.toString() in favorites }
                playlist.autoKind != null -> automaticPlaylistTracks(playlist, tracks, history)
                playlist.isLocal -> playlistStore.loadTrackUris(playlist.id).mapNotNull(tracksByUri::get)
                else -> repository.loadPlaylistTracks(playlist)
            }
            val count = when {
                playlist.autoKind == "favorites" -> favorites.size
                playlist.autoKind != null -> playlistTracks.size
                else -> playlist.trackCount
            }
            playlist.copy(trackCount = count, artworkUris = playlistTracks.take(4).map { it.uri })
        }
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

    private fun filterTracks(
        tracks: List<AudioTrack>,
        query: String,
        sortOrder: String,
        hideShortTracks: Boolean,
        ascending: Boolean = _state.value.sortAscending,
        hideLongTracks: Boolean = _state.value.hideLongTracks,
        hideLongTracksAfterMinutes: Int = _state.value.hideLongTracksAfterMinutes,
    ): List<AudioTrack> {
        val needle = query.trim()
        val filtered = tracks.filter { track ->
            (!hideShortTracks || track.durationMs >= 30_000L) &&
                (!hideLongTracks || track.durationMs <= hideLongTracksAfterMinutes * MILLISECONDS_PER_MINUTE) &&
                (needle.isEmpty() || track.title.contains(needle, ignoreCase = true) ||
                    track.artist.contains(needle, ignoreCase = true) || track.album.contains(needle, ignoreCase = true))
        }
        val sorted = when (sortOrder) {
            "Artist" -> filtered.sortedWith(compareBy<AudioTrack, String>(String.CASE_INSENSITIVE_ORDER) { it.albumArtist.ifBlank { it.artist } }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            "Date added" -> filtered.sortedWith(compareBy<AudioTrack> { it.dateAddedSeconds }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            "Duration" -> filtered.sortedWith(compareBy<AudioTrack> { it.durationMs }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            else -> filtered.sortedWith(
                compareBy<AudioTrack, String>(String.CASE_INSENSITIVE_ORDER) { sortableTitle(it.title) }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
            )
        }
        return if (ascending) sorted else sorted.asReversed()
    }

    private fun sortableTitle(title: String): String {
        val withoutLeadingPunctuation = title.trim().replace(Regex("""^[\p{P}\p{S}\s]+"""), "")
        val withoutArticle = withoutLeadingPunctuation.replace(Regex("""^the\b[\s\p{P}\p{S}]*""", RegexOption.IGNORE_CASE), "")
        return withoutArticle.replace(Regex("""^[\p{P}\p{S}\s]+"""), "")
    }

    private companion object {
        const val SETTING_SORT_ORDER = "sortOrder"
        const val SETTING_SORT_ASCENDING = "sortAscending"
        const val SETTING_HIDE_SHORT_TRACKS = "hideShortTracks"
        const val SETTING_HIDE_LONG_TRACKS = "hideLongTracks"
        const val SETTING_HIDE_LONG_TRACKS_AFTER_MINUTES = "hideLongTracksAfterMinutes"
        const val SETTING_WHEEL_SENSITIVITY = "clickWheelSensitivity"
        const val SETTING_WHEEL_HAPTICS = "clickWheelHaptics"
        const val SETTING_MEDIA_VERSION = "mediaStoreVersion"
        const val SETTING_LYRICS_TREE = "lyricsTreeUri"
        const val RECENTLY_ADDED_DAYS = 30L
        const val SECONDS_PER_DAY = 24L * 60L * 60L
        const val RECENTLY_ADDED_LIMIT = 100
        const val SHUFFLE_MAX_TRACK_DURATION_MS = 20L * 60L * 1_000L
        const val MILLISECONDS_PER_MINUTE = 60_000
        val LONG_TRACK_FILTER_MINUTES = setOf(20, 30, 45, 60, 90, 120)
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
