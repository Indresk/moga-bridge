package com.rafa_linux.moga_tauri.moga

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

    override fun onCreate() {
        super.onCreate()
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
            text = getString(com.rafa_linux.moga_tauri.R.string.moga_ime_hint)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(28, 36, 48))
            gravity = Gravity.CENTER
            setPadding(20, 12, 20, 12)
        }
    }

    private fun acceptState(state: JSONObject) {
        val preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
        val buttons = state.optJSONObject("buttons") ?: JSONObject()
        val dpad = state.optJSONObject("dpad") ?: JSONObject()
        val pressedControls = mapOf(
            "buttonA" to buttons.optBoolean("a"),
            "buttonB" to buttons.optBoolean("b"),
            "buttonX" to buttons.optBoolean("x"),
            "buttonY" to buttons.optBoolean("y"),
            "start" to buttons.optBoolean("start"),
            "select" to buttons.optBoolean("select"),
            "leftBumper" to buttons.optBoolean("leftBumper"),
            "rightBumper" to buttons.optBoolean("rightBumper"),
            "up" to dpad.optBoolean("up"),
            "down" to dpad.optBoolean("down"),
            "left" to dpad.optBoolean("left"),
            "right" to dpad.optBoolean("right"),
            "rightUp" to dpad.optBoolean("rightUp"),
            "rightDown" to dpad.optBoolean("rightDown"),
            "rightLeft" to dpad.optBoolean("rightLeft"),
            "rightRight" to dpad.optBoolean("rightRight"),
        )
        val currentKeyCodes = pressedControls
            .filterValues { it }
            .keys
            .mapNotNull { control ->
                preferences.getInt(control, 0).takeIf { it != 0 }
            }
            .toSet()

        (currentKeyCodes - previousKeyCodes).forEach(::sendDownUpKeyEvents)
        previousKeyCodes = currentKeyCodes
    }

    companion object {
        private const val PREFERENCES = "moga-key-mapping"
        @Volatile private var activeService: WeakReference<MogaInputMethodService>? = null

        fun dispatch(state: JSONObject): Boolean {
            val service = activeService?.get() ?: return false
            service.mainHandler.post { service.acceptState(state) }
            return true
        }

        fun mappingChanged() {
            val service = activeService?.get() ?: return
            service.mainHandler.post {
                service.previousKeyCodes = emptySet()
            }
        }
    }
}
