import { useEffect, useRef, useState } from "react";
import Header from "./components/Header";
import Tabs from "./components/Tabs";
import { useController } from "./hooks/useController";
import { useDeviceDiscovery } from "./hooks/useDeviceDiscovery";
import { useKeyMapping } from "./hooks/useKeyMapping";
import { useOutputSettings } from "./hooks/useOutputSettings";
import { openImeSettings } from "./lib/api";
import ConnectionView from "./views/ConnectionView";
import MappingView from "./views/MappingView";
import TestView from "./views/TestView";
import "./styles/index.css";

const tabs = [
  { id: "connection", label: "Conexión" },
  { id: "test", label: "Prueba" },
  { id: "mapping", label: "Mapeo" },
];

/** Composition root: owns the shared error and wires hooks to views. */
function App() {
  const [view, setView] = useState("connection");
  const [error, setError] = useState("");

  const controller = useController({ onError: setError });
  const discovery = useDeviceDiscovery({ onError: setError });
  const keyMapping = useKeyMapping({ onError: setError });
  const output = useOutputSettings({ onError: setError });
  const { refresh: refreshOutput } = output;

  // Jump to Test on connect; if the link drops on its own (the controller powers itself off
  // after a while without input) return to Connection, where the explanation is shown.
  const previousState = useRef(controller.status.state);
  useEffect(() => {
    const current = controller.status.state;
    if (current === "connected") setView("test");
    if (previousState.current === "connected" && ["disconnected", "error"].includes(current)) {
      setView("connection");
    }
    previousState.current = current;
  }, [controller.status.state]);

  useEffect(() => {
    if (view === "mapping") refreshOutput();
  }, [view, refreshOutput]);

  async function showImeSettings() {
    setError("");
    try {
      await openImeSettings();
    } catch (reason) {
      setError(String(reason));
    }
  }

  return (
    <main className="app-shell">
      <Header status={controller.status} />
      <Tabs tabs={tabs} active={view} onChange={setView} />

      {view === "connection" && (
        <ConnectionView discovery={discovery} controller={controller} error={error} />
      )}
      {view === "test" && (
        <TestView
          controller={controller}
          output={output}
          onOpenConnection={() => setView("connection")}
        />
      )}
      {view === "mapping" && (
        <MappingView
          output={output}
          keyMapping={keyMapping}
          openImeSettings={showImeSettings}
          error={error}
        />
      )}
    </main>
  );
}

export default App;
