# Virtual gamepad

The default output. Android lets only privileged identities create input devices, so a normal app cannot make a gamepad. The `shell` user (the identity of `adb shell`) can, and Android ships a tool for it: `/system/bin/uinput`, which reads JSON commands (`register`, `inject`, `delay`) from stdin. MOGA Bridge uses a tiny helper started once with `adb` that exposes that tool on a loopback socket, and streams the controller to it.

Everything here was **verified**: on Android 16 the helper creates a `GAMEPAD | JOYSTICK` device, and games and emulators detect and use it.

## Components

```text
Kotlin UinputBridge ──TCP 127.0.0.1:7777, first line = secret token──► helper (shell uid)
                                                                          │ toybox nc -L … sh -c 'read token; exec uinput -'
                                                                          ▼
                                                                  /system/bin/uinput  ──► /dev/uinput ──► Android input
```

| Piece | Where | Role |
|---|---|---|
| `helper.sh` | Generated from `res/raw/helper_script.sh` by `HelperFiles`, written to the app's external private dir (`/sdcard/Android/data/dev.mogabridge.app/files/`) | Start/stop/status of the helper. Run from a PC: `adb shell sh /sdcard/Android/data/dev.mogabridge.app/files/helper.sh`. |
| `helper.token` | Same directory | 24 random bytes (hex), created once. The first line of every connection must equal it. |
| helper process | Phone, detached with `setsid nohup`, runs until reboot | `toybox nc -s 127.0.0.1 -p 7777 -L sh -c '…'`: one `sh` per connection that checks the token (5 s timeout) and then `exec uinput -`. |
| `UinputBridge` | Kotlin | The client: connects, authenticates, registers the device, injects events, reconnects. |
| `scripts/uinput-helper.sh` (`pnpm android:uinput`) | Repo | Convenience wrapper that runs `helper.sh` through `adb` for developers. |

The PC is needed only to start the helper (once per phone reboot). The app shows the exact command and watches for the helper (the *Ayudante uinput* panel in the Mapeo tab).

## The device

`UinputBridge.registerCommand` creates **"MOGA Pocket"** (vendor `0x20d6`, product `0x89e5`, bus bluetooth):

| Controller | Event | Android sees |
|---|---|---|
| A / B / X / Y | `BTN_SOUTH`(304) / `BTN_EAST`(305) / `BTN_NORTH`(307) / `BTN_WEST`(308) | BUTTON_A / B / X / Y |
| L / R | `BTN_TL`(310) / `BTN_TR`(311) | BUTTON_L1 / R1 |
| Select / Start | `BTN_SELECT`(314) / `BTN_START`(315) | BUTTON_SELECT / START |
| Left stick | `ABS_X`, `ABS_Y` (-127..127, flat 8) | AXIS_X, AXIS_Y |
| Right stick | `ABS_RX`, `ABS_RY` (-127..127, flat 8) | the right-stick axes (Android decides the exact axis ids) |
| D-pad (stick converted) | `ABS_HAT0X`, `ABS_HAT0Y` (-1..1) | AXIS_HAT_X / Y |

Notes: the `uinput` tool wants a **stream of JSON objects, not a JSON array**. Android's `Generic.kl` maps `BTN_NORTH`→BUTTON_X and `BTN_WEST`→BUTTON_Y, which matches the controller's physical layout. The device exists only while a controller is connected in gamepad mode.

## Stick layouts

`StickLayout` (persisted; changed in the Mapeo tab):

| Value | Left stick | Right stick |
|---|---|---|
| `analogs` (default) | analog | analog |
| `leftDpad` | becomes the D-pad | analog |
| `rightDpad` | analog | becomes the D-pad |

A converted stick stops driving its analog axes (held at 0) and drives the hat from the controller's own digitised direction bits (byte 5 of the report), so diagonals are two bits. The hat axes are always registered. Changing the layout re-sends the current state immediately.

## Isolation (test mode)

While the *Prueba* tab shows the controller you may not want it to also move the app's own UI. The **isolation** switch (off by default, in memory only) suspends the bridge: held inputs are released once and nothing more is sent. It switches off automatically when the activity pauses (another app, notification shade) and when the Test tab is left, and the held state is restored when it ends.

## Self-healing

A dropped helper connection removes the virtual device, which used to go unnoticed until the next write. The bridge now:

- reads from the socket on a watcher thread; EOF means the helper closed, so the failure is handled immediately;
- runs a **watchdog every 2 s** while active that rebuilds the connection and device and **replays the last state**;
- re-checks on `onResume`;
- logs every transition under `MogaUinput` with the reason (`registered … (watchdog)`, `connection closed (failed)`, `suspended/resumed`).

`setActive(true)` happens when a controller connects in gamepad mode; `release()` when the link ends or the mode changes. Identical controller reports are filtered upstream in Rust, so the bridge also keeps the latest reported state and re-sends it after a layout change, after isolation ends and after a reconnect.

## Security

- The helper listens **only on loopback** and requires the token on every connection; with no/wrong token no device is created (verified).
- The token lives in the app's external private directory. On Android 11+ other apps cannot read it (scoped storage). **On Android 10 or lower, apps holding storage permission can**, so protection is weaker there.
- A hostile local connection can hold a helper `sh` for up to 5 s (the read timeout) before being dropped.
- A stronger design would be an `app_process` daemon on an abstract socket that checks the caller's UID ([roadmap](../project/roadmap.md)).
- The virtual device can inject input into whatever app is in front, which is why access is token-gated. Do not widen exposure (no non-loopback bind, no unauthenticated mode).

## Limits

- Needs a PC with `adb` once per phone reboot (wireless debugging or Shizuku could remove that — [roadmap](../project/roadmap.md)).
- Needs a controller connected; the device appears and disappears with it.
- Tested with a MOGA Pocket only; analog behaviour of the right stick is not fully verified.

## Manual testing without a controller

You can drive the helper from a PC with a short script over `adb forward tcp:7777 tcp:7777` — it sends the token line, a `register` command and `inject` events. A ready recipe is in [testing-and-debugging.md](../development/testing-and-debugging.md#driving-the-virtual-gamepad-from-a-pc).
