package dev.mogabridge.app.moga

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid

/**
 * Classic Bluetooth inquiry for MOGA controllers. Results are pushed through `publish` as
 * `moga-discovered-devices` events (paired list + discovered list + scanning flag).
 */
internal class DeviceDiscovery(
    private val context: Context,
    private val publish: (Map<String, Any>) -> Unit,
) {
    private val lock = Any()
    private val discovered = linkedMapOf<String, BluetoothDevice>()
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var receiver: BroadcastReceiver? = null
    @Volatile private var scanning = false

    fun start(adapter: BluetoothAdapter) {
        stop()
        synchronized(lock) {
            discovered.clear()
            scanning = true
            adapter.bondedDevices
                .filter(MogaDevices::isMogaDevice)
                .forEach { discovered[it.address] = it }
            emit(scanning = true)
        }

        val newReceiver = createReceiver(adapter)
        context.registerBluetoothReceiver(newReceiver, filter())
        synchronized(lock) { receiver = newReceiver }
        if (!adapter.startDiscovery()) {
            stop()
            throw IllegalStateException("Android could not start Bluetooth discovery.")
        }
    }

    /** Cancel discovery, unregister the receiver and publish a final "not scanning" update. */
    fun stop() {
        val adapter = try {
            context.bluetoothAdapter()
        } catch (_: Exception) {
            null
        }
        val oldReceiver: BroadcastReceiver?
        val wasScanning: Boolean
        synchronized(lock) {
            oldReceiver = receiver
            receiver = null
            wasScanning = scanning || oldReceiver != null
            scanning = false
        }
        handler.removeCallbacksAndMessages(null)
        try {
            adapter?.cancelDiscovery()
        } catch (_: Exception) {
            Unit
        }
        if (oldReceiver != null) {
            try {
                context.unregisterReceiver(oldReceiver)
            } catch (_: IllegalArgumentException) {
                Unit
            }
        }
        if (wasScanning && adapter != null) {
            synchronized(lock) { emit(scanning = false) }
        }
    }

    /** Re-publish the lists, e.g. after a device became bonded. */
    fun republish() {
        synchronized(lock) { emit(scanning) }
    }

    private fun createReceiver(adapter: BluetoothAdapter) = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND, BluetoothDevice.ACTION_NAME_CHANGED -> {
                    val device = intent.bluetoothDevice() ?: return
                    if (device.bondState == BluetoothDevice.BOND_BONDED) return
                    // device.name is often still null when the device is first found; the
                    // inquiry name arrives in EXTRA_NAME (the legacy app relied on it too).
                    // No fetchUuidsWithSdp() here: SDP traffic during inquiry slows discovery
                    // and can make the Pocket drop out; the legacy app matched by name only.
                    val name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                    if (MogaDevices.isMogaName(name) || MogaDevices.isMogaDevice(device)) {
                        remember(device)
                    }
                }
                BluetoothDevice.ACTION_UUID -> {
                    val device = intent.bluetoothDevice() ?: return
                    val uuids = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID)
                        ?.filterIsInstance<ParcelUuid>()
                        .orEmpty()
                    if (device.bondState != BluetoothDevice.BOND_BONDED &&
                        uuids.any { it.uuid == RfcommSockets.SPP_UUID }
                    ) {
                        remember(device)
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    scanning = false
                    synchronized(lock) { emit(scanning = false) }
                    // Keep listening briefly so late SPP UUID lookups still arrive.
                    handler.postDelayed({ stop() }, UUID_LOOKUP_GRACE_MILLIS)
                }
            }
        }
    }

    private fun remember(device: BluetoothDevice) {
        synchronized(lock) {
            if (device.address !in bondedIds()) {
                discovered[device.address] = device
                emit(scanning)
            }
        }
    }

    private fun bondedIds(): Set<String> {
        return try {
            context.bluetoothAdapter().bondedDevices.map { it.address }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    /** Must be called with `lock` held. */
    private fun emit(scanning: Boolean) {
        val bonded = bondedIds()
        val pairedMoga = try {
            context.bluetoothAdapter().bondedDevices
                .filter(MogaDevices::isMogaDevice)
                .sortedBy { it.name.orEmpty() }
                .map { MogaDevices.info(it, bonded = true) }
        } catch (_: Exception) {
            emptyList()
        }
        publish(
            mapOf(
                "devices" to discovered.values
                    .filter { it.address !in bonded }
                    .sortedBy { it.name.orEmpty() }
                    .map { MogaDevices.info(it, bonded = false) },
                "bondedDevices" to pairedMoga,
                "scanning" to scanning,
            ),
        )
    }

    private fun filter() = IntentFilter().apply {
        addAction(BluetoothDevice.ACTION_FOUND)
        addAction(BluetoothDevice.ACTION_NAME_CHANGED)
        addAction(BluetoothDevice.ACTION_UUID)
        addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
    }

    private companion object {
        const val UUID_LOOKUP_GRACE_MILLIS = 5_000L
    }
}
