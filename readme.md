# MOGA Pocket Bridge

A cross-platform-ready Tauri v2 application for reading the legacy MOGA Pocket's proprietary Bluetooth serial reports. Android has active Classic discovery, user-confirmed bonding, RFCOMM transport, and a keyboard-mapping IME bridge; Rust owns protocol parsing and emits controller state to the Tauri frontend.

## Architecture

```text
React UI
  ├─ invoke(scan_moga / scan_unpaired_devices / connect_moga)
  ├─ listen(moga-status / moga-state)
  └─ addPluginListener(moga-android / moga-discovered-devices)
             │ Tauri commands and events
Rust MogaDriver abstraction
  ├─ AndroidDriver → Tauri mobile PluginHandle/JNI → Kotlin RFCOMM plugin
  ├─ PlatformDriver (desktop placeholder)
  └─ MockDriver (test-only RFCOMM byte stream)
             │ Read stream
PacketStreamParser → validated 12-byte report → MogaState
             ├─ Tauri moga-state event (future in-app game/emulator hook)
             └─ Android plugin → active InputMethodService → keyboard strokes
```

The protocol decoder is platform-independent. Android's native plugin performs blocking Bluetooth reads on a Kotlin worker and exposes buffered reads/writes over the Tauri JNI plugin handle; Rust's MOGA worker performs the handshake and parsing. Desktop adapters can be added behind `MogaDriver`; the current `PlatformDriver` fails explicitly rather than pretending that desktop scanning or connecting succeeded.

## Hardware and pairing

- A MOGA Pocket controller configured for **Mode A** is the target hardware. The app targets the legacy proprietary SPP/RFCOMM protocol, not the controller's alternate standard-HID mode.
- An Android device with Bluetooth Classic/RFCOMM support is required for the intended mobile path.
- Use the in-app **Escanear** action to run Bluetooth Classic discovery. Android 12+ requires Nearby devices scan/connect permission; older Android versions require location permission for Classic discovery. Pocket and Pro are identified from the inquiry name (`BD&A`, `Moga Pro`; matched case-insensitively by prefix, excluding `HID` names). The UI lists bonded and unbonded controllers separately.
- Selecting an unbonded controller requests Android's public `BluetoothDevice.createBond()` before RFCOMM is opened, matching the legacy app's explicit Add Device pairing flow. Android may show a system confirmation or PIN dialog; accept/complete it to proceed. The legacy app did not silently suppress that flow.
- After bonding, the socket connection waits 2 s and tries the legacy order: reflected hidden BluetoothSocket constructor, reflected RFCOMM channel 1, reflected insecure channel 1, public insecure SPP socket, and finally public secure SPP socket, for up to 3 rounds. The winning strategy is logged under logcat tag `MogaRfcomm`; on Android 16 the reflected constructor is blocked and reflected channel 1 connects. Reflection may be blocked on modern Android, so failures fall through. “Insecure” refers to the RFCOMM socket's authentication/encryption options; it is not a general Android bond-dialog bypass.
- The Bluetooth Serial Port Profile UUID used by the legacy app is `00001101-0000-1000-8000-00805F9B34FB`. The legacy fallback list tries a reflected fixed channel 1 as its second option; desktop service-discovery adapters should use the discovered RFCOMM channel instead of hardcoding it.

## Android no-root strategy and limits

End-user setup steps are in [usuario.md](./usuario.md).

**Supported hardware:** the button mapping (report layout, sticks, buttons) is designed for the **MOGA Pocket in Mode A** only. Other models such as the MOGA Pro use different layouts (D-pad, triggers, stick clicks) and are not supported yet; the app says so in the Mapeo tab.

**Idle power-off:** the controller powers itself off after a while without input. The app treats this as expected: it shows an explanatory message and returns to the Connection tab.

**Default output: virtual gamepad.** The app streams the controller to Android's own `uinput` tool through an adb-started loopback helper (`pnpm android:uinput`, no root), so games and emulators see a real gamepad with analog sticks. The keyboard IME below is only a fallback for text fields.


The transport is a normal Android Bluetooth RFCOMM socket; root access is not needed to read that socket. Do **not** use Linux `uinput` or Windows `ViGEmBus` for Android.

