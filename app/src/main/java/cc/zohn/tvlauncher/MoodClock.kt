package cc.zohn.tvlauncher

import android.os.SystemClock
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

private const val FAST_DAY_SPEEDUP = 720L // one day in two minutes
private const val FAST_DAY_TICK_MS = 1_000L

/**
 * Ticks the current [MoodState]: every 2 min inside a blend, otherwise at most every 10 min.
 * Restart [run] (cancel + relaunch) after a time/zone change or a fast-day toggle.
 */
class MoodClock(private val zone: () -> ZoneId = { ZoneId.systemDefault() }) {
    var fastDay = false
        private set
    private var fastStartRealtime = 0L
    private var fastStartInstant: Instant = Instant.EPOCH

    // Debug: plays a whole day in two minutes, starting from now.
    fun setFastDay(on: Boolean) {
        fastDay = on
        fastStartRealtime = SystemClock.elapsedRealtime()
        fastStartInstant = Instant.now()
    }

    fun now(): Instant = if (!fastDay) Instant.now() else
        fastStartInstant.plusMillis((SystemClock.elapsedRealtime() - fastStartRealtime) * FAST_DAY_SPEEDUP)

    fun state(): MoodState = stateAt(now())

    suspend fun run(onState: (MoodState) -> Unit) {
        while (true) {
            val now = now()
            onState(stateAt(now))
            val wait = if (fastDay) FAST_DAY_TICK_MS
            else Mood.nextTickDelay(now, boundaries(now)).toMillis().coerceAtLeast(1_000L)
            delay(wait)
        }
    }

    private fun boundaries(now: Instant) = Mood.boundariesAround(now.atZone(zone()).toLocalDate(), zone())

    private fun stateAt(now: Instant) = Mood.stateAt(
        now,
        boundaries(now),
        SunCalc.elevationDegrees(now, HOME_LATITUDE, HOME_LONGITUDE),
    )
}
