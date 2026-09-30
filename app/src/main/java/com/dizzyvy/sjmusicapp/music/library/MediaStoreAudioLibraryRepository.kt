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
        val collections = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.getExternalVolumeNames(appContext).sorted().map { volumeName ->
                MediaStore.Audio.Media.getContentUri(volumeName)
            }
        } else {
            listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        }

        collections
            .flatMap(::queryCollection)
            .distinctBy { it.uri }
            .sortedBy { it.title.lowercase() }
    }

    private fun queryCollection(collection: android.net.Uri): List<AudioTrack> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
        )

        return try {
            resolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

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
                                artist = cursor.getString(artistColumn).orEmpty(),
                                album = cursor.getString(albumColumn).orEmpty(),
                                durationMs = cursor.getLong(durationColumn),
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
        }
    }

    private companion object {
        const val TAG = "SJMusicLibrary"
    }
}
