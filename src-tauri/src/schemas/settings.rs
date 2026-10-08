use serde::{Deserialize, Serialize};

use crate::constants::android::MAX_KEY_CODE;

/// Android key code assigned to each control in keyboard-output mode (0 = disabled).
#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct KeyMapping {
    pub button_a: i32,
    pub button_b: i32,
    pub button_x: i32,
    pub button_y: i32,
    pub start: i32,
    pub select: i32,
    pub left_bumper: i32,
    pub right_bumper: i32,
    pub left_stick_up: i32,
    pub left_stick_down: i32,
    pub left_stick_left: i32,
    pub left_stick_right: i32,
    pub right_stick_up: i32,
    pub right_stick_down: i32,
    pub right_stick_left: i32,
    pub right_stick_right: i32,
}

impl Default for KeyMapping {
    /// PPSSPP's default keyboard layout (Android key codes): Cross Z, Circle X, Square A,
    /// Triangle S, L Q, R W, Start Space, Select V, arrows for the D-pad and I/J/K/L for the
    /// analog stick. MOGA A/B/X/Y are in the Cross/Circle/Square/Triangle positions, the left
    /// stick drives the arrows and the right stick drives I/J/K/L.
    fn default() -> Self {
        Self {
            button_a: 54,
            button_b: 52,
            button_x: 29,
            button_y: 47,
            start: 62,
            select: 50,
            left_bumper: 45,
            right_bumper: 51,
            left_stick_up: 19,
            left_stick_down: 20,
            left_stick_left: 21,
            left_stick_right: 22,
            right_stick_up: 37,
            right_stick_down: 39,
            right_stick_left: 38,
            right_stick_right: 40,
        }
    }
}

impl KeyMapping {
    pub fn validate(&self) -> Result<(), String> {
        let key_codes = [
            self.button_a,
            self.button_b,
            self.button_x,
            self.button_y,
            self.start,
            self.select,
            self.left_bumper,
            self.right_bumper,
            self.left_stick_up,
            self.left_stick_down,
            self.left_stick_left,
            self.left_stick_right,
            self.right_stick_up,
            self.right_stick_down,
            self.right_stick_left,
            self.right_stick_right,
        ];

        if key_codes
            .iter()
            .any(|key_code| !(0..=MAX_KEY_CODE).contains(key_code))
        {
            return Err(format!(
                "Android key codes must be between 0 (disabled) and {MAX_KEY_CODE}."
            ));
        }
        Ok(())
    }
}

/// How controller input leaves the app.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum OutputMode {
    /// Virtual gamepad through Android's `uinput` tool (needs the adb-started helper).
    Gamepad,
    /// Key events through the MOGA input method (only reaches focused text editors).
    Keyboard,
}

/// How the two physical sticks are presented by the virtual gamepad.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum StickLayout {
    /// Left and right analog sticks (default).
    Analogs,
    /// Left stick becomes a D-pad; the right stick stays analog.
    LeftDpad,
    /// Right stick becomes a D-pad; the left stick stays analog.
    RightDpad,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct OutputSettings {
    pub mode: OutputMode,
    pub stick_layout: StickLayout,
    pub helper_reachable: bool,
    pub helper_port: u16,
    pub helper_error: Option<String>,
    /// The command a user runs from a PC to start the helper, when it could be prepared.
    pub helper_command: Option<String>,
    /// The virtual gamepad is silenced so the controller can be tested inside the app
    /// without reaching Android. Never persisted: it resets when the app leaves the screen.
    pub isolated: bool,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn default_mapping_is_valid() {
        assert!(KeyMapping::default().validate().is_ok());
    }

    #[test]
    fn rejects_out_of_range_android_codes() {
        let mut mapping = KeyMapping::default();
        mapping.button_a = MAX_KEY_CODE + 1;
        assert!(mapping.validate().is_err());
        mapping.button_a = -1;
        assert!(mapping.validate().is_err());
    }
}
