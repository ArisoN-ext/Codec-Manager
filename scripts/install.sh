#!/usr/bin/env bash
# Install the debug APK on the connected device and launch it.
set -euo pipefail

cd "$(dirname "$0")/.."

apk="app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$apk" ]; then
  echo "APK not found, building…"
  ./scripts/build.sh debug
fi

adb install -r "$apk"
adb shell am start -n dev.arison.codecmanager.debug/dev.arison.codecmanager.MainActivity
