package dev.mogabridge.app.moga

import android.inputmethodservice.InputMethodService
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import org.json.JSONObject
import java.lang.ref.WeakReference

class MogaInputMethodService : InputMethodService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var previousKeyCodes = emptySet<Int>()

    /** Key code per control, loaded once and refreshed when the mapping changes. */
    private var keyCodes: Map<String, Int> = emptyMap()

    override fun onCreate() {
        super.onCreate()
        keyCodes = MogaPreferences(this).keyMapping()
        activeService = WeakReference(this)
    }

    override fun onDestroy() {
        previousKeyCodes = emptySet()
        if (activeService?.get() === this) activeService = null
        super.onDestroy()
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        previousKeyCodes = emptySet()
    }

    override fun onCreateInputView(): View {
        return TextView(this).apply {
            text = getString(dev.mogabridge.app.R.string.moga_ime_hint)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(28, 36, 48))
            gravity = Gravity.CENTER
            setPadding(20, 12, 20, 12)
        }
    }

    private fun acceptState(state: JSONObject) {
        val buttons = state.optJSONObject("buttons") ?: JSONObject()
        val leftStick = state.optJSONObject("leftStick") ?: JSONObject()
        val rightStick = state.optJSONObject("rightStick") ?: JSONObject()
        val pressedControls = mapOf(
            "buttonA" to buttons.optBoolean("a"),
            "buttonB" to buttons.optBoolean("b"),
            "buttonX" to buttons.optBoolean("x"),
            "buttonY" to buttons.optBoolean("y"),
            "start" to buttons.optBoolean("start"),
            "select" to buttons.optBoolean("select"),
            "leftBumper" to buttons.optBoolean("leftBumper"),
            "rightBumper" to buttons.optBoolean("rightBumper"),
            "leftStickUp" to leftStick.optBoolean("up"),
            "leftStickDown" to leftStick.optBoolean("down"),
            "leftStickLeft" to leftStick.optBoolean("left"),
            "leftStickRight" to leftStick.optBoolean("right"),
            "rightStickUp" to rightStick.optBoolean("up"),
            "rightStickDown" to rightStick.optBoolean("down"),
            "rightStickLeft" to rightStick.optBoolean("left"),
            "rightStickRight" to rightStick.optBoolean("right"),
        )
        val currentKeyCodes = pressedControls
            .filterValues { it }
            .keys
            .mapNotNull { control ->
                keyCodes[control]?.takeIf { it != 0 }
            }
            .toSet()

        (currentKeyCodes - previousKeyCodes).forEach(::sendDownUpKeyEvents)
        previousKeyCodes = currentKeyCodes
    }

    companion object {
        @Volatile private var activeService: WeakReference<MogaInputMethodService>? = null

        fun dispatch(state: JSONObject): Boolean {
            val service = activeService?.get() ?: return false
            service.mainHandler.post { service.acceptState(state) }
            return true
        }

        fun mappingChanged() {
            val service = activeService?.get() ?: return
            service.mainHandler.post {
                service.keyCodes = MogaPreferences(service).keyMapping()
                service.previousKeyCodes = emptySet()
            }
        }
    }
}
