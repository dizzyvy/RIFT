# RIFT

RIFT is a local Android music player with a bright, click-wheel-era portable-player feel. It scans audio on the phone and removable storage, then plays it through Android's media session so lock-screen controls and Bluetooth/headset buttons work.

The launcher uses the RIFT Queen artwork. The Android `applicationId` remains `com.dizzyvy.sjmusicapp` so this rebrand can update existing installations without resetting their app data.

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
- Browses Songs, Artists, Albums, and playlists already indexed on the device
- Reads embedded album art from local audio files, with a colorful gradient fallback when art is missing or unreadable
- Searches titles, artists, and albums
- Plays tracks in the background with a Media3 session, system notification, lock-screen controls, and audio focus handling
- Remembers the last track, position, shuffle, and repeat mode between launches
- Provides a click wheel with tap controls, rotation seeking, haptic ticks, and an A–Z library index
- Lets you select, remove, and reorder tracks in the playback queue
- Uses Android's audio permission prompt and requests access only when the library is opened

Playback formats depend on the codecs available on the phone. Android's built-in media decoders cover common MP3, AAC/M4A, Ogg Vorbis, WAV, and FLAC files; a particular encoding may still be unsupported by the device.

## Install on a phone

Build `app/build/outputs/apk/debug/app-debug.apk`, copy it to the phone, open it in My Files, and approve Android's install prompt if shown. The debug APK is signed for development installs and is not a Play Store release.
