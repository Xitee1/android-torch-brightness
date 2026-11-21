package de.xitee.torchbrightness

import de.xitee.torchbrightness.brightness.LuxSmoother
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LuxSmootherTest {
    @Test
    fun brighteningIsFasterThanDarkening() {
        val up = LuxSmoother(brightenTauMs = 1000f, darkenTauMs = 3000f).apply { reset(0f, 0) }
        val down = LuxSmoother(brightenTauMs = 1000f, darkenTauMs = 3000f).apply { reset(100f, 0) }
        val upValue = up.update(100f, 1000)
        val downValue = down.update(0f, 1000)
        assertTrue(upValue > 100f - downValue)
        assertEquals(63.2f, upValue, 0.1f)
    }
}
