import Button from "./Button";

export default function DeviceList({ devices, empty, actionLabel, busyLabel, busyId, locked, onSelect }) {
  if (devices.length === 0) return <p className="empty-state">{empty}</p>;
  return (
    <ul className="device-list">
      {devices.map((device) => (
        <li key={device.id}>
          <span className="device-name">{device.name}</span>
          <Button onClick={() => onSelect(device)} disabled={locked}>
            {busyId === device.id ? busyLabel : actionLabel}
          </Button>
        </li>
      ))}
    </ul>
  );
}
