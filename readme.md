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
- Use the in-app **Escanear** action to run Bluetooth Classic discovery. Android 12+ requires Nearby devices scan/connect permission; older Android versions require location permission for Classic discovery. The app filters results by a MOGA name or discovered SPP UUID and lists bonded and unbonded controllers separately.
- Selecting an unbonded controller requests a bond using Android's public `BluetoothDevice.createBond()` before RFCOMM is opened. Android may show a system confirmation or PIN dialog; accept/complete it to proceed. The app cannot silently accept or suppress the Android system pairing UI.
- The Bluetooth Serial Port Profile UUID used by the legacy app is `00001101-0000-1000-8000-00805F9B34FB`. The Java app normally opens channel 1; a desktop service-discovery adapter should use the discovered RFCOMM channel rather than hardcoding it.

## Android no-root strategy and limits

The transport is a normal Android Bluetooth RFCOMM socket; root access is not needed to read that socket. Do **not** use Linux `uinput` or Windows `ViGEmBus` for Android.

The primary output is a configurable `InputMethodService` key mapper. Users must enable and select “MOGA Key Mapper” in Android keyboard settings. It calls `sendDownUpKeyEvents` when mapped controls transition from released to pressed. This is only useful while the IME is active for a focused editor, and can work only with apps/emulators that accept those keyboard events. It is not raw gamepad/HID injection and does not promise control of arbitrary native games.

The secondary future-proof hook is the Tauri `moga-state` event, emitted from Rust for each valid controller report. A built-in game/emulator hosted in this app's webview can consume this event without routing controls through the IME.

The Android app declares Bluetooth scan/connect and legacy location permissions plus the IME service. Native code lives in the generated app module at `src-tauri/gen/android/app/src/main/java/com/rafa_linux/moga_tauri/moga/`; preserve these files when regenerating Android project sources.

## Protocol baseline

- Outgoing messages are five bytes: `5A 05 <command> <player> <XOR>`. The worker sends player-1 select `43` (`5A 05 43 01 1D`) and poll `41` (`5A 05 41 01 1F`) on connect, then listen `44` (`5A 05 44 01 1A`) before each read, following the Java loop.
- Incoming reports in the Linux prototype are 12 bytes, start with `7A`, declare size `0C`, contain response ID `61` (poll) or `64` (listen) and player at bytes 2 and 3, and end with XOR of preceding bytes.
- The decoder resynchronizes on `7A`, validates framing/response/player/checksum, and reads controls from report bytes 4–9. Button and axis offsets are detailed in [agents.md](./agents.md).
- The decompiled Java app validates the XOR checksum but does not validate marker or response ID in its read loop. This implementation follows the stricter Python prototype framing, limited to the documented 12-byte Pocket report. Confirm against physical Mode A hardware before treating it as a production transport.
- Axis values are retained as raw bytes. Java reads the values directly; the Linux prototype applies signed conversion and Y inversion. Normalization is intentionally not silently selected until device behavior is verified.

## Development prerequisites

- Rust stable toolchain and Cargo
- Node.js and `pnpm`
- Tauri v2 CLI (`pnpm install` installs the project-local CLI)
- For Android builds: Android Studio/SDK, an installed Android NDK, Java toolchain, and Tauri's Android target setup

## Run and test

From this directory:

```sh
pnpm install
pnpm tauri dev
```

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
