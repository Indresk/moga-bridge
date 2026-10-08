import { useCallback, useEffect, useState } from "react";
import {
  connectController,
  disconnectController,
  getConnectionStatus,
  onControllerError,
  onControllerState,
  onStatus,
} from "../lib/api";

const busyStates = ["connecting", "connected", "disconnecting"];

/**
 * Connection lifecycle and the live controller state.
 * `onError(message)` receives transport and protocol errors.
 */
export function useController({ onError }) {
  const [status, setStatus] = useState({ state: "idle" });
  const [state, setState] = useState(null);
  const [pairingDeviceId, setPairingDeviceId] = useState(null);
  const [bonding, setBonding] = useState(false);

  useEffect(() => {
    let active = true;
    const unlisteners = [];

    async function subscribe() {
      try {
        const subscriptions = await Promise.all([
          onStatus((payload) => {
            setStatus(payload);
            onError(payload.message ?? "");
            if (payload.state !== "connecting") {
              setPairingDeviceId(null);
              setBonding(false);
            }
          }),
          onControllerState(setState),
          onControllerError(onError),
        ]);
        if (active) unlisteners.push(...subscriptions);
        else subscriptions.forEach((unlisten) => unlisten());

        const current = await getConnectionStatus();
        if (active) setStatus(current);
      } catch (reason) {
        if (active) onError(String(reason));
      }
    }

    subscribe();
    return () => {
      active = false;
      unlisteners.forEach((unlisten) => unlisten());
    };
  }, [onError]);

  const connect = useCallback(
    async (device) => {
      onError("");
      setPairingDeviceId(device.id);
      setBonding(!device.bonded);
      try {
        await connectController(device.id);
      } catch (reason) {
        onError(String(reason));
        setPairingDeviceId(null);
        setBonding(false);
      }
    },
    [onError],
  );

  const disconnect = useCallback(async () => {
    onError("");
    try {
      await disconnectController();
      setPairingDeviceId(null);
    } catch (reason) {
      onError(String(reason));
    }
  }, [onError]);

  return {
    status,
    state,
    pairingDeviceId,
    bonding,
    locked: busyStates.includes(status.state),
    connect,
    disconnect,
  };
}
