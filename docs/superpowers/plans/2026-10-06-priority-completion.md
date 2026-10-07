# SJ Music Priority Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Finish the remaining priority features and produce a debug APK without adding routine dependencies.

**Architecture:** Extend the existing SQLite helper with a versioned track cache and persisted settings, keeping MediaStore authoritative. Add a SAF-backed local lyrics resolver and a versioned JSON backup flow using the existing playlist/settings data. Preserve the existing MediaSessionService queue checkpointing unless source inspection finds a specific defect.

**Tech Stack:** Kotlin, Android Jetpack Compose, Android MediaStore/SAF, SQLiteOpenHelper, Media3, kotlinx.coroutines.

**Spec:** `docs/superpowers/specs/2026-10-06-priority-completion-design.md`

## Global Constraints

- Keep Android `minSdk = 24`, `targetSdk = 36`, and `compileSdk = 37`.
- Do not add a runtime dependency unless a required Android behavior cannot be implemented with current APIs.
- Run MediaStore queries and file reads on `Dispatchers.IO`; bound database writes into batches.
- Preserve existing playlist, favorites, history, folder, and playback data across schema migrations.
- Do not include audio files or the playback queue in backups.
- Deliver `app/build/outputs/apk/debug/app-debug.apk` by running `./gradlew assembleDebug`.

## Review Focus

- Permission revoked or scan interrupted: retain cached tracks and avoid pruning the cache until a full refresh succeeds.
- Large library: database writes are batched and progress state updates do not emit per-row UI recompositions.
- SAF lyrics directory unavailable: show a useful unavailable state without blocking playback.
- Invalid or future-version backup: reject before modifying playlists, favorites, or settings.
- Backup track URI differs on restore: match normalized metadata and report unmatched entries.

---

### Task 1: Persist a launch-ready library cache and scan progress

**Files:**
- Modify: `app/src/main/java/com/dizzyvy/sjmusicapp/music/library/PlaylistStore.kt`
- Modify: `app/src/main/java/com/dizzyvy/sjmusicapp/music/library/AudioLibraryRepository.kt`
- Modify: `app/src/main/java/com/dizzyvy/sjmusicapp/music/library/MediaStoreAudioLibraryRepository.kt`
- Modify: `app/src/main/java/com/dizzyvy/sjmusicapp/music/model/AudioTrack.kt` only if cache serialization exposes a missing field
- Modify: `app/src/main/java/com/dizzyvy/sjmusicapp/ui/library/LibraryViewModel.kt`
- Modify: `app/src/main/java/com/dizzyvy/sjmusicapp/ui/library/LibraryScreen.kt`

**Interfaces:**
- `LibraryUiState` gains `scanProcessed: Int` and `scanTotal: Int?` (or an equivalent compact progress value).
- `AudioLibraryRepository.loadTracks(onProgress: (processed: Int, total: Int) -> Unit)` reports cursor progress while returning the refreshed track list.
- `PlaylistStore` gains cache read, batch upsert, and successful-refresh completion operations; each cached row retains the fields needed to reconstruct `AudioTrack`.

- [ ] Add the track-cache table with a schema migration that preserves all existing tables.
- [ ] Load cached tracks before the MediaStore query and display them while a refresh runs.
- [ ] Emit scan progress at bounded intervals, batch cache upserts, and prune rows only after a complete successful scan.
- [ ] Connect startup, pull-to-refresh, and existing MediaStore observer refreshes to the cache refresh path; retain cache on permission loss or query failure.
- [ ] Show scan progress in the library UI without changing existing empty, permission, or error behavior.

### Task 2: Add local synchronized/plain-text lyrics

**Files:**
- Create: `app/src/main/java/com/dizzyvy/sjmusicapp/music/lyrics/LocalLyricsRepository.kt`
- Modify: `app/src/main/java/com/dizzyvy/sjmusicapp/MainActivity.kt`
- Modify: `app/src/main/java/com/dizzyvy/sjmusicapp/ui/SJMusicApp.kt`
- Modify: `app/src/main/java/com/dizzyvy/sjmusicapp/ui/player/NowPlayingScreen.kt`
- Modify: `app/src/main/java/com/dizzyvy/sjmusicapp/music/library/PlaylistStore.kt` to persist the SAF tree URI

**Interfaces:**
- Lyrics repository loads a result for an `AudioTrack` and selected tree URI, preferring same-folder `.lrc` over `.txt`.
- `.lrc` results expose timestamped lines; `.txt` results expose plain text.

- [ ] Add a SAF directory picker and persist its read grant and URI.
- [ ] Resolve sidecar names from the track display name and relative path; parse LRC timestamps and handle malformed lines safely.
- [ ] Add a lyrics section to Now Playing, highlighting the line matching playback position and rendering `.txt` fallback plainly.
- [ ] Show setup/empty/error states when no folder is selected or lyrics cannot be read.

### Task 3: Add versioned playlist, favorite, and settings backup/restore

**Files:**
- Create: `app/src/main/java/com/dizzyvy/sjmusicapp/music/backup/LibraryBackup.kt`
- Modify: `app/src/main/java/com/dizzyvy/sjmusicapp/music/library/PlaylistStore.kt`
- Modify: `app/src/main/java/com/dizzyvy/sjmusicapp/ui/library/LibraryViewModel.kt`
- Modify: `app/src/main/java/com/dizzyvy/sjmusicapp/ui/library/LibraryScreen.kt`
- Modify: `app/src/main/java/com/dizzyvy/sjmusicapp/ui/SJMusicApp.kt`
- Modify: `app/src/main/java/com/dizzyvy/sjmusicapp/MainActivity.kt`

**Interfaces:**
- A versioned JSON snapshot contains local playlist names and ordered track metadata, favorite metadata, and persisted settings.
- Restore fully validates and resolves entries before one SQLite transaction replaces backed-up data; unmatched entries are skipped and counted.

- [ ] Persist backed-up settings in the app database alongside playlist data and expose load/update methods to the UI.
- [ ] Encode/decode a versioned JSON snapshot; reject missing, malformed, or unsupported versions before applying changes.
- [ ] Add SAF export and import launchers and concise controls in the Playlists UI.
- [ ] Resolve restored track references by URI first, then normalized title/artist/album/duration; report skipped entries after restore.
- [ ] Preserve device-managed playlists and playback queue; restore only app-owned playlists, favorites, and included settings.

### Task 4: Review playback restore and build the APK

**Files:**
- Inspect: `app/src/main/java/com/dizzyvy/sjmusicapp/music/playback/PlaybackService.kt`
- Modify only if needed: `app/src/main/java/com/dizzyvy/sjmusicapp/music/playback/PlaybackService.kt`
- Modify: `README.md` if the generated APK path or final feature summary changes.

- [ ] Review service creation, checkpoint cadence, empty-queue handling, and playback resumption against the queue recovery requirement; fix only a concrete gap.
- [ ] Build with `./gradlew assembleDebug` and resolve compile/build errors.
- [ ] Confirm the APK exists at `app/build/outputs/apk/debug/app-debug.apk`; report build result and any runtime behaviors that require an Android device to verify.

## Execution notes

- Do not create a new automated test suite or run unrelated tests; the requested verification is the debug APK build.
- Keep implementation work in this existing checkout and preserve user changes.
