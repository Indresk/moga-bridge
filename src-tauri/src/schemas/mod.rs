//! Data shapes exchanged between layers and serialised to the frontend (camelCase JSON).

pub mod connection;
pub mod device;
pub mod error;
pub mod settings;
pub mod state;

pub use connection::{ConnectionState, ConnectionStatus};
pub use device::DeviceInfo;
pub use error::ProtocolError;
pub use settings::{KeyMapping, OutputMode, OutputSettings, StickLayout};
pub use state::{Buttons, MogaState, Stick};
