use serde::Serialize;

use crate::constants::protocol::REPORT_LEN;

/// One decoded controller report.
#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct MogaState {
    pub response_id: u8,
    pub player: u8,
    pub buttons: Buttons,
    pub left_stick: Stick,
    pub right_stick: Stick,
    /// The undecoded report, kept for diagnostics and protocol research.
    pub raw: [u8; REPORT_LEN],
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

/// An analog stick: the analog position plus the controller's digitised direction bits.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Stick {
    /// `-127..=127`, positive to the right. Centre is `0`.
    pub x: i16,
    /// `-127..=127`, positive downwards (screen / evdev convention). Centre is `0`.
    pub y: i16,
    pub up: bool,
    pub down: bool,
    pub left: bool,
    pub right: bool,
}
