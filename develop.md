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
3. Status must turn **Connected** and the "Estado del mando" panel shows `responseId`, buttons, d-pad and raw axes live.
4. For keyboard output enable **MOGA Key Mapper** through "Configurar teclado Android" and focus a text field.

## Logs

```sh
adb logcat -c                                   # clear
adb logcat | grep -E "MogaRfcomm|Tauri/|RustStdoutStderr"
```

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

Release:

```sh
pnpm tauri android build --apk          # APK
pnpm tauri android build --aab          # Play Store bundle
```

Release output is **unsigned** (`apk/universal/release/app-universal-release-unsigned.apk`). Sign it before installing, for example:

```sh
BT=~/Android/Sdk/build-tools/<version>
$BT/zipalign -p -f 4 app-universal-release-unsigned.apk app-aligned.apk
$BT/apksigner sign --ks my-release.keystore --out moga-tauri-release.apk app-aligned.apk
```

Install on the phone: `adb install -r path/to/app.apk`.

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
