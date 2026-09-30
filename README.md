# SJ Music

A small, local music player for Android with a bright, click-wheel-era portable-player feel. The first version scans audio on the phone and removable storage, then plays it through Android's media session so lock-screen controls and Bluetooth/headset buttons work.

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
- Searches titles, artists, and albums
- Plays tracks with a queue, seek bar, previous/next, and play/pause
- Exposes an Android media session for system media controls
- Uses Android's audio permission prompt and requests access only when the library is opened

Playback formats depend on the codecs available on the phone. Android's built-in media decoders cover common MP3, AAC/M4A, Ogg Vorbis, WAV, and FLAC files; a particular encoding may still be unsupported by the device.
