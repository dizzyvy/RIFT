package com.dizzyvy.rift.downloader.util

import com.dizzyvy.rift.downloader.DownloaderRuntime.Companion.context
import com.dizzyvy.rift.downloader.util.PreferenceUtil.getInt
import com.dizzyvy.rift.downloader.util.PreferenceUtil.updateLong
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UpdateUtil {
    suspend fun updateYtDlp(): YoutubeDL.UpdateStatus? =
        withContext(Dispatchers.IO) {
            val channel =
                when (YT_DLP_UPDATE_CHANNEL.getInt()) {
                    YT_DLP_NIGHTLY -> YoutubeDL.UpdateChannel.NIGHTLY
                    else -> YoutubeDL.UpdateChannel.STABLE
                }

            YoutubeDL.getInstance()
                .updateYoutubeDL(appContext = context, updateChannel = channel)
                .also {
                    if (it == YoutubeDL.UpdateStatus.DONE) {
                        YoutubeDL.getInstance().version(context)?.let { version ->
                            PreferenceUtil.encodeString(YT_DLP_VERSION, version)
                        }
                    }
                    YT_DLP_UPDATE_TIME.updateLong(System.currentTimeMillis())
                }
        }
}
