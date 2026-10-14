package com.dizzyvy.rift.downloader.util

import android.os.SystemClock
import android.util.Log
import com.dizzyvy.rift.downloader.BuildConfig
import com.yausername.youtubedl_android.YoutubeDLTimingEvent
import java.util.UUID

private const val DOWNLOADER_TIMING_TAG = "RiftDownloaderTiming"
private val SAFE_TIMING_VALUE = Regex("[A-Za-z_][A-Za-z0-9_]{0,63}")
private val TIMING_CATEGORIES =
    setOf("request", "extractor", "download", "postprocess", "retry", "process")

private fun Throwable.timingExceptionType(): String =
    javaClass.simpleName.takeIf { SAFE_TIMING_VALUE.matches(it) } ?: "Unknown"

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
        val errorType = error?.timingExceptionType()
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
    private var formatResultPublishedAt: Long? = null
    private var contentLaidOut = false
    private var sheetExpanded = false
    private var uiReadyAt: Long? = null
    private var queuedAt: Long? = null
    private var taskEnqueuedAt: Long? = null
    private var queueSlotStartedAt: Long? = null
    private var transferStartedAt: Long? = null
    private var postProcessingStartedAt: Long? = null
    private var taskFinishedAt: Long? = null

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
    internal fun formatResultPublished(outcome: String, error: Throwable? = null) {
        if (formatResultPublishedAt != null) return
        val now = SystemClock.elapsedRealtime()
        formatResultPublishedAt = now
        val elapsed = formatsAvailableAt?.let { "formats_to_result_publication_ms=${now - it} " }
        val errorType = error?.let { " exception_type=${it.timingExceptionType()}" }
        log(
            "format_result_published",
            now,
            "${elapsed.orEmpty()}outcome=$outcome${errorType.orEmpty()}",
        )
    }

    @Synchronized
    internal fun wrapperElapsedTimeReported(elapsedTimeMs: Long) {
        val now = SystemClock.elapsedRealtime()
        log("wrapper_elapsed_time_reported", now, "response_elapsed_time_ms=$elapsedTimeMs")
    }

    @Synchronized
    internal fun wrapperTimingEvent(event: YoutubeDLTimingEvent) {
        val now = SystemClock.elapsedRealtime()
        val details = buildList {
            if (event.name == "retry_reported") {
                add("retry_count=${event.operationId}")
            } else if (event.operationId > 0) {
                add("runtime_operation_id=${event.operationId}")
            }
            if (event.clockSource == "yt_dlp_monotonic") {
                add("yt_dlp_monotonic_ns=${event.monotonicTimeNs}")
            } else {
                add("wrapper_monotonic_ns=${event.monotonicTimeNs}")
            }
            event.durationMs?.let { add("duration_ms=$it") }
            add("outcome=${event.outcome}")
            event.exceptionType
                ?.takeIf { SAFE_TIMING_VALUE.matches(it) }
                ?.let { add("exception_type=$it") }
            event.category
                ?.takeIf { it in TIMING_CATEGORIES }
                ?.let { add("category=$it") }
            event.value?.let {
                when (event.name) {
                    "retry_reported" -> add("retry_limit=$it")
                    "wrapper_process_exited" -> add("exit_code=$it")
                }
            }
        }
        log(event.name, now, details.joinToString(" "))
    }

    @Synchronized
    internal fun extractorFailed(error: Throwable) {
        val now = SystemClock.elapsedRealtime()
        val errorType = error.timingExceptionType()
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
    internal fun taskEnqueued() {
        if (taskEnqueuedAt != null) return
        val now = SystemClock.elapsedRealtime()
        taskEnqueuedAt = now
        log("task_enqueued", now)
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
        val errorType = error?.let { " exception_type=${it.timingExceptionType()}" }
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
        val errorType = error?.let { " exception_type=${it.timingExceptionType()}" }
        log(
            "post_processing_finished",
            now,
            "post_processing_duration_ms=${now - startedAt} outcome=$outcome${errorType.orEmpty()}",
        )
    }

    @Synchronized
    internal fun taskFinished(outcome: String, error: Throwable? = null) {
        if (taskFinishedAt != null) return
        val now = SystemClock.elapsedRealtime()
        taskFinishedAt = now
        val duration = queuedAt?.let { "queued_to_terminal_ms=${now - it} " }.orEmpty()
        val enqueueDuration =
            taskEnqueuedAt?.let { "enqueued_to_terminal_ms=${now - it} " }.orEmpty()
        val errorType = error?.let { " exception_type=${it.timingExceptionType()}" }
        log("task_finished", now, "$duration$enqueueDuration outcome=$outcome${errorType.orEmpty()}")
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
