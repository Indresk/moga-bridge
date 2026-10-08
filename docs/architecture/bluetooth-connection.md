# Bluetooth connection

How the app finds, pairs with and connects to the controller. The Kotlin classes involved are in [android-plugin.md](android-plugin.md); the session loop that runs afterwards is in [overview.md](overview.md#end-to-end-data-flow).

## Permissions

| Android | Needed | Why |
|---|---|---|
| 12+ (API 31) | `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` (declared `neverForLocation`) | Pairing, sockets, discovery. |
| 13+ (API 33) | `POST_NOTIFICATIONS` (optional) | The connection notification. Denying it only hides the notification. |
| 6–11 | `ACCESS_FINE_LOCATION` | Required by Classic discovery on those versions. |
| ≤ 11 | legacy `BLUETOOTH`, `BLUETOOTH_ADMIN` | Declared with `maxSdkVersion="30"`. |
| any | `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` | The notification's foreground service. |

`requestBluetoothPermission` asks for the set that applies to the running Android version. Keep the requested surface limited to these.

## Discovery

`DeviceDiscovery` runs a Classic inquiry (`startDiscovery`) with a temporary broadcast receiver and publishes the lists to the UI through the plugin event `moga-discovered-devices` (`{ devices, bondedDevices, scanning }`).

- **Name matching** (`MogaDevices.isMogaName`): case-insensitive prefix `BD&A`, `BDA` or `MOGA`, and the name must **not** contain `HID` (that is Mode B, a standard HID gamepad that Android handles natively). The legacy app matched the exact names `BD&A` and `Moga Pro`; the looser prefix mirrors the Linux reference.
- **Use the inquiry name.** `device.name` is often still `null` when a device is first found; the name arrives in `EXTRA_NAME`.
- **No SDP lookup during inquiry** (`fetchUuidsWithSdp`). It slows the inquiry and can make the Pocket drop out; the legacy app matched by name only. `ACTION_UUID` is still honoured if it arrives.
- The receiver stays registered for 5 s after the inquiry finishes so late UUID results arrive, then is unregistered. It is also unregistered on stop and when the plugin is destroyed.

## Pairing

`DeviceBonding.ensureBonded` calls the public `BluetoothDevice.createBond()` and waits (up to 90 s) for the bond-state broadcast, exactly like the legacy *Add Device* flow. **Android owns the confirmation/PIN dialog**; the legacy app never used `setPin()` or `abortBroadcast()`, and this project must not either.

If the user cancels (disconnect while pairing), a latch wakes the wait and the attempt aborts.

## Socket strategies

`RfcommConnector` tries, in order, over **3 rounds** with a 500 ms pause between rounds, after a 2 s settle delay (the Pocket often refuses the first connect right after pairing or inquiry — the legacy app also looped):

| # | Strategy (`RfcommSockets`) | Notes |
|---|---|---|
| 1 | `reflectedSocketConstructor` | Hidden 7-argument `BluetoothSocket` constructor (legacy first choice). **Blocked on Android 16.** |
| 2 | `reflectedChannelOne` | Hidden `createRfcommSocket(1)`. **Verified: the one that works on Android 16.** |
| 3 | `reflectedInsecureChannelOne` | Hidden `createInsecureRfcommSocket(1)` (added by this project). |
| 4 | `publicInsecureSpp` | `createInsecureRfcommSocketToServiceRecord(SPP)` — resolves the channel through SDP. |
| 5 | `publicSecureSpp` | `createRfcommSocketToServiceRecord(SPP)`. |

The list lives in Rust (`drivers/mod.rs::RFCOMM_STRATEGIES`) and is sent to Kotlin at connect time, so reordering or pruning is a Rust-side change. Every failure is logged under `MogaRfcomm` and listed in the final error message if all fail; the winner is logged as `RFCOMM connected with strategy=… (round n/3)`. "Insecure" refers to the socket's authentication options; it does not bypass the bond.

Hidden-API reflection may be blocked or removed on any Android version; the public strategies are the safety net. Keep all five until broader device testing says otherwise ([roadmap](../project/roadmap.md)).

## Cancellation

Every connect attempt captures a generation counter; `closeConnection()` bumps it, cancels bonding, closes the socket being connected and the open link. Each blocking step checks `isCancelled()`, so *Desconectar* works at any stage.

## After connecting

The Rust worker sends *select player* and *poll*, marks the session **connected**, then loops *listen → read → decode → output* ([protocol.md](protocol.md)). On the Kotlin side `ControllerLink` keeps a reader thread that blocks on the socket and queues chunks (capacity 128) for the Rust `read` calls; an empty chunk means the link ended.

## The controller powers itself off

The Pocket switches itself off after a period without input. It looks like a lost link (clean EOF or a read error). The app treats it as expected: unexpected disconnects carry an explanatory message (`IDLE_POWER_OFF_HINT` in `services/connection.rs`), the UI returns to the Connection tab where the same note is always visible, and user-requested disconnects stay silent. Auto-reconnect would fit naturally here ([roadmap](../project/roadmap.md)). The legacy app also sends a keep-alive every 5 minutes; whether that prevents the power-off is untested.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| Controller never listed | Not in Mode A, not in pairing mode, permissions missing, Bluetooth off; name not matching (see above). |
| "All legacy MOGA RFCOMM connection methods failed …" | Read the per-strategy reasons in the message / `MogaRfcomm` logs; remove the stale bond and retry. |
| Pairing "timed out" | The system dialog was not confirmed within 90 s. |
