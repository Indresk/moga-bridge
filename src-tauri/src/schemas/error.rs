/// Why a received report was rejected.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum ProtocolError {
    Marker(u8),
    Length(u8),
    ResponseId(u8),
    Player(u8),
    Checksum { expected: u8, actual: u8 },
}

impl std::fmt::Display for ProtocolError {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Marker(marker) => {
                write!(formatter, "invalid report marker 0x{marker:02X}")
            }
            Self::Length(length) => write!(formatter, "unsupported report length {length}"),
            Self::ResponseId(id) => write!(formatter, "unsupported response ID 0x{id:02X}"),
            Self::Player(player) => write!(formatter, "unexpected player ID {player}"),
            Self::Checksum { expected, actual } => write!(
                formatter,
                "checksum mismatch (expected 0x{expected:02X}, got 0x{actual:02X})"
            ),
        }
    }
}

impl std::error::Error for ProtocolError {}
