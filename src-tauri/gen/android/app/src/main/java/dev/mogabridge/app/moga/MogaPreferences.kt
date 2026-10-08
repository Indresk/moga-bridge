package dev.mogabridge.app.moga

import android.content.Context

/** How controller input leaves the app. Keep in sync with `OutputMode` in schemas/settings.rs. */
internal object OutputModes {
    const val GAMEPAD = "gamepad"
    const val KEYBOARD = "keyboard"
}

/** How the two sticks are presented. Keep in sync with `StickLayout` in schemas/settings.rs. */
internal object StickLayouts {
    const val ANALOGS = "analogs"
    const val LEFT_DPAD = "leftDpad"
    const val RIGHT_DPAD = "rightDpad"
    val ALL = setOf(ANALOGS, LEFT_DPAD, RIGHT_DPAD)
}

/** Everything the user can configure; the single owner of the app's SharedPreferences. */
internal class MogaPreferences(context: Context) {
    private val output = context.getSharedPreferences(OUTPUT_FILE, Context.MODE_PRIVATE)
    private val keys = context.getSharedPreferences(KEY_MAPPING_FILE, Context.MODE_PRIVATE)

    var outputMode: String
        get() = output.getString(MODE_KEY, OutputModes.GAMEPAD) ?: OutputModes.GAMEPAD
        set(value) {
            require(value == OutputModes.GAMEPAD || value == OutputModes.KEYBOARD) {
                "Unsupported output mode: $value"
            }
            output.edit().putString(MODE_KEY, value).apply()
        }

    var stickLayout: String
        get() = output.getString(STICK_LAYOUT_KEY, StickLayouts.ANALOGS) ?: StickLayouts.ANALOGS
        set(value) {
            require(value in StickLayouts.ALL) { "Unsupported stick layout: $value" }
            output.edit().putString(STICK_LAYOUT_KEY, value).apply()
        }

    /** Android key code for a control in keyboard mode (0 = disabled). */
    fun keyCode(control: String): Int = keys.getInt(control, DEFAULT_KEY_MAPPING[control] ?: 0)

    fun keyMapping(): Map<String, Int> = KEY_CONTROLS.associateWith(::keyCode)

    fun saveKeyMapping(mapping: Map<String, Int>) {
        require(mapping.values.all { it in 0..MAX_KEY_CODE }) {
            "Android key codes must be between 0 and $MAX_KEY_CODE."
        }
        keys.edit().clear().apply { mapping.forEach { (control, code) -> putInt(control, code) } }.apply()
    }

    companion object {
        private const val OUTPUT_FILE = "moga-output"
        private const val KEY_MAPPING_FILE = "moga-key-mapping"
        private const val MODE_KEY = "mode"
        private const val STICK_LAYOUT_KEY = "stickLayout"
        private const val MAX_KEY_CODE = 288

        /** Control names; keep in sync with `KeyMapping` in schemas/settings.rs. */
        val KEY_CONTROLS = listOf(
            "buttonA", "buttonB", "buttonX", "buttonY",
            "start", "select", "leftBumper", "rightBumper",
            "leftStickUp", "leftStickDown", "leftStickLeft", "leftStickRight",
            "rightStickUp", "rightStickDown", "rightStickLeft", "rightStickRight",
        )

        // PPSSPP's default keyboard layout: Cross Z, Circle X, Square A, Triangle S,
        // L Q, R W, Start Space, Select V, arrows for the D-pad, I/J/K/L for the stick.
        // MOGA A/B/X/Y sit where Cross/Circle/Square/Triangle sit on a PSP-style pad.
        private val DEFAULT_KEY_MAPPING = mapOf(
            "buttonA" to 54, "buttonB" to 52, "buttonX" to 29, "buttonY" to 47,
            "start" to 62, "select" to 50,
            "leftBumper" to 45, "rightBumper" to 51,
            "leftStickUp" to 19, "leftStickDown" to 20,
            "leftStickLeft" to 21, "leftStickRight" to 22,
            "rightStickUp" to 37, "rightStickDown" to 39,
            "rightStickLeft" to 38, "rightStickRight" to 40,
        )
    }
}
