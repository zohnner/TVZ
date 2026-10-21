package cc.zohn.tvlauncher

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class Phase {
    MORNING, DAY, EVENING, NIGHT;

    fun next(): Phase = entries[(ordinal + 1) % entries.size]
}

/**
 * Where we are in the day. Outside a blend window: from = current phase, to = the next one,
 * blend = 0. Inside a 45-minute window: blend runs 0→1 from [from] to [to]; the phase
 * officially switches at the window's midpoint.
 */
data class MoodState(val from: Phase, val to: Phase, val blend: Float, val sunElevation: Double) {
    val phase: Phase get() = if (blend >= 0.5f) to else from
}

object Mood {
    val BLEND: Duration = Duration.ofMinutes(45)
    private val HALF: Duration = BLEND.dividedBy(2)
    private val BLEND_TICK: Duration = Duration.ofMinutes(2)
    private val MAX_SLEEP: Duration = Duration.ofMinutes(10)

    /** Phase starts (blend midpoints) for one day's sun times. */
    fun boundaries(sun: SunTimes): List<Pair<Instant, Phase>> = listOf(
        sun.sunrise.minus(Duration.ofMinutes(30)) to Phase.MORNING,
        sun.sunrise.plus(Duration.ofHours(3)) to Phase.DAY,
        sun.sunset.minus(Duration.ofHours(1)) to Phase.EVENING,
        sun.sunset.plus(Duration.ofMinutes(150)) to Phase.NIGHT,
    )

    /** Yesterday's, today's and tomorrow's boundaries in order, so night can span midnight. */
    fun boundariesAround(date: LocalDate, zone: ZoneId): List<Pair<Instant, Phase>> =
        listOf(date.minusDays(1), date, date.plusDays(1)).flatMap {
            boundaries(SunCalc.sunTimes(it, HOME_LATITUDE, HOME_LONGITUDE, zone))
        }

    fun stateAt(now: Instant, boundaries: List<Pair<Instant, Phase>>, sunElevation: Double): MoodState {
        val lastIndex = boundaries.indexOfLast { !it.first.isAfter(now) }
        require(lastIndex in 1 until boundaries.lastIndex) { "boundaries must surround $now" }
        val (lastAt, current) = boundaries[lastIndex]
        val (nextAt, upcoming) = boundaries[lastIndex + 1]
        val previous = boundaries[lastIndex - 1].second
        return when {
            now.isBefore(lastAt.plus(HALF)) ->
                MoodState(previous, current, fraction(lastAt.minus(HALF), now), sunElevation)
            !now.isBefore(nextAt.minus(HALF)) ->
                MoodState(current, upcoming, fraction(nextAt.minus(HALF), now), sunElevation)
            else -> MoodState(current, upcoming, 0f, sunElevation)
        }
    }

    /** 2 min inside a blend window; otherwise until the next window opens, at most 10 min. */
    fun nextTickDelay(now: Instant, boundaries: List<Pair<Instant, Phase>>): Duration {
        val inWindow = boundaries.any { (at, _) -> !now.isBefore(at.minus(HALF)) && now.isBefore(at.plus(HALF)) }
        if (inWindow) return BLEND_TICK
        val nextWindow = boundaries.map { it.first.minus(HALF) }.firstOrNull { it.isAfter(now) }
            ?: return MAX_SLEEP
        val untilWindow = Duration.between(now, nextWindow)
        return if (untilWindow < MAX_SLEEP) untilWindow else MAX_SLEEP
    }

    private fun fraction(windowStart: Instant, now: Instant): Float =
        (Duration.between(windowStart, now).toMillis().toFloat() / BLEND.toMillis()).coerceIn(0f, 1f)
}
