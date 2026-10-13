package com.dizzyvy.rift.downloader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import com.dizzyvy.rift.downloader.download.DownloaderV2
import com.dizzyvy.rift.downloader.ui.common.LocalDarkTheme
import com.dizzyvy.rift.downloader.ui.common.LocalFixedColorRoles
import com.dizzyvy.rift.downloader.ui.common.LocalWindowWidthState
import com.dizzyvy.rift.downloader.ui.page.command.TaskListPage
import com.dizzyvy.rift.downloader.ui.page.command.TaskLogPage
import com.dizzyvy.rift.downloader.ui.page.downloadv2.DownloadPageV2
import com.dizzyvy.rift.downloader.ui.page.downloadv2.configure.DownloadDialogViewModel
import com.dizzyvy.rift.downloader.ui.page.settings.command.TemplateEditPage
import com.dizzyvy.rift.downloader.ui.page.settings.command.TemplateListPage
import com.dizzyvy.rift.downloader.ui.page.settings.directory.DownloadDirectoryPreferences
import com.dizzyvy.rift.downloader.ui.page.settings.format.DownloadFormatPreferences
import com.dizzyvy.rift.downloader.ui.page.settings.format.SubtitlePreference
import com.dizzyvy.rift.downloader.ui.page.settings.general.GeneralDownloadPreferences
import com.dizzyvy.rift.downloader.ui.page.settings.network.CookieProfilePage
import com.dizzyvy.rift.downloader.ui.page.settings.network.CookiesViewModel
import com.dizzyvy.rift.downloader.ui.page.settings.network.NetworkPreferences
import com.dizzyvy.rift.downloader.ui.page.settings.network.WebViewPage
import com.dizzyvy.rift.downloader.ui.page.settings.troubleshooting.TroubleShootingPage
import com.dizzyvy.rift.downloader.ui.page.videolist.VideoListPage
import com.dizzyvy.rift.downloader.ui.theme.FixedColorRoles
import com.dizzyvy.rift.downloader.util.DarkThemePreference
import com.dizzyvy.rift.downloader.util.findSharedDownloadUrls
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private object DownloaderRoute {
    const val DOWNLOADS = "rift-downloads"
    const val TASKS = "rift-tasks"
    const val FILES = "rift-files"
    const val SETTINGS = "rift-settings"
    const val GENERAL = "rift-general"
    const val DIRECTORY = "rift-directory"
    const val FORMAT = "rift-format"
    const val SUBTITLES = "rift-subtitles"
    const val NETWORK = "rift-network"
    const val COOKIES = "rift-cookies"
    const val COOKIE_WEB = "rift-cookie-web"
    const val TEMPLATES = "rift-templates"
    const val TEMPLATE_EDIT = "rift-template-edit/{templateId}"
    const val TASK_LOG = "rift-task-log/{taskId}"
    const val TROUBLESHOOTING = "rift-troubleshooting"
}

