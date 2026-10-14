package com.dizzyvy.rift.downloader

import com.dizzyvy.rift.downloader.util.DownloaderTiming
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex

internal class DownloaderEnvironmentGate {
    private val mutex = Mutex()
    @Volatile private var initialized = false

    suspend fun ensure(operationId: String? = null, initialize: () -> Unit) {
        if (initialized) return
        val gateWait =
            operationId?.let { DownloaderTiming.beginOperation(it, "environment_gate_wait") }
        try {
            mutex.lock()
        } catch (cancelled: CancellationException) {
            DownloaderTiming.finishOperation(gateWait, outcome = "cancelled", error = cancelled)
            throw cancelled
        }
        DownloaderTiming.finishOperation(gateWait)
        try {
            if (!initialized) {
                initialize()
                initialized = true
            }
        } finally {
            mutex.unlock()
        }
    }
}
