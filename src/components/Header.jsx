import launcher from "../assets/legacy/moga-launcher.png";
import StatusBadge from "./StatusBadge";

export default function Header({ status }) {
  return (
    <header className="app-header">
      <img className="app-logo" src={launcher} alt="" width="48" height="48" />
      <div className="app-heading">
        <p className="eyebrow">Modo A · RFCOMM</p>
        <h1>MOGA Bridge</h1>
      </div>
      <StatusBadge state={status.state} />
    </header>
  );
}
