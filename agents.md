# Agent handbook

This document records protocol and architecture facts that should be kept consistent by future contributors and coding agents.

## Scope and current state

- Make project changes only in this `moga-tauri/` tree. The sibling `decompilado-moga-universal/` and `moga-uinput/` directories are read-only protocol references.
- The Kotlin side lives in `gen/android/app/src/main/java/dev/mogabridge/app/moga/` (preserve it if the Android project is regenerated). One class per responsibility: `MogaAndroidPlugin` (Tauri commands and wiring only), `MogaPreferences` (all settings), `MogaDevices` (name filter + Bluetooth helpers), `DeviceDiscovery` (inquiry), `DeviceBonding` (`createBond`), `RfcommSockets` + `RfcommConnector` (socket strategies and retries), `ControllerLink` (open link, reader thread, queue), `UinputBridge` + `HelperFiles` (virtual gamepad), `MogaInputMethodService` (keyboard output), `MogaConnectionService` (notification). Their command names must match `drivers/android.rs`.

## Architecture

Rust (`src-tauri/src/`), layered; a layer only imports from layers above it in this list:

| Layer | Path | Responsibility |
|---|---|---|
| Constants | `constants/` | Wire-protocol numbers and bit masks, event names, plugin ids, key-code limit. No logic. |
| Utils | `utils/` | Pure helpers: XOR checksum, signed-axis decoding. |
| Schemas | `schemas/` | Serialisable data shapes: `MogaState`/`Buttons`/`Stick`, `DeviceInfo`, `ConnectionState`/`ConnectionStatus`, `KeyMapping`, `OutputMode`/`OutputSettings`, `ProtocolError`. |
| Protocol | `protocol/` | Transport-independent: `build_command`, `parse_report`, `PacketStreamParser` (+ test fixtures). |
| Drivers | `drivers/` | Platform boundary: traits `MogaDriver`, `MogaConnection`, `InputMapper`; `UnsupportedDriver` (desktop), `MockDriver` (tests), `android.rs` (`AndroidDriver` over the Tauri plugin handle). |
| Services | `services/` | `AppState` (driver + status) and the connection session (handshake, read loop, event emission). |
| Commands | `commands/` | Thin Tauri handlers (`devices`, `connection`, `settings`) registered in `lib.rs`. |

Add a command by writing the handler in `commands/`, registering it in `lib.rs`, and adding a wrapper in the frontend's `src/lib/api.js`.

Frontend (`src/`): `lib/` (`api.js` is the only place that calls `invoke`/`listen`; `events.js`, `keys.js`, `platform.js`) → `hooks/` (`useController`, `useDeviceDiscovery`, `useKeyMapping`, `useOutputSettings`) → `components/` (presentational, reusable) → `views/` (`ConnectionView`, `TestView`, `MappingView`) → `App.jsx` (composition root, owns the shared error) ; styles in `styles/` (design tokens in `tokens.css`). Visual identity is a homage to the legacy app: Holo blue `#33B5E5`, "on" green `#66CC00`, controller glyphs copied to `src/assets/legacy/`.

## Bluetooth protocol

- Bluetooth Classic SPP UUID: `00001101-0000-1000-8000-00805F9B34FB`.
- The legacy Java implementation opens RFCOMM channel 1. Prefer service discovery on platforms that support it.
- Outbound messages are five bytes: `[0x5A, 0x05, command, player, XOR(first four bytes)]`.
- Commands: select player `0x43`, poll `0x41`, listen `0x44`. For player 1 the exact messages are `5A 05 43 01 1D`, `5A 05 41 01 1F`, and `5A 05 44 01 1A`.
- Polling/listening protocol response IDs in the Python reference are `0x61` and `0x64`; report player is 1 in this baseline.
- The strict baseline treats each incoming Pocket report as 12 bytes: `7A`, length `0C`, response ID, player, two button bytes, four axis bytes, reserved byte, XOR byte. XOR covers every byte except the final checksum byte.
- `PacketStreamParser` accepts fragmented reads, discards bytes before the next `7A` marker, and validates each complete report. It currently supports exactly the 12-byte report documented by the prototype; don't infer variable-length support from its framing code.

