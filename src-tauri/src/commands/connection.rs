use tauri::{AppHandle, State};

use crate::schemas::ConnectionStatus;
use crate::services::{connection, AppState};

#[tauri::command]
pub fn connection_status(state: State<'_, AppState>) -> Result<ConnectionStatus, String> {
    state.status()
}

#[tauri::command]
pub fn connect_moga(
    app: AppHandle,
    state: State<'_, AppState>,
    device_id: String,
) -> Result<(), String> {
    if device_id.trim().is_empty() {
        return Err("A Bluetooth device ID is required.".into());
    }

    connection::begin(&state.connection, &app, &device_id)?;
    let driver = state.driver()?;
    connection::spawn_worker(app, driver, state.connection.clone(), device_id)
}

#[tauri::command]
pub fn disconnect_moga(app: AppHandle, state: State<'_, AppState>) -> Result<(), String> {
    state.driver()?.disconnect()?;
    connection::request_disconnect(&state.connection, &app)
}
