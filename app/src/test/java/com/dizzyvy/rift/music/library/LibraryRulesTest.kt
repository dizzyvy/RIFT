package com.dizzyvy.rift.music.library

import android.net.Uri
import com.dizzyvy.rift.music.model.AudioTrack
import com.dizzyvy.rift.music.playback.PlaybackHistoryTracker
import com.dizzyvy.rift.ui.library.buildDuplicateGroups
import com.dizzyvy.rift.ui.library.groupArtistAliasesByGroup
import com.dizzyvy.rift.ui.library.firstIndexByLibrarySection
import com.dizzyvy.rift.ui.library.normalizeLibrarySearchText
import com.dizzyvy.rift.ui.library.selectedTracksInLibraryOrder
import com.dizzyvy.rift.ui.library.shouldReuseCachedTracks
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
    fun fastScrollIndexKeepsOnlyTheFirstPositionForEachSection() {
        assertEquals(
            mapOf('A' to 0, 'B' to 2, '#' to 3),
            firstIndexByLibrarySection(listOf("Artist", "Another artist", "Band", "123")),
        )
    }

    @Test
    fun artistAliasesAreGroupedByResolvedCanonicalArtist() {
        val aliases = mapOf(
            "band" to "The Band",
            "bandname" to "The Band",
            "solo" to "Solo Artist",
        )

        assertEquals(setOf("band", "bandname"), groupArtistAliasesByGroup(aliases)["theband"])
        assertEquals(setOf("solo"), groupArtistAliasesByGroup(aliases)["solo artist"])
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

    @Test
    fun playbackHistoryStartsImmediatelyAndIgnoresProgressAndPauseResume() {
        val tracker = PlaybackHistoryTracker()

        assertEquals(null, tracker.onPlaybackState("content://media/audio/1", isPlaying = false))
        assertEquals("content://media/audio/1", tracker.onPlaybackState("content://media/audio/1", isPlaying = true))
        assertEquals(null, tracker.onPlaybackState("content://media/audio/1", isPlaying = true))
        assertEquals(null, tracker.onPlaybackState("content://media/audio/1", isPlaying = false))
        assertEquals(null, tracker.onPlaybackState("content://media/audio/1", isPlaying = true))
    }

    @Test
    fun playbackHistoryRecordsSameUriAgainAfterPlaybackStops() {
        val tracker = PlaybackHistoryTracker()

        assertEquals("track-a", tracker.onPlaybackState("track-a", isPlaying = true))
        assertEquals(null, tracker.onPlaybackState("track-a", isPlaying = false, stopped = true))
        assertEquals("track-a", tracker.onPlaybackState("track-a", isPlaying = true))
    }

    @Test
    fun playbackHistoryRecordsConsecutiveQueueEntriesWithSameUri() {
        val tracker = PlaybackHistoryTracker()

        assertEquals("track-a", tracker.onPlaybackState("track-a", isPlaying = true))
        assertEquals(
            "track-a",
            tracker.onPlaybackState("track-a", isPlaying = true, mediaItemTransition = true),
        )
        assertEquals(null, tracker.onPlaybackState("track-a", isPlaying = true))
    }

    @Test
    fun playbackHistoryDoesNotCountRepeatOneTransitionsOrProgressUpdates() {
        val tracker = PlaybackHistoryTracker()

        assertEquals("track-a", tracker.onPlaybackState("track-a", isPlaying = true))
        assertEquals(
            null,
            tracker.onPlaybackState(
                "track-a",
                isPlaying = true,
                mediaItemTransition = true,
                repeatOneLoop = true,
            ),
        )
        assertEquals(null, tracker.onPlaybackState("track-a", isPlaying = true))
    }

    @Test
    fun selectedTracksPreserveVisibleLibraryOrder() {
        val first = track(1L, "content://media/audio/1", 1_000L)
        val second = track(2L, "content://media/audio/2", 1_000L)
        val third = track(3L, "content://media/audio/3", 1_000L)

        assertEquals(
            listOf(first, third),
            selectedTracksInLibraryOrder(listOf(first, second, third), setOf(third.uri.toString(), first.uri.toString())),
        )
    }

    @Test
    fun cachedLibraryIsReusedOnlyForMatchingKnownMediaStoreVersions() {
        assertTrue(shouldReuseCachedTracks(true, "version-a", "version-a"))
        assertFalse(shouldReuseCachedTracks(false, "version-a", "version-a"))
        assertFalse(shouldReuseCachedTracks(true, "version-a", "version-b"))
        assertFalse(shouldReuseCachedTracks(true, "legacy", "legacy"))
        assertFalse(shouldReuseCachedTracks(true, "unknown", "unknown"))
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
