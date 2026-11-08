# Simple Player

A native, offline Android music player. Android 7.0 (API 24) or newer; no account or network permission required.

[Download Simple Player](<dist/Simple Player.apk>) · [Theme previews](docs/THEMES.md)

## Version 3.5 · Gold text

- Gold-yellow primary text (`#FFD54F`) and softer gold secondary text in both dark themes, including library / player headings, settings, dialogs, lyrics, input hints, and widget labels.
- Light mode uses darker gold for readable text; filled buttons use contrasting gold tones. Existing theme control accents and backgrounds are retained.
- Version **3.5**, code **8**. App name **Simple Player**, package **`simple.music.player`**, and signing certificate are unchanged, so this updates an existing Simple Player installation.
- Theme screenshots below predate this text-color update.

## Version 3.4 · Simple Player

- App / launcher / widget / About name: **Simple Player**.
- Android package and Java namespace: **`simple.music.player`**. Playback action names, resources, services, and test targets use the new namespace.
- APK filename: **`Simple Player.apk`**, version **3.4**, code **7**.
- Changing the Android package creates a separate installation from `com.auss.musicplayer`; that app's playlists, favorites, history, and settings are not transferred automatically. Existing shared music files remain available after granting audio access.

## Version 3.3 theme consistency

- Shared palettes for app-owned library, player, settings, forms, and dialogs. Amethyst now uses cool purple surfaces rather than orange/brown backgrounds; Daylight uses warm light surfaces and dark readable text.
- An Appearance page with Ember, Amethyst, and Daylight preview cards and selection indicators. Changing the theme keeps Appearance open and leaves paused playback paused.
- Theme-matched radio / checkbox accents, switch colors, slider accents, button text, dimming, rounded dialog backgrounds, borders, and pressed-state ripples.
- Primary, secondary, and accent text meet a 4.5:1 contrast ratio against the card surface in all three palettes.
- Version **3.3**, code **6**, with the same app name, package, and signing identity.

## Version 3.2 settings

- A dedicated, scrollable Settings screen with Appearance, Playback, Music Library, and About sections, descriptive icons, current values, and a Back button.
- A separate Sound & Playback page with native switches for gapless playback and ReplayGain, radio choices for speed / crossfade / equalizer, and a bass-strength slider.
- Clear transition behavior: gapless is shown as unavailable while crossfade is active; its saved preference returns when crossfade is turned off.
- Settings values refresh after changes. Theme changes recreate the interface while keeping the Settings page open. Back from a nested sound page returns to Settings; direct sound entry from the player returns to the player.
- Version **3.2**, code **5**, with the same Music Player name, `com.auss.musicplayer` package, and signing certificate.

## Version 3.1 design refinement

- A gradient Continue Listening card with real cover art, the current track, and a Resume action.
- Favorites / recent-history shortcuts with live counts, numbered song rows, separate duration labels, selected-track outlines, and readable sort labels.
- A more detailed player with playback-speed information, an Up Next card that follows shuffle/repeat, spaced actions, a refined mini-player, and a scrollable layout for compact screens.
- Embedded MP3 cover-art fallback when a document or audio URI has no platform thumbnail; a theme-aware record sleeve when no artwork exists.
- Version name **3.1**, version code **4**. App name and package remain Music Player / `com.auss.musicplayer`.

## Version 3 features

- Redesigned library and now-playing screens: warm Ember colors, rounded artwork, accessible controls, and Amethyst / Daylight themes.
- Songs, albums, artists, saved playlists, favorites, folders, and listening history (recent / most played).
- Song sorting by title, artist, album, date added, or duration; folder exclusions and manual library rescan.
- Queue editing, play next, shuffle, and repeat; queue / current track / position restore **paused** after a process restart. Shuffle and repeat settings survive restarts. Active playback checkpoints its position every ten seconds and immediately on pause or queue changes.
- Sleep timer: 15 / 30 / 45 / 60 minutes or end of song. Timers are intentionally session-only.
- Embedded MP3 lyrics and user-selected `.lrc` / UTF-8 `.txt` lyrics; synchronized highlighting, auto-scroll, and tap a timed line to seek.
- Equalizer presets and bass boost (when supported by the device), 0.5–2× playback speed, gapless playback, and optional 3 / 5 / 8 second crossfade.
- Optional ReplayGain normalization reads embedded ID3 `REPLAYGAIN_TRACK_GAIN` tags. Untagged / unsupported formats keep their original volume; it does not analyze loudness or write gain tags.
- UTF-8 M3U import/export through Android's document picker. Local paths and unique filenames are matched; ambiguous / missing files are reported. Streaming URLs are never fetched.
- MP3 title / artist / album and JPEG / PNG cover editing through a user-selected writable document. ID3v2.3 / v2.4 frames and audio bytes are preserved. Unsupported tag flags are rejected without modifying the file. Other audio formats can play but cannot be edited. A failed write attempts restoration from a private backup.
- Resizable home-screen playback widget and Android Auto media browsing (songs, albums, favorites, playlists, recent tracks).
- Open audio from a file manager, background playback, notification / lock-screen / headset controls, audio-focus handling, and pause on headphone disconnect.

Lyrics files use Android's document permissions. Keep a linked file available at its selected location. External audio URIs granted temporarily by other apps may need to be opened again after a reboot. A system file picker grants write access for tag editing; metadata refresh timing depends on the file provider.

## Build

Install JDK 17 or newer and Android SDK `platforms;android-35`, `build-tools;35.0.0`, and `platform-tools` using the official SDK Manager. Set `ANDROID_HOME` to the SDK directory and add your JDK's `bin` to `PATH`.

Linux / macOS:

```sh
bash tests/run.sh
bash build.sh
```

Windows PowerShell:

```powershell
.\build.ps1
```

Output: `build/Simple Player.apk`. The default build uses a generated, local debug key. To update an existing Simple Player installation (`simple.music.player`) while preserving its app data, sign with that installation's original key:

```sh
export MUSIC_SIGNING_KEY=/private/path/musicplayer.keystore
# Set MUSIC_STORE_PASSWORD and MUSIC_KEY_PASSWORD privately in your environment.
bash build.sh
```

Keep signing keys outside the repository. The historical ZIP on `main` is retained as the original upload; the editable `src/`, `res/`, and manifest at repository root are the current source.

## Verification

`tests/run.sh` exercises LRC timing, playlist ordering and path matching, ID3v2.3 / v2.4 preservation, ReplayGain parsing, artwork updates, and rejection of unsafe edits. Tests compare the audio bytes before and after MP3 tag changes. The URI stand-in under `tests/stubs/` is used only by host tests and is excluded from the APK build.

Device checks and screenshots are recorded in [VALIDATION.md](VALIDATION.md). Android Auto, hardware equalizer effects, Bluetooth routing, and physical-device audio quality require corresponding hardware / host testing.
