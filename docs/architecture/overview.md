# Overview

## What problem this solves

The MOGA Pocket in **Mode A** talks a proprietary protocol over a Bluetooth Classic serial link (RFCOMM/SPP). Android does not recognise it as a gamepad, so games and emulators ignore it. The original *MOGA Universal* app solved this by running the protocol and either typing keys (the "virtual keyboard" mode) or, with root, creating a virtual gamepad. MOGA Bridge does the same without root: it decodes the protocol itself and creates a real virtual gamepad through Android's own `uinput` tool, started once from a PC with `adb`.

## The four runtimes

```text
┌─────────────────────────┐   invoke / events    ┌────────────────────────┐
│ WebView — React (src/)  │ ◄──────────────────► │ Rust core (src-tauri/) │
│ UI, hooks, views        │                      │ protocol, session,     │
└─────────────────────────┘                      │ commands, state        │
                                                 └───────────┬────────────┘
                                          Tauri plugin handle │ (JNI, run_mobile_plugin)
                                                 ┌───────────▼────────────┐
                                                 │ Kotlin plugin          │
                                                 │ Bluetooth, link,       │
                                                 │ notification, prefs    │
                                                 └───────────┬────────────┘
                              loopback TCP + secret token     │
                                                 ┌───────────▼────────────┐
                                                 │ helper on the phone    │
                                                 │ (started by adb as     │
                                                 │ shell) → `uinput`      │
                                                 └───────────┬────────────┘
                                                             ▼
                                                  Android sees a gamepad
```

| Runtime | Language | Responsibility | Where |
|---|---|---|---|
| WebView | React | Screens, settings, live test view. Talks only to Rust. | `src/` |
| Rust core | Rust | The protocol (build commands, decode reports), the connection session, application state, the Tauri commands. Platform-independent except `drivers/android.rs`. | `src-tauri/src/` |
| Kotlin plugin | Kotlin | Everything Android-specific: Bluetooth discovery/pairing/sockets, preferences, the virtual-gamepad bridge, the notification, the keyboard fallback. | `src-tauri/gen/android/.../moga/` |
| Helper | shell + `uinput` | Runs as the adb `shell` user, the only unprivileged identity allowed to create input devices. Started from a PC; lives until reboot. | generated `helper.sh` (see [virtual-gamepad.md](virtual-gamepad.md)) |

## End-to-end data flow

1. **Connect.** The user taps *Conectar*. `connect_moga` (Rust) marks the session *connecting* and spawns the `moga-rfcomm` worker thread. The worker asks the driver to connect; the Android driver calls the Kotlin `connect` command, which pairs if needed, opens the RFCOMM socket and starts a reader thread.
2. **Handshake.** The worker sends *select player* and *poll* ([protocol.md](protocol.md)), sets the status to *connected*, then loops.
3. **Loop.** Each iteration sends *listen*, then blocks on `read`. Kotlin's reader thread has queued whatever bytes the controller sent; the call returns them.
4. **Decode.** `PacketStreamParser` reassembles 12-byte reports from arbitrary fragments, validates them and produces a `MogaState` (buttons, two sticks with analog position and digital directions, the raw bytes).
5. **Filter.** Identical consecutive states are dropped (the controller repeats itself).
6. **Output.** For each changed state the worker (a) calls `dispatchState` on the Kotlin plugin, which feeds the virtual gamepad (or the keyboard fallback), (b) stores it as the latest state, and (c) emits `moga-state` to the UI **only if the UI asked for the live stream**.
7. **Virtual gamepad.** `UinputBridge` turns the state into `inject` commands and writes them to the helper's loopback socket; `uinput` creates the key and axis events; Android delivers them to the foreground app like any gamepad.
8. **Disconnect.** EOF or an error ends the loop; the worker publishes *disconnected*/*error* (with the idle power-off explanation when it was not user-initiated), the virtual device and the notification are removed.

## Threads

| Thread | Runtime | Purpose |
|---|---|---|
| `moga-rfcomm` | Rust | One per session: handshake, read loop, decoding, output. |
| main thread | Android | Tauri plugin commands run here (`write`, `dispatchState`, …). They must stay short. |
| `moga-rfcomm-connect` | Kotlin | One per connect attempt: pairing and socket retries (can take minutes). |
| `moga-rfcomm-read` | Kotlin | A single-thread executor that parks inside `read` until the controller sends bytes. |
| `moga-rfcomm-reader` | Kotlin | One per link: blocking `BluetoothSocket` reads into a bounded queue. |
| `moga-uinput` | Kotlin | Scheduled single thread owning the helper socket and the watchdog. |
| `moga-uinput-watch` | Kotlin | One per helper connection: detects the helper closing the socket. |
| `moga-io` (×2) | Kotlin | Short blocking jobs, e.g. the helper reachability probe. |
| WebView JS thread | WebView | React. |

Blocking Bluetooth or socket calls never run on the main thread.

## Events and commands at a glance

- **Frontend → Rust:** Tauri commands, wrapped one-to-one in `src/lib/api.js` ([backend-rust.md](backend-rust.md#commands)).
- **Rust → frontend:** `moga-status` (connection state), `moga-state` (controller state, on demand), `moga-error` (throttled), and the plugin event `moga-discovered-devices` (Kotlin).
- **Rust → Kotlin:** `run_mobile_plugin(<command>, payload)`; names are listed in [android-plugin.md](android-plugin.md#commands).

## The hot path

The controller can report many times per second while a stick moves. Per *changed* report the app currently does three Rust→Kotlin calls (`write` of the listen command, `read`, `dispatchState`) and, if the UI wants it, one event. Rules that keep this cheap — please preserve them:

- **Filter first.** Unchanged reports are dropped in Rust before any JNI call or event (`utils/change_filter.rs`).
- **Stream on demand.** `moga-state` is emitted only while the Test tab is visible (`set_state_stream`); otherwise nothing is serialised or rendered. When the stream starts, the latest state is returned immediately so the view does not wait for the next change.
- **No UI re-render for the stream.** The live state lives in `useControllerState`, used only by the Test view; the rest of the app does not re-render per report.
- **Throttle errors.** Malformed-report storms emit at most one error event per 2 s (`utils/throttle.rs`).
- **Latest-wins in the bridge.** `UinputBridge` keeps only the newest state if the helper socket is slow.
- **Poll only when visible.** Status polling pauses when the app is hidden (`usePolling`).

## Where to go next

Protocol details → [protocol.md](protocol.md). How the connection is made → [bluetooth-connection.md](bluetooth-connection.md). Adding code → [backend-rust.md](backend-rust.md), [android-plugin.md](android-plugin.md), [frontend.md](frontend.md).
