#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ADB="/home/yoda/Android/Sdk/platform-tools/adb"
APP_ID="com.troubashare"
APK_PATH="$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"

echo "==> Checking device..."
DEVICE=$("$ADB" devices | grep -v "List of" | grep "device$" | awk '{print $1}' | head -1)
if [ -z "$DEVICE" ]; then
    echo "ERROR: No device connected. Connect your tablet and try again."
    exit 1
fi
echo "    Device: $DEVICE"

echo "==> Building..."
cd "$SCRIPT_DIR"
./gradlew assembleDebug

echo "==> Installing..."
"$ADB" -s "$DEVICE" install -r "$APK_PATH"

echo "==> Starting..."
"$ADB" -s "$DEVICE" shell am start -n "$APP_ID/.MainActivity"

echo "==> Done."
