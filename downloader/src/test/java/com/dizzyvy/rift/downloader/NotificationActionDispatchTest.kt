package com.dizzyvy.rift.downloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationActionDispatchTest {
    @Test
    fun cancellationHandledByDownloaderDoesNotUseFallback() {
        var fallbackCalls = 0

        val canceled =
            dispatchNotificationTaskCancellation(
                taskId = "task-1",
                cancelTask = { it == "task-1" },
                cancelUnmanagedTask = { fallbackCalls++ },
            )

        assertTrue(canceled)
        assertEquals(0, fallbackCalls)
    }

    @Test
    fun unmanagedCancellationUsesFallbackOnce() {
        val fallbackTaskIds = mutableListOf<String>()

        val canceled =
            dispatchNotificationTaskCancellation(
                taskId = "custom-task",
                cancelTask = { false },
                cancelUnmanagedTask = fallbackTaskIds::add,
            )

        assertFalse(canceled)
        assertEquals(listOf("custom-task"), fallbackTaskIds)
    }
}
