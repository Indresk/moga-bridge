package dev.mogabridge.app.moga

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Explicit pairing through the public `createBond()`, as in the legacy app's Add Device flow.
 * Android owns the confirmation/PIN UI; this only waits for the outcome.
 */
internal class DeviceBonding(private val context: Context) {
    @Volatile private var cancelled = false
    @Volatile private var latch: CountDownLatch? = null

    /** Wake and abort a bonding wait in progress. */
    fun cancel() {
        cancelled = true
        latch?.countDown()
    }

    /** Returns true if a new bond had to be created, false if the device was already bonded. */
    fun ensureBonded(device: BluetoothDevice, isCancelled: () -> Boolean): Boolean {
        if (device.bondState == BluetoothDevice.BOND_BONDED) return false
        if (isCancelled()) throw IOException("Pairing was cancelled.")

        val finished = CountDownLatch(1)
        cancelled = false
        latch = finished
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
                val changed = intent.bluetoothDevice() ?: return
                if (changed.address == device.address &&
                    changed.bondState != BluetoothDevice.BOND_BONDING
                ) {
                    finished.countDown()
                }
            }
        }

        context.registerBluetoothReceiver(
            receiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
        )
        try {
            if (!device.createBond()) {
                throw IOException("Android could not start pairing with ${device.name ?: device.address}.")
            }
            if (!finished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw IOException("Pairing timed out. Confirm the pairing request on Android.")
            }
            if (cancelled || isCancelled()) throw IOException("Pairing was cancelled.")
            if (device.bondState != BluetoothDevice.BOND_BONDED) {
                throw IOException("Pairing was declined or failed. Confirm the Android pairing request.")
            }
            return true
        } finally {
            latch = null
            try {
                context.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
                Unit
            }
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 90L
    }
}
