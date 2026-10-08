//! Tauri command handlers: thin wrappers that validate input and delegate to the services
//! and drivers. Register new commands in `lib.rs`.

pub mod connection;
pub mod devices;
pub mod settings;
