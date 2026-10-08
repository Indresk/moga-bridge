# MOGA Bridge documentation

The documents are ordered so that a newcomer can read them top to bottom. If you only have ten minutes, read the **Overview** and the **Protocol** pages.

## 1. For users

| Document | What it covers |
|---|---|
| [`user/guia-de-usuario.md`](user/guia-de-usuario.md) (Spanish) | Install, connect the controller, enable the virtual gamepad with `adb`, play, what to do when something fails. |

## 2. How the app works (architecture)

Read in this order.

| # | Document | What it covers |
|---|---|---|
| 1 | [`architecture/overview.md`](architecture/overview.md) | The three runtimes, the end-to-end data flow, threads, events, the performance "hot path". Start here. |
| 2 | [`architecture/protocol.md`](architecture/protocol.md) | The MOGA Mode A wire protocol: commands, the 12-byte report, how it is decoded, what is verified. |
| 3 | [`architecture/bluetooth-connection.md`](architecture/bluetooth-connection.md) | Discovery, pairing, the RFCOMM socket strategies, the handshake and session loop, idle power-off. |
| 4 | [`architecture/backend-rust.md`](architecture/backend-rust.md) | The layered Rust backend, every module, commands, state, tests, how to extend it. |
| 5 | [`architecture/android-plugin.md`](architecture/android-plugin.md) | The Kotlin plugin: classes, threads, permissions, manifest, notification, keyboard fallback. |
| 6 | [`architecture/virtual-gamepad.md`](architecture/virtual-gamepad.md) | The `uinput` helper, the bridge, stick layouts, isolation, self-healing, security. |
| 7 | [`architecture/frontend.md`](architecture/frontend.md) | The React app: structure, hooks, components, data flow, styling, how to extend it. |

## 3. Developing

| Document | What it covers |
|---|---|
| [`development/getting-started.md`](development/getting-started.md) | Toolchain, running on a phone, scripts, the dev loop and its gotchas. |
| [`development/building-and-signing.md`](development/building-and-signing.md) | Debug/release builds, signing, identifier, icons, regenerating the Android project. |
| [`development/testing-and-debugging.md`](development/testing-and-debugging.md) | Tests and lints, logs, hardware checklist, driving the pad without a controller, memory profiling, troubleshooting. |
| [`../CONTRIBUTING.md`](../CONTRIBUTING.md) | How to contribute: rules, checks, commits, bug reports. |

## 4. Project

| Document | What it covers |
|---|---|
| [`project/roadmap.md`](project/roadmap.md) | What is open, grouped by area, and what is explicitly out of scope. |
| [`project/decisions.md`](project/decisions.md) | Why things are the way they are (and what was tried and rejected). |
| [`project/legacy-analysis.md`](project/legacy-analysis.md) | What the original app and the Linux reference do, and how we differ. |

## Conventions used in these docs

- Code identifiers are in `monospace`; paths are relative to the repository root unless stated.
- "Pocket" means the MOGA Pocket in Mode A; "legacy app" means the decompiled MOGA Universal.
- "Helper" means the adb-started `uinput` helper process on the phone.
- Statements marked **verified** were observed on real hardware (Android 16, MOGA Pocket); others come from code reading or the references.
