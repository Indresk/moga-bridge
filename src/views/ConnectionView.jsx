import Button from "../components/Button";
import DeviceList from "../components/DeviceList";
import Message from "../components/Message";
import Panel from "../components/Panel";
import SectionTitle from "../components/SectionTitle";

export default function ConnectionView({ discovery, controller, error }) {
  const { paired, discovered, scanning, busy, scan, stop } = discovery;
  const { status, pairingDeviceId, bonding, locked, connect, disconnect } = controller;
  const connecting = status.state === "connecting";
  const connectedOrConnecting = connecting || status.state === "connected";

  const actions = (
    <>
      <Button onClick={scan} disabled={busy || scanning}>
        {busy ? "Preparando…" : scanning ? "Buscando…" : "Escanear"}
      </Button>
      {scanning && (
        <Button variant="secondary" onClick={stop}>
          Detener búsqueda
        </Button>
      )}
      {connectedOrConnecting && (
        <Button variant="secondary" onClick={disconnect}>
          Desconectar
        </Button>
      )}
    </>
  );

  return (
    <Panel
      title="Conexión Bluetooth"
      description="Enciende el MOGA en Modo A y escanéalo; confirma el aviso de vinculación de Android."
      actions={actions}
    >
      <SectionTitle>Mandos vinculados</SectionTitle>
      <DeviceList
        devices={paired}
        empty="No hay mandos MOGA vinculados."
        actionLabel="Conectar"
        busyLabel="Conectando…"
        busyId={connecting ? pairingDeviceId : null}
        locked={locked}
        onSelect={connect}
      />

      <SectionTitle>Mandos disponibles</SectionTitle>
      <DeviceList
        devices={discovered}
        empty={scanning ? "Buscando mandos MOGA cercanos…" : "No se encontraron mandos disponibles."}
        actionLabel="Vincular y conectar"
        busyLabel={bonding ? "Vinculando…" : "Conectando…"}
        busyId={connecting ? pairingDeviceId : null}
        locked={locked}
        onSelect={connect}
      />

      {pairingDeviceId && bonding && connecting && (
        <Message tone="warning">
          Vinculando el mando… confirma la solicitud de emparejamiento que muestra Android.
        </Message>
      )}
      <Message tone="error">{error}</Message>
      <p className="notice">
        Ten en cuenta que el MOGA se apaga solo tras un rato sin recibir pulsaciones: es un
        comportamiento propio del mando para ahorrar batería. Si ocurre, enciéndelo y vuelve a
        conectar.
      </p>
    </Panel>
  );
}
