package com.dizzyvy.sjmusicapp.music.library

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface PlaylistStore {
    suspend fun loadPlaylists(): List<DevicePlaylist>
    suspend fun createPlaylist(name: String): DevicePlaylist
    suspend fun renamePlaylist(id: Long, name: String)
    suspend fun deletePlaylist(id: Long)
    suspend fun loadTrackUris(playlistId: Long): List<Uri>
    suspend fun addTrack(playlistId: Long, uri: Uri)
    suspend fun removeTrack(playlistId: Long, uri: Uri)
    suspend fun reorderTrack(playlistId: Long, fromIndex: Int, toIndex: Int)
}

class SqlitePlaylistStore(context: Context) : SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION), PlaylistStore {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE playlists (_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL COLLATE NOCASE UNIQUE)")
        db.execSQL("CREATE TABLE playlist_tracks (playlist_id INTEGER NOT NULL REFERENCES playlists(_id) ON DELETE CASCADE, uri TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY (playlist_id, uri))")
        db.execSQL("CREATE INDEX playlist_tracks_order ON playlist_tracks(playlist_id, position)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    override suspend fun loadPlaylists(): List<DevicePlaylist> = withContext(Dispatchers.IO) {
        readableDatabase.query("playlists", arrayOf("_id", "name"), null, null, null, null, "name COLLATE NOCASE ASC").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(DevicePlaylist(cursor.getLong(0), LOCAL_VOLUME, cursor.getString(1), isLocal = true))
            }
        }
    }

    override suspend fun createPlaylist(name: String): DevicePlaylist = withContext(Dispatchers.IO) {
        val normalized = name.trim()
        require(normalized.isNotEmpty()) { "Playlist name cannot be empty." }
        val id = writableDatabase.insertOrThrow("playlists", null, ContentValues().apply { put("name", normalized) })
        DevicePlaylist(id, LOCAL_VOLUME, normalized, isLocal = true)
    }

    override suspend fun renamePlaylist(id: Long, name: String) = withContext(Dispatchers.IO) {
        val normalized = name.trim()
        require(normalized.isNotEmpty()) { "Playlist name cannot be empty." }
        val values = ContentValues().apply { put("name", normalized) }
        check(writableDatabase.update("playlists", values, "_id = ?", arrayOf(id.toString())) == 1) { "Playlist no longer exists." }
    }

    override suspend fun deletePlaylist(id: Long) = withContext(Dispatchers.IO) {
        writableDatabase.delete("playlists", "_id = ?", arrayOf(id.toString()))
        writableDatabase.delete("playlist_tracks", "playlist_id = ?", arrayOf(id.toString()))
    }

    override suspend fun loadTrackUris(playlistId: Long): List<Uri> = withContext(Dispatchers.IO) {
        readableDatabase.query("playlist_tracks", arrayOf("uri"), "playlist_id = ?", arrayOf(playlistId.toString()), null, null, "position ASC").use { cursor ->
            buildList { while (cursor.moveToNext()) add(Uri.parse(cursor.getString(0))) }
        }
    }

    override suspend fun addTrack(playlistId: Long, uri: Uri) = withContext(Dispatchers.IO) {
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
    }

    override suspend fun removeTrack(playlistId: Long, uri: Uri) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.delete("playlist_tracks", "playlist_id = ? AND uri = ?", arrayOf(playlistId.toString(), uri.toString()))
        normalizePositions(db, playlistId)
    }

    override suspend fun reorderTrack(playlistId: Long, fromIndex: Int, toIndex: Int) = withContext(Dispatchers.IO) {
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
        const val DATABASE_VERSION = 1
        const val LOCAL_VOLUME = "app"
    }
}
