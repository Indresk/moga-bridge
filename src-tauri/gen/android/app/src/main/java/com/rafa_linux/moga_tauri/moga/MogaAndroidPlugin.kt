package com.rafa_linux.moga_tauri.moga

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.provider.Settings
import android.util.Log
import app.tauri.annotation.Command
import app.tauri.annotation.InvokeArg
import app.tauri.annotation.Permission
import app.tauri.annotation.PermissionCallback
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Invoke
import app.tauri.plugin.Plugin
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@InvokeArg
internal class ConnectArgs {
    lateinit var deviceId: String
    lateinit var strategies: List<String>
}

@InvokeArg
internal class ByteArrayArgs {
    lateinit var bytes: List<Int>
}

@InvokeArg
internal class MappingArgs {
    lateinit var mapping: Map<String, Int>
}

@InvokeArg
internal class StateArgs {
    lateinit var state: org.json.JSONObject
}

@TauriPlugin(
    permissions = [
        Permission(
            strings = [Manifest.permission.BLUETOOTH_CONNECT],
            alias = "bluetoothConnect",
        ),
        Permission(
            strings = [Manifest.permission.BLUETOOTH_SCAN],
            alias = "bluetoothScan",
        ),
        Permission(
            strings = [Manifest.permission.ACCESS_FINE_LOCATION],
            alias = "bluetoothLocation",
        ),
    ],
)
class MogaAndroidPlugin(private val activity: Activity) : Plugin(activity) {
    @Volatile private var socket: android.bluetooth.BluetoothSocket? = null
    @Volatile private var pendingSocket: android.bluetooth.BluetoothSocket? = null
    @Volatile private var connected = false
    @Volatile private var connectionError: String? = null
    private val incoming = LinkedBlockingQueue<List<Int>>(128)
    private val connectionLock = Any()
    private val scanLock = Any()
    private val discoveredDevices = linkedMapOf<String, BluetoothDevice>()
    private val scanHandler = Handler(Looper.getMainLooper())
    @Volatile private var scanReceiver: BroadcastReceiver? = null
    @Volatile private var scanning = false
    @Volatile private var bondCancelled = false
    @Volatile private var bondLatch: CountDownLatch? = null
    @Volatile private var connectionGeneration = 0
    private val readExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "moga-rfcomm-read")
    }

    @Command
    fun requestBluetoothPermission(invoke: Invoke) {
        val aliases = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                arrayOf("bluetoothConnect", "bluetoothScan")
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                arrayOf("bluetoothLocation")
            else -> emptyArray()
        }
        requestPermissionForAliases(aliases, invoke, "bluetoothPermissionResult")
    }

    @PermissionCallback
    fun bluetoothPermissionResult(invoke: Invoke) {
        val required = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                listOf(Manifest.permission.ACCESS_FINE_LOCATION)
            else -> emptyList()
        }
        val denied = required.filter {
            activity.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (denied.isEmpty()) {
            invoke.resolve()
        } else {
            invoke.reject("Bluetooth permissions were denied: ${denied.joinToString()}")
        }
    }

    @Command
    fun scan(invoke: Invoke) {
        try {
            requireBluetoothPermission()
            val adapter = bluetoothAdapter()
            if (!adapter.isEnabled) {
                throw IllegalStateException("Bluetooth is turned off.")
            }
            val devices = adapter.bondedDevices
                .sortedBy { it.name.orEmpty() }
                .filter { isMogaDevice(it) }
                .map { deviceInfo(it, bonded = true) }
            invoke.resolveObject(devices)
        } catch (error: Exception) {
            invoke.reject(error.message ?: "Could not list paired Bluetooth devices.", error)
        }
    }

    @Command
    fun scanUnpairedDevices(invoke: Invoke) {
        try {
            requireDiscoveryPermission()
            val adapter = bluetoothAdapter()
            if (!adapter.isEnabled) {
                throw IllegalStateException("Bluetooth is turned off.")
            }

            stopDeviceScanInternal()
            synchronized(scanLock) {
                discoveredDevices.clear()
                scanning = true
                val pairedIds = adapter.bondedDevices.map { it.address }.toSet()
                adapter.bondedDevices
                    .filter(::isMogaDevice)
                    .forEach { discoveredDevices[it.address] = it }
                emitDiscoveredDevices(pairedIds, scanning = true)
            }

            val receiver = createDiscoveryReceiver(adapter)
            registerReceiverCompat(receiver, discoveryFilter())
            synchronized(scanLock) {
                scanReceiver = receiver
            }
            if (!adapter.startDiscovery()) {
                stopDeviceScanInternal()
                throw IllegalStateException("Android could not start Bluetooth discovery.")
            }
            invoke.resolve()
        } catch (error: Exception) {
            stopDeviceScanInternal()
            invoke.reject(error.message ?: "Could not start Bluetooth discovery.", error)
        }
    }

    @Command
    fun stopDeviceScan(invoke: Invoke) {
        try {
            stopDeviceScanInternal()
            invoke.resolve()
        } catch (error: Exception) {
            invoke.reject(error.message ?: "Could not stop Bluetooth discovery.", error)
        }
    }

    @Command
    fun connect(invoke: Invoke) {
        val args = invoke.parseArgs(ConnectArgs::class.java)
        Thread(
            {
                try {
                    requireBluetoothPermission()
                    closeConnection()
                    val generation = connectionGeneration
                    connectionError = null
                    val adapter = bluetoothAdapter()
                    if (!adapter.isEnabled) {
                        throw IllegalStateException("Bluetooth is turned off.")
                    }

                    val device = adapter.getRemoteDevice(args.deviceId)
                    stopDeviceScanInternal()
                    ensureBonded(device, generation)
                    synchronized(connectionLock) {
                        if (connectionGeneration != generation) {
                            throw IOException("The Bluetooth connection was cancelled.")
                        }
                    }
                    val newSocket = connectWithLegacyFallbacks(
                        adapter,
                        device,
                        generation,
                        args.strategies,
                    )

                    synchronized(connectionLock) {
                        if (connectionGeneration != generation) {
                            throw IOException("The Bluetooth connection was cancelled.")
                        }
                        socket = newSocket
                        connected = true
                        connectionError = null
                        incoming.clear()
                    }
                    startReader(newSocket)
                    invoke.resolve()
                } catch (error: Exception) {
                    closeConnection()
                    invoke.reject(error.message ?: "Could not open the MOGA RFCOMM connection.", error)
                }
            },
            "moga-rfcomm-connect",
        ).start()
    }

    @Command
    fun read(invoke: Invoke) {
        readExecutor.execute {
            try {
                val chunk = incoming.take()
                if (chunk.isEmpty()) {
                    invoke.resolveObject(
                        mapOf(
                            "bytes" to emptyList<Int>(),
                            "connected" to false,
                            "error" to connectionError,
                        ),
                    )
                } else {
                    invoke.resolveObject(
                        mapOf("bytes" to chunk, "connected" to true, "error" to null),
                    )
                }
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                invoke.resolveObject(
                    mapOf(
                        "bytes" to emptyList<Int>(),
                        "connected" to false,
                        "error" to "RFCOMM read was interrupted.",
                    ),
                )
            } catch (error: Exception) {
                invoke.reject(error.message ?: "Could not read from the MOGA controller.", error)
            }
        }
    }

    @Command
    fun write(invoke: Invoke) {
        try {
            requireBluetoothPermission()
            val args = invoke.parseArgs(ByteArrayArgs::class.java)
            val current = socket ?: throw IOException("The MOGA RFCOMM socket is not connected.")
            synchronized(connectionLock) {
                current.outputStream.write(args.bytes.map { it.toByte() }.toByteArray())
                current.outputStream.flush()
            }
            invoke.resolve()
        } catch (error: Exception) {
            invoke.reject(error.message ?: "Could not write to the MOGA controller.", error)
        }
    }

    @Command
    fun setMapping(invoke: Invoke) {
        try {
            val mapping = invoke.parseArgs(MappingArgs::class.java).mapping
            if (mapping.values.any { it !in 0..288 }) {
                throw IllegalArgumentException("Android key codes must be between 0 and 288.")
            }
            activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply {
                    mapping.forEach { (control, keyCode) -> putInt(control, keyCode) }
                }
                .apply()
            MogaInputMethodService.mappingChanged()
            invoke.resolve()
        } catch (error: Exception) {
            invoke.reject(error.message ?: "Could not save the keyboard mapping.", error)
        }
    }

    @Command
    fun getMapping(invoke: Invoke) {
        val preferences = activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val mapping = MAPPING_CONTROLS.associateWith { control ->
            preferences.getInt(control, DEFAULT_MAPPING[control] ?: 0)
        }
        invoke.resolveObject(mapping)
    }

    @Command
    fun dispatchState(invoke: Invoke) {
        try {
            val state = invoke.parseArgs(StateArgs::class.java).state
            MogaInputMethodService.dispatch(state)
            invoke.resolve()
        } catch (error: Exception) {
            invoke.reject(error.message ?: "Could not dispatch controller state to the IME.", error)
        }
    }

    @Command
    fun openImeSettings(invoke: Invoke) {
        try {
            activity.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            invoke.resolve()
        } catch (error: Exception) {
            invoke.reject(error.message ?: "Could not open Android keyboard settings.", error)
        }
    }

    @Command
    fun disconnect(invoke: Invoke) {
        closeConnection()
        invoke.resolve()
    }

    override fun onDestroy(activity: androidx.appcompat.app.AppCompatActivity) {
        stopDeviceScanInternal()
        closeConnection()
        readExecutor.shutdownNow()
        super.onDestroy(activity)
    }

    private fun createDiscoveryReceiver(adapter: BluetoothAdapter): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = intent.bluetoothDevice() ?: return
                        if (device.bondState == BluetoothDevice.BOND_BONDED) return
                        // device.name is often still null at FOUND time; the legacy app relied on
                        // the inquiry name, which Android delivers in EXTRA_NAME.
                        val name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                        if (isMogaName(name) || isMogaDevice(device)) {
                            rememberDiscoveredDevice(adapter, device)
                        }
                        // Deliberately no fetchUuidsWithSdp() here: SDP traffic during inquiry
                        // slows discovery and can make the Pocket drop out. The legacy app
                        // matched by name only.
                    }
                    BluetoothDevice.ACTION_NAME_CHANGED -> {
                        val device = intent.bluetoothDevice() ?: return
                        val name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                        if (
                            device.bondState != BluetoothDevice.BOND_BONDED &&
                            (isMogaName(name) || isMogaDevice(device))
                        ) {
                            rememberDiscoveredDevice(adapter, device)
                        }
                    }
                    BluetoothDevice.ACTION_UUID -> {
                        val device = intent.bluetoothDevice() ?: return
                        val uuids = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID)
                            ?.filterIsInstance<ParcelUuid>()
                            .orEmpty()
                        if (
                            device.bondState != BluetoothDevice.BOND_BONDED &&
                            uuids.any { it.uuid == SPP_UUID }
                        ) {
                            rememberDiscoveredDevice(adapter, device)
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        scanning = false
                        val pairedIds = try {
                            adapter.bondedDevices.map { it.address }.toSet()
                        } catch (_: SecurityException) {
                            emptySet()
                        }
                        synchronized(scanLock) {
                            emitDiscoveredDevices(pairedIds, scanning = false)
                        }
                        scanHandler.postDelayed(
                            { stopDeviceScanInternal() },
                            UUID_LOOKUP_GRACE_MILLIS,
                        )
                    }
                }
            }
        }
    }

    private fun rememberDiscoveredDevice(adapter: BluetoothAdapter, device: BluetoothDevice) {
        val bondedIds = try {
            adapter.bondedDevices.map { it.address }.toSet()
        } catch (_: SecurityException) {
            emptySet()
        }
        synchronized(scanLock) {
            if (device.address !in bondedIds) {
                discoveredDevices[device.address] = device
                emitDiscoveredDevices(bondedIds, scanning = scanning)
            }
        }
    }

    private fun emitDiscoveredDevices(bondedIds: Set<String>, scanning: Boolean) {
        triggerObject(
            "moga-discovered-devices",
            mapOf(
                "devices" to discoveredDevices.values
                    .filter { it.address !in bondedIds }
                    .sortedBy { it.name.orEmpty() }
                    .map { deviceInfo(it, bonded = false) },
                "bondedDevices" to try {
                    bluetoothAdapter().bondedDevices
                        .filter(::isMogaDevice)
                        .sortedBy { it.name.orEmpty() }
                        .map { deviceInfo(it, bonded = true) }
                } catch (_: Exception) {
                    emptyList<Map<String, Any>>()
                },
                "scanning" to scanning,
            ),
        )
    }

    private fun discoveryFilter(): IntentFilter {
        return IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_NAME_CHANGED)
            addAction(BluetoothDevice.ACTION_UUID)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
    }

    private fun stopDeviceScanInternal() {
        val receiver: BroadcastReceiver?
        val adapter = try {
            bluetoothAdapter()
        } catch (_: Exception) {
            null
        }
        val wasScanning: Boolean
        synchronized(scanLock) {
            receiver = scanReceiver
            scanReceiver = null
            wasScanning = scanning || receiver != null
            scanning = false
        }
        scanHandler.removeCallbacksAndMessages(null)
        try {
            adapter?.cancelDiscovery()
        } catch (_: Exception) {
            Unit
        }
        if (receiver != null) {
            try {
                activity.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
                Unit
            }
        }
        if (wasScanning && adapter != null) {
            val bondedIds = try {
                adapter.bondedDevices.map { it.address }.toSet()
            } catch (_: SecurityException) {
                emptySet()
            }
            synchronized(scanLock) {
                emitDiscoveredDevices(bondedIds, scanning = false)
            }
        }
    }

    private fun ensureBonded(device: BluetoothDevice, generation: Int) {
        if (device.bondState == BluetoothDevice.BOND_BONDED) return
        val finished = CountDownLatch(1)
        if (connectionGeneration != generation) throw IOException("Pairing was cancelled.")
        bondCancelled = false
        bondLatch = finished
        val bondReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
                val changedDevice = intent.bluetoothDevice() ?: return
                if (changedDevice.address == device.address &&
                    changedDevice.bondState != BluetoothDevice.BOND_BONDING
                ) {
                    finished.countDown()
                }
            }
        }

        registerReceiverCompat(
            bondReceiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
        )
        try {
            if (!device.createBond()) {
                throw IOException("Android could not start pairing with ${device.name ?: device.address}.")
            }
            if (!finished.await(BOND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw IOException("Pairing timed out. Confirm the pairing request on Android.")
            }
            if (bondCancelled || connectionGeneration != generation) {
                throw IOException("Pairing was cancelled.")
            }
            if (device.bondState != BluetoothDevice.BOND_BONDED) {
                throw IOException("Pairing was declined or failed. Confirm the Android pairing request.")
            }
            val adapter = bluetoothAdapter()
            val bondedIds = adapter.bondedDevices.map { it.address }.toSet()
            synchronized(scanLock) {
                emitDiscoveredDevices(bondedIds, scanning = scanning)
            }
        } finally {
            bondLatch = null
            try {
                activity.unregisterReceiver(bondReceiver)
            } catch (_: IllegalArgumentException) {
                Unit
            }
        }
    }

    private fun registerReceiverCompat(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            activity.registerReceiver(receiver, filter)
        }
    }

    private fun Intent.bluetoothDevice(): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    private fun isMogaDevice(device: BluetoothDevice): Boolean {
        return try {
            isMogaName(device.name)
        } catch (_: SecurityException) {
            false
        }
    }

    /**
     * Mode A names: Pocket advertises "BD&A" (legacy app: exact match; moga-uinput: prefix
     * "BD&A"/"BDA"), Pro/Pro Power advertise "Moga Pro"/"MOGA ...". "HID" names are Mode B
     * (standard HID) and must not be driven through this proprietary protocol.
     */
    private fun isMogaName(name: String?): Boolean {
        val upper = name?.trim()?.uppercase() ?: return false
        if ("HID" in upper) return false
        return upper.startsWith("BD&A") || upper.startsWith("BDA") || upper.startsWith("MOGA")
    }

    private fun connectWithLegacyFallbacks(
        adapter: BluetoothAdapter,
        device: BluetoothDevice,
        generation: Int,
        strategies: List<String>,
    ): BluetoothSocket {
        adapter.cancelDiscovery()
        val failures = mutableListOf<String>()

        // The legacy app waited (CONNECTION_DELAY_MS = 2000) and looped with a 500 ms pause:
        // the Pocket often refuses the first RFCOMM connect right after bonding/inquiry.
        Thread.sleep(POST_BOND_SETTLE_MILLIS)
        for (round in 1..CONNECT_ROUNDS) {
            for (strategy in strategies) {
            if (connectionGeneration != generation) {
                throw IOException("The Bluetooth connection was cancelled.")
            }

            val candidate = try {
                createSocket(device, strategy)
            } catch (error: Exception) {
                Log.w(TAG, "RFCOMM strategy=$strategy unavailable: ${error.message}")
                failures += "$strategy: ${error.message ?: error.javaClass.simpleName}"
                continue
            }

            synchronized(connectionLock) {
                if (connectionGeneration != generation) {
                    try {
                        candidate.close()
                    } catch (_: IOException) {
                        Unit
                    }
                    throw IOException("The Bluetooth connection was cancelled.")
                }
                pendingSocket = candidate
            }

            try {
                candidate.connect()
                if (connectionGeneration != generation) {
                    candidate.close()
                    throw IOException("The Bluetooth connection was cancelled.")
                }
                Log.i(TAG, "RFCOMM connected with strategy=$strategy (round $round/$CONNECT_ROUNDS)")
                return candidate
            } catch (error: Exception) {
                synchronized(connectionLock) {
                    if (pendingSocket === candidate) pendingSocket = null
                }
                try {
                    candidate.close()
                } catch (_: IOException) {
                    Unit
                }
                if (connectionGeneration != generation) {
                    throw IOException("The Bluetooth connection was cancelled.", error)
                }
                Log.w(TAG, "RFCOMM strategy=$strategy failed (round $round): ${error.message}")
                failures += "$strategy: ${error.message ?: error.javaClass.simpleName}"
            }
            }
            if (round < CONNECT_ROUNDS) Thread.sleep(RECONNECT_DELAY_MILLIS)
        }

        throw IOException(
            "All legacy MOGA RFCOMM connection methods failed. ${failures.joinToString(" | ")}",
        )
    }

    private fun createSocket(device: BluetoothDevice, strategy: String): BluetoothSocket {
        return when (strategy) {
            "reflectedSocketConstructor" -> createLegacySocket(device)
            "reflectedChannelOne" -> createChannelOneSocket(device)
            "reflectedInsecureChannelOne" -> createInsecureChannelOneSocket(device)
            "publicInsecureSpp" -> device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            "publicSecureSpp" -> device.createRfcommSocketToServiceRecord(SPP_UUID)
            else -> throw IllegalArgumentException("Unsupported RFCOMM strategy: $strategy")
        }
    }

    private fun createLegacySocket(device: BluetoothDevice): BluetoothSocket {
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

    private fun createChannelOneSocket(device: BluetoothDevice): BluetoothSocket {
        val portType = Int::class.javaPrimitiveType
            ?: throw NoSuchMethodException("Primitive int class is unavailable.")
        val method = BluetoothDevice::class.java.getMethod("createRfcommSocket", portType)
        return method.invoke(device, 1) as BluetoothSocket
    }

    private fun createInsecureChannelOneSocket(device: BluetoothDevice): BluetoothSocket {
        val portType = Int::class.javaPrimitiveType
            ?: throw NoSuchMethodException("Primitive int class is unavailable.")
        val method = BluetoothDevice::class.java.getMethod("createInsecureRfcommSocket", portType)
        return method.invoke(device, 1) as BluetoothSocket
    }

    private fun deviceInfo(device: BluetoothDevice, bonded: Boolean): Map<String, Any> {
        return mapOf(
            "id" to device.address,
            "name" to (device.name ?: device.address),
            "bonded" to bonded,
        )
    }

    private fun startReader(rfcommSocket: android.bluetooth.BluetoothSocket) {
        Thread(
            {
                val buffer = ByteArray(64)
                try {
                    val input = rfcommSocket.inputStream
                    while (connected && rfcommSocket.isConnected) {
                        val size = input.read(buffer)
                        if (size < 0) break
                        val queued = incoming.offer(
                            buffer.copyOf(size).map { it.toInt() and 0xFF },
                            500,
                            TimeUnit.MILLISECONDS,
                        )
                        if (!queued) {
                            throw IOException("MOGA input queue remained full; controller read stopped.")
                        }
                    }
                } catch (error: Exception) {
                    if (connected) connectionError = error.message ?: "RFCOMM read failed."
                } finally {
                    var wasCurrentSocket = false
                    synchronized(connectionLock) {
                        if (socket === rfcommSocket) {
                            connected = false
                            socket = null
                            wasCurrentSocket = true
                        }
                    }
                    try {
                        rfcommSocket.close()
                    } catch (_: IOException) {
                        connectionError = connectionError ?: "Could not close the RFCOMM socket."
                    }
                    if (wasCurrentSocket) {
                        incoming.clear()
                        incoming.offer(emptyList())
                    }
                }
            },
            "moga-rfcomm-reader",
        ).apply { isDaemon = true }.start()
    }

    private fun closeConnection() {
        val oldSocket: android.bluetooth.BluetoothSocket?
        val connectingSocket: android.bluetooth.BluetoothSocket?
        synchronized(connectionLock) {
            connectionGeneration += 1
            bondCancelled = true
            bondLatch?.countDown()
            connected = false
            oldSocket = socket
            connectingSocket = pendingSocket
            pendingSocket = null
            socket = null
            incoming.clear()
            incoming.offer(emptyList())
        }
        listOfNotNull(oldSocket, connectingSocket).forEach { old ->
            try {
                old.close()
            } catch (error: IOException) {
                connectionError = error.message ?: "Could not close the RFCOMM socket."
            }
        }
    }

    private fun bluetoothAdapter(): BluetoothAdapter {
        val manager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: throw IllegalStateException("Bluetooth is not available on this device.")
        return manager.adapter
            ?: throw IllegalStateException("Bluetooth is not available on this device.")
    }

    private fun requireBluetoothPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("Grant Nearby devices permission before using Bluetooth.")
        }
    }

    private fun requireDiscoveryPermission() {
        requireBluetoothPermission()
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            activity.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("Grant Bluetooth scan permission before discovering devices.")
        }
        if (
            Build.VERSION.SDK_INT in Build.VERSION_CODES.M until Build.VERSION_CODES.S &&
            activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("Grant location permission for Bluetooth discovery on this Android version.")
        }
    }

    companion object {
        private const val TAG = "MogaRfcomm"
        private const val PREFERENCES = "moga-key-mapping"
        private const val BOND_TIMEOUT_SECONDS = 90L
        private const val POST_BOND_SETTLE_MILLIS = 2_000L
        private const val RECONNECT_DELAY_MILLIS = 500L
        private const val CONNECT_ROUNDS = 3
        private const val UUID_LOOKUP_GRACE_MILLIS = 5_000L
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private val MAPPING_CONTROLS = listOf(
            "buttonA", "buttonB", "buttonX", "buttonY",
            "start", "select", "leftBumper", "rightBumper",
            "up", "down", "left", "right",
            "rightUp", "rightDown", "rightLeft", "rightRight",
        )
        private val DEFAULT_MAPPING = mapOf(
            "buttonA" to 62,
            "start" to 66,
            "up" to 19,
            "down" to 20,
            "left" to 21,
            "right" to 22,
        )
    }
}
