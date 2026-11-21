package de.xitee.torchbrightness.brightness

import android.content.Context
import android.provider.Settings

/** Reads and writes the system brightness settings. Writing needs "Modify system settings". */
class SystemBrightness(private val context: Context) {

    private val resolver get() = context.contentResolver

    fun canWrite(): Boolean = Settings.System.canWrite(context)

    fun isAutomatic(): Boolean =
        Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, MODE_MANUAL) == MODE_AUTOMATIC

    fun setAutomatic(automatic: Boolean) {
        Settings.System.putInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            if (automatic) MODE_AUTOMATIC else MODE_MANUAL,
        )
    }

    /** The manual brightness setting (1..255). */
    fun value(): Int = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)

    fun setValue(value: Int) {
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value.coerceIn(1, 255))
    }

    companion object {
        const val MODE_MANUAL = Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        const val MODE_AUTOMATIC = Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    }
}
