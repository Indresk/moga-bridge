use crate::constants::protocol::{
    button_bit, offset, stick_bit, PLAYER_ONE, REPORT_LEN, REPORT_MARKER, RESPONSE_LISTEN,
    RESPONSE_POLL,
};
use crate::schemas::{Buttons, MogaState, ProtocolError, Stick};
use crate::utils::checksum::xor_checksum;
use crate::utils::signed_axis::decode_axis;

/// Validates and decodes one 12-byte report.
pub fn parse_report(bytes: &[u8]) -> Result<MogaState, ProtocolError> {
    if bytes.len() != REPORT_LEN {
        return Err(ProtocolError::Length(
            bytes
                .get(offset::LENGTH)
                .copied()
                .unwrap_or(bytes.len() as u8),
        ));
    }
    if bytes[offset::MARKER] != REPORT_MARKER {
        return Err(ProtocolError::Marker(bytes[offset::MARKER]));
    }
    if bytes[offset::LENGTH] as usize != REPORT_LEN {
        return Err(ProtocolError::Length(bytes[offset::LENGTH]));
    }
    let response_id = bytes[offset::RESPONSE_ID];
    if response_id != RESPONSE_POLL && response_id != RESPONSE_LISTEN {
        return Err(ProtocolError::ResponseId(response_id));
    }
    if bytes[offset::PLAYER] != PLAYER_ONE {
        return Err(ProtocolError::Player(bytes[offset::PLAYER]));
    }
    let expected = xor_checksum(&bytes[..REPORT_LEN - 1]);
    if bytes[REPORT_LEN - 1] != expected {
        return Err(ProtocolError::Checksum {
            expected,
            actual: bytes[REPORT_LEN - 1],
        });
    }

    let buttons = bytes[offset::BUTTONS];
    let sticks = bytes[offset::STICK_BITS];
    let mut raw = [0_u8; REPORT_LEN];
    raw.copy_from_slice(bytes);

    Ok(MogaState {
        response_id,
        player: bytes[offset::PLAYER],
        buttons: Buttons {
            y: buttons & button_bit::Y != 0,
            b: buttons & button_bit::B != 0,
            a: buttons & button_bit::A != 0,
            x: buttons & button_bit::X != 0,
            start: buttons & button_bit::START != 0,
            select: buttons & button_bit::SELECT != 0,
            left_bumper: buttons & button_bit::LEFT_BUMPER != 0,
            right_bumper: buttons & button_bit::RIGHT_BUMPER != 0,
        },
        // The controller's Y axis is "up is positive"; the app exposes "down is positive".
        left_stick: Stick {
            x: decode_axis(bytes[offset::LEFT_X]),
            y: -decode_axis(bytes[offset::LEFT_Y]),
            up: sticks & stick_bit::LEFT_UP != 0,
            down: sticks & stick_bit::LEFT_DOWN != 0,
            left: sticks & stick_bit::LEFT_LEFT != 0,
            right: sticks & stick_bit::LEFT_RIGHT != 0,
        },
        right_stick: Stick {
            x: decode_axis(bytes[offset::RIGHT_X]),
            y: -decode_axis(bytes[offset::RIGHT_Y]),
            up: sticks & stick_bit::RIGHT_UP != 0,
            down: sticks & stick_bit::RIGHT_DOWN != 0,
            left: sticks & stick_bit::RIGHT_LEFT != 0,
            right: sticks & stick_bit::RIGHT_RIGHT != 0,
        },
        raw,
    })
}

/// Reassembles reports from arbitrarily fragmented reads, resynchronising on the marker.
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
mod tests {
    use super::super::fixtures::test_packet;
    use super::*;

    #[test]
    fn decodes_buttons_sticks_and_axes() {
        // Y, A, both bumpers; left-stick up, right-stick right; axes at known raw values.
        let packet = test_packet(0x64, 1, 0xC5, 0x81, [0, 64, 128, 255]);
        let state = parse_report(&packet).expect("valid packet");
        assert!(state.buttons.y && state.buttons.a);
        assert!(state.buttons.left_bumper && state.buttons.right_bumper);
        assert!(state.left_stick.up && state.right_stick.right);
        assert_eq!(state.left_stick.x, 0);
        assert_eq!(state.left_stick.y, -64); // raw 64 is "up", so down-positive is -64
        assert_eq!(state.right_stick.x, -127);
        assert_eq!(state.right_stick.y, 0);
        assert_eq!(state.raw, packet);
    }

    #[test]
    fn matches_the_hardware_capture_for_a_stick_pushed_down() {
        // Observed on a real Pocket: left stick pushed down => raw axes [0, 128, 0, 0].
        let packet = test_packet(0x64, 1, 0, 0x02, [0, 128, 0, 0]);
        let state = parse_report(&packet).expect("valid packet");
        assert!(state.left_stick.down);
        assert_eq!(state.left_stick.y, 127);
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
            Err(ProtocolError::Checksum { .. })
        ));
    }
}
