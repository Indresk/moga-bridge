# Testing and debugging

## Automated checks

```sh
cd src-tauri
cargo test --lib                              # 13 tests, no hardware
cargo fmt --check
cargo clippy --lib --all-targets              # warning-free
cargo clippy --lib --target aarch64-linux-android   # compiles the Android-only code too
cd .. && pnpm build                           # frontend compiles
```

What the tests cover: command bytes against the legacy app, report decoding (including a real hardware capture), fragmentation/noise, checksum errors, the helpers (`xor`, signed axis, `ChangeFilter`, `Throttle`), key-mapping validation, and a mock-driver run of reader → parser. There are **no Kotlin or frontend tests yet** ([roadmap](../project/roadmap.md)); the Kotlin and the UI are verified on a device.

## Hardware checklist

Run this on a phone with a MOGA Pocket (Mode A) when you change Kotlin, the plugin ACL or anything Bluetooth. Say in your pull request what you ran.

1. Scan, grant permissions, pair (accept the system dialog), connect → status *Conectado*, notification appears, the app jumps to *Prueba*.
2. Buttons and both sticks move in the picture; the raw report changes.
3. *Mapeo*: helper state is correct; stick layouts change the picture in *Prueba*.
4. Start the helper, open a game/emulator: the pad is detected and works; leave the app and return — still works.
5. Isolation on: the pad does not move the app; switching apps or leaving the tab turns it off.
6. Let the controller idle until it powers off: the app returns to *Conexión* with the explanation.
7. Disconnect from the notification: the virtual device and notification disappear.

## Logs

```sh
adb logcat -c
adb logcat -s MogaUinput MogaRfcomm MogaConnection        # the app's own tags
adb logcat | grep -E "Tauri/Console|RustStdoutStderr"     # WebView console and Rust eprintln!
```

| Tag | Content |
|---|---|
| `MogaRfcomm` | Which socket strategy was tried, failed (with reason) or connected (`strategy=…, round n/3`). |
| `MogaUinput` | Helper registration, close, suspend/resume, the helper's own messages, reasons for reconnects. |
| `MogaConnection` | The notification service. |
| `Tauri/Console` | JavaScript `console.*` and unhandled errors. |
| `Tauri/Plugin` | One line per plugin command call (very verbose while connected). |

In-app errors: connection failures list every strategy that failed and why; protocol errors appear throttled to one per 2 s.

## Driving the virtual gamepad from a PC

You can exercise the virtual gamepad — and test how a game reacts — without a MOGA controller:

```sh
pnpm android:uinput                                     # helper running
python3 scripts/virtual-pad.py A down stick:l:100:0 wait:1 stick:l:0:0
```

`scripts/virtual-pad.py` reads the token with `adb`, forwards the port, registers a **second** pad ("MOGA Pocket (PC test)") and sends the steps; closing the connection removes it. Steps: `A B X Y L R SELECT START`, `up down left right`, `stick:<l|r>:<x>:<y>`, `wait:<s>`. Use it to navigate a game's menu, confirm detection, or reproduce stick behaviour.

To inspect what Android sees: `adb shell dumpsys input | grep -A8 "MOGA Pocket"`.

## Memory and thread-leak checks

The app should be flat over a long session. Compare readings before and after, say, 30 minutes of play with the controller connected, and again after disconnect:

```sh
P=$(adb shell pidof dev.mogabridge.app)
adb shell "dumpsys meminfo dev.mogabridge.app | grep -E 'TOTAL PSS|Java Heap:|Native Heap:|Views:|Activities:|WebViews:'"
adb shell "cat /proc/$P/status | grep -E 'VmRSS|Threads'"
adb shell "for t in /proc/$P/task/*; do cat \$t/comm; done | sort | uniq -c | sort -rn | grep -E 'moga|tokio'"
```

What good looks like: `Activities: 1`, `WebViews: 1`, `Views` stable, the count of `moga-*` threads bounded (`moga-rfcomm-read`, `moga-rfcomm-reader` while connected, `moga-uinput`, `moga-uinput-watch` while the bridge holds a socket, `moga-io` ≤ 2) and falling back after disconnect. Growth in any of these across repeated connect/disconnect cycles is a leak. Debug builds use more memory than release.

The design choices that keep the hot path flat are in [overview.md](../architecture/overview.md#the-hot-path).

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `Webpage not available … tauri.localhost … ERR_CONNECTION_REFUSED` | The phone cannot reach Vite. Use `pnpm android:dev` (adb reverse). Check `adb reverse --list`. |
| `Port 1420 is already in use` | A leftover dev session. `ps -eo pid,cmd \| grep -E "[t]auri.js android\|[v]ite/bin/vite"`, then `kill <pid>`. |
| `moga-android.registerListener not allowed. Plugin not found` | The plugin ACL is missing: `build.rs` `InlinedPlugin` + `moga-android:default` in `capabilities/default.json`. |
| `moga-android.remove_listener not allowed` | The ACL spells it `remove_listener` (what the JS API calls). |
| `Blocking waiting for file lock on Android` | Another cargo/tauri Android build is running. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | An installed build is signed with another key: uninstall it. |
| Controller never listed | Not Mode A / not pairing / permissions / Bluetooth off; see [bluetooth-connection.md](../architecture/bluetooth-connection.md). |
| "Puente al sistema no iniciado" | The helper is not running: `pnpm android:uinput`; open the app once so the token exists. |
| `Missing …/helper.token` | Open the app once, then retry. |
| Pad stops responding after leaving the app | Check `adb logcat -s MogaUinput` for the reason; on Xiaomi/HyperOS also set the app to *No restrictions* in battery settings (the Mapeo tab has a shortcut). Not reproduced; self-healing was added ([roadmap](../project/roadmap.md)). |
| `adb shell input tap` fails with `INJECT_EVENTS` | Xiaomi: enable *USB debugging (Security settings)* in Developer options. |
| Changes not picked up | Restart `pnpm android:dev` for Kotlin/`build.rs`/manifest edits. |

## Tips

- Never use `pkill -f "tauri android dev"` from a shell whose own command line contains that text — it kills the shell. Kill by PID.
- `adb exec-out screencap -p > shot.png` takes a screenshot.
- `adb shell pm list packages | grep moga` and `adb uninstall dev.mogabridge.app` reset the app.
