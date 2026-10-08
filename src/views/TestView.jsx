import { useEffect } from "react";
import Button from "../components/Button";
import GamepadView from "../components/GamepadView";
import Panel from "../components/Panel";
import Switch from "../components/Switch";

const hex = (bytes) =>
  bytes.map((byte) => byte.toString(16).padStart(2, "0").toUpperCase()).join(" ");

export default function TestView({ controller, output, onOpenConnection }) {
  const { status, state } = controller;
  const connected = status.state === "connected";
  const { settings, refresh, setIsolated } = output;
  // The picture follows what the virtual gamepad is actually presenting to Android.
  const gamepadActive = settings?.mode === "gamepad" && Boolean(settings?.helperReachable);
  const layout = gamepadActive ? (settings?.stickLayout ?? "analogs") : "analogs";

  useEffect(() => {
    refresh();
  }, [refresh]);

  // Isolation is only meant for this screen: always release it when leaving.
  useEffect(() => () => setIsolated(false), [setIsolated]);

  return (
    <Panel
      title="Prueba del mando"
      description="Mueve los sticks y pulsa botones: todo se muestra en vivo."
      actions={
        !connected && (
          <Button variant="secondary" onClick={onOpenConnection}>
            Ir a Conexión
          </Button>
        )
      }
    >
      {!connected && <p className="empty-state">Conecta un mando para ver su estado en vivo.</p>}
      {gamepadActive && (
        <Switch
          checked={Boolean(output.settings?.isolated)}
          onChange={setIsolated}
          label="Aislar el mando mientras pruebo"
          hint="No envía nada al sistema, así que el mando no mueve esta app. Se desactiva solo al salir de esta pantalla o pasar a otra app."
        />
      )}
      <GamepadView state={state} active={connected} layout={layout} />

      <details>
        <summary>Datos en bruto</summary>
        {state ? (
          <>
            <p className="raw-line">
              Reporte: <code>{hex(state.raw)}</code>
            </p>
            <pre>{JSON.stringify({ ...state, raw: undefined }, null, 2)}</pre>
          </>
        ) : (
          <p className="empty-state">Aún no se ha recibido ningún reporte.</p>
        )}
      </details>
    </Panel>
  );
}
