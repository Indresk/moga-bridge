use std::sync::{atomic::AtomicBool, Arc, Mutex, RwLock};

use crate::drivers::{MogaDriver, UnsupportedDriver};
use crate::schemas::{ConnectionStatus, MogaState};

/// State shared by every command: the active platform driver and the connection status.
pub struct AppState {
    driver: RwLock<Arc<dyn MogaDriver>>,
    pub connection: Arc<Mutex<ConnectionStatus>>,
    /// Whether the frontend is showing live controller state. When off, `moga-state` events
    /// are not emitted (the platform output still receives every change).
    pub state_stream: Arc<AtomicBool>,
    /// The latest decoded state of the active session, handed to the frontend when it starts
    /// listening (identical reports are filtered, so a new listener may wait a long time).
    pub last_state: Arc<Mutex<Option<MogaState>>>,
}

impl Default for AppState {
    fn default() -> Self {
        Self {
            driver: RwLock::new(Arc::new(UnsupportedDriver)),
            connection: Arc::new(Mutex::new(ConnectionStatus::idle())),
            state_stream: Arc::new(AtomicBool::new(false)),
            last_state: Arc::new(Mutex::new(None)),
        }
    }
}

impl AppState {
    pub fn driver(&self) -> Result<Arc<dyn MogaDriver>, String> {
        self.driver
            .read()
            .map(|driver| Arc::clone(&driver))
            .map_err(|error| format!("Could not access the MOGA driver: {error}"))
    }

    /// Replace the driver (called by the Android plugin during setup).
    #[cfg(target_os = "android")]
    pub fn set_driver(&self, driver: Arc<dyn MogaDriver>) -> Result<(), String> {
        *self
            .driver
            .write()
            .map_err(|error| format!("Could not initialize the MOGA driver: {error}"))? = driver;
        Ok(())
    }

    pub fn status(&self) -> Result<ConnectionStatus, String> {
        self.connection
            .lock()
            .map(|status| status.clone())
            .map_err(|error| format!("Could not read connection status: {error}"))
    }
}
