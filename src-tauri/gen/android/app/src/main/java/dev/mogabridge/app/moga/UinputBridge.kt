package dev.mogabridge.app.moga

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Virtual gamepad for other apps (PPSSPP, ...) without root.
 *
 * A normal app cannot open /dev/uinput, but Android's `shell` user can, and ships the
 * `/system/bin/uinput` tool, which reads JSON commands (register / inject) from stdin.
 * A helper started once per boot from adb exposes that tool on a loopback socket:
 *
 *     toybox nc -s 127.0.0.1 -p 7777 -L uinput -
 *
 * This class connects to that socket, registers a standard gamepad and streams the MOGA
 * state to it. Android then sees a real GAMEPAD|JOYSTICK input device, so games need no
 * key mapping and the d-pad/axes keep their native semantics.
 *
 * All socket work happens on one background thread (network access is forbidden on the
 * main thread) and only the newest state is kept if the socket is slow.
 */
internal class UinputBridge(
    private val port: Int = DEFAULT_PORT,
    private val tokenProvider: () -> String? = { null },
) {
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "moga-uinput").apply { isDaemon = true }
    }
    private val pendingState = AtomicReference<JSONObject?>(null)

    // The fields below are only touched from the executor thread.
    private var socket: Socket? = null
    private var output: OutputStream? = null
    private var lastValues = HashMap<Int, Int>()
    private var nextAttemptAtMillis = 0L
    private var watchdog: ScheduledFuture<*>? = null

    /** The state to replay after reconnecting, so the device comes back exactly as it was. */
    private var lastState: JSONObject = NEUTRAL_STATE

    @Volatile var lastError: String? = null
        private set

    @Volatile private var suspended = false

    /** True while a controller is connected and the virtual gamepad is the selected output. */
    @Volatile private var active = false

    /** The newest state reported by the controller, kept even while suspended or inactive. */
    @Volatile private var latestReported: JSONObject = NEUTRAL_STATE

    /**
     * `analogs`, `leftDpad` or `rightDpad`; see [StickLayouts]. Changing it re-sends the current
     * state, because identical controller reports are filtered out upstream and no new report
     * may come for a while.
     */
    @Volatile var stickLayout: String = StickLayouts.ANALOGS
        set(value) {
            field = value
            resend()
        }

    /**
     * Turn the virtual gamepad on or off. While active, a watchdog keeps the helper connection
     * (and so the virtual device) alive: it is rebuilt on its own if it drops, instead of
     * waiting for the next controller report.
     */
    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        executor.execute {
            if (value) {
                watchdog = executor.scheduleWithFixedDelay(
                    { maintain("watchdog") },
                    WATCHDOG_MS,
                    WATCHDOG_MS,
                    TimeUnit.MILLISECONDS,
                )
                maintain("activated")
            } else {
                watchdog?.cancel(false)
                watchdog = null
                pendingState.set(null)
                lastState = NEUTRAL_STATE
                closeSocket("deactivated")
            }
        }
    }

    /** Re-check the connection now (e.g. when the app returns to the foreground). */
    fun kick(reason: String) {
        if (active && !executor.isShutdown) executor.execute { maintain(reason) }
    }

    /**
     * While suspended nothing reaches Android; held buttons and sticks are released once so
     * nothing stays stuck. Used to keep the controller from navigating this app's own UI.
     */
    fun setSuspended(value: Boolean) {
        if (suspended == value) return
        suspended = value
        Log.i(TAG, "Virtual gamepad ${if (value) "suspended" else "resumed"}")
        if (value) {
            pendingState.set(null)
            executor.execute {
                lastState = NEUTRAL_STATE
                guardedSend(NEUTRAL_STATE)
            }
        } else {
            resend() // identical reports are filtered upstream: restore held inputs now
        }
    }

    /** Send the state that should currently be visible to Android. */
    private fun resend() {
        if (!active || executor.isShutdown) return
        executor.execute {
            lastState = if (suspended) NEUTRAL_STATE else latestReported
            guardedSend(lastState)
        }
    }

    /** Queue the newest controller state; older queued states are dropped. */
    fun dispatch(state: JSONObject) {
        latestReported = state
        if (suspended || !active || executor.isShutdown) return
        pendingState.set(state)
        executor.execute {
            val latest = pendingState.getAndSet(null) ?: return@execute
            lastState = latest
            guardedSend(latest)
        }
    }

    /** Stop the virtual gamepad and remove its device (e.g. when the controller disconnects). */
    fun release() = setActive(false)

    /** Final cleanup when the plugin is destroyed: drop the device and stop the worker thread. */
    fun shutdown() {
        active = false
        executor.execute {
            watchdog?.cancel(false)
            watchdog = null
            closeSocket("shutdown")
        }
        executor.shutdown()
    }

    /** True when the adb-started helper accepts connections on the loopback port. */
    fun isHelperReachable(): Boolean {
        val result = executor.submit<Boolean> {
            if (socket != null) return@submit true
            try {
                Socket().use { probe -> probe.connect(InetSocketAddress(LOOPBACK, port), CONNECT_TIMEOUT_MS) }
                true
            } catch (error: IOException) {
                false
            }
        }
        return try {
            result.get(REACHABLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (error: Exception) {
            result.cancel(true)
            false
        }
    }

    /** Make sure the device exists; replay the last state if it had to be rebuilt. */
    private fun maintain(reason: String) {
        if (!active || socket != null) return
        if (ensureConnected(reason)) guardedSend(lastState)
    }

    private fun guardedSend(state: JSONObject) {
        try {
            send(state)
        } catch (error: IOException) {
            fail("Virtual gamepad helper write failed: ${error.message}")
        }
    }

    private fun send(state: JSONObject) {
        if (!ensureConnected()) return

        val buttons = state.optJSONObject("buttons") ?: JSONObject()
        val leftStick = state.optJSONObject("leftStick") ?: JSONObject()
        val rightStick = state.optJSONObject("rightStick") ?: JSONObject()

        val wanted = linkedMapOf(
            BTN_A to buttons.optBoolean("a").toInt(),
            BTN_B to buttons.optBoolean("b").toInt(),
            BTN_X to buttons.optBoolean("x").toInt(),
            BTN_Y to buttons.optBoolean("y").toInt(),
            BTN_TL to buttons.optBoolean("leftBumper").toInt(),
            BTN_TR to buttons.optBoolean("rightBumper").toInt(),
            BTN_SELECT to buttons.optBoolean("select").toInt(),
            BTN_START to buttons.optBoolean("start").toInt(),
        )
        // Rust already normalised the sticks to -127..127 (x right-positive, y down-positive).
        // A stick turned into a D-pad stops driving its analog axes and feeds the hat from
        // the controller's own digitised direction bits.
        val layout = stickLayout
        val leftIsDpad = layout == StickLayouts.LEFT_DPAD
        val rightIsDpad = layout == StickLayouts.RIGHT_DPAD
        val axes = linkedMapOf(
            ABS_X to (if (leftIsDpad) 0 else leftStick.optInt("x")),
            ABS_Y to (if (leftIsDpad) 0 else leftStick.optInt("y")),
            ABS_RX to (if (rightIsDpad) 0 else rightStick.optInt("x")),
            ABS_RY to (if (rightIsDpad) 0 else rightStick.optInt("y")),
        )
        val dpadSource = when {
            leftIsDpad -> leftStick
            rightIsDpad -> rightStick
            else -> JSONObject()
        }
        axes[ABS_HAT0X] = (if (dpadSource.optBoolean("right")) 1 else 0) -
            (if (dpadSource.optBoolean("left")) 1 else 0)
        axes[ABS_HAT0Y] = (if (dpadSource.optBoolean("down")) 1 else 0) -
            (if (dpadSource.optBoolean("up")) 1 else 0)

        val events = JSONArray()
        wanted.forEach { (code, value) ->
            if (lastValues[code] != value) {
                addEvent(events, EV_KEY, code, value)
                lastValues[code] = value
            }
        }
        axes.forEach { (code, value) ->
            if (lastValues[code] != value) {
                addEvent(events, EV_ABS, code, value)
                lastValues[code] = value
            }
        }
        if (events.length() == 0) return

        addEvent(events, EV_SYN, 0, 0)
        writeLine(JSONObject().put("id", DEVICE_ID).put("command", "inject").put("events", events))
    }

    private fun ensureConnected(reason: String = "report"): Boolean {
        if (socket != null) return true
        val now = System.currentTimeMillis()
        if (now < nextAttemptAtMillis) return false
        try {
            val candidate = Socket()
            candidate.tcpNoDelay = true
            candidate.connect(InetSocketAddress(LOOPBACK, port), CONNECT_TIMEOUT_MS)
            socket = candidate
            output = candidate.getOutputStream()
            lastValues = HashMap()
            // The helper drops any connection whose first line is not the shared secret.
            val token = tokenProvider() ?: throw IOException("helper token unavailable")
            output!!.write((token + "\n").toByteArray(Charsets.UTF_8))
            writeLine(registerCommand())
            lastError = null
            Log.i(TAG, "Virtual gamepad registered through uinput helper on port $port ($reason)")
            watchForClose(candidate)
            return true
        } catch (error: IOException) {
            fail(
                "Virtual gamepad helper is not running on 127.0.0.1:$port " +
                    "(start it from a PC with adb; see the Mapeo tab).",
            )
            return false
        }
    }

    private fun registerCommand(): JSONObject {
        val keyBits = JSONArray(
            listOf(BTN_A, BTN_B, BTN_X, BTN_Y, BTN_TL, BTN_TR, BTN_SELECT, BTN_START),
        )
        val absInfo = JSONArray()
        STICK_AXES.forEach { code ->
            absInfo.put(
                JSONObject().put("code", code).put(
                    "info",
                    JSONObject()
                        .put("value", 0)
                        .put("minimum", -AXIS_RANGE)
                        .put("maximum", AXIS_RANGE)
                        .put("fuzz", 0)
                        .put("flat", AXIS_FLAT)
                        .put("resolution", 0),
                ),
            )
        }
        HAT_AXES.forEach { code ->
            absInfo.put(
                JSONObject().put("code", code).put(
                    "info",
                    JSONObject()
                        .put("value", 0)
                        .put("minimum", -1)
                        .put("maximum", 1)
                        .put("fuzz", 0)
                        .put("flat", 0)
                        .put("resolution", 0),
                ),
            )
        }
        return JSONObject()
            .put("id", DEVICE_ID)
            .put("command", "register")
            .put("name", "MOGA Pocket")
            .put("vid", POWERA_VENDOR_ID)
            .put("pid", DEVICE_PRODUCT_ID)
            .put("bus", "bluetooth")
            .put(
                "configuration",
                JSONArray()
                    .put(JSONObject().put("type", UI_SET_EVBIT).put("data", JSONArray(listOf(EV_KEY, EV_ABS))))
                    .put(JSONObject().put("type", UI_SET_KEYBIT).put("data", keyBits))
                    .put(
                        JSONObject().put("type", UI_SET_ABSBIT)
                            .put("data", JSONArray(STICK_AXES + HAT_AXES)),
                    ),
            )
            .put("abs_info", absInfo)
    }

    private fun writeLine(command: JSONObject) {
        val stream = output ?: throw IOException("socket closed")
        stream.write((command.toString() + "\n").toByteArray(Charsets.UTF_8))
        stream.flush()
    }

    private fun addEvent(events: JSONArray, type: Int, code: Int, value: Int) {
        events.put(type).put(code).put(value)
    }

    /**
     * The helper never writes back unless something went wrong, so a read returning means the
     * connection ended (the virtual device is gone) or `uinput` printed an error. Either way
     * we find out immediately instead of at the next write.
     */
    private fun watchForClose(candidate: Socket) {
        Thread(
            {
                try {
                    val reader = candidate.getInputStream().bufferedReader()
                    while (true) {
                        val line = reader.readLine() ?: break
                        Log.w(TAG, "uinput helper said: $line")
                    }
                } catch (_: IOException) {
                    Unit
                }
                try {
                    executor.execute {
                        if (socket === candidate) fail("The uinput helper closed the connection")
                    }
                } catch (_: RejectedExecutionException) {
                    Unit // the plugin was destroyed meanwhile
                }
            },
            "moga-uinput-watch",
        ).apply { isDaemon = true }.start()
    }

    private fun fail(message: String) {
        lastError = message
        Log.w(TAG, message)
        closeSocket("failed")
        nextAttemptAtMillis = System.currentTimeMillis() + RETRY_DELAY_MS
    }

    private fun closeSocket(reason: String) {
        if (socket != null) Log.i(TAG, "Virtual gamepad connection closed ($reason)")
        try {
            socket?.close()
        } catch (_: IOException) {
            Unit
        }
        socket = null
        output = null
        lastValues = HashMap()
    }

    private fun Boolean.toInt() = if (this) 1 else 0

    companion object {
        const val DEFAULT_PORT = 7777
        private const val TAG = "MogaUinput"
        private const val LOOPBACK = "127.0.0.1"
        private const val CONNECT_TIMEOUT_MS = 300
        private const val REACHABLE_TIMEOUT_MS = 1_500L
        private const val RETRY_DELAY_MS = 1_000L
        private const val WATCHDOG_MS = 2_000L
        private const val DEVICE_ID = 1

        // PowerA vendor id; the product id is arbitrary for a virtual device.
        private const val POWERA_VENDOR_ID = 0x20d6
        private const val DEVICE_PRODUCT_ID = 0x89e5

        // <linux/uinput.h> ioctl numbers accepted by the `uinput` tool's "configuration".
        private const val UI_SET_EVBIT = 100
        private const val UI_SET_KEYBIT = 101
        private const val UI_SET_ABSBIT = 103

        // <linux/input-event-codes.h>
        private const val EV_SYN = 0
        private const val EV_KEY = 1
        private const val EV_ABS = 3
        private const val ABS_X = 0
        private const val ABS_Y = 1
        private const val ABS_RX = 3
        private const val ABS_RY = 4
        private const val ABS_HAT0X = 16
        private const val ABS_HAT0Y = 17
        private val STICK_AXES = listOf(ABS_X, ABS_Y, ABS_RX, ABS_RY)
        private val HAT_AXES = listOf(ABS_HAT0X, ABS_HAT0Y)

        private const val AXIS_RANGE = 127
        private const val AXIS_FLAT = 8

        private val NEUTRAL_STATE: JSONObject = JSONObject()

        // Android's Generic.kl turns these into BUTTON_A/B/X/Y, L1, R1, SELECT, START.
        private const val BTN_A = 304
        private const val BTN_B = 305
        private const val BTN_X = 307
        private const val BTN_Y = 308
        private const val BTN_TL = 310
        private const val BTN_TR = 311
        private const val BTN_SELECT = 314
        private const val BTN_START = 315
    }
}
