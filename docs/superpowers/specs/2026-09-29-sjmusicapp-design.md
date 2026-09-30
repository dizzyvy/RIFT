# SJMusicApp Design

**Date:** 2026-09-29 | **Status:** Design approved in chat; awaiting written review

## Goal

Build a simple Android music player for Steve's Samsung Galaxy A15 (SM-A156W), running Android 16 with One UI 8.5. The visual direction takes inspiration from the colorful iPod nano generation while using original artwork and interface details. The first version plays music stored on the device; it does not stream music.

The GitHub repository is `dizzyvy/SJMusicApp`. This local workspace is currently empty and is not yet a Git checkout. The repository itself is also empty. The project will be connected to that repository as part of its initial setup so source and Git history stay together.

## First version

The app will:

- Find audio tracks in built-in shared storage and on mounted SD-card storage using Android's indexed media library.
- Request Android's audio-library permission only when needed, explain why it is needed, and show a useful empty or permission-denied state.
- Show a browsable song list with basic text search and track metadata where available.
- Play a selected track and maintain a play queue, with play/pause, previous, next, and seek controls.
- Continue audio playback when the UI is backgrounded, and expose system media controls through the notification, lock screen, and supported headset controls.
- Use a colorful, touch-friendly interface with a click-wheel-inspired playback control and distinct Now Playing view.
- Handle missing or unreadable files and device-unsupported codecs without crashing; show a concise playback error.

Expected mainstream formats include MP3, AAC in M4A, Ogg Vorbis, Opus, WAV, and FLAC when supported by the device's Android decoders. Support depends on codec/profile and device decoder availability, so the app will not claim that every file sharing a familiar extension is playable. Android's platform format matrix is the baseline; actual-device playback is the final compatibility check.

## Technical direction

- **Platform:** Native Android, Kotlin, AndroidX, Jetpack Compose.
- **Playback:** AndroidX Media3 ExoPlayer, hosted in a `MediaSessionService` so playback and system controls can continue outside the app screen.
- **Library:** Query `MediaStore.Audio` for indexed tracks across every available external storage volume. Resolve playable items through their content URIs rather than hard-coded filesystem paths.
- **Permissions:** Request `READ_MEDIA_AUDIO` on Android 13 and newer, and `READ_EXTERNAL_STORAGE` on older Android versions down to the selected minimum SDK.
- **Android target:** Target Android 16 (API level 36). The audio playback path has a documented Media3 baseline of Android 6.0 (API 23); use API 23 as the initial minimum SDK unless a dependency or implementation constraint discovered during setup requires a higher floor.
- **State:** Keep the initial library and queue in memory. Persisting playlists, favorites, or playback history is outside this first version.
- **Repository workflow:** Initialize the local checkout with `dizzyvy/SJMusicApp` as `origin`; keep implementation changes reviewable in Git and publish them through a branch or pull request when ready.

Android's MediaStore indexes audio across external storage volumes, which covers built-in shared storage and mounted SD cards. The implementation must query all available volume names so secondary storage is included. Its shared-media guide documents `READ_MEDIA_AUDIO` for access to audio made by other apps. Media3 ExoPlayer supplies the playback and media-session building blocks, while Android's decoder support determines which codec variants work on a given phone.

## Out of scope for the first version

- Music streaming, accounts, cloud libraries, or online metadata lookup.
- Editing tags or moving/deleting users' audio files.
- User-created playlists, favorites, ratings, and listening history.
- Equalizer, lyrics, visualizers, crossfade, and advanced audio effects.
- iOS or other non-Android clients.
- Copying Apple's logos, product artwork, or proprietary interface assets.

## Acceptance checks

1. On the target Galaxy A15, the app requests audio access in context and can show indexed tracks from built-in storage and an inserted SD card.
2. Search narrows the visible song list by track metadata and selecting a result starts playback.
3. Playback controls update correctly as tracks change, and a queued next track plays after the current one ends.
4. Playback continues after leaving the app, and system media controls can pause/resume and skip tracks.
5. Common device-supported formats play; unsupported or unreadable files produce a recoverable message.
6. The core library and playback screens follow the approved colorful, classic-iPod-inspired direction and remain usable on the target phone's touch display.
7. The project builds from the GitHub-connected checkout and its changes can be reviewed through Git history.

## References

- [Android: Access media files from shared storage](https://developer.android.com/training/data-storage/shared/media)
- [Android: Media3 ExoPlayer](https://developer.android.com/media/media3/exoplayer)
- [Android: Media3 supported formats](https://developer.android.com/media/media3/exoplayer/supported-formats)
- [Android: Set up the Android 16 SDK](https://developer.android.com/about/versions/16/setup-sdk)
