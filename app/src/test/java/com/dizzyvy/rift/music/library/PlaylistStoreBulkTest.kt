package com.dizzyvy.rift.music.library

import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlaylistStoreBulkTest {
    @Test
    fun recordingAPlayImmediatelyUpdatesCountAndOrderingTimestamp() = runBlocking {
        val store = SqlitePlaylistStore(RuntimeEnvironment.getApplication())
        try {
            val uri = Uri.parse("content://media/audio/${System.nanoTime()}")
            val firstPlayedAt = 1_000L
            val secondPlayedAt = 2_000L

            store.recordPlay(uri, firstPlayedAt)
            assertEquals(TrackPlayHistory(1, firstPlayedAt), store.loadPlayHistory()[uri.toString()])

            store.recordPlay(uri, secondPlayedAt)
            assertEquals(TrackPlayHistory(2, secondPlayedAt), store.loadPlayHistory()[uri.toString()])
        } finally {
            store.close()
        }
    }

    @Test
    fun bulkCreateAndAddKeepEveryUniqueTrackInInputOrder() = runBlocking {
        val store = SqlitePlaylistStore(RuntimeEnvironment.getApplication())
        try {
            val first = Uri.parse("content://media/audio/${System.nanoTime()}-1")
            val second = Uri.parse("content://media/audio/${System.nanoTime()}-2")
            val third = Uri.parse("content://media/audio/${System.nanoTime()}-3")
            val playlist = store.createPlaylistWithTracks("Bulk ${System.nanoTime()}", listOf(first, second))

            store.addTracks(playlist.id, listOf(third, second))

            assertEquals(listOf(first, second, third), store.loadTrackUris(playlist.id))
        } finally {
            store.close()
        }
    }

    @Test
    fun failedBulkCreateRejectsEmptyTracksAndInvalidNamesWithoutCreatingAPlaylist() = runBlocking {
        val store = SqlitePlaylistStore(RuntimeEnvironment.getApplication())
        try {
            val existingName = "BulkConflict ${System.nanoTime()}"
            val existing = store.createPlaylist(existingName)
            val before = store.loadPlaylists().filterNot { it.isAuto }.map { it.id to it.name }.toSet()
            val track = Uri.parse("content://media/audio/${System.nanoTime()}")
            val emptyTracksRejected = try {
                store.createPlaylistWithTracks("Bulk ${System.nanoTime()}", emptyList())
                false
            } catch (_: IllegalArgumentException) {
                true
            }
            val blankNameRejected = try {
                store.createPlaylistWithTracks(" ", listOf(track))
                false
            } catch (_: IllegalArgumentException) {
                true
            }
            val duplicateNameRejected = runCatching {
                store.createPlaylistWithTracks(existingName.lowercase(), listOf(track))
            }.isFailure

            assertTrue(emptyTracksRejected)
            assertTrue(blankNameRejected)
            assertTrue(duplicateNameRejected)
            assertEquals(existingName, store.loadPlaylists().single { it.id == existing.id }.name)
            assertEquals(before, store.loadPlaylists().filterNot { it.isAuto }.map { it.id to it.name }.toSet())
        } finally {
            store.close()
        }
    }
}
