# External references

The protocol was reverse-engineered from two external projects that are **not part of this repository** (they are not committed and must not be). This page says where they come from and how to recreate them locally if you want to read the original code or verify a protocol detail. You do **not** need them to build, run or contribute to the app; they matter only for protocol research.

## 1. The original app: MOGA Universal Driver 3.1.4

| | |
|---|---|
| App | *MOGA Universal Driver* 3.1.4 (package `net.obsidianx.android.mogaime`) |
| File | `MOGA Universal Driver_3.1.4_apk-dl.com.apk` (537.2 KB) |
| Download | <https://archive.org/details/moga-universal-driver-android-314> |
| Decompiler | [jadx](https://github.com/skylot/jadx) **1.5.6** |

It is closed-source third-party software. We use it only to understand the controller's protocol for interoperability; the APK and its decompiled output are **never** committed or redistributed here. Our code is a clean reimplementation: it follows the protocol facts (byte layouts, command bytes, connection order), not the original's source.

### Recreating the decompiled sources

```sh
# jadx 1.5.6 (https://github.com/skylot/jadx/releases)
jadx -d decompilado-moga-universal "MOGA Universal Driver_3.1.4_apk-dl.com.apk"
```

jadx writes two folders: `sources/` (Java) and `resources/` (manifest, `res/`, native libs). The docs refer to this layout.

### What we read from it

| Topic | File (under `decompilado-moga-universal/`) |
|---|---|
| Connection loop, handshake, socket strategies, 5-minute ping, bad-packet threshold | `sources/net/obsidianx/android/mogaime/service/BluetoothThread.java` |
| Discovery, pairing, name filtering | `…/mogaime/BluetoothState.java`, `…/settings/fragments/AddDeviceFragment.java`, `…/DeviceType.java` |
| Pocket report layout | `…/service/MOGAPocketState.java`, `…/service/MOGAState.java` |
| Pro report layout | `…/service/MOGAProState.java` |
| Outputs (keyboard, root gamepad, touch stub) | `…/outputs/IMEOutput.java`, `SystemOutput.java`, `TouchOutput.java` |
| Profiles, widgets, official-app conflicts | `…/profiles/`, `…/widget/`, `…/PivotUtil.java`, `…/KillPivotTask.java` |
| Visual identity (icons, "on" green, Holo look) | `resources/res/drawable-*-v4/`, `resources/res/values/` |

The app's launcher and controller icons used in this project (`src/assets/legacy/`) come from that APK's resources, as a homage.

## 2. The Linux reference: moga-uinput

| | |
|---|---|
| Project | `moga-uinput` — a userland Linux driver for MOGA gamepads in "A" mode |
| Repository | <https://github.com/jakobend/moga-uinput> |
| Author / licence | Jakob Endrikat, MIT |

Clone it next to this repository:

```sh
git clone https://github.com/jakobend/moga-uinput
```

We read `moga-uinput.py` (a working prototype) for the report validation, the signed-axis rule, name matching, the evdev button mapping, and the second-generation command differences. Credit to its author: it made the protocol approachable.

## Suggested workspace layout

```text
moga-workspace/
├── moga-tauri/                      this repository
├── decompilado-moga-universal/      jadx output (local only)
├── moga-uinput/                     clone of the Linux reference (local only)
└── MOGA Universal Driver_3.1.4_apk-dl.com.apk   (local only, optional)
```

Nothing in the build depends on the sibling folders. Documentation and code comments that cite a class or file name point into them.

## Rules

- Never commit the APK, the decompiled output or copies of the reference sources. `.gitignore` already excludes `*.apk`, `decompilado-moga-universal/` and `moga-uinput/` in case you place them inside the repository by mistake.
- Treat both as **read-only**. If you verify something against them, record the finding in [`../architecture/protocol.md`](../architecture/protocol.md) or [`legacy-analysis.md`](legacy-analysis.md) with the file and class name, and add a test.
- Prefer hardware captures over reading code when the two disagree ([`legacy-analysis.md`](legacy-analysis.md#differences-we-chose)).
