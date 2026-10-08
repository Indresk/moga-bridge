package dev.mogabridge.app.moga

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

/** Recognising MOGA controllers among Bluetooth devices. */
internal object MogaDevices {
    /**
     * Mode A names: the Pocket advertises "BD&A" (legacy app: exact match; moga-uinput: prefix
     * "BD&A"/"BDA"), the Pro "Moga Pro"/"MOGA ...". Names containing "HID" are Mode B (standard
     * HID) and must not be driven through this proprietary protocol.
     */
    fun isMogaName(name: String?): Boolean {
        val upper = name?.trim()?.uppercase() ?: return false
        if ("HID" in upper) return false
        return upper.startsWith("BD&A") || upper.startsWith("BDA") || upper.startsWith("MOGA")
    }

    fun isMogaDevice(device: BluetoothDevice): Boolean {
        return try {
            isMogaName(device.name)
        } catch (_: SecurityException) {
            false
        }
    }

    /** The shape the frontend expects (`DeviceInfo` in schemas/device.rs). */
    fun info(device: BluetoothDevice, bonded: Boolean): Map<String, Any> {
        return mapOf(
            "id" to device.address,
            "name" to (device.name ?: device.address),
            "bonded" to bonded,
        )
    }
}

internal fun Context.bluetoothAdapter(): BluetoothAdapter {
    val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        ?: throw IllegalStateException("Bluetooth is not available on this device.")
    return manager.adapter
        ?: throw IllegalStateException("Bluetooth is not available on this device.")
}

internal fun Intent.bluetoothDevice(): BluetoothDevice? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }
}

/** Register for Bluetooth system broadcasts (Android 13+ requires an explicit export flag). */
internal fun Context.registerBluetoothReceiver(receiver: BroadcastReceiver, filter: IntentFilter) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
    } else {
        @Suppress("DEPRECATION")
        registerReceiver(receiver, filter)
    }
}
