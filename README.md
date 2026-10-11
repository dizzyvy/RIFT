# RIFT

RIFT is a local Android music player with a bright, click-wheel-era portable-player feel. It scans audio on the phone and removable storage, then plays it through Android's media session so lock-screen controls and Bluetooth/headset buttons work.

The launcher uses the RIFT Queen artwork. The Android application ID remains stable so this rebrand can update existing installations without resetting their app data.

## **WORK IN PROGRESS — NOT RELEASE READY**

Work has continued beyond the original checkpoint. Phase 3 UI/UX, Phase 4 search, the main Phase 5 performance work, Phase 6 backup integrity, and Phase 7 correctness updates have been implemented and committed locally. Phase 5 is not fully complete: the planned `LibraryScreen.kt` component split remains outstanding. Phase 8 tests, CI integration, and documentation are in progress. The app version remains **0.12.0**.

No Gradle build or test has been run for this work. Do not assume or claim that the app compiles or that tests pass. The recent phase commits are local and have not been pushed.

### Work since the original checkpoint

The work includes duplicate grouping and selection improvements, normalized/debounced search, off-main-thread library and queue processing, deferred duplicate computation, backup format v3 with v1/v2 compatibility, queue duration metadata, and an explanation of Shuffle's 20-minute limit. The files changed since the original checkpoint are:

- `.github/workflows/android-build.yml`
- `app/build.gradle.kts`
- `app/src/main/java/com/dizzyvy/rift/music/backup/LibraryBackup.kt`
- `app/src/main/java/com/dizzyvy/rift/music/library/LibraryBrowseItem.kt`
- `app/src/main/java/com/dizzyvy/rift/music/library/PlaylistStore.kt`
- `app/src/main/java/com/dizzyvy/rift/music/playback/Media3PlaybackController.kt`
- `app/src/main/java/com/dizzyvy/rift/music/playback/PlaybackService.kt`
- `app/src/main/java/com/dizzyvy/rift/ui/RiftApp.kt`
- `app/src/main/java/com/dizzyvy/rift/ui/library/LibraryScreen.kt`
- `app/src/main/java/com/dizzyvy/rift/ui/library/LibraryViewModel.kt`
- `app/src/main/java/com/dizzyvy/rift/ui/player/NowPlayingScreen.kt`
- `app/src/test/java/com/dizzyvy/rift/music/backup/LibraryBackupCodecTest.kt` (new)
- `app/src/test/java/com/dizzyvy/rift/music/library/LibraryRulesTest.kt` (new)
- `app/src/test/java/com/dizzyvy/rift/music/library/M3uPlaylistFormatTest.kt` (new)
- `gradle/libs.versions.toml`
- `README.md`
- `AGENTS.md`

The latest Settings, fast-scroller, and folder changes have been inspected with editor diagnostics, but have not been exercised in a running app. No release verification has been performed.

### Remaining work and continuation

Finish the planned Phase 5 `LibraryScreen.kt` split and Phase 8 coverage, CI, and documentation, preserving RIFT's visual identity and protecting existing user data.

**Immediate next steps:**

1. Run the configured `testDebugUnitTest` and `assembleDebug` CI workflow and resolve any failures; these checks have not been run locally.
2. Complete the `LibraryScreen.kt` split in a focused change, preserving existing behavior.
3. Expand the tests for any remaining acceptance criteria, then review the full diff and CI results before considering the work complete.
4. Keep version `0.12.0`, application/database identity, and existing user data unchanged; do not treat this branch as release-ready before verification.

## Requirements

- Android Studio with JDK 17
- Android SDK Platform 37 and Build Tools installed through SDK Manager
- Android 7.0 (API 24) or newer; target SDK 36

## Build a debug APK

Open the project in Android Studio and allow Gradle sync to install the configured Gradle and Android components. Then run:

```sh
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Run unit tests

Run the local JVM unit tests with:

```sh
./gradlew testDebugUnitTest
```

The Android CI workflow runs unit tests and assembles the debug APK for pushes and pull requests. No Gradle task was run while preparing this work.

## Features

- Scans device and SD card audio through MediaStore
- Shows a local library cache immediately and incrementally refreshes it when MediaStore changes
- Browses Songs, Artists, Albums, Genres, Years, Folders, Duplicates, and app-managed playlists
- Supports playlist import/export, favorites, play history, hidden folders, and artist-name aliases
- Exports playlists and user library settings in a versioned backup; media and artwork caches are not included
- Reads embedded album art from local audio files, with a colorful gradient fallback when art is missing or unreadable
- Caches album-art thumbnails in the app database for faster offline browsing
- Searches normalized track metadata and file names
- Plays tracks in the background with a Media3 session, system notification, lock-screen controls, and audio focus handling
- Remembers the playback queue, current track and position, shuffle, and repeat mode between launches
- Provides a click wheel with tap controls, rotation seeking, haptic ticks, and an A–Z library index
- Lets you select, remove, and reorder tracks in the playback queue; Shuffle all skips tracks longer than 20 minutes
- Uses Android's audio permission prompt and requests access only when the library is opened

Playback formats depend on the codecs available on the phone. Android's built-in media decoders cover common MP3, AAC/M4A, Ogg Vorbis, WAV, and FLAC files; a particular encoding may still be unsupported by the device.

## Install on a phone

Build `app/build/outputs/apk/debug/app-debug.apk`, copy it to the phone, open it in My Files, and approve Android's install prompt if shown. The debug APK is signed for development installs and is not a Play Store release.

## Historical debug APKs

The APKs in `releases/` are historical development builds and are not current releases. The current branch is work in progress.
