package com.dizzyvy.rift.music.library

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import org.json.JSONObject
import com.dizzyvy.rift.music.model.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TrackPlayHistory(val playCount: Int, val lastPlayedAtMs: Long)
data class BackupTrackRef(val uri: String, val title: String, val artist: String, val album: String, val durationMs: Long)
data class BackupPlaylist(val name: String, val tracks: List<BackupTrackRef>)
data class LibraryBackupSnapshot(val playlists: List<BackupPlaylist>, val favorites: List<BackupTrackRef>, val settings: Map<String, String>)

interface PlaylistStore {
    suspend fun loadPlaylists(): List<DevicePlaylist>
    suspend fun createPlaylist(name: String): DevicePlaylist
    suspend fun renamePlaylist(id: Long, name: String)
    suspend fun deletePlaylist(id: Long)
    suspend fun loadTrackUris(playlistId: Long): List<Uri>
    suspend fun addTrack(playlistId: Long, uri: Uri)
    suspend fun removeTrack(playlistId: Long, uri: Uri)
    suspend fun reorderTrack(playlistId: Long, fromIndex: Int, toIndex: Int)
    suspend fun loadFavoriteUris(): List<Uri>
    suspend fun setFavorite(uri: Uri, favorite: Boolean)
    suspend fun recordPlay(uri: Uri, playedAtMs: Long)
    suspend fun loadPlayHistory(): Map<String, TrackPlayHistory>
    suspend fun loadHiddenFolderPaths(): Set<String>
    suspend fun setFolderHidden(path: String, hidden: Boolean)
    suspend fun loadArtistAliases(): Map<String, String>
    suspend fun setArtistAlias(alias: String, canonicalName: String)
    suspend fun removeArtistAlias(alias: String)
    suspend fun loadCachedTracks(): List<AudioTrack>
    suspend fun beginTrackCacheRefresh(): String
    suspend fun cacheTracks(generation: String, tracks: List<AudioTrack>)
    suspend fun finishTrackCacheRefresh(generation: String)
    suspend fun loadSettings(): Map<String, String>
    suspend fun saveSettings(settings: Map<String, String>)
    suspend fun createBackupSnapshot(tracks: List<AudioTrack>): LibraryBackupSnapshot
    suspend fun restoreBackupSnapshot(snapshot: LibraryBackupSnapshot, resolvedPlaylists: List<Pair<String, List<Uri>>>, resolvedFavorites: List<Uri>)
}

