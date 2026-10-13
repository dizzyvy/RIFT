package com.dizzyvy.rift.downloader.download

import com.dizzyvy.rift.downloader.database.objects.CommandTemplate
import com.dizzyvy.rift.downloader.download.Task.TypeInfo
import com.dizzyvy.rift.downloader.download.Task.ViewState
import com.dizzyvy.rift.downloader.util.DownloadUtil
import com.dizzyvy.rift.downloader.util.DownloadTimingTrace
import com.dizzyvy.rift.downloader.util.Format
import com.dizzyvy.rift.downloader.util.VideoInfo
import com.dizzyvy.rift.downloader.util.toHttpsUrl
import kotlinx.coroutines.Job
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.roundToInt

private val TypeInfo.id: String
    get() =
        when (this) {
            is TypeInfo.CustomCommand -> "${template.id}_${template.name}"
            is TypeInfo.Playlist -> "$index"
            TypeInfo.URL -> ""
        }

private fun makeId(url: String, type: TypeInfo, preferences: DownloadUtil.DownloadPreferences): String =
    "${url}_${type.id}_${preferences.hashCode()}"

@Serializable
data class Task(
    val url: String,
    val type: TypeInfo = TypeInfo.URL,
    val preferences: DownloadUtil.DownloadPreferences,
    val id: String = makeId(url, type, preferences),
) : Comparable<Task> {

    internal var timingTrace: DownloadTimingTrace? = null

    val timeCreated: Long = System.currentTimeMillis()

    override fun compareTo(other: Task): Int {
        return timeCreated.compareTo(other.timeCreated)
    }

    @Serializable
    // Preserve stored discriminator values while source packages use the RIFT namespace.
    sealed interface TypeInfo {

        @Serializable
        @SerialName("com.junkfood.seal.download.Task.TypeInfo.Playlist")
        data class Playlist(val index: Int = 0) : TypeInfo

        @Serializable
        @SerialName("com.junkfood.seal.download.Task.TypeInfo.CustomCommand")
        data class CustomCommand(val template: CommandTemplate) : TypeInfo

        @Serializable
        @SerialName("com.junkfood.seal.download.Task.TypeInfo.URL")
        data object URL : TypeInfo
    }

    @Serializable
    data class State(
        val downloadState: DownloadState,
        val videoInfo: VideoInfo?,
        val viewState: ViewState,
    )

    @Serializable
    // Preserve stored discriminator values while source packages use the RIFT namespace.
    sealed interface DownloadState : Comparable<DownloadState> {

        interface Cancelable {
            val job: Job
            val taskId: String
            val action: RestartableAction
        }

        interface Restartable {
            val action: RestartableAction
        }

        @Serializable
        @SerialName("com.junkfood.seal.download.Task.DownloadState.Idle")
        data object Idle : DownloadState

        @Serializable
        @SerialName("com.junkfood.seal.download.Task.DownloadState.FetchingInfo")
        data class FetchingInfo(
            @Transient override val job: Job = Job(),
            override val taskId: String,
        ) : DownloadState, Cancelable {
            override val action: RestartableAction = RestartableAction.FetchInfo
        }

        @Serializable
        @SerialName("com.junkfood.seal.download.Task.DownloadState.ReadyWithInfo")
        data object ReadyWithInfo : DownloadState

        @Serializable
        @SerialName("com.junkfood.seal.download.Task.DownloadState.Running")
        data class Running(
            @Transient override val job: Job = Job(),
            override val taskId: String,
            val progress: Float = PROGRESS_INDETERMINATE,
            val progressText: String = "",
        ) : DownloadState, Cancelable {
            override val action: RestartableAction = RestartableAction.Download
        }

        @Serializable
        @SerialName("com.junkfood.seal.download.Task.DownloadState.Canceled")
        data class Canceled(override val action: RestartableAction, val progress: Float? = null) :
            DownloadState, Restartable

        @Serializable
        @SerialName("com.junkfood.seal.download.Task.DownloadState.Error")
        data class Error(
            @Transient val throwable: Throwable = Throwable(),
            override val action: RestartableAction,
        ) : DownloadState, Restartable

        @Serializable
        @SerialName("com.junkfood.seal.download.Task.DownloadState.Completed")
        data class Completed(val filePath: String?) : DownloadState

        override fun compareTo(other: DownloadState): Int {
            return ordinal - other.ordinal
        }

        private val ordinal: Int
            get() =
                when (this) {
                    is Canceled -> 4
                    is Error -> 5
                    is Completed -> 6
                    Idle -> 3
                    is FetchingInfo -> 2
                    ReadyWithInfo -> 1
                    is Running -> 0
                }
    }

    @Serializable
    // Preserve stored discriminator values while source packages use the RIFT namespace.
    sealed interface RestartableAction {
        @Serializable
        @SerialName("com.junkfood.seal.download.Task.RestartableAction.FetchInfo")
        data object FetchInfo : RestartableAction

        @Serializable
        @SerialName("com.junkfood.seal.download.Task.RestartableAction.Download")
        data object Download : RestartableAction
    }

    @Serializable
    data class ViewState(
        val url: String = "https://www.example.com",
        val title: String = "",
        val uploader: String = "",
        val extractorKey: String = "",
        val duration: Int = 0,
        val fileSizeApprox: Double = .0,
        val thumbnailUrl: String? = null,
        val videoFormats: List<Format>? = null,
        val audioOnlyFormats: List<Format>? = null,
    ) {
        companion object {
            fun fromVideoInfo(info: VideoInfo): ViewState {
                val formats =
                    info.requestedFormats
                        ?: info.requestedDownloads?.map { it.toFormat() }
                        ?: emptyList()

                val videoFormats = formats.filter { it.containsVideo() }
                val audioOnlyFormats = formats.filter { it.isAudioOnly() }

                return ViewState(
                    url = info.originalUrl.toString(),
                    title = info.title,
                    uploader = info.uploader ?: info.channel ?: info.uploaderId.toString(),
                    extractorKey = info.extractorKey,
                    duration = info.duration?.roundToInt() ?: 0,
                    thumbnailUrl = info.thumbnail.toHttpsUrl(),
                    fileSizeApprox = info.fileSize ?: info.fileSizeApprox ?: .0,
                    videoFormats = videoFormats,
                    audioOnlyFormats = audioOnlyFormats,
                )
            }
        }
    }

    companion object {
        private const val PROGRESS_INDETERMINATE = -1f
    }
}
