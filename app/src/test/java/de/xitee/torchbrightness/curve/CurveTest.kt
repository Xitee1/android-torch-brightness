package de.xitee.torchbrightness.curve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurveTest {

    private fun excerpt(): String =
        javaClass.classLoader!!.getResource("display_dump_excerpt.txt")!!.readText()

    @Test
    fun spline_hitsItsPointsAndClamps() {
        val spline = Spline.parse("MonotoneCubicSpline{[(0.0, 1.0: 0.5), (2.0, 2.0: 0.5), (4.0, 3.0: 0.5)]}")!!
        assertEquals(1f, spline.interpolate(-1f), 1e-6f)
        assertEquals(2f, spline.interpolate(2f), 1e-6f)
        assertEquals(1.5f, spline.interpolate(1f), 1e-6f)
        assertEquals(3f, spline.interpolate(10f), 1e-6f)
    }

    @Test
    fun parser_readsCurveFromRealDump() {
        val result = DumpParser.parse(excerpt(), 0L)
        assertTrue(result is DumpParser.Result.Success)
        val curve = (result as DumpParser.Result.Success).curve
        assertTrue(curve.pointCount > 10)
        // Darkness gives the minimum, more light never gives less brightness.
        val dark = curve.settingFor(0f)
        val room = curve.settingFor(100f)
        val sun = curve.settingFor(10000f)
        assertTrue("dark=$dark room=$room sun=$sun", dark in 1..30 && dark <= room && room <= sun)
        assertTrue(sun <= 252) // capped at the normal-mode maximum (0.9877808)
    }

    @Test
    fun parser_reportsMissingFields() {
        assertEquals(DumpParser.Result.NotFound, DumpParser.parse("nothing here", 0L))
    }

    @Test
    fun relevantLines_keepsEverythingTheParserNeeds() {
        val noisy = "header\n" + excerpt() + "\nfooter"
        assertTrue(DumpParser.parse(DumpParser.relevantLines(noisy), 0L) is DumpParser.Result.Success)
    }
}
