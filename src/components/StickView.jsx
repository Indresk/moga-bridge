const RANGE = 127;

/** Analog stick position (-127..127, y positive = down) inside a circle, plus its value. */
export default function StickView({ label, stick }) {
  const x = stick?.x ?? 0;
  const y = stick?.y ?? 0;
  const moved = Math.abs(x) > 8 || Math.abs(y) > 8;
  const style = {
    left: `${50 + (x / RANGE) * 38}%`,
    top: `${50 + (y / RANGE) * 38}%`,
  };

  return (
    <figure className="stick">
      <div className="stick-well" role="img" aria-label={`${label}: x ${x}, y ${y}`}>
        <span className={`stick-dot ${moved ? "moved" : ""}`} style={style} />
      </div>
      <figcaption>
        {label}
        <small>
          x {x} · y {y}
        </small>
      </figcaption>
    </figure>
  );
}
