/// XOR of all bytes; the controller protocol's frame checksum.
pub fn xor_checksum(bytes: &[u8]) -> u8 {
    bytes.iter().fold(0, |checksum, byte| checksum ^ byte)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn xors_every_byte() {
        assert_eq!(xor_checksum(&[0x5A, 0x05, 0x43, 0x01]), 0x1D);
        assert_eq!(xor_checksum(&[]), 0);
    }
}
