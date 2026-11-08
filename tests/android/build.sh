#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
sdk="${ANDROID_HOME:?Set ANDROID_HOME}"
bt="$sdk/build-tools/35.0.0"
jar="$sdk/platforms/android-35/android.jar"
out="$PWD/build/instrumentation"
mkdir -p "$out/classes" "$out/dex"
"$bt/aapt2" link --manifest tests/android/AndroidManifest.xml -I "$jar" -A build/fixtures -o "$out/unsigned.apk" --min-sdk-version 24 --target-sdk-version 35
javac -source 8 -target 8 -bootclasspath "$jar:$bt/core-lambda-stubs.jar" -cp build/classes.jar -encoding UTF-8 -d "$out/classes" tests/android/PlayerIntegrationTests.java
jar cf "$out/tests.jar" -C "$out/classes" .
java -cp "$bt/lib/d8.jar" com.android.tools.r8.D8 --min-api 24 --lib "$jar" --classpath build/classes.jar --output "$out/dex" "$out/tests.jar"
(cd "$out/dex" && "$bt/aapt" add "$out/unsigned.apk" classes.dex)
"$bt/zipalign" -f -p 4 "$out/unsigned.apk" "$out/aligned.apk"
java -jar "$bt/lib/apksigner.jar" sign --ks build/debug.keystore --ks-pass pass:android --key-pass pass:android --out "$out/tests.apk" "$out/aligned.apk"
