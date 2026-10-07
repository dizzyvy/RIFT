package com.dizzyvy.rift

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
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
    private val themeModeState = mutableStateOf("light")
    private val accentNameState = mutableStateOf("Coral")
    private val externalAudioUriState = mutableStateOf<Uri?>(null)
    private var requestedAudioPermissionBefore = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        externalAudioUriState.value = intent?.data?.takeIf { intent?.action == Intent.ACTION_VIEW }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        repository = MediaStoreAudioLibraryRepository(applicationContext)
        playlistStore = SqlitePlaylistStore(applicationContext)
        artworkRepository = EmbeddedArtworkRepository(applicationContext)
        playbackController = Media3PlaybackController(applicationContext, artworkRepository)
        requestedAudioPermissionBefore = getPreferences(MODE_PRIVATE)
            .getBoolean(KEY_REQUESTED_AUDIO_PERMISSION, false)
        audioPermissionState.value = hasAudioPermission()
        themeModeState.value = getPreferences(MODE_PRIVATE).getString(KEY_THEME_MODE, "light") ?: "light"
        accentNameState.value = getPreferences(MODE_PRIVATE).getString(KEY_ACCENT_NAME, "Coral") ?: "Coral"
        setContent {
            SideEffect {
                val lightSystemBars = themeModeState.value.equals("light", ignoreCase = true)
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = lightSystemBars
                    isAppearanceLightNavigationBars = lightSystemBars
                }
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
                accentName = accentNameState.value,
                externalAudioUri = externalAudioUriState.value,
                onExternalAudioHandled = { externalAudioUriState.value = null },
                onThemeModeChange = { value ->
                    themeModeState.value = value
                    getPreferences(MODE_PRIVATE).edit().putString(KEY_THEME_MODE, value).apply()
                },
                onAccentChange = { value ->
                    accentNameState.value = value
                    getPreferences(MODE_PRIVATE).edit().putString(KEY_ACCENT_NAME, value).apply()
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

    private companion object {
        const val KEY_REQUESTED_AUDIO_PERMISSION = "requested_audio_permission"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_ACCENT_NAME = "accent_name"
    }
}
