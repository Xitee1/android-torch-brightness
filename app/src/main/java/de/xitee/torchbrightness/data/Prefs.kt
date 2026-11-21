package de.xitee.torchbrightness.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val enabled: Boolean = false,
    /** Relative change of the target brightness in percent, e.g. 20 = 20 % brighter. */
    val adjustmentPercent: Int = 0,
)

/**
 * Small persistent store. The saved brightness state is written synchronously, so the original setting
 * can be restored even if the app is killed while the flashlight is on.
 */
class Prefs(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("torch_brightness", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun setEnabled(enabled: Boolean) = writeSettings { putBoolean(KEY_ENABLED, enabled) }

    fun setAdjustmentPercent(percent: Int) = writeSettings { putInt(KEY_ADJUSTMENT, percent) }

    /** True while adaptive brightness is switched off by this app and must be restored. */
    val hasSavedState: Boolean get() = prefs.getBoolean(KEY_SAVED, false)

    val savedBrightness: Int get() = prefs.getInt(KEY_SAVED_BRIGHTNESS, 128)

    fun saveState(brightness: Int) {
        prefs.edit(commit = true) {
            putBoolean(KEY_SAVED, true)
            putInt(KEY_SAVED_BRIGHTNESS, brightness)
        }
    }

    fun clearState() {
        prefs.edit(commit = true) { putBoolean(KEY_SAVED, false) }
    }

    var lastLux: Float
        get() = prefs.getFloat(KEY_LAST_LUX, 10f)
        set(value) = prefs.edit { putFloat(KEY_LAST_LUX, value) }

    private fun readSettings() = Settings(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        adjustmentPercent = prefs.getInt(KEY_ADJUSTMENT, 0),
    )

    private fun writeSettings(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit(action = block)
        _settings.value = readSettings()
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_ADJUSTMENT = "adjustment_percent"
        const val KEY_SAVED = "saved_active"
        const val KEY_SAVED_BRIGHTNESS = "saved_brightness"
        const val KEY_LAST_LUX = "last_lux"
    }
}
