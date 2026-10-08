#[cfg(target_os = "android")]
mod android;
mod driver;
pub mod protocol;

use std::{
    io::Read,
    sync::{Arc, Mutex, RwLock},
    thread,
};

use driver::{DeviceInfo, KeyMapping, MogaDriver, PlatformDriver};
use protocol::{build_command, Command, PacketStreamParser};
use serde::Serialize;
use tauri::{AppHandle, Emitter, State};

struct AppState {
    driver: RwLock<Arc<dyn MogaDriver>>,
    connection: Arc<Mutex<ConnectionStatus>>,
}

impl Default for AppState {
    fn default() -> Self {
        Self {
            driver: RwLock::new(Arc::new(PlatformDriver)),
            connection: Arc::new(Mutex::new(ConnectionStatus::idle())),
        }
    }
}

impl AppState {
    fn current_driver(&self) -> Result<Arc<dyn MogaDriver>, String> {
        self.driver
            .read()
            .map(|driver| Arc::clone(&driver))
            .map_err(|error| format!("Could not access the MOGA driver: {error}"))
    }

    #[cfg(target_os = "android")]
    fn set_driver(&self, driver: Arc<dyn MogaDriver>) -> Result<(), String> {
        *self
            .driver
            .write()
            .map_err(|error| format!("Could not initialize the MOGA driver: {error}"))? = driver;
        Ok(())
    }
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct ConnectionStatus {
    state: String,
    device_id: Option<String>,
    message: Option<String>,
}

impl ConnectionStatus {
    fn idle() -> Self {
        Self {
            state: "idle".into(),
            device_id: None,
            message: None,
        }
    }
}

#[tauri::command]
fn scan_moga(state: State<'_, AppState>) -> Result<Vec<DeviceInfo>, String> {
    state.current_driver()?.scan()
}

#[tauri::command]
fn scan_unpaired_devices(state: State<'_, AppState>) -> Result<(), String> {
    state.current_driver()?.scan_unpaired()
}

#[tauri::command]
fn stop_device_scan(state: State<'_, AppState>) -> Result<(), String> {
    state.current_driver()?.stop_scan()
}

#[tauri::command]
fn set_key_mapping(state: State<'_, AppState>, mapping: KeyMapping) -> Result<(), String> {
    mapping.validate()?;
    state.current_driver()?.set_mapping(&mapping)
}

#[tauri::command]
fn get_key_mapping(state: State<'_, AppState>) -> Result<KeyMapping, String> {
    state.current_driver()?.get_mapping()
}

#[tauri::command]
fn request_bluetooth_permission(state: State<'_, AppState>) -> Result<(), String> {
    state.current_driver()?.request_bluetooth_permission()
}

#[tauri::command]
fn open_ime_settings(state: State<'_, AppState>) -> Result<(), String> {
    state.current_driver()?.open_ime_settings()
}

#[tauri::command]
fn connection_status(state: State<'_, AppState>) -> Result<ConnectionStatus, String> {
    state
        .connection
        .lock()
        .map(|status| status.clone())
        .map_err(|error| format!("Could not read connection status: {error}"))
}

#[tauri::command]
fn connect_moga(
    app: AppHandle,
    state: State<'_, AppState>,
    device_id: String,
) -> Result<(), String> {
    if device_id.trim().is_empty() {
        return Err("A Bluetooth device ID is required.".into());
    }

    {
        let mut status = state
            .connection
            .lock()
            .map_err(|error| format!("Could not update connection status: {error}"))?;
        if status.state == "connecting"
            || status.state == "connected"
            || status.state == "disconnecting"
        {
            return Err("A MOGA connection is already active.".into());
        }
        *status = ConnectionStatus {
            state: "connecting".into(),
            device_id: Some(device_id.clone()),
            message: None,
        };
        emit_status(&app, &status);
    }

    let driver = state.current_driver()?;
    let connection = Arc::clone(&state.connection);
    let app_for_error = app.clone();
    let device_id_for_error = device_id.clone();
    thread::Builder::new()
        .name("moga-rfcomm".into())
        .spawn(move || {
            let result = driver.connect(&device_id).and_then(|mut stream| {
                stream.send_command(build_command(Command::SelectPlayer, 1))?;
                stream.send_command(build_command(Command::Poll, 1))?;
                set_status(
                    &connection,
                    &app,
                    "connected",
                    Some(device_id.clone()),
                    None,
                );
                let mut parser = PacketStreamParser::default();
                let mut buffer = [0_u8; 64];

                loop {
                    stream.send_command(build_command(Command::Listen, 1))?;
                    match stream.read(&mut buffer) {
                        Ok(0) => break,
                        Ok(bytes_read) => {
                            for packet in parser.feed(&buffer[..bytes_read]) {
                                match packet {
                                    Ok(state) => {
                                        if let Err(error) = stream.dispatch_input_state(&state) {
                                            let message = format!(
                                                "Could not send MOGA state to Android input: {error}"
                                            );
                                            eprintln!("{message}");
                                            if let Err(emit_error) =
                                                app.emit("moga-error", message)
                                            {
                                                eprintln!(
                                                    "Could not emit Android input error: {emit_error}"
                                                );
                                            }
                                        }
                                        if let Err(error) = app.emit("moga-state", state) {
                                            eprintln!("Could not emit MOGA state event: {error}");
                                        }
                                    }
                                    Err(error) => {
                                        let message = format!("Rejected MOGA packet: {error}");
                                        eprintln!("{message}");
                                        if let Err(emit_error) = app.emit("moga-error", message) {
                                            eprintln!(
                                                "Could not emit MOGA protocol error event: {emit_error}"
                                            );
                                        }
                                    }
                                }
                            }
                        }
                        Err(error) => return Err(format!("RFCOMM read failed: {error}")),
                    }
                }

                Ok(())
            });

            match result {
                Ok(()) => set_status(&connection, &app, "disconnected", None, None),
                Err(error) => {
                    let disconnect_requested = connection
                        .lock()
                        .map(|status| status.state == "disconnecting")
                        .unwrap_or(false);
                    if disconnect_requested {
                        set_status(&connection, &app, "disconnected", None, None);
                    } else {
                        set_status(
                            &connection,
                            &app,
                            "error",
                            Some(device_id),
                            Some(error),
                        );
                    }
                }
            }
        })
        .map_err(|error| {
            let message = format!("Could not start the MOGA connection thread: {error}");
            set_status(
                &state.connection,
                &app_for_error,
                "error",
                Some(device_id_for_error),
                Some(message.clone()),
            );
            message
        })?;

    Ok(())
}

#[tauri::command]
fn disconnect_moga(app: AppHandle, state: State<'_, AppState>) -> Result<(), String> {
    state.current_driver()?.disconnect()?;
    let mut status = state
        .connection
        .lock()
        .map_err(|error| format!("Could not update connection status: {error}"))?;
    if status.state == "connecting" || status.state == "connected" {
        status.state = "disconnecting".into();
        status.message = None;
        emit_status(&app, &status);
    } else {
        *status = ConnectionStatus::idle();
        status.state = "disconnected".into();
        emit_status(&app, &status);
    }
    Ok(())
}

fn set_status(
    connection: &Mutex<ConnectionStatus>,
    app: &AppHandle,
    state: &str,
    device_id: Option<String>,
    message: Option<String>,
) {
    match connection.lock() {
        Ok(mut status) => {
            *status = ConnectionStatus {
                state: state.into(),
                device_id,
                message,
            };
            emit_status(app, &status);
        }
        Err(error) => eprintln!("Could not update MOGA connection status: {error}"),
    }
}

fn emit_status(app: &AppHandle, status: &ConnectionStatus) {
    if let Err(error) = app.emit("moga-status", status.clone()) {
        eprintln!("Could not emit MOGA connection status: {error}");
    }
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let builder = tauri::Builder::default()
        .manage(AppState::default())
        .plugin(tauri_plugin_opener::init())
        .invoke_handler(tauri::generate_handler![
            scan_moga,
            scan_unpaired_devices,
            stop_device_scan,
            connect_moga,
            disconnect_moga,
            connection_status,
            get_key_mapping,
            request_bluetooth_permission,
            set_key_mapping,
            open_ime_settings
        ]);

    #[cfg(target_os = "android")]
    let builder = builder.plugin(android::init());

    builder
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}

#[cfg(test)]
mod tests {
    use super::*;
    use driver::MockDriver;
    use protocol::{build_command, Command};

