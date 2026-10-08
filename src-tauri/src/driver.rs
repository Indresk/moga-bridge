use std::io::Read;

use serde::Serialize;

#[cfg(test)]
use std::{io::Cursor, sync::Arc};

use crate::protocol::MogaState;

#[derive(Clone, Debug, Serialize, serde::Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DeviceInfo {
    pub id: String,
    pub name: String,
    pub bonded: bool,
}

pub trait MogaDriver: InputMapper + Send + Sync + 'static {
    fn scan(&self) -> Result<Vec<DeviceInfo>, String>;
    fn scan_unpaired(&self) -> Result<(), String>;
    fn stop_scan(&self) -> Result<(), String>;
    fn connect(&self, device_id: &str) -> Result<Box<dyn MogaConnection>, String>;
    fn disconnect(&self) -> Result<(), String>;
}

pub trait MogaConnection: Read + Send {
    fn send_command(&mut self, command: [u8; 5]) -> Result<(), String>;

    fn dispatch_input_state(&mut self, _state: &MogaState) -> Result<(), String> {
        Ok(())
    }
}

pub trait InputMapper: Send + Sync + 'static {
    fn get_mapping(&self) -> Result<KeyMapping, String>;
    fn request_bluetooth_permission(&self) -> Result<(), String>;
    fn set_mapping(&self, mapping: &KeyMapping) -> Result<(), String>;
    fn open_ime_settings(&self) -> Result<(), String>;
}

#[derive(Clone, Debug, Serialize, serde::Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct KeyMapping {
    pub button_a: i32,
    pub button_b: i32,
    pub button_x: i32,
    pub button_y: i32,
    pub start: i32,
    pub select: i32,
    pub left_bumper: i32,
    pub right_bumper: i32,
    pub up: i32,
    pub down: i32,
    pub left: i32,
    pub right: i32,
    pub right_up: i32,
    pub right_down: i32,
    pub right_left: i32,
    pub right_right: i32,
}

impl Default for KeyMapping {
    fn default() -> Self {
        Self {
            button_a: 62,
            button_b: 0,
            button_x: 0,
            button_y: 0,
            start: 66,
            select: 0,
            left_bumper: 0,
            right_bumper: 0,
            up: 19,
            down: 20,
            left: 21,
            right: 22,
            right_up: 0,
            right_down: 0,
            right_left: 0,
            right_right: 0,
        }
    }
}

impl KeyMapping {
    pub fn validate(&self) -> Result<(), String> {
        let key_codes = [
            self.button_a,
            self.button_b,
            self.button_x,
            self.button_y,
            self.start,
            self.select,
            self.left_bumper,
            self.right_bumper,
            self.up,
            self.down,
            self.left,
            self.right,
            self.right_up,
            self.right_down,
            self.right_left,
            self.right_right,
        ];

        if key_codes
            .iter()
            .any(|key_code| !(0..=288).contains(key_code))
        {
            return Err("Android key codes must be between 0 (disabled) and 288.".into());
        }
        Ok(())
    }
}

pub struct PlatformDriver;

impl InputMapper for PlatformDriver {
    fn get_mapping(&self) -> Result<KeyMapping, String> {
        Err("Android keyboard mapping is available only on Android.".into())
    }

    fn request_bluetooth_permission(&self) -> Result<(), String> {
        Err("Android Bluetooth permissions are available only on Android.".into())
    }

    fn set_mapping(&self, _mapping: &KeyMapping) -> Result<(), String> {
        Err("Android keyboard mapping is available only on Android.".into())
    }

    fn open_ime_settings(&self) -> Result<(), String> {
        Err("Android input method settings are available only on Android.".into())
    }
}

impl MogaDriver for PlatformDriver {
    fn scan(&self) -> Result<Vec<DeviceInfo>, String> {
        Err("Bluetooth RFCOMM scanning is not connected to a native platform adapter yet.".into())
    }

    fn scan_unpaired(&self) -> Result<(), String> {
        Err("Bluetooth discovery is currently supported only on Android.".into())
    }

    fn stop_scan(&self) -> Result<(), String> {
        Err("Bluetooth discovery is currently supported only on Android.".into())
    }

    fn connect(&self, _device_id: &str) -> Result<Box<dyn MogaConnection>, String> {
        Err("Bluetooth RFCOMM transport is not connected to a native platform adapter yet.".into())
    }

    fn disconnect(&self) -> Result<(), String> {
        Err("Bluetooth disconnect is available only on Android.".into())
    }
}

#[cfg(test)]
pub struct MockDriver {
    packet_stream: Arc<Vec<u8>>,
}

#[cfg(test)]
impl MockDriver {
    pub fn new(packet_stream: Vec<u8>) -> Self {
        Self {
            packet_stream: Arc::new(packet_stream),
        }
    }
}

#[cfg(test)]
impl MogaDriver for MockDriver {
    fn scan(&self) -> Result<Vec<DeviceInfo>, String> {
        Ok(vec![DeviceInfo {
            id: "mock-moga-pocket".into(),
            name: "MOGA Pocket (mock)".into(),
            bonded: true,
        }])
    }

    fn scan_unpaired(&self) -> Result<(), String> {
        Ok(())
    }

    fn stop_scan(&self) -> Result<(), String> {
        Ok(())
    }

    fn connect(&self, device_id: &str) -> Result<Box<dyn MogaConnection>, String> {
        if device_id != "mock-moga-pocket" {
            return Err(format!("Unknown mock device: {device_id}"));
        }
        Ok(Box::new(MockConnection {
            incoming: Cursor::new(self.packet_stream.as_ref().clone()),
            sent_commands: Vec::new(),
        }))
    }

    fn disconnect(&self) -> Result<(), String> {
        Ok(())
    }
}

#[cfg(test)]
impl InputMapper for MockDriver {
    fn get_mapping(&self) -> Result<KeyMapping, String> {
        Ok(KeyMapping::default())
    }

    fn request_bluetooth_permission(&self) -> Result<(), String> {
        Ok(())
    }

    fn set_mapping(&self, _mapping: &KeyMapping) -> Result<(), String> {
        Ok(())
    }

    fn open_ime_settings(&self) -> Result<(), String> {
        Ok(())
    }
}

#[cfg(test)]
struct MockConnection {
    incoming: Cursor<Vec<u8>>,
    sent_commands: Vec<[u8; 5]>,
}

#[cfg(test)]
impl Read for MockConnection {
    fn read(&mut self, buffer: &mut [u8]) -> std::io::Result<usize> {
        self.incoming.read(buffer)
    }
}

#[cfg(test)]
impl MogaConnection for MockConnection {
    fn send_command(&mut self, command: [u8; 5]) -> Result<(), String> {
        self.sent_commands.push(command);
        Ok(())
    }
}
