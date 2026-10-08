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
- [x] `pnpm android:sign` (`scripts/sign-apk.sh`): zipalign + apksigner + verify for the release APK.
- [x] Neutral app identifier `dev.mogabridge.app` (was a personal one).
- [ ] Run release builds and signing in CI (keystore as a secret).

## Virtual gamepad and notification

- [x] Virtual gamepad through Android's `uinput` (adb-started loopback helper), with analog sticks; PPSSPP detects it and responds in menus and in game.
- [x] "Isolate the controller" switch in the Test tab (off by default; resets when the app leaves the screen or the tab is left).
- [x] Persistent foreground-service notification while connecting/connected, with a Disconnect action.
- [x] Verified with the real controller in PPSSPP after the stick fix (user report: works).
- [x] Connection notification verified on device.
- [ ] Verify stick direction, range and the right stick against hardware; add dead-zone/calibration settings.
- [ ] Find out whether the user's unit has a physical D-pad and where it reports.
- [x] Harden the helper: per-install secret token, loopback only (see agents.md).
- [x] Helper panel redesign: status tag, steps only when not running, info on demand, gamepad option disabled without helper, automatic status polling instead of a check button.
- [x] Stick layout setting: two analogs, or one stick converted to a D-pad (either side).
- [x] Helper polling limited to the visible Mapeo tab with back-off; no file writes per poll.
- [x] Pocket-only compatibility disclaimer (app + docs) and idle power-off messaging.
- [ ] Verify stick-to-D-pad conversion with the real controller.
- [ ] Replace the token check with a peer-UID-checked daemon (`app_process`) for Android ≤10.
- [ ] Start the uinput helper without a PC (wireless debugging pairing or Shizuku).
- [ ] Battery level: no known source (see agents.md); inspect the raw report across battery states.
- [ ] Support the MOGA Pro layout (D-pad, triggers, stick clicks).

## Legacy app review: features we do not have yet

Findings from reading `decompilado-moga-universal` (not implemented; candidates, roughly by value):

