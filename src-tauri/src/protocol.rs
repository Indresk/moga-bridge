use serde::Serialize;

pub const RFCOMM_SPP_UUID: &str = "00001101-0000-1000-8000-00805F9B34FB";
pub const REPORT_MARKER: u8 = 0x7A;
pub const REPORT_LEN: usize = 12;
pub const PLAYER_ONE: u8 = 1;

#[derive(Clone, Copy, Debug)]
pub enum Command {
    SelectPlayer,
    Poll,
    Listen,
}

impl Command {
    const fn byte(self) -> u8 {
        match self {
            Self::SelectPlayer => 0x43,
            Self::Poll => 0x41,
            Self::Listen => 0x44,
        }
    }
}

pub fn build_command(command: Command, player: u8) -> [u8; 5] {
    let mut bytes = [0x5A, 0x05, command.byte(), player, 0];
    bytes[4] = xor_checksum(&bytes[..4]);
    bytes
}

fn xor_checksum(bytes: &[u8]) -> u8 {
    bytes.iter().fold(0, |checksum, byte| checksum ^ byte)
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct MogaState {
    pub response_id: u8,
    pub player: u8,
    pub buttons: Buttons,
    pub dpad: Dpad,
    pub axes: [u8; 4],
}

#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Buttons {
    pub y: bool,
    pub b: bool,
    pub a: bool,
    pub x: bool,
    pub start: bool,
    pub select: bool,
    pub left_bumper: bool,
    pub right_bumper: bool,
}

#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Dpad {
    pub up: bool,
    pub down: bool,
    pub left: bool,
    pub right: bool,
    pub right_up: bool,
    pub right_down: bool,
    pub right_left: bool,
    pub right_right: bool,
}

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

pub fn parse_report(bytes: &[u8]) -> Result<MogaState, ProtocolError> {
    if bytes.len() != REPORT_LEN {
        return Err(ProtocolError::InvalidLength(
            bytes.get(1).copied().unwrap_or(bytes.len() as u8),
        ));
    }
    if bytes[0] != REPORT_MARKER {
        return Err(ProtocolError::InvalidMarker(bytes[0]));
    }
    if bytes[1] as usize != REPORT_LEN {
        return Err(ProtocolError::InvalidLength(bytes[1]));
    }
    if bytes[2] != 0x61 && bytes[2] != 0x64 {
        return Err(ProtocolError::InvalidResponseId(bytes[2]));
    }
    if bytes[3] != PLAYER_ONE {
        return Err(ProtocolError::InvalidPlayer(bytes[3]));
    }
    let expected_checksum = xor_checksum(&bytes[..REPORT_LEN - 1]);
    if bytes[REPORT_LEN - 1] != expected_checksum {
        return Err(ProtocolError::InvalidChecksum {
            expected: expected_checksum,
            actual: bytes[REPORT_LEN - 1],
        });
    }

    let buttons = bytes[4];
    let dpad = bytes[5];
    Ok(MogaState {
        response_id: bytes[2],
        player: bytes[3],
        buttons: Buttons {
            y: buttons & 0x01 != 0,
            b: buttons & 0x02 != 0,
            a: buttons & 0x04 != 0,
            x: buttons & 0x08 != 0,
            start: buttons & 0x10 != 0,
            select: buttons & 0x20 != 0,
            left_bumper: buttons & 0x40 != 0,
            right_bumper: buttons & 0x80 != 0,
        },
        dpad: Dpad {
            up: dpad & 0x01 != 0,
            down: dpad & 0x02 != 0,
            left: dpad & 0x04 != 0,
            right: dpad & 0x08 != 0,
            right_up: dpad & 0x10 != 0,
            right_down: dpad & 0x20 != 0,
            right_left: dpad & 0x40 != 0,
            right_right: dpad & 0x80 != 0,
        },
        axes: [bytes[6], bytes[7], bytes[8], bytes[9]],
    })
}

#[derive(Default)]
pub struct PacketStreamParser {
    pending: Vec<u8>,
}

impl PacketStreamParser {
    pub fn feed(&mut self, bytes: &[u8]) -> Vec<Result<MogaState, ProtocolError>> {
        self.pending.extend_from_slice(bytes);
        let mut reports = Vec::new();

        while self.pending.len() >= REPORT_LEN {
            let Some(marker_offset) = self.pending.iter().position(|byte| *byte == REPORT_MARKER)
            else {
                self.pending.clear();
                break;
            };

            if marker_offset > 0 {
                self.pending.drain(..marker_offset);
            }
            if self.pending.len() < REPORT_LEN {
                break;
            }

            reports.push(parse_report(&self.pending[..REPORT_LEN]));
            self.pending.drain(..REPORT_LEN);
        }

        reports
    }
}

#[cfg(test)]
pub(crate) fn test_packet(
    response_id: u8,
    player: u8,
    buttons: u8,
    dpad: u8,
    axes: [u8; 4],
) -> [u8; REPORT_LEN] {
    let mut packet = [
        REPORT_MARKER,
        REPORT_LEN as u8,
        response_id,
        player,
        buttons,
        dpad,
        axes[0],
        axes[1],
        axes[2],
        axes[3],
        0,
        0,
    ];
    packet[REPORT_LEN - 1] = xor_checksum(&packet[..REPORT_LEN - 1]);
    packet
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn decodes_buttons_dpad_and_axes() {
        let packet = test_packet(0x64, 1, 0xC5, 0x81, [0, 64, 128, 255]);
        let state = parse_report(&packet).expect("valid packet");
        assert!(state.buttons.y);
        assert!(state.buttons.a);
        assert!(state.buttons.left_bumper);
        assert!(state.buttons.right_bumper);
        assert!(state.dpad.up);
        assert!(state.dpad.right_right);
        assert_eq!(state.axes, [0, 64, 128, 255]);
    }

    #[test]
    fn buffers_fragmented_frames_and_discards_noise() {
        let packet = test_packet(0x61, 1, 0, 0, [1, 2, 3, 4]);
        let mut parser = PacketStreamParser::default();
        assert!(parser.feed(&[0x00, 0x05]).is_empty());
        assert!(parser.feed(&packet[..3]).is_empty());
        let output = parser.feed(&packet[3..]);
        assert_eq!(output.len(), 1);
        assert!(output[0].is_ok());
    }

    #[test]
    fn reports_invalid_checksum() {
        let mut packet = test_packet(0x61, 1, 0, 0, [0; 4]);
        packet[11] ^= 0xFF;
        assert!(matches!(
            parse_report(&packet),
            Err(ProtocolError::InvalidChecksum { .. })
        ));
    }
}
