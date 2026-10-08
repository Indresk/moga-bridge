# MOGA Mode A protocol

Everything in this page is implemented in `src-tauri/src/protocol/` and `src-tauri/src/constants/protocol.rs`, independent of any platform. Sources: the decompiled legacy app (`BluetoothThread`, `MOGAPocketState`) and the Linux reference (`moga-uinput.py`); neither is in this repository — see [references](../project/references.md) for where they come from, and [legacy-analysis](../project/legacy-analysis.md) for how they differ.

## Transport

- Bluetooth Classic, serial port profile (SPP), **RFCOMM**. SPP UUID `00001101-0000-1000-8000-00805F9B34FB`.
- The legacy app connects to **RFCOMM channel 1** directly instead of resolving the service record. **Verified:** on Android 16 only the channel-1 strategy connects ([bluetooth-connection.md](bluetooth-connection.md#socket-strategies)).
- The controller is addressed as **player 1**.

## Commands (host → controller)

Five bytes: `[0x5A, 0x05, command, player, XOR(first four bytes)]`.

| Command | Byte | Player 1 message | Purpose |
|---|---|---|---|
| Select player | `0x43` | `5A 05 43 01 1D` | Assign the player slot (sent once after connecting). |
| Poll | `0x41` | `5A 05 41 01 1F` | Ask for the current state (sent once after connecting). |
| Listen | `0x44` | `5A 05 44 01 1A` | Ask the controller to report; sent **before every read**. |

Built by `protocol::build_command`; the three messages above are unit-tested against the legacy app's bytes.

## Reports (controller → host)

Twelve bytes, always: `7A 0C <response> <player> <buttons> <sticks> <lx> <ly> <rx> <ry> <reserved> <xor>`.

| Offset | Field | Notes |
|---|---|---|
| 0 | marker `0x7A` | The parser resynchronises on it after noise. |
| 1 | length `0x0C` | Must equal 12. |
| 2 | response ID | `0x61` (answer to poll) or `0x64` (answer to listen). **Verified:** real traffic is `0x64`. |
| 3 | player | Must be 1. |
| 4 | buttons | bit mask, below |
| 5 | digitised sticks | bit mask, below |
| 6, 7 | left stick X, Y | signed byte, below |
| 8, 9 | right stick X, Y | signed byte, below |
| 10 | reserved | Always 0 so far. Kept in `raw`. |
| 11 | checksum | XOR of bytes 0–10. |

### Buttons (byte 4)

| `01` Y | `02` B | `04` A | `08` X | `10` Start | `20` Select | `40` L | `80` R |
|---|---|---|---|---|---|---|---|

### Sticks (byte 5)

The legacy app calls these `LeftUp…` / `RightUp…` "pads". **They are the two analog sticks, digitised** — the app's own text says the joysticks cannot be used as analogs through its keyboard mode, "only DPads". This was confirmed on hardware: pushing the left stick down sets bit `02` *and* byte 7 = 128.

| `01/02/04/08` | Left stick up / down / left / right |
|---|---|
| `10/20/40/80` | Right stick up / down / left / right |

Whether the user's unit has a separate physical D-pad, and where it would report, is unverified.

### Analog axes (bytes 6–9)

A signed byte in an unusual wrap-around form: `0..=127` are the positive half and `128..=255` map to `-127..=0` (the Linux reference does `value - 255`). Implemented in `utils/signed_axis.rs`.

The controller's Y axis is "up is positive"; the app exposes **down-positive** (screen/evdev convention), so `y = -decode(byte)`. Resulting `Stick { x, y }` is `-127..=127`, centre `0`, x right-positive.

Hardware captures (left stick): pushed down → bytes 6..9 `[0, 128, 0, 0]` (y = +127); down+left → `[128, 192, 0, 0]` (x = -127, y = +63). The right stick's sign and the full range are **not yet verified**.

## Decoding in code

```text
bytes ──► PacketStreamParser::feed ──► Vec<Result<MogaState, ProtocolError>>
            • buffers fragments, drops bytes before the next 0x7A
            • parse_report: length, marker, response ID, player, XOR
```

`MogaState` (`schemas/state.rs`) is the serialised shape the frontend receives:

```jsonc
{ "responseId": 100, "player": 1,
  "buttons": { "y": false, "b": false, "a": true, "x": false, "start": false,
               "select": false, "leftBumper": false, "rightBumper": false },
  "leftStick":  { "x": 0, "y": 127, "up": false, "down": true, "left": false, "right": false },
  "rightStick": { "x": 0, "y": 0, "up": false, "down": false, "left": false, "right": false },
  "raw": [122, 12, 100, 1, 4, 2, 0, 128, 0, 0, 0, 0] }
```

Errors (`schemas/error.rs`): `Marker`, `Length`, `ResponseId`, `Player`, `Checksum`. A rejected report is reported to the UI at most once every 2 s.

## Strictness and reference differences

- The legacy Java checks the XOR and the 12-byte block but **not** the marker or response ID. The Python reference validates marker, size, XOR, player and response IDs. This implementation follows the **stricter** Python checks for the fixed 12-byte Pocket report, and real hardware passes them.
- The Python reference reads longer frames if the declared size exceeds 12; nothing observed needs it. Treat any support for other lengths as an explicit protocol change with tests.
- The Python base mapping is not proven for every MOGA generation; the implementation follows the Java **Pocket** state mapping.
- The MOGA **Pro** uses the same offsets differently (D-pad in the low nibble of byte 5, trigger buttons `0x10/0x20`, stick clicks `0x40/0x80`). It is **not supported**; the UI says so.

## Changing the protocol

1. Capture real bytes (the Test tab shows the raw report; `adb logcat` and `MockDriver` help).
2. Add the constants to `constants/protocol.rs`, the decoding to `protocol/parser.rs`, and a test using `protocol/fixtures.rs::test_packet` (ideally with a hardware capture as the expected value).
3. Update this page and the roadmap.

## Not in the protocol (as far as we know)

- **Battery level.** The legacy app never reads one, the 12-byte report has no documented field, and Android lists the controller with SPP only. If you find a field, document it here.
