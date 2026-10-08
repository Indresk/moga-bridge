# Rust backend

Location: `src-tauri/src/`. The backend owns the protocol, the connection session and the application state. It is platform-independent except for `drivers/android.rs`; on desktop a driver that fails explicitly stands in (see [Drivers](#drivers)).

## Layers

A module may import only from the layers **above** it in this table. Platform code lives only in `drivers/`.

| Layer | Path | Responsibility |
|---|---|---|
| Constants | `constants/` | Wire-protocol numbers and bit masks (`protocol.rs`), event names (`events.rs`), Android plugin id and the key-code limit (`android.rs`). No logic. |
| Utils | `utils/` | Pure helpers: `checksum` (XOR), `signed_axis` (axis decoding), `change_filter` (drop repeated values), `throttle` (rate-limit an action). All unit-tested. |
| Schemas | `schemas/` | Serialisable data shapes (camelCase JSON): `state` (`MogaState`, `Buttons`, `Stick`), `device` (`DeviceInfo`), `connection` (`ConnectionState`, `ConnectionStatus`), `settings` (`KeyMapping`, `OutputMode`, `StickLayout`, `OutputSettings`), `error` (`ProtocolError`). |
| Protocol | `protocol/` | `build_command`, `parse_report`, `PacketStreamParser`. No I/O. `fixtures.rs` builds valid reports for tests. |
| Drivers | `drivers/` | The platform boundary: the traits and their implementations. |
| Services | `services/` | `AppState` and the connection session. |
| Commands | `commands/` | Thin Tauri handlers, registered in `lib.rs`. |

`lib.rs` only declares the modules, builds the Tauri app (`manage(AppState)`, `invoke_handler`, the Android plugin) and holds one integration-style test.

## Drivers

`drivers/mod.rs` defines three traits:

- **`MogaDriver`**: `scan`, `scan_unpaired`, `stop_scan`, `connect(device_id) -> Box<dyn MogaConnection>`, `disconnect`.
- **`MogaConnection`** (`Read + Send`): `send_command([u8; 5])` and `dispatch_input_state(&MogaState)` (forward a decoded state to the platform's output; default no-op).
- **`InputMapper`**: permissions, key mapping, output settings (`get_output_settings`, `set_output_mode`, `set_stick_layout`, `set_input_isolated`), `open_ime_settings`, `open_battery_settings`. Defaults return the "Android only" error.

Implementations:

| Driver | Where | Used for |
|---|---|---|
| `AndroidDriver` | `drivers/android.rs` (`cfg(target_os = "android")`) | Calls the Kotlin plugin through a Tauri `PluginHandle` (`run_mobile_plugin`). Registered in `init()`, which replaces the driver in `AppState`. `AndroidConnection` implements `Read` by calling the plugin's blocking `read`, and closes the link on `Drop`. |
| `UnsupportedDriver` | `drivers/unsupported.rs` | Desktop placeholder: every operation fails with an explicit message instead of pretending to work. |
| `MockDriver` | `drivers/mock.rs` (`cfg(test)`) | Replays a recorded byte stream; lets the parser and session logic be tested without hardware. |

Adding a new platform (e.g. Linux over BlueZ) means writing a driver and selecting it at startup; nothing above `drivers/` changes.

## State and the session

`services/app_state.rs::AppState` (managed by Tauri):

| Field | Purpose |
|---|---|
| `driver: RwLock<Arc<dyn MogaDriver>>` | The active platform driver (starts as `UnsupportedDriver`). |
| `connection: Arc<Mutex<ConnectionStatus>>` | The session status. |
| `state_stream: Arc<AtomicBool>` | Whether the UI wants live `moga-state` events. |
| `last_state: Arc<Mutex<Option<MogaState>>>` | The latest decoded state, returned when the stream is enabled. |

`ConnectionState`: `Idle → Connecting → Connected → Disconnecting → Disconnected`, or `Error`. `is_active()` (connecting, connected, disconnecting) blocks starting a second session.

`services/connection.rs` implements the session:

1. `begin` — moves to `Connecting` (or errors if a session is active) and emits `moga-status`.
2. `spawn_worker` — a named thread `moga-rfcomm`: `driver.connect` → `run_session` → clear `last_state` → `finish`.
3. `run_session` — sends *select player* and *poll*, sets `Connected`, then loops: send *listen*, `read`, feed the parser; for each decoded report: **drop it if unchanged** (`ChangeFilter`), store it in `last_state`, forward it to the platform (`dispatch_input_state`), and emit `moga-state` **only if** `state_stream` is on. Malformed reports and forwarding errors are emitted at most once per 2 s.
4. `finish` — if the user asked to disconnect, publish `Disconnected` silently; otherwise publish `Disconnected` (clean EOF) or `Error` with `IDLE_POWER_OFF_HINT` appended.
5. `request_disconnect` — called by the `disconnect_moga` command after the driver closed the link.

## Commands

All return `Result<_, String>` (a string is what the frontend displays). Handlers validate input and delegate; they hold no logic.

| Command | Module | Delegates to |
|---|---|---|
| `scan_moga`, `scan_unpaired_devices`, `stop_device_scan`, `request_bluetooth_permission` | `commands/devices.rs` | `MogaDriver` |
| `connect_moga`, `disconnect_moga`, `connection_status`, `set_state_stream` | `commands/connection.rs` | `services::connection`, `AppState` |
| `get_key_mapping`, `set_key_mapping` (validated), `get_output_settings`, `set_output_mode`, `set_stick_layout`, `set_input_isolated`, `open_ime_settings`, `open_battery_settings` | `commands/settings.rs` | `InputMapper` |

Events (`constants/events.rs`): `moga-status` (`ConnectionStatus`), `moga-state` (`MogaState`), `moga-error` (string).

## Adding a command

1. Write the handler in the matching `commands/*.rs` (thin; add a trait method with a default "Android only" error if it needs the platform).
2. Implement it in `drivers/android.rs` (`self.invoke("kotlinCommand", payload)`) and in the Kotlin plugin with the **same command name** ([android-plugin.md](android-plugin.md#commands)).
3. Register it in `lib.rs`'s `generate_handler!`.
4. Add a wrapper in `src/lib/api.js` (the only place that calls `invoke`).
5. No permission entry is needed: your app's own commands are allowed by default, and Kotlin commands called from Rust through `run_mobile_plugin` bypass the ACL. Only commands that **JavaScript calls on the plugin itself** (the listener commands) need one — see [the plugin ACL](#the-plugin-acl).

## The plugin ACL

The Android plugin is registered at runtime, so Tauri's permission system does not know it. `build.rs` declares it with an `InlinedPlugin` listing the commands the **JavaScript API** calls on it — `registerListener` and `remove_listener` (exactly those spellings) — and `capabilities/default.json` grants `moga-android:default`. Without this, `addPluginListener` fails with `moga-android.registerListener not allowed. Plugin not found`. Add a command here only if JavaScript will call it directly.

## Tests

`cd src-tauri && cargo test --lib` (13 tests, no hardware): command bytes against the legacy app, report decoding (including a real hardware capture), fragmentation and noise, checksum errors, the XOR and axis helpers, `ChangeFilter`, `Throttle`, the key-mapping validation, and a mock-driver end-to-end of reader → parser. Lints: `cargo fmt --check`, `cargo clippy --lib --all-targets` and the Android target (`--target aarch64-linux-android`) must stay warning-free.

## Conventions

- Errors cross the Tauri boundary as `String`; use `format!` with context. Inside protocol code use typed errors (`ProtocolError`).
- No `unwrap`/`expect` on runtime paths; lock poisoning is handled (`map_err` or `if let Ok`).
- Anything platform-specific is behind `cfg(target_os = "android")` and lives in `drivers/`.
- Do not add per-report work to `run_session` without reading [the hot path](overview.md#the-hot-path).
