package de.xitee.torchbrightness.curve

/** Extracts the adaptive brightness curve from `dumpsys display` output. */
object DumpParser {

    private const val STRATEGY = "PhysicalMappingStrategy"
    private const val LUX_TO_NITS = "mBrightnessSpline="
    private const val NITS_TO_BRIGHTNESS = "mAdjustedNitsToBrightnessSpline="
    private const val MAX_BRIGHTNESS = "mBrightnessMax="

    /** Lines needed by [parse]; used to keep the text small when it crosses process boundaries. */
    fun relevantLines(dump: String): String = dump.lineSequence()
        .filter { line ->
            val t = line.trim()
            t == STRATEGY || t.startsWith(LUX_TO_NITS) || t.startsWith(NITS_TO_BRIGHTNESS) || t.startsWith(MAX_BRIGHTNESS)
        }
        .joinToString("\n")

    sealed interface Result {
        data class Success(val curve: BrightnessCurve) : Result
        /** The dump does not contain the expected fields (other Android version or vendor). */
        data object NotFound : Result
    }

    fun parse(dump: String, nowMillis: Long): Result {
        val lines = dump.lineSequence().map { it.trim() }.toList()
        // The first PhysicalMappingStrategy belongs to the default display's normal mode.
        val start = lines.indexOfFirst { it == STRATEGY }
        if (start < 0) return Result.NotFound
        val section = lines.drop(start + 1)
        val luxToNits = section.firstOrNull { it.startsWith(LUX_TO_NITS) }?.let(Spline::parse)
        val nitsToBrightness = section.firstOrNull { it.startsWith(NITS_TO_BRIGHTNESS) }?.let(Spline::parse)
        if (luxToNits == null || nitsToBrightness == null) return Result.NotFound
        val max = lines.firstOrNull { it.startsWith(MAX_BRIGHTNESS) }
            ?.removePrefix(MAX_BRIGHTNESS)?.toFloatOrNull()
            ?.takeIf { it > 0f && it <= 1f }
            ?: 1f
        return Result.Success(BrightnessCurve(luxToNits, nitsToBrightness, max, nowMillis))
    }
}
