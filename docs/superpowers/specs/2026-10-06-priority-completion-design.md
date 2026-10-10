# RIFT Priority Completion Design

## Goal

Complete the remaining priority items in the RIFT roadmap and produce a debug APK, with minimal dependency and build overhead.

## Current state

- Playback queue, current item, playback position, shuffle, and repeat are already checkpointed by `PlaybackService`; verify this behavior during the requested build rather than replacing it.
- `SqlitePlaylistStore` already provides local playlist, favorite, play-history, and hidden-folder persistence.
- The library is read from MediaStore and refreshed from a `ContentObserver`, but the full track index is not persisted as an app-owned cache and there is no scan progress state.
- The app has no lyrics view or playlist/settings backup and restore flow.

## Design

### Cached library and scan progress

Extend the existing SQLite store with a versioned track-cache table. Persist the metadata required to reconstruct `AudioTrack`, including the content URI, volume, display path, album artist, artwork metadata, and audio properties. Keep MediaStore as the authoritative source: show a valid cache immediately, query MediaStore on a background dispatcher, replace/update the cache, and publish scan progress as rows are read. On permission loss, do not erase cached rows; report that refreshed access is needed. Refresh the cache on startup, pull-to-refresh, and MediaStore observer events. Avoid holding the whole database transaction while scanning; apply bounded batches and report discovered/processed counts.

### Lyrics

Add a lightweight local lyrics resolver with no new dependency. Because Android's audio permission does not grant general access to neighboring text files, let the user select the music directory through the Storage Access Framework and persist that read grant. Resolve a same-directory `.lrc` sidecar first and show synchronized lines against playback position. If no `.lrc` exists, show a same-directory `.txt` sidecar as plain text. Unavailable or unreadable lyrics produce a clear empty state. The lyrics UI is reachable from Now Playing. Embedded lyrics are outside this pass because the current Android MediaStore/Media3 pipeline does not expose a consistent embedded-lyrics API without adding a metadata library.

### Backup and restore

Add versioned JSON backup and restore through Android's Storage Access Framework. Back up local playlists and ordered entries, favorites, and settings (theme, accent, sort order, short-track filter, and hidden folders); persist settings in the app database if they are currently only held in memory or preferences. Exclude audio files and the transient playback queue. Validate the entire file before applying it; for a valid backup, replace app-owned playlists, favorites, and included settings in one database transaction so malformed or unsupported backups leave existing data intact. Match restored playlist entries to the current library using stable URI when possible, then normalized title/artist/album/duration metadata; skip unmatched tracks and report the count. Keep the format versioned for future migration.

### Playback queue reliability and APK

Retain the existing MediaSessionService persistence design. Inspect lifecycle and restore behavior, make only targeted fixes if source review identifies a concrete gap, and build `assembleDebug`. The deliverable is the APK at `app/build/outputs/apk/debug/app-debug.apk`, with build success and output path reported. Do not add a new test suite or run unrelated tests; the user requested an APK build.

## Constraints and success criteria

- No new runtime dependencies unless implementation proves an existing Android API cannot meet a required behavior.
- MediaStore remains the source of truth; SQLite cache accelerates launch and survives temporary permission or scan failures.
- Database migrations preserve existing playlist, favorite, history, and folder data.
- Scan work and file parsing run off the main thread; UI updates remain bounded for libraries with 10,000+ tracks.
- Existing playback and playlist behavior remains intact.
- Debug APK builds successfully and is present at the documented path.

## Out of scope

- Embedded lyrics extraction, cloud lyrics lookup, audio file copying into backups, and synchronizing backup files to a cloud provider.
- New playback DSP/equalizer features and optional integrations such as Android Auto, Chromecast, or Last.fm.