class SqlitePlaylistStore(context: Context) : SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION), PlaylistStore {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE playlists (_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL COLLATE NOCASE UNIQUE)")
        db.execSQL("CREATE TABLE playlist_tracks (playlist_id INTEGER NOT NULL REFERENCES playlists(_id) ON DELETE CASCADE, uri TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY (playlist_id, uri))")
        db.execSQL("CREATE INDEX playlist_tracks_order ON playlist_tracks(playlist_id, position)")
        db.execSQL("CREATE TABLE favorites (uri TEXT PRIMARY KEY NOT NULL)")
        db.execSQL("CREATE TABLE track_history (uri TEXT PRIMARY KEY NOT NULL, play_count INTEGER NOT NULL, last_played_ms INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE hidden_folders (path TEXT PRIMARY KEY NOT NULL)")
        db.execSQL("CREATE TABLE track_cache (uri TEXT PRIMARY KEY NOT NULL, generation TEXT NOT NULL, payload TEXT NOT NULL)")
        db.execSQL("CREATE INDEX track_cache_generation ON track_cache(generation)")
        db.execSQL("CREATE TABLE app_settings (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
        db.execSQL("CREATE TABLE artist_aliases (alias_key TEXT PRIMARY KEY NOT NULL, alias_name TEXT NOT NULL, canonical_name TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("CREATE TABLE favorites (uri TEXT PRIMARY KEY NOT NULL)")
        if (oldVersion < 3) db.execSQL("CREATE TABLE track_history (uri TEXT PRIMARY KEY NOT NULL, play_count INTEGER NOT NULL, last_played_ms INTEGER NOT NULL)")
        if (oldVersion < 4) db.execSQL("CREATE TABLE hidden_folders (path TEXT PRIMARY KEY NOT NULL)")
        if (oldVersion < 5) {
            db.execSQL("CREATE TABLE track_cache (uri TEXT PRIMARY KEY NOT NULL, generation TEXT NOT NULL, payload TEXT NOT NULL)")
            db.execSQL("CREATE INDEX track_cache_generation ON track_cache(generation)")
        }
        if (oldVersion < 6) db.execSQL("CREATE TABLE app_settings (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
        if (oldVersion < 7) db.execSQL("CREATE TABLE artist_aliases (alias_key TEXT PRIMARY KEY NOT NULL, alias_name TEXT NOT NULL, canonical_name TEXT NOT NULL)")
    }

    override suspend fun loadCachedTracks(): List<AudioTrack> = withContext(Dispatchers.IO) {
        readableDatabase.query("track_cache", arrayOf("payload"), null, null, null, null, "payload COLLATE NOCASE").use { cursor ->
            buildList { while (cursor.moveToNext()) runCatching { add(trackFromJson(JSONObject(cursor.getString(0)))) } }
        }
    }

    override suspend fun beginTrackCacheRefresh(): String = java.util.UUID.randomUUID().toString()

    override suspend fun cacheTracks(generation: String, tracks: List<AudioTrack>) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        tracks.chunked(CACHE_BATCH_SIZE).forEach { batch ->
            db.beginTransaction()
            try {
                batch.forEach { track ->
                    db.insertWithOnConflict("track_cache", null, ContentValues().apply {
                        put("uri", track.uri.toString())
                        put("generation", generation)
                        put("payload", track.toCacheJson().toString())
                    }, SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
    }

    override suspend fun finishTrackCacheRefresh(generation: String) = withContext(Dispatchers.IO) {
        writableDatabase.delete("track_cache", "generation != ?", arrayOf(generation))
        Unit
    }

    override suspend fun loadSettings(): Map<String, String> = withContext(Dispatchers.IO) {
        readableDatabase.query("app_settings", arrayOf("key", "value"), null, null, null, null, null).use { cursor ->
            buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1)) }
        }
    }

    override suspend fun saveSettings(settings: Map<String, String>) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            settings.forEach { (key, value) -> db.insertWithOnConflict("app_settings", null, ContentValues().apply { put("key", key); put("value", value) }, SQLiteDatabase.CONFLICT_REPLACE) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    override suspend fun createBackupSnapshot(tracks: List<AudioTrack>): LibraryBackupSnapshot = withContext(Dispatchers.IO) {
        val byUri = tracks.associateBy { it.uri.toString() }
        val playlists = readableDatabase.query("playlists", arrayOf("_id", "name"), null, null, null, null, "name COLLATE NOCASE ASC").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val name = cursor.getString(1)
                    val uris = readableDatabase.query("playlist_tracks", arrayOf("uri"), "playlist_id = ?", arrayOf(id.toString()), null, null, "position ASC").use { entries ->
                        buildList { while (entries.moveToNext()) add(entries.getString(0)) }
                    }
                    add(BackupPlaylist(name, uris.map { uri -> byUri[uri]?.let(::backupRef) ?: BackupTrackRef(uri, "", "", "", 0L) }))
                }
            }
        }
        val favorites = loadFavoriteUris().map { uri -> byUri[uri.toString()]?.let(::backupRef) ?: BackupTrackRef(uri.toString(), "", "", "", 0L) }
        val settings = loadSettings().filterKeys { it != SETTING_LYRICS_TREE }.toMutableMap().apply {
            put(SETTING_HIDDEN_FOLDERS, org.json.JSONArray(loadHiddenFolderPaths().sorted()).toString())
        }
        LibraryBackupSnapshot(playlists, favorites, settings)
    }

