package de.xitee.torchbrightness.service

import de.xitee.torchbrightness.curve.BrightnessCurve
import kotlinx.coroutines.flow.MutableStateFlow

enum class ControlStatus {
    /** Service not running. */
    OFF,
    /** "Modify system settings" is not granted. */
    NO_PERMISSION,
    /** Neither the DUMP permission nor Shizuku is available to read the brightness curve. */
    NO_CURVE_ACCESS,
    /** `dumpsys display` does not contain the expected curve (other Android version or vendor). */
    CURVE_UNSUPPORTED,
    /** Flashlight off, nothing to do. */
    WAITING,
    /** Flashlight on, brightness follows the front sensor. */
    CONTROLLING,
    /** Flashlight on, the user moved the brightness slider; writing paused until the flashlight is off. */
    USER_OVERRIDE,
    /** Flashlight on, but adaptive brightness was off, so nothing is changed. */
    SKIPPED_MANUAL,
}

/** Live state shared between [TorchBrightnessService] and the UI (same process). */
object TorchRuntime {
    val status = MutableStateFlow(ControlStatus.OFF)
    val torchOn = MutableStateFlow(false)
    /** Last brightness value written by the app, null if none. */
    val writtenValue = MutableStateFlow<Int?>(null)
    /** Most recently read brightness curve. */
    val curve = MutableStateFlow<BrightnessCurve?>(null)
}
