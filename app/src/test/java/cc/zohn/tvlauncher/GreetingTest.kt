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

    @Test fun greetOnResumeRules() {
        val m = 60_000L
        val now = 200 * m
        // resume 5 s after a qualifying wake
        assertTrue(greetOnResume(null, now - 5_000, true, now))
        // woke into another app, came Home 10 min later
        assertFalse(greetOnResume(null, now - 10 * m, true, now))
        // wake after only 10 min off
        assertFalse(greetOnResume(null, now - 5_000, false, now))
        // resume before the screen-on broadcast, off for 40 min
        assertTrue(greetOnResume(now - 40 * m, null, false, now))
        // nothing recorded
        assertFalse(greetOnResume(null, null, false, now))
    }
}
