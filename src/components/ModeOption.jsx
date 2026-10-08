export default function ModeOption({
  name,
  value,
  selected,
  onSelect,
  title,
  disabled = false,
  note,
  children,
}) {
  return (
    <label className={`mode-card ${selected ? "selected" : ""} ${disabled ? "disabled" : ""}`}>
      <input
        type="radio"
        name={name}
        value={value}
        checked={selected}
        disabled={disabled}
        onChange={() => onSelect(value)}
      />
      <strong>{title}</strong>
      <span>{children}</span>
      {note && <em className="mode-note">{note}</em>}
    </label>
  );
}
