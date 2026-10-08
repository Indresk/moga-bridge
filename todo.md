# Project backlog

This checklist reflects the current implementation: a Rust protocol parser/driver boundary, Tauri Android plugin bridge to Bluetooth RFCOMM and an IME key mapper, a test mock, and a React mapping/device UI. Remaining hardware validation and future work are unchecked.

## Android transport and lifecycle

- [x] Implement Android paired-device enumeration and RFCOMM socket connection using the SPP UUID through a Tauri Android plugin and a Rust `MogaDriver` adapter.
- [x] Add Android Classic discovery with `startDiscovery`, dynamic found/UUID/finished broadcasts, SPP/name filtering, and a live Tauri plugin event.
- [x] Add API-appropriate Bluetooth permission requests: Android 12+ scan/connect and Android 6–11 location for Classic discovery.
- [x] Match the legacy explicit `createBond()` pairing flow before RFCOMM; Android remains responsible for confirmation/PIN UI and the bond receiver is cleaned up.
- [x] Try the legacy RFCOMM fallbacks in order: reflected BluetoothSocket constructor, reflected channel 1, public insecure SPP, then public secure SPP.
- [x] Add Android Bluetooth permissions and show permission and transport errors in the UI.
- [x] Unregister temporary discovery receivers on stop, completion, and plugin teardown.
- [x] Preserve Android's system confirmation/PIN UI; legacy source uses `createBond()` and does not use `setPin()` / `abortBroadcast()`.
- [x] Validate user-confirmed pairing and RFCOMM connection on a real Pocket (Xiaomi, Android 16).
- [x] Socket strategy on Android 16 (Xiaomi 24117RN76L): `reflectedSocketConstructor` is blocked by hidden-API enforcement (NoSuchMethod on the 7-arg constructor); `reflectedChannelOne` (`createRfcommSocket(1)`) connects on round 1.
- [ ] Check other Android versions/devices, then prune strategies that never work (keep the fallbacks until then) and record controller-specific PIN behavior.
- [x] Lenient Mode A name matching (`BD&A`/`BDA`/`MOGA`, no `HID`) from `EXTRA_NAME`; no SDP fetch during inquiry; 2 s settle delay and 3 connect rounds after bonding.
- [ ] Verify service discovery versus RFCOMM channel 1 against physical Pocket hardware and test reconnect after real disconnects.
- [x] Send player-select and poll/listen handshake commands in the documented order.
- [x] Close the socket when the Rust connection drops and the Android activity/plugin is destroyed.
- [x] Select Android's concrete driver during Tauri Android plugin setup while leaving the explicit desktop adapter and test mock available.
- [x] Add explicit user-facing disconnect/cancel control and close an in-flight or active socket on disconnect.
- [ ] Define app background/foreground policy for maintaining or suspending the controller connection.
- [ ] Add bounded timeouts, retry/backoff rules, and distinct pairing, permission, transport, and protocol errors.

## Dev tooling

- [x] `pnpm android:dev` (`scripts/android-dev.sh`): `adb reverse` for Vite ports plus `--host 127.0.0.1`, working around a firewalled LAN IP.
- [x] Declare the runtime Android plugin in `build.rs` (`InlinedPlugin`) and grant `moga-android:default` so `addPluginListener` works.
- [x] Add [develop.md](./develop.md) with dev-loop, APK build, logging and troubleshooting instructions.
- [ ] Add a script/CI job for release APK builds and signing.

## Android no-root input destination

- [x] Select the Virtual Keyboard / `InputMethodService` mapper as the primary no-root destination, with the supported boundary limited to keyboard-compatible focused editors.
- [x] Add `MogaInputMethodService`, its Android manifest declaration, IME metadata, and an explicit Android keyboard-settings entry point.
- [x] Add configurable key mappings for face buttons, system buttons, bumpers, and both d-pads; persist them in Android `SharedPreferences`.
- [x] Dispatch one `sendDownUpKeyEvents` tap per newly pressed mapped key, suppressing repeated events while a control remains held.
- [ ] Verify IME active/focus requirements on real devices and determine whether the intended emulator/game targets accept IME keyboard events.
- [ ] Improve onboarding so users can enable and select the IME and understand that it is not universal gamepad injection.
- [ ] Define named mapping profiles and clarify behavior for duplicate key assignments and key-map changes while controls are held.
- [ ] Validate the secondary in-app hook by consuming `moga-state` from a bundled sample canvas/game surface.

## Protocol and device validation

- [x] Real Mode A reports are accepted by the strict parser (12 bytes, `0x7A`, response `0x64`, player 1, XOR); buttons and d-pad confirmed in the diagnostics view.
- [ ] Capture and store longer Mode A report streams (including poll `0x61` responses) as golden fixtures.
- [ ] Verify checksum coverage, resynchronization after dropped/corrupt bytes, and whether responses may exceed 12 bytes.
- [ ] Confirm the Pocket bit matrix for every button, both directional pads, and simultaneous button combinations.
- [ ] Verify axis center/range/deadzone and direction. Resolve the Java raw-byte versus Python signed/Y-inverted conventions using recorded hardware traces.
- [ ] Add golden byte fixtures and parser tests for valid poll/listen responses, invalid marker/size/player/response/checksum, noise, truncation, and back-to-back reports.
- [ ] Confirm differences between MOGA Pocket and later MOGA generations before expanding accepted reports or mappings.

## Product and frontend

- [x] Replace the Android "adapter unavailable" scan response with paired-device and active unpaired-device discovery and show separate device lists.
- [x] Add Android permission request, discovery UI, mapping controls, and Bluetooth error display.
- [ ] Add loading/empty/error polish and clear disconnect/retry UX, especially around native connection teardown.
- [ ] Render button, d-pad, and stick state in an accessible live diagnostics view; avoid flooding the UI with unthrottled events.
- [ ] Add settings for mapping, player ID, reporting mode, and axis calibration only after protocol behavior is verified.
- [ ] Add localization, keyboard/screen-reader accessibility, and responsive-device testing.

## Validation and release

- [ ] Run `cargo fmt --check`, `cargo clippy --all-targets --all-features -- -D warnings`, and Rust tests in CI.
- [ ] Build and smoke-test the Android APK on a supported Android SDK/API-level matrix.
- [ ] Test Bluetooth permission denial, disabled Bluetooth, controller powered off, disconnect mid-frame, malformed input, and application background/foreground transitions.
- [ ] Add end-to-end tests using the mock driver and verify Tauri events reach the frontend.
- [ ] Document supported hardware, Android versions, known protocol limits, and bridge limitations in release notes.
- [ ] Review Bluetooth data handling, minimal permissions, and privacy behavior before release.

## Future desktop targets

- [ ] Implement a Linux Bluetooth Classic/RFCOMM adapter and determine whether BlueZ APIs or another maintained library fit the desktop permission/device lifecycle.
- [ ] If Linux virtual gamepad support is desired, make `uinput` a separately installed/configured desktop sink; never require it for Android.
- [ ] Implement a Windows transport adapter and evaluate a maintained virtual-controller backend; keep any ViGEm-related design desktop-only and document its installation/licensing/support constraints.
- [ ] Define a shared `InputSink` trait and a common normalized state model without losing raw protocol values needed for device-specific calibration.
- [ ] Add per-platform packaging, signing, permissions/install prerequisites, CI runners, and hardware acceptance tests.
