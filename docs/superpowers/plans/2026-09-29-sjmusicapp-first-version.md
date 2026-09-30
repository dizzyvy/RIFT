# SJMusicApp First Version Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a simple Android music player for Steve's Galaxy A15 that browses and plays local music from built-in and SD-card storage.

**Architecture:** A single Android app uses Jetpack Compose for the library and Now Playing screens. A MediaStore repository supplies track metadata from every available storage volume, while a Media3 playback service owns the player, queue, and system media session.

**Tech Stack:** Kotlin 2.4.20, Android Gradle Plugin 9.1.1, Gradle 9.3.1, JDK 17, Jetpack Compose BOM 2026.09.00, AndroidX Media3 1.11.1, AndroidX Lifecycle 2.11.0.

**Spec:** [`docs/superpowers/specs/2026-09-29-sjmusicapp-design.md`](../specs/2026-09-29-sjmusicapp-design.md)

## Global Constraints

- Target Android 16 (API level 36) on the Samsung Galaxy A15 (SM-A156W), running One UI 8.5.
- Use `applicationId` and Kotlin package `com.dizzyvy.sjmusicapp`.
- Set `minSdk` to 24 because current AndroidX releases default to API 24; the target device runs Android 16.
- Set `compileSdk` to 37 for the September 2026 Compose BOM, which requires compile SDK 37; keep `targetSdk` at 36.
- Query all available MediaStore volume names on API 29+, and use the primary external audio collection on older supported versions.
- Ask for `READ_MEDIA_AUDIO` on API 33+ and `READ_EXTERNAL_STORAGE` below API 33. Do not request broad file-management access.
- Use Media3 ExoPlayer and MediaSessionService for background playback and system controls.
- Handle MP3, AAC/M4A, Ogg Vorbis, Opus, WAV, and FLAC when the Android device has a compatible decoder; show a recoverable error for unsupported files.
- Treat media as read-only. Do not move, edit, or delete the user's music files.
- Keep playlists, favorites, ratings, and listening history out of this version.
- Use original artwork and interface details; do not copy Apple logos or proprietary art.
- Commit each completed task locally on the implementation branch; publish the branch to GitHub after the user reviews the finished app changes.

## File Structure

- `settings.gradle.kts` — plugin and dependency repositories; include `:app`.
- `.gitignore` — generated macOS and Gradle files, local SDK settings, and build outputs.
- `build.gradle.kts` — root plugin aliases.
- `gradle/libs.versions.toml` — pinned tool and library versions.
- `gradle/wrapper/gradle-wrapper.properties` — Gradle 9.3.1 distribution.
- `app/build.gradle.kts` — Android app ID, SDK values, Compose, and dependencies.
- `app/src/main/AndroidManifest.xml` — permissions, launcher activity, and media playback service.
- `app/src/main/java/com/dizzyvy/sjmusicapp/MainActivity.kt` — app entry, dependency creation, and permission result wiring.
- `app/src/main/java/com/dizzyvy/sjmusicapp/music/model/AudioTrack.kt` — immutable track metadata model.
- `app/src/main/java/com/dizzyvy/sjmusicapp/music/library/AudioLibraryRepository.kt` — library interface.
- `app/src/main/java/com/dizzyvy/sjmusicapp/music/library/MediaStoreAudioLibraryRepository.kt` — volume-aware MediaStore query.
- `app/src/main/java/com/dizzyvy/sjmusicapp/music/playback/PlaybackService.kt` — ExoPlayer and MediaSession lifecycle.
- `app/src/main/java/com/dizzyvy/sjmusicapp/music/playback/PlaybackController.kt` — UI-facing playback state and commands.
- `app/src/main/java/com/dizzyvy/sjmusicapp/music/playback/Media3PlaybackController.kt` — MediaController connection and command adapter.
- `app/src/main/java/com/dizzyvy/sjmusicapp/ui/SJMusicApp.kt` — top-level screen selection and shared app state.
- `app/src/main/java/com/dizzyvy/sjmusicapp/ui/library/LibraryViewModel.kt` — permission-aware loading and search state.
- `app/src/main/java/com/dizzyvy/sjmusicapp/ui/library/LibraryScreen.kt` — colorful song list, search, and loading/empty/error states.
- `app/src/main/java/com/dizzyvy/sjmusicapp/ui/nowplaying/NowPlayingScreen.kt` — artwork, metadata, wheel-inspired controls, and queue entry point.
- `app/src/main/java/com/dizzyvy/sjmusicapp/ui/theme/Color.kt` and `Theme.kt` — original bright iPod-inspired palette and Compose theme.
- `app/src/main/res/values/strings.xml` — user-facing permission rationale and playback messages.
- `README.md` — project purpose, build prerequisites, and local APK build command.

