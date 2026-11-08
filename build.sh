#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
sdk="${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK directory}"
tools="${ANDROID_BUILD_TOOLS:-$sdk/build-tools/35.0.0}"
platform="$sdk/platforms/android-35/android.jar"
out="$PWD/build"
mkdir -p "$out/gen" "$out/classes" "$out/dex"
find "$out/gen" "$out/classes" "$out/dex" -type f -delete
"$tools/aapt2" compile --dir res -o "$out/res.zip"
"$tools/aapt2" link -o "$out/unsigned.apk" -I "$platform" --manifest AndroidManifest.xml --java "$out/gen" \
  --min-sdk-version 24 --target-sdk-version 35 --version-code 10 --version-name 3.7 "$out/res.zip"
find src "$out/gen" -name '*.java' > "$out/sources.txt"
javac -source 8 -target 8 -bootclasspath "$platform:$tools/core-lambda-stubs.jar" -encoding UTF-8 -Xlint:-options -d "$out/classes" @"$out/sources.txt"
jar cf "$out/classes.jar" -C "$out/classes" .
java -cp "$tools/lib/d8.jar" com.android.tools.r8.D8 --release --min-api 24 --lib "$platform" --output "$out/dex" "$out/classes.jar"
(cd "$out/dex" && "$tools/aapt" add "$out/unsigned.apk" classes.dex)
"$tools/zipalign" -f -p 4 "$out/unsigned.apk" "$out/aligned.apk"
# Use a local debug key by default. A release signing key must be supplied privately.
key="${MUSIC_SIGNING_KEY:-$out/debug.keystore}"
if [[ ! -f "$key" ]]; then
  if [[ -n "${MUSIC_SIGNING_KEY:-}" ]]; then echo 'MUSIC_SIGNING_KEY was not found' >&2; exit 1; fi
  keytool -genkeypair -keystore "$key" -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass android -keypass android -dname 'CN=Android Debug,O=Android,C=US'
fi
if [[ -n "${MUSIC_SIGNING_KEY:-}" ]]; then
  : "${MUSIC_STORE_PASSWORD:?Set MUSIC_STORE_PASSWORD}"
  : "${MUSIC_KEY_PASSWORD:?Set MUSIC_KEY_PASSWORD}"
  java -jar "$tools/lib/apksigner.jar" sign --ks "$key" --ks-pass env:MUSIC_STORE_PASSWORD \
    --key-pass env:MUSIC_KEY_PASSWORD --out "$out/Simple Player.apk" "$out/aligned.apk"
else
  java -jar "$tools/lib/apksigner.jar" sign --ks "$key" --ks-pass pass:android --key-pass pass:android \
    --out "$out/Simple Player.apk" "$out/aligned.apk"
fi
java -jar "$tools/lib/apksigner.jar" verify --verbose "$out/Simple Player.apk"
echo "Built $out/Simple Player.apk"
