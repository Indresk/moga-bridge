# Developer guide

How to run the Android dev loop, build an APK, read logs, and fix the usual problems. Protocol and architecture details live in [readme.md](./readme.md) and [agents.md](./agents.md).

## Prerequisites

- Rust stable with the Android targets: `rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android`
- Node.js and `pnpm`; run `pnpm install` once.
- Android SDK (with `platform-tools`), an installed NDK, and a JDK. The Tauri CLI finds the SDK/NDK under `~/Android/Sdk` if `ANDROID_HOME` is unset.
- A physical Android phone with **USB debugging** enabled (Bluetooth Classic cannot be tested on an emulator). `adb devices` must list it as `device`.
- A MOGA Pocket set to **Mode A**.

## Dev loop (hot reload on a USB-connected phone)

```sh
pnpm android:dev
```

`scripts/android-dev.sh` does this for you:

1. Checks that adb sees a device and that ports `1420` (Vite) and `1421` (HMR) are free.
2. Runs `adb reverse tcp:1420 tcp:1420` and `adb reverse tcp:1421 tcp:1421`.
3. Starts `pnpm tauri android dev --host 127.0.0.1`, which builds the Rust library, installs the app and launches it.

Why not plain `pnpm tauri android dev`? It points the phone at your PC's LAN IP, and the PC firewall usually drops that traffic. The app then shows `Webpage not available ... http://tauri.localhost/ ... net::ERR_CONNECTION_REFUSED` while the terminal shows no error (in dev, `tauri.localhost` proxies to the dev URL). With `adb reverse` the phone reaches Vite through `127.0.0.1` over USB.

Notes:

- React edits hot-reload. Rust edits rebuild and reinstall automatically. Changes to `build.rs`, capabilities or Kotlin files are not always picked up: stop and rerun `pnpm android:dev`.
- `adb reverse` rules disappear when the cable is unplugged or adb restarts; the script recreates them each run.
- Only one dev session can run: Vite needs ports 1420/1421 (`strictPort`). Extra arguments are forwarded to Tauri: `pnpm android:dev -- --release`.
- The first build after `cargo clean` takes several minutes.

### Testing the controller

1. Turn the Pocket on in Mode A, in pairing mode. Remove any stale pairing in Android Bluetooth settings.
2. In the app press **Escanear**, grant the "Nearby devices" permission (Android 6–11: location), pick the controller and confirm Android's pairing prompt (PIN, if asked: try `0000`).
3. Status turns **Connected**, the app jumps to the **Prueba** tab and shows buttons, both analog sticks and the raw report live.
4. For games: start the virtual-gamepad helper (below), keep **Mapeo → Gamepad virtual**, and switch to the game; the pad is silent while this app is on screen.
5. For keyboard output enable **MOGA Key Mapper** through "Configurar teclado Android" and focus a text field.

## Virtual gamepad helper

The default output creates a virtual gamepad through Android's `uinput` tool, which a normal app cannot run but the adb `shell` user can. Start the helper from the PC with the phone connected over USB:

```sh
pnpm android:uinput          # start (default); also: stop | status
```

It runs the app-generated `helper.sh` (`adb shell sh /sdcard/Android/data/dev.mogabridge.app/files/helper.sh`, also shown in the Mapeo tab), which starts a token-protected `nc … -L uinput -` detached on the phone (loopback only); it lives until the phone reboots. Open the app once first so the script and `helper.token` exist. The app shows whether it is reachable in **Mapeo**.

To test PPSSPP without the controller, a script can register its own virtual pad over `adb forward tcp:7777 tcp:7777` and send `inject` commands; the format is the same JSON stream the app uses (see `UinputBridge.kt`).

## Logs

```sh
adb logcat -c                                   # clear
adb logcat | grep -E "MogaRfcomm|Tauri/|RustStdoutStderr"
```

- `MogaUinput`: virtual gamepad helper registration and errors.
- `MogaConnection`: the connection notification.
- `MogaRfcomm`: which RFCOMM strategy connected (`RFCOMM connected with strategy=...`) or why each one failed.
- `Tauri/Console`: WebView console output; `Tauri/Plugin`: plugin command calls; `RustStdoutStderr`: Rust `eprintln!`.
- Protocol problems also reach the UI as `moga-error` events (`Rejected MOGA packet: ...`).

