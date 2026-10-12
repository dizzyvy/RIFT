package com.dizzyvy.rift.downloader.ui.page.settings.troubleshooting

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dizzyvy.rift.downloader.DownloaderRuntime
import com.dizzyvy.rift.downloader.R
import com.dizzyvy.rift.downloader.ui.common.Route
import com.dizzyvy.rift.downloader.ui.common.booleanState
import com.dizzyvy.rift.downloader.ui.component.PreferenceInfo
import com.dizzyvy.rift.downloader.ui.component.PreferenceItem
import com.dizzyvy.rift.downloader.ui.component.PreferenceSubtitle
import com.dizzyvy.rift.downloader.ui.component.PreferenceSwitch
import com.dizzyvy.rift.downloader.ui.page.settings.BasePreferencePage
import com.dizzyvy.rift.downloader.ui.page.settings.general.YtdlpUpdateChannelDialog
import com.dizzyvy.rift.downloader.util.PreferenceUtil.getString
import com.dizzyvy.rift.downloader.util.PreferenceUtil.updateBoolean
import com.dizzyvy.rift.downloader.util.RESTRICT_FILENAMES
import com.dizzyvy.rift.downloader.util.UpdateUtil
import com.dizzyvy.rift.downloader.util.YT_DLP_VERSION
import com.dizzyvy.rift.downloader.util.makeToast
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TroubleShootingPage(
    modifier: Modifier = Modifier,
    onNavigateTo: (String) -> Unit,
    onBack: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    BasePreferencePage(
        modifier = modifier,
        title = stringResource(R.string.trouble_shooting),
        onBack = onBack,
    ) {
        LazyColumn(contentPadding = it) {
            item {
                OutlinedCard(modifier = Modifier.padding(16.dp)) {
                    PreferenceInfo(
                        modifier = Modifier,
                        text = stringResource(R.string.issue_tracker_hint),
                    )
                    val knownIssueUrl = "https://github.com/dizzyvy/RIFT/issues"
                    PreferenceItem(
                        title = "Downloader issue tracker",
                        description = null,
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                        onClick = { uriHandler.openUri(knownIssueUrl) },
                    )

                    val knownIssueUrlYtdlp = "https://github.com/yt-dlp/yt-dlp/issues/3766"
                    PreferenceItem(
                        title = "yt-dlp Issue Tracker",
                        description = null,
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                        onClick = { uriHandler.openUri(knownIssueUrlYtdlp) },
                    )

                    Spacer(Modifier.height(8.dp))
                }
            }
            item { PreferenceSubtitle(text = stringResource(R.string.update)) }
            item {
                var isUpdating by remember { mutableStateOf(false) }
                var showYtdlpDialog by remember { mutableStateOf(false) }

                var ytdlpVersion by remember {
                    mutableStateOf(
                        YoutubeDL.getInstance().version(context.applicationContext)
                            ?: context.getString(R.string.ytdlp_update)
                    )
                }
                PreferenceItem(
                    title = stringResource(id = R.string.ytdlp_update_action),
                    description = ytdlpVersion,
                    leadingIcon = {
                        if (isUpdating) {
                            CircularProgressIndicator(
                                modifier =
                                    Modifier.padding(start = 8.dp, end = 16.dp)
                                        .size(24.dp)
                                        .padding(2.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Update,
                                contentDescription = null,
                                modifier = Modifier.padding(start = 8.dp, end = 16.dp).size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            runCatching {
                                    isUpdating = true
                                    UpdateUtil.updateYtDlp()
                                    ytdlpVersion = YT_DLP_VERSION.getString()
                                }
                                .onFailure { th ->
                                    th.printStackTrace()
                                    withContext(Dispatchers.Main) {
                                        context.makeToast(
                                            DownloaderRuntime.context.getString(R.string.yt_dlp_update_fail)
                                        )
                                    }
                                }
                                .onSuccess {
                                    withContext(Dispatchers.Main) {
                                        context.makeToast(
                                            context.getString(R.string.yt_dlp_up_to_date) +
                                                " (${YT_DLP_VERSION.getString()})"
                                        )
                                    }
                                }
                            isUpdating = false
                        }
                    },
                    onClickLabel = stringResource(id = R.string.update),
                    trailingIcon = {
                        IconButton(onClick = { showYtdlpDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(id = R.string.open_settings),
                            )
                        }
                    },
                )
                if (showYtdlpDialog) {
                    YtdlpUpdateChannelDialog(onDismissRequest = { showYtdlpDialog = false })
                }
            }

            item { PreferenceSubtitle(text = stringResource(R.string.network)) }
            item {
                PreferenceItem(
                    title = stringResource(R.string.cookies),
                    description = stringResource(R.string.cookies_desc),
                    icon = Icons.Outlined.Cookie,
                    onClick = { onNavigateTo(Route.COOKIE_PROFILE) },
                )
            }
            item { PreferenceSubtitle(text = stringResource(R.string.download_directory)) }
            item {
                var restrictFilenames by RESTRICT_FILENAMES.booleanState
                PreferenceSwitch(
                    title = stringResource(id = R.string.restrict_filenames),
                    icon = Icons.Outlined.Spellcheck,
                    description = stringResource(id = R.string.restrict_filenames_desc),
                    isChecked = restrictFilenames,
                ) {
                    restrictFilenames = !restrictFilenames
                    RESTRICT_FILENAMES.updateBoolean(restrictFilenames)
                }
            }
        }
    }
}
