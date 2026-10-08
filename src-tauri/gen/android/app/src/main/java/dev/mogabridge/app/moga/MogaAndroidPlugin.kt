package dev.mogabridge.app.moga

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import app.tauri.annotation.Command
import app.tauri.annotation.InvokeArg
import app.tauri.annotation.Permission
import app.tauri.annotation.PermissionCallback
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Invoke
import app.tauri.plugin.Plugin
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

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
internal class OutputModeArgs {
    lateinit var mode: String
}

@InvokeArg
internal class StickLayoutArgs {
    lateinit var layout: String
}

@InvokeArg
internal class IsolatedArgs {
    var isolated: Boolean = false
}

@InvokeArg
internal class StateArgs {
    lateinit var state: org.json.JSONObject
}

/**
 * Tauri command layer for Android. Each command validates input and delegates:
 * discovery -> [DeviceDiscovery], pairing -> [DeviceBonding], sockets -> [RfcommConnector],
 * the open link -> [ControllerLink], settings -> [MogaPreferences], virtual gamepad ->
 * [UinputBridge] (+ [HelperFiles]), keyboard output -> [MogaInputMethodService],
 * notification -> [MogaConnectionService]. Command names match drivers/android.rs.
 */
@TauriPlugin(
    permissions = [
        Permission(strings = [Manifest.permission.BLUETOOTH_CONNECT], alias = "bluetoothConnect"),
        Permission(strings = [Manifest.permission.BLUETOOTH_SCAN], alias = "bluetoothScan"),
        Permission(strings = [Manifest.permission.ACCESS_FINE_LOCATION], alias = "bluetoothLocation"),
        Permission(strings = [Manifest.permission.POST_NOTIFICATIONS], alias = "notifications"),
    ],
)
class MogaAndroidPlugin(private val activity: Activity) : Plugin(activity) {
    private val preferences = MogaPreferences(activity)
    private val helperFiles = HelperFiles(activity)
    private val uinputBridge = UinputBridge(tokenProvider = helperFiles::readToken)
    private val link = ControllerLink()
    private val connector = RfcommConnector()
    private val bonding = DeviceBonding(activity)
    private val discovery = DeviceDiscovery(activity) { update ->
        triggerObject(DISCOVERY_EVENT, update)
    }