    override suspend fun restoreBackupSnapshot(snapshot: LibraryBackupSnapshot, resolvedPlaylists: List<Pair<String, List<Uri>>>, resolvedFavorites: List<Uri>) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val localLyricsTree = db.query("app_settings", arrayOf("value"), "key = ?", arrayOf(SETTING_LYRICS_TREE), null, null, null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            db.delete("playlist_tracks", null, null)
            db.delete("playlists", null, null)
            db.delete("favorites", null, null)
            db.delete("app_settings", null, null)
            db.delete("hidden_folders", null, null)
            resolvedPlaylists.forEach { (name, tracks) ->
                val id = db.insertOrThrow("playlists", null, ContentValues().apply { put("name", name) })
                tracks.forEachIndexed { index, uri -> db.insertWithOnConflict("playlist_tracks", null, ContentValues().apply { put("playlist_id", id); put("uri", uri.toString()); put("position", index) }, SQLiteDatabase.CONFLICT_IGNORE) }
            }
            resolvedFavorites.forEach { uri -> db.insertWithOnConflict("favorites", null, ContentValues().apply { put("uri", uri.toString()) }, SQLiteDatabase.CONFLICT_IGNORE) }
            snapshot.settings.forEach { (key, value) -> db.insertWithOnConflict("app_settings", null, ContentValues().apply { put("key", key); put("value", value) }, SQLiteDatabase.CONFLICT_REPLACE) }
            if (localLyricsTree != null) db.insertWithOnConflict("app_settings", null, ContentValues().apply { put("key", SETTING_LYRICS_TREE); put("value", localLyricsTree) }, SQLiteDatabase.CONFLICT_REPLACE)
            val hiddenFolders = org.json.JSONArray(snapshot.settings[SETTING_HIDDEN_FOLDERS] ?: "[]")
            for (index in 0 until hiddenFolders.length()) {
                val path = hiddenFolders.optString(index).trim().trimEnd('/')
                if (path.isNotEmpty()) db.insertWithOnConflict("hidden_folders", null, ContentValues().apply { put("path", path) }, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    override suspend fun loadPlaylists(): List<DevicePlaylist> = withContext(Dispatchers.IO) {
        val saved = readableDatabase.query("playlists", arrayOf("_id", "name"), null, null, null, null, "name COLLATE NOCASE ASC").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(DevicePlaylist(cursor.getLong(0), LOCAL_VOLUME, cursor.getString(1), isLocal = true))
            }
        }
        listOf(
            DevicePlaylist(FAVORITES_PLAYLIST_ID, LOCAL_VOLUME, "Favorites", isLocal = true, isAuto = true, autoKind = "favorites"),
            DevicePlaylist(RECENTLY_ADDED_ID, LOCAL_VOLUME, "Recently Added", isLocal = true, isAuto = true, autoKind = "recently_added"),
            DevicePlaylist(RECENTLY_PLAYED_ID, LOCAL_VOLUME, "Recently Played", isLocal = true, isAuto = true, autoKind = "recently_played"),
            DevicePlaylist(MOST_PLAYED_ID, LOCAL_VOLUME, "Most Played", isLocal = true, isAuto = true, autoKind = "most_played"),
            DevicePlaylist(NEVER_PLAYED_ID, LOCAL_VOLUME, "Never Played", isLocal = true, isAuto = true, autoKind = "never_played"),
        ) + saved
    }

    override suspend fun createPlaylist(name: String): DevicePlaylist = withContext(Dispatchers.IO) {
        val normalized = name.trim()
        require(normalized.isNotEmpty()) { "Playlist name cannot be empty." }
        require(normalized.lowercase() !in AUTO_PLAYLIST_NAMES) { "That name is reserved for an automatic playlist." }
        val id = writableDatabase.insertOrThrow("playlists", null, ContentValues().apply { put("name", normalized) })
        DevicePlaylist(id, LOCAL_VOLUME, normalized, isLocal = true)
    }

    override suspend fun renamePlaylist(id: Long, name: String) = withContext(Dispatchers.IO) {
        require(id < 0L) { "Automatic playlists cannot be renamed." }
        val normalized = name.trim()
        require(normalized.lowercase() !in AUTO_PLAYLIST_NAMES) { "That name is reserved for an automatic playlist." }
        require(normalized.isNotEmpty()) { "Playlist name cannot be empty." }
        val values = ContentValues().apply { put("name", normalized) }
        check(writableDatabase.update("playlists", values, "_id = ?", arrayOf(id.toString())) == 1) { "Playlist no longer exists." }
    }

    override suspend fun deletePlaylist(id: Long): Unit = withContext(Dispatchers.IO) {
        require(id >= 0L) { "Automatic playlists cannot be deleted." }
        writableDatabase.delete("playlists", "_id = ?", arrayOf(id.toString()))
        writableDatabase.delete("playlist_tracks", "playlist_id = ?", arrayOf(id.toString()))
    }

    override suspend fun loadTrackUris(playlistId: Long): List<Uri> = withContext(Dispatchers.IO) {
        if (playlistId == FAVORITES_PLAYLIST_ID) return@withContext loadFavoriteUris()
        if (playlistId < 0L) return@withContext emptyList()
        readableDatabase.query("playlist_tracks", arrayOf("uri"), "playlist_id = ?", arrayOf(playlistId.toString()), null, null, "position ASC").use { cursor ->
            buildList { while (cursor.moveToNext()) add(Uri.parse(cursor.getString(0))) }
        }
    }

    override suspend fun addTrack(playlistId: Long, uri: Uri) = withContext(Dispatchers.IO) {
        if (playlistId < 0L) {
            require(playlistId == FAVORITES_PLAYLIST_ID) { "Only Favorites can be edited." }
            writableDatabase.insertWithOnConflict("favorites", null, ContentValues().apply { put("uri", uri.toString()) }, SQLiteDatabase.CONFLICT_IGNORE)
            return@withContext Unit
        }
        val db = writableDatabase
        checkPlaylistExists(db, playlistId)
        val position = db.compileStatement("SELECT COALESCE(MAX(position) + 1, 0) FROM playlist_tracks WHERE playlist_id = ?").run {
            bindLong(1, playlistId)
            simpleQueryForLong().toInt()
        }
        db.insertWithOnConflict("playlist_tracks", null, ContentValues().apply {
            put("playlist_id", playlistId)
            put("uri", uri.toString())
            put("position", position)
        }, SQLiteDatabase.CONFLICT_IGNORE)
        Unit
    }

    override suspend fun removeTrack(playlistId: Long, uri: Uri) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        if (playlistId < 0L) {
            require(playlistId == FAVORITES_PLAYLIST_ID) { "Only Favorites can be edited." }
            db.delete("favorites", "uri = ?", arrayOf(uri.toString()))
            return@withContext Unit
        }
        db.delete("playlist_tracks", "playlist_id = ? AND uri = ?", arrayOf(playlistId.toString(), uri.toString()))
        normalizePositions(db, playlistId)
    }

