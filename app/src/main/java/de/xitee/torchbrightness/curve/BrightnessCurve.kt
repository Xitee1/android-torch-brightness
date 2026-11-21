package de.xitee.torchbrightness.curve

import kotlin.math.roundToInt

/**
 * The system's own adaptive brightness curve, read from `dumpsys display` (`PhysicalMappingStrategy`):
 * lux → nits including the user's personal adjustments, and nits → brightness.
 */
class BrightnessCurve(
    private val luxToNits: Spline,
    private val nitsToBrightness: Spline,
    /** Highest brightness in normal (non high-brightness) mode. */
    private val maxBrightness: Float,
    /** Wall-clock time when the curve was read. */
    val readAtMillis: Long,
) {
    val pointCount: Int get() = luxToNits.size

    fun nitsFor(lux: Float): Float = luxToNits.interpolate(lux.coerceAtLeast(0f))

    /**
     * @param adjustment relative change of the target nits, e.g. 0.2 = 20 % brighter.
     * @return value for `Settings.System.SCREEN_BRIGHTNESS` (1..255).
     */
    fun settingFor(lux: Float, adjustment: Float = 0f): Int {
        val nits = nitsFor(lux) * (1f + adjustment)
        val brightness = nitsToBrightness.interpolate(nits).coerceIn(0f, maxBrightness)
        return brightnessToSetting(brightness)
    }

    companion object {
        /** Same linear mapping as AOSP's BrightnessSynchronizer: float 0..1 → int 1..255. */
        fun brightnessToSetting(brightness: Float): Int = (1 + brightness * 254).roundToInt().coerceIn(1, 255)
    }
}
