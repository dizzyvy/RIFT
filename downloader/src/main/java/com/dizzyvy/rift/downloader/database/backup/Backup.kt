package com.dizzyvy.rift.downloader.database.backup

import com.dizzyvy.rift.downloader.database.objects.CommandTemplate
import com.dizzyvy.rift.downloader.database.objects.DownloadedVideoInfo
import com.dizzyvy.rift.downloader.database.objects.OptionShortcut
import kotlinx.serialization.Serializable

@Serializable
data class Backup(
    val templates: List<CommandTemplate>? = null,
    val shortcuts: List<OptionShortcut>? = null,
    val downloadHistory: List<DownloadedVideoInfo>? = null,
)
