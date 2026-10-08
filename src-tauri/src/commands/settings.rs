use tauri::State;

use crate::schemas::{KeyMapping, OutputMode, OutputSettings, StickLayout};
use crate::services::AppState;

#[tauri::command]
pub fn get_key_mapping(state: State<'_, AppState>) -> Result<KeyMapping, String> {
    state.driver()?.get_mapping()
}

#[tauri::command]
pub fn set_key_mapping(state: State<'_, AppState>, mapping: KeyMapping) -> Result<(), String> {
    mapping.validate()?;
    state.driver()?.set_mapping(&mapping)
}

#[tauri::command]
pub fn get_output_settings(state: State<'_, AppState>) -> Result<OutputSettings, String> {
    state.driver()?.get_output_settings()
}

#[tauri::command]
pub fn set_output_mode(state: State<'_, AppState>, mode: OutputMode) -> Result<(), String> {
    state.driver()?.set_output_mode(mode)
}

#[tauri::command]
pub fn set_stick_layout(state: State<'_, AppState>, layout: StickLayout) -> Result<(), String> {
    state.driver()?.set_stick_layout(layout)
}

#[tauri::command]
pub fn set_input_isolated(state: State<'_, AppState>, isolated: bool) -> Result<(), String> {
    state.driver()?.set_input_isolated(isolated)
}

#[tauri::command]
pub fn open_ime_settings(state: State<'_, AppState>) -> Result<(), String> {
    state.driver()?.open_ime_settings()
}
