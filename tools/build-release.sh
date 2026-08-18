#!/usr/bin/env bash
#
# Build the signed release APK in one step (CREPE enabled, arm64-only),
# signed with the project debug keystore.
#
# Usage:
#   ./tools/build-release.sh [-PcrepeModel=small]
#
# Output: $PROJECT_DIR/precision-tuner-v<VERSION>.apk (signed, verified)
#
# The GitHub publish step needs a token and is done via the API
# (draft release -> upload APK asset -> publish).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

EXTRA_ARGS="${1:--PtinyCrepeEnabled=true}"

# Resolve the newest apksigner from the SDK in local.properties.
SDK_DIR="$(grep '^sdk.dir' local.properties | cut -d= -f2 | sed 's/\\\\/\//g')"
APKSIGNER="$(find "$SDK_DIR/build-tools" -name apksigner -type f 2>/dev/null | sort -V | tail -1)"
if [[ -z "$APKSIGNER" ]]; then
    echo "错误：未找到 apksigner（检查 local.properties 的 sdk.dir）" >&2
    exit 1
fi

VERSION="$(grep 'versionName' app/build.gradle.kts | grep -oE '"[0-9.]+"' | head -1 | tr -d '"')"
OUT="$PROJECT_DIR/precision-tuner-v$VERSION.apk"

echo "构建 release（$EXTRA_ARGS）..."
bash "$PROJECT_DIR/build.sh" ./gradlew assembleRelease "$EXTRA_ARGS"

echo "签名并验证..."
"$APKSIGNER" sign \
    --ks keystore/debug.keystore \
    --ks-key-alias androiddebugkey \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out "$OUT" \
    app/build/outputs/apk/release/app-release-unsigned.apk
"$APKSIGNER" verify --print-certs "$OUT" 2>/dev/null | grep -E "Signer #1 certificate" || true

echo "完成：$OUT"
