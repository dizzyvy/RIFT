package com.dizzyvy.rift.music.backup

import com.dizzyvy.rift.music.library.BackupDuplicatePair
import com.dizzyvy.rift.music.library.BackupHistoryEntry
import com.dizzyvy.rift.music.library.BackupPlaylist
import com.dizzyvy.rift.music.library.BackupTrackRef
import com.dizzyvy.rift.music.library.LibraryBackupSnapshot
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
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

        val folders = JSONArray(snapshot.settings["hiddenFolders"])
        assertEquals(1, folders.length())
        assertEquals("Music/Hidden", folders.getString(0))
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

        val folders = JSONArray(snapshot.settings["hiddenFolders"])
        assertEquals(1, folders.length())
        assertEquals("Music/Hidden", folders.getString(0))
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
        val invalidElements: List<Pair<String, Any?>> = listOf(
            "number" to 42,
            "JSON null" to JSONObject.NULL,
            "boolean" to true,
            "object" to JSONObject().put("path", "Music"),
            "nested array" to JSONArray().put("Music/Nested"),
            "empty string" to "",
            "whitespace-only string" to "   ",
        )

        invalidElements.forEach { (description, element) ->
            val folders = JSONArray().put(element).toString()
            val settings = JSONObject().put("hiddenFolders", folders)
            val contents = JSONObject()
                .put("formatVersion", 3)
                .put("playlists", JSONArray())
                .put("favorites", JSONArray())
                .put("settings", settings)
                .toString()

            val exception = assertThrows(IllegalArgumentException::class.java) {
                LibraryBackupCodec.decode(contents)
            }
            assertEquals(
                description,
                "Backup contains an invalid hidden-folder path.",
                exception.message,
            )
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

        val expectedFolders = JSONArray(snapshot.settings.getValue("hiddenFolders"))
        val restoredFolders = JSONArray(restored.settings.getValue("hiddenFolders"))
        assertEquals(expectedFolders.length(), restoredFolders.length())
        for (index in 0 until expectedFolders.length()) {
            assertEquals(expectedFolders.getString(index), restoredFolders.getString(index))
        }
        assertEquals(
            snapshot.copy(settings = snapshot.settings - "hiddenFolders"),
            restored.copy(settings = restored.settings - "hiddenFolders"),
        )
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
