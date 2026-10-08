use serde::Serialize;

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum ConnectionState {
    Idle,
    Connecting,
    Connected,
    Disconnecting,
    Disconnected,
    Error,
}

impl ConnectionState {
    /// A connection attempt or session is in progress and must not be restarted.
    pub fn is_active(self) -> bool {
        matches!(self, Self::Connecting | Self::Connected | Self::Disconnecting)
    }
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ConnectionStatus {
    pub state: ConnectionState,
    pub device_id: Option<String>,
    pub message: Option<String>,
}

impl ConnectionStatus {
    pub fn idle() -> Self {
        Self::new(ConnectionState::Idle, None, None)
    }

    pub fn new(
        state: ConnectionState,
        device_id: Option<String>,
        message: Option<String>,
    ) -> Self {
        Self {
            state,
            device_id,
            message,
        }
    }
}
