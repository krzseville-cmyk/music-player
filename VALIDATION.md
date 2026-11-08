# Version 1.0 · Display-version change

- Updated both build scripts to display version **1.0**. Internal Android version code increments to **17** to allow updates from the earlier builds.
- Resource / Java / DEX compilation, alignment, and signature verification passed. APK metadata confirms `simple.music.player`, versionName `1.0`, versionCode `17`, minimum API 24, target API 35. About reads versionName from package metadata.
- Existing signing certificate retained: `3fb413c86beccc6627a4c3be5de0a8cb4bfc15715d03ebbf2d02ab85ff037c73`.
- APK SHA-256: `cecd8ebcc30691eb8e1f9c8828c4de4f05b2b909721d84b9a8cfd123952725c7`.
- Version metadata only; app code and assets are unchanged, so runtime suites were not rerun. Earlier development-version sections below are historical.

# Version 4.3 · Gold vinyl launcher icon

- Replaced the black / red launcher record with a metallic gold vinyl design. Exported legacy icons at 48 / 72 / 96 / 144 / 192 px and adaptive foregrounds at 108 / 162 / 216 / 324 / 432 px with transparent padding. Adaptive dark background and launcher resource references are unchanged.
- Visually inspected the generated design and actual 192 px launcher asset; checked all ten PNG dimensions / RGBA output. Resource / Java / DEX compilation, alignment, and signature verification passed.
- APK metadata confirms Simple Player, package `simple.music.player`, version **4.3**, code **16**, minimum API **24**, target API **35**.
- Certificate remains `3fb413c86beccc6627a4c3be5de0a8cb4bfc15715d03ebbf2d02ab85ff037c73`.
- APK SHA-256: `fefca741ca823f3053b8ffcd5475aff2f09612f9749725ff76700f1f6f5a572c`.
- This is a launcher-art update; runtime suites were not rerun. Earlier layout and playback results below are historical.

# Version 4.2 · Simpler music library

- Replaced the large library heading / statistics and Continue Listening section with a compact Simple Player heading, search, underlined tabs, and one count / sort toolbar. Favorites and History remain available in the category tabs; the bottom player provides resume and Now Playing access.
- Song rows use 48 dp artwork, title / artist / duration, and the existing menu with 72 dp row height. Removed library row numbers and favorite prefixes; album / playlist details retain their numbered rows. Shuffle uses a smaller plain icon row. The compact player now has gold play / pause and next icons without a filled circle.
- Resource / Java / DEX compilation, alignment, production signing, and instrumentation compilation passed. Metadata confirms `simple.music.player`, version **4.2**, code **15**, minimum API **24**, target API **35**.
- **10 Android layout / navigation checks passed** on the API 35 emulator: fixture playback, embedded artwork, compact-player visibility / metadata, Up Next metadata, opening Now Playing, resuming through the compact play button, and Favorites / History navigation.
- Native preview: [simplified library](docs/screenshots/library-v4.2.png), using synthetic tracks and artwork in the Amethyst theme. Production APK contains no test audio. Large-font, landscape, and physical-device layouts were not exercised for this change.
- Playback, software processing, and file parsers are unchanged; their earlier results below are historical and were not rerun for this appearance update.
- Signing certificate remains `3fb413c86beccc6627a4c3be5de0a8cb4bfc15715d03ebbf2d02ab85ff037c73`.
- APK SHA-256: `0f900ac17dad2f8989425196a9a32d264a00d888917d9e14c9668a73f92629b9`.

# Private signing workflow · Build infrastructure

- Removed the original uploaded source ZIP from the current tracked files because it contained `musicplayer.keystore`. A private local backup was retained. No Git history was rewritten; the earlier key remains exposed through history / prior copies and requires a planned signing-key rotation. The distributed APK signing identity is unchanged.
- Added a manual, main-only GitHub Actions workflow with read-only repository permissions, fixed action commit references, and signing credentials scoped to the signing step. It decodes `MUSIC_KEYSTORE_BASE64` into a permission-restricted runner temporary file, passes passwords through existing `env:` signing options, deletes the key on success / failure, and uploads only the signed APK.
- Added ignore rules for environment files, signing properties, private key formats, secret directories, and Base64 keystore exports. Confirmed representative private filenames match these rules.
- YAML structure / trigger / action pins and Bash syntax checked. Exercised the actual signing-step shell locally: missing-secret rejection, invalid Base64 rejection and cleanup, successful APK signing using a disposable JKS with distinct randomly generated store / key passwords, successful cleanup, and no secret values in captured output all passed.
- Real GitHub repository secrets were not read, set, or printed. The hosted workflow was not run; the repository owner must configure its three signing secrets before dispatching it. The public `android` debug/test password remains a development convention, not a production secret.
- App code, version, and canonical `dist/Simple Player.apk` unchanged; SHA-256 remains `baeda5f099b385c21da615d39cc501456dd7e5f68c81f2df7071179351cd7bec`.

