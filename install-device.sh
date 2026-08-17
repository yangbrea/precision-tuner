#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
apk_path="$project_dir/app/build/outputs/apk/debug/app-debug.apk"
package_name="com.example.tunner"
requested_serial="${1:-}"

if ! command -v adb >/dev/null 2>&1; then
    echo "错误：未找到 adb，请先安装 Android platform-tools 或加入 PATH。" >&2
    exit 1
fi

if [[ -n "$requested_serial" ]]; then
    device_serial="$requested_serial"
else
    mapfile -t physical_devices < <(
        adb devices | awk 'NR > 1 && $2 == "device" && $1 !~ /^emulator-/ { print $1 }'
    )
    if [[ ${#physical_devices[@]} -eq 0 ]]; then
        echo "错误：没有检测到已授权的 USB 真机。" >&2
        adb devices -l >&2
        exit 1
    fi
    if [[ ${#physical_devices[@]} -gt 1 ]]; then
        echo "错误：检测到多台 USB 真机，请传入序列号：./install-device.sh SERIAL" >&2
        adb devices -l >&2
        exit 1
    fi
    device_serial="${physical_devices[0]}"
fi

device_state="$(adb -s "$device_serial" get-state 2>/dev/null || true)"
if [[ "$device_state" != "device" ]]; then
    echo "错误：设备 $device_serial 未连接或尚未授权。" >&2
    adb devices -l >&2
    exit 1
fi

echo "构建 Debug APK..."
"$project_dir/gradlew" -p "$project_dir" assembleDebug

echo "安装到设备 $device_serial..."
adb -s "$device_serial" install -r -g "$apk_path"

installed_path="$(adb -s "$device_serial" shell pm path "$package_name")"
if [[ -z "$installed_path" ]]; then
    echo "错误：安装命令完成，但设备上未找到 $package_name。" >&2
    exit 1
fi

echo "安装成功：$installed_path"
