import { useCallback, useEffect, useState } from "react";
import {
  getOutputSettings,
  setOutputMode as setOutputModeCommand,
  setInputIsolated as setIsolatedCommand,
  setStickLayout as setStickLayoutCommand,
} from "../lib/api";
import { isAndroid } from "../lib/platform";

/** Output mode (virtual gamepad / keyboard), helper status and the test isolation. */
export function useOutputSettings({ onError }) {
  const [settings, setSettings] = useState(null);

  const refresh = useCallback(async () => {
    if (!isAndroid) return;
    try {
      setSettings(await getOutputSettings());
    } catch (reason) {
      onError(String(reason));
    }
  }, [onError]);

  useEffect(() => {
    refresh();
    // The backend switches isolation off when the app leaves the screen; resync on return.
    const onVisible = () => document.visibilityState === "visible" && refresh();
    document.addEventListener("visibilitychange", onVisible);
    return () => document.removeEventListener("visibilitychange", onVisible);
  }, [refresh]);

  const run = useCallback(
    async (command) => {
      onError("");
      try {
        await command();
        await refresh();
      } catch (reason) {
        onError(String(reason));
      }
    },
    [onError, refresh],
  );

  const setMode = useCallback((mode) => run(() => setOutputModeCommand(mode)), [run]);
  const setStickLayout = useCallback((layout) => run(() => setStickLayoutCommand(layout)), [run]);
  const setIsolated = useCallback((isolated) => run(() => setIsolatedCommand(isolated)), [run]);

  return { settings, refresh, setMode, setStickLayout, setIsolated };
}
