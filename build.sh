#!/usr/bin/env bash
# Build helper for the Tuner app.
#
# This machine mounts ~/.gradle read-only, so Gradle's writable state (native
# libs, daemon, dependency cache) is redirected into the project workspace via
# GRADLE_USER_HOME, and the JDK is pinned to Android Studio's bundled Java 21.
#
# Usage:
#   ./build.sh ./gradlew assembleDebug
#   ./build.sh ./gradlew testDebugUnitTest
#   ./build.sh ./gradlew installDebug
set -e
cd "$(dirname "$0")"

export JAVA_HOME=/opt/android-studio/jbr
export GRADLE_USER_HOME="$PWD/.gradle-home"

# One-time seed from the machine's read-only Gradle cache (reflink = instant on btrfs).
if [ ! -d "$GRADLE_USER_HOME/caches/modules-2" ]; then
  mkdir -p "$GRADLE_USER_HOME"
  cp -a --reflink=auto "$HOME/.gradle/caches" "$GRADLE_USER_HOME/" 2>/dev/null || true
fi
if [ ! -d "$GRADLE_USER_HOME/wrapper/dists" ]; then
  mkdir -p "$GRADLE_USER_HOME"
  cp -a --reflink=auto "$HOME/.gradle/wrapper" "$GRADLE_USER_HOME/" 2>/dev/null || true
fi

exec "$@"
