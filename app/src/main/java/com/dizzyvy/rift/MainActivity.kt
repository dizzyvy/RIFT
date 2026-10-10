package com.dizzyvy.rift

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.dizzyvy.rift.music.library.MediaStoreAudioLibraryRepository
import com.dizzyvy.rift.music.library.SqlitePlaylistStore
import com.dizzyvy.rift.music.artwork.EmbeddedArtworkRepository
import com.dizzyvy.rift.music.playback.Media3PlaybackController
import com.dizzyvy.rift.ui.RiftApp

class MainActivity : ComponentActivity() {
    private lateinit var repository: MediaStoreAudioLibraryRepository
    private lateinit var playlistStore: SqlitePlaylistStore
    private lateinit var playbackController: Media3PlaybackController
    private lateinit var artworkRepository: EmbeddedArtworkRepository
    private val audioPermissionState = mutableStateOf(false)
    private val themeModeState = mutableStateOf("system")
    private val externalAudioUriState = mutableStateOf<Uri?>(null)
    private var requestedAudioPermissionBefore = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        externalAudioUriState.value = intent?.data?.takeIf { intent?.action == Intent.ACTION_VIEW }
        repository = MediaStoreAudioLibraryRepository(applicationContext)
        playlistStore = SqlitePlaylistStore(applicationContext)
        artworkRepository = EmbeddedArtworkRepository(applicationContext, playlistStore)
        playbackController = Media3PlaybackController(applicationContext, artworkRepository)
        requestedAudioPermissionBefore = getPreferences(MODE_PRIVATE)
            .getBoolean(KEY_REQUESTED_AUDIO_PERMISSION, false)
        audioPermissionState.value = hasAudioPermission()
        val preferences = getPreferences(MODE_PRIVATE)
        if (preferences.getInt(KEY_THEME_SCHEMA, 0) < THEME_SCHEMA_VERSION) {
            preferences.edit()
                .putInt(KEY_THEME_SCHEMA, THEME_SCHEMA_VERSION)
                .putString(KEY_THEME_MODE, "system")
                .remove("accent_name")
                .remove("text_color")
                .apply()
        }
        themeModeState.value = normalizeThemeMode(preferences.getString(KEY_THEME_MODE, "system"))
        val systemDarkTheme =
            (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        applySystemBars(isThemeDark(themeModeState.value, systemDarkTheme))
        setContent {
            val systemDarkTheme = isSystemInDarkTheme()
            LaunchedEffect(themeModeState.value, systemDarkTheme) {
                applySystemBars(isThemeDark(themeModeState.value, systemDarkTheme))
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted -> audioPermissionState.value = granted }

            RiftApp(
                repository = repository,
                playlistStore = playlistStore,
                playbackController = playbackController,
                artworkRepository = artworkRepository,
                hasAudioPermission = audioPermissionState.value,
                themeMode = themeModeState.value,
                externalAudioUri = externalAudioUriState.value,
                onExternalAudioHandled = { externalAudioUriState.value = null },
                onThemeModeChange = { value ->
                    val normalized = normalizeThemeMode(value)
                    themeModeState.value = normalized
                    preferences.edit().putString(KEY_THEME_MODE, normalized).apply()
                },
                onRequestPermission = {
                    val permission = requiredAudioPermission()
                    if (requestedAudioPermissionBefore && !shouldShowRequestPermissionRationale(permission)) {
                        startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", packageName, null),
                            ),
                        )
                    } else {
                        requestedAudioPermissionBefore = true
                        getPreferences(MODE_PRIVATE).edit()
                            .putBoolean(KEY_REQUESTED_AUDIO_PERMISSION, true)
                            .apply()
                        permissionLauncher.launch(permission)
                    }
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (::repository.isInitialized) {
            audioPermissionState.value = hasAudioPermission()
        }
    }

    override fun onDestroy() {
        if (::playbackController.isInitialized) playbackController.release()
        if (::playlistStore.isInitialized) playlistStore.close()
        super.onDestroy()
    }

    private fun requiredAudioPermission(): String = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    private fun hasAudioPermission(): Boolean = ContextCompat.checkSelfPermission(
        this,
        requiredAudioPermission(),
    ) == PackageManager.PERMISSION_GRANTED

    private fun isThemeDark(mode: String, isSystemDarkTheme: Boolean): Boolean = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemDarkTheme
    }

    private fun applySystemBars(isDark: Boolean) {
        val style = if (isDark) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    private fun normalizeThemeMode(value: String?): String = when (value?.lowercase()) {
        "light" -> "light"
        "dark" -> "dark"
        "system", "follow system" -> "system"
        else -> "system"
    }

    private companion object {
        const val KEY_REQUESTED_AUDIO_PERMISSION = "requested_audio_permission"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_THEME_SCHEMA = "theme_schema"
        const val THEME_SCHEMA_VERSION = 1
    }
}