# Version 4.1 · Route-independent software effects

- Replaced the native effect dependency with MediaExtractor / MediaCodec decoding, per-stream software PCM filters, and AudioTrack output. Equalizer / bass processing occurs before Android output routing; native Equalizer, BassBoost, and LoudnessEnhancer are no longer required. This processes Simple Player audio, not other apps.
- Eight presets use five peaking filters plus an adjustable low shelf; filter state is isolated per channel / stream. Boosts reserve headroom, gain changes ramp, and a final limiter bounds peaks. Disabled effects and Flat at zero bass / gain preserve input PCM exactly.
- **56 host checks passed**, including frequency-response measurements at 44.1 / 48 / 96 kHz, positive gain, stereo isolation, bypass, seek-state reset, parameter bounds, all presets with maximum bass / gain, and the existing file/parser checks.
- **57 Android decoder checks passed** on API 35 for stereo WAV, AAC/M4A, FLAC, Vorbis/Ogg, Opus, 24-bit WAV, and multichannel AAC. Each format prepared, exposed duration, advanced its PCM playback head, sought while paused, resumed, drained, and completed with software processing enabled.
- **29 Android software-effects checks passed**: availability before decoding, saved / live settings, pause preservation, bounds, manual next, preloaded transitions, both crossfade streams, ReplayGain, bypass, and settings status.
- **22 existing Android playback checks passed**, including seek, pause, speed, queue operations, favorites / history, sleep-at-end, crossfade / gapless promotion, and media-browser categories. Transition tests verify active playback / position, not sample-accurate gapless alignment.
- Resource / Java / DEX compilation, alignment, instrumentation compilation, and release signature verification passed. A final compatibility-only Buffer cast was compiled and checked in bytecode to call legacy `java.nio.Buffer.position/limit`; its behavior is unchanged. API 24 was not run.
- Output uses 16-bit PCM. Sources with more than two channels are downmixed to stereo. Compressed-audio offload is bypassed. Hardware Bluetooth / USB route changes, manufacturer devices, battery use, physical output quality, and sample-accurate gapless boundaries remain unmeasured. Software processing removes the native effect-service dependency; it does not guarantee Android codec support or connectivity for every possible device.
- Native preset indexes reset to Off once because the new software collection differs. Bass strength persists. App identity is **Simple Player**, `simple.music.player`, version **4.1**, code **14**, minimum API **24**, target API **35**.
- Certificate remains `3fb413c86beccc6627a4c3be5de0a8cb4bfc15715d03ebbf2d02ab85ff037c73`.
- APK SHA-256: `baeda5f099b385c21da615d39cc501456dd7e5f68c81f2df7071179351cd7bec`.

# Version 4.0 · Audio effects repair

- Added the normal Android `MODIFY_AUDIO_SETTINGS` permission. Effects now attach after the player is prepared, track its actual nonzero audio session, and reuse that chain when settings change. Preloaded gapless / crossfade players share the active session; promotion retains the chain.
- Equalizer and bass setters validate platform control / enabled state. Settings show Android's actual preset and rounded bass strength, or an explicit unavailable message. Invalid inputs are bounded / rejected; an unavailable saved preset resets to Off. Newer ReplayGain requests invalidate older asynchronous results.
- Resource / Java / DEX compilation, APK alignment, production signing, and instrumentation compilation passed. **34 host feature checks passed**.
- **31 Android effects integration checks passed** on the API 35 emulator. Verified the normal permission, effect/session association, native enabled state and current preset, rounded bass strength, input bounds, paused-state preservation, manual track changes, shared sessions and retained effects across gapless / crossfade, negative ReplayGain attenuation, positive ReplayGain native target gain, disable behavior, and settings status.
- Native loudness target was confirmed at 300 mB for a synthetic +3 dB tag; the attenuation path used approximately 0.501 for a synthetic -6 dB tag. Test audio and tags are private fixtures, excluded from production. These are API/state checks, not physical measurements of speaker output. Manufacturer devices, Bluetooth routes, and subjective listening still need device testing.
- The emulator displayed System UI ANR alerts during capture; those previews were omitted. Native effect checks completed successfully. Earlier screenshots and test results below describe their stated versions.
- APK metadata confirms **Simple Player**, package `simple.music.player`, version **4.0**, code **13**, minimum API **24**, target API **35**.
- Certificate remains `3fb413c86beccc6627a4c3be5de0a8cb4bfc15715d03ebbf2d02ab85ff037c73`.
- APK SHA-256: `2093571c1516455c67083ce8a06dcf5c0078620aebc3d74b95d879947c91fb96`.

# Version 3.9 · Remove duplicate queue shortcut

