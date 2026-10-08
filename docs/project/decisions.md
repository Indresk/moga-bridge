# Decisions

Why the project is shaped the way it is, including what was tried and rejected. Each entry says what we do, why, and what would change our mind.

## Tauri with a Kotlin plugin, not a pure-Rust Bluetooth stack
Android Bluetooth Classic is only reachable through the Android SDK. Rust owns the protocol and session (portable, testable); a Kotlin plugin owns the platform. The two talk through Tauri's plugin handle (JNI), never a second JNI mechanism around the same socket. *Change if:* a maintained Rust Android Bluetooth Classic crate appears.

## Rust drives the read loop; Kotlin queues bytes
A Kotlin reader thread blocks on the `BluetoothSocket` and queues chunks; Rust's `Read` calls a blocking plugin `read` on a dedicated executor (never the main thread). Rust does not busy-poll and mirrors the legacy loop (*listen* before each read). Cost: three JNI calls per changed report — accepted, and kept cheap by filtering ([overview.md](../architecture/overview.md#the-hot-path)).

## Replicate the legacy connection strategies
Pairing with public `createBond()`, then the legacy socket order with channel 1 and retries, plus one extra strategy. We follow the original because the Pocket's SDP record is unreliable and the original is the only known-good reference. On Android 16 the reflected constructor is blocked and reflected channel 1 works, so the fallbacks are insurance. *Change if:* broad device testing shows strategies that never succeed.

## Never bypass Android's pairing UI
`setPin()`/`abortBroadcast()` need privileged permissions and hide a security prompt; the legacy app never used them.

## The "pads" are sticks
Bytes 5–9 of the report are two analog sticks and their digitised directions. We first read them as a D-pad plus unknown axes and dropped the analog data; a user observation and the legacy app's own text corrected this. The raw report travels with every state so future findings are possible. *Lesson:* trust hardware observations over inferred naming.

## Virtual gamepad through an adb-started `uinput` helper
Options considered: (1) the keyboard input method — reaches only focused text fields, no analog; (2) accessibility touch injection — touch only, fragile; (3) a Bluetooth HID device role — presents the phone to *another* host; (4) root `uinput` like the legacy app — excludes most users; (5) the adb `shell` identity, which is allowed to use `/system/bin/uinput`. (5) works without root and was **verified** in games. The keyboard output stays as a fallback.

## Loopback helper protected by a per-install token
The first version listened on `127.0.0.1` without authentication; any local app could inject input. The helper now demands a secret (stored in the app's external private directory, readable by the app and by `adb shell`, not by other apps on Android 11+) as the first line of each connection and drops it otherwise. Known limits are on [virtual-gamepad.md](../architecture/virtual-gamepad.md#security). *Change to:* a UID-checked `app_process` daemon.

## The helper generates its own files
`helper.sh` and `helper.token` are written by the app, so an end user only needs `adb` and one command, not this repository. The same script is used by the developer wrapper (`pnpm android:uinput`).

### Helper without a PC
Not built yet. Wireless debugging (in-app ADB client): no extra app, needs Android 11+ and Wi-Fi, large implementation (pairing and TLS) and varying vendor behaviour. Shizuku: much less code, but an extra app the user must install and restart after each reboot, plus a third-party dependency. Keeping the PC path available regardless.

## Stream the live state on demand, and filter repeats
The UI needs the controller state only on the Test tab. Emitting and rendering it everywhere wastes CPU and, in the background, floods a paused WebView. The backend drops unchanged reports, emits `moga-state` only while requested, hands the latest state to a new listener, and the live state lives in one hook so the rest of the UI does not re-render.

## Reconnect and layout/isolation changes replay the last state
Because repeats are filtered upstream, the bridge keeps the latest state and re-sends it after a layout change, after isolation ends and after reconnecting.

## Spanish UI, English code and developer docs
The current audience speaks Spanish; the code base targets international contributors. Strings are inline for now; extracting them is on the [roadmap](roadmap.md).

## Neutral identity
Application id `dev.mogabridge.app` and name *MOGA Bridge*; the original app's palette and glyphs are used as a homage (not a clone). The MOGA name describes the supported hardware.

## No battery meter
There is no known source: the legacy app has none, the report has no documented field and Android lists the device with SPP only. We chose to show nothing rather than invent a value.

## Notification via a connected-device foreground service
Keeps the process important while a controller is connected and tells the user the bridge is running. It must be started while the app is visible, hence it starts when the user taps *Conectar*.

## Do not store personal information in the repository
The identifier, package names and scripts avoid personal names; keystores live outside the repo.
