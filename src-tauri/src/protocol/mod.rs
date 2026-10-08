//! Transport-independent MOGA Mode A protocol: building commands and decoding reports.

mod command;
mod parser;

pub use command::{build_command, Command};
pub use parser::PacketStreamParser;

#[cfg(test)]
pub(crate) mod fixtures;
