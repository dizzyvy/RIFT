package com.dizzyvy.rift.music.library

import android.net.Uri
import com.dizzyvy.rift.music.model.AudioTrack
import com.dizzyvy.rift.ui.library.buildDuplicateGroups
import com.dizzyvy.rift.ui.library.normalizeLibrarySearchText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LibraryRulesTest {
    @Test
    fun titleSortingIgnoresLeadingNoiseAndThe() {
        assertEquals("Eternal", sortableTitle("\"Eternal"))
        assertEquals("N The Casa", sortableTitle("'N The Casa"))
        assertEquals("Eternal", sortableTitle("The Eternal"))
        assertTrue(compareNaturalIgnoreCase("Track 2", "Track 10") < 0)
    }

    @Test
    fun yearsAndGenresRejectInvalidValuesAndNormalizeKnownGenres() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        assertFalse(isValidLibraryYear(1899))
        assertTrue(isValidLibraryYear(currentYear))
        assertTrue(isValidLibraryYear(currentYear + 1))
        assertFalse(isValidLibraryYear(currentYear + 2))
        assertEquals("Hip-Hop/Rap", canonicalGenre("hip hop & rap"))
        assertEquals(null, canonicalGenre("People & Blogs"))
        assertEquals("Rock", genreGroupLabel("rock"))
    }

    @Test
    fun metadataCleanupRemovesVideoTagsAndInfersArtistFromFilenameTitle() {
        val track = AudioTrack(
            id = 1L,
            uri = Uri.parse("content://media/audio/1"),
            title = "Artist - Song [Official Video]",
            artist = "<unknown>",
            album = "Unknown Album",
            durationMs = 180_000L,
            genre = "rap",
        )

        val cleaned = cleanTrackMetadata(track)

        assertEquals("Song", cleaned.title)
        assertEquals("Artist", cleaned.artist)
        assertEquals("Hip-Hop/Rap", cleaned.genre)
        assertTrue(cleaned.metadataCleaned)
    }

    @Test
    fun searchNormalizationIgnoresAccentsPunctuationAndExpandsAmpersands() {
        assertEquals("beyonce and jay z", normalizeLibrarySearchText("Beyoncé & Jay-Z"))
        assertEquals("beyonce jay z", normalizeLibrarySearchText("Beyonce Jay Z"))
    }

    @Test
    fun duplicateDetectionFindsMatchesAndHonorsUserExclusions() {
        val first = track(id = 1L, uri = "content://media/audio/1", size = 1_000L)
        val second = track(id = 2L, uri = "content://media/audio/2", size = 1_000L, duration = 180_500L)

        val groups = buildDuplicateGroups(listOf(first, second), emptyMap(), emptySet())
        val excluded = buildDuplicateGroups(
            listOf(first, second),
            emptyMap(),
            setOf(duplicatePairKey(first.uri.toString(), second.uri.toString())),
        )

        assertEquals(1, groups.size)
        assertEquals(2, groups.single().tracks.size)
        assertTrue(excluded.isEmpty())
    }

    private fun track(
        id: Long,
        uri: String,
        size: Long,
        duration: Long = 180_000L,
    ) = AudioTrack(
        id = id,
        uri = Uri.parse(uri),
        title = "Song",
        artist = "Artist",
        album = "Album",
        durationMs = duration,
        sizeBytes = size,
    )
}
