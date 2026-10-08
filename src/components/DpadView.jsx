/** A stick shown as a D-pad: four arrows lit by the stick's digital direction flags. */
export default function DpadView({ label, stick }) {
  const flags = stick ?? {};
  const arrow = (name, symbol) => (
    <span className={`dpad-arrow ${name} ${flags[name] ? "pressed" : ""}`} aria-pressed={!!flags[name]}>
      {symbol}
    </span>
  );

  return (
    <figure className="stick">
      <div className="dpad" role="img" aria-label={label}>
        {arrow("up", "▲")}
        {arrow("left", "◀")}
        {arrow("right", "▶")}
        {arrow("down", "▼")}
      </div>
      <figcaption>{label}</figcaption>
    </figure>
  );
}
