import { useCallback, useEffect, useState } from "react";
import { getKeyMapping, saveKeyMapping } from "../lib/api";
import { defaultMapping } from "../lib/keys";
import { isAndroid } from "../lib/platform";

/** Keyboard-output mapping: editing is local until saved. */
export function useKeyMapping({ onError }) {
  const [mapping, setMapping] = useState(defaultMapping);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (!isAndroid) return;
    getKeyMapping()
      .then((stored) => setMapping({ ...defaultMapping, ...stored }))
      .catch((reason) => onError(String(reason)));
  }, [onError]);

  const persist = useCallback(
    async (next) => {
      onError("");
      setSaved(false);
      try {
        await saveKeyMapping(next);
        setSaved(true);
      } catch (reason) {
        onError(String(reason));
      }
    },
    [onError],
  );

  // The value is read by the caller before any state update: React runs state updaters
  // later, when `event.currentTarget` is already null.
  const setKey = useCallback((control, keyCode) => {
    setMapping((current) => ({ ...current, [control]: keyCode }));
    setSaved(false);
  }, []);

  const applyPreset = useCallback(
    (preset) => {
      setMapping(preset.mapping);
      persist(preset.mapping);
    },
    [persist],
  );

  const save = useCallback(() => persist(mapping), [persist, mapping]);

  return { mapping, saved, setKey, applyPreset, save };
}
