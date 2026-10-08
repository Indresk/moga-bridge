package dev.mogabridge.app.moga

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.ParcelUuid
import java.util.UUID

/**
 * The ways the legacy app (and one extra) opens an RFCOMM socket. Names match
 * `RfcommStrategy` in drivers/mod.rs. Hidden APIs may be blocked on newer Android, in which
 * case the factory throws and the caller moves on to the next strategy.
 */
internal object RfcommSockets {
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun create(device: BluetoothDevice, strategy: String): BluetoothSocket {
        return when (strategy) {
            "reflectedSocketConstructor" -> reflectedConstructor(device)
            "reflectedChannelOne" -> reflectedChannelOne(device, "createRfcommSocket")
            "reflectedInsecureChannelOne" -> reflectedChannelOne(device, "createInsecureRfcommSocket")
            "publicInsecureSpp" -> device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            "publicSecureSpp" -> device.createRfcommSocketToServiceRecord(SPP_UUID)
            else -> throw IllegalArgumentException("Unsupported RFCOMM strategy: $strategy")
        }
    }

    /** Legacy: the hidden 7-argument `BluetoothSocket` constructor (blocked on Android 16). */
    private fun reflectedConstructor(device: BluetoothDevice): BluetoothSocket {
        val intType = Int::class.javaPrimitiveType
            ?: throw NoSuchMethodException("Primitive int class is unavailable.")
        val boolType = Boolean::class.javaPrimitiveType
            ?: throw NoSuchMethodException("Primitive boolean class is unavailable.")
        val socketType = BluetoothSocket::class.java.getDeclaredField("TYPE_RFCOMM")
            .apply { isAccessible = true }
            .getInt(null)
        val constructor = BluetoothSocket::class.java.getDeclaredConstructor(
            intType,
            intType,
            boolType,
            boolType,
            BluetoothDevice::class.java,
            intType,
            ParcelUuid::class.java,
        ).apply { isAccessible = true }
        return constructor.newInstance(
            socketType,
            -1,
            false,
            true,
            device,
            -1,
            ParcelUuid(SPP_UUID),
        ) as BluetoothSocket
    }

    /** Hidden `createRfcommSocket(1)` / `createInsecureRfcommSocket(1)`: the legacy fixed channel. */
    private fun reflectedChannelOne(device: BluetoothDevice, methodName: String): BluetoothSocket {
        val portType = Int::class.javaPrimitiveType
            ?: throw NoSuchMethodException("Primitive int class is unavailable.")
        val method = BluetoothDevice::class.java.getMethod(methodName, portType)
        return method.invoke(device, 1) as BluetoothSocket
    }
}