## Review Focus

- Permission denied or revoked: show a clear rationale and retry path; keep the app usable without crashing.
- SD card removed during scan or playback: skip unavailable items and present a recoverable message.
- Missing metadata or no indexed tracks: use readable filename fallbacks and a useful empty-library state.
- Corrupt or device-unsupported audio: show a concise playback error and allow the user to continue to another track.
- Backgrounding, audio focus loss, or headset controls: keep playback state coherent and expose supported system commands.

---

### Task 1: Create the Android project shell

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradle/wrapper/gradle-wrapper.jar`
- Create: `gradlew`
- Create: `gradlew.bat`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/dizzyvy/sjmusicapp/MainActivity.kt`
- Create: `README.md`

**Interfaces:** Produces installable app ID `com.dizzyvy.sjmusicapp` and a Compose `MainActivity` that opens `SJMusicApp`.

- [ ] **Step 1: Add the pinned Gradle and Android plugin setup.** Use AGP 9.1.1, Gradle 9.3.1, JDK 17, Kotlin 2.4.20, `compileSdk = 37`, `targetSdk = 36`, and `minSdk = 24`. Set `android.builtInKotlin=false` and `android.newDsl=false` for compatibility with the Kotlin Android plugin, apply Kotlin Android and Compose compiler plugins at 2.4.20, and enable Compose.
- [ ] **Step 2: Add `.gitignore`.** Ignore `.DS_Store`, `.gradle/`, `local.properties`, and `**/build/` so machine-local files and generated APKs do not enter Git history.
- [ ] **Step 3: Add the app entry point and README.** `MainActivity.onCreate` calls `setContent { Text("SJ Music") }` as the temporary screen. Task 4 replaces the placeholder with `SJMusicApp(repository, playbackController)`.
- [ ] **Step 4: Build the empty app shell.** Run `./gradlew assembleDebug` and confirm `app/build/outputs/apk/debug/app-debug.apk` is produced.
- [ ] **Step 5: Commit the project shell.** Commit only the Android scaffold and `.gitignore` as `build: scaffold Android app`.

### Task 2: Read the local audio library

