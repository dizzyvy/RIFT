package com.dizzyvy.rift.music.backup

import com.dizzyvy.rift.music.library.BackupDuplicatePair
import com.dizzyvy.rift.music.library.BackupHistoryEntry
import com.dizzyvy.rift.music.library.BackupPlaylist
import com.dizzyvy.rift.music.library.BackupTrackRef
import com.dizzyvy.rift.music.library.LibraryBackupSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryBackupCodecTest {
    @Test
    fun decodesVersionOneBackupsAndMigratesAppearanceToSystem() {
        val snapshot = LibraryBackupCodec.decode(
            """
            {
              "formatVersion": 1,
              "playlists": [],
              "favorites": [],
              "settings": {
                "accentName": "coral",
                "textColor": "chromatic",
                "themeMode": "dark"
              }
            }
            """.trimIndent(),
        )

        assertEquals("system", snapshot.settings["themeMode"])
        assertTrue(snapshot.artistAliases.isEmpty())
        assertTrue(snapshot.playHistory.isEmpty())
        assertTrue(snapshot.notDuplicatePairs.isEmpty())
    }

    @Test
    fun decodesVersionTwoBackupsWithEmptyVersionThreeFields() {
        val snapshot = LibraryBackupCodec.decode(
            """
            {
              "formatVersion": 2,
              "playlists": [{"name": "Road", "tracks": []}],
              "favorites": [],
              "settings": {"themeMode": "light"}
            }
            """.trimIndent(),
        )

        assertEquals("Road", snapshot.playlists.single().name)
        assertEquals("light", snapshot.settings["themeMode"])
        assertTrue(snapshot.playHistory.isEmpty())
        assertTrue(snapshot.artistAliases.isEmpty())
        assertTrue(snapshot.notDuplicatePairs.isEmpty())
    }

    @Test
    fun versionThreeRoundTripPreservesUserDataAndFolderHints() {
        val first = BackupTrackRef(
            uri = "content://media/audio/1",
            title = "Song",
            artist = "Artist",
            album = "Album",
            durationMs = 180_000L,
            folderName = "Records",
        )
        val second = first.copy(uri = "content://media/audio/2", folderName = "Archive")
        val snapshot = LibraryBackupSnapshot(
            playlists = listOf(BackupPlaylist("Road", listOf(first, second))),
            favorites = listOf(first),
            settings = mapOf("themeMode" to "system", "hiddenFolders" to """["Music/Hidden"]"""),
            playHistory = listOf(BackupHistoryEntry(first, 8, 1_700_000_000_000L)),
            artistAliases = mapOf("The Artist" to "Artist"),
            notDuplicatePairs = listOf(BackupDuplicatePair(first, second)),
        )

        val restored = LibraryBackupCodec.decode(LibraryBackupCodec.encode(snapshot))

        assertEquals(snapshot, restored)
    }

    @Test
    fun rejectsMalformedPlayHistoryAndDuplicateExclusion() {
        val malformedHistory = """
            {
              "formatVersion": 3,
              "playlists": [],
              "favorites": [],
              "playHistory": [{
                "track": {"uri": "content://media/audio/1", "durationMs": 1},
                "playCount": -1,
                "lastPlayedAtMs": 10
              }],
              "settings": {}
            }
        """.trimIndent()
        val malformedPair = """
            {
              "formatVersion": 3,
              "playlists": [],
              "favorites": [],
              "notDuplicatePairs": [{
                "first": {"uri": "content://media/audio/1", "durationMs": 1},
                "second": {"uri": "content://media/audio/1", "durationMs": 1}
              }],
              "settings": {}
            }
        """.trimIndent()

        assertTrue(runCatching { LibraryBackupCodec.decode(malformedHistory) }.isFailure)
        assertTrue(runCatching { LibraryBackupCodec.decode(malformedPair) }.isFailure)
    }
}
