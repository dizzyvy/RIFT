package com.dizzyvy.rift.music.library

import android.net.Uri
import com.dizzyvy.rift.music.model.AudioTrack
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class M3uPlaylistFormatTest {
    @Test
    fun encodesTracksAndParsesOnlyNonCommentEntries() {
        val track = AudioTrack(
            id = 1L,
            uri = Uri.parse("content://media/audio/1"),
            title = "Song",
            artist = "Artist",
            album = "Album",
            durationMs = 123_000L,
        )

        val encoded = M3uPlaylistFormat.encode("Mix", listOf(track))

        assertEquals(
            listOf("content://media/audio/1"),
            M3uPlaylistFormat.entries(encoded),
        )
        assertEquals("My Song.mp3", M3uPlaylistFormat.fileName("file:///Music/My%20Song.mp3"))
    }
}
