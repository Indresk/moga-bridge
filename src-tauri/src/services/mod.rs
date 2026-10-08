//! Application logic that sits between the Tauri commands and the platform drivers.

mod app_state;
pub mod connection;

pub use app_state::AppState;
