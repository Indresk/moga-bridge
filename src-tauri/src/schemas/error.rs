/// Why a received report was rejected.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum ProtocolError {
    InvalidMarker(u8),
    InvalidLength(u8),
    InvalidResponseId(u8),
    InvalidPlayer(u8),
    InvalidChecksum { expected: u8, actual: u8 },
}

impl std::fmt::Display for ProtocolError {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::InvalidMarker(marker) => {
                write!(formatter, "invalid report marker 0x{marker:02X}")
            }
            Self::InvalidLength(length) => write!(formatter, "unsupported report length {length}"),
            Self::InvalidResponseId(id) => write!(formatter, "unsupported response ID 0x{id:02X}"),
            Self::InvalidPlayer(player) => write!(formatter, "unexpected player ID {player}"),
            Self::InvalidChecksum { expected, actual } => write!(
                formatter,
                "checksum mismatch (expected 0x{expected:02X}, got 0x{actual:02X})"
            ),
        }
    }
}

impl std::error::Error for ProtocolError {}
