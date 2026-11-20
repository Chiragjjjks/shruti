#!/usr/bin/env bash
# Builds the installable APK into dist/Shruti.apk.
# Any book files you placed under bundle/books/<book-id>/ are packed in.
set -euo pipefail
cd "$(dirname "$0")/.."

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  for j in "$HOME"/Android/jdk/jdk-17*; do [ -x "$j/bin/javac" ] && export JAVA_HOME="$j"; done
fi
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties

echo "Bundled book files:"
find bundle/books -type f ! -name .gitkeep -printf "  %p (%s bytes)\n" 2>/dev/null || true

./gradlew --console=plain assembleRelease
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk dist/Shruti.apk
echo "APK ready: $(pwd)/dist/Shruti.apk ($(du -h dist/Shruti.apk | cut -f1))"
