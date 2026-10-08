# Legacy app and reference analysis

The behaviour of this project is anchored to two read-only references that are **not part of this repository**: the original app *MOGA Universal Driver* 3.1.4 (package `net.obsidianx.android.mogaime`, decompiled with jadx 1.5.6 into a local `decompilado-moga-universal/` folder) and the Linux implementation [`moga-uinput`](https://github.com/jakobend/moga-uinput) (MIT, Jakob Endrikat). Where to get both and how to recreate them: [references.md](references.md). This page records what they do and how we differ; file and class names below refer to those local copies.

## What the original app does

| Area | Legacy behaviour (class) | Ours |
|---|---|---|
| Device names | Exact `BD&A` (Pocket) and `Moga Pro` (`DeviceType`, `BluetoothState`). | Case-insensitive prefix `BD&A`/`BDA`/`MOGA`, no `HID`, using the inquiry name (as `moga-uinput`). |
| Discovery | `startDiscovery`; `ACTION_FOUND`/`NAME_CHANGED`/`BOND_STATE_CHANGED`; no SDP, no class-of-device filter. | Same, plus an `ACTION_UUID` path; no SDP during inquiry. |
| Pairing | Public `createBond()` (reflected); waits 20 s for the bond; never `setPin()`/`abortBroadcast()`. | `createBond()`, waits up to 90 s, cancellable. |
| Connection | Four strategies in order (reflected constructor, `createRfcommSocket(1)`, insecure SPP, secure SPP); loops with a 500 ms pause on failure (`BluetoothThread`). | The same four plus `createInsecureRfcommSocket(1)`, 3 rounds, 2 s settle delay. |
| Handshake | Writes select player `0x43` then `0x41`, flushes; writes `0x44` and flushes before every blocking read; a 5-minute ping of the same command. `CONNECTION_DELAY_MS = 2000` is declared but unused. | Same bytes and order; no 5-minute ping (untested whether it matters). |
| Report handling | 12-byte blocks, XOR check only (no marker/response check); stops after 1000 bad packets and writes an error log. | Strict marker/length/response/player/XOR; bad-report events throttled; no stop threshold. |
| Controller types | Pocket and Pro state classes with different bit layouts (`MOGAPocketState`, `MOGAProState`). | Pocket only. |
| Outputs | *Virtual keyboard* (IME; no analogs) and *Gamepad* (root: chowns `/dev/uinput` with RootTools and uses a native driver). `TouchOutput` is an empty stub. | Virtual gamepad without root (adb helper), IME as fallback. |
| Profiles | XML profiles per mode and device type; create/rename/delete/select; "Enable Analog Input" per stick, trigger and D-pad. | One global mapping and a stick layout setting. |
| Service | Foreground service with notification, start/stop actions, home-screen toggle widgets; auto-reconnect and saved device. | Foreground service and notification; no widgets or auto-reconnect yet ([roadmap](roadmap.md)). |
| Conflicts | Detects the official MOGA Pivot/Controller apps and force-stops them (root) or opens their settings. | Not yet. |
| Other | AdMob ads, `ErrorLogger`. | Neither. |

## What the Linux reference does (`moga-uinput.py`)

- Finds devices with `bluetooth.discover_devices`, matches names by prefix (`BD&A`/`BDA` for gen 1, `MOGA` without `HID` for gen 2), then resolves the **RFCOMM channel through SDP** (`find_service`) instead of hard-coding channel 1.
- Sends the player command, then *poll* and *listen*; expects response IDs `0x61` (poll) and `0x64` (listen) and checks marker, declared size, XOR and player.
- Maps buttons and axes to evdev: `Y→BTN_NORTH`, `B→BTN_EAST`, `A→BTN_SOUTH`, `X→BTN_WEST`, bumpers to `BTN_TL/TR`, and treats `value ≥ 128` as `value - 255` with **both Y axes inverted**. Gen 2 adds trigger axes and different poll/listen commands (`0x45`/`0x46`, responses `0x65`/`0x66`).
- Note its "pad" naming (`UP/DOWN/LEFT/RIGHT`, `L2P/R2P`, …) comes from a layout that is not proven for every generation; we follow the Java Pocket mapping.

## Differences we chose

- **Strictness:** the Python reference's marker/response/player checks, applied to fixed 12-byte Pocket reports. Real hardware passes.
- **Axis convention:** decode with the reference's signed rule; expose *down-positive* Y (evdev/screen convention).
- **Channel:** keep the legacy fixed channel 1 as a strategy; SDP-resolved channels remain a fallback (public strategies). A future desktop driver should use SDP.

## Mapping legacy classes to this project

| Legacy | Here |
|---|---|
| `BluetoothThread` | `services/connection.rs` (loop), `RfcommConnector`/`RfcommSockets`/`ControllerLink` (sockets) |
| `BluetoothState` | `DeviceDiscovery`, `DeviceBonding`, `MogaDevices` |
| `MOGAPocketState`, `MOGAState` | `protocol/parser.rs`, `schemas/state.rs` |
| `MOGAService` | `MogaConnectionService` + the Rust session |
| `IMEOutput` | `MogaInputMethodService` |
| `SystemOutput` (root) | `UinputBridge` + the adb helper |
| `ProfileManager`, `XProfile`, … | not implemented ([roadmap](roadmap.md)) |
| `PivotUtil`, `KillPivotTask` | not implemented |
| `MOGASettings` | `MogaPreferences` |
