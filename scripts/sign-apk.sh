#!/bin/sh
# Build (optional), align and sign the release APK.
#
#   pnpm android:sign            sign the already-built unsigned release APK
#   pnpm android:sign --build    run `tauri android build --apk` first
#   pnpm android:sign --init     create the release keystore if it does not exist, then sign
#
# Environment (all optional):
#   KEYSTORE     keystore path            (default: ~/.moga-release.keystore)
#   KEY_ALIAS    key alias                (default: moga)
#   KS_PASS      keystore password        (default: prompted by apksigner)
#   ANDROID_HOME SDK location             (default: ~/Android/Sdk)
#
# The keystore is the app's identity: keep it OUTSIDE the repository and back it up.
# Without it you cannot ship updates that install over this build.
set -eu

cd "$(dirname "$0")/.."

SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
KEYSTORE="${KEYSTORE:-$HOME/.moga-release.keystore}"
KEY_ALIAS="${KEY_ALIAS:-moga}"
OUT_DIR="src-tauri/gen/android/app/build/outputs/apk/universal/release"
UNSIGNED="$OUT_DIR/app-universal-release-unsigned.apk"
ALIGNED="$OUT_DIR/app-universal-release-aligned.apk"
SIGNED="$OUT_DIR/moga-release-signed.apk"

BUILD=0
INIT=0
for arg in "$@"; do
  case "$arg" in
    --build) BUILD=1 ;;
    --init) INIT=1 ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done

# Newest installed build-tools (needs zipalign and apksigner).
BT="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -n 1)"
if [ -z "$BT" ] || [ ! -x "$BT/apksigner" ]; then
  echo "No Android build-tools with apksigner found under $SDK/build-tools." >&2
  exit 1
fi

if [ ! -f "$KEYSTORE" ]; then
  if [ "$INIT" -eq 1 ]; then
    echo "Creating keystore $KEYSTORE (you will be asked for a password and your details)."
    keytool -genkeypair -v -keystore "$KEYSTORE" -alias "$KEY_ALIAS" \
      -keyalg RSA -keysize 2048 -validity 10000
    echo "Keystore created. Back it up and do not commit it."
  else
    echo "Keystore not found: $KEYSTORE" >&2
    echo "Create it with: pnpm android:sign --init   (or set KEYSTORE=/path/to/file)" >&2
    exit 1
  fi
fi

if [ "$BUILD" -eq 1 ]; then
  pnpm tauri android build --apk
fi

if [ ! -f "$UNSIGNED" ]; then
  echo "Unsigned APK not found: $UNSIGNED" >&2
  echo "Build it first: pnpm android:sign --build" >&2
  exit 1
fi

rm -f "$ALIGNED" "$SIGNED" "$SIGNED.idsig"

# Align BEFORE signing; aligning afterwards invalidates the signature.
"$BT/zipalign" -p -f 4 "$UNSIGNED" "$ALIGNED"

if [ -n "${KS_PASS:-}" ]; then
  "$BT/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
    --ks-pass env:KS_PASS --out "$SIGNED" "$ALIGNED"
else
  "$BT/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
    --out "$SIGNED" "$ALIGNED"
fi
rm -f "$ALIGNED"

"$BT/apksigner" verify --verbose "$SIGNED"
echo
echo "Signed APK: $SIGNED"
echo "Install:    adb install -r $SIGNED"