    override suspend fun reorderTrack(playlistId: Long, fromIndex: Int, toIndex: Int) = withContext(Dispatchers.IO) {
        if (playlistId == FAVORITES_PLAYLIST_ID) return@withContext
        val db = writableDatabase
        val uris = readableDatabase.query("playlist_tracks", arrayOf("uri"), "playlist_id = ?", arrayOf(playlistId.toString()), null, null, "position ASC").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }.toMutableList()
        if (fromIndex !in uris.indices || toIndex !in uris.indices || fromIndex == toIndex) return@withContext
        val moved = uris.removeAt(fromIndex)
        uris.add(toIndex, moved)
        db.beginTransaction()
        try {
            uris.forEachIndexed { index, uri ->
                db.execSQL("UPDATE playlist_tracks SET position = ? WHERE playlist_id = ? AND uri = ?", arrayOf(index, playlistId, uri))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override suspend fun loadFavoriteUris(): List<Uri> = withContext(Dispatchers.IO) {
        readableDatabase.query("favorites", arrayOf("uri"), null, null, null, null, "uri ASC").use { cursor ->
            buildList { while (cursor.moveToNext()) add(Uri.parse(cursor.getString(0))) }
        }
    }

    override suspend fun setFavorite(uri: Uri, favorite: Boolean) = withContext(Dispatchers.IO) {
        if (favorite) {
            writableDatabase.insertWithOnConflict("favorites", null, ContentValues().apply { put("uri", uri.toString()) }, SQLiteDatabase.CONFLICT_IGNORE)
        } else {
            writableDatabase.delete("favorites", "uri = ?", arrayOf(uri.toString()))
        }
        Unit
    }

    override suspend fun loadHiddenFolderPaths(): Set<String> = withContext(Dispatchers.IO) {
        readableDatabase.query("hidden_folders", arrayOf("path"), null, null, null, null, "path COLLATE NOCASE ASC").use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
    }

    override suspend fun setFolderHidden(path: String, hidden: Boolean) = withContext(Dispatchers.IO) {
        val normalized = path.trim().trimEnd('/')
        require(normalized.isNotEmpty()) { "Folder path cannot be empty." }
        if (hidden) {
            writableDatabase.insertWithOnConflict(
                "hidden_folders",
                null,
                ContentValues().apply { put("path", normalized) },
                SQLiteDatabase.CONFLICT_IGNORE,
            )
        } else {
            writableDatabase.delete("hidden_folders", "path = ?", arrayOf(normalized))
        }
        Unit
    }

    override suspend fun loadArtistAliases(): Map<String, String> = withContext(Dispatchers.IO) {
        readableDatabase.query("artist_aliases", arrayOf("alias_key", "canonical_name"), null, null, null, null, null).use { cursor ->
            buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1)) }
        }
    }

    override suspend fun setArtistAlias(alias: String, canonicalName: String) = withContext(Dispatchers.IO) {
        val cleanedAlias = alias.trim().replace(Regex("\\s+"), " ")
        val cleanedCanonical = canonicalName.trim().replace(Regex("\\s+"), " ")
        require(cleanedAlias.isNotBlank() && cleanedCanonical.isNotBlank()) { "Enter both artist names." }
        val aliasKey = normalizeArtistName(cleanedAlias)
        require(aliasKey != normalizeArtistName(cleanedCanonical)) { "An artist cannot be an alias of itself." }
        val values = ContentValues().apply {
            put("alias_key", aliasKey)
            put("alias_name", cleanedAlias)
            put("canonical_name", cleanedCanonical)
        }
        writableDatabase.insertWithOnConflict("artist_aliases", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        Unit
    }

    override suspend fun removeArtistAlias(alias: String) = withContext(Dispatchers.IO) {
        writableDatabase.delete("artist_aliases", "alias_key = ?", arrayOf(normalizeArtistName(alias)))
        Unit
    }

    override suspend fun recordPlay(uri: Uri, playedAtMs: Long) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val uriText = uri.toString()
            val currentCount = db.rawQuery("SELECT play_count FROM track_history WHERE uri = ?", arrayOf(uriText)).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else null
            }
            val values = ContentValues().apply {
                put("uri", uriText)
                put("play_count", (currentCount ?: 0) + 1)
                put("last_played_ms", playedAtMs)
            }
            db.insertWithOnConflict("track_history", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        Unit
    }

    override suspend fun loadPlayHistory(): Map<String, TrackPlayHistory> = withContext(Dispatchers.IO) {
        readableDatabase.query("track_history", arrayOf("uri", "play_count", "last_played_ms"), null, null, null, null, null).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) put(cursor.getString(0), TrackPlayHistory(cursor.getInt(1), cursor.getLong(2)))
            }
        }
    }

    private fun checkPlaylistExists(db: SQLiteDatabase, id: Long) {
        db.rawQuery("SELECT 1 FROM playlists WHERE _id = ?", arrayOf(id.toString())).use { cursor ->
            check(cursor.moveToFirst()) { "Playlist no longer exists." }
        }
    }

    private fun normalizePositions(db: SQLiteDatabase, playlistId: Long) {
        val uris = db.query("playlist_tracks", arrayOf("uri"), "playlist_id = ?", arrayOf(playlistId.toString()), null, null, "position ASC").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        uris.forEachIndexed { index, uri ->
            db.execSQL("UPDATE playlist_tracks SET position = ? WHERE playlist_id = ? AND uri = ?", arrayOf(index, playlistId, uri))
        }
    }

    private companion object {
        const val DATABASE_NAME = "sj_music_library.db"
        const val DATABASE_VERSION = 7
        const val CACHE_BATCH_SIZE = 200
        const val SETTING_HIDDEN_FOLDERS = "hiddenFolders"
        const val SETTING_LYRICS_TREE = "lyricsTreeUri"
        const val LOCAL_VOLUME = "app"
        const val FAVORITES_PLAYLIST_ID = -1L
        const val RECENTLY_ADDED_ID = -2L
        const val RECENTLY_PLAYED_ID = -3L
        const val MOST_PLAYED_ID = -4L
        const val NEVER_PLAYED_ID = -5L
        val AUTO_PLAYLIST_NAMES = setOf("favorites", "recently added", "recently played", "most played", "never played")
    }
}

