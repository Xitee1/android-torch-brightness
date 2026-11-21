package de.xitee.torchbrightness.brightness

import kotlin.math.exp

/**
 * Exponential smoothing of lux readings so the screen does not flicker. Brightening reacts faster than
 * darkening, like the system's adaptive brightness.
 */
class LuxSmoother(
    private val brightenTauMs: Float = 1000f,
    private val darkenTauMs: Float = 3000f,
) {
    var value: Float? = null
        private set
    private var lastTimeMs = 0L

    fun reset(lux: Float, timeMs: Long) {
        value = lux
        lastTimeMs = timeMs
    }

    fun update(lux: Float, timeMs: Long): Float {
        val current = value ?: return lux.also { reset(it, timeMs) }
        val dt = (timeMs - lastTimeMs).coerceAtLeast(0L).toFloat()
        val tau = if (lux > current) brightenTauMs else darkenTauMs
        val alpha = 1f - exp(-dt / tau)
        return (current + (lux - current) * alpha).also {
            value = it
            lastTimeMs = timeMs
        }
    }
}