### Pocket report matrix

Offsets below are absolute report offsets. The Linux prototype names the control payload starting at offset zero of the post-header portion, equivalent to the Java absolute offsets shown here.

| Report byte | Bit/value | MOGA Pocket control |
|---|---:|---|
| 4 | `01` | Y |
| 4 | `02` | B |
| 4 | `04` | A |
| 4 | `08` | X |
| 4 | `10` | Start |
| 4 | `20` | Select |
| 4 | `40` | Left bumper |
| 4 | `80` | Right bumper |
| 5 | `01 / 02 / 04 / 08` | Left stick, digitised: Up / Down / Left / Right |
| 5 | `10 / 20 / 40 / 80` | Right stick, digitised: Up / Down / Left / Right |
| 6 / 7 | signed byte | Left stick analog X / Y |
| 8 / 9 | signed byte | Right stick analog X / Y |

**The "pads" of byte 5 are the two analog sticks** (the legacy app calls them `LeftUp…`/`RightUp…`; its own text says joysticks cannot be used as analogs through the keyboard mode, "only DPads"). They are not a physical D-pad. Confirmed on hardware: pushing the left stick down sets bit `02` and byte 7 = `128`. The Rust state exposes `leftStick`/`rightStick` with `x`/`y` in `-127..=127` (x right-positive, y down-positive) plus the four digital flags. The MOGA *Pro* uses the same offsets differently (D-pad + triggers + stick clicks); only the Pocket layout is implemented. Where a physical D-pad of the user's unit reports, if it has one, is unverified.

Java reads the four axes directly as byte values. The Linux Python prototype normalizes values with its signed conversion (`>= 128` becomes `value - 255`) and inverts both Y axes. Keep the raw bytes in core state until hardware traces verify the desired shared normalization; do not conflate the Java and Python semantics.

### Reference differences to preserve

- Java checks checksum and 12-byte blocks, but does not check the `0x7A` marker or response ID in the described input path.
- Python validates `0x7A`, declared size, XOR, player, and base response IDs, but its receive payload slicing and extra-length handling deserve separate verification before being copied.
- This Rust baseline uses the strict Python marker/response checks for fixed 12-byte reports; axes are decoded as described above and the raw 12 bytes travel with every state (`raw`). Treat packet-length extensions and further response IDs as explicit protocol changes with tests.
- Python's generalized base button mapping is not proven to describe every MOGA generation; this implementation specifically follows the Java Pocket state mapping.

## Rust state/command flow

1. `scan_moga` asks the configured `MogaDriver` to enumerate devices.
2. `connect_moga` rejects an empty ID, marks connection state as connecting, then creates a named worker thread.
3. `MogaDriver::connect` returns a `MogaConnection` stream. The worker sends select-player and poll commands, then sends a listen command before each read, matching the Java loop.
4. The worker feeds read chunks to `PacketStreamParser`. Valid states emit `moga-state`; malformed reports are explicitly logged and dropped. Connection lifecycle emits `moga-status`.
5. The Android Kotlin reader thread queues socket bytes. Each Rust `Read` call invokes the plugin's asynchronous queue read, which waits on a dedicated executor (never the UI thread) and resolves with a data chunk or EOF/error marker. Rust does not busy-poll; it sends another `0x44` after the current read returns.
6. On each valid state, the worker calls the connection's Android IME dispatch hook and emits `moga-state`. The same event is the stable in-app/webview hook for a future bundled game or emulator.

Keep transport, parser, and input-output mapping separate. Future Windows and Linux implementations should implement the driver/input-sink boundaries without adding OS-specific code to `protocol.rs`. The Linux reference uses `uinput`; Windows may need a supported virtual-device backend. Neither path belongs in Android.

## Android discovery and pairing

