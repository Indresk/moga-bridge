#!/bin/sh
# Start/stop the loopback helper that lets the app create a virtual gamepad without root.
#
#   pnpm android:uinput           start (default)
#   pnpm android:uinput stop      stop
#   pnpm android:uinput status    show whether it is running
#
# This is a thin wrapper: the app generates the real script (`helper.sh`) and a secret token in
# its own storage on the phone. An end user without this repository runs the same thing with
#   adb shell sh /sdcard/Android/data/dev.mogabridge.app/files/helper.sh
# (the Mapeo tab shows the exact command). Open the app once first so the files exist.
set -eu

ADB="${ADB:-$(command -v adb || echo "${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb")}"
PACKAGE="${APP_PACKAGE:-dev.mogabridge.app}"
SCRIPT="/sdcard/Android/data/$PACKAGE/files/helper.sh"

if ! "$ADB" get-state >/dev/null 2>&1; then
  echo "No Android device detected by adb (check USB debugging)." >&2
  exit 1
fi

exec "$ADB" shell sh "$SCRIPT" "${1:-start}"
