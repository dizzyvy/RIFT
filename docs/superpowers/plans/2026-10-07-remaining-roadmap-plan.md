# RIFT Remaining Roadmap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Complete the RIFT rebrand, port the Nano-Chromatic PR #4 design, and implement the still-missing user-requested music-player features without duplicating existing work.

**Architecture:** Preserve the existing Kotlin/Compose, Media3, MediaStore, SAF, and SQLite architecture. Audit current code first; port UI styling selectively; put library, playback, and platform behavior behind existing repository, controller, and UI state boundaries.

**Tech Stack:** Kotlin, Android SDK 37, Jetpack Compose, Media3, SQLite, MediaStore, SAF, Gradle.

**Spec:** `docs/superpowers/specs/2026-10-07-remaining-roadmap-design.md`

## Global Constraints

- Keep `minSdk = 24`, `targetSdk = 36`, and `compileSdk = 37`.
- Preserve current feature-branch work and existing user data.
- Add dependencies only when Android APIs and existing libraries cannot meet a requirement.
- Keep network lookups and third-party integrations opt-in; never hard-code credentials.
- Keep MediaStore and file work off the main thread; support at least 10,000 tracks.
- Keep interactive controls accessible with labels and 48dp+ targets.
- Do not run automated tests unless requested; verify deliverables with `.\gradlew.bat assembleDebug`.

## Review Focus

- The current feature branch contains newer behavior than PR #4's `main` base; preserve it while porting visuals.
- Permission revocation, unavailable storage, and failed scans must retain app-owned data and show a recoverable state.
- Large libraries must not cause main-thread scans or per-track UI recomposition.
- Optional DSP, media formats, and integrations must degrade clearly when unsupported or unconfigured.
- Migrations and settings changes must preserve existing playlists, favorites, history, hidden folders, and playback state.

## Source Audit (2026-10-07)

- **Already present in the current working tree:** global library search and category filters; Songs/Artists/Albums/Playlists/Folders/Genres/Years/Duplicates; short-track filtering and hidden folders; playlist CRUD, Favorites/auto playlists, M3U and JSON backup/restore; artist/album artwork and aliases normalization; multi-select and per-song menu/details/share/delete; Media3 session/service, persistent queue, queue reorder/remove/undo/clear/save, play-next/add-to-end, shuffle/repeat, sleep timer, playback speed, lyrics; light/dark/AMOLED theme choices and Nano color accents; track cache/progress and MediaStore observer refresh.
- **Still missing or needing focused audit:** PR #4's visual treatment; artist alias editor/merge persistence; truly dynamic artwork colors; list/grid/click-wheel navigation settings and custom tab ordering; click-wheel sensitivity/haptics; ringtone action; embedded lyrics; A-B loop, equalizer/audio effects, ReplayGain/normalization, skip silence, crossfade/fades, waveform/visualizer, tag/art editing, detailed headset/noisy-device/call behavior; home widgets, quick-settings tile, Android Auto, Wear, Assistant, listening stats, and opt-in external services.
- **Baseline build:** `GRADLE_USER_HOME=.gradle .\gradlew.bat assembleDebug` completed successfully (Gradle 9.3.1; 36 actionable tasks, 1 executed, 35 up-to-date; 22m03s). The first wrapper download required network escalation because the sandbox blocked the Gradle distribution download.

---

### Task 1: Audit existing implementation and establish current build baseline

**Files:**
- Inspect: `app/src/main/java/com/dizzyvy/rift/`
- Inspect: `app/src/main/AndroidManifest.xml`
- Inspect: `gradle/libs.versions.toml`

- [x] Compare the original user roadmap with current APIs and UI; record implemented and outstanding items above.
- [x] Inspect PR #4's changed files: it is a UI/color/theme port based on `main`; preserve current feature-branch behavior.
- [x] Run baseline `assembleDebug`; it passed after setting a writable `GRADLE_USER_HOME`.

### Task 1.5: Complete the RIFT rebrand

