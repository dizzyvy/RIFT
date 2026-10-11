package com.dizzyvy.rift.music.backup

import com.dizzyvy.rift.music.library.BackupDuplicatePair
import com.dizzyvy.rift.music.library.BackupHistoryEntry
import com.dizzyvy.rift.music.library.BackupPlaylist
import com.dizzyvy.rift.music.library.BackupTrackRef
import com.dizzyvy.rift.music.library.LibraryBackupSnapshot
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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
    fun versionOneWithoutLegacyAppearancePreservesExplicitTheme() {
        val snapshot = LibraryBackupCodec.decode(
            """
            {
              "formatVersion": 1,
              "playlists": [],
              "favorites": [],
              "settings": {"themeMode": "dark"}
            }
            """.trimIndent(),
        )

        assertEquals("dark", snapshot.settings["themeMode"])
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
    fun decodesVersionTwoHiddenFoldersStoredAsJsonArray() {
        val snapshot = LibraryBackupCodec.decode(
            """
            {
              "formatVersion": 2,
              "playlists": [],
              "favorites": [],
              "settings": {"hiddenFolders": ["Music/Hidden"]}
            }
            """.trimIndent(),
        )

        assertEquals("""["Music/Hidden"]""", snapshot.settings["hiddenFolders"])
    }

    @Test
    fun decodesLegacyHiddenFolderPath() {
        val snapshot = LibraryBackupCodec.decode(
            """
            {
              "formatVersion": 2,
              "playlists": [],
              "favorites": [],
              "settings": {"hiddenFolders": "Music/Hidden"}
            }
            """.trimIndent(),
        )

        assertEquals("Music/Hidden", org.json.JSONArray(snapshot.settings["hiddenFolders"]).getString(0))
    }

    @Test
    fun rejectsMalformedCurrentVersionHiddenFoldersAndUnknownSettings() {
        val malformedHiddenFolders = """
            {
              "formatVersion": 3,
              "playlists": [],
              "favorites": [],
              "settings": {"hiddenFolders": "[invalid"}
            }
        """.trimIndent()
        val unknownSetting = """
            {
              "formatVersion": 3,
              "playlists": [],
              "favorites": [],
              "settings": {"unrecognizedSetting": "value"}
            }
        """.trimIndent()

        assertThrows(JSONException::class.java) {
            LibraryBackupCodec.decode(malformedHiddenFolders)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LibraryBackupCodec.decode(unknownSetting)
        }
    }

    @Test
    fun rejectsNonStringAndBlankHiddenFolderArrayElements() {
        val invalidElements = listOf(
            "42",
            "null",
            "true",
            """{"path":"Music"}""",
            """["Music/Nested"]""",
            "\"   \"",
        )

        invalidElements.forEach { element ->
            val contents = """
                {
                  "formatVersion": 3,
                  "playlists": [],
                  "favorites": [],
                  "settings": {"hiddenFolders": "[$element]"}
                }
            """.trimIndent()

            val exception = assertThrows(IllegalArgumentException::class.java) {
                LibraryBackupCodec.decode(contents)
            }
            assertEquals("Backup contains an invalid hidden-folder path.", exception.message)
        }
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

        assertThrows(IllegalArgumentException::class.java) {
            LibraryBackupCodec.decode(malformedHistory)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LibraryBackupCodec.decode(malformedPair)
        }
    }
}
