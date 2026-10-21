package cc.zohn.tvlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtSubjectsTest {
    @Test fun subjectsMatchTheSpec() {
        assertEquals(listOf("sunrise", "morning"), subjectsFor(Phase.MORNING))
        assertEquals(listOf("garden", "water lilies", "river"), subjectsFor(Phase.DAY))
        assertEquals(listOf("sunset", "evening"), subjectsFor(Phase.EVENING))
        assertEquals(listOf("night", "moonlight", "nocturne"), subjectsFor(Phase.NIGHT))
    }

    @Test fun pickIsStableForADayAndDiffersByPhase() {
        assertEquals(pickIndex(20_381, Phase.DAY, 10), pickIndex(20_381, Phase.DAY, 10))
        assertNotEquals(pickIndex(20_381, Phase.DAY, 10), pickIndex(20_381, Phase.EVENING, 10))
        assertTrue(pickIndex(20_381, Phase.NIGHT, 3) in 0 until 3)
    }
}
