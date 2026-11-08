#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/tests
javac -encoding UTF-8 -d build/tests tests/stubs/android/net/Uri.java \
  src/com/auss/musicplayer/{Song,Lyrics,PlaylistIO,Mp3Metadata}.java tests/com/auss/musicplayer/FeatureTests.java
java -cp build/tests com.auss.musicplayer.FeatureTests
