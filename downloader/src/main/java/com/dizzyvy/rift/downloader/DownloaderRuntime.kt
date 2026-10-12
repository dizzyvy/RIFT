package com.dizzyvy.rift.downloader

import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.dizzyvy.rift.downloader.download.DownloaderV2
import com.dizzyvy.rift.downloader.download.DownloaderV2Impl
import com.dizzyvy.rift.downloader.ui.page.downloadv2.configure.DownloadDialogViewModel
import com.dizzyvy.rift.downloader.ui.page.settings.directory.Directory
import com.dizzyvy.rift.downloader.ui.page.settings.network.CookiesViewModel
import com.dizzyvy.rift.downloader.ui.page.videolist.VideoListViewModel
import com.dizzyvy.rift.downloader.util.AUDIO_DIRECTORY
import com.dizzyvy.rift.downloader.util.AUDIO_DIRECTORY_TREE_URI
import com.dizzyvy.rift.downloader.util.COMMAND_DIRECTORY
import com.dizzyvy.rift.downloader.util.COMMAND_DIRECTORY_TREE_URI
import com.dizzyvy.rift.downloader.util.PreferenceUtil.getString
import com.dizzyvy.rift.downloader.util.PreferenceUtil.updateString
import com.dizzyvy.rift.downloader.util.SDCARD_URI
import com.dizzyvy.rift.downloader.util.VIDEO_DIRECTORY
import com.dizzyvy.rift.downloader.util.VIDEO_DIRECTORY_TREE_URI
import com.dizzyvy.rift.downloader.util.YT_DLP_VERSION
import com.dizzyvy.rift.downloader.util.DownloadUtil
import com.dizzyvy.rift.downloader.util.FileUtil
import com.dizzyvy.rift.downloader.util.FileUtil.createEmptyFile
import com.dizzyvy.rift.downloader.util.FileUtil.getCookiesFile
import com.dizzyvy.rift.downloader.util.FileUtil.writeContentToFile
import com.dizzyvy.rift.downloader.util.NotificationUtil
import com.tencent.mmkv.MMKV
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

sealed interface DownloaderInitialization {
    data object NotStarted : DownloaderInitialization
    data object Preparing : DownloaderInitialization
    data object Ready : DownloaderInitialization
    data class Failed(val message: String) : DownloaderInitialization
}

