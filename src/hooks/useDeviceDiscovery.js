import { useCallback, useEffect, useState } from "react";
import {
  listPairedDevices,
  onDevicesDiscovered,
  requestBluetoothPermission,
  startDeviceScan,
  stopDeviceScan,
} from "../lib/api";
import { isAndroid } from "../lib/platform";

/** Paired and discovered controllers, kept live by the Android plugin's discovery events. */
export function useDeviceDiscovery({ onError }) {
  const [paired, setPaired] = useState([]);
  const [discovered, setDiscovered] = useState([]);
  const [scanning, setScanning] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!isAndroid) return undefined;
    let active = true;
    let subscription;

    onDevicesDiscovered((payload) => {
      if (!active) return;
      setDiscovered(payload.devices ?? []);
      setPaired(payload.bondedDevices ?? []);
      setScanning(Boolean(payload.scanning));
    })
      .then((listener) => {
        if (active) subscription = listener;
        else listener.unregister();
      })
      .catch((reason) => active && onError(String(reason)));

    return () => {
      active = false;
      subscription?.unregister().catch((reason) => console.error(reason));
      stopDeviceScan().catch((reason) => console.error(reason));
    };
  }, [onError]);

  const scan = useCallback(async () => {
    setBusy(true);
    onError("");
    try {
      await requestBluetoothPermission();
      setPaired(await listPairedDevices());
      setDiscovered([]);
      setScanning(true);
      await startDeviceScan();
    } catch (reason) {
      setScanning(false);
      onError(String(reason));
    } finally {
      setBusy(false);
    }
  }, [onError]);

  const stop = useCallback(async () => {
    try {
      await stopDeviceScan();
      setScanning(false);
    } catch (reason) {
      onError(String(reason));
    }
  }, [onError]);

  return { paired, discovered, scanning, busy, scan, stop };
}
