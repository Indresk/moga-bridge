# MOGA Bridge

Use a legacy **MOGA Pocket** (Mode A) as a standard gamepad on Android — **no root required**.

MOGA Bridge is a [Tauri v2](https://tauri.app) app (Rust + React + a Kotlin Android plugin). It speaks the controller's proprietary Bluetooth serial protocol, which Android does not understand on its own, and turns it into a real virtual gamepad with analog sticks that games and emulators detect automatically. Its look is a homage to the original *MOGA Universal* app.

> **Compatibility:** the button mapping is designed only for the **MOGA Pocket in Mode A**. Other models (e.g. MOGA Pro) use a different report layout and are not supported yet. The app is Android-only today.

## Purpose and non-commercial intent

This project exists **only to revive old controllers that use this protocol**, whose official driver is obsolete and no longer maintained, so that working hardware does not end up in a drawer. It was **not created, and is not offered, for profit under any circumstances**: no sales, ads, tracking, paywalls or donation requirements, now or later. It is an independent, community effort for preservation and interoperability, **not affiliated with or endorsed by** the makers of the controllers or of the original driver; *MOGA*, *PowerA* and related names belong to their owners and appear here only to describe compatible hardware.

## Features

- Bluetooth Classic discovery, pairing and connection to the MOGA Pocket, mirroring the original app's behaviour.
- **Virtual gamepad** (analog sticks, A/B/X/Y, L/R, Start/Select) through Android's own `uinput`, started once from a PC with `adb` — no root.
- Stick layouts: two analogs, or either stick converted to a D-pad.
- Live controller test screen (buttons, sticks, raw report) with an isolation switch.
- Keyboard fallback (input method) for text fields, plus a configurable key mapping.
- Persistent notification while the controller is connected; clear messaging when the controller powers itself off after idling.

## Quick start — I just want to play (Spanish guide)

La guía completa, paso a paso, está en [`docs/user/guia-de-usuario.md`](docs/user/guia-de-usuario.md). En resumen:

1. Instala el APK de MOGA Bridge y conecta el mando (Modo A) desde la pestaña **Conexión**.
2. Para el modo gamepad, en un PC con `adb` ejecuta el comando que muestra la pestaña **Mapeo** (una vez por reinicio del móvil).
3. Abre tu juego o emulador: el mando se detecta solo.

## Quick start — I want to develop

Prerequisites: Rust, Node.js + `pnpm`, Android SDK/NDK, a JDK, and an Android phone with USB debugging. Details in [`docs/development/getting-started.md`](docs/development/getting-started.md).

```sh
pnpm install
pnpm android:dev      # builds, installs and hot-reloads on the USB-connected phone
pnpm android:uinput   # starts the virtual-gamepad helper on the phone (needs adb)
cd src-tauri && cargo test --lib   # backend tests
```

## Documentation

Start at [`docs/README.md`](docs/README.md). The short map:

| You want to… | Read |
|---|---|
| Use the app | [`docs/user/guia-de-usuario.md`](docs/user/guia-de-usuario.md) |
| Understand how it works | [`docs/architecture/overview.md`](docs/architecture/overview.md) |
| Set up, build, sign, debug | [`docs/development/`](docs/development/) |
| Contribute | [`CONTRIBUTING.md`](CONTRIBUTING.md) |
| See what is planned or decided | [`docs/project/roadmap.md`](docs/project/roadmap.md), [`docs/project/decisions.md`](docs/project/decisions.md) |
| Check the protocol sources | [`docs/project/references.md`](docs/project/references.md) |

## License

[MIT](LICENSE) (the `LICENSE` file sits at the repository root). Third-party material referenced for protocol research is not included; see [`docs/project/references.md`](docs/project/references.md).

## Project status

Verified on real hardware (Android 16 + MOGA Pocket): discovery, pairing, RFCOMM connection, live reports, the virtual gamepad in a game, stick layouts, the notification. Unverified: other Android versions and devices, other MOGA models, battery level (the controller exposes none that we know of). See the [roadmap](docs/project/roadmap.md).

## Repository layout

```text
src/                 React frontend (lib → hooks → components → views)
src-tauri/src/       Rust backend (constants, utils, schemas, protocol, drivers, services, commands)
src-tauri/gen/android/app/src/main/java/dev/mogabridge/app/moga/   Kotlin plugin
scripts/             android-dev.sh, uinput-helper.sh, sign-apk.sh
docs/                Documentation
```

The protocol was reverse-engineered from two external projects that are **not** in this repository: the original *MOGA Universal Driver* 3.1.4 APK (decompiled with jadx 1.5.6) and the [`moga-uinput`](https://github.com/jakobend/moga-uinput) Linux reference. Where to get them and how to recreate them is in [`docs/project/references.md`](docs/project/references.md); you do not need them to build or contribute.