- The decompiled legacy receiver listens for discovery start/finish, `ACTION_FOUND`, `ACTION_NAME_CHANGED`, and bond-state changes. `getPaired()` filters exact names: Pocket is **`BD&A`** and Pro is **`Moga Pro`**. Our plugin is deliberately more lenient (see `isMogaName` in `MogaAndroidPlugin.kt`): case-insensitive prefix `BD&A`/`BDA`/`MOGA`, excluding names containing `HID` (Mode B), mirroring `moga-uinput`. The discovery callback itself forwards found devices and does not filter on Bluetooth Class of Device; `MOGADevice` classifies generation by exact name.
- `scan_moga` returns the supported named bonded devices; `scan_unpaired_devices` starts `BluetoothAdapter.startDiscovery()`. The plugin registers a temporary receiver for `ACTION_FOUND`, `ACTION_NAME_CHANGED`, `ACTION_UUID`, and `ACTION_DISCOVERY_FINISHED`, matching the name from the intent's `EXTRA_NAME` (`device.name` is often still null at `ACTION_FOUND`) or the SPP UUID, and sends `moga-discovered-devices` plugin events. The frontend subscribes using Tauri's `addPluginListener("moga-android", "moga-discovered-devices", ...)`.
- Do **not** call `fetchUuidsWithSdp()` during inquiry: SDP traffic slows discovery and the legacy app matched by name only.
- `stop_device_scan`, discovery completion, and plugin/activity destruction cancel discovery and unregister the dynamic receiver. After inquiry finishes, the receiver remains registered for a bounded five-second SPP UUID lookup grace period. Any new path that registers a receiver must unregister it on completion, cancellation, and errors.
- Android 12+ requests `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN`; Android 6–11 requests `ACCESS_FINE_LOCATION` for Classic discovery. `neverForLocation` is declared for the modern scan permission. Keep the requested permission surface limited to these Bluetooth operations.
- The legacy `AddDeviceFragment` explicitly calls `BluetoothState.pair()`, which reflectively invokes public `BluetoothDevice.createBond()`, shows a pairing-progress dialog, and waits up to 20 seconds for a `BOND_BONDED` broadcast. It does **not** skip OS bonding. Its sources contain no `ACTION_PAIRING_REQUEST`, `setPin()`, or `abortBroadcast()` handling.
- The legacy `BluetoothThread.connect()` attempts these strategies in order: (1) reflected hidden `BluetoothSocket` constructor `(TYPE_RFCOMM, -1, false, true, device, -1, ParcelUuid(SPP_UUID))`; (2) reflected `BluetoothDevice.createRfcommSocket(1)`; (3) public `createInsecureRfcommSocketToServiceRecord(SPP_UUID)`; (4) public secure `createRfcommSocketToServiceRecord(SPP_UUID)`. All failures fall through to the next attempt. Our plugin adds a fifth, `createInsecureRfcommSocket(1)` (`reflectedInsecureChannelOne`), after the secure channel-1 attempt.
- `RFCOMM_STRATEGIES` in `drivers/mod.rs` is serialized to the Kotlin plugin and is the single ordered strategy list. Hidden reflection may be blocked by Android hidden-API enforcement; catch that failure and keep trying the remaining documented methods. Close each failed candidate socket before proceeding.
- `connect` requires a completed bond first, matching the legacy app's add-device flow. Public insecure RFCOMM is unauthenticated at the socket layer; it does not undo an existing bond or guarantee bypassing the Android bond UI. Never describe it as an OS-pairing bypass.
- After `BluetoothSocket.connect()` succeeds and streams are obtained, the legacy app immediately signals connected and writes player-select (`0x43`) followed by reset/poll (`0x41`), then flushes. It sends listen (`0x44`) and flushes before every blocking read. There is no challenge-response in the active code, and `CONNECTION_DELAY_MS = 2000` is declared but unused there. Our plugin intentionally deviates: it waits 2 s after bonding and runs up to 3 rounds of the whole strategy list (500 ms apart), because the Pocket often refuses the first connect right after bonding/inquiry. The only active sleep is 500 ms after a read loop exits before reconnecting.
- **Never call `setPin()` or `abortBroadcast()` to hide/bypass system pairing UI.** Normal third-party apps lack the needed privileged permission. Android owns confirmation/PIN entry, and bonding receivers are always unregistered in `finally`.
- `request_bluetooth_permission` invokes Tauri's Android permission request. No `uinput`, `ViGEmBus`, root shell, or privileged HID injection.
- `AndroidDriver` uses `PluginHandle` methods for scan/connect/read/write/mapping/dispatch. Tauri's plugin handle performs the JNI bridge to Kotlin; the Rust packet parser remains platform-independent.
- The Kotlin reader owns blocking `BluetoothSocket` reads on a background thread and places chunks in a bounded queue. Plugin `read` waits for data on a dedicated executor, not the WebView/main thread. Rust's worker performs the protocol state machine and handshake.
- `MogaInputMethodService` dispatches `sendDownUpKeyEvents` only on newly pressed controls, avoiding repeats on each unchanged poll. The mapping is stored in Android `SharedPreferences` and edited through the UI's select list.
- The user must explicitly enable and select this IME. Its events target only the focused editor/input connection while Android has the IME active; this is not raw gamepad injection and cannot promise control of arbitrary games. The service displays a brief active-keyboard view and does not create a general gamepad device.
- `moga-state` is the second, in-app hook for a future game/emulator hosted in the app's own webview. It is an application event, not an external-application injection API.
- Before release, validate permission denial/revocation, Bluetooth-off state, reconnect and teardown races, IME selection/focus requirements, mapping collisions, and actual Mode A hardware on supported Android API levels.