- Removed the Queue icon from the Now Playing action row. The Up Next card and its queue icon still open the queue; the remaining three actions share the row equally.
- Resource / Java / DEX compilation, APK alignment, and signature verification passed. Metadata confirms package `simple.music.player`, version **3.9**, code **12**, minimum API **24**, target API **35**.
- Certificate remains `3fb413c86beccc6627a4c3be5de0a8cb4bfc15715d03ebbf2d02ab85ff037c73`.
- APK SHA-256: `c48c2885bd28184f5530eef754549c6c1726239615a674ddd9e667d44afe4eef`.
- This single-control removal was reviewed in source and built; emulator and physical-device suites were not rerun. Earlier results below are historical.

# Version 3.8 · Icon actions

- Converted Now Playing's favorite, lyrics / cover, queue, and sleep-timer chips to native ImageButtons in a single row. Preserved click handlers, favorite state, lyrics toggle state, and accessible timer status. Each action has a 48 dp touch target, a descriptive accessibility label, and a tooltip on API 26+.
- Resource / Java / DEX compilation, APK alignment, and signature verification passed. APK metadata confirms `simple.music.player`, version **3.8**, code **11**, minimum API **24**, target API **35**.
- Certificate remains `3fb413c86beccc6627a4c3be5de0a8cb4bfc15715d03ebbf2d02ab85ff037c73`.
- APK SHA-256: `ab4b13d75f86ce5230a3f3284883f3b9892351c33593a1dc510a5b376846067f`.
- This UI change was built and reviewed in source; emulator and physical-device tests were not rerun. Existing screenshots and test results below describe their stated versions.

# Version 3.7 · Simple play / pause button

- Now Playing's central play / pause control uses the theme's gold text tint and the standard borderless press feedback. Removed its filled circular background; retained the 72 dp touch target and existing playback listener / state updates.
- Resource / Java / DEX compilation, APK alignment, and signature verification passed. APK metadata confirms `simple.music.player`, version **3.7**, code **10**, minimum API **24**, target API **35**.
- The signing certificate is unchanged: `3fb413c86beccc6627a4c3be5de0a8cb4bfc15715d03ebbf2d02ab85ff037c73`.
- APK SHA-256: `35179c7978383d88381abd51ca61a9db117b6d5b0ac9947132089e1965462edb`.
- This appearance-only change was built and inspected in source. No new emulator or physical-device run was performed; earlier screenshots and functional test results below describe their stated versions.

# Version 3.6 · Now Playing lyrics validation

- Resource / Java / DEX compilation, APK alignment, and signature verification passed.
- **34 host feature checks passed**, including lyrics timing, malformed timestamps, metadata removal, and UTF-8 / UTF-16 decoding.
- **23 Android lyrics integration checks passed** on the API 35 emulator: inline display, automatic opening, linked UTF-16 text, seek highlighting, tap-to-seek without resuming paused playback, cover toggle, track changes, missing-file recovery, original-song attachment, plain text, and online-response parsing.
- Online lookup is explicit and uses HTTPS with bounded reads and timeouts; successful lyrics are privately cached. The service endpoint was reachable from the build host (HTTP 400 for a request without track metadata). Live song matching and Android network lookup were not tested end to end.
- Screenshots use synthetic lyrics and test audio; production builds contain no demo songs. Emulator launcher ANR alerts required dismissal during preview capture. Physical devices and other Android versions remain untested for this change.
- APK metadata confirms **Simple Player**, package `simple.music.player`, version **3.6**, code **9**, minimum API **24**, target API **35**. The new internet permission supports optional lyrics lookup.
- Signature retains certificate `3fb413c86beccc6627a4c3be5de0a8cb4bfc15715d03ebbf2d02ab85ff037c73`.
- APK SHA-256: `ddd7b1840eda097539258aea7d6ee2748c64ae72b1692ae5d7b36d90e18535c1`.

# Version 3.5 · Gold text validation

- Resource / Java / DEX compilation, APK alignment, and signature verification passed for the gold-text update.
- Primary and secondary text were checked against each theme's background, card, and raised surface; filled-button text was checked against its accent background. All combinations meet **4.5:1**. Minimum body-text contrast: Ember **7.74:1**, Amethyst **7.52:1**, Daylight **4.91:1**.
- Shared native app / dialog text colors, input hints, link colors, explicit app text colors, lyrics, and widget text use the updated gold palette. Theme control colors are unchanged.
- APK metadata confirms **Simple Player**, package `simple.music.player`, version **3.5**, code **8**; minimum API **24**, target API **35**.
- Signature retains certificate `3fb413c86beccc6627a4c3be5de0a8cb4bfc15715d03ebbf2d02ab85ff037c73`.
- APK SHA-256: `45319eaad60a0162382cd00fd755be563387d52fc5f120358df24986abd3d94c`.
- This reversible text-color change was built and checked statically. Emulator / physical-device suites were not rerun; screenshots and test results for earlier versions below are historical.

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
