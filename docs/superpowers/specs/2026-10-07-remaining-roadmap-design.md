# SJ Music Remaining Roadmap Design

## Goal

Integrate the Nano-Chromatic visual direction from PR #4 into the current SJ Music feature branch and complete the still-missing items from the user's original roadmap, producing a buildable debug APK.

## Approved direction

- Keep the current feature branch and all of its v0.3.0 work as the source of truth.
- Port the visual system from PR #4 selectively. The PR targets `main` and changes `LibraryScreen.kt`, `Color.kt`, and `Theme.kt`; do not merge its branch wholesale or discard newer feature-branch behavior.
- Audit the current source before implementing each roadmap item. Existing functionality is retained and counted as complete only where source supports it; build/device dependent behavior is reported separately.
- Reuse Kotlin, Compose, Media3, SQLite, MediaStore, and SAF. Add dependencies only when the requested Android behavior cannot be implemented with current APIs.
- Keep network lookups and third-party integrations opt-in. Never add secrets or hard-coded credentials.
- Deliver a successful `assembleDebug` APK. Do not run automated tests unless the user asks.

## Feature scope

The source audit and implementation plan will cover the uncompleted parts of the original request, grouped as follows. Items already implemented are excluded from new work and verified against the code.

### Nano-Chromatic design integration

- Port PR #4's glossy dark surfaces, chromatic gradients, pill navigation, redesigned rows, empty states, and mini-player to the current feature branch.
- Preserve current library actions, playlists, selection, search, artwork, sorting, playback callbacks, themes, and accessibility behavior.
- Retain light, dark, and AMOLED choices and expose Nano-inspired chromatic accents without forcing a single theme on existing users.

### Remaining library discovery and organization

- Global search across songs, artists, albums, and playlists with type filters.
- Folder, genre, and year views; duplicate detection; persisted artist aliases/merge.
- Complete sort and filter controls, hidden-folder management, short-track filtering, and robust MediaStore change refresh.
- Dynamic colors from artwork, optional waveform/visualizer surfaces, and remaining list/grid/click-wheel and tab customization options.
- Preserve scan caching, progress, unknown metadata handling, A-Z grouping, and large-library responsiveness already present.

### Remaining playback and song actions

- Audit and fill only gaps in audio-focus/call behavior, noisy/headset/Bluetooth events, queue recovery, queue screen actions, mini-player gestures, sleep behavior, and media-session controls.
- Implement still-missing playback capabilities from the request where supported: gapless/crossfade and fades, equalizer/presets/bass/virtualizer, ReplayGain or normalization, skip silence, A-B loop, and richer playback controls.
- Complete remaining per-song actions: share, ringtone, metadata/details, artwork selection/tag editing, favorites and automatic collections, and safe delete confirmation.
- Keep optional processing disabled by default and ensure unsupported formats or device capabilities fail clearly rather than destabilizing playback.

### Remaining platform integrations and insights

- Widgets, quick-settings tile, Android Auto, Wear OS, Assistant intents, and opt-in Last.fm/Chromecast/online metadata or lyrics lookup.
- Listening statistics and year-in-review views.
- Clear permission education and recovery states for Android media access and optional integrations.

## Architecture

Continue the existing repository boundaries: library and metadata work stays in the library/repository and ViewModel layer; persistent user state uses the existing SQLite store with versioned migrations; playback capabilities are exposed through the playback controller and implemented by the Media3 service; Compose screens consume state and callbacks. Android entry points (service, receiver, tile, widget, automotive metadata) remain small adapters over these existing components.

Features that require runtime permissions, account credentials, a compatible Android device, or external service configuration must have explicit opt-in/setup states. Platform-specific work should not block core playback or library startup.

## RIFT rebrand addendum (2026-10-07)

- Use **RIFT** as the product and repository name, with the transparent Queen artwork as the launcher identity and the Nano-Chromatic palette as its visual foundation.
- The GitHub repository has been renamed to `dizzyvy/RIFT`; update the local Git remote to match. Rename the local checkout folder to `RIFT` when Windows releases its open-folder handle.
- Rename the Gradle root project, app label, source namespace, Kotlin packages, app entry composable, theme composable, README, and active implementation documents to RIFT.
- Keep `applicationId = "com.dizzyvy.sjmusicapp"` stable for installed v0.3.0 builds. Changing it would make Android install RIFT as a different app and stop it inheriting the existing app's local playlist, settings, and queue data. The namespace can change independently when explicitly configured.
- Keep the existing SQLite database name and schema migration path; the rebrand must not reset playlists, aliases, favorites, history, or queue state.

The namespace/application ID distinction follows the [Android app module configuration guidance](https://developer.android.com/build/configure-app-module), which recommends keeping the application ID stable after distribution and permits namespace refactoring independently when the ID is explicit.

## Constraints and success criteria

- Do not overwrite the user's current changes or the feature branch's newer behavior with PR #4's older base.
- Avoid duplicating capabilities already present in the source.
- Keep schema/data migrations backward-compatible and preserve playlists, favorites, settings, history, hidden folders, and queue state.
- Keep MediaStore and file work off the main thread; support libraries with at least 10,000 tracks without unbounded per-item UI work.
- Use accessible labels and at least 48dp touch targets for interactive controls.
- Build the debug APK successfully and report features that still require a physical device, credentials, or external service setup.

## Delivery boundaries

This is a broad roadmap, so implementation is divided into independently buildable slices. A slice is complete only when its source changes are integrated, the debug build succeeds, and any device-only validation limits are stated. No production release or PR merge is implied by this design approval.
