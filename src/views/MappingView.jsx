import Button from "../components/Button";
import HelperPanel from "../components/HelperPanel";
import MappingGrid from "../components/MappingGrid";
import Message from "../components/Message";
import ModeOption from "../components/ModeOption";
import Panel from "../components/Panel";
import SectionTitle from "../components/SectionTitle";
import { usePolling } from "../hooks/usePolling";
import { mappingControls, presets } from "../lib/keys";

const gamepadLayout = [
  ["Stick izquierdo / derecho", "Ejes analógicos (X, Y / Rx, Ry)"],
  ["A / B / X / Y", "BUTTON_A / B / X / Y"],
  ["L / R", "BUTTON_L1 / BUTTON_R1"],
  ["Start / Select", "BUTTON_START / BUTTON_SELECT"],
];

// The helper's state is re-read only while this tab is open AND the app is on screen (see
// usePolling). While waiting for the user to start it: every 3 s for the first minute, then
// every 10 s. Once it runs: every 15 s, just to notice if it stops.
const POLL_FAST_MS = 3000;
const POLL_FAST_ATTEMPTS = 20;
const POLL_SLOW_MS = 10000;
const POLL_RUNNING_MS = 15000;

const waitingDelay = (attempt) => (attempt < POLL_FAST_ATTEMPTS ? POLL_FAST_MS : POLL_SLOW_MS);
const runningDelay = () => POLL_RUNNING_MS;

const stickLayouts = [
  {
    value: "analogs",
    title: "Dos analógicos",
    text: "Stick izquierdo y derecho como ejes analógicos.",
  },
  {
    value: "leftDpad",
    title: "Analógico derecho + D-pad",
    text: "El stick izquierdo pasa a ser la cruceta; el derecho sigue analógico.",
  },
  {
    value: "rightDpad",
    title: "Analógico izquierdo + D-pad",
    text: "El stick derecho pasa a ser la cruceta; el izquierdo sigue analógico.",
  },
];

export default function MappingView({ output, keyMapping, openImeSettings, error }) {
  const settings = output.settings;
  const helperReady = Boolean(settings?.helperReachable);
  // The virtual gamepad only exists while the helper runs, whatever the saved preference.
  const mode = settings?.mode === "gamepad" && helperReady ? "gamepad" : "keyboard";

  usePolling(output.refresh, helperReady ? runningDelay : waitingDelay, {
    resetKey: helperReady,
  });

  return (
    <>
      <Message tone="info">
        El mapeo de botones está pensado solo para el <strong>MOGA Pocket</strong> (modo A). Otros
        modelos usan una disposición distinta y todavía no son compatibles.
      </Message>

      <HelperPanel reachable={helperReady} command={settings?.helperCommand} />

      <Panel title="Salida del mando" description="Cómo llegan los controles a otras aplicaciones.">
        <div className="mode-options" role="radiogroup" aria-label="Modo de salida">
          <ModeOption
            name="output-mode"
            value="gamepad"
            selected={mode === "gamepad"}
            onSelect={output.setMode}
            disabled={!helperReady}
            note={helperReady ? undefined : "Requiere el ayudante uinput activo (ver arriba)."}
            title="Gamepad virtual"
          >
            Android ve un mando real con sticks analógicos. Funciona en juegos y emuladores sin
            configurar teclas.
          </ModeOption>
          <ModeOption
            name="output-mode"
            value="keyboard"
            selected={mode === "keyboard"}
            onSelect={output.setMode}
            title="Teclado (IME)"
          >
            Envía teclas al campo de texto enfocado. No llega a juegos ni emuladores y no tiene
            sensibilidad analógica.
          </ModeOption>
        </div>

        {mode === "gamepad" && (
          <>
            <SectionTitle>Distribución de sticks</SectionTitle>
            <div className="mode-options" role="radiogroup" aria-label="Distribución de sticks">
              {stickLayouts.map((layout) => (
                <ModeOption
                  key={layout.value}
                  name="stick-layout"
                  value={layout.value}
                  selected={(settings?.stickLayout ?? "analogs") === layout.value}
                  onSelect={output.setStickLayout}
                  title={layout.title}
                >
                  {layout.text}
                </ModeOption>
              ))}
            </div>

            <SectionTitle>Correspondencia</SectionTitle>
            <table className="layout-table">
              <tbody>
                {gamepadLayout.map(([from, to]) => (
                  <tr key={from}>
                    <th scope="row">{from}</th>
                    <td>{to}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <p className="notice">
              El mando se detecta como un gamepad estándar. Si una app no reconoce algún botón,
              asígnalo en los ajustes de controles de esa app pulsándolo en el mando.
            </p>
          </>
        )}
        <Message tone="error">{error}</Message>
      </Panel>

      {mode === "keyboard" && (
        <Panel
          title="Mapeo de teclas"
          description="Tecla que enviará cada control mientras el teclado MOGA esté activo."
          actions={
            <Button variant="secondary" onClick={openImeSettings}>
              Configurar teclado Android
            </Button>
          }
        >
          <div className="button-row">
            {presets.map((preset) => (
              <Button variant="secondary" key={preset.id} onClick={() => keyMapping.applyPreset(preset)}>
                {preset.label}
              </Button>
            ))}
          </div>
          <MappingGrid
            controls={mappingControls}
            mapping={keyMapping.mapping}
            onChange={keyMapping.setKey}
          />
          <div className="mapping-actions">
            <Button onClick={keyMapping.save}>Guardar mapeo</Button>
            {keyMapping.saved && <Message tone="success">Mapeo guardado</Message>}
          </div>
          <p className="notice">
            Activa “MOGA Key Mapper” en los ajustes de teclado y selecciónalo como método de
            entrada. Android solo entrega teclas al campo de texto enfocado.
          </p>
        </Panel>
      )}
    </>
  );
}