## Output modes

- **Gamepad (default)**: `UinputBridge.kt` streams JSON (`register`, `inject`) to a loopback socket served by `toybox nc -s 127.0.0.1 -p 7777 -L uinput -`, started from the PC by `scripts/uinput-helper.sh` (`pnpm android:uinput`) as the adb `shell` user (which may open `/dev/uinput`). Android then sees a `GAMEPAD|JOYSTICK` device with `ABS_X/Y/RX/RY` (-127..127, flat 8) and `BTN_SOUTH/EAST/NORTH/WEST` (Android A/B/X/Y), `BTN_TL/TR`, `BTN_SELECT/START`. Verified end to end: PPSSPP detects it and advances its menus and a game's title screen. A device only exists while a controller is connected. The `uinput` tool wants a *stream of JSON objects*, not a JSON array.
- The Test tab has an **isolation** switch (`isolated`, off by default, in memory only): while on, the bridge is suspended so the controller can be tried inside the app without navigating its UI; held inputs are released. The backend turns it off when the activity pauses (another app, notification shade) and the frontend turns it off when the Test tab is left.
- **UI rules for the helper** (Mapeo tab): `HelperPanel` is its own panel titled "Ayudante uinput" with a status tag. Not running: what it is, numbered steps and a copyable command, with a note that the screen updates by itself. Running: a one-line confirmation and a "Ver información" toggle (the same text on demand). The tab re-reads the helper state by polling (`usePolling`, 3 s while waiting, 10 s once running), so there is no manual "check" button. The **Gamepad virtual** option is disabled with a reason until the helper is reachable, and the effective mode shown is keyboard in that case (the saved preference is kept and applies again when the helper returns).
- **Stick layout** (virtual gamepad only; `StickLayout`, persisted in the `moga-output` prefs): `analogs` (default), `leftDpad` (left stick becomes the D-pad, right stays analog) or `rightDpad`. A converted stick stops driving its analog axes (held at 0) and drives `ABS_HAT0X/Y` from the controller's own digitised direction bits. The hat axes are always registered. The setting applies on the next report.
- **Polling policy** (Mapeo tab, `usePolling`): the helper state is re-read only while the Mapeo tab is mounted **and** `document.visibilityState` is `visible`; it stops on tab change, unmount, another app or screen off, and polls once immediately on return. While the helper is not running: every 3 s for 20 attempts, then every 10 s; once running: every 15 s. Each poll costs one short loopback connection on a Kotlin thread (skipped when the bridge already holds a socket) and no file writes (`HelperFiles.prepare` writes only on change).
- **Compatibility disclaimer**: the mapping is for the MOGA Pocket (Mode A) only; shown in the Mapeo tab and in the docs. MOGA Pro uses the same offsets differently and is unsupported.
- **Idle power-off**: the controller turns itself off after a period without input. `services/connection.rs` appends an explanatory hint (`IDLE_POWER_OFF_HINT`) to unexpected disconnects/errors (not to user-requested disconnects), the Connection tab states it permanently, and the app returns to that tab when a connected session ends unexpectedly.
- **Keyboard (IME)**: `MogaInputMethodService` only reaches focused text editors, so it cannot drive games or emulators; kept as a fallback.
- **Helper hardening**: the helper is no longer a bare `nc`+`uinput`. The app writes `helper.token` (24 random bytes, hex) and a generated `helper.sh` (from `res/raw/helper_script.sh`) into its external private directory (`Android/data/<pkg>/files`, via `HelperFiles.kt`). The helper runs `toybox nc -s 127.0.0.1 -p 7777 -L sh -c 'read -r -t 5 t …; exec uinput -'`: the first line of every connection must equal the token (re-read on each connection) or the connection is dropped before any device is created. Verified: no token / wrong token ⇒ 0 devices, right token ⇒ device registered. Limits: on Android ≤10 other apps with storage permission can read that directory; a rogue connection can hold a shell process for up to 5 s. A stronger design is a small `app_process` daemon on an abstract socket that checks the peer UID.
- The helper is not a persistent service: it lives until the phone reboots. Starting it without a PC (wireless debugging or Shizuku) is future work.
- **Battery**: no source found. The legacy app has none, the 12-byte report has no documented field (the raw report is shown in the Test view to look for one), and Android lists the BD&A with the SPP profile only (`BATTERY=-1`), so `BluetoothDevice` battery APIs return nothing.