    #[test]
    fn mock_driver_exercises_stream_parser() {
        let bytes = protocol::test_packet(0x61, 1, 0x05, 0x09, [128, 255, 0, 127]);
        let driver = MockDriver::new(bytes.to_vec());
        let devices = driver.scan().expect("mock scan should work");
        assert_eq!(devices.len(), 1);

        let mut stream = driver.connect(&devices[0].id).expect("mock connect");
        let mut received = Vec::new();
        stream.read_to_end(&mut received).expect("read mock stream");
        let mut parser = PacketStreamParser::default();
        let parsed = parser.feed(&received);
        let state = parsed.into_iter().next().expect("one packet").unwrap();

        assert!(state.buttons.y);
        assert!(state.buttons.a);
        assert!(state.dpad.up);
        assert!(state.dpad.right);
        assert_eq!(state.axes, [128, 255, 0, 127]);
    }

    #[test]
    fn commands_have_expected_player_one_checksums() {
        assert_eq!(
            build_command(Command::SelectPlayer, 1),
            [0x5A, 0x05, 0x43, 0x01, 0x1D]
        );
        assert_eq!(
            build_command(Command::Poll, 1),
            [0x5A, 0x05, 0x41, 0x01, 0x1F]
        );
        assert_eq!(
            build_command(Command::Listen, 1),
            [0x5A, 0x05, 0x44, 0x01, 0x1A]
        );
    }

    #[test]
    fn key_mapping_rejects_out_of_range_android_codes() {
        let mut mapping = KeyMapping::default();
        assert!(mapping.validate().is_ok());
        mapping.button_a = 289;
        assert!(mapping.validate().is_err());
    }
}
