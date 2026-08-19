#!/usr/bin/env bash
set -euo pipefail

# Install the Tuner debug build over wireless debugging (adb pair/connect).
#
# The AI-assisted CREPE engine is a default feature: the build bundles the
# small TFLite model unless explicitly hidden.
#
# Usage:
#   ./install-wireless.sh                                     # interactive: pair + connect + build + install
#   ./install-wireless.sh --connect IP:PORT                   # already paired, just connect (build + install)
#   ./install-wireless.sh --pair IP:PAIR_PORT CODE --connect IP:CONNECT_PORT
#   ./install-wireless.sh --connect IP:PORT --no-crepe        # DSP-only build (all ABIs, no model)
#   ./install-wireless.sh --connect IP:PORT --tiny-crepe      # explicit CREPE build (default, arm64-only)
#
# Wireless debugging steps on the phone:
#   设置 → 开发者选项 → 无线调试 → 开启；点击「使用配对码配对设备」得到
#   IP:配对端口 与 6 位配对码；配对成功后回到无线调试主界面查看连接用的
#   「IP 地址和端口」（与配对端口不同）。

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
apk_path="$project_dir/app/build/outputs/apk/debug/app-debug.apk"
package_name="com.precisiontuner"

pair_target=""
pair_code=""
connect_target=""
# CREPE is on by default; --no-crepe hides it.
tiny_crepe_enabled=true

while [[ $# -gt 0 ]]; do
    case "$1" in
        --pair)
            pair_target="${2:-}"
            pair_code="${3:-}"
            if [[ -z "$pair_target" || -z "$pair_code" ]]; then
                echo "错误：--pair 需要 IP:配对端口 和 配对码两个参数。" >&2
                exit 1
            fi
            shift 3 ;;
        --connect)
            connect_target="${2:-}"
            if [[ -z "$connect_target" ]]; then
                echo "错误：--connect 需要 IP:连接端口 参数。" >&2
                exit 1
            fi
            shift 2 ;;
        --tiny-crepe) tiny_crepe_enabled=true; shift ;;
        --no-crepe) tiny_crepe_enabled=false; shift ;;
        *)
            echo "错误：无法识别参数 $1" >&2
            exit 1 ;;
    esac
done

if ! command -v adb >/dev/null 2>&1; then
    echo "错误：未找到 adb，请先安装 Android platform-tools 或加入 PATH。" >&2
    exit 1
fi

# Gradle needs a JDK; prefer Android Studio's bundled one when present and
# JAVA_HOME is unset (same pin as build.sh).
if [[ -z "${JAVA_HOME:-}" && -d /opt/android-studio/jbr ]]; then
    export JAVA_HOME=/opt/android-studio/jbr
fi

# ---- 配对（参数或交互） ----
if [[ -n "$pair_target" ]]; then
    echo "配对 $pair_target ..."
    adb pair "$pair_target" "$pair_code"
    echo "配对成功。"
elif [[ -z "$connect_target" ]]; then
    echo "请在手机上开启 设置 → 开发者选项 → 无线调试，"
    echo "然后点击「使用配对码配对设备」，并输入显示的信息："
    read -r -p "  IP:配对端口（例如 192.168.2.35:41795）: " pair_target
    read -r -p "  6 位配对码: " pair_code
    echo "配对 $pair_target ..."
    adb pair "$pair_target" "$pair_code"
    echo "配对成功。"
fi

# ---- 连接（交互兜底） ----
if [[ -z "$connect_target" ]]; then
    echo ""
    echo "配对成功后，回到无线调试主界面查看连接用的「IP 地址和端口」（与配对端口不同）。"
    read -r -p "  IP:连接端口（例如 192.168.2.35:39423）: " connect_target
fi

echo "连接 $connect_target ..."
adb connect "$connect_target" >/dev/null 2>&1 || true
sleep 1

device_state="$(adb -s "$connect_target" get-state 2>/dev/null || true)"
if [[ "$device_state" != "device" ]]; then
    echo "错误：无法连接 $connect_target（未配对？网络不通？）。" >&2
    adb devices -l >&2
    exit 1
fi
model="$(adb -s "$connect_target" shell getprop ro.product.model 2>/dev/null || true)"
echo "已连接：${model:-unknown} ($connect_target)"

# ---- 构建 ----
gradle_args=(assembleDebug)
if [[ "$tiny_crepe_enabled" == true ]]; then
    gradle_args=(-PtinyCrepeEnabled=true assembleDebug)
fi
if [[ "$tiny_crepe_enabled" == true ]]; then
    echo "构建 Debug APK（含 CREPE 模型）..."
else
    echo "构建 Debug APK（DSP-only，不含 CREPE）..."
fi
GRADLE_USER_HOME="$project_dir/.gradle-home" \
    "$project_dir/gradlew" -p "$project_dir" "${gradle_args[@]}"

# ---- 安装 ----
echo "安装到 $connect_target ..."
adb -s "$connect_target" install -r -g "$apk_path"

installed_path="$(adb -s "$connect_target" shell pm path "$package_name")"
if [[ -z "$installed_path" ]]; then
    echo "错误：安装命令完成，但设备上未找到 $package_name。" >&2
    exit 1
fi
echo "安装成功：$installed_path"
