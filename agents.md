# Agent handbook

This document records protocol and architecture facts that should be kept consistent by future contributors and coding agents.

## Scope and current state

- Make project changes only in this `moga-tauri/` tree. The sibling `decompilado-moga-universal/` and `moga-uinput/` directories are read-only protocol references.
- `src-tauri/src/protocol.rs` is transport-independent and owns framing, checksum validation, and byte-to-state conversion.
- `src-tauri/src/driver.rs` defines `MogaDriver`, `MogaConnection`, `InputMapper`, and the serializable `KeyMapping`. `PlatformDriver` is the explicit unavailable adapter on desktop; the test-only `MockDriver` exercises byte parsing.
- On Android, `src-tauri/src/android.rs` registers Tauri's Android plugin through `PluginApi::register_android_plugin` and replaces the app's driver with `AndroidDriver`. Tauri owns the JNI calls to the app's Kotlin plugin; do not build a second JNI mechanism around the same socket.
- `src-tauri/src/lib.rs` exposes paired-device scan, active unpaired discovery start/stop, connect, disconnect, status, Bluetooth permission, mapping read/write, and IME settings commands. The connection worker emits `moga-status`, `moga-state`, and `moga-error`.
- Android's paired-device enumeration and RFCOMM socket are implemented in `gen/android/app/src/main/java/com/rafa_linux/moga_tauri/moga/MogaAndroidPlugin.kt`; the IME is `MogaInputMethodService.kt`.

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
| 5 | `01 / 02 / 04 / 08` | Left-pad Up / Down / Left / Right |
| 5 | `10 / 20 / 40 / 80` | Right-pad Up / Down / Left / Right |
| 6 / 7 | raw bytes | Left X / Left Y |
| 8 / 9 | raw bytes | Right X / Right Y |

Java reads the four axes directly as byte values. The Linux Python prototype normalizes values with its signed conversion (`>= 128` becomes `value - 255`) and inverts both Y axes. Keep the raw bytes in core state until hardware traces verify the desired shared normalization; do not conflate the Java and Python semantics.

### Reference differences to preserve

- Java checks checksum and 12-byte blocks, but does not check the `0x7A` marker or response ID in the described input path.
- Python validates `0x7A`, declared size, XOR, player, and base response IDs, but its receive payload slicing and extra-length handling deserve separate verification before being copied.
- This Rust baseline uses the strict Python marker/response checks for fixed 12-byte reports and keeps raw axes. Treat packet-length extensions and further response IDs as explicit protocol changes with tests.
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

- `scan_moga` returns MOGA-named bonded devices; `scan_unpaired_devices` starts `BluetoothAdapter.startDiscovery()`. The plugin registers a temporary receiver for `ACTION_FOUND`, `ACTION_UUID`, and `ACTION_DISCOVERY_FINISHED`, filters by MOGA device name or the SPP UUID, and sends `moga-discovered-devices` plugin events. The frontend subscribes using Tauri's `addPluginListener("moga-android", "moga-discovered-devices", ...)`.
- `stop_device_scan`, discovery completion, and plugin/activity destruction cancel discovery and unregister the dynamic receiver. After inquiry finishes, the receiver remains registered for a bounded five-second SPP UUID lookup grace period. Any new path that registers a receiver must unregister it on completion, cancellation, and errors.
- Android 12+ requests `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN`; Android 6–11 requests `ACCESS_FINE_LOCATION` for Classic discovery. `neverForLocation` is declared for the modern scan permission. Keep the requested permission surface limited to these Bluetooth operations.
- `connect` checks `BluetoothDevice.bondState`. If the device is not bonded it registers a temporary `ACTION_BOND_STATE_CHANGED` receiver, calls public `createBond()`, waits with a bounded timeout, and only then opens the SPP RFCOMM socket. A non-bonded device shows the frontend's “Sincronizando control programáticamente…” status while Android owns pairing consent.
- **Never call `setPin()` or `abortBroadcast()` to hide/bypass the system pairing dialog.** Normal third-party apps do not hold `BLUETOOTH_PRIVILEGED`, and silent pairing cannot be made reliable or user-consented through the public Android API. Let Android display any required confirmation/PIN entry; require the bond to reach `BOND_BONDED` before socket creation. Pairing receivers must always be unregistered in `finally`.
- Kotlin's Tauri `MogaAndroidPlugin` uses `createRfcommSocketToServiceRecord` with the SPP UUID. `request_bluetooth_permission` invokes Tauri's Android permission request. No `uinput`, `ViGEmBus`, root shell, or privileged HID injection.
- `AndroidDriver` uses `PluginHandle` methods for scan/connect/read/write/mapping/dispatch. Tauri's plugin handle performs the JNI bridge to Kotlin; the Rust packet parser remains platform-independent.
- The Kotlin reader owns blocking `BluetoothSocket` reads on a background thread and places chunks in a bounded queue. Plugin `read` waits for data on a dedicated executor, not the WebView/main thread. Rust's worker performs the protocol state machine and handshake.
- `MogaInputMethodService` dispatches `sendDownUpKeyEvents` only on newly pressed controls, avoiding repeats on each unchanged poll. The mapping is stored in Android `SharedPreferences` and edited through the UI's select list.
- The user must explicitly enable and select this IME. Its events target only the focused editor/input connection while Android has the IME active; this is not raw gamepad injection and cannot promise control of arbitrary games. The service displays a brief active-keyboard view and does not create a general gamepad device.
- `moga-state` is the second, in-app hook for a future game/emulator hosted in the app's own webview. It is an application event, not an external-application injection API.
- Before release, validate permission denial/revocation, Bluetooth-off state, reconnect and teardown races, IME selection/focus requirements, mapping collisions, and actual Mode A hardware on supported Android API levels.

## Tests and change checklist

- Run `cd src-tauri && cargo test` after parser/driver edits.
- Add tests for every newly supported report size, response ID, player configuration, checksum rule, and button/axis mapping.
- Preserve the test-only mock transport so fragmented/corrupt streams can be verified without a controller.
- Keep frontend command names and event payloads in sync with Rust serde names.
- Update [readme.md](./readme.md) and [todo.md](./todo.md) when implementation status or platform behavior changes.