**Files:**
- Create: `app/src/main/java/com/dizzyvy/sjmusicapp/music/model/AudioTrack.kt`
- Create: `app/src/main/java/com/dizzyvy/sjmusicapp/music/library/AudioLibraryRepository.kt`
- Create: `app/src/main/java/com/dizzyvy/sjmusicapp/music/library/MediaStoreAudioLibraryRepository.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces `data class AudioTrack(val id: Long, val uri: Uri, val title: String, val artist: String, val album: String, val durationMs: Long)`.
- Produces `interface AudioLibraryRepository { suspend fun loadTracks(): List<AudioTrack> }`.
- `MediaStoreAudioLibraryRepository(context: Context)` implements the repository and performs queries on `Dispatchers.IO`.

- [ ] **Step 1: Add the track model and repository contract.** Store the content URI so later playback can open the item without assuming a filesystem path.
- [ ] **Step 2: Implement the MediaStore query.** On API 29+, enumerate `MediaStore.getExternalVolumeNames(context)` and query each volume's audio collection; on API 24–28, query `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI`. Return indexed audio rows sorted by title and de-duplicate by content URI. Fall back to the file name when the title is blank.
- [ ] **Step 3: Add versioned audio permissions and permission copy.** Declare `READ_MEDIA_AUDIO` for API 33+ and `READ_EXTERNAL_STORAGE` through API 32. Put the short reason in `strings.xml` and request permission only when the user opens the library.
- [ ] **Step 4: Build the app.** Run `./gradlew assembleDebug` and confirm the APK is produced.
- [ ] **Step 5: Commit the library layer.** Commit the model, repository, permission declarations, and strings as `feat: index local audio library`.

### Task 3: Add background playback and the queue

**Files:**
- Create: `app/src/main/java/com/dizzyvy/sjmusicapp/music/playback/PlaybackService.kt`
- Create: `app/src/main/java/com/dizzyvy/sjmusicapp/music/playback/PlaybackController.kt`
- Create: `app/src/main/java/com/dizzyvy/sjmusicapp/music/playback/Media3PlaybackController.kt`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces `data class PlaybackSnapshot(val queue: List<AudioTrack>, val currentIndex: Int, val currentTrack: AudioTrack?, val isPlaying: Boolean, val positionMs: Long, val durationMs: Long, val errorMessage: String?)`.
- Produces `interface PlaybackController { val snapshot: StateFlow<PlaybackSnapshot>; fun setQueue(tracks: List<AudioTrack>, startIndex: Int); fun playQueueItem(index: Int); fun playPause(); fun skipNext(); fun skipPrevious(); fun seekTo(positionMs: Long) }`.
- `Media3PlaybackController(context: Context)` implements the UI-facing interface and connects to `PlaybackService` with `MediaController`.

- [ ] **Step 1: Add the Media3 dependencies.** Pin all Media3 modules to 1.11.1 and include `media3-exoplayer`, `media3-session`, and `media3-common-ktx`.
- [ ] **Step 2: Implement `PlaybackService`.** Own one ExoPlayer and MediaSession; release both with the service lifecycle. Convert queued `AudioTrack` values into MediaItems using their content URIs and metadata. Let Media3 handle audio focus and system media notifications.
- [ ] **Step 3: Implement the controller adapter.** Connect/disconnect through a MediaController future; expose current item, playing status, duration, and position as `StateFlow<PlaybackSnapshot>`. Poll position once per second only while playing, and stop polling when paused or disconnected.
- [ ] **Step 4: Register the media playback service.** Declare `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK`; set `android:exported="true"` and `foregroundServiceType="mediaPlayback"`; add the `androidx.media3.session.MediaSessionService` and `android.media.browse.MediaBrowserService` intent actions.
- [ ] **Step 5: Build the app.** Run `./gradlew assembleDebug` and confirm the APK is produced.
- [ ] **Step 6: Commit background playback.** Commit the service, controller, dependencies, and manifest as `feat: add background media playback`.

### Task 4: Build the library screen and search

**Files:**
- Create: `app/src/main/java/com/dizzyvy/sjmusicapp/ui/SJMusicApp.kt`
- Create: `app/src/main/java/com/dizzyvy/sjmusicapp/ui/library/LibraryViewModel.kt`
- Create: `app/src/main/java/com/dizzyvy/sjmusicapp/ui/library/LibraryScreen.kt`
- Create: `app/src/main/java/com/dizzyvy/sjmusicapp/ui/theme/Color.kt`
- Create: `app/src/main/java/com/dizzyvy/sjmusicapp/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/dizzyvy/sjmusicapp/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/build.gradle.kts`

**Interfaces:** `LibraryViewModel(repository: AudioLibraryRepository, playbackController: PlaybackController)` exposes `StateFlow<LibraryUiState>`, `loadLibrary()`, `setSearchQuery(query: String)`, and `playTrack(index: Int)`. `LibraryUiState` contains `tracks`, `visibleTracks`, `searchQuery`, `isLoading`, `permissionRequired`, and `message`. The ViewModel includes a `ViewModelProvider.Factory` for those constructor dependencies.

- [ ] **Step 1: Add the colorful Compose theme.** Define original saturated accent colors, a light neutral background, rounded touch targets, and large readable type; do not use Apple logos or product imagery.
- [ ] **Step 2: Implement library state.** Load tracks through `AudioLibraryRepository`; filter `visibleTracks` case-insensitively on title, artist, and album; provide filename and “Unknown artist” fallbacks for missing metadata. `playTrack(index)` calls `setQueue(visibleTracks, index)` so queue order matches the visible search result.
- [ ] **Step 3: Implement `LibraryScreen`.** Render search, a scrollable song list, loading state, permission explanation/retry action, and an empty-library message. Selecting a row calls `playTrack(index)`.
- [ ] **Step 4: Connect the app shell.** `MainActivity` creates one repository and controller, passes them to `SJMusicApp(repository, playbackController)`, and releases the controller when the Activity is destroyed. Wire permission results into `loadLibrary()` and provide a `ViewModelProvider.Factory` to the library screen.
- [ ] **Step 5: Build the app.** Run `./gradlew assembleDebug` and confirm the APK is produced.
- [ ] **Step 6: Commit the library UI.** Commit the theme, library screen, view model, app shell, and strings as `feat: add searchable music library`.

### Task 5: Build Now Playing and queue controls

**Files:**
- Create: `app/src/main/java/com/dizzyvy/sjmusicapp/ui/player/NowPlayingScreen.kt`
- Modify: `app/src/main/java/com/dizzyvy/sjmusicapp/ui/SJMusicApp.kt`
- Modify: `app/src/main/java/com/dizzyvy/sjmusicapp/ui/library/LibraryScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:** `NowPlayingScreen(snapshot: PlaybackSnapshot, onPlayPause: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onSeek: (Long) -> Unit, onSelectQueueItem: (Int) -> Unit, onBack: () -> Unit)`. Render the queue from `snapshot.queue` and highlight `snapshot.currentIndex`.