Screenshot of the phone: `adb exec-out screencap -p > shot.png`.

## Tests

```sh
cd src-tauri
cargo test          # protocol parser, command bytes, mock driver, mapping validation
cargo check --lib   # desktop compile check (does not compile the Android-only code or Kotlin)
```

Android-only Rust (`#[cfg(target_os = "android")]`) and the Kotlin plugin are only compiled by the Android build.

## Building an APK

Debug (installable directly, signed with the debug key):

```sh
pnpm tauri android build --debug --apk                  # universal APK, all ABIs
pnpm tauri android build --debug --apk --target aarch64 # arm64 only (faster, smaller)
```

Output: `src-tauri/gen/android/app/build/outputs/apk/<variant>/debug/app-<variant>-debug.apk` (e.g. `apk/arm64/debug/app-arm64-debug.apk`, `apk/universal/debug/app-universal-debug.apk`).

Release (signed, via script):

```sh
pnpm android:sign --init    # first time only: creates ~/.moga-release.keystore (asks for a password and details)
pnpm android:sign --build   # builds the release APK, aligns it, signs it and verifies it
pnpm android:sign           # re-sign the APK that is already built
```

`scripts/sign-apk.sh` runs `zipalign` (before signing) and `apksigner sign`/`verify` from the newest installed build-tools, and writes `src-tauri/gen/android/app/build/outputs/apk/universal/release/moga-release-signed.apk`. Variables: `KEYSTORE` (default `~/.moga-release.keystore`), `KEY_ALIAS` (default `moga`), `KS_PASS` (otherwise apksigner prompts for the password), `ANDROID_HOME`.

The keystore is the app's identity. Keep it **outside the repository**, back it up, and never commit it: without it you cannot ship updates that install over an existing install.

Plain `pnpm tauri android build --apk` / `--aab` still produce an *unsigned* APK (`app-universal-release-unsigned.apk`, not installable) and an unsigned bundle, because the Gradle project has no `signingConfig`. A Play Store `.aab` must be signed separately (`jarsigner` or Play App Signing).

Install on the phone: `adb install -r path/to/app.apk`.

## App identifier

The bundle identifier / Android `applicationId` is `dev.mogabridge.app` (`src-tauri/tauri.conf.json`). It also appears in the Kotlin package (`gen/android/app/src/main/java/dev/mogabridge/app/`), `namespace`/`applicationId` in `gen/android/app/build.gradle.kts`, and `PLUGIN_ID` in `src-tauri/src/android.rs`. If you change it, update all of them. A different identifier installs as a different app, so uninstall the old one from the phone.

Do not use a debug build as the "dev" environment: it embeds the built frontend (`dist/`), so UI changes need a rebuild, and `tauri.localhost` serves the bundled assets instead of Vite.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `Webpage not available ... tauri.localhost ... ERR_CONNECTION_REFUSED` | Phone cannot reach Vite. Use `pnpm android:dev` (adb reverse). Check `adb reverse --list`. |
| `Port 1420 is already in use` | A leftover `tauri android dev` or Vite is running. Find it with `ps -eo pid,cmd \| grep -E "[t]auri.js android\|[v]ite/bin/vite"` and `kill <pid>`. Avoid `pkill -f` patterns that also match your own shell. |
| `moga-android.registerListener not allowed. Plugin not found` | The runtime plugin must be declared in `build.rs` (`InlinedPlugin`) and `moga-android:default` granted in `capabilities/default.json`. |
| `Blocking waiting for file lock on Android` | Another cargo/tauri Android build is running; wait or kill it. |
| Controller not listed after Escanear | Mode A (not HID/B), controller in pairing mode, permissions granted, Bluetooth on. Name must start with `BD&A`/`BDA`/`MOGA` and not contain `HID`. |
| `All legacy MOGA RFCOMM connection methods failed` | Read the per-strategy reasons in the message and under logcat tag `MogaRfcomm`; remove the stale bond and retry. |
| App loads old UI/Rust | Stale build artifacts: `cd src-tauri && cargo clean -p moga-tauri --target aarch64-linux-android`, then rerun. |
| Gradle `--offline` compile of Kotlin fails instantly | Use the Tauri build/dev commands; they set up the Gradle project properties Rust needs. |
