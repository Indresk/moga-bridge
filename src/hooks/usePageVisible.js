import { useSyncExternalStore } from "react";

function subscribe(callback) {
  document.addEventListener("visibilitychange", callback);
  return () => document.removeEventListener("visibilitychange", callback);
}

/** True while the app is on screen (false in the background or with the screen off). */
export function usePageVisible() {
  return useSyncExternalStore(
    subscribe,
    () => document.visibilityState === "visible",
    () => true,
  );
}
