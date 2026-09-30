package com.dizzyvy.sjmusicapp

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
import androidx.core.content.ContextCompat
import com.dizzyvy.sjmusicapp.music.library.MediaStoreAudioLibraryRepository
import com.dizzyvy.sjmusicapp.music.playback.Media3PlaybackController
import com.dizzyvy.sjmusicapp.ui.SJMusicApp

class MainActivity : ComponentActivity() {
    private lateinit var repository: MediaStoreAudioLibraryRepository
    private lateinit var playbackController: Media3PlaybackController
    private val audioPermissionState = mutableStateOf(false)
    private var requestedAudioPermissionBefore = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = MediaStoreAudioLibraryRepository(applicationContext)
        playbackController = Media3PlaybackController(applicationContext)
        requestedAudioPermissionBefore = getPreferences(MODE_PRIVATE)
            .getBoolean(KEY_REQUESTED_AUDIO_PERMISSION, false)
        audioPermissionState.value = hasAudioPermission()
        setContent {
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted -> audioPermissionState.value = granted }

            SJMusicApp(
                repository = repository,
                playbackController = playbackController,
                hasAudioPermission = audioPermissionState.value,
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
    }
}
