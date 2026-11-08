# Version 3 validation

Built with JDK 21, Android platform 35, build-tools 35.0.0, and D8 with minimum API 24.

## Passed

- Compiled Android resources, manifest, and all production Java sources; converted to DEX; aligned and signed APK.
- `bash tests/run.sh`: **27 checks passed** for LRC parsing and boundaries, M3U roundtrips and ambiguous filenames, embedded MP3 lyrics, ReplayGain, ID3v2.3 / v2.4 rewriting, retained unknown frames, artwork frames, and unsupported-file rejection. Tests verify the audio payload remains byte-for-byte identical after tag edits.
- APK signature verified with `apksigner`. The APK in `dist/` uses the same certificate as the original APK in the historical source archive. No signing key was added to the new source tree.
- Confirmed package `com.auss.musicplayer`, version code **3**, version name **3.0**, minimum SDK **24**, target SDK **35**. No internet permission is requested.
- Android instrumentation test APK compiled. The runner covers service binding, favorites, history, pause / seek / shuffle / queue edits, speed while paused, end-of-song sleep, crossfade, gapless transitions, media-browser categories, and paused restoration in a fresh process.

## Android runtime

Android 35 x86_64 emulator launched with software CPU emulation because this host has no KVM device. Initial boot and installation were slow; Android displayed system / System UI ANRs and the emulated storage volume initially failed to mount. The volume was remounted and generated audio fixtures were pushed and scanned. The APK installed and the redesigned library screen opened.

The corrected build passed **22 Android integration checks**, followed by **6 checks in a fresh process**. The first run exposed a callback lifecycle bug after crossfade; the preloaded player now receives the normal preparation/error listeners when promoted. The suite passed after that correction.

Verified: service binding; favorite toggling; play-history recording; seek and pause; shuffle preserving the current song and restoring original order; play-next insertion and queue removal; playback settings; changing speed while paused; end-of-song sleep without queue advancement; crossfade with the incoming track already advanced; gapless transition; media-browser connection and its five root categories; paused restoration of a two-track queue, current index, position, and sort setting.

The emulator's MediaStore library was not populated in time for the suite, so the runner used its two app-private generated MP3 fixtures. **MediaStore scanning and real document-provider behavior remain unverified.** The library / player screenshots below use those same fixtures injected by the test runner; production has no demo-library fallback.

[Library screenshot](docs/screenshots/library.png) · [Player screenshot](docs/screenshots/player.png)

The signed APK is [dist/MusicPlayer-v3.apk](dist/MusicPlayer-v3.apk). SHA-256: `f0435692409e8e5db792f25f9761cc568cbd134b883c58d49e8e04407ab5f160`.

## Reproduce runtime checks

Generate fixtures with Python 3 + Pillow and `ffmpeg`:

```sh
python3 tests/make-fixtures.py
bash build.sh
bash tests/android/build.sh
adb install --no-incremental -r build/MusicPlayer.apk
adb install --no-incremental -r build/instrumentation/tests.apk
adb shell pm grant com.auss.musicplayer android.permission.READ_MEDIA_AUDIO
adb shell pm grant com.auss.musicplayer android.permission.POST_NOTIFICATIONS
adb shell mkdir -p /sdcard/Music/Studio
adb push build/fixtures/. /sdcard/Music/Studio/
# Scan the audio files using MediaScannerConnection or the emulator's media scanner.
adb shell am instrument -w com.auss.musicplayer.tests/com.auss.musicplayer.PlayerIntegrationTests
adb shell am force-stop com.auss.musicplayer
adb shell am instrument -w -e phase restore com.auss.musicplayer.tests/com.auss.musicplayer.PlayerIntegrationTests
```

Run the two phases in order. On an emulator without a scanned library, the runner uses two generated MP3 assets in the app's private test storage. Those fixtures exercise playback and checkpoint restoration, but do not establish MediaStore scanning behavior. `-e phase preview` also saves two screenshots in the target app's private files directory using test-fixture library injection; it is intended only for UI inspection.

## Physical-device / host checks still needed

- MediaStore library scanning on a normally provisioned device.
- Android Auto host integration and distribution eligibility; only media-browser code can be exercised in a standalone emulator.
- Physical-device equalizer / bass capabilities, ReplayGain loudness, Bluetooth controls / audio routing, and gapless / crossfade audio quality.
- Document-provider write grants and metadata refresh for real MP3 tag editing, and widget placement on different launchers.
- Background playback under manufacturer-specific battery policies; notification permission denial; large libraries and large fonts; screen sizes / Android versions beyond the tested emulator.

Signing compatibility establishes that this APK can use the existing application's update identity. An upgrade from an installed original APK has not yet been exercised end to end on a physical device.
