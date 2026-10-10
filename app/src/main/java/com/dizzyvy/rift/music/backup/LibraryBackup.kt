package com.dizzyvy.rift.music.backup

import com.dizzyvy.rift.music.library.BackupPlaylist
import com.dizzyvy.rift.music.library.BackupTrackRef
import com.dizzyvy.rift.music.library.LibraryBackupSnapshot
import org.json.JSONArray
import org.json.JSONObject

object LibraryBackupCodec {
    private const val FORMAT_VERSION = 1
    private const val MAX_ENTRIES = 100_000

    fun encode(snapshot: LibraryBackupSnapshot): String = JSONObject()
        .put("formatVersion", FORMAT_VERSION)
        .put("playlists", JSONArray().apply {
            snapshot.playlists.forEach { playlist ->
                put(JSONObject().put("name", playlist.name).put("tracks", tracksToJson(playlist.tracks)))
            }
        })
        .put("favorites", tracksToJson(snapshot.favorites))
        .put("settings", JSONObject(snapshot.settings))
        .toString(2)

    fun decode(contents: String): LibraryBackupSnapshot {
        require(contents.length <= MAX_BACKUP_CHARS) { "Backup file is too large." }
        val root = JSONObject(contents)
        require(root.optInt("formatVersion", -1) == FORMAT_VERSION) { "This backup version is not supported." }
        val playlistsJson = root.getJSONArray("playlists")
        val favoritesJson = root.getJSONArray("favorites")
        require(playlistsJson.length() + favoritesJson.length() <= MAX_ENTRIES) { "Backup contains too many entries." }
        val playlists = buildList {
            for (index in 0 until playlistsJson.length()) {
                val entry = playlistsJson.getJSONObject(index)
                val name = entry.getString("name").trim()
                require(name.isNotEmpty()) { "Backup contains a playlist with no name." }
                require(name.lowercase() !in RESERVED_NAMES) { "Backup uses a reserved automatic playlist name." }
                add(BackupPlaylist(name, tracksFromJson(entry.getJSONArray("tracks"))))
            }
        }
        require(playlists.map { it.name.lowercase() }.distinct().size == playlists.size) { "Backup contains duplicate playlist names." }
        val settingsJson = root.optJSONObject("settings") ?: JSONObject()
        val settings = buildMap { settingsJson.keys().forEach { key -> put(key, settingsJson.getString(key)) } }
        require(settings.keys.all { it in SETTING_KEYS }) { "Backup contains unknown settings." }
        settings["themeMode"]?.let { require(it in setOf("system", "light", "dark", "amoled", "nano")) { "Backup contains an invalid theme." } }
        settings["accentName"]?.let { require(it in ACCENTS) { "Backup contains an invalid accent." } }
        settings["sortOrder"]?.let { require(it in SORT_ORDERS) { "Backup contains an invalid sort order." } }
        settings["sortAscending"]?.let { require(it == "true" || it == "false") { "Backup contains an invalid sort direction." } }
        settings["hideShortTracks"]?.let { require(it == "true" || it == "false") { "Backup contains an invalid short-track setting." } }
        settings["clickWheelSensitivity"]?.let { require(it.toFloatOrNull()?.let { value -> value in 0.5f..2f } == true) { "Backup contains an invalid click-wheel sensitivity." } }
        settings["clickWheelHaptics"]?.let { require(it == "true" || it == "false") { "Backup contains an invalid click-wheel haptic setting." } }
        settings["hiddenFolders"]?.let { folders ->
            val paths = JSONArray(folders)
            require(paths.length() <= 10_000) { "Backup contains too many hidden folders." }
            for (index in 0 until paths.length()) require(paths.optString(index).isNotBlank()) { "Backup contains an empty hidden-folder path." }
        }
        return LibraryBackupSnapshot(playlists, tracksFromJson(favoritesJson), settings)
    }

    private fun tracksToJson(tracks: List<BackupTrackRef>) = JSONArray().apply {
        tracks.forEach { track ->
            put(JSONObject().put("uri", track.uri).put("title", track.title).put("artist", track.artist)
                .put("album", track.album).put("durationMs", track.durationMs))
        }
    }

    private fun tracksFromJson(array: JSONArray): List<BackupTrackRef> = buildList {
        require(array.length() <= MAX_ENTRIES) { "Backup contains too many tracks." }
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val uri = item.getString("uri").trim()
            require(uri.isNotEmpty()) { "Backup contains a track without a URI." }
            add(BackupTrackRef(uri, item.optString("title"), item.optString("artist"), item.optString("album"), item.optLong("durationMs")))
        }
    }

    private const val MAX_BACKUP_CHARS = 16 * 1024 * 1024
    private val RESERVED_NAMES = setOf("favorites", "recently added", "recently played", "most played", "never played")
    private val SETTING_KEYS = setOf("themeMode", "accentName", "sortOrder", "sortAscending", "hideShortTracks", "hiddenFolders", "clickWheelSensitivity", "clickWheelHaptics")
    private val SORT_ORDERS = setOf("Title", "Artist", "Date added", "Duration")
    private val ACCENTS = setOf("Chromatic", "Cyan", "Coral", "Red", "Orange", "Yellow", "Green", "Blue", "Purple", "Pink", "Silver", "Graphite")
}
