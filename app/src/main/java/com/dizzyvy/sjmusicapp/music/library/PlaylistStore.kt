package com.dizzyvy.sjmusicapp.music.library

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TrackPlayHistory(val playCount: Int, val lastPlayedAtMs: Long)

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
}

class SqlitePlaylistStore(context: Context) : SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION), PlaylistStore {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE playlists (_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL COLLATE NOCASE UNIQUE)")
        db.execSQL("CREATE TABLE playlist_tracks (playlist_id INTEGER NOT NULL REFERENCES playlists(_id) ON DELETE CASCADE, uri TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY (playlist_id, uri))")
        db.execSQL("CREATE INDEX playlist_tracks_order ON playlist_tracks(playlist_id, position)")
        db.execSQL("CREATE TABLE favorites (uri TEXT PRIMARY KEY NOT NULL)")
        db.execSQL("CREATE TABLE track_history (uri TEXT PRIMARY KEY NOT NULL, play_count INTEGER NOT NULL, last_played_ms INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("CREATE TABLE favorites (uri TEXT PRIMARY KEY NOT NULL)")
        if (oldVersion < 3) db.execSQL("CREATE TABLE track_history (uri TEXT PRIMARY KEY NOT NULL, play_count INTEGER NOT NULL, last_played_ms INTEGER NOT NULL)")
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
        const val DATABASE_VERSION = 3
        const val LOCAL_VOLUME = "app"
        const val FAVORITES_PLAYLIST_ID = -1L
        const val RECENTLY_ADDED_ID = -2L
        const val RECENTLY_PLAYED_ID = -3L
        const val MOST_PLAYED_ID = -4L
        const val NEVER_PLAYED_ID = -5L
        val AUTO_PLAYLIST_NAMES = setOf("favorites", "recently added", "recently played", "most played", "never played")
    }
}
