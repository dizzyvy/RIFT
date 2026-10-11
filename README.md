# RIFT

RIFT is a local Android music player with a bright, click-wheel-era portable-player feel. It scans audio on the phone and removable storage, then plays it through Android's media session so lock-screen controls and Bluetooth/headset buttons work.

The launcher uses the RIFT Queen artwork. The Android application ID remains stable so this rebrand can update existing installations without resetting their app data.

## **WORK IN PROGRESS — NOT RELEASE READY**

This work is stopped partway through **Phase 3 of 8: UI/UX polish**. The app version remains **0.12.0**. These changes are an incomplete checkpoint, not a release; the app has not been verified to compile.

### Current checkpoint

Work already touched Now Playing, the click wheel, library navigation, Settings, playlists, metadata cleanup, sorting, detail screens, the fast-scroller, and folders. The current Git diff includes:

- `app/build.gradle.kts`
- `app/src/main/java/com/dizzyvy/rift/music/library/LibraryBrowseItem.kt`
- `app/src/main/java/com/dizzyvy/rift/music/library/MediaStoreAudioLibraryRepository.kt`
- `app/src/main/java/com/dizzyvy/rift/music/library/PlaylistStore.kt`
- `app/src/main/java/com/dizzyvy/rift/music/library/TrackMetadataCleanup.kt`
- `app/src/main/java/com/dizzyvy/rift/music/model/AudioTrack.kt`
- `app/src/main/java/com/dizzyvy/rift/music/playback/Media3PlaybackController.kt`
- `app/src/main/java/com/dizzyvy/rift/ui/RiftApp.kt`
- `app/src/main/java/com/dizzyvy/rift/ui/components/AlbumArtwork.kt`
- `app/src/main/java/com/dizzyvy/rift/ui/format/TimeFormat.kt` (new)
- `app/src/main/java/com/dizzyvy/rift/ui/library/LibraryScreen.kt`
- `app/src/main/java/com/dizzyvy/rift/ui/library/LibraryViewModel.kt`
- `app/src/main/java/com/dizzyvy/rift/ui/player/ClickWheel.kt`
- `app/src/main/java/com/dizzyvy/rift/ui/player/NowPlayingScreen.kt`
- `gradle/libs.versions.toml`

The latest Settings, fast-scroller, and folder changes have **not been checked**. No Gradle builds or tests were run; do not assume or claim that the app compiles. No additional release verification has been performed.

### Remaining work and handoff

Finish Phase 3 first, preserving RIFT's warm visual identity and protecting existing user data. Then complete Phases 4–8, covering search, performance, architecture, backups, additional correctness fixes, tests, CI, and documentation. The detailed roadmap file `RIFT-v0.12.0-improvement-prompt.md` was not present in the repository when this checkpoint was prepared; consult it if it becomes available, together with `AGENTS.md` and any applicable subdirectory guidance.

**Immediate next steps for the next Copilot session:**

1. Check `git status --branch` and inspect the checkpoint commit and current diff before editing; preserve all existing work and keep version `0.12.0`.
2. Run editor diagnostics on the latest Settings, fast-scroller, and folder changes, then carefully review Phase 3 for incomplete or inconsistent UI wiring. Do not claim a successful compile without actually verifying it under the project constraints.
3. Complete the remaining Phase 3 items from the detailed roadmap, keeping RIFT's existing visual identity and ensuring database migrations preserve playlists, favorites, play history, hidden folders, aliases, and playback state.
4. Continue Phases 4–8 in order, including their required implementation, tests, CI, documentation, and final whole-diff review. Follow the roadmap and repository instructions for which checks are allowed; do not release or change the version until the full work is verified.

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

## Features

- Scans device and SD card audio through MediaStore
- Shows a local library cache immediately and incrementally refreshes it when MediaStore changes
- Browses Songs, Artists, Albums, and playlists already indexed on the device
- Reads embedded album art from local audio files, with a colorful gradient fallback when art is missing or unreadable
- Caches album-art thumbnails in the app database for faster offline browsing
- Searches titles, artists, and albums
- Plays tracks in the background with a Media3 session, system notification, lock-screen controls, and audio focus handling
- Remembers the last track, position, shuffle, and repeat mode between launches
- Provides a click wheel with tap controls, rotation seeking, haptic ticks, and an A–Z library index
- Lets you select, remove, and reorder tracks in the playback queue
- Uses Android's audio permission prompt and requests access only when the library is opened

Playback formats depend on the codecs available on the phone. Android's built-in media decoders cover common MP3, AAC/M4A, Ogg Vorbis, WAV, and FLAC files; a particular encoding may still be unsupported by the device.

## Install on a phone

Build `app/build/outputs/apk/debug/app-debug.apk`, copy it to the phone, open it in My Files, and approve Android's install prompt if shown. The debug APK is signed for development installs and is not a Play Store release.

## Download

[Download the RIFT v0.4.0 debug APK](releases/RIFT-v0.4.0-debug.apk).
