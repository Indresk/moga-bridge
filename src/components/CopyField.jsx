import { useState } from "react";
import Button from "./Button";

/** Selectable command with a copy button that confirms the copy (or explains the failure). */
export default function CopyField({ value }) {
  const [feedback, setFeedback] = useState("");

  async function copy() {
    try {
      await navigator.clipboard.writeText(value);
      setFeedback("Copiado");
    } catch {
      setFeedback("No se pudo copiar; mantén pulsado el comando para seleccionarlo");
    }
    setTimeout(() => setFeedback(""), 2500);
  }

  return (
    <div className="copy-field">
      <code className="command">{value}</code>
      <div className="copy-actions">
        <Button variant="secondary" onClick={copy}>
          Copiar comando
        </Button>
        {feedback && <span className="copy-feedback">{feedback}</span>}
      </div>
    </div>
  );
}
