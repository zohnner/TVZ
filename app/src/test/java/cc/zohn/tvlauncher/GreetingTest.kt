package cc.zohn.tvlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GreetingTest {
    @Test fun linesPerPhase() {
        assertEquals("Good morning, Zohn", greetingFor(Phase.MORNING, "Zohn"))
        assertEquals("Good afternoon, Zohn", greetingFor(Phase.DAY, "Zohn"))
        assertEquals("Good evening, Sydney", greetingFor(Phase.EVENING, "Sydney"))
        assertEquals("Late night, Sydney", greetingFor(Phase.NIGHT, "Sydney"))
    }

    @Test fun onlyAfterThirtyMinutesOff() {
        val minute = 60_000L
        assertFalse(shouldGreet(null, 100 * minute))
        assertFalse(shouldGreet(80 * minute, 100 * minute))
        assertTrue(shouldGreet(70 * minute, 100 * minute))
    }
}