    /** Bumped by every close; connection attempts compare it to know they were cancelled. */
    private val generation = AtomicInteger()
    private val connectionLock = Any()
    private val readExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "moga-rfcomm-read")
    }

    // Test mode: the virtual gamepad is silenced so the controller can be tried inside this
    // app without reaching Android. In memory only; leaving the screen switches it off.
    @Volatile private var isolated = false

    init {
        instance = this
        uinputBridge.stickLayout = preferences.stickLayout
        try {
            helperFiles.prepare()
        } catch (error: Exception) {
            Log.w(TAG_UINPUT, "Could not prepare the helper files: ${error.message}")
        }
    }

    // ---- Lifecycle -------------------------------------------------------------------------

    override fun onPause(activity: AppCompatActivity) {
        // The app left the screen (another app, notification shade, dialogs): stop isolating
        // so the controller reaches whatever the user switched to.
        isolated = false
        applyIsolation()
        super.onPause(activity)
    }

    override fun onResume(activity: AppCompatActivity) {
        uinputBridge.kick("app resumed")
        super.onResume(activity)
    }

    override fun onDestroy(activity: AppCompatActivity) {
        discovery.stop()
        closeConnection()
        MogaConnectionService.stop(activity)
        instance = null
        readExecutor.shutdownNow()
        super.onDestroy(activity)
    }

    // ---- Permissions -----------------------------------------------------------------------

    @Command
    fun requestBluetoothPermission(invoke: Invoke) {
        val aliases = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                arrayOf("bluetoothConnect", "bluetoothScan", "notifications")
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                arrayOf("bluetoothConnect", "bluetoothScan")
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> arrayOf("bluetoothLocation")
            else -> emptyArray()
        }
        // The notification permission is optional: denying it only hides the notification.
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
        val denied = required.filter { !isGranted(it) }
        if (denied.isEmpty()) {
            invoke.resolve()
        } else {
            invoke.reject("Bluetooth permissions were denied: ${denied.joinToString()}")
        }
    }

    // ---- Discovery -------------------------------------------------------------------------

    @Command
    fun scan(invoke: Invoke) {
        try {
            requireBluetoothPermission()
            val adapter = activity.bluetoothAdapter()
            if (!adapter.isEnabled) throw IllegalStateException("Bluetooth is turned off.")
            invoke.resolveObject(
                adapter.bondedDevices
                    .sortedBy { it.name.orEmpty() }
                    .filter(MogaDevices::isMogaDevice)
                    .map { MogaDevices.info(it, bonded = true) },
            )
        } catch (error: Exception) {
            invoke.reject(error.message ?: "Could not list paired Bluetooth devices.", error)
        }
    }

    @Command
    fun scanUnpairedDevices(invoke: Invoke) {
        try {
            requireDiscoveryPermission()
            val adapter = activity.bluetoothAdapter()
            if (!adapter.isEnabled) throw IllegalStateException("Bluetooth is turned off.")
            discovery.start(adapter)
            invoke.resolve()
        } catch (error: Exception) {
            discovery.stop()
            invoke.reject(error.message ?: "Could not start Bluetooth discovery.", error)
        }
    }

    @Command
    fun stopDeviceScan(invoke: Invoke) {
        try {
            discovery.stop()
            invoke.resolve()
        } catch (error: Exception) {
            invoke.reject(error.message ?: "Could not stop Bluetooth discovery.", error)
        }
    }

    // ---- Connection ------------------------------------------------------------------------

    @Command
    fun connect(invoke: Invoke) {
        val args = invoke.parseArgs(ConnectArgs::class.java)
        // Started here, while the app is visible: Android only allows foreground services
        // to start from the foreground.
        MogaConnectionService.show(activity, args.deviceId, connected = false)
        Thread(
            {
                try {
                    openConnection(args)
                    invoke.resolve()
                } catch (error: Exception) {
                    closeConnection()
                    MogaConnectionService.stop(activity)
                    invoke.reject(error.message ?: "Could not open the MOGA RFCOMM connection.", error)
                }
            },
            "moga-rfcomm-connect",
        ).start()
    }

    /** Pair if needed, open the socket, then start the reader, bridge and notification. */
    private fun openConnection(args: ConnectArgs) {
        requireBluetoothPermission()
        closeConnection()
        val attempt = generation.get()
        val cancelled = { generation.get() != attempt }
        link.resetError()

        val adapter = activity.bluetoothAdapter()
        if (!adapter.isEnabled) throw IllegalStateException("Bluetooth is turned off.")
        val device = adapter.getRemoteDevice(args.deviceId)

        discovery.stop()
        if (bonding.ensureBonded(device, cancelled)) discovery.republish()
        if (cancelled()) throw IOException("The Bluetooth connection was cancelled.")

        val socket = connector.connect(adapter, device, args.strategies, cancelled)
        synchronized(connectionLock) {
            if (cancelled()) {
                socket.close()
                throw IOException("The Bluetooth connection was cancelled.")
            }
            link.attach(socket)
        }

        MogaConnectionService.show(activity, device.name ?: device.address, connected = true)
        syncBridge()
        applyIsolation()
        link.startReader(socket) {
            uinputBridge.release()
            MogaConnectionService.stop(activity)
        }
    }

    @Command
    fun read(invoke: Invoke) {
        readExecutor.execute {
            try {
                val chunk = link.take()
                if (chunk.isEmpty()) {
                    invoke.resolveObject(
                        mapOf("bytes" to emptyList<Int>(), "connected" to false, "error" to link.error),
                    )
                } else {
                    invoke.resolveObject(mapOf("bytes" to chunk, "connected" to true, "error" to null))
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
            val bytes = invoke.parseArgs(ByteArrayArgs::class.java).bytes
            link.write(bytes.map { it.toByte() }.toByteArray())
            invoke.resolve()
        } catch (error: Exception) {
            invoke.reject(error.message ?: "Could not write to the MOGA controller.", error)
        }
    }

    @Command
    fun disconnect(invoke: Invoke) {
        closeConnection()
        MogaConnectionService.stop(activity)
        invoke.resolve()
    }

    /** Cancel any attempt in progress and tear everything down. Also used by the notification. */
    internal fun closeConnection() {
        synchronized(connectionLock) {
            generation.incrementAndGet()
            bonding.cancel()
            connector.cancel()
            link.close()
            uinputBridge.release()
        }
    }

    // ---- Output ----------------------------------------------------------------------------

    @Command
    fun dispatchState(invoke: Invoke) {
        try {
            val state = invoke.parseArgs(StateArgs::class.java).state
            when (preferences.outputMode) {
                OutputModes.GAMEPAD -> uinputBridge.dispatch(state)
                else -> MogaInputMethodService.dispatch(state)
            }
            invoke.resolve()
        } catch (error: Exception) {
            invoke.reject(error.message ?: "Could not forward the controller state.", error)
        }
    }

    @Command
    fun getOutputSettings(invoke: Invoke) {
        // The reachability probe opens a socket, which is not allowed on the main thread.
        Thread(
            {
                val helperCommand = try {
                    helperFiles.startCommand()
                } catch (error: Exception) {
                    null
                }
                invoke.resolveObject(
                    mapOf(
                        "mode" to preferences.outputMode,
                        "stickLayout" to preferences.stickLayout,
                        "helperCommand" to helperCommand,
                        "helperReachable" to uinputBridge.isHelperReachable(),
                        "helperPort" to UinputBridge.DEFAULT_PORT,
                        "helperError" to uinputBridge.lastError,
                        "isolated" to isolated,
                    ),
                )
            },
            "moga-output-status",
        ).start()
    }

    @Command
    fun setOutputMode(invoke: Invoke) {
        try {
            preferences.outputMode = invoke.parseArgs(OutputModeArgs::class.java).mode
            syncBridge()
            applyIsolation()
            MogaInputMethodService.mappingChanged()
            invoke.resolve()
        } catch (error: Exception) {
            invoke.reject(error.message ?: "Could not change the output mode.", error)
        }
    }

    @Command
    fun setStickLayout(invoke: Invoke) {
        try {
            val layout = invoke.parseArgs(StickLayoutArgs::class.java).layout
            preferences.stickLayout = layout
            uinputBridge.stickLayout = layout
            invoke.resolve()
        } catch (error: Exception) {
            invoke.reject(error.message ?: "Could not change the stick layout.", error)
        }
    }

    @Command
    fun setInputIsolated(invoke: Invoke) {
        try {
            isolated = invoke.parseArgs(IsolatedArgs::class.java).isolated
            applyIsolation()
            invoke.resolve()
        } catch (error: Exception) {
            invoke.reject(error.message ?: "Could not change the test isolation.", error)
        }
    }

    @Command
    fun getMapping(invoke: Invoke) {
        invoke.resolveObject(preferences.keyMapping())
    }

    @Command
    fun setMapping(invoke: Invoke) {
        try {
            preferences.saveKeyMapping(invoke.parseArgs(MappingArgs::class.java).mapping)
            MogaInputMethodService.mappingChanged()
            invoke.resolve()
        } catch (error: Exception) {
            invoke.reject(error.message ?: "Could not save the keyboard mapping.", error)
        }
    }

    /** The virtual gamepad lives exactly while a controller is connected in gamepad mode. */
    private fun syncBridge() {
        uinputBridge.setActive(link.connected && preferences.outputMode == OutputModes.GAMEPAD)
    }

    private fun applyIsolation() {
        uinputBridge.setSuspended(preferences.outputMode == OutputModes.GAMEPAD && isolated)
    }

    // ---- System settings screens -----------------------------------------------------------

    @Command
    fun openBatterySettings(invoke: Invoke) {
        try {
            // The list of battery-optimisation exceptions; where that screen is missing, fall
            // back to this app's own settings page.
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            val target = if (intent.resolveActivity(activity.packageManager) != null) {
                intent
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.parse("package:${activity.packageName}"))
            }
            activity.startActivity(target)
            invoke.resolve()
        } catch (error: Exception) {
            invoke.reject(error.message ?: "Could not open the battery settings.", error)
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

    // ---- Permission checks -----------------------------------------------------------------

    private fun isGranted(permission: String): Boolean =
        activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun requireBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !isGranted(Manifest.permission.BLUETOOTH_CONNECT)
        ) {
            throw SecurityException("Grant Nearby devices permission before using Bluetooth.")
        }
    }

    private fun requireDiscoveryPermission() {
        requireBluetoothPermission()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !isGranted(Manifest.permission.BLUETOOTH_SCAN)
        ) {
            throw SecurityException("Grant Bluetooth scan permission before discovering devices.")
        }
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.M until Build.VERSION_CODES.S &&
            !isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        ) {
            throw SecurityException("Grant location permission for Bluetooth discovery on this Android version.")
        }
    }

    companion object {
        private const val TAG_UINPUT = "MogaUinput"
        private const val DISCOVERY_EVENT = "moga-discovered-devices"

        @Volatile private var instance: MogaAndroidPlugin? = null

        /** Called by the notification's "Disconnect" action. */
        fun requestDisconnect() {
            instance?.closeConnection()
        }
    }
}