The primary output is a configurable `InputMethodService` key mapper. Users must enable and select “MOGA Key Mapper” in Android keyboard settings. It calls `sendDownUpKeyEvents` when mapped controls transition from released to pressed. This is only useful while the IME is active for a focused editor, and can work only with apps/emulators that accept those keyboard events. It is not raw gamepad/HID injection and does not promise control of arbitrary native games.

The secondary future-proof hook is the Tauri `moga-state` event, emitted from Rust for each valid controller report. A built-in game/emulator hosted in this app's webview can consume this event without routing controls through the IME.

The Android app declares Bluetooth scan/connect and legacy location permissions plus the IME service. Native code lives in the generated app module at `src-tauri/gen/android/app/src/main/java/dev/mogabridge/app/moga/`; preserve these files when regenerating Android project sources.

## Protocol baseline

- Outgoing messages are five bytes: `5A 05 <command> <player> <XOR>`. The worker sends player-1 select `43` (`5A 05 43 01 1D`) and poll `41` (`5A 05 41 01 1F`) on connect, then listen `44` (`5A 05 44 01 1A`) before each read, following the Java loop.
- The decompiled implementation sends select and poll immediately after socket/stream setup, then flushes. No dynamic challenge-response occurs in the legacy active code and its 2-second delay constant is unused (our Android plugin still waits 2 s after bonding; see [agents.md](./agents.md)). A 500 ms sleep is only used after a read loop ends and before reconnect.
- Incoming reports in the Linux prototype are 12 bytes, start with `7A`, declare size `0C`, contain response ID `61` (poll) or `64` (listen) and player at bytes 2 and 3, and end with XOR of preceding bytes.
- The decoder resynchronizes on `7A`, validates framing/response/player/checksum, and reads controls from report bytes 4–9. Button and axis offsets are detailed in [agents.md](./agents.md).
- The decompiled Java app validates the XOR checksum but does not validate marker or response ID in its read loop. This implementation follows the stricter Python prototype framing, limited to the documented 12-byte Pocket report. Confirm against physical Mode A hardware before treating it as a production transport.
- Axis values are retained as raw bytes. Java reads the values directly; the Linux prototype applies signed conversion and Y inversion. Normalization is intentionally not silently selected until device behavior is verified.

## Status

Validated on real hardware (Android 16 + MOGA Pocket in Mode A): discovery, bonding, RFCOMM connection, live reports, and the in-app diagnostics view. IME key mapping, axis normalization, reconnect behavior and other Android versions are still unverified; see [todo.md](./todo.md).

## Development prerequisites

- Rust stable toolchain and Cargo
- Node.js and `pnpm`
- Tauri v2 CLI (`pnpm install` installs the project-local CLI)
- For Android builds: Android Studio/SDK, an installed Android NDK, Java toolchain, and Tauri's Android target setup

## Run and test

From this directory:

```sh
pnpm install
pnpm android:dev   # Android device over USB, hot reload (see develop.md)
pnpm tauri dev     # desktop shell (no Bluetooth driver)
```

Full instructions for the Android dev loop, APK builds, logs and troubleshooting are in [develop.md](./develop.md).

Run the Rust protocol and mock-driver tests:

```sh
cd src-tauri
cargo test
```

The Android UI can enumerate paired devices and run active Classic discovery for unpaired MOGA devices. Rust tests exercise framing, parsing, handshake bytes, and the mock stream without Bluetooth hardware. Validate the Android APK, runtime permissions, user-confirmed bond flow, IME opt-in/selection, and real controller traces on hardware before release.

## Reference sources

- Decompiled Android protocol/service implementation: `../decompilado-moga-universal/sources/net/obsidianx/android/mogaime/service/BluetoothThread.java`, `MOGAPocketState.java`, and `MOGAState.java`.
- Linux/Python protocol and input prototype: `../moga-uinput/moga-uinput.py`. Its README and `src/main.c` describe an unfinished Linux driver/connection monitor, not a completed Android path.
- The reference folders are analysis inputs only; implementation and documentation changes for this project belong inside `moga-tauri/`.
