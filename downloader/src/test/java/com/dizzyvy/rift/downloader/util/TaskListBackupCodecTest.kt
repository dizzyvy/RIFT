package com.dizzyvy.rift.downloader.util

import com.dizzyvy.rift.downloader.download.Task
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TaskListBackupCodecTest {
    private val format = Json {
        ignoreUnknownKeys = true
        allowStructuredMapKeys = true
    }

    @Test
    fun missingBackupDecodesAsEmptyQueue() {
        assertEquals(emptyMap<Task, Task.State>(), decodeTaskListBackupPayload(null, format))
    }

    @Test
    fun malformedBackupIsRejectedInsteadOfReset() {
        assertThrows(SerializationException::class.java) {
            decodeTaskListBackupPayload("{", format)
        }
    }
}
