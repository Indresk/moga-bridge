/// Converts a raw MOGA axis byte to a signed value in `-127..=127`.
///
/// The controller sends a signed byte in an unusual wrap-around form: `0..=127` are the
/// positive half and `128..=255` map to `-127..=0` (the `moga-uinput` reference does
/// `value - 255`). The sign convention of each axis is applied by the caller.
pub fn decode_axis(raw: u8) -> i16 {
    if raw >= 128 {
        i16::from(raw) - 255
    } else {
        i16::from(raw)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn maps_both_halves() {
        assert_eq!(decode_axis(0), 0);
        assert_eq!(decode_axis(127), 127);
        assert_eq!(decode_axis(128), -127);
        assert_eq!(decode_axis(192), -63);
        assert_eq!(decode_axis(255), 0);
    }
}
