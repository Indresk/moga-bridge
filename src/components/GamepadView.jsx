import StickView from "./StickView";

function Key({ label, pressed, className = "" }) {
  return (
    <span className={`pad-key ${className} ${pressed ? "pressed" : ""}`} aria-pressed={!!pressed}>
      {label}
    </span>
  );
}

/** Live picture of the controller: buttons light up, sticks show their analog position. */
export default function GamepadView({ state, active }) {
  const buttons = state?.buttons ?? {};
  return (
    <div className={`pad ${active ? "" : "pad-idle"}`} aria-label="Estado del mando">
      <div className="pad-shoulders">
        <Key label="L" pressed={buttons.leftBumper} />
        <div className="pad-center">
          <Key label="Select" pressed={buttons.select} className="small" />
          <Key label="Start" pressed={buttons.start} className="small" />
        </div>
        <Key label="R" pressed={buttons.rightBumper} />
      </div>
      <div className="pad-body">
        <StickView label="Stick izquierdo" stick={state?.leftStick} />
        <div className="pad-face">
          <Key label="Y" pressed={buttons.y} className="up" />
          <Key label="X" pressed={buttons.x} className="left" />
          <Key label="B" pressed={buttons.b} className="right" />
          <Key label="A" pressed={buttons.a} className="down" />
        </div>
        <StickView label="Stick derecho" stick={state?.rightStick} />
      </div>
    </div>
  );
}
