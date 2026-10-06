package com.dizzyvy.sjmusicapp.music.library

import android.content.ContentUris
import android.content.Context
import android.database.sqlite.SQLiteException
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.dizzyvy.sjmusicapp.music.model.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreAudioLibraryRepository(context: Context) : AudioLibraryRepository {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    override suspend fun loadTracks(): List<AudioTrack> = withContext(Dispatchers.IO) {
        queryAllTracks()
    }

    override suspend fun loadArtists(): List<ArtistBrowseItem> = withContext(Dispatchers.IO) {
        queryAllTracks().groupBy(::artistGroupKey)
            .map { (id, tracks) -> ArtistBrowseItem(id, tracks.first().artist.ifBlank { "Unknown artist" }, tracks.size) }
            .sortedWith(browseComparator { it.name })
    }

    override suspend fun loadAlbums(): List<AlbumBrowseItem> = withContext(Dispatchers.IO) {
        queryAllTracks().groupBy(::albumGroupKey)
            .map { (id, tracks) -> AlbumBrowseItem(id, tracks.first().album.ifBlank { "Unknown album" }, tracks.first().artist, tracks.size) }
            .sortedWith(browseComparator { it.title })
    }

    override suspend fun loadPlaylists(): List<DevicePlaylist> = withContext(Dispatchers.IO) {
        val volumes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.getExternalVolumeNames(appContext).toList() else listOf("external")
        volumes.flatMap { volume ->
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Playlists.getContentUri(volume) else MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI
            runCatching {
                resolver.query(uri, arrayOf(MediaStore.Audio.Playlists._ID, MediaStore.Audio.Playlists.NAME), null, null, "${MediaStore.Audio.Playlists.NAME} COLLATE NOCASE ASC")?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.NAME)
                    buildList { while (cursor.moveToNext()) add(DevicePlaylist(cursor.getLong(idCol), volume, cursor.getString(nameCol).orEmpty())) }
                }.orEmpty()
            }.getOrElse { Log.w(TAG, "Could not read playlists on $volume", it); emptyList() }
        }
    }

    override suspend fun loadPlaylistTracks(playlist: DevicePlaylist): List<AudioTrack> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = MediaStore.Audio.Playlists.Members.getContentUri(playlist.volumeName, playlist.id)
            resolver.query(uri, arrayOf(MediaStore.Audio.Playlists.Members.AUDIO_ID), null, null, "${MediaStore.Audio.Playlists.Members.PLAY_ORDER} ASC")?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID)
                val ids = buildList { while (cursor.moveToNext()) add(cursor.getLong(idColumn)) }
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.getContentUri(playlist.volumeName) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val tracksById = queryCollection(collection).associateBy { it.id }
                ids.mapNotNull(tracksById::get)
            }.orEmpty()
        }.getOrElse { Log.w(TAG, "Could not read playlist ${playlist.name}", it); emptyList() }
    }

    private fun queryAllTracks(): List<AudioTrack> {
        val collections = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.getExternalVolumeNames(appContext).sorted().map { volumeName ->
                MediaStore.Audio.Media.getContentUri(volumeName)
            }
        } else {
            listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        }

        return collections
            .flatMap(::queryCollection)
            .distinctBy { it.uri }
            .sortedWith(browseComparator { it.title })
    }

    private fun queryCollection(collection: android.net.Uri): List<AudioTrack> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM_ID,
        ) + if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) arrayOf(MediaStore.Audio.Media.ALBUM_ARTIST, MediaStore.Audio.Media.BITRATE, MediaStore.Audio.Media.SAMPLERATE) else emptyArray()

        return try {
            resolver.query(
                collection,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumArtistColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST) else -1
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dateAddedColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val sizeColumn = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val mimeTypeColumn = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val dataColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val bitrateColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) cursor.getColumnIndex(MediaStore.Audio.Media.BITRATE) else -1
                val sampleRateColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) cursor.getColumnIndex(MediaStore.Audio.Media.SAMPLERATE) else -1
                val artistIdColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST_ID)
                val albumIdColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)

                buildList {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val displayName = cursor.getString(displayNameColumn).orEmpty()
                        val title = cursor.getString(titleColumn)
                            ?.takeIf(String::isNotBlank)
                            ?: displayName.substringBeforeLast('.', displayName)
                                .ifBlank { "Untitled track" }

                        add(
                            AudioTrack(
                                id = id,
                                uri = ContentUris.withAppendedId(collection, id),
                                title = title,
                                displayName = displayName,
                                artist = cursor.getString(artistColumn).orEmpty(),
                                albumArtist = if (albumArtistColumn >= 0) cursor.getString(albumArtistColumn).orEmpty() else "",
                                album = cursor.getString(albumColumn).orEmpty(),
                                durationMs = cursor.getLong(durationColumn),
                                dateAddedSeconds = if (dateAddedColumn >= 0) cursor.getLong(dateAddedColumn) else 0L,
                                sizeBytes = if (sizeColumn >= 0) cursor.getLong(sizeColumn) else 0L,
                                mimeType = if (mimeTypeColumn >= 0) cursor.getString(mimeTypeColumn).orEmpty() else "",
                                bitrate = if (bitrateColumn >= 0 && !cursor.isNull(bitrateColumn)) cursor.getInt(bitrateColumn) else -1,
                                sampleRateHz = if (sampleRateColumn >= 0 && !cursor.isNull(sampleRateColumn)) cursor.getInt(sampleRateColumn) else -1,
                                filePath = if (dataColumn >= 0) cursor.getString(dataColumn).orEmpty() else "",
                                artistId = if (artistIdColumn >= 0) cursor.getLong(artistIdColumn) else -1L,
                                albumId = if (albumIdColumn >= 0) cursor.getLong(albumIdColumn) else -1L,
                                volumeName = collection.pathSegments.firstOrNull() ?: "external",
                            ),
                        )
                    }
                }
            }.orEmpty()
        } catch (exception: IllegalArgumentException) {
            Log.w(TAG, "A media volume became unavailable during the library scan", exception)
            emptyList()
        } catch (exception: SQLiteException) {
            Log.w(TAG, "A media volume could not be queried during the library scan", exception)
            emptyList()
        } catch (exception: SecurityException) {
            Log.w(TAG, "A media volume was not accessible during the library scan", exception)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "SJMusicLibrary"
    }

    private fun <T> browseComparator(title: (T) -> String): Comparator<T> = compareBy<T> { if (librarySection(title(it)) == '#') 0 else 1 }
        .thenBy { librarySection(title(it)) }
        .thenBy(String.CASE_INSENSITIVE_ORDER, title)
}