## Hardware validation status

- Verified on a Xiaomi 24117RN76L (Android 16, API 36) with a real MOGA Pocket in Mode A: discovery by name `BD&A`, Android-confirmed bonding, RFCOMM connection, and live reports. The diagnostics view shows `responseId` 100 (`0x64`), player 1, and correct button/d-pad changes, so the strict 12-byte parser accepts real traffic.
- The plugin logs the winning socket strategy under logcat tag `MogaRfcomm` (`RFCOMM connected with strategy=...`), and every failed one as a warning. Observed result on that device: strategy 1 (reflected constructor) is unavailable because Android 16 blocks the 7-arg `BluetoothSocket` constructor, and strategy 2 (`createRfcommSocket(1)`, i.e. the legacy hard-coded channel 1) connects on round 1. Keep the other fallbacks for older/other devices.
- Axis capture: left stick pushed down => bytes 6..9 = `[0, 128, 0, 0]`; down+left => `[128, 192, 0, 0]`. Decoded with `value >= 128 ? value - 255 : value`, y inverted (down positive). Sign/range of the right stick and full-range values are not yet verified on hardware.

## Dev-environment pitfalls

See [develop.md](./develop.md) for commands. Facts that cost time before:

- The plugin is registered at runtime, so Tauri's ACL does not know it. `build.rs` must declare it with `InlinedPlugin` (commands `registerListener` and `remove_listener`: the JS API calls them with those exact spellings) and `capabilities/default.json` must grant `moga-android:default`; otherwise `addPluginListener` fails with `moga-android.registerListener not allowed. Plugin not found`. If you add Kotlin commands invoked **from JS**, add them there too (commands invoked from Rust via `run_mobile_plugin` do not need it).
- `ERR_CONNECTION_REFUSED` on `http://tauri.localhost/` in dev means the phone cannot reach Vite, not that the app is a release build. The LAN IP is usually firewalled; use `pnpm android:dev` (`adb reverse` + `--host 127.0.0.1`).
- `build.rs`/capability/Kotlin changes are not always picked up by the running dev watcher; restart `pnpm android:dev`.
- Never use `pkill -f "tauri android dev"` from an agent shell: it matches its own command line. Kill by PID.

## Tests and change checklist

- Run `cd src-tauri && cargo test` after parser/driver edits.
- Add tests for every newly supported report size, response ID, player configuration, checksum rule, and button/axis mapping.
- Preserve the test-only mock transport so fragmented/corrupt streams can be verified without a controller.
- Keep frontend command names and event payloads in sync with Rust serde names.
- Update [readme.md](./readme.md) and [todo.md](./todo.md) when implementation status or platform behavior changes.
