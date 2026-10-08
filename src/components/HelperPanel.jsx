import { useState } from "react";
import Button from "./Button";
import { openBatterySettings } from "../lib/api";
import CopyField from "./CopyField";
import Panel from "./Panel";
import StatusTag from "./StatusTag";

function WhatIsIt() {
  return (
    <p className="helper-text">
      Android no deja que una app normal cree un mando virtual. El ayudante uinput es un
      pequeño proceso que se inicia desde un PC con adb (sin root) y ejecuta la herramienta{" "}
      <code>uinput</code> del propio Android, que solo escucha dentro de este móvil. MOGA Bridge
      le envía los botones y sticks para que juegos y emuladores vean un mando real.
    </p>
  );
}

function Steps({ command }) {
  return (
    <ol className="steps">
      <li>
        En el PC, instala las <strong>platform-tools de Android</strong> (incluyen <code>adb</code>
        ): se descargan gratis desde developer.android.com/tools/releases/platform-tools.
      </li>
      <li>
        En el móvil activa las <strong>opciones de desarrollador</strong> (toca 7 veces el número
        de compilación) y la <strong>Depuración USB</strong>.
      </li>
      <li>Conecta el móvil al PC por USB y acepta el aviso de autorización.</li>
      <li>
        En una terminal del PC ejecuta:
        {command ? (
          <CopyField value={command} />
        ) : (
          <p className="helper-text">Abre la app una vez más para generar el comando.</p>
        )}
      </li>
    </ol>
  );
}

/**
 * The adb-started helper that makes the virtual gamepad possible. People who already run it
 * only need a confirmation; everyone else gets what it is, the steps, and live status.
 */
export default function HelperPanel({ reachable, command }) {
  const [showInfo, setShowInfo] = useState(false);

  if (reachable) {
    return (
      <Panel title="Ayudante uinput" actions={<StatusTag tone="ok">Activo</StatusTag>}>
        <p className="helper-text">
          Todo en orden: ya se puede usar el gamepad virtual con sticks analógicos en cualquier
          juego o emulador.
        </p>
        <div className="button-row">
          <Button variant="secondary" onClick={() => setShowInfo((value) => !value)}>
            {showInfo ? "Ocultar información" : "Ver información"}
          </Button>
          <Button variant="secondary" onClick={() => openBatterySettings().catch(console.error)}>
            Ajustes de batería
          </Button>
        </div>
        <p className="helper-text">
          ¿El mando deja de responder al salir de la app? Algunos móviles limitan las apps en
          segundo plano: deja MOGA Bridge en «Sin restricciones» en el ahorro de batería.
        </p>
        {showInfo && (
          <>
            <WhatIsIt />
            <h3 className="section-title">Si necesitas iniciarlo de nuevo</h3>
            <p className="helper-text">Hay que repetirlo cada vez que se reinicia el móvil.</p>
            <Steps command={command} />
          </>
        )}
      </Panel>
    );
  }

  return (
    <Panel title="Ayudante uinput" actions={<StatusTag tone="warning">No iniciado</StatusTag>}>
      <WhatIsIt />
      <p className="helper-text">
        Sin él no se puede usar el gamepad virtual; solo queda el modo teclado, que no funciona
        en juegos.
      </p>
      <h3 className="section-title">Cómo iniciarlo</h3>
      <Steps command={command} />
      <p className="helper-wait" role="status">
        <span className="pulse" aria-hidden="true" />
        Esta pantalla se actualiza sola cuando el ayudante responda. Hay que repetir estos pasos
        tras cada reinicio del móvil.
      </p>
    </Panel>
  );
}
