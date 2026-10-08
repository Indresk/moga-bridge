import { useEffect, useRef } from "react";

function isVisible() {
  return document.visibilityState === "visible";
}

/**
 * Calls `callback` repeatedly while `enabled` **and the app is visible**.
 *
 * - `delayFor(attempt)` returns the wait before poll number `attempt` (0, 1, 2...), so the
 *   caller can back off. The count restarts whenever `resetKey` changes or the app returns
 *   to the foreground.
 * - Nothing runs while the page is hidden (another app, screen off) or after unmount.
 * - On return to the foreground it polls once immediately.
 */
export function usePolling(callback, delayFor, { enabled = true, resetKey } = {}) {
  const savedCallback = useRef(callback);
  const savedDelay = useRef(delayFor);
  useEffect(() => {
    savedCallback.current = callback;
    savedDelay.current = delayFor;
  });

  useEffect(() => {
    if (!enabled) return undefined;
    let timer;
    let attempt = 0;

    const stop = () => {
      clearTimeout(timer);
      timer = undefined;
    };
    const schedule = () => {
      stop();
      if (!isVisible()) return;
      timer = setTimeout(() => {
        savedCallback.current();
        attempt += 1;
        schedule();
      }, savedDelay.current(attempt));
    };
    const onVisibilityChange = () => {
      if (isVisible()) {
        attempt = 0;
        savedCallback.current();
        schedule();
      } else {
        stop();
      }
    };

    document.addEventListener("visibilitychange", onVisibilityChange);
    schedule();
    return () => {
      document.removeEventListener("visibilitychange", onVisibilityChange);
      stop();
    };
  }, [enabled, resetKey]);
}
