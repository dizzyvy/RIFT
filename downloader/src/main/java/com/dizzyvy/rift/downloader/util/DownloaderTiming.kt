package com.dizzyvy.rift.downloader.util

import android.os.SystemClock
import android.util.Log
import com.dizzyvy.rift.downloader.BuildConfig
import java.util.UUID

private const val DOWNLOADER_TIMING_TAG = "RiftDownloaderTiming"

internal object DownloaderTiming {
    fun newOperationId(prefix: String): String? =
        if (BuildConfig.DEBUG) "$prefix-${UUID.randomUUID().toString().take(8)}" else null

    fun beginOperation(id: String, stage: String): TimingOperation? {
        if (!BuildConfig.DEBUG) return null
        val startedAt = SystemClock.elapsedRealtime()
        Log.d(DOWNLOADER_TIMING_TAG, "id=$id event=${stage}_started elapsed_realtime_ms=$startedAt")
        return TimingOperation(id, stage, startedAt)
    }

    fun finishOperation(
        operation: TimingOperation?,
        outcome: String = "success",
        error: Throwable? = null,
    ) {
        if (operation == null) return
        val finishedAt = SystemClock.elapsedRealtime()
        val errorType = error?.let { it::class.java.simpleName.ifBlank { "Unknown" } }
        Log.d(
            DOWNLOADER_TIMING_TAG,
            "id=${operation.id} event=${operation.stage}_finished " +
                "elapsed_realtime_ms=$finishedAt duration_ms=${finishedAt - operation.startedAt} " +
                "outcome=$outcome${errorType?.let { " exception_type=$it" }.orEmpty()}",
        )
    }

    fun <T> measure(operation: TimingOperation?, block: () -> T): T {
        if (operation == null) return block()
        return try {
            block().also { finishOperation(operation) }
        } catch (error: Throwable) {
            finishOperation(operation, outcome = "failed", error = error)
            throw error
        }
    }

    fun newFormatTrace(): DownloadTimingTrace? =
        if (BuildConfig.DEBUG) DownloadTimingTrace() else null
}

internal data class TimingOperation(val id: String, val stage: String, val startedAt: Long)

class DownloadTimingTrace internal constructor() {
    internal val id: String = UUID.randomUUID().toString().take(8)
    private val submittedAt = SystemClock.elapsedRealtime()
    private var extractorStartedAt: Long? = null
    private var formatsAvailableAt: Long? = null
    private var contentLaidOut = false
    private var sheetExpanded = false
    private var uiReadyAt: Long? = null
    private var queuedAt: Long? = null
    private var queueSlotStartedAt: Long? = null
    private var transferStartedAt: Long? = null
    private var postProcessingStartedAt: Long? = null

    init {
        log("format_submitted", submittedAt)
    }

    @Synchronized
    internal fun extractorStarted() {
        if (extractorStartedAt != null) return
        val now = SystemClock.elapsedRealtime()
        extractorStartedAt = now
        log("extractor_started", now, "url_to_extractor_start_ms=${now - submittedAt}")
    }

    @Synchronized
    internal fun formatsAvailable() {
        if (formatsAvailableAt != null) return
        val now = SystemClock.elapsedRealtime()
        formatsAvailableAt = now
        val extractorDuration =
            extractorStartedAt?.let { "extractor_to_formats_available_ms=${now - it} " }
                .orEmpty()
        log(
            "formats_available",
            now,
            "${extractorDuration}submission_to_formats_available_ms=${now - submittedAt}",
        )
    }

    @Synchronized
    internal fun wrapperElapsedTimeReported(elapsedTimeMs: Long) {
        val now = SystemClock.elapsedRealtime()
        log("wrapper_elapsed_time_reported", now, "response_elapsed_time_ms=$elapsedTimeMs")
    }

    @Synchronized
    internal fun extractorFailed(error: Throwable) {
        val now = SystemClock.elapsedRealtime()
        val errorType = error::class.java.simpleName.ifBlank { "Unknown" }
        log("extractor_failed", now, "outcome=failed exception_type=$errorType")
    }

    @Synchronized
    internal fun formatContentLaidOut() {
        contentLaidOut = true
        recordFormatUiReady()
    }

    @Synchronized
    internal fun formatSheetExpanded() {
        sheetExpanded = true
        recordFormatUiReady()
    }

    @Synchronized
    internal fun queued() {
        if (queuedAt != null) return
        val now = SystemClock.elapsedRealtime()
        queuedAt = now
        val sinceFormats =
            formatsAvailableAt?.let { "formats_to_enqueue_ms=${now - it} " }.orEmpty()
        log("download_queued", now, sinceFormats)
    }

    @Synchronized
    internal fun queueSlotStarted() {
        if (queueSlotStartedAt != null) return
        val now = SystemClock.elapsedRealtime()
        val enqueuedAt = queuedAt ?: return
        queueSlotStartedAt = now
        log("queue_slot_started", now, "queue_wait_ms=${now - enqueuedAt}")
    }

    @Synchronized
    internal fun transferStarted() {
        if (transferStartedAt != null) return
        val now = SystemClock.elapsedRealtime()
        transferStartedAt = now
        val afterSlot =
            queueSlotStartedAt?.let { "slot_to_transfer_start_ms=${now - it} " }.orEmpty()
        log("transfer_started", now, afterSlot)
    }

    @Synchronized
    internal fun transferFinished(error: Throwable? = null) {
        val now = SystemClock.elapsedRealtime()
        val startedAt = transferStartedAt ?: return
        val outcome = if (error == null) "success" else "failed"
        val errorType =
            error?.let {
                " exception_type=${it::class.java.simpleName.ifBlank { "Unknown" }}"
            }
        log(
            "transfer_finished",
            now,
            "yt_dlp_execution_duration_ms=${now - startedAt} " +
                "outcome=$outcome${errorType.orEmpty()}",
        )
    }

    @Synchronized
    internal fun postProcessingStarted() {
        if (postProcessingStartedAt != null) return
        val now = SystemClock.elapsedRealtime()
        postProcessingStartedAt = now
        log("post_processing_started", now)
    }

    @Synchronized
    internal fun postProcessingFinished(error: Throwable? = null) {
        val now = SystemClock.elapsedRealtime()
        val startedAt = postProcessingStartedAt ?: return
        val outcome = if (error == null) "success" else "failed"
        val errorType =
            error?.let {
                " exception_type=${it::class.java.simpleName.ifBlank { "Unknown" }}"
            }
        log(
            "post_processing_finished",
            now,
            "post_processing_duration_ms=${now - startedAt} outcome=$outcome${errorType.orEmpty()}",
        )
    }

    private fun recordFormatUiReady() {
        val availableAt = formatsAvailableAt ?: return
        if (!contentLaidOut || !sheetExpanded || uiReadyAt != null) return
        val now = SystemClock.elapsedRealtime()
        uiReadyAt = now
        log("format_ui_ready", now, "formats_to_ui_ready_ms=${now - availableAt}")
    }

    private fun log(event: String, elapsedRealtimeMs: Long, details: String = "") {
        Log.d(
            DOWNLOADER_TIMING_TAG,
            "id=$id event=$event elapsed_realtime_ms=$elapsedRealtimeMs $details",
        )
    }
}
