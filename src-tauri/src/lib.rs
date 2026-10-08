//! MOGA Pocket Bridge backend.
//!
//! Layers (each only depends on the ones listed before it):
//! `constants` / `utils` → `schemas` → `protocol` → `drivers` → `services` → `commands`.

mod commands;
mod constants;
mod drivers;
mod protocol;
mod schemas;
mod services;
mod utils;

use services::AppState;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let builder = tauri::Builder::default()
        .manage(AppState::default())
        .invoke_handler(tauri::generate_handler![
            commands::devices::scan_moga,
            commands::devices::scan_unpaired_devices,
            commands::devices::stop_device_scan,
            commands::devices::request_bluetooth_permission,
            commands::connection::connect_moga,
            commands::connection::disconnect_moga,
            commands::connection::connection_status,
            commands::connection::set_state_stream,
            commands::settings::get_key_mapping,
            commands::settings::set_key_mapping,
            commands::settings::get_output_settings,
            commands::settings::set_output_mode,
            commands::settings::set_stick_layout,
            commands::settings::set_input_isolated,
            commands::settings::open_ime_settings,
            commands::settings::open_battery_settings,
        ]);

    #[cfg(target_os = "android")]
    let builder = builder.plugin(drivers::android::init());

    builder
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}

#[cfg(test)]
mod tests {
    use std::io::Read;

    use crate::constants::protocol::REPORT_LEN;
    use crate::drivers::mock::MockDriver;
    use crate::drivers::MogaDriver;
    use crate::protocol::{fixtures::test_packet, PacketStreamParser};

    #[test]
    fn mock_driver_exercises_stream_parser() {
        let first = test_packet(0x64, 1, 0x04, 0x01, [0, 0, 0, 0]);
        let second = test_packet(0x64, 1, 0x00, 0x00, [0, 0, 0, 0]);
        let mut bytes = Vec::new();
        bytes.extend_from_slice(&first);
        bytes.extend_from_slice(&second);

        let driver = MockDriver::new(bytes);
        let mut connection = driver.connect("mock-moga-pocket").expect("mock connection");
        let mut buffer = [0_u8; REPORT_LEN * 2];
        let read = connection.read(&mut buffer).expect("read");
        let mut parser = PacketStreamParser::default();
        let states = parser.feed(&buffer[..read]);

        assert_eq!(states.len(), 2);
        assert!(states[0].as_ref().expect("first").buttons.a);
        assert!(states[0].as_ref().expect("first").left_stick.up);
        assert!(!states[1].as_ref().expect("second").buttons.a);
    }
}
