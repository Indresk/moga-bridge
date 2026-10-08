# Instructions for coding agents

Short rules for AI/code assistants working in this repository. Everything else is in [`docs/`](docs/README.md); read the page that matches your task before editing.

## Ground rules

- Change files only inside this repository. `decompilado-moga-universal/` (jadx 1.5.6 output of the MOGA Universal Driver 3.1.4 APK) and `moga-uinput/` (the Linux reference) may exist as sibling folders on a developer's machine; they are **read-only** protocol references, are not part of the repository, and must never be committed. Sources and how to recreate them: [`docs/project/references.md`](docs/project/references.md).
- Respect the layering: Rust `constants`/`utils` → `schemas` → `protocol` → `drivers` → `services` → `commands`; frontend `lib` → `hooks` → `components` → `views` → `App`. See [`docs/architecture/backend-rust.md`](docs/architecture/backend-rust.md) and [`docs/architecture/frontend.md`](docs/architecture/frontend.md).
- Keep the Kotlin command names identical to the strings in `src-tauri/src/drivers/android.rs`; keep event names identical in `constants/events.rs` and `src/lib/events.js`; keep control names identical in `schemas/settings.rs`, `MogaPreferences.kt` and `src/lib/keys.js`.
- UI strings are Spanish; code, comments and developer docs are English.

## Do not

- Call `setPin()` / `abortBroadcast()` or try to hide Android's pairing UI.
- Widen the virtual-gamepad helper's exposure (it must stay loopback-only and token-protected).
- Add per-report work on the hot path (see [`docs/architecture/overview.md`](docs/architecture/overview.md#the-hot-path)).
- Run `pkill -f "tauri android dev"` from an agent shell: it matches its own command line. Kill by PID.
- Assume a protocol detail: verify against `docs/architecture/protocol.md` and the references, and add a test.

## Before you finish

```sh
cd src-tauri && cargo fmt --check && cargo clippy --lib --all-targets && cargo test --lib
cd .. && pnpm build
```

Update the affected docs and [`docs/project/roadmap.md`](docs/project/roadmap.md) when behaviour or status changes. Commit trailers follow the repository convention given by your harness.

## Where things are

| Task | Page |
|---|---|
| Add a Tauri command | [`docs/architecture/backend-rust.md`](docs/architecture/backend-rust.md#adding-a-command) |
| Add a screen or setting | [`docs/architecture/frontend.md`](docs/architecture/frontend.md#adding-a-screen-or-setting) |
| Change the wire protocol | [`docs/architecture/protocol.md`](docs/architecture/protocol.md) |
| Touch Bluetooth/pairing | [`docs/architecture/bluetooth-connection.md`](docs/architecture/bluetooth-connection.md) |
| Touch the virtual gamepad | [`docs/architecture/virtual-gamepad.md`](docs/architecture/virtual-gamepad.md) |
| Build, run, debug | [`docs/development/`](docs/development/) |
