import { useEffect, useState } from "react";
import { addPluginListener, invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import "./App.css";

const keyOptions = [
  { value: 0, label: "Desactivado" },
  { value: 19, label: "Flecha arriba" },
  { value: 20, label: "Flecha abajo" },
  { value: 21, label: "Flecha izquierda" },
  { value: 22, label: "Flecha derecha" },
  { value: 62, label: "Espacio" },
  { value: 66, label: "Enter" },
  { value: 111, label: "Escape" },
  { value: 61, label: "Tab" },
  { value: 29, label: "A" },
  { value: 30, label: "B" },
  { value: 31, label: "C" },
  { value: 32, label: "D" },
  { value: 33, label: "E" },
  { value: 44, label: "J" },
  { value: 45, label: "K" },
  { value: 46, label: "L" },
  { value: 54, label: "Z" },
];

const mappingControls = [
  ["up", "Cruceta arriba"],
  ["down", "Cruceta abajo"],
  ["left", "Cruceta izquierda"],
  ["right", "Cruceta derecha"],
  ["buttonA", "Botón A"],
  ["buttonB", "Botón B"],
  ["buttonX", "Botón X"],
  ["buttonY", "Botón Y"],
  ["start", "Start"],
  ["select", "Select"],
  ["leftBumper", "Bumper izquierdo"],
  ["rightBumper", "Bumper derecho"],
  ["rightUp", "Cruceta secundaria arriba"],
  ["rightDown", "Cruceta secundaria abajo"],
  ["rightLeft", "Cruceta secundaria izquierda"],
  ["rightRight", "Cruceta secundaria derecha"],
];

const defaultMapping = {
  buttonA: 62,
  buttonB: 0,
  buttonX: 0,
  buttonY: 0,
  start: 66,
  select: 0,
  leftBumper: 0,
  rightBumper: 0,
  up: 19,
  down: 20,
  left: 21,
  right: 22,
  rightUp: 0,
  rightDown: 0,
  rightLeft: 0,
  rightRight: 0,
};

function App() {
  const [devices, setDevices] = useState([]);
  const [unpairedDevices, setUnpairedDevices] = useState([]);
  const [scanning, setScanning] = useState(false);
  const [pairingDeviceId, setPairingDeviceId] = useState(null);
  const [bonding, setBonding] = useState(false);
  const [status, setStatus] = useState({ state: "idle" });
  const [controllerState, setControllerState] = useState(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [mapping, setMapping] = useState(defaultMapping);
  const [mappingSaved, setMappingSaved] = useState(false);

  useEffect(() => {
    const unlisteners = [];
    let removeDiscoveryListener;
    let active = true;

    async function register(event, handler) {
      const unlisten = await listen(event, handler);
      if (active) unlisteners.push(unlisten);
      else unlisten();
    }

    async function subscribe() {
      try {
        await register("moga-status", (event) => {
          setStatus(event.payload);
          setError(event.payload.message ?? "");
          if (event.payload.state !== "connecting") {
            setPairingDeviceId(null);
            setBonding(false);
          }
        });
        await register("moga-state", (event) => setControllerState(event.payload));
        await register("moga-error", (event) => setError(String(event.payload)));
        if (/Android/i.test(navigator.userAgent)) {
          const listener = await addPluginListener(
            "moga-android",
            "moga-discovered-devices",
            (payload) => {
              if (!active) return;
              setUnpairedDevices(payload.devices ?? []);
            setDevices(payload.bondedDevices ?? []);
            setScanning(Boolean(payload.scanning));
            },
          );
          if (active) removeDiscoveryListener = listener;
          else await listener.unregister();
        }
        const currentStatus = await invoke("connection_status");
        if (active) {
          setStatus(currentStatus);
        }
        if (active && /Android/i.test(navigator.userAgent)) {
          const savedMapping = await invoke("get_key_mapping");
          setMapping({ ...defaultMapping, ...savedMapping });
        }
      } catch (reason) {
        if (active) setError(String(reason));
      }
    }

    subscribe();

    return () => {
      active = false;
      unlisteners.forEach((unlisten) => unlisten());
      removeDiscoveryListener?.unregister().catch((reason) => {
        console.error("Could not remove Bluetooth discovery listener:", reason);
      });
      if (/Android/i.test(navigator.userAgent)) {
        invoke("stop_device_scan").catch((reason) => {
          console.error("Could not stop Bluetooth discovery during cleanup:", reason);
        });
      }
    };
  }, []);

  async function scan() {
    setBusy(true);
    setError("");
    try {
      await invoke("request_bluetooth_permission");
      setDevices(await invoke("scan_moga"));
      setUnpairedDevices([]);
      setScanning(true);
      await invoke("scan_unpaired_devices");
    } catch (reason) {
      setScanning(false);
      setError(String(reason));
    } finally {
      setBusy(false);
    }
  }

  async function connect(device) {
    setError("");
    setPairingDeviceId(device.id);
    setBonding(!device.bonded);
    try {
      await invoke("connect_moga", { deviceId: device.id });
    } catch (reason) {
      setError(String(reason));
      setPairingDeviceId(null);
      setBonding(false);
    }
  }

  async function disconnect() {
    setError("");
    try {
      await invoke("disconnect_moga");
      setPairingDeviceId(null);
    } catch (reason) {
      setError(String(reason));
    }
  }

  async function saveMapping(event) {
    event.preventDefault();
    setError("");
    setMappingSaved(false);
    try {
      await invoke("set_key_mapping", { mapping });
      setMappingSaved(true);
    } catch (reason) {
      setError(String(reason));
    }
  }

  async function openImeSettings() {
    setError("");
    try {
      await invoke("open_ime_settings");
    } catch (reason) {
      setError(String(reason));
    }
  }

  return (
    <main className="app-shell">
      <header className="app-header">
        <div>
          <p className="eyebrow">MOGA POCKET · MODE A</p>
          <h1>Puente de control</h1>
          <p className="lede">Bluetooth RFCOMM · Mapeo de teclado</p>
        </div>
        <div className={`status status-${status.state}`} role="status">
          <span className="status-dot" />
          {status.state}
        </div>
      </header>

      <section className="panel">
        <div className="panel-heading">
          <div>
            <h2>Dispositivo Bluetooth</h2>
            <p>Enciende el MOGA y escanéalo aquí; confirma cualquier aviso de vinculación de Android.</p>
          </div>
          <button type="button" onClick={scan} disabled={busy || scanning}>
            {busy ? "Preparando…" : scanning ? "Buscando…" : "Escanear"}
          </button>
          {scanning && (
            <button
              type="button"
              className="button-secondary"
              onClick={async () => {
                try {
                  await invoke("stop_device_scan");
                  setScanning(false);
                } catch (reason) {
                  setError(String(reason));
                }
              }}
            >
              Detener búsqueda
            </button>
          )}
          {(status.state === "connecting" || status.state === "connected") && (
            <button type="button" className="button-secondary" onClick={disconnect}>
              Desconectar
            </button>
          )}
        </div>

        <h3>Dispositivos Ya Sincronizados</h3>
        {devices.length > 0 ? (
          <ul className="device-list">
            {devices.map((device) => (
              <li key={device.id}>
                <span>{device.name}</span>
                <button
                  type="button"
                  onClick={() => connect(device)}
                  disabled={["connecting", "connected", "disconnecting"].includes(status.state)}
                >
                  {pairingDeviceId === device.id && status.state === "connecting"
                    ? "Conectando…"
                    : "Conectar"}
                </button>
              </li>
            ))}
          </ul>
        ) : (
          <p className="empty-state">No hay mandos MOGA sincronizados.</p>
        )}

        <h3>Dispositivos Disponibles (No Sincronizados)</h3>
        {unpairedDevices.length > 0 ? (
          <ul className="device-list">
            {unpairedDevices.map((device) => (
              <li key={device.id}>
                <span>{device.name}</span>
                <button
                  type="button"
                  onClick={() => connect(device)}
                  disabled={["connecting", "connected", "disconnecting"].includes(status.state)}
                >
                  {pairingDeviceId === device.id && bonding && status.state === "connecting"
                    ? "Sincronizando…"
                    : "Sincronizar y conectar"}
                </button>
              </li>
            ))}
          </ul>
        ) : (
          <p className="empty-state">
            {scanning ? "Buscando mandos MOGA cercanos…" : "No se encontraron mandos disponibles."}
          </p>
        )}
        {pairingDeviceId && bonding && status.state === "connecting" && (
          <p className="pairing-message" role="status">
            Sincronizando control programáticamente… Confirma la solicitud de vinculación que
            muestra Android para continuar.
          </p>
        )}
        {error && <p className="error-message" role="alert">{error}</p>}
      </section>

      <section className="panel">
        <div className="panel-heading">
          <div>
            <h2>Mapeo de Teclas</h2>
            <p>Selecciona la tecla que enviará cada control mientras el IME esté activo.</p>
          </div>
          <button type="button" className="button-secondary" onClick={openImeSettings}>
            Configurar teclado Android
          </button>
        </div>
        <form onSubmit={saveMapping}>
          <div className="mapping-grid">
            {mappingControls.map(([control, label]) => (
              <label className="mapping-row" key={control}>
                <span>{label}</span>
                <select
                  value={mapping[control]}
                  onChange={(event) => {
                    setMapping((current) => ({
                      ...current,
                      [control]: Number(event.currentTarget.value),
                    }));
                    setMappingSaved(false);
                  }}
                >
                  {keyOptions.map((option) => (
                    <option value={option.value} key={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
            ))}
          </div>
          <div className="mapping-actions">
            <button type="submit">Guardar mapeo</button>
            {mappingSaved && <span className="saved-message" role="status">Mapeo guardado</span>}
          </div>
        </form>
        <p className="notice">
          Activa “MOGA Key Mapper” en los ajustes de teclado y selecciónalo como método de
          entrada. Android solo enviará teclas al editor enfocado; esto no inyecta botones de
          gamepad en juegos arbitrarios.
        </p>
      </section>

      <section className="panel">
        <h2>Estado del mando</h2>
        {controllerState ? (
          <pre>{JSON.stringify(controllerState, null, 2)}</pre>
        ) : (
          <p className="empty-state">El estado aparecerá cuando se reciba un reporte.</p>
        )}
      </section>

      <p className="notice">
        La lectura RFCOMM se realiza sin root. El IME traduce pulsaciones a eventos de teclado
        para la aplicación que tenga el editor activo; para contenido propio, el evento
        <code> moga-state </code> queda disponible en Tauri sin pasar por el IME.
      </p>
    </main>
  );
}

export default App;
