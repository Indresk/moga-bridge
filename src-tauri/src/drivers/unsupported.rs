use super::{InputMapper, MogaConnection, MogaDriver};
use crate::schemas::{DeviceInfo, KeyMapping};

pub const ANDROID_ONLY: &str = "This feature is currently available only on Android.";

/// Desktop placeholder: every operation fails explicitly instead of pretending to work.
pub struct UnsupportedDriver;

impl InputMapper for UnsupportedDriver {
    fn get_mapping(&self) -> Result<KeyMapping, String> {
        Err(ANDROID_ONLY.into())
    }

    fn set_mapping(&self, _mapping: &KeyMapping) -> Result<(), String> {
        Err(ANDROID_ONLY.into())
    }

    fn request_bluetooth_permission(&self) -> Result<(), String> {
        Err(ANDROID_ONLY.into())
    }

    fn open_ime_settings(&self) -> Result<(), String> {
        Err(ANDROID_ONLY.into())
    }
}

impl MogaDriver for UnsupportedDriver {
    fn scan(&self) -> Result<Vec<DeviceInfo>, String> {
        Err("Bluetooth RFCOMM scanning is not connected to a native platform adapter yet.".into())
    }

    fn scan_unpaired(&self) -> Result<(), String> {
        Err(ANDROID_ONLY.into())
    }

    fn stop_scan(&self) -> Result<(), String> {
        Err(ANDROID_ONLY.into())
    }

    fn connect(&self, _device_id: &str) -> Result<Box<dyn MogaConnection>, String> {
        Err("Bluetooth RFCOMM transport is not connected to a native platform adapter yet.".into())
    }

    fn disconnect(&self) -> Result<(), String> {
        Err(ANDROID_ONLY.into())
    }
}
