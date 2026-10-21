package cc.zohn.tvlauncher

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import cc.zohn.tvlauncher.ui.theme.LauncherPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MoodsTest {
    private fun palette(c: Color, stars: Float) = LauncherPalette(
        background = c, surface = c, accent = c, glow = c, secondary = c, text = c,
        display = FontFamily.Default, starAlpha = stars, glowStrength = stars,
    )
    private val a = palette(Color(0xFF120821), 0.1f)
    private val b = palette(Color(0xFFFF8A3D), 0.9f)

    @Test fun endpointsAreExact() {
        assertEquals(a, blendPalettes(a, b, 0f))
        assertEquals(b, blendPalettes(a, b, 1f))
    }

    @Test fun midpointBlendsColorsAndNumbers() {
        val mid = blendPalettes(a, b, 0.5f)
        assertTrue(mid.text == a.text || mid.text == b.text) // text picks an endpoint, never a lerp
        assertEquals(lerp(a.sky1, b.sky1, 0.5f), mid.sky1)
        assertEquals(0.5f, mid.starAlpha, 0.0001f)
        assertEquals(0.5f, mid.glowStrength, 0.0001f)
    }

    @Test fun everyProfileHasFourDistinctPhases() {
        for (id in listOf("zohn", "sydney")) {
            val backgrounds = Phase.entries.map { Moods.palette(id, it).background }.toSet()
            assertEquals("$id phases", 4, backgrounds.size)
        }
    }

    @Test fun zohnNightHasNoGlowAndDimGrid() {
        val night = Moods.palette("zohn", Phase.NIGHT)
        assertEquals(0f, night.glowStrength, 0f)
        assertEquals(0.35f, night.gridAlpha, 0.001f)
    }

    @Test fun effectiveFollowsTheBlend() {
        val state = MoodState(Phase.DAY, Phase.EVENING, 0.5f, 3.0)
        val expected = blendPalettes(Moods.palette("zohn", Phase.DAY), Moods.palette("zohn", Phase.EVENING), 0.5f)
        assertEquals(expected, Moods.effective("zohn", state))
    }

    @Test fun overrideExpires() {
        val o = ThemeOverride(a, "KICKOFF IN 0:20", Instant.parse("2026-10-25T21:00:00Z"))
        assertTrue(o.activeAt(Instant.parse("2026-10-25T20:59:00Z")))
        assertFalse(o.activeAt(Instant.parse("2026-10-25T21:00:00Z")))
    }

    @Test fun sydneyNightHasNoFocusBloom() {
        assertEquals(0f, Moods.palette("sydney", Phase.NIGHT).glowStrength, 0f)
    }

    @Test fun contrastRatioKnownValues() {
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.01f)
        assertEquals(1f, contrastRatio(Color.White, Color.White), 0.001f)
    }

    @Test fun sydneyBlendsStayLegible() {
        val pairs = listOf(Phase.EVENING to Phase.NIGHT, Phase.NIGHT to Phase.MORNING)
        for ((from, to) in pairs) {
            for (t in listOf(0.2f, 0.35f, 0.5f, 0.65f, 0.8f)) {
                val m = blendPalettes(Moods.palette("sydney", from), Moods.palette("sydney", to), t)
                assertTrue("$from->$to t=$t text", contrastRatio(m.text, m.background) >= 3.0f)
            }
        }
    }
}
