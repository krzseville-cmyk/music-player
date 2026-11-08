#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/tests
find build/tests -type f -delete
javac -encoding UTF-8 -d build/tests tests/stubs/android/net/Uri.java \
  src/simple/music/player/{Song,Lyrics,LyricsText,PlaylistIO,Mp3Metadata,SoftwareEffects}.java tests/simple/music/player/FeatureTests.java
java -cp build/tests simple.music.player.FeatureTests