**Files:**
- Modify: `settings.gradle.kts`, `README.md`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`
- Keep the RIFT source package path `com/dizzyvy/rift/` and update Kotlin package/import references as needed.
- Preserve the stable Android `applicationId` and local database identity so current installs retain update compatibility and app-owned data.
- Rename the local checkout folder to `RIFT` after Windows releases its current open-folder handle.

- [ ] Finish product and source branding while preserving existing install/data identity.
- [ ] Rename the local checkout directory when the current process releases it.
- [ ] Verify source package, resources, and build configuration compile.

### Task 2: Port PR #4 Nano-Chromatic design without losing current behavior

**Files:**
- Modify: `app/src/main/java/com/dizzyvy/rift/ui/library/LibraryScreen.kt`
- Modify: `app/src/main/java/com/dizzyvy/rift/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/dizzyvy/rift/ui/theme/Theme.kt`
- Modify: theme selection UI in `LibraryScreen.kt` or its existing owner, as found in the audit.

- [ ] Port the PR's dark glossy surfaces, chromatic gradients, pill tabs, row treatment, empty states, and mini-player onto the feature-branch implementation.
- [ ] Keep current search, A-Z, album/artist artwork, playlist actions, multi-select, callbacks, and player gestures intact.
- [ ] Add/retain light, dark, AMOLED, and Nano-inspired accent choices; persist them through the existing settings path.
- [ ] Run `.\gradlew.bat assembleDebug` and fix compile errors before proceeding.

### Task 3: Complete library organization and search gaps

**Files:**
- Modify: `app/src/main/java/com/dizzyvy/rift/music/library/AudioLibraryRepository.kt`
- Modify: `app/src/main/java/com/dizzyvy/rift/music/library/MediaStoreAudioLibraryRepository.kt`
- Modify: `app/src/main/java/com/dizzyvy/rift/music/library/LibraryBrowseItem.kt`
- Modify: `app/src/main/java/com/dizzyvy/rift/music/library/PlaylistStore.kt`
- Modify: `app/src/main/java/com/dizzyvy/rift/ui/library/LibraryViewModel.kt`
- Modify: `app/src/main/java/com/dizzyvy/rift/ui/library/LibraryScreen.kt`

- [ ] Add global search across songs, artists, albums, and playlists with type filter chips, reusing loaded/cached data.
- [ ] Add Folders, Genres, and Years views backed by MediaStore metadata and existing hidden-folder state.
- [ ] Add duplicate grouping using normalized title, artist, duration, and file identity; never delete automatically.
- [ ] Add persisted artist aliases and merge/undo controls without rewriting source tags.
- [ ] Audit and complete sort, tiny-file, blacklist, pull-to-refresh, and observer refresh controls; avoid duplicate controls for behavior already present.
- [ ] Keep queries off the main thread and keep list rendering lazy; build with `.\gradlew.bat assembleDebug`.

### Task 4: Complete playback and song-action gaps

**Files:**
- Modify: `app/src/main/java/com/dizzyvy/rift/music/playback/PlaybackService.kt`
- Modify: `app/src/main/java/com/dizzyvy/rift/music/playback/PlaybackController.kt`
- Modify: `app/src/main/java/com/dizzyvy/rift/music/playback/Media3PlaybackController.kt`
- Modify: `app/src/main/java/com/dizzyvy/rift/ui/player/NowPlayingScreen.kt`
- Modify: `app/src/main/java/com/dizzyvy/rift/ui/library/LibraryScreen.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] Audit media session, audio focus, call interruption, becoming-noisy/headphone disconnect, Bluetooth/headset commands, notification controls, and process-death queue restore; change only missing behavior.
- [ ] Complete queue screen interactions: reorder, swipe remove/undo, clear, save as playlist, play-next, and add-to-end; retain crash-safe state.
- [ ] Audit sleep timer, finish-current-track, shuffle/repeat, playback speed, mini-player seek/skip gestures, and song action menu; fill only gaps.
- [ ] Add remaining song actions: share, set ringtone, bitrate/format/sample-rate/size/path details, editable tags/artwork, and permission-safe confirmed deletion.
- [ ] Add A-B loop and audit gapless, crossfade/fades, skip silence, and ReplayGain/normalization. Expose only capabilities supported by the selected engine and make processing optional.
- [ ] Add equalizer presets/bass boost/virtualizer only through supported platform audio effects; handle unavailable effects without playback failure.
- [ ] Add optional waveform/visualizer and album-art swipe navigation only if they remain responsive on large libraries and low-memory devices.
- [ ] Build with `.\gradlew.bat assembleDebug`.

### Task 5: Complete formats, metadata, and visual personalization

**Files:**
- Modify: `gradle/libs.versions.toml` only if an approved decoder/tag dependency is necessary.
- Modify: `app/build.gradle.kts` only if required by the chosen dependency.
- Modify: `app/src/main/java/com/dizzyvy/rift/music/`
- Modify: `app/src/main/java/com/dizzyvy/rift/ui/`

- [ ] Verify FLAC, ALAC, OPUS, OGG, WAV, and M4A behavior with the existing Media3 pipeline; add only decoders required for requested formats.
- [ ] Complete manual album-art pick/replace and tag editing using scoped Android access and safe metadata writes.
- [ ] Add dynamic accent extraction from artwork with a stable fallback and no repeated decoding during scrolling.
- [ ] Finish click-wheel sensitivity and haptic settings, list/grid/click-wheel layout, custom tab order, and hide-unused-tabs settings.
- [ ] Build with `.\gradlew.bat assembleDebug`.

### Task 6: Add local integrations, widgets, and listening insights

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Create/modify: platform-specific widget, quick-settings tile, automotive, and Wear adapters under `app/src/main/java/com/dizzyvy/rift/`
- Modify: existing playback/library stores and settings UI where required.

- [ ] Add small, medium, and Nano-style home-screen widgets backed by the existing playback session.
- [ ] Add a quick-settings playback tile using the existing controller/session.
- [ ] Add Android Auto and Wear OS session controls, plus Assistant-compatible media intents; keep unsupported platform launchers isolated.
- [ ] Add listening statistics for top artists/songs, listening time, and year review using persisted play history.
- [ ] Add opt-in setup for Last.fm, Chromecast, and online lyrics/art lookup; require user-supplied configuration and keep core playback offline-capable.
- [ ] Build with `.\gradlew.bat assembleDebug`; document integrations that require credentials, companion apps, or physical-device validation.

### Task 7: Final requirement audit and APK delivery

**Files:**
- Inspect all files changed in Tasks 1–6.
- Modify: `README.md` if install instructions or feature notes need updating.

- [ ] Reconcile every item in the original request against source and classify any device- or credential-dependent item honestly.
- [ ] Run `.\gradlew.bat assembleDebug` and confirm `app/build/outputs/apk/debug/app-debug.apk` exists.
- [ ] Report the APK path, build result, implemented scope, and any remaining external/device limitations.
