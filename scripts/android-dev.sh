#!/bin/sh
# Start `tauri android dev` for a USB-connected device.
#
# The PC firewall usually blocks the phone from reaching Vite on the LAN IP, so the
# dev server is exposed through `adb reverse` and addressed as 127.0.0.1 on the phone.
# `adb reverse` rules are lost when the cable is unplugged or adb restarts, hence
# they are re-created on every run.
set -eu

ADB="${ADB:-$(command -v adb || echo "${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb")}"
PORTS="1420 1421"

if ! "$ADB" get-state >/dev/null 2>&1; then
  echo "No Android device detected by adb (check USB debugging)." >&2
  exit 1
fi

for port in $PORTS; do
  if (ss -ltn 2>/dev/null | grep -q ":$port "); then
    echo "Port $port is already in use (leftover tauri/vite process?). Free it and retry." >&2
    exit 1
  fi
  "$ADB" reverse "tcp:$port" "tcp:$port" >/dev/null
done

exec pnpm tauri android dev --host 127.0.0.1 "$@"
