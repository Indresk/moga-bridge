//! Platform boundary. Everything above this layer is platform-independent; each driver
//! implements transport (scan/connect/read/write) and input output for one platform.

#[cfg(target_os = "android")]
pub mod android;
#[cfg(test)]
pub mod mock;
pub mod unsupported;

use std::io::Read;

use crate::constants::protocol::COMMAND_LEN;
use crate::schemas::{DeviceInfo, KeyMapping, MogaState, OutputMode, OutputSettings, StickLayout};

pub use unsupported::UnsupportedDriver;

/// Socket strategies, tried in this order (the legacy app's order plus one extra).
#[cfg(target_os = "android")]
#[derive(Clone, Copy, Debug, serde::Serialize, serde::Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum RfcommStrategy {
    ReflectedSocketConstructor,
    ReflectedChannelOne,
    ReflectedInsecureChannelOne,
    PublicInsecureSpp,
    PublicSecureSpp,
}

#[cfg(target_os = "android")]
pub const RFCOMM_STRATEGIES: [RfcommStrategy; 5] = [
    RfcommStrategy::ReflectedSocketConstructor,
    RfcommStrategy::ReflectedChannelOne,
    RfcommStrategy::ReflectedInsecureChannelOne,
    RfcommStrategy::PublicInsecureSpp,
    RfcommStrategy::PublicSecureSpp,
];

pub trait MogaDriver: InputMapper + Send + Sync + 'static {
    fn scan(&self) -> Result<Vec<DeviceInfo>, String>;
    fn scan_unpaired(&self) -> Result<(), String>;
    fn stop_scan(&self) -> Result<(), String>;
    /// Connect (bonding first when needed) and return the byte stream of the controller.
    fn connect(&self, device_id: &str) -> Result<Box<dyn MogaConnection>, String>;
    fn disconnect(&self) -> Result<(), String>;
}

pub trait MogaConnection: Read + Send {
    fn send_command(&mut self, command: [u8; COMMAND_LEN]) -> Result<(), String>;

    /// Forward a decoded state to the platform's input output (virtual gamepad / IME).
    fn dispatch_input_state(&mut self, _state: &MogaState) -> Result<(), String> {
        Ok(())
    }
}

/// Settings of the input side: permissions, key mapping and the output mode.
pub trait InputMapper: Send + Sync + 'static {
    fn get_mapping(&self) -> Result<KeyMapping, String>;
    fn set_mapping(&self, mapping: &KeyMapping) -> Result<(), String>;
    fn request_bluetooth_permission(&self) -> Result<(), String>;
    fn open_ime_settings(&self) -> Result<(), String>;

    fn open_battery_settings(&self) -> Result<(), String> {
        Err(unsupported::ANDROID_ONLY.into())
    }

    fn get_output_settings(&self) -> Result<OutputSettings, String> {
        Err(unsupported::ANDROID_ONLY.into())
    }

    fn set_output_mode(&self, _mode: OutputMode) -> Result<(), String> {
        Err(unsupported::ANDROID_ONLY.into())
    }

    fn set_stick_layout(&self, _layout: StickLayout) -> Result<(), String> {
        Err(unsupported::ANDROID_ONLY.into())
    }

    fn set_input_isolated(&self, _isolated: bool) -> Result<(), String> {
        Err(unsupported::ANDROID_ONLY.into())
    }
}
