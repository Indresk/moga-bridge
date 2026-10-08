use std::{
    io::{self, Read},
    sync::Arc,
};

use serde::{Deserialize, Serialize};
use tauri::{
    plugin::{PluginApi, PluginHandle, TauriPlugin},
    AppHandle, Manager, Runtime,
};

use crate::{
    constants::{
        android::{PLUGIN_CLASS, PLUGIN_ID},
        protocol::COMMAND_LEN,
    },
    drivers::{InputMapper, MogaConnection, MogaDriver, RfcommStrategy, RFCOMM_STRATEGIES},
    schemas::{DeviceInfo, KeyMapping, MogaState, OutputMode, OutputSettings, StickLayout},
    services::AppState,
};

pub fn init<R: Runtime>() -> TauriPlugin<R> {
    tauri::plugin::Builder::<R>::new("moga-android")
        .setup(|app, api| initialize_android_driver(app, api))
        .build()
}

fn initialize_android_driver<R: Runtime>(
    app: &AppHandle<R>,
    api: PluginApi<R, ()>,
) -> Result<(), Box<dyn std::error::Error>> {
    let plugin = api.register_android_plugin(PLUGIN_ID, PLUGIN_CLASS)?;
    app.state::<AppState>()
        .set_driver(Arc::new(AndroidDriver::new(plugin)))
        .map_err(io::Error::other)?;
    Ok(())
}

#[derive(Clone)]
struct AndroidDriver<R: Runtime> {
    plugin: PluginHandle<R>,
}

impl<R: Runtime> AndroidDriver<R> {
    fn new(plugin: PluginHandle<R>) -> Self {
        Self { plugin }
    }

    fn invoke<T: for<'de> Deserialize<'de>>(
        &self,
        command: &str,
        payload: impl Serialize,
    ) -> Result<T, String> {
        self.plugin
            .run_mobile_plugin(command, payload)
            .map_err(|error| format!("Android {command} failed: {error}"))
    }
}

impl<R: Runtime> MogaDriver for AndroidDriver<R> {
    fn scan(&self) -> Result<Vec<DeviceInfo>, String> {
        self.invoke("scan", ())
    }

    fn scan_unpaired(&self) -> Result<(), String> {
        self.invoke("scanUnpairedDevices", ())
    }

    fn stop_scan(&self) -> Result<(), String> {
        self.invoke("stopDeviceScan", ())
    }

    fn connect(&self, device_id: &str) -> Result<Box<dyn MogaConnection>, String> {
        self.invoke::<()>(
            "connect",
            ConnectPayload {
                device_id: device_id.to_string(),
                strategies: &RFCOMM_STRATEGIES,
            },
        )?;
        Ok(Box::new(AndroidConnection {
            plugin: self.plugin.clone(),
            closed: false,
        }))
    }

    fn disconnect(&self) -> Result<(), String> {
        self.invoke("disconnect", ())
    }
}

impl<R: Runtime> InputMapper for AndroidDriver<R> {
    fn get_mapping(&self) -> Result<KeyMapping, String> {
        self.invoke("getMapping", ())
    }

    fn request_bluetooth_permission(&self) -> Result<(), String> {
        self.invoke("requestBluetoothPermission", ())
    }

    fn set_mapping(&self, mapping: &KeyMapping) -> Result<(), String> {
        mapping.validate()?;
        self.invoke::<()>("setMapping", MappingPayload { mapping })
    }

    fn open_ime_settings(&self) -> Result<(), String> {
        self.invoke::<()>("openImeSettings", ())
    }

    fn open_battery_settings(&self) -> Result<(), String> {
        self.invoke::<()>("openBatterySettings", ())
    }

    fn get_output_settings(&self) -> Result<OutputSettings, String> {
        self.invoke("getOutputSettings", ())
    }

    fn set_output_mode(&self, mode: OutputMode) -> Result<(), String> {
        self.invoke::<()>("setOutputMode", OutputModePayload { mode })
    }

    fn set_stick_layout(&self, layout: StickLayout) -> Result<(), String> {
        self.invoke::<()>("setStickLayout", StickLayoutPayload { layout })
    }

    fn set_input_isolated(&self, isolated: bool) -> Result<(), String> {
        self.invoke::<()>("setInputIsolated", IsolatedPayload { isolated })
    }
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ConnectPayload {
    device_id: String,
    strategies: &'static [RfcommStrategy],
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct StickLayoutPayload {
    layout: StickLayout,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct IsolatedPayload {
    isolated: bool,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct OutputModePayload {
    mode: OutputMode,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct MappingPayload<'a> {
    mapping: &'a KeyMapping,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ReadResponse {
    bytes: Vec<u8>,
    connected: bool,
    error: Option<String>,
}

struct AndroidConnection<R: Runtime> {
    plugin: PluginHandle<R>,
    closed: bool,
}

impl<R: Runtime> Read for AndroidConnection<R> {
    fn read(&mut self, buffer: &mut [u8]) -> io::Result<usize> {
        if buffer.is_empty() {
            return Ok(0);
        }
        if self.closed {
            return Ok(0);
        }

        let response: ReadResponse = self
            .plugin
            .run_mobile_plugin("read", ())
            .map_err(|error| io::Error::other(format!("Android RFCOMM read failed: {error}")))?;

        if let Some(error) = response.error {
            self.closed = true;
            return Err(io::Error::other(error));
        }
        if !response.connected {
            self.closed = true;
            return Ok(0);
        }

        let bytes_read = buffer.len().min(response.bytes.len());
        buffer[..bytes_read].copy_from_slice(&response.bytes[..bytes_read]);
        Ok(bytes_read)
    }
}

impl<R: Runtime> MogaConnection for AndroidConnection<R> {
    fn send_command(&mut self, command: [u8; COMMAND_LEN]) -> Result<(), String> {
        self.plugin
            .run_mobile_plugin::<()>(
                "write",
                WritePayload {
                    bytes: command.to_vec(),
                },
            )
            .map_err(|error| format!("Android RFCOMM write failed: {error}"))
    }

    fn dispatch_input_state(&mut self, state: &MogaState) -> Result<(), String> {
        self.plugin
            .run_mobile_plugin::<()>("dispatchState", StatePayload { state })
            .map_err(|error| format!("Could not dispatch MOGA state to the Android IME: {error}"))
    }
}

impl<R: Runtime> Drop for AndroidConnection<R> {
    fn drop(&mut self) {
        if let Err(error) = self.plugin.run_mobile_plugin::<()>("disconnect", ()) {
            eprintln!("Could not close Android MOGA connection: {error}");
        }
    }
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct WritePayload {
    bytes: Vec<u8>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct StatePayload<'a> {
    state: &'a MogaState,
}
