//! In-memory driver that replays a recorded byte stream; used by tests.

use std::{
    io::{Cursor, Read},
    sync::Arc,
};

use super::{InputMapper, MogaConnection, MogaDriver};
use crate::constants::protocol::COMMAND_LEN;
use crate::schemas::{DeviceInfo, KeyMapping};

pub struct MockDriver {
    packet_stream: Arc<Vec<u8>>,
}

impl MockDriver {
    pub fn new(packet_stream: Vec<u8>) -> Self {
        Self {
            packet_stream: Arc::new(packet_stream),
        }
    }
}

impl MogaDriver for MockDriver {
    fn scan(&self) -> Result<Vec<DeviceInfo>, String> {
        Ok(vec![DeviceInfo {
            id: "mock-moga-pocket".into(),
            name: "MOGA Pocket (mock)".into(),
            bonded: true,
        }])
    }

    fn scan_unpaired(&self) -> Result<(), String> {
        Ok(())
    }

    fn stop_scan(&self) -> Result<(), String> {
        Ok(())
    }

    fn connect(&self, device_id: &str) -> Result<Box<dyn MogaConnection>, String> {
        if device_id != "mock-moga-pocket" {
            return Err(format!("Unknown mock device: {device_id}"));
        }
        Ok(Box::new(MockConnection {
            incoming: Cursor::new(self.packet_stream.as_ref().clone()),
            sent_commands: Vec::new(),
        }))
    }

    fn disconnect(&self) -> Result<(), String> {
        Ok(())
    }
}

impl InputMapper for MockDriver {
    fn get_mapping(&self) -> Result<KeyMapping, String> {
        Ok(KeyMapping::default())
    }

    fn set_mapping(&self, _mapping: &KeyMapping) -> Result<(), String> {
        Ok(())
    }

    fn request_bluetooth_permission(&self) -> Result<(), String> {
        Ok(())
    }

    fn open_ime_settings(&self) -> Result<(), String> {
        Ok(())
    }
}

pub struct MockConnection {
    incoming: Cursor<Vec<u8>>,
    pub sent_commands: Vec<[u8; COMMAND_LEN]>,
}

impl Read for MockConnection {
    fn read(&mut self, buffer: &mut [u8]) -> std::io::Result<usize> {
        self.incoming.read(buffer)
    }
}

impl MogaConnection for MockConnection {
    fn send_command(&mut self, command: [u8; COMMAND_LEN]) -> Result<(), String> {
        self.sent_commands.push(command);
        Ok(())
    }
}
