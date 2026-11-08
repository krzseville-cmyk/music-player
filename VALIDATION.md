# Version 3.4 · Simple Player identity validation

- Built, aligned, and signed **`dist/Simple Player.apk`**. APK metadata confirms application label **Simple Player**, package **`simple.music.player`**, launcher **`simple.music.player.MainActivity`**, version **3.4**, code **7**, minimum API **24**, target API **35**.
- Source and test packages moved to `simple.music.player`; source paths are `src/simple/music/player/` and `tests/simple/music/player/`. Widget branding, About text, playback action strings, media-session tag, build outputs, and instrumentation targets were updated.
- DEX namespace inspection confirms the new launcher class is present and the old `com.auss.musicplayer` class namespace is absent from the current APK.
- **27 host feature checks passed** under the renamed package. **15 Android identity / smoke checks passed** after a fresh install: app package / label, resource namespace, launcher, playback and browser services, widget label, action strings, Settings branding, actual playback / pause, and media-browser root categories.
- Signature verified; certificate remains `3fb413c86beccc6627a4c3be5de0a8cb4bfc15715d03ebbf2d02ab85ff037c73`.
- APK SHA-256: `8ded6115fddf5bd67260f0e3be88536a54280b4bd9dfff59e797ea59e44357c6`.
- The new application ID installs separately from the old app. Its private playlists, favorites, history, settings, and checkpoints do not migrate automatically. Shared audio files remain accessible after granting the new app audio permission.
- Theme layouts and playback implementation are unchanged apart from branding and namespace references. Prior-version tests below are retained as historical evidence.

# Version 3.3 theme validation

- Android resource compilation, production Java / DEX compilation, alignment, signed APK generation, and instrumentation compilation passed.
- **46 Android theme checks passed** on the Android 35 emulator. The suite switches Ember → Amethyst → Daylight → Ember through actual Appearance-card clicks and Activity recreation.
- Verified the selected palette, Appearance-page and parent-navigation restoration, native app / dialog accent attributes, themed row ripples, switch tints, and paused playback after each theme change.
- Primary, secondary, accent, and button text contrast is at least **4.5:1** in all three palettes. Secondary text contrast against card backgrounds: Ember **7.84:1**, Amethyst **7.79:1**, Daylight **4.94:1**. Accent text: **9.36:1**, **8.75:1**, **4.89:1**, respectively.
- Native Android screenshots cover Settings, Sound & Playback, the speed picker, and Appearance for every theme: [previews](docs/THEMES.md). Native buttons use uppercase labels; the accessibility test runner accepts case differences when dismissing pickers.
- Signed APK: [MusicPlayer-v3.3.apk](dist/MusicPlayer-v3.3.apk). Version **3.3**, code **6**, package `com.auss.musicplayer`, minimum API **24**, target API **35**.
- Verified the existing signing certificate: `3fb413c86beccc6627a4c3be5de0a8cb4bfc15715d03ebbf2d02ab85ff037c73`.
- APK SHA-256: `5cc5ef13c2653da31ce7f787eaf694e31a951bca03bcf2f35fa2ec3a6a63340e`.
- The playback engine / file-format code is unchanged; earlier functional test results below are historical and were not rerun for this theme-only change. Physical devices, other Android versions, large-font / rotated dialogs, and system-owned pickers still need corresponding testing. System-owned UI follows the device's theme.

# Version 3.2 settings validation

- Built the native Android resources, all production Java sources, DEX, aligned APK, and instrumentation runner successfully.
- **14 Android settings checks passed** on the Android 35 emulator: service binding; full-screen Settings entry; nested sound navigation; ReplayGain switch and saved-preference update; gapless switch; speed selection without starting playback; crossfade selection; gapless availability / restored state; and Back navigation from nested and direct sound entry points.
- The tests used actual accessibility clicks for speed / crossfade choices. Screenshots: [Settings](docs/screenshots/settings-v3.2.png), [Sound & playback](docs/screenshots/settings-sound-v3.2.png), [Audio effects](docs/screenshots/settings-effects-v3.2.png).
- Native back handling also uses Android's back dispatcher on API 33+. Settings saves its page during Activity recreation so theme changes can return to the same page; theme recreation and other Android versions were not exercised by this suite.
- Playback engine and file-format implementation unchanged. Their older results below are retained as historical evidence and were not rerun for this settings UI change.
- Signed APK: [MusicPlayer-v3.2.apk](dist/MusicPlayer-v3.2.apk). Version **3.2**, code **5**, package `com.auss.musicplayer`, minimum API **24**, target API **35**.
- Signature verified against the existing app certificate: `3fb413c86beccc6627a4c3be5de0a8cb4bfc15715d03ebbf2d02ab85ff037c73`.
- APK SHA-256: `904d45b12be7835c4979530e648154579e34144c41876e5345914efcab201eb4`.
- Real-device equalizer capabilities, system permission navigation, document pickers, large-font layouts, and manufacturer-specific behavior still need physical-device testing.

# Version 3.1 design validation

- Android resources / Java / DEX compilation, APK alignment, and signature verification passed.
- **10 Android design checks passed** on the Android 35 emulator: service binding, fixture playback, embedded MP3 art loading via the new fallback, Continue Listening visibility and the actual current title, Up Next matching the queue, Resume starting playback and opening the player, and Favorites navigation / hiding the overview outside the Songs tab.
- Inspected native Android screenshots using six generated local audio fixtures. No production demo library was added. Screenshots: [library](docs/screenshots/library-v3.1.png), [player](docs/screenshots/player-v3.1.png).
- Playback engine unchanged from version 3.0. Its earlier playback / restart and format results are retained below; those suites were not rerun for this visual refinement.
- Signed APK: [MusicPlayer-v3.1.apk](dist/MusicPlayer-v3.1.apk). Package `com.auss.musicplayer`; version name **3.1**, code **4**; minimum API **24**, target API **35**.
- Same signing certificate as version 3.0: `3fb413c86beccc6627a4c3be5de0a8cb4bfc15715d03ebbf2d02ab85ff037c73`.
- APK SHA-256: `5d7311a404c8965430ef852b7fb5c9595943591a8f023c47b88a1ee600dc47e8`.
- Physical-device / host limitations from version 3.0 still apply. Large-font and small-screen behavior has a scroll fallback; it has not been exhaustively exercised across devices.

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
