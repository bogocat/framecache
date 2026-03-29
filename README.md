# FrameCache

Offline-first digital photo frame for Android. Caches photos locally so WiFi drops are invisible.

> **Currently supports [Immich](https://immich.app).** Not affiliated with or endorsed by the Immich project.

## Why?

Existing photo frame solutions ([ImmichFrame](https://github.com/immichFrame/ImmichFrame), [ImmichKiosk](https://github.com/damongolding/immich-kiosk)) are web-based. Every image requires an active connection between the device's browser and the server. On cheap photo frame hardware (Frameo, budget tablets) with weak WiFi, a single dropped request shows an error screen or kills the slideshow entirely.

FrameCache takes a different approach: **sync and display are completely decoupled.** The display layer reads only from local cache. The sync layer runs in the background when WiFi is available. WiFi can be gone for days and the frame keeps cycling through cached photos.

```
Web-based:  Device browser ←—WiFi (every image)—→ Web server ←→ Photo library
FrameCache: Device app ←—reads local disk—→ [cached JPEGs + Room DB]
                                              ↑
                                    WorkManager (WiFi when available) → Photo library API
```

## Features

- **Offline-first** — photos cached locally on the device, slideshow never touches the network
- **Background sync** — WorkManager downloads new photos hourly when WiFi is available
- **Weighted random** — every photo shown before any repeats
- **Ken Burns effect** — configurable slow zoom/pan animation
- **Crossfade transitions** — smooth blending between photos
- **Background blur** — blurred version of the photo behind the main image
- **Metadata overlays** — clock, date, photo date, location, description, people, camera (all toggleable, pill-style)
- **Sleep schedule** — dim or black screen during configurable hours
- **Orientation lock** — auto, landscape, or portrait
- **Settings** — swipe down or long-press from the slideshow
- **Android Settings access** — WiFi and system settings accessible from the app
- **ADB config** — push server URL, API key, and album IDs via intent extras (no typing on the device)
- **DreamService** — works as an Android screensaver
- **Launcher mode** — can replace the home screen on dedicated frames
- **Burn-in prevention** — periodic pixel shifting

## Setup

### 1. Build

```bash
# Requires JDK 17+ and Android SDK
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug
```

APK at `app/build/outputs/apk/debug/app-debug.apk`

### 2. Install

```bash
adb install app-debug.apk
```

### 3. Configure

**Option A: On-device setup screen**

Launch the app, enter your Immich server URL and API key, select albums, tap Start.

**Option B: ADB (no typing on device)**

```bash
adb shell am start -n com.bogocat.framecache/.MainActivity \
  --es server_url "https://photos.example.com" \
  --es api_key "your-immich-api-key" \
  --es album_ids "album-uuid-1,album-uuid-2"
```

Get your API key from Immich: Account Settings > API Keys > New API Key.

Get album IDs from the URL when viewing an album in Immich (the UUID in the address bar).

## Settings

Swipe down or long-press on the slideshow to open settings.

| Section | Options |
|---------|---------|
| **Connection** | Server URL, API key, album IDs (protected by default, tap Edit to modify) |
| **Slideshow** | Photo duration (5-120s), crossfade speed, Ken Burns on/off + zoom, background blur, fit vs fill |
| **Overlays** | Clock, current date, photo date, location, description, people, camera |
| **Sync** | Sync interval (15min-6hr), max cached photos (50-1000), manual sync, last sync time |
| **Sleep** | Enable/disable, start/end hours, dim vs black |
| **Display** | Orientation lock (auto/landscape/portrait) |
| **System** | Android Settings, WiFi Settings |

## Architecture

- **Kotlin** + **Jetpack Compose** for UI
- **Coil 3** for image loading
- **Room** for asset metadata (IDs, EXIF, display history)
- **WorkManager** for background sync (WiFi-only constraint)
- **DataStore** for settings
- **Retrofit + OkHttp** for API communication
- **Hilt** for dependency injection

### Photo Sources

Currently supports **Immich** via its REST API (`GET /api/albums/{id}`, `GET /api/assets/{id}/thumbnail`).

The sync and display layers are decoupled — adding new photo sources (Google Photos, Synology Photos, PhotoPrism, local folders, etc.) would mean implementing a new sync adapter without touching the display code.

> Note: `POST /search/random` doesn't work for shared-album users in Immich, which is why we fetch the full album and randomize client-side.

## Requirements

- Android 6.0+ (API 23)
- An Immich server with an API key
- At least one shared album

## License

MIT
