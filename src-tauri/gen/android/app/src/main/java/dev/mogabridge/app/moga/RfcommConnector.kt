package dev.mogabridge.app.moga

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException

/** Opens the RFCOMM socket, trying each strategy in order, over several rounds. */
internal class RfcommConnector {
    private val lock = Any()
    private var pending: BluetoothSocket? = null

    /** Abort a connection attempt in progress (closing the socket makes `connect()` fail). */
    fun cancel() {
        val candidate = synchronized(lock) { pending.also { pending = null } }
        closeQuietly(candidate)
    }

    /**
     * Blocks until a strategy connects. The legacy app waited and looped (its
     * `CONNECTION_DELAY_MS` / 500 ms pause) because the Pocket often refuses the first connect
     * right after bonding or inquiry; this does the same.
     */
    fun connect(
        adapter: BluetoothAdapter,
        device: BluetoothDevice,
        strategies: List<String>,
        isCancelled: () -> Boolean,
    ): BluetoothSocket {
        adapter.cancelDiscovery()
        val failures = mutableListOf<String>()

        Thread.sleep(SETTLE_MILLIS)
        for (round in 1..ROUNDS) {
            for (strategy in strategies) {
                if (isCancelled()) throw cancelled()

                val candidate = try {
                    RfcommSockets.create(device, strategy)
                } catch (error: Exception) {
                    Log.w(TAG, "RFCOMM strategy=$strategy unavailable: ${error.message}")
                    failures += "$strategy: ${error.message ?: error.javaClass.simpleName}"
                    continue
                }

                synchronized(lock) {
                    if (isCancelled()) {
                        closeQuietly(candidate)
                        throw cancelled()
                    }
                    pending = candidate
                }

                try {
                    candidate.connect()
                    if (isCancelled()) {
                        closeQuietly(candidate)
                        throw cancelled()
                    }
                    synchronized(lock) { if (pending === candidate) pending = null }
                    Log.i(TAG, "RFCOMM connected with strategy=$strategy (round $round/$ROUNDS)")
                    return candidate
                } catch (error: Exception) {
                    synchronized(lock) { if (pending === candidate) pending = null }
                    closeQuietly(candidate)
                    if (isCancelled()) throw IOException("The Bluetooth connection was cancelled.", error)
                    Log.w(TAG, "RFCOMM strategy=$strategy failed (round $round): ${error.message}")
                    failures += "$strategy: ${error.message ?: error.javaClass.simpleName}"
                }
            }
            if (round < ROUNDS) Thread.sleep(RETRY_MILLIS)
        }

        throw IOException(
            "All legacy MOGA RFCOMM connection methods failed. ${failures.joinToString(" | ")}",
        )
    }

    private fun cancelled() = IOException("The Bluetooth connection was cancelled.")

    private fun closeQuietly(socket: BluetoothSocket?) {
        try {
            socket?.close()
        } catch (_: IOException) {
            Unit
        }
    }

    private companion object {
        const val TAG = "MogaRfcomm"
        const val SETTLE_MILLIS = 2_000L
        const val RETRY_MILLIS = 500L
        const val ROUNDS = 3
    }
}
