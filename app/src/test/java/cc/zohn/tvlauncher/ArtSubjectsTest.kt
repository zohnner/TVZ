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

    private fun art(title: String, fact: String?) = Artwork(1, title, "a", "d", "i", fact)
    private val night = listOf("night", "moonlight", "nocturne")

    @Test fun subjectScoreRanksTitleOverDescription() {
        assertEquals(2, subjectScore(art("Moonlight on the Seine", null), night))
        assertEquals(2, subjectScore(art("Night Cafe", "a fact"), night))
        assertEquals(1, subjectScore(art("Portrait", "Painted after a ball one night."), night))
        assertEquals(0, subjectScore(art("Portrait", "A lady."), night))
        assertEquals(0, subjectScore(art("Portrait", null), night))
    }

    @Test fun subjectScoreMatchesWholeWordsOnly() {
        assertEquals(0, subjectScore(art("Nightly Rounds", "A nightly ritual."), night))
    }
}