class DownloaderRuntime private constructor() {
    companion object {
        private val initializationLock = Any()
        private val environmentInitialization = DownloaderEnvironmentGate()
        private val serviceLock = Any()
        private val mutableInitializationState =
            MutableStateFlow<DownloaderInitialization>(DownloaderInitialization.NotStarted)

        val initializationState: StateFlow<DownloaderInitialization> =
            mutableInitializationState.asStateFlow()

        private var youtubeDlInitialized = false
        private var ffmpegInitialized = false
        private var aria2cInitialized = false
        private var initializationJob: Job? = null

        lateinit var clipboard: ClipboardManager
            private set
        lateinit var connectivityManager: ConnectivityManager
            private set
        lateinit var applicationScope: CoroutineScope
            private set
        lateinit var packageInfo: PackageInfo
            private set
        lateinit var context: Context
            private set
        lateinit var videoDownloadDir: String
            private set
        lateinit var audioDownloadDir: String
            private set
        lateinit var commandDownloadDir: String
            private set
        var videoDownloadTreeUri: String? = null
            private set
        var audioDownloadTreeUri: String? = null
            private set
        var commandDownloadTreeUri: String? = null
            private set

        var isServiceRunning = false
            private set
        private var isServiceBound = false

        private val connection =
            object : ServiceConnection {
                override fun onServiceConnected(className: ComponentName, service: IBinder) {
                    service as DownloadService.DownloadServiceBinder
                    isServiceRunning = true
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    isServiceRunning = false
                }
            }

        fun initialize(appContext: Context) {
            synchronized(initializationLock) {
                if (mutableInitializationState.value == DownloaderInitialization.Ready) return
                if (initializationJob?.isActive == true) return

                val applicationContext = appContext.applicationContext
                ensureApplicationScope()
                mutableInitializationState.value = DownloaderInitialization.Preparing

                initializationJob =
                    applicationScope.launch(Dispatchers.IO) {
                        try {
                            ensureEnvironmentInitialized(applicationContext)
                            if (!youtubeDlInitialized) {
                                YoutubeDL.init(context)
                                youtubeDlInitialized = true
                            }
                            if (!ffmpegInitialized) {
                                FFmpeg.init(context)
                                ffmpegInitialized = true
                            }
                            if (!aria2cInitialized) {
                                Aria2c.init(context)
                                aria2cInitialized = true
                            }
                            DownloadUtil.getCookiesContentFromDatabase()
                                .getOrNull()
                                ?.let { writeContentToFile(it, getCookiesFile()) }
                            withContext(Dispatchers.Main.immediate) {
                                mutableInitializationState.value = DownloaderInitialization.Ready
                            }
                        } catch (error: Throwable) {
                            Log.e("RiftDownloader", "Downloader initialization failed.", error)
                            withContext(Dispatchers.Main.immediate) {
                                mutableInitializationState.value =
                                    DownloaderInitialization.Failed(
                                        error.message ?: "Downloader tools could not be initialized.",
                                    )
                            }
                        }
                    }
            }
        }

        suspend fun ensureEnvironmentInitialized(appContext: Context) {
            ensureApplicationScope()
            withContext(Dispatchers.IO) {
                environmentInitialization.ensure {
                    configureEnvironment(appContext.applicationContext)
                }
            }
        }

        suspend fun downloaderForNotificationAction(appContext: Context): DownloaderV2 {
            ensureEnvironmentInitialized(appContext)
            return org.koin.core.context.GlobalContext.get()
                .get<DownloaderV2>()
                .also { it.awaitTasksRestored() }
        }

        private fun ensureApplicationScope() {
            synchronized(initializationLock) {
                if (!::applicationScope.isInitialized) {
                    applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
                }
            }
        }

        private fun configureEnvironment(appContext: Context) {
            val mmkvDirectory = File(appContext.filesDir, "rift_downloader_mmkv").apply {
                check(isDirectory || mkdirs()) { "Could not create downloader preferences storage." }
            }
            MMKV.initialize(appContext, mmkvDirectory.absolutePath)
            context = appContext
            clipboard =
                appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            connectivityManager =
                appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            packageInfo =
                if (Build.VERSION.SDK_INT >= 33) {
                    appContext.packageManager.getPackageInfo(
                        appContext.packageName,
                        PackageManager.PackageInfoFlags.of(0),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    appContext.packageManager.getPackageInfo(appContext.packageName, 0)
                }

            val downloadRoot = FileUtil.getExternalDownloadDirectory()
            val videoDirectory = File(downloadRoot, "Video").apply { check(isDirectory || mkdirs()) }
            val audioDirectory = File(downloadRoot, "Audio").apply { check(isDirectory || mkdirs()) }
            videoDownloadDir = VIDEO_DIRECTORY.getString(videoDirectory.absolutePath)
            audioDownloadDir =
                AUDIO_DIRECTORY.getString(File(downloadRoot, "Audio").absolutePath)
            File(videoDownloadDir).mkdirs()
            File(audioDownloadDir).mkdirs()
            commandDownloadDir =
                COMMAND_DIRECTORY.getString(
                    File(appContext.filesDir, "downloader/commands").absolutePath,
                )
            File(commandDownloadDir).mkdirs()
            videoDownloadTreeUri = VIDEO_DIRECTORY_TREE_URI.getString().takeIf(String::isNotBlank)
            audioDownloadTreeUri = AUDIO_DIRECTORY_TREE_URI.getString().takeIf(String::isNotBlank)
            commandDownloadTreeUri =
                COMMAND_DIRECTORY_TREE_URI.getString().takeIf(String::isNotBlank)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationUtil.createNotificationChannel()
            }
            startKoin {
                androidLogger()
                androidContext(appContext)
                modules(
                    module {
                        single<DownloaderV2> { DownloaderV2Impl(appContext) }
                        viewModel { DownloadDialogViewModel(downloader = get()) }
                        viewModel { CookiesViewModel() }
                        viewModel { VideoListViewModel() }
                    },
                )
            }
        }

        fun startService() {
            synchronized(serviceLock) {
                if (isServiceBound) return
                isServiceBound =
                    context.bindService(
                        Intent(context, DownloadService::class.java),
                        connection,
                        Context.BIND_AUTO_CREATE,
                    )
                if (!isServiceBound) {
                    Log.e("RiftDownloader", "Could not bind the download notification service.")
                }
            }
        }

        fun stopService() {
            synchronized(serviceLock) {
                if (!isServiceBound) return
                context.unbindService(connection)
                isServiceBound = false
                isServiceRunning = false
            }
        }

        val privateDownloadDir: String
            get() =
                File(context.filesDir, "downloader/private").run {
                    check(isDirectory || mkdirs()) { "Could not create private download storage." }
                    createEmptyFile(".nomedia")
                    absolutePath
                }

        fun updateDownloadDir(uri: Uri, directoryType: Directory) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            when (directoryType) {
                Directory.AUDIO -> {
                    audioDownloadTreeUri = uri.toString()
                    AUDIO_DIRECTORY_TREE_URI.updateString(uri.toString())
                }
                Directory.VIDEO -> {
                    videoDownloadTreeUri = uri.toString()
                    VIDEO_DIRECTORY_TREE_URI.updateString(uri.toString())
                }
                Directory.CUSTOM_COMMAND -> {
                    commandDownloadTreeUri = uri.toString()
                    COMMAND_DIRECTORY_TREE_URI.updateString(uri.toString())
                    commandDownloadDir =
                        File(context.filesDir, "downloader/commands").apply {
                            check(isDirectory || mkdirs()) {
                                "Could not create custom-command storage."
                            }
                        }.absolutePath
                    COMMAND_DIRECTORY.updateString(commandDownloadDir)
                }
                Directory.SDCARD -> SDCARD_URI.updateString(uri.toString())
            }
        }

        fun getVersionReport(): String {
            val versionCode =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }
            val release =
                if (Build.VERSION.SDK_INT >= 30) Build.VERSION.RELEASE_OR_CODENAME
                else Build.VERSION.RELEASE
            return "RIFT ${packageInfo.versionName} ($versionCode)\n" +
                "Android $release (API ${Build.VERSION.SDK_INT})\n" +
                "Supported ABIs: ${Build.SUPPORTED_ABIS.contentToString()}\n" +
                "yt-dlp version: ${YT_DLP_VERSION.getString()}"
        }

        fun isFDroidBuild(): Boolean = false

        fun isDebugBuild(): Boolean = BuildConfig.DEBUG

    }
}
