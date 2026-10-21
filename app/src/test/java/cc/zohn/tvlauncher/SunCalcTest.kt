package cc.zohn.tvlauncher

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

class SunCalcTest {
    private val zone = ZoneId.of("America/Chicago")

    private fun assertNear(expected: String, actual: java.time.Instant, label: String) {
        val got = actual.atZone(zone).toLocalTime()
        val diff = abs(got.toSecondOfDay() - LocalTime.parse(expected).toSecondOfDay())
        assert(diff <= 180) { "$label: expected ~$expected, got $got" }
    }

    private fun check(date: String, sunrise: String, sunset: String) {
        val times = SunCalc.sunTimes(LocalDate.parse(date), HOME_LATITUDE, HOME_LONGITUDE, zone)
        assertNear(sunrise, times.sunrise, "$date sunrise")
        assertNear(sunset, times.sunset, "$date sunset")
    }

    @Test fun marchEquinox() = check("2026-03-20", "07:21", "19:30")
    @Test fun juneSolstice() = check("2026-06-21", "05:52", "20:47")
    @Test fun septemberEquinox() = check("2026-09-22", "07:06", "19:14")
    @Test fun today() = check("2026-10-20", "07:33", "18:32")
    @Test fun dstEndsNovember1() = check("2026-11-01", "06:46", "17:16")
    @Test fun decemberSolstice() = check("2026-12-21", "07:34", "16:58")

    private fun elevation(local: String) =
        SunCalc.elevationDegrees(LocalDateTime.parse(local).atZone(zone).toInstant(), HOME_LATITUDE, HOME_LONGITUDE)

    @Test fun summerNoonHigh() = assertEquals(74.2, elevation("2026-06-21T13:30"), 1.0)
    @Test fun winterNoonLow() = assertEquals(27.5, elevation("2026-12-21T12:15"), 1.0)
    @Test fun aroundSunrise() = assertEquals(-1.0, elevation("2026-10-20T07:31"), 1.0)
}
