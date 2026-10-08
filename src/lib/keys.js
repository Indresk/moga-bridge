// Android KeyEvent key codes offered in the keyboard-output mapping.
const letters = Array.from({ length: 26 }, (_, index) => ({
  value: 29 + index, // KEYCODE_A = 29 ... KEYCODE_Z = 54
  label: String.fromCharCode(65 + index),
}));

const digits = Array.from({ length: 10 }, (_, index) => ({
  value: 7 + index, // KEYCODE_0 = 7 ... KEYCODE_9 = 16
  label: String(index),
}));

export const keyOptions = [
  { value: 0, label: "Desactivado" },
  { value: 19, label: "Flecha arriba" },
  { value: 20, label: "Flecha abajo" },
  { value: 21, label: "Flecha izquierda" },
  { value: 22, label: "Flecha derecha" },
  { value: 62, label: "Espacio" },
  { value: 66, label: "Enter" },
  { value: 111, label: "Escape" },
  { value: 61, label: "Tab" },
  ...letters,
  ...digits,
];

// [mapping key, label, group]. Keys match KeyMapping in src-tauri/src/schemas/settings.rs.
export const mappingControls = [
  ["leftStickUp", "Stick izquierdo ↑", "Stick izquierdo"],
  ["leftStickDown", "Stick izquierdo ↓", "Stick izquierdo"],
  ["leftStickLeft", "Stick izquierdo ←", "Stick izquierdo"],
  ["leftStickRight", "Stick izquierdo →", "Stick izquierdo"],
  ["rightStickUp", "Stick derecho ↑", "Stick derecho"],
  ["rightStickDown", "Stick derecho ↓", "Stick derecho"],
  ["rightStickLeft", "Stick derecho ←", "Stick derecho"],
  ["rightStickRight", "Stick derecho →", "Stick derecho"],
  ["buttonA", "Botón A", "Botones"],
  ["buttonB", "Botón B", "Botones"],
  ["buttonX", "Botón X", "Botones"],
  ["buttonY", "Botón Y", "Botones"],
  ["leftBumper", "Bumper izquierdo (L)", "Botones"],
  ["rightBumper", "Bumper derecho (R)", "Botones"],
  ["start", "Start", "Botones"],
  ["select", "Select", "Botones"],
];

const key = (letter) => 29 + letter.charCodeAt(0) - 65;

// Common emulator keyboard layout (PPSSPP's default). MOGA A/B/X/Y occupy the Cross/Circle/Square/Triangle
// positions of a PSP-style pad. Keep in sync with KeyMapping::default() in schemas/settings.rs
// and DEFAULT_MAPPING in MogaAndroidPlugin.kt.
export const ppssppMapping = {
  buttonA: key("Z"),
  buttonB: key("X"),
  buttonX: key("A"),
  buttonY: key("S"),
  start: 62,
  select: key("V"),
  leftBumper: key("Q"),
  rightBumper: key("W"),
  leftStickUp: 19,
  leftStickDown: 20,
  leftStickLeft: 21,
  leftStickRight: 22,
  rightStickUp: key("I"),
  rightStickDown: key("K"),
  rightStickLeft: key("J"),
  rightStickRight: key("L"),
};

export const presets = [
  { id: "ppsspp", label: "Emulador (Z X A S, flechas)", mapping: ppssppMapping },
  {
    id: "arrows",
    label: "Flechas, Espacio y Enter",
    mapping: {
      ...Object.fromEntries(mappingControls.map(([control]) => [control, 0])),
      leftStickUp: 19,
      leftStickDown: 20,
      leftStickLeft: 21,
      leftStickRight: 22,
      buttonA: 62,
      start: 66,
    },
  },
];

export const defaultMapping = ppssppMapping;
