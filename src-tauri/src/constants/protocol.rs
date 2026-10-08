//! MOGA Mode A wire protocol numbers (see `docs/architecture/protocol.md`; sources: the decompiled `BluetoothThread` and `moga-uinput`).

/// First byte of every message we send to the controller.
pub const COMMAND_MARKER: u8 = 0x5A;
/// Outgoing messages are always `[marker, len, command, player, xor]`.
pub const COMMAND_LEN: usize = 5;

/// First byte of every report the controller sends.
pub const REPORT_MARKER: u8 = 0x7A;
/// Incoming reports are always 12 bytes, including the trailing XOR byte.
pub const REPORT_LEN: usize = 12;

pub const PLAYER_ONE: u8 = 1;

pub const COMMAND_SELECT_PLAYER: u8 = 0x43;
pub const COMMAND_POLL: u8 = 0x41;
pub const COMMAND_LISTEN: u8 = 0x44;

/// Response IDs of poll (`0x41`) and listen (`0x44`) replies.
pub const RESPONSE_POLL: u8 = 0x61;
pub const RESPONSE_LISTEN: u8 = 0x64;

/// Absolute offsets inside a report.
pub mod offset {
    pub const MARKER: usize = 0;
    pub const LENGTH: usize = 1;
    pub const RESPONSE_ID: usize = 2;
    pub const PLAYER: usize = 3;
    pub const BUTTONS: usize = 4;
    pub const STICK_BITS: usize = 5;
    pub const LEFT_X: usize = 6;
    pub const LEFT_Y: usize = 7;
    pub const RIGHT_X: usize = 8;
    pub const RIGHT_Y: usize = 9;
}

/// Bit masks of the button byte (offset 4).
pub mod button_bit {
    pub const Y: u8 = 0x01;
    pub const B: u8 = 0x02;
    pub const A: u8 = 0x04;
    pub const X: u8 = 0x08;
    pub const START: u8 = 0x10;
    pub const SELECT: u8 = 0x20;
    pub const LEFT_BUMPER: u8 = 0x40;
    pub const RIGHT_BUMPER: u8 = 0x80;
}

/// Bit masks of the digitised-stick byte (offset 5). The Pocket reports each analog stick as
/// four direction bits (the legacy app calls them "Left*"/"Right*" pads) plus analog axes.
pub mod stick_bit {
    pub const LEFT_UP: u8 = 0x01;
    pub const LEFT_DOWN: u8 = 0x02;
    pub const LEFT_LEFT: u8 = 0x04;
    pub const LEFT_RIGHT: u8 = 0x08;
    pub const RIGHT_UP: u8 = 0x10;
    pub const RIGHT_DOWN: u8 = 0x20;
    pub const RIGHT_LEFT: u8 = 0x40;
    pub const RIGHT_RIGHT: u8 = 0x80;
}
