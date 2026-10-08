use tauri::State;

use crate::schemas::DeviceInfo;
use crate::services::AppState;

#[tauri::command]
pub fn scan_moga(state: State<'_, AppState>) -> Result<Vec<DeviceInfo>, String> {
    state.driver()?.scan()
}

#[tauri::command]
pub fn scan_unpaired_devices(state: State<'_, AppState>) -> Result<(), String> {
    state.driver()?.scan_unpaired()
}

#[tauri::command]
pub fn stop_device_scan(state: State<'_, AppState>) -> Result<(), String> {
    state.driver()?.stop_scan()
}

#[tauri::command]
pub fn request_bluetooth_permission(state: State<'_, AppState>) -> Result<(), String> {
    state.driver()?.request_bluetooth_permission()
}
