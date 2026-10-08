// Single entry point to the Rust backend: one function per Tauri command, plus event
// subscriptions. Components and hooks never call `invoke` directly.
import { addPluginListener, invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import {
  EVENT_ERROR,
  EVENT_STATE,
  EVENT_STATUS,
  PLUGIN_EVENT_DISCOVERED,
  PLUGIN_NAME,
} from "./events";

export const requestBluetoothPermission = () => invoke("request_bluetooth_permission");
export const listPairedDevices = () => invoke("scan_moga");
export const startDeviceScan = () => invoke("scan_unpaired_devices");
export const stopDeviceScan = () => invoke("stop_device_scan");

export const connectController = (deviceId) => invoke("connect_moga", { deviceId });
export const disconnectController = () => invoke("disconnect_moga");
export const getConnectionStatus = () => invoke("connection_status");

export const getKeyMapping = () => invoke("get_key_mapping");
export const saveKeyMapping = (mapping) => invoke("set_key_mapping", { mapping });
export const openImeSettings = () => invoke("open_ime_settings");

export const getOutputSettings = () => invoke("get_output_settings");
export const setOutputMode = (mode) => invoke("set_output_mode", { mode });
export const setStickLayout = (layout) => invoke("set_stick_layout", { layout });
export const setInputIsolated = (isolated) => invoke("set_input_isolated", { isolated });

/** Each subscription returns a promise of an `unlisten` function. */
export const onStatus = (handler) => listen(EVENT_STATUS, (event) => handler(event.payload));
export const onControllerState = (handler) =>
  listen(EVENT_STATE, (event) => handler(event.payload));
export const onControllerError = (handler) =>
  listen(EVENT_ERROR, (event) => handler(String(event.payload)));

/** Android only. Resolves to an object with `unregister()`. */
export const onDevicesDiscovered = (handler) =>
  addPluginListener(PLUGIN_NAME, PLUGIN_EVENT_DISCOVERED, handler);
