//! Android plugin identity. The package must match the app identifier in `tauri.conf.json`
//! (`dev.mogabridge.app`) and the Kotlin package of `MogaAndroidPlugin`.

#[cfg(target_os = "android")]
pub const PLUGIN_ID: &str = "dev.mogabridge.app.moga";
#[cfg(target_os = "android")]
pub const PLUGIN_CLASS: &str = "MogaAndroidPlugin";

/// Highest Android `KeyEvent` code accepted in a key mapping (0 disables a control).
pub const MAX_KEY_CODE: i32 = 288;
