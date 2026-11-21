package de.xitee.torchbrightness.curve

/**
 * Monotone cubic Hermite spline, evaluated exactly like AOSP's `MonotoneCubicSpline`, from the points and
 * tangents printed in `dumpsys display`.
 */
class Spline(private val x: FloatArray, private val y: FloatArray, private val m: FloatArray) {

    init {
        require(x.size >= 2 && x.size == y.size && y.size == m.size) { "Invalid spline" }
    }

    val size: Int get() = x.size

    fun interpolate(value: Float): Float {
        val n = x.size
        if (value.isNaN()) return value
        if (value <= x[0]) return y[0]
        if (value >= x[n - 1]) return y[n - 1]
        var i = 0
        while (value >= x[i + 1]) {
            i++
            if (value == x[i]) return y[i]
        }
        val h = x[i + 1] - x[i]
        val t = (value - x[i]) / h
        return (y[i] * (1 + 2 * t) + h * m[i] * t) * (1 - t) * (1 - t) +
            (y[i + 1] * (3 - 2 * t) + h * m[i + 1] * (t - 1)) * t * t
    }

    companion object {
        private val POINT = Regex("""\(\s*([-0-9.Ee]+),\s*([-0-9.Ee]+):\s*([-0-9.Ee]+)\s*\)""")

        /** Parses the `MonotoneCubicSpline{[(x, y: m), ...]}` text representation. */
        fun parse(text: String): Spline? {
            val points = POINT.findAll(text).map { match ->
                Triple(
                    match.groupValues[1].toFloat(),
                    match.groupValues[2].toFloat(),
                    match.groupValues[3].toFloat(),
                )
            }.toList()
            if (points.size < 2) return null
            return Spline(
                FloatArray(points.size) { points[it].first },
                FloatArray(points.size) { points[it].second },
                FloatArray(points.size) { points[it].third },
            )
        }
    }
}
