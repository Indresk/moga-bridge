# Roadmap

Open work only; finished work is summarised at the bottom. Effort: **S** (hours), **M** (a day or two), **L** (several days or needs hardware/design). **good first** = no controller needed and little context. Before starting an **M/L** item, open an issue to agree on the approach ([CONTRIBUTING.md](../../CONTRIBUTING.md)).

## Highest value

| Item | Effort | Notes |
|---|---|---|
| **Remember the last controller and auto-reconnect** | M | The legacy app stores the device (`PREF_DEVICE_ADDR/NAME/TYPE`) and reconnects on its own after a drop (state `RECONNECTING`, 500 ms pause). Ours returns to *Conexión* and needs a tap. It fits the idle power-off: turning the pad back on could reconnect it. |
| **Start the helper without a PC** | L | Today a PC with `adb` is needed after every reboot. Options: in-app wireless debugging (Android 11+, needs Wi-Fi, in-app ADB pairing/TLS), or Shizuku. Trade-offs in [decisions.md](decisions.md#helper-without-a-pc). Keep the PC path as a fallback. |
| **Harden the helper** | M | Replace the token check with an `app_process` daemon on an abstract socket that verifies the caller's UID (closes the Android ≤ 10 gap and the 5 s hold). |
| **Hardware validation on other devices** | L | More Android versions/vendors; prune socket strategies that never work; PIN behaviour. Findings welcome as issues even without code. |

## Connection and devices

- [ ] **Manage devices / forget a controller** (M) — list paired controllers and unpair (`removeBond` via reflection), as the legacy `ManageDevicesFragment`.
- [ ] **Detect conflicting official MOGA apps** (S) — warn when `com.bda.pivot`, `com.bda.controller.service`, … hold the controller (`PivotUtil`).
- [ ] **Keep-alive** (S, needs hardware) — the legacy thread writes *listen* every 5 minutes (`PING_DELAY_MS`); test whether it prevents the idle power-off.
- [ ] **Structured error kinds** (M) — distinguish pairing, permission, transport and protocol failures in the UI instead of free-text.
- [ ] **Test reconnect after real disconnects and Bluetooth-off/permission-revoked paths** (M).

## Virtual gamepad and output

- [ ] **Verify the right stick** (S, needs hardware) — sign, range, centre; add dead-zone/calibration settings.
- [ ] **Find out whether the unit has a physical D-pad** and where it reports (S, needs hardware).
- [ ] **MOGA Pro layout** (L) — D-pad bits, trigger buttons (`0x10`/`0x20`), stick clicks (`0x40`/`0x80`) in byte 5 ([protocol.md](../architecture/protocol.md)). Needs a Pro to verify.
- [ ] **Named profiles** (L) — per device type and output mode; quick switching (legacy `ProfileManager`/`SwitchProfileActivity`).
- [ ] **Per-input remapping / analog-vs-digital per input** (L) — generalises the stick layout setting (legacy "Enable Analog Input").
- [ ] **Home-screen toggle widgets** (M) — start/stop from the launcher, like the legacy `ServiceToggleWidget`.
- [ ] **Complete key catalog and IME picker shortcut** (S, good first) — the legacy `Keyboard*` tables expose far more keys; add `showInputMethodPicker()`.
- [ ] **Keyboard-mode onboarding** (S, good first) — explain enabling/selecting the IME and that it only reaches text fields.

## Protocol and fixtures

- [ ] **Golden fixtures from real captures** (S, good first with a capture) — longer Mode A streams, poll (`0x61`) responses, simultaneous buttons, noise, truncation, back-to-back reports.
- [ ] **Resynchronisation and over-long frames** (S) — confirm behaviour after dropped bytes and whether any response exceeds 12 bytes.
- [ ] **Bit-matrix confirmation** (S, needs hardware) — every button and all four digital directions per stick, in combination.
- [ ] **Battery level** (M, needs hardware) — no source known ([protocol.md](../architecture/protocol.md#not-in-the-protocol-as-far-as-we-know)). Compare the raw report (Test tab) across charge states; if a field exists, document and show it.
- [ ] **Differences between Pocket and later generations** (M) — before accepting other reports.

## Product and UX

- [ ] **Loading/empty/error polish and clearer retry** around teardown (S, good first).
- [ ] **Accessibility** (S, good first) — roles/labels on the live view, keyboard navigation, screen readers.
- [ ] **Localization** (M) — strings are inline Spanish; extract them (e.g. a small i18n layer) and add English.
- [ ] **In-app error log and "share log"** (M) — the legacy `ErrorLogger` equivalent (bounded, no personal data).
- [ ] **Settings for player ID and axis calibration** (S) — only once protocol behaviour is verified.
- [ ] **First-run walkthrough** (M) — the legacy app explained each mode up front.

## Quality and release

- [ ] **CI** (M) — fmt, clippy (desktop + Android target), tests, `pnpm build`; release job with the keystore as a secret.
- [ ] **Frontend tests** (M, good first) — Vitest + Testing Library with `lib/api.js` mocked; start with `usePolling`, `useKeyMapping`, `MappingView`.
- [ ] **Kotlin tests** (M) — `MogaPreferences`, `MogaDevices`, `UinputBridge` layout/replay logic with a fake socket.
- [ ] **End-to-end test with the mock driver** (M) — verify events reach the frontend.
- [ ] **Android API-level smoke matrix and release notes** (L) — supported hardware/versions, known limits.
- [ ] **Privacy and permissions review before release** (S).
- [ ] **Choose and add a LICENSE** (S) — required before inviting outside contributions.

## Desktop targets

- [ ] **Linux** (L) — a BlueZ RFCOMM driver; an optional `uinput` sink (never required on Android).
- [ ] **Windows** (L) — a transport driver and a maintained virtual-controller backend (ViGEm-style, desktop-only; mind licensing).
- [ ] **Shared `InputSink` trait and normalised state** (M) — without losing raw values needed for calibration.
- [ ] **Per-platform packaging, signing and CI runners** (L).

## Open investigation

- **Virtual gamepad occasionally not detected after leaving the app.** Not reproduced. Fixed what the code showed (the bridge never read its socket and only reconnected on the next report; now it detects EOF, a 2 s watchdog rebuilds the device and replays the state, and it re-checks on resume). If it returns, capture `adb logcat -d -s MogaUinput MogaRfcomm MogaConnection`. Other suspect: HyperOS/MIUI background restrictions (the Mapeo tab links to battery settings).

## Out of scope

- AdMob ads and the `show_ads` preference from the legacy app.
- Root-only paths (the legacy `SystemOutput` chowns `/dev/uinput` with RootTools; the adb helper replaces it without root).
- A touch-emulation output (the legacy `TouchOutput` is an empty stub and was never built).
- Hiding or bypassing Android's pairing UI (`setPin`, `abortBroadcast`).

## Recently completed

- Rust backend in layers (`constants`, `utils`, `schemas`, `protocol`, `drivers`, `services`, `commands`); frontend in `lib`/`hooks`/`components`/`views`/`styles`; Kotlin plugin split by responsibility; clippy/fmt clean.
- Discovery, pairing and RFCOMM with the legacy strategies plus one extra, retries and a settle delay; name matching from `EXTRA_NAME`; verified on a real Pocket.
- Correct decoding (bytes 5–9 are two analog sticks) with a hardware capture as a test.
- Virtual gamepad through `uinput` with a token-protected loopback helper, stick layouts, isolation, self-healing; verified in a game.
- Foreground-service notification; idle power-off messaging; Pocket-only disclaimer.
- Performance and leak pass: unchanged reports dropped, `moga-state` emitted on demand, error events throttled, threads and executors shut down, unused template code and the unused `opener` plugin removed.
- Dev tooling (`android:dev`, `android:uinput`, `android:sign`, `virtual-pad.py`) and this documentation set.