@Composable
fun RiftDownloaderFeature(
    themeMode: String,
    startUrl: String?,
    onStartUrlHandled: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val initialization by DownloaderRuntime.initializationState.collectAsStateWithLifecycle()
    val widthClass = when {
        LocalConfiguration.current.screenWidthDp < 600 -> WindowWidthSizeClass.Compact
        LocalConfiguration.current.screenWidthDp < 840 -> WindowWidthSizeClass.Medium
        else -> WindowWidthSizeClass.Expanded
    }
    val colorScheme = MaterialTheme.colorScheme
    val fixedColors =
        remember(colorScheme) {
            FixedColorRoles(
                primaryFixed = colorScheme.primaryContainer,
                primaryFixedDim = colorScheme.primary,
                onPrimaryFixed = colorScheme.onPrimaryContainer,
                onPrimaryFixedVariant = colorScheme.onPrimary,
                secondaryFixed = colorScheme.secondaryContainer,
                secondaryFixedDim = colorScheme.secondary,
                onSecondaryFixed = colorScheme.onSecondaryContainer,
                onSecondaryFixedVariant = colorScheme.onSecondary,
                tertiaryFixed = colorScheme.tertiaryContainer,
                tertiaryFixedDim = colorScheme.tertiary,
                onTertiaryFixed = colorScheme.onTertiaryContainer,
                onTertiaryFixedVariant = colorScheme.onTertiary,
            )
        }

    CompositionLocalProvider(
        LocalDarkTheme provides
            DarkThemePreference(
                darkThemeValue =
                    if (themeMode.equals("dark", ignoreCase = true)) DarkThemePreference.ON
                    else if (themeMode.equals("light", ignoreCase = true)) DarkThemePreference.OFF
                    else DarkThemePreference.FOLLOW_SYSTEM,
            ),
        LocalFixedColorRoles provides fixedColors,
        LocalWindowWidthState provides widthClass,
    ) {
        LaunchedEffect(context) { DownloaderRuntime.initialize(context.applicationContext) }
        when (initialization) {
            DownloaderInitialization.NotStarted,
            DownloaderInitialization.Preparing -> InitializationScreen(onClose)
            is DownloaderInitialization.Failed ->
                InitializationScreen(
                    onClose = onClose,
                    error = (initialization as DownloaderInitialization.Failed).message,
                    onRetry = { DownloaderRuntime.initialize(context.applicationContext) },
                )
            DownloaderInitialization.Ready -> {
                val dialogViewModel: DownloadDialogViewModel = koinViewModel()
                val downloader: DownloaderV2 = koinInject()
                val cookiesViewModel: CookiesViewModel = koinViewModel()
                DownloaderNavigation(
                    dialogViewModel = dialogViewModel,
                    downloader = downloader,
                    cookiesViewModel = cookiesViewModel,
                    startUrl = startUrl,
                    onStartUrlHandled = onStartUrlHandled,
                    onClose = onClose,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InitializationScreen(
    onClose: () -> Unit,
    error: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloader") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close downloader")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (error == null) {
                CircularProgressIndicator()
                Text("Preparing downloader…", modifier = Modifier.padding(top = 16.dp))
            } else {
                Text(error, color = MaterialTheme.colorScheme.error)
                Button(onClick = { onRetry?.invoke() }, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Retry")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloaderNavigation(
    dialogViewModel: DownloadDialogViewModel,
    downloader: DownloaderV2,
    cookiesViewModel: CookiesViewModel,
    startUrl: String?,
    onStartUrlHandled: () -> Unit,
    onClose: () -> Unit,
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    LaunchedEffect(startUrl) {
        if (!startUrl.isNullOrBlank()) {
            val urls = findSharedDownloadUrls(startUrl)
            if (urls.isNotEmpty()) {
                navController.navigate(DownloaderRoute.DOWNLOADS) {
                    launchSingleTop = true
                    popUpTo(DownloaderRoute.DOWNLOADS) { inclusive = false }
                }
                dialogViewModel.postAction(
                    DownloadDialogViewModel.Action.ShowSheet(urlList = urls),
                )
            }
            onStartUrlHandled()
        }
    }

    BackHandler(enabled = currentRoute == DownloaderRoute.DOWNLOADS) { onClose() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentRoute == DownloaderRoute.DOWNLOADS,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = "Downloads",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(24.dp),
                )
                DrawerDestination(
                    label = "Download queue",
                    icon = Icons.Filled.Download,
                    selected = currentRoute == DownloaderRoute.DOWNLOADS,
                ) {
                    scope.launch { drawerState.close() }
                    navController.navigate(DownloaderRoute.DOWNLOADS) {
                        launchSingleTop = true
                    }
                }
                DrawerDestination(
                    label = "Tasks",
                    icon = Icons.Filled.Terminal,
                    selected = currentRoute == DownloaderRoute.TASKS,
                ) {
                    scope.launch { drawerState.close() }
                    navController.navigate(DownloaderRoute.TASKS) { launchSingleTop = true }
                }
                DrawerDestination(
                    label = "Downloaded files",
                    icon = Icons.Filled.Folder,
                    selected = currentRoute == DownloaderRoute.FILES,
                ) {
                    scope.launch { drawerState.close() }
                    navController.navigate(DownloaderRoute.FILES) { launchSingleTop = true }
                }
                DrawerDestination(
                    label = "Settings",
                    icon = Icons.Filled.Settings,
                    selected = currentRoute == DownloaderRoute.SETTINGS,
                ) {
                    scope.launch { drawerState.close() }
                    navController.navigate(DownloaderRoute.SETTINGS) { launchSingleTop = true }
                }
                NavigationDrawerItem(
                    label = { Text("Return to RIFT") },
                    selected = false,
                    icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
                    onClick = onClose,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        },
    ) {
        NavHost(navController = navController, startDestination = DownloaderRoute.DOWNLOADS) {
            composable(DownloaderRoute.DOWNLOADS) {
                DownloadPageV2(
                    dialogViewModel = dialogViewModel,
                    downloader = downloader,
                    onMenuOpen = { scope.launch { drawerState.open() } },
                )
            }
            composable(DownloaderRoute.TASKS) {
                TaskListPage(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { id ->
                        navController.navigate("${DownloaderRoute.TASK_LOG.substringBefore("/{")}/$id")
                    },
                )
            }
            composable(
                route = "${DownloaderRoute.TASK_LOG.substringBefore("/{")}/{taskId}",
                arguments = listOf(navArgument("taskId") { type = NavType.IntType }),
            ) { entry ->
                TaskLogPage(
                    onNavigateBack = { navController.popBackStack() },
                    taskHashCode = entry.arguments?.getInt("taskId") ?: return@composable,
                )
            }
            composable(DownloaderRoute.FILES) {
                VideoListPage(onNavigateBack = { navController.popBackStack() })
            }
            composable(DownloaderRoute.SETTINGS) {
                DownloaderSettings(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigate = { route -> navController.navigate(route) },
                )
            }
            composable(DownloaderRoute.GENERAL) {
                GeneralDownloadPreferences(
                    onNavigateBack = { navController.popBackStack() },
                    navigateToTemplate = { navController.navigate(DownloaderRoute.TEMPLATES) },
                )
            }
            composable(DownloaderRoute.DIRECTORY) {
                DownloadDirectoryPreferences(onNavigateBack = { navController.popBackStack() })
            }
            composable(DownloaderRoute.FORMAT) {
                DownloadFormatPreferences(
                    onNavigateBack = { navController.popBackStack() },
                    navigateToSubtitlePage = { navController.navigate(DownloaderRoute.SUBTITLES) },
                )
            }
            composable(DownloaderRoute.SUBTITLES) {
                SubtitlePreference(onNavigateBack = { navController.popBackStack() })
            }
            composable(DownloaderRoute.NETWORK) {
                NetworkPreferences(
                    navigateToCookieProfilePage = { navController.navigate(DownloaderRoute.COOKIES) },
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable(DownloaderRoute.COOKIES) {
                CookieProfilePage(
                    cookiesViewModel = cookiesViewModel,
                    navigateToCookieGeneratorPage = {
                        navController.navigate(DownloaderRoute.COOKIE_WEB)
                    },
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable(DownloaderRoute.COOKIE_WEB) {
                WebViewPage(
                    cookiesViewModel = cookiesViewModel,
                    onDismissRequest = { navController.popBackStack() },
                )
            }
            composable(DownloaderRoute.TEMPLATES) {
                TemplateListPage(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEditPage = { id ->
                        navController.navigate("${DownloaderRoute.TEMPLATE_EDIT.substringBefore("/{")}/$id")
                    },
                )
            }
            composable(
                route = "${DownloaderRoute.TEMPLATE_EDIT.substringBefore("/{")}/{templateId}",
                arguments = listOf(navArgument("templateId") { type = NavType.IntType }),
            ) { entry ->
                TemplateEditPage(
                    onDismissRequest = { navController.popBackStack() },
                    templateId = entry.arguments?.getInt("templateId") ?: return@composable,
                )
            }
            composable(DownloaderRoute.TROUBLESHOOTING) {
                TroubleShootingPage(
                    onNavigateTo = { route ->
                        if (route == com.dizzyvy.rift.downloader.ui.common.Route.COOKIE_PROFILE) {
                            navController.navigate(DownloaderRoute.COOKIES)
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun DrawerDestination(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(label) },
        icon = { Icon(icon, contentDescription = null) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloaderSettings(onNavigateBack: () -> Unit, onNavigate: (String) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloader settings") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item { SettingsItem("General and yt-dlp", "Formats, history, updates", Icons.Filled.Settings) { onNavigate(DownloaderRoute.GENERAL) } }
            item { SettingsItem("Download folders", "Choose where completed downloads are copied", Icons.Filled.Folder) { onNavigate(DownloaderRoute.DIRECTORY) } }
            item { SettingsItem("Formats and subtitles", "Audio, video, and subtitle options", Icons.Filled.Download) { onNavigate(DownloaderRoute.FORMAT) } }
            item { SettingsItem("Network and cookies", "Proxy, cookies, and connection settings", Icons.Filled.MoreVert) { onNavigate(DownloaderRoute.NETWORK) } }
            item { SettingsItem("Command templates", "Manage custom yt-dlp templates", Icons.Filled.Terminal) { onNavigate(DownloaderRoute.TEMPLATES) } }
            item { SettingsItem("Troubleshooting", "Diagnostics and downloader information", Icons.Filled.History) { onNavigate(DownloaderRoute.TROUBLESHOOTING) } }
        }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
        tonalElevation = 0.dp,
    )
    HorizontalDivider()
}
