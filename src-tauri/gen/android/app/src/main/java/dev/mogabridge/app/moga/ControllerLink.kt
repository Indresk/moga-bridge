package dev.mogabridge.app.moga

import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The open RFCOMM link to the controller: a reader thread fills a bounded queue that the Rust
 * side drains with [take], and [write] sends commands. An empty chunk from [take] means the
 * link ended.
 */
internal class ControllerLink {
    private val lock = Any()
    private val incoming = LinkedBlockingQueue<List<Int>>(QUEUE_CAPACITY)
    @Volatile private var socket: BluetoothSocket? = null

    @Volatile var connected = false
        private set

    @Volatile var error: String? = null
        private set

    fun resetError() {
        error = null
    }

    fun attach(newSocket: BluetoothSocket) {
        synchronized(lock) {
            socket = newSocket
            connected = true
            error = null
            incoming.clear()
        }
    }

    /** Start the reader; `onEnded` runs once, only if this socket was still the current one. */
    fun startReader(rfcommSocket: BluetoothSocket, onEnded: () -> Unit) {
        Thread(
            {
                val buffer = ByteArray(READ_BUFFER)
                try {
                    val input = rfcommSocket.inputStream
                    while (connected && rfcommSocket.isConnected) {
                        val size = input.read(buffer)
                        if (size < 0) break
                        val queued = incoming.offer(
                            buffer.copyOf(size).map { it.toInt() and 0xFF },
                            OFFER_TIMEOUT_MS,
                            TimeUnit.MILLISECONDS,
                        )
                        if (!queued) {
                            throw IOException("MOGA input queue remained full; controller read stopped.")
                        }
                    }
                } catch (readError: Exception) {
                    if (connected) error = readError.message ?: "RFCOMM read failed."
                } finally {
                    var wasCurrent = false
                    synchronized(lock) {
                        if (socket === rfcommSocket) {
                            connected = false
                            socket = null
                            wasCurrent = true
                        }
                    }
                    closeQuietly(rfcommSocket)
                    if (wasCurrent) {
                        incoming.clear()
                        incoming.offer(emptyList())
                        onEnded()
                    }
                }
            },
            "moga-rfcomm-reader",
        ).apply { isDaemon = true }.start()
    }

    /** Blocks for the next chunk of controller bytes; an empty list means the link ended. */
    fun take(): List<Int> = incoming.take()

    fun write(bytes: ByteArray) {
        val current = socket ?: throw IOException("The MOGA RFCOMM socket is not connected.")
        synchronized(lock) {
            current.outputStream.write(bytes)
            current.outputStream.flush()
        }
    }

    /** Close the link and wake anyone waiting in [take]. */
    fun close() {
        val old: BluetoothSocket?
        synchronized(lock) {
            connected = false
            old = socket
            socket = null
            incoming.clear()
            incoming.offer(emptyList())
        }
        closeQuietly(old)
    }

    private fun closeQuietly(target: BluetoothSocket?) {
        try {
            target?.close()
        } catch (closeError: IOException) {
            error = error ?: closeError.message ?: "Could not close the RFCOMM socket."
        }
    }

    private companion object {
        const val QUEUE_CAPACITY = 128
        const val READ_BUFFER = 64
        const val OFFER_TIMEOUT_MS = 500L
    }
}
