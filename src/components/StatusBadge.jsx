import iconOff from "../assets/legacy/moga-icon.png";
import iconOn from "../assets/legacy/moga-icon-on.png";

const labels = {
  idle: "Inactivo",
  connecting: "Conectando",
  connected: "Conectado",
  disconnecting: "Desconectando",
  disconnected: "Desconectado",
  error: "Error",
};

/** Controller glyph from the legacy app: white when off, green when connected. */
export default function StatusBadge({ state }) {
  const connected = state === "connected";
  return (
    <div className={`status-badge status-${state}`} role="status">
      <img src={connected ? iconOn : iconOff} alt="" width="24" height="24" />
      <span>{labels[state] ?? state}</span>
    </div>
  );
}
