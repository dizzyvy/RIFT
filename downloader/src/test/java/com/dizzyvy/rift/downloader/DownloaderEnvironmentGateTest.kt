package com.dizzyvy.rift.downloader

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloaderEnvironmentGateTest {
    @Test
    fun concurrentRequestsInitializeEnvironmentOnlyOnce() = runBlocking {
        val gate = DownloaderEnvironmentGate()
        val initializationCount = AtomicInteger()

        (1..8)
            .map {
                async(Dispatchers.Default) {
                    gate.ensure { initializationCount.incrementAndGet() }
                }
            }
            .awaitAll()

        assertEquals(1, initializationCount.get())
    }

    @Test
    fun failedInitializationCanBeRetried() = runBlocking {
        val gate = DownloaderEnvironmentGate()
        var initializationCount = 0

        val failedAttempt = runCatching { gate.ensure { error("initialization failed") } }
        gate.ensure { initializationCount++ }

        assertTrue(failedAttempt.isFailure)
        assertEquals(1, initializationCount)
    }
}
