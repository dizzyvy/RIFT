package com.dizzyvy.rift.downloader

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dizzyvy.rift.downloader.util.NotificationUtil
import com.dizzyvy.rift.downloader.util.ToastUtil
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class NotificationActionReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "DownloaderActionReceiver"
        private const val ACTION_TIMEOUT_MS = 8_000L
        // Existing notification PendingIntents may still carry these extra keys.
        private const val LEGACY_ACTION_EXTRA_PREFIX = "com.junkfood.seal."

        const val ACTION_CANCEL_TASK = 0
        const val ACTION_ERROR_REPORT = 1

        const val ACTION_KEY = LEGACY_ACTION_EXTRA_PREFIX + "action"
        const val TASK_ID_KEY = LEGACY_ACTION_EXTRA_PREFIX + "taskId"

        const val NOTIFICATION_ID_KEY = LEGACY_ACTION_EXTRA_PREFIX + "notificationId"
        const val ERROR_REPORT_KEY = LEGACY_ACTION_EXTRA_PREFIX + "error_report"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val actionIntent = intent ?: return
        val appContext = context?.applicationContext ?: return
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(ACTION_TIMEOUT_MS) {
                    val notificationId = actionIntent.getIntExtra(NOTIFICATION_ID_KEY, 0)
                    when (actionIntent.getIntExtra(ACTION_KEY, ACTION_CANCEL_TASK)) {
                        ACTION_CANCEL_TASK -> {
                            val taskId = actionIntent.getStringExtra(TASK_ID_KEY)
                            cancelTask(taskId, notificationId, appContext)
                        }
                        ACTION_ERROR_REPORT -> {
                            val errorReport = actionIntent.getStringExtra(ERROR_REPORT_KEY)
                            if (!errorReport.isNullOrEmpty()) {
                                copyErrorReport(errorReport, notificationId, appContext)
                            }
                        }
                        else -> Log.w(TAG, "Ignoring unknown notification action.")
                    }
                }
            } catch (error: TimeoutCancellationException) {
                Log.e(TAG, "Timed out preparing downloader notification action.", error)
            } catch (error: Exception) {
                Log.e(TAG, "Could not process downloader notification action.", error)
            } catch (error: LinkageError) {
                Log.e(TAG, "Downloader notification support could not be loaded.", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun cancelTask(taskId: String?, notificationId: Int, context: Context) {
        if (taskId.isNullOrEmpty()) return
        val downloader = DownloaderRuntime.downloaderForNotificationAction(context)
        NotificationUtil.cancelNotification(notificationId)
        val canceled = dispatchNotificationTaskCancellation(
            taskId = taskId,
            cancelTask = downloader::cancel,
            cancelUnmanagedTask = { id ->
                runCatching { YoutubeDL.destroyProcessById(id) }
                    .onFailure { Log.w(TAG, "Could not stop unmanaged task $id.", it) }
                Downloader.onProcessCanceled(id)
            },
        )
        if (canceled) Log.d(TAG, "Task (id:$taskId) was canceled.")
    }

    private suspend fun copyErrorReport(error: String, notificationId: Int, context: Context) {
        DownloaderRuntime.ensureEnvironmentInitialized(context)
        withContext(Dispatchers.Main.immediate) {
            DownloaderRuntime.clipboard.setPrimaryClip(ClipData.newPlainText(null, error))
            ToastUtil.makeToastSuspend(context.getString(R.string.error_copied))
        }
        NotificationUtil.cancelNotification(notificationId)
    }
}

internal fun dispatchNotificationTaskCancellation(
    taskId: String,
    cancelTask: (String) -> Boolean,
    cancelUnmanagedTask: (String) -> Unit,
): Boolean {
    if (cancelTask(taskId)) return true
    cancelUnmanagedTask(taskId)
    return false
}
