# Contributing to MOGA Bridge

Thanks for helping! This project mixes Rust, React and Kotlin, and it talks to real Bluetooth hardware, so a little context goes a long way. This page is the shortest path from "I cloned it" to "my change is merged".

## 1. Get running (about 15 minutes)

1. Follow [`docs/development/getting-started.md`](docs/development/getting-started.md) (toolchain, phone, `pnpm android:dev`).
2. Read [`docs/architecture/overview.md`](docs/architecture/overview.md) once; it explains the three runtimes (React, Rust, Kotlin) and the data flow.
3. Run the checks below to confirm a clean baseline.

You do **not** need a MOGA controller to work on most of the code: the Rust protocol and the React UI can be developed and tested without one, and [`docs/development/testing-and-debugging.md`](docs/development/testing-and-debugging.md) shows how to drive the virtual gamepad from a PC script.

## 2. Where to help

- Open items live in [`docs/project/roadmap.md`](docs/project/roadmap.md), grouped by area and marked by effort.
- Items tagged **good first** need no controller and little context: tests, accessibility, error messages, documentation.
- Hardware validation (other Android versions, other MOGA models, battery reporting) is valuable even without code: post logs and findings in an issue.
- Before starting something large (profiles, auto-reconnect, helper without a PC, new controller layouts), open an issue or discussion to agree on the approach. The reasoning behind existing choices is in [`docs/project/decisions.md`](docs/project/decisions.md).

## 3. The rules that keep the code maintainable

**Layering (Rust).** `constants`/`utils` → `schemas` → `protocol` → `drivers` → `services` → `commands`. A module may only import from layers before it. Platform code lives only in `drivers/`. Commands stay thin. Details: [`docs/architecture/backend-rust.md`](docs/architecture/backend-rust.md).

**Frontend.** Only `src/lib/api.js` calls `invoke`/`listen`. Components are presentational; behaviour lives in hooks; views compose them. Details: [`docs/architecture/frontend.md`](docs/architecture/frontend.md).

**Kotlin.** One class per responsibility; `MogaAndroidPlugin` only wires commands. Keep command names identical to `drivers/android.rs`. Details: [`docs/architecture/android-plugin.md`](docs/architecture/android-plugin.md).

**Protocol changes** need a test (and, ideally, a hardware capture) — see [`docs/architecture/protocol.md`](docs/architecture/protocol.md).

**Never** call `setPin()`/`abortBroadcast()` or otherwise hide Android's pairing UI, and never widen the virtual-gamepad helper's attack surface (see [`docs/architecture/virtual-gamepad.md`](docs/architecture/virtual-gamepad.md#security)).

**Performance.** The controller reports many times per second. Do not add per-report work to the hot path (JNI calls, IPC events, React renders) without need; read [`docs/architecture/overview.md#the-hot-path`](docs/architecture/overview.md#the-hot-path).

## 4. Before you open a pull request

```sh
cd src-tauri
cargo fmt --check
cargo clippy --lib --all-targets            # must be warning-free
cargo clippy --lib --target aarch64-linux-android
cargo test --lib
cd .. && pnpm build                          # frontend compiles
```

Also:

- Test on a device when you touch Kotlin, the plugin ACL (`build.rs`, `capabilities/`) or anything Bluetooth. State in the PR what you tested (device, Android version, controller or not).
- Add or update **tests** for protocol, schema and utility changes.
- Update the **docs** that your change affects (the roadmap, the architecture page, the user guide). Stale docs are treated as bugs.
- Keep UI strings in Spanish (the current audience) and developer docs and code comments in English.

## 5. Commits and pull requests

- One logical change per commit; imperative, descriptive subject (`Throttle error events during a malformed-report storm`), body explaining *why*.
- Keep pull requests small and focused; link the roadmap item or issue.
- Do not commit signing keys, keystores, `local.properties` or build output.

## 6. Reporting bugs

Include: phone model and Android version, whether it is a debug or release build, controller state, what you did, and the log:

```sh
adb logcat -d -s MogaUinput MogaRfcomm MogaConnection
```

For connection problems also include the in-app error text (it lists every socket strategy that failed and why).

## 7. License

No license file has been added yet. Until the maintainers choose one, treat the code as all-rights-reserved and ask before redistributing. If you are a maintainer, adding a `LICENSE` is the first thing to do before inviting outside contributions.
