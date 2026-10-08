import { useEffect, useState } from "react";
import { onControllerState, setStateStream } from "../lib/api";
import { usePageVisible } from "./usePageVisible";

/**
 * Live controller state. The stream is requested from the backend only while the component
 * using this hook is mounted and the app is visible, so nothing is emitted, serialised or
 * rendered while another tab or another app is in front.
 */
export function useControllerState() {
  const [state, setState] = useState(null);
  const visible = usePageVisible();

  useEffect(() => {
    if (!visible) return undefined;
    let active = true;
    let unlisten;

    onControllerState((next) => active && setState(next))
      .then((stop) => {
        if (active) unlisten = stop;
        else stop();
        return setStateStream(true);
      })
      .then((current) => active && current && setState(current))
      .catch((reason) => console.error("Could not start the controller stream:", reason));

    return () => {
      active = false;
      unlisten?.();
      setStateStream(false).catch((reason) => console.error(reason));
    };
  }, [visible]);

  return state;
}