- [ ] **Step 1: Add the Now Playing view.** Show track title, artist, album, a colorful original graphic, elapsed/remaining time, and a seek bar.
- [ ] **Step 2: Add playback controls.** Wire play/pause, previous, next, seek, and queue-item selection to the shared playback controller's matching method. Use an original click-wheel-inspired circular control layout with accessible touch targets.
- [ ] **Step 3: Connect navigation.** Open Now Playing from a selected library track and provide a clear return path to the song list.
- [ ] **Step 4: Build the app.** Run `./gradlew assembleDebug` and confirm the APK is produced.
- [ ] **Step 5: Commit Now Playing.** Commit the screen and navigation as `feat: add iPod-inspired now playing screen`.

### Task 6: Handle playback failures and package the first APK

**Files:**
- Modify: `app/src/main/java/com/dizzyvy/sjmusicapp/music/playback/PlaybackService.kt`
- Modify: `app/src/main/java/com/dizzyvy/sjmusicapp/music/playback/Media3PlaybackController.kt`
- Modify: `app/src/main/java/com/dizzyvy/sjmusicapp/ui/library/LibraryViewModel.kt`
- Modify: `app/src/main/java/com/dizzyvy/sjmusicapp/ui/SJMusicApp.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `README.md`

- [ ] **Step 1: Add recoverable storage and decoding messages.** Handle removed-volume query failures, revoked media permission, missing content URIs, and Media3 playback errors as UI state; allow moving to the next queued track after an item fails.
- [ ] **Step 2: Confirm system control wiring.** Ensure MediaSession commands update the same playback snapshot used by the Compose UI and that notification, lock-screen, and supported headset actions reach the service.
- [ ] **Step 3: Document the local build and install path.** Add the exact `./gradlew assembleDebug` command and output APK path to the README.
- [ ] **Step 4: Produce the debug APK.** Run `./gradlew assembleDebug`; expected output: `app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **Step 5: Commit recovery and build instructions.** Commit the error handling and README updates as `fix: handle unavailable audio tracks`.

## Version references

- [Android Gradle Plugin 9.1.1 compatibility](https://developer.android.com/build/releases/agp-9-1-0-release-notes)
- [Kotlin 2.4.20 compatibility](https://kotlinlang.org/docs/gradle-configure-project.html)
- [Jetpack Compose setup and September 2026 BOM](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)
- [Kotlin 2.4.20 release](https://kotlinlang.org/docs/whatsnew2420.html)
- [Media3 1.11.1 release and dependencies](https://developer.android.com/jetpack/androidx/releases/media3)
- [Android 16 SDK (API 36)](https://developer.android.com/about/versions/16/setup-sdk)
