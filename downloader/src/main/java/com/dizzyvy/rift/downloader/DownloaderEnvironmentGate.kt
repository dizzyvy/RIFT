package com.dizzyvy.rift.downloader

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class DownloaderEnvironmentGate {
    private val mutex = Mutex()
    @Volatile private var initialized = false

    suspend fun ensure(initialize: () -> Unit) {
        if (initialized) return
        mutex.withLock {
            if (!initialized) {
                initialize()
                initialized = true
            }
        }
    }
}
