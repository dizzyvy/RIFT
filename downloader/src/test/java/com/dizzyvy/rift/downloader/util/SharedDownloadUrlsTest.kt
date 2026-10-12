package com.dizzyvy.rift.downloader.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedDownloadUrlsTest {
    @Test
    fun extractsDistinctHttpLinksFromSharedText() {
        assertEquals(
            listOf(
                "https://example.com/video",
                "http://example.org/watch?id=1",
                "HTTPS://example.net/video",
            ),
            findSharedDownloadUrls(
                "Download these: https://example.com/video and http://example.org/watch?id=1 " +
                    "https://example.com/video HTTPS://example.net/video",
            ),
        )
    }

    @Test
    fun ignoresTextWithoutHttpLinks() {
        assertEquals(emptyList<String>(), findSharedDownloadUrls("No downloadable link here"))
    }
}
