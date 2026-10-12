package com.dizzyvy.rift.downloader.download

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskSerializationCompatibilityTest {
    @Test
    fun taskStateKeepsLegacyPolymorphicNames() {
        val state: Task.DownloadState =
            Task.DownloadState.Canceled(action = Task.RestartableAction.Download)
        val encoded = Json.encodeToString<Task.DownloadState>(state)

        assertTrue(encoded.contains("com.junkfood.seal.download.Task.DownloadState.Canceled"))
        assertTrue(encoded.contains("com.junkfood.seal.download.Task.RestartableAction.Download"))
        assertEquals(state, Json.decodeFromString<Task.DownloadState>(encoded))
    }

    @Test
    fun urlTypeKeepsLegacyPolymorphicName() {
        val encoded = Json.encodeToString<Task.TypeInfo>(Task.TypeInfo.URL)

        assertTrue(encoded.contains("com.junkfood.seal.download.Task.TypeInfo.URL"))
        assertEquals(Task.TypeInfo.URL, Json.decodeFromString<Task.TypeInfo>(encoded))
    }
}
