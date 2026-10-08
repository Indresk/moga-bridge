//! The connection session: handshake, read loop, decoding and event emission.

use std::{
    io::Read,
    sync::{Arc, Mutex},
    thread,
};

use tauri::{AppHandle, Emitter};

use crate::constants::{events, protocol::PLAYER_ONE};
use crate::drivers::{MogaConnection, MogaDriver};
use crate::protocol::{build_command, Command, PacketStreamParser};
use crate::schemas::{ConnectionState, ConnectionStatus};

const READ_BUFFER_LEN: usize = 64;

/// The controller powers itself off after a period without input; both the clean close and
/// the read error that follow look like a lost link, so the message says it is expected.
const IDLE_POWER_OFF_HINT: &str =
    "El mando se desconectó. Es normal si pasó un rato sin pulsaciones: se apaga solo para ahorrar batería. Enciéndelo y vuelve a conectar.";

pub type SharedStatus = Arc<Mutex<ConnectionStatus>>;

/// Move to `Connecting` unless a session is already active.
pub fn begin(connection: &Mutex<ConnectionStatus>, app: &AppHandle, device_id: &str) -> Result<(), String> {
    let mut status = connection
        .lock()
        .map_err(|error| format!("Could not update connection status: {error}"))?;
    if status.state.is_active() {
        return Err("A MOGA connection is already active.".into());
    }
    *status = ConnectionStatus::new(
        ConnectionState::Connecting,
        Some(device_id.to_string()),
        None,
    );
    emit_status(app, &status);
    Ok(())
}

/// Spawn the named worker that connects and then streams reports until the link closes.
pub fn spawn_worker(
    app: AppHandle,
    driver: Arc<dyn MogaDriver>,
    connection: SharedStatus,
    device_id: String,
) -> Result<(), String> {
    let app_for_error = app.clone();
    let connection_for_error = Arc::clone(&connection);
    let device_for_error = device_id.clone();

    thread::Builder::new()
        .name("moga-rfcomm".into())
        .spawn(move || {
            let result = driver
                .connect(&device_id)
                .and_then(|stream| run_session(&app, &connection, &device_id, stream));
            finish(&app, &connection, device_id, result);
        })
        .map(|_| ())
        .map_err(|error| {
            let message = format!("Could not start the MOGA connection thread: {error}");
            set_status(
                &connection_for_error,
                &app_for_error,
                ConnectionState::Error,
                Some(device_for_error),
                Some(message.clone()),
            );
            message
        })
}

/// Handshake, then read until EOF: select player + poll, and listen before every read, just
/// like the legacy Java loop.
fn run_session(
    app: &AppHandle,
    connection: &SharedStatus,
    device_id: &str,
    mut stream: Box<dyn MogaConnection>,
) -> Result<(), String> {
    stream.send_command(build_command(Command::SelectPlayer, PLAYER_ONE))?;
    stream.send_command(build_command(Command::Poll, PLAYER_ONE))?;
    set_status(
        connection,
        app,
        ConnectionState::Connected,
        Some(device_id.to_string()),
        None,
    );

    let mut parser = PacketStreamParser::default();
    let mut buffer = [0_u8; READ_BUFFER_LEN];

    loop {
        stream.send_command(build_command(Command::Listen, PLAYER_ONE))?;
        let bytes_read = stream
            .read(&mut buffer)
            .map_err(|error| format!("RFCOMM read failed: {error}"))?;
        if bytes_read == 0 {
            return Ok(());
        }

        for report in parser.feed(&buffer[..bytes_read]) {
            match report {
                Ok(state) => {
                    if let Err(error) = stream.dispatch_input_state(&state) {
                        emit_error(app, format!("Could not forward controller input: {error}"));
                    }
                    if let Err(error) = app.emit(events::STATE, state) {
                        eprintln!("Could not emit MOGA state event: {error}");
                    }
                }
                Err(error) => emit_error(app, format!("Rejected MOGA packet: {error}")),
            }
        }
    }
}

fn finish(
    app: &AppHandle,
    connection: &SharedStatus,
    device_id: String,
    result: Result<(), String>,
) {
    let disconnect_requested = connection
        .lock()
        .map(|status| status.state == ConnectionState::Disconnecting)
        .unwrap_or(false);

    if disconnect_requested {
        set_status(connection, app, ConnectionState::Disconnected, None, None);
        return;
    }
    match result {
        Ok(()) => set_status(
            connection,
            app,
            ConnectionState::Disconnected,
            None,
            Some(IDLE_POWER_OFF_HINT.into()),
        ),
        Err(error) => set_status(
            connection,
            app,
            ConnectionState::Error,
            Some(device_id),
            Some(format!("{error}. {IDLE_POWER_OFF_HINT}")),
        ),
    }
}

/// Mark a requested disconnect: an active session becomes `Disconnecting` (the worker then
/// reports `Disconnected`), otherwise it is already over.
pub fn request_disconnect(connection: &Mutex<ConnectionStatus>, app: &AppHandle) -> Result<(), String> {
    let mut status = connection
        .lock()
        .map_err(|error| format!("Could not update connection status: {error}"))?;
    if matches!(
        status.state,
        ConnectionState::Connecting | ConnectionState::Connected
    ) {
        status.state = ConnectionState::Disconnecting;
        status.message = None;
    } else {
        *status = ConnectionStatus::new(ConnectionState::Disconnected, None, None);
    }
    emit_status(app, &status);
    Ok(())
}

pub fn set_status(
    connection: &Mutex<ConnectionStatus>,
    app: &AppHandle,
    state: ConnectionState,
    device_id: Option<String>,
    message: Option<String>,
) {
    match connection.lock() {
        Ok(mut status) => {
            *status = ConnectionStatus::new(state, device_id, message);
            emit_status(app, &status);
        }
        Err(error) => eprintln!("Could not update MOGA connection status: {error}"),
    }
}

fn emit_status(app: &AppHandle, status: &ConnectionStatus) {
    if let Err(error) = app.emit(events::STATUS, status.clone()) {
        eprintln!("Could not emit MOGA connection status: {error}");
    }
}

fn emit_error(app: &AppHandle, message: String) {
    eprintln!("{message}");
    if let Err(error) = app.emit(events::ERROR, message) {
        eprintln!("Could not emit MOGA error event: {error}");
    }
}
