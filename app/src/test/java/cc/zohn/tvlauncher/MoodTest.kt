package cc.zohn.tvlauncher

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

class MoodTest {
    private val zone = ZoneId.of("America/Chicago")
    private fun at(local: String) = LocalDateTime.parse(local).atZone(zone).toInstant()

    // Synthetic sun times (independent of SunCalc): sunrise 07:33, sunset 18:32 every day.
    private fun day(date: String) = Mood.boundaries(SunTimes(at("${date}T07:33"), at("${date}T18:32")))
    private val bounds = day("2026-10-19") + day("2026-10-20") + day("2026-10-21")

    @Test fun boundariesFollowTheSpec() {
        val b = day("2026-10-20")
        assertEquals(listOf(Phase.MORNING, Phase.DAY, Phase.EVENING, Phase.NIGHT), b.map { it.second })
        assertEquals(at("2026-10-20T07:03"), b[0].first)
        assertEquals(at("2026-10-20T10:33"), b[1].first)
        assertEquals(at("2026-10-20T17:32"), b[2].first)
        assertEquals(at("2026-10-20T21:02"), b[3].first)
    }

    @Test fun middayIsSteadyDay() {
        val s = Mood.stateAt(at("2026-10-20T13:00"), bounds, 30.0)
        assertEquals(Phase.DAY, s.phase)
        assertEquals(0f, s.blend, 0.001f)
    }

    @Test fun blendMidpointSwitchesPhase() {
        val s = Mood.stateAt(at("2026-10-20T17:32"), bounds, 5.0)
        assertEquals(Phase.DAY, s.from)
        assertEquals(Phase.EVENING, s.to)
        assertEquals(0.5f, s.blend, 0.01f)
        assertEquals(Phase.EVENING, s.phase)
    }

    @Test fun earlyInBlendStillDay() {
        // Window opens 22.5 min before 17:32 = 17:09:30; at 17:20 we're ~23% in.
        val s = Mood.stateAt(at("2026-10-20T17:20"), bounds, 5.0)
        assertEquals(Phase.DAY, s.phase)
        assertEquals(0.233f, s.blend, 0.01f)
    }

    @Test fun lateInBlendIsEvening() {
        val s = Mood.stateAt(at("2026-10-20T17:50"), bounds, 2.0)
        assertEquals(Phase.DAY, s.from)
        assertEquals(Phase.EVENING, s.to)
        assertEquals(Phase.EVENING, s.phase)
        assertEquals(0.9f, s.blend, 0.01f)
    }

    @Test fun pastMidnightIsNight() {
        val s = Mood.stateAt(at("2026-10-21T03:00"), bounds, -40.0)
        assertEquals(Phase.NIGHT, s.phase)
        assertEquals(Phase.MORNING, s.to)
        assertEquals(0f, s.blend, 0.001f)
    }

    @Test fun ticksEveryTwoMinutesInsideABlend() {
        assertEquals(Duration.ofMinutes(2), Mood.nextTickDelay(at("2026-10-20T17:20"), bounds))
    }

    @Test fun outsideABlendWakesAtTheLatestInTenMinutes() {
        assertEquals(Duration.ofMinutes(10), Mood.nextTickDelay(at("2026-10-20T13:00"), bounds))
        // 17:05 → window opens 17:09:30, sooner than 10 min.
        assertEquals(Duration.ofSeconds(270), Mood.nextTickDelay(at("2026-10-20T17:05"), bounds))
    }
}
