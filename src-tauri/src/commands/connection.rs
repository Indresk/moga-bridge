use std::sync::atomic::Ordering;

use tauri::{AppHandle, State};

use crate::schemas::{ConnectionStatus, MogaState};
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
    connection::spawn_worker(
        app,
        driver,
        state.connection.clone(),
        state.state_stream.clone(),
        state.last_state.clone(),
        device_id,
    )
}

/// The frontend asks for `moga-state` events only while it is showing them. Enabling returns
/// the latest state so the view can draw immediately.
#[tauri::command]
pub fn set_state_stream(state: State<'_, AppState>, enabled: bool) -> Option<MogaState> {
    state.state_stream.store(enabled, Ordering::Relaxed);
    if enabled {
        state.last_state.lock().ok().and_then(|last| last.clone())
    } else {
        None
    }
}

#[tauri::command]
pub fn disconnect_moga(app: AppHandle, state: State<'_, AppState>) -> Result<(), String> {
    state.driver()?.disconnect()?;
    connection::request_disconnect(&state.connection, &app)
}