- [ ] **Remember the last controller and auto-reconnect.** The legacy `BluetoothThread` stores the device (`PREF_DEVICE_ADDR/NAME/TYPE`) and, when the link drops, reconnects on its own (state `RECONNECTING`, 500 ms pause) instead of asking the user. Ours returns to the Connection tab and needs a manual tap. This pairs with the controller's idle power-off: turning the pad back on could reconnect it automatically.
- [ ] **Named profiles.** `ProfileManager` / `XProfile` (XML): create, rename, delete and select profiles, separate per driver mode (keyboard/gamepad) and per device type (Pocket/Pro); `SwitchProfileActivity` switches quickly. Ours has one global mapping and one stick layout.
- [ ] **Per-input analog/digital choice.** Its profile editor has "Enable Analog Input" for left stick, right stick, triggers and D-pad, and per-button remapping ("Change"). Our stick-layout switch is a first step; a full per-input remap would generalise it.
- [ ] **Quick toggle widgets.** `ServiceToggleWidget` / `ServiceToggleWidgetLong` start and stop the service from the home screen (with on/off icons). We only have the notification's Disconnect action.
- [ ] **Manage devices screen.** `ManageDevicesFragment`: list paired controllers, choose the active one, and unpair (`removeBond` via reflection). We can only connect; there is no "forget this controller".
- [ ] **Conflict detection with the official MOGA apps.** `PivotUtil` / `KillPivotTask` detect the MOGA Pivot/Controller services (`com.bda.pivot`, `com.bda.controller.service`, …) holding the controller and guide the user to stop them (it used root `am force-stop`; without root it opens the app's settings page). Worth a warning in the Connection tab.
- [ ] **Keep-alive ping.** The legacy thread writes the listen command every 5 minutes (`PING_DELAY_MS = 300000`). It may or may not prevent the controller's idle power-off; worth testing, since we only send it before each read.
- [ ] **Bad-packet threshold and error log.** It stops after 1000 bad packets (`BAD_PACKET_THRESHOLD`) and `ErrorLogger` writes errors to a file. We drop bad packets silently apart from the UI message; a bounded in-app log and a "share log" action would help support.
- [ ] **MOGA Pro layout.** `MOGAProState`: D-pad bits, L/R trigger buttons (`0x10`/`0x20`), stick clicks (`0x40`/`0x80`) in byte 5, plus the same four axes. Only the Pocket layout is implemented (see the compatibility disclaimer).
- [ ] **Complete key catalog for keyboard mode.** `KeyboardLetters`, `KeyboardSpecialKeys`, `KeyboardSymbols` and `KeyboardGamepadButtons` expose far more keys than our letters/digits/arrows list, including gamepad key codes.
- [ ] **IME picker shortcut.** The legacy home screen has "Configure Virtual Keyboards" and "Select Virtual Keyboard" actions; we only open the keyboard settings (no `showInputMethodPicker()` shortcut).
- [ ] **Touch output.** `TouchOutput` exists in the legacy code but is an empty stub; screen-touch emulation was planned and never built.
- [ ] **Mode selection dialog / first-run guidance.** `SelectModeDialogFragment` explains each driver mode up front (keyboard = no root but no analogs; gamepad = root). Our Mapeo tab covers this, but there is no first-run walkthrough.
- Not worth copying: AdMob ads and `show_ads`, root-only paths (`SystemOutput` chowns `/dev/uinput` with RootTools; our adb helper replaces it without root).

## Investigations

- [ ] **Virtual gamepad occasionally stops being detected after leaving the app** (fixed by returning to the app for a moment). Not reproduced; root cause unconfirmed. Found and fixed: the bridge never read from its socket, so a dropped helper connection (which removes the device) went unnoticed until the next write, and it only reconnected on the next controller report. Now it reads for EOF, a 2 s watchdog rebuilds the device and replays the last state, it re-checks on resume, and every transition is logged under tag `MogaUinput`. Next occurrence: capture `adb logcat -d -s MogaUinput MogaRfcomm MogaConnection`. Another suspect on Xiaomi/HyperOS is background restriction of the app; the Mapeo tab now links to the battery settings.

## Architecture

- [x] Rust backend split into `constants`, `utils`, `schemas`, `protocol`, `drivers`, `services`, `commands`.
- [x] Frontend split into `lib` (api), `hooks`, `components`, `views`, `styles`; legacy-app palette and assets.
- [x] Split `MogaAndroidPlugin.kt` into preferences, device filter, discovery, bonding, socket strategies/connector and controller link; the plugin is now only commands and wiring.
- [x] Review pass: clippy/fmt clean, dead constants and the unused plugin wrapper removed, `ProtocolError` variants renamed, the IME now honours the default key mapping.
- [ ] Frontend tests (hooks and components).

## Android no-root input destination

- [x] Select the Virtual Keyboard / `InputMethodService` mapper as the primary no-root destination, with the supported boundary limited to keyboard-compatible focused editors.
- [x] Add `MogaInputMethodService`, its Android manifest declaration, IME metadata, and an explicit Android keyboard-settings entry point.
- [x] Add configurable key mappings for face buttons, system buttons, bumpers, and the two sticks' digital directions; persist them in Android `SharedPreferences`.
- [x] Dispatch one `sendDownUpKeyEvents` tap per newly pressed mapped key, suppressing repeated events while a control remains held.
- [ ] Verify IME active/focus requirements on real devices and determine whether the intended emulator/game targets accept IME keyboard events.
- [ ] Improve onboarding so users can enable and select the IME and understand that it is not universal gamepad injection.
- [ ] Define named mapping profiles and clarify behavior for duplicate key assignments and key-map changes while controls are held.
- [ ] Validate the secondary in-app hook by consuming `moga-state` from a bundled sample canvas/game surface.

## Protocol and device validation

- [x] Real Mode A reports are accepted by the strict parser (12 bytes, `0x7A`, response `0x64`, player 1, XOR); buttons and sticks confirmed in the diagnostics view. The "d-pad" bits are the digitised sticks.
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
- [x] Live diagnostics view (Test tab): buttons, both analog sticks and the raw report.
- [ ] Make the diagnostics accessible and throttle UI updates if event rate becomes a problem.
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
