#!/bin/bash

ADB="/home/yoda/Android/Sdk/platform-tools/adb"
APP_ID="com.troubashare"

DEVICE=$("$ADB" devices | grep -v "List of" | grep "device$" | awk '{print $1}' | head -1)
if [ -z "$DEVICE" ]; then
    echo "ERROR: No device connected."
    exit 1
fi

echo "==> Streaming logs from $DEVICE (Ctrl+C to stop)..."
"$ADB" -s "$DEVICE" logcat --pid=$("$ADB" -s "$DEVICE" shell pidof "$APP_ID" 2>/dev/null) 2>/dev/null || \
"$ADB" -s "$DEVICE" logcat -s "$APP_ID"
