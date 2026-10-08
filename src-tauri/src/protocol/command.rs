use crate::constants::protocol::{
    COMMAND_LEN, COMMAND_LISTEN, COMMAND_MARKER, COMMAND_POLL, COMMAND_SELECT_PLAYER,
};
use crate::utils::checksum::xor_checksum;

#[derive(Clone, Copy, Debug)]
pub enum Command {
    SelectPlayer,
    Poll,
    Listen,
}

impl Command {
    const fn byte(self) -> u8 {
        match self {
            Self::SelectPlayer => COMMAND_SELECT_PLAYER,
            Self::Poll => COMMAND_POLL,
            Self::Listen => COMMAND_LISTEN,
        }
    }
}

/// `[0x5A, 0x05, command, player, XOR of the previous four bytes]`.
pub fn build_command(command: Command, player: u8) -> [u8; COMMAND_LEN] {
    let mut bytes = [COMMAND_MARKER, COMMAND_LEN as u8, command.byte(), player, 0];
    bytes[COMMAND_LEN - 1] = xor_checksum(&bytes[..COMMAND_LEN - 1]);
    bytes
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn player_one_commands_match_the_legacy_app() {
        assert_eq!(
            build_command(Command::SelectPlayer, 1),
            [0x5A, 0x05, 0x43, 0x01, 0x1D]
        );
        assert_eq!(
            build_command(Command::Poll, 1),
            [0x5A, 0x05, 0x41, 0x01, 0x1F]
        );
        assert_eq!(
            build_command(Command::Listen, 1),
            [0x5A, 0x05, 0x44, 0x01, 0x1A]
        );
    }
}
