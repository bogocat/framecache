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
- **Background sync** — configurable interval; downloads new photos when WiFi is available
- **Least-recently-shown rotation** — every photo shown before any repeats, and newly added photos slot into the rotation immediately instead of monopolising it
- **Orientation filter** — show all photos, only those matching the screen orientation, or matching + opposite-orientation shown two-up
- **Ken Burns effect** — configurable slow zoom/pan animation
- **Crossfade transitions** — smooth blending between photos
- **Background blur** — blurred version of the photo behind the main image
- **Metadata overlays** — clock, date, photo date, location, description, people, camera, rating, person age
- **Overlay styling & motion** — independent expanded/collapsed sizes, corner position, text size/colour, background opacity, corner radius, marquee for long lines, and a Static/Loop/Once expand-collapse cycle
- **Independent clock style** — the clock (and the date under it) is sized separately from the photo info, with its own font family (sans / serif / condensed / mono), weight, and letter spacing, so it can be a large statement piece while the info stays small
- **Auto-contrast overlays** — text colour can be `Auto`, which samples the brightness of the photo under each pill (per corner, 80th percentile) and picks white or black so the overlay stays readable on bright skies *and* dark shadows, with or without a background
- **Music (Navidrome)** — browse artists → albums → songs, cache favourites / playlists / individual songs offline, with a configurable now-playing pill over the slideshow
- **Cast to Denon** — stream playback to a Denon/HEOS receiver over DLNA (the receiver pulls the stream directly); accurate progress + seeking
- **Sleep schedule** — dim or black screen during configurable hours
- **Settings** — swipe down or long-press from the slideshow; collapsible sections
- **Android Settings access** — WiFi, Bluetooth and system settings accessible from the app
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

Swipe down or long-press on the slideshow to open settings. Sections are collapsible and show their current state while closed (e.g. `Clock — 12-hour · Condensed Light · 200% · date`), and controls that no longer apply are hidden: turning the clock off hides its format and styling, with music off the now-playing pill section disappears.

| Section | Options |
|---------|---------|
| **Server Connection** | Server URL, API key, album IDs |
| **Photo Sources** | Immich albums, local folder |
| **Slideshow** | Photo duration (presets + custom, 1s–24h), crossfade, Ken Burns + zoom, background blur, fill vs fit, progress bar, order, favourites only, photo orientation |
| **Clock** | Show clock, 12h/24h, show current date, position, size (in sp, with a live preview of the frame), font family (sans / serif / condensed / mono), weight (thin → bold), letter spacing, text colour (light / dark / **auto-contrast**), background (none / dark / light / auto) + opacity + corner radius |
| **Photo Info** | Field toggles (photo date, location, description, people, camera, rating, person age); position, size (a multiplier on each line's default, with a live preview), text colour (light / dark / auto), background + opacity + corner radius; motion (collapse mode, expanded/collapsed ratios, holds, collapsed fields, marquee) |
| **Sleep Schedule** | Enable/disable, start/end hours, dim vs black |
| **Sync & Cache** | Sync interval (15 min–6 h), max cached photos (50–1000), sync now, last sync |
| **Music (Navidrome)** | Server/credentials, enable, cache favourites, max cached songs, Denon host, sync now |
| **Now Playing Pill** | Elements (art / title / artist / controls), expanded + collapsed size, opacity, corner radius, collapse mode + holds, collapsed elements — only while music is enabled |
| **System** | Android Settings, WiFi, Bluetooth |

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

### Music (Navidrome) & Denon

Optional: connect a [Navidrome](https://www.navidrome.org/) (Subsonic-compatible) server for music.

- The full library metadata is indexed; **audio is cached only for what you choose** — playlists
  marked “Cached”, favourites (starred), and individual songs / albums / artists pinned from the
  browse screens. Downloads are incremental and resumable.
- Browse by **artist → album → songs**, plus playlists, favourites and search, with a cache toggle
  at each level (bulk selections over 50 songs ask for confirmation).
- **Cast to a Denon/HEOS receiver** over DLNA: the receiver pulls the authenticated Navidrome
  stream directly, so audio survives the frame sleeping. Progress and seeking are read from / sent
  to the receiver. Toggle it from the now-playing pill (long-press the pill).
- A configurable **now-playing pill** shows art/title/artist/controls over the slideshow and can
  auto-collapse to a minimal set.

## Requirements

- Android 6.0+ (API 23)
- An Immich server with an API key and at least one shared album
- *Optional:* a Navidrome server for music, and/or a Denon/HEOS receiver for casting

## License

MIT
