# RIFT

RIFT is a local Android music player with a bright, click-wheel-era portable-player feel. It scans audio on the phone and removable storage, then plays it through Android's media session so lock-screen controls and Bluetooth/headset buttons work.

The launcher uses the RIFT wordmark and lightning-bolt logo. The Android application ID remains stable so this rebrand can update existing installations without resetting their app data.

## Development status

The current development version is **0.14.0** (version code 12), based on the stable `v0.12.0` release at commit `17fb5771d6fad942f5500269b731664efc5af9be`. This integration is in progress and has not been built or tested yet.

The integrated downloader provides yt-dlp downloads, queue management, format selection, task logs, download history, settings, and troubleshooting. It opens from RIFT's Settings sheet, accepts shared HTTP(S) links, and is embedded in RIFT's theme without a separate launcher or activity.

Downloader preferences and its Room database are stored separately from RIFT's music library. Private data from separately installed downloader apps is not imported. Downloads are staged in app-scoped storage; users can choose a folder through Android's Storage Access Framework, and successful downloads are copied there without all-files access.

## Licensing

The root [LICENSE](LICENSE) contains the complete GNU General Public License, version 3 (GPL-3.0-only). RIFT-authored software is intended to be distributed under GPL-3.0-only where the relevant rights and file provenance support that designation; this does not apply one license to every repository file or relicense third-party material. The same GPLv3 text is also available at [licenses/GPL-3.0.txt](licenses/GPL-3.0.txt).

The integrated downloader contains code derived from Seal, whose source repository declares GNU GPL version 3; it must not be treated as exclusively original RIFT code. Since the downloader is a dependency of the same Android application, a separate Gradle module does not by itself establish that the distributed app is a mere aggregation of independent programs. Distribution may require compliance with GPLv3 terms for a combined work. These documents do not resolve that legal question or establish rights to relicense upstream contributions. See [licenses/THIRD-PARTY-NOTICES.txt](licenses/THIRD-PARTY-NOTICES.txt) for available provenance and attribution information.

The downloader also includes Apache-noticed Material color and motion helpers; the Apache License 2.0 text is preserved in [licenses/APACHE-2.0.txt](licenses/APACHE-2.0.txt). The bundled Libre Franklin and Shrikhand fonts have separate SIL Open Font License 1.1 texts in [licenses/OFL-Libre-Franklin.txt](licenses/OFL-Libre-Franklin.txt) and [licenses/OFL-Shrikhand.txt](licenses/OFL-Shrikhand.txt). Third-party libraries, native components, and bundled assets retain their applicable license terms and notices. See the [dependency and asset inventory](licenses/DEPENDENCY-AND-ASSET-INVENTORY.md); the complete transitive-dependency, native-library, and bundled-asset audit remains outstanding. The [GPLv3 release-source checklist](docs/GPLV3-RELEASE-SOURCE-CHECKLIST.md) is a review aid, not a certification that a release satisfies every applicable obligation. Documentation alone does not establish complete GPLv3 compliance.

The downloader compatibility implementation preserves its existing database filename, schema version, preference keys, notification channels, and notification extra keys.

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

The Android CI workflow runs `testDebugUnitTest` and then `assembleDebug` as separate ordered steps for pushes, pull requests, and manual runs. No Gradle task was run while preparing this work.

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
- Embeds yt-dlp downloads, custom command templates, task logs, download history, and downloader settings
- Accepts shared HTTP(S) links and supports app-scoped staging with user-selected SAF destinations

Playback formats depend on the codecs available on the phone. Android's built-in media decoders cover common MP3, AAC/M4A, Ogg Vorbis, WAV, and FLAC files; a particular encoding may still be unsupported by the device.

## Install on a phone

Build `app/build/outputs/apk/debug/app-debug.apk`, copy it to the phone, open it in My Files, and approve Android's install prompt if shown. The debug APK is signed for development installs and is not a Play Store release.

## Historical debug APKs

The APKs in `releases/` are historical development builds and are not current releases. The current branch is work in progress.
