# Getting started

How to get the app running on a phone with hot reload. Plan for about 15 minutes plus a first Rust/Android build of several minutes.

## Prerequisites

| Tool | Notes |
|---|---|
| Rust (stable) | `rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android` |
| Node.js + [pnpm](https://pnpm.io) | `pnpm install` also installs the project-local Tauri CLI. |
| Android SDK | Platform-tools (`adb`), build-tools, a platform. The CLI finds `~/Android/Sdk` if `ANDROID_HOME` is unset. |
| Android NDK | Installed through the SDK manager. |
| JDK | One recent enough for the Android Gradle Plugin. |
| An Android phone | **Physical** — Bluetooth Classic cannot be tested on an emulator. Enable *Developer options* and *USB debugging*; `adb devices` must list it as `device`. |
| A MOGA Pocket (Mode A) | Optional for most work (see [testing-and-debugging.md](testing-and-debugging.md#driving-the-virtual-gamepad-from-a-pc)). |

## First run

```sh
git clone <repository> && cd moga-tauri
pnpm install
pnpm android:dev
```

`pnpm android:dev` (`scripts/android-dev.sh`) does the following:

1. Checks that `adb` sees a device and that ports **1420** (Vite) and **1421** (HMR) are free.
2. Runs `adb reverse tcp:1420 tcp:1420` and `adb reverse tcp:1421 tcp:1421`.
3. Starts `pnpm tauri android dev --host 127.0.0.1`: it builds the Rust library, installs the app and launches it, and starts Vite.

### Why not plain `pnpm tauri android dev`?

By default the CLI points the phone at your PC's LAN IP. A desktop firewall usually drops that traffic, so the app shows `Webpage not available … http://tauri.localhost/ … net::ERR_CONNECTION_REFUSED` while the terminal prints no error (in dev, `tauri.localhost` proxies to the dev URL). With `adb reverse` the phone reaches Vite through its own `127.0.0.1` over USB, no firewall change needed. The reverse rules disappear when the cable is unplugged or adb restarts; the script recreates them.

## The virtual-gamepad helper

The default output needs the helper running on the phone (see [virtual-gamepad.md](../architecture/virtual-gamepad.md)). Open the app once (it generates the helper files), then:

```sh
pnpm android:uinput           # start (default)
pnpm android:uinput status    # also: stop
```

It lives until the phone reboots. The *Mapeo* tab shows whether the app can reach it.

## Scripts

| Command | Does |
|---|---|
| `pnpm android:dev` | The dev loop above. Extra arguments go to Tauri. |
| `pnpm android:uinput [start\|stop\|status]` | Controls the helper through `adb`. |
| `pnpm android:sign [--init\|--build]` | Aligns, signs and verifies the release APK ([building-and-signing.md](building-and-signing.md)). |
| `python3 scripts/virtual-pad.py …` | Drives the virtual gamepad from the PC without a controller. |
| `pnpm build` | Builds the frontend only (also a quick compile check). |
| `pnpm tauri dev` | Desktop shell: the UI runs, every Bluetooth operation reports "Android only". |

## Day-to-day workflow

- **React edits** hot-reload on the phone.
- **Rust edits** are rebuilt and reinstalled by the watcher.
- **Kotlin, `build.rs`, capability, manifest or resource edits** are not always picked up: stop and rerun `pnpm android:dev`.
- Only **one** dev session at a time (Vite uses `strictPort`). Stop leftovers by PID, not with `pkill -f` patterns that match your own shell.
- The first build after `cargo clean` takes several minutes. If the running app looks stale: `cd src-tauri && cargo clean -p moga-tauri --target aarch64-linux-android`, then rerun.
- If a *debug* build conflicts with an installed *signed* build of the same package, Android refuses the update (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`); uninstall the old app first.

## Phone notes

- On Xiaomi/HyperOS, `adb shell input …` is blocked unless *USB debugging (Security settings)* is enabled, and the system may restrict background apps; see [testing-and-debugging.md](testing-and-debugging.md#troubleshooting).
- Grant *Nearby devices* (and *Notifications* on Android 13+) when the app asks.

## Repository tour

See [README.md](../../README.md#repository-layout) and [architecture/overview.md](../architecture/overview.md).
