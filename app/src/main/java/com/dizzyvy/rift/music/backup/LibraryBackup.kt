package com.dizzyvy.rift.music.backup

import com.dizzyvy.rift.music.library.BackupPlaylist
import com.dizzyvy.rift.music.library.BackupHistoryEntry
import com.dizzyvy.rift.music.library.BackupDuplicatePair
import com.dizzyvy.rift.music.library.BackupTrackRef
import com.dizzyvy.rift.music.library.LibraryBackupSnapshot
import com.dizzyvy.rift.music.library.normalizeArtistName
import org.json.JSONArray
import org.json.JSONObject

object LibraryBackupCodec {
    private const val FORMAT_VERSION = 3
    private const val MAX_ENTRIES = 100_000

    fun encode(snapshot: LibraryBackupSnapshot): String = JSONObject()
        .put("formatVersion", FORMAT_VERSION)
        .put("playlists", JSONArray().apply {
            snapshot.playlists.forEach { playlist ->
                put(JSONObject().put("name", playlist.name).put("tracks", tracksToJson(playlist.tracks)))
            }
        })
        .put("favorites", tracksToJson(snapshot.favorites))
        .put("playHistory", JSONArray().apply {
            snapshot.playHistory.forEach { entry ->
                put(
                    JSONObject()
                        .put("track", trackToJson(entry.track))
                        .put("playCount", entry.playCount)
                        .put("lastPlayedAtMs", entry.lastPlayedAtMs),
                )
            }
        })
        .put("artistAliases", JSONObject().apply {
            snapshot.artistAliases.forEach { (alias, canonical) -> put(alias, canonical) }
        })
        .put("notDuplicatePairs", JSONArray().apply {
            snapshot.notDuplicatePairs.forEach { pair ->
                put(JSONObject().put("first", trackToJson(pair.first)).put("second", trackToJson(pair.second)))
            }
        })
        .put("settings", JSONObject(snapshot.settings))
        .toString(2)

