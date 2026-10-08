//! Test-only helpers to build valid controller reports.

use crate::constants::protocol::{REPORT_LEN, REPORT_MARKER};
use crate::utils::checksum::xor_checksum;

pub fn test_packet(
    response_id: u8,
    player: u8,
    buttons: u8,
    sticks: u8,
    axes: [u8; 4],
) -> [u8; REPORT_LEN] {
    let mut packet = [
        REPORT_MARKER,
        REPORT_LEN as u8,
        response_id,
        player,
        buttons,
        sticks,
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