private fun backupRef(track: AudioTrack) = BackupTrackRef(track.uri.toString(), track.title, track.artist, track.album, track.durationMs)

private fun AudioTrack.toCacheJson() = JSONObject()
    .put("id", id).put("uri", uri.toString()).put("title", title).put("displayName", displayName)
    .put("artist", artist).put("albumArtist", albumArtist).put("album", album).put("durationMs", durationMs)
    .put("dateAddedSeconds", dateAddedSeconds).put("sizeBytes", sizeBytes).put("mimeType", mimeType)
    .put("bitrate", bitrate).put("sampleRateHz", sampleRateHz).put("filePath", filePath).put("relativePath", relativePath)
    .put("genre", genre).put("year", year).put("artistId", artistId).put("albumId", albumId).put("volumeName", volumeName)

private fun trackFromJson(json: JSONObject) = AudioTrack(
    id = json.optLong("id", -1L), uri = Uri.parse(json.getString("uri")), title = json.optString("title"),
    displayName = json.optString("displayName"), artist = json.optString("artist"), albumArtist = json.optString("albumArtist"),
    album = json.optString("album"), durationMs = json.optLong("durationMs"), dateAddedSeconds = json.optLong("dateAddedSeconds"),
    sizeBytes = json.optLong("sizeBytes"), mimeType = json.optString("mimeType"), bitrate = json.optInt("bitrate", -1),
    sampleRateHz = json.optInt("sampleRateHz", -1), filePath = json.optString("filePath"), relativePath = json.optString("relativePath"),
    genre = json.optString("genre"), year = json.optInt("year"), artistId = json.optLong("artistId", -1L),
    albumId = json.optLong("albumId", -1L), volumeName = json.optString("volumeName", "external"),
)