    fun decode(contents: String): LibraryBackupSnapshot {
        require(contents.length <= MAX_BACKUP_CHARS) { "Backup file is too large." }
        val root = JSONObject(contents)
        val formatVersion = root.optInt("formatVersion", -1)
        require(formatVersion in 1..FORMAT_VERSION) { "This backup version is not supported." }
        val playlistsJson = root.optJSONArray("playlists") ?: JSONArray()
        val favoritesJson = root.optJSONArray("favorites") ?: JSONArray()
        val historyJson = root.optJSONArray("playHistory") ?: JSONArray()
        val aliasesJson = root.optJSONObject("artistAliases") ?: JSONObject()
        val duplicatePairsJson = root.optJSONArray("notDuplicatePairs") ?: JSONArray()
        val playlists = buildList {
            for (index in 0 until playlistsJson.length()) {
                val entry = playlistsJson.getJSONObject(index)
                val name = entry.optString("name", "").trim()
                require(name.isNotEmpty()) { "Backup contains a playlist with no name." }
                require(name.lowercase() !in RESERVED_NAMES) { "Backup uses a reserved automatic playlist name." }
                val tracksJson = entry.optJSONArray("tracks") ?: JSONArray()
                add(BackupPlaylist(name, tracksFromJson(tracksJson)))
            }
        }
        require(playlists.map { it.name.lowercase() }.distinct().size == playlists.size) { "Backup contains duplicate playlist names." }
        val entryCount = playlists.sumOf { it.tracks.size } + favoritesJson.length() +
            historyJson.length() + duplicatePairsJson.length() * 2 + aliasesJson.length()
        require(entryCount <= MAX_ENTRIES) { "Backup contains too many entries." }
        val playHistory = buildList {
            for (index in 0 until historyJson.length()) {
                val entry = historyJson.getJSONObject(index)
                val playCount = entry.getInt("playCount")
                val lastPlayedAtMs = entry.getLong("lastPlayedAtMs")
                require(playCount >= 0 && lastPlayedAtMs >= 0L) { "Backup contains invalid play history." }
                add(BackupHistoryEntry(trackFromJson(entry.getJSONObject("track")), playCount, lastPlayedAtMs))
            }
        }
        val artistAliases = buildMap {
            aliasesJson.keys().forEach { alias ->
                val canonical = aliasesJson.getString(alias).trim()
                require(
                    alias.isNotBlank() &&
                        canonical.isNotBlank() &&
                        normalizeArtistName(alias) != normalizeArtistName(canonical),
                ) { "Backup contains an invalid artist alias." }
                put(alias, canonical)
            }
        }
        require(artistAliases.keys.map(::normalizeArtistName).distinct().size == artistAliases.size) {
            "Backup contains duplicate artist aliases."
        }
        val notDuplicatePairs = buildList {
            for (index in 0 until duplicatePairsJson.length()) {
                val pair = duplicatePairsJson.getJSONObject(index)
                val first = trackFromJson(pair.getJSONObject("first"))
                val second = trackFromJson(pair.getJSONObject("second"))
                require(first.uri != second.uri) { "Backup contains an invalid duplicate exclusion." }
                add(BackupDuplicatePair(first, second))
            }
        }
        val settingsJson = root.optJSONObject("settings") ?: JSONObject()
        val importedSettings = buildMap {
            settingsJson.keys().forEach { key ->
                val value = settingsJson.opt(key)
                val text = when (value) {
                    null, JSONObject.NULL -> ""
                    is String -> value
                    is Number, is Boolean -> value.toString()
                    is JSONArray, is JSONObject -> value.toString()
                    else -> value.toString()
                }
                put(key, text)
            }
        }
        val legacyAppearanceKeys = setOf("accentName", "textColor", "text_color")
        val settings = importedSettings
            .filterKeys { it in SETTING_KEYS || (formatVersion == 1 && it in legacyAppearanceKeys) }
            .filterKeys { it !in legacyAppearanceKeys }
            .toMutableMap()
        if (formatVersion == 1) settings["themeMode"] = settings["themeMode"] ?: "system"
        settings["themeMode"]?.let {
            require(it in setOf("system", "light", "dark")) { "Backup contains an invalid theme." }
        }
        settings["sortOrder"]?.let { require(it in SORT_ORDERS) { "Backup contains an invalid sort order." } }
        settings["sortAscending"]?.let { require(it == "true" || it == "false") { "Backup contains an invalid sort direction." } }
        settings["hideShortTracks"]?.let { require(it == "true" || it == "false") { "Backup contains an invalid short-track setting." } }
        settings["hideLongTracks"]?.let { require(it == "true" || it == "false") { "Backup contains an invalid long-track setting." } }
        settings["hideLongTracksAfterMinutes"]?.let {
            require(it.toIntOrNull()?.let { minutes -> minutes in LONG_TRACK_FILTER_MINUTES } == true) {
                "Backup contains an invalid long-track duration."
            }
        }
        settings["clickWheelSensitivity"]?.let { require(it.toFloatOrNull()?.let { value -> value in 0.5f..2f } == true) { "Backup contains an invalid click-wheel sensitivity." } }
        settings["clickWheelHaptics"]?.let { require(it == "true" || it == "false") { "Backup contains an invalid click-wheel haptic setting." } }
        settings["hiddenFolders"]?.let { folders ->
            val normalized = folders.trim()
            val paths = when {
                normalized.isEmpty() -> JSONArray()
                normalized.trimStart().startsWith("[") -> runCatching { JSONArray(normalized) }.getOrElse { JSONArray().apply { put(normalized) } }
                else -> JSONArray().apply { put(normalized) }
            }
            require(paths.length() <= 10_000) { "Backup contains too many hidden folders." }
            for (index in 0 until paths.length()) require(paths.optString(index).isNotBlank()) { "Backup contains an empty hidden-folder path." }
        }
        return LibraryBackupSnapshot(
            playlists = playlists,
            favorites = tracksFromJson(favoritesJson),
            settings = settings,
            playHistory = playHistory,
            artistAliases = artistAliases,
            notDuplicatePairs = notDuplicatePairs,
        )
    }

    private fun tracksToJson(tracks: List<BackupTrackRef>) = JSONArray().apply {
        tracks.forEach { put(trackToJson(it)) }
    }

    private fun trackToJson(track: BackupTrackRef): JSONObject = JSONObject()
        .put("uri", track.uri)
        .put("title", track.title)
        .put("artist", track.artist)
        .put("album", track.album)
        .put("durationMs", track.durationMs)
        .put("folderName", track.folderName)

    private fun tracksFromJson(array: JSONArray): List<BackupTrackRef> = buildList {
        require(array.length() <= MAX_ENTRIES) { "Backup contains too many tracks." }
        for (index in 0 until array.length()) {
            add(trackFromJson(array.getJSONObject(index)))
        }
    }

    private fun trackFromJson(item: JSONObject): BackupTrackRef {
        val uri = item.getString("uri").trim()
        val duration = item.optLong("durationMs")
        require(uri.isNotEmpty()) { "Backup contains a track without a URI." }
        require(duration >= 0L) { "Backup contains an invalid track duration." }
        return BackupTrackRef(
            uri = uri,
            title = item.optString("title"),
            artist = item.optString("artist"),
            album = item.optString("album"),
            durationMs = duration,
            folderName = item.optString("folderName"),
        )
    }

    private const val MAX_BACKUP_CHARS = 16 * 1024 * 1024
    private val RESERVED_NAMES = setOf("favorites", "recently added", "recently played", "most played", "never played")
    private val SETTING_KEYS = setOf("themeMode", "sortOrder", "sortAscending", "hideShortTracks", "hideLongTracks", "hideLongTracksAfterMinutes", "hiddenFolders", "clickWheelSensitivity", "clickWheelHaptics")
    private val SORT_ORDERS = setOf("Title", "Artist", "Date added", "Duration")
    private val LONG_TRACK_FILTER_MINUTES = setOf(20, 30, 45, 60, 90, 120)
}
