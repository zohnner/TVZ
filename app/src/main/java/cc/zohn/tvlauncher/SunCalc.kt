package cc.zohn.tvlauncher

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

// Kansas City, MO — drives the moods (sunrise/sunset).
const val HOME_LATITUDE = 39.10
const val HOME_LONGITUDE = -94.58

data class SunTimes(val sunrise: Instant, val sunset: Instant)

/** NOAA solar equations: about a minute of accuracy at mid latitudes, no network needed. */
object SunCalc {
    fun sunTimes(date: LocalDate, latitude: Double, longitude: Double, zone: ZoneId): SunTimes {
        val solar = solar(date.atTime(12, 0).atZone(zone).toInstant())
        val lat = Math.toRadians(latitude)
        // 90.833° = sun's center 50' below the horizon (refraction + radius).
        val cosHourAngle = cos(Math.toRadians(90.833)) / (cos(lat) * cos(solar.declination)) -
            tan(lat) * tan(solar.declination)
        val hourAngleDeg = Math.toDegrees(acos(cosHourAngle.coerceIn(-1.0, 1.0)))
        val utcMidnight = date.atStartOfDay(ZoneOffset.UTC).toInstant()
        val solarNoonMinutes = 720 - 4 * longitude - solar.equationOfTime
        return SunTimes(
            sunrise = utcMidnight.plusSeconds(((solarNoonMinutes - 4 * hourAngleDeg) * 60).roundToLong()),
            sunset = utcMidnight.plusSeconds(((solarNoonMinutes + 4 * hourAngleDeg) * 60).roundToLong()),
        )
    }

    /** Geometric sun elevation in degrees (negative = below the horizon). */
    fun elevationDegrees(instant: Instant, latitude: Double, longitude: Double): Double {
        val solar = solar(instant)
        val utcMinutes = instant.epochSecond.mod(86_400L) / 60.0
        val trueSolarMinutes = (utcMinutes + solar.equationOfTime + 4 * longitude).mod(1440.0)
        val hourAngle = Math.toRadians(
            if (trueSolarMinutes / 4 < 0) trueSolarMinutes / 4 + 180 else trueSolarMinutes / 4 - 180
        )
        val lat = Math.toRadians(latitude)
        val cosZenith = sin(lat) * sin(solar.declination) +
            cos(lat) * cos(solar.declination) * cos(hourAngle)
        return 90 - Math.toDegrees(acos(cosZenith.coerceIn(-1.0, 1.0)))
    }

    private class Solar(val declination: Double, val equationOfTime: Double) // radians, minutes

    private fun solar(instant: Instant): Solar {
        val julianDay = instant.epochSecond / 86_400.0 + 2_440_587.5
        val t = (julianDay - 2_451_545.0) / 36_525.0
        val meanLong = (280.46646 + t * (36_000.76983 + t * 0.0003032)).mod(360.0)
        val meanAnomaly = Math.toRadians(357.52911 + t * (35_999.05029 - 0.0001537 * t))
        val eccentricity = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val center = sin(meanAnomaly) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2 * meanAnomaly) * (0.019993 - 0.000101 * t) + sin(3 * meanAnomaly) * 0.000289
        val omega = Math.toRadians(125.04 - 1_934.136 * t)
        val apparentLong = Math.toRadians(meanLong + center - 0.00569 - 0.00478 * sin(omega))
        val meanObliquity = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60) / 60
        val obliquity = Math.toRadians(meanObliquity + 0.00256 * cos(omega))
        val declination = asin(sin(obliquity) * sin(apparentLong))
        val y = tan(obliquity / 2).pow(2)
        val l0 = Math.toRadians(meanLong)
        val equationOfTime = 4 * Math.toDegrees(
            y * sin(2 * l0) - 2 * eccentricity * sin(meanAnomaly) +
                4 * eccentricity * y * sin(meanAnomaly) * cos(2 * l0) -
                0.5 * y * y * sin(4 * l0) - 1.25 * eccentricity * eccentricity * sin(2 * meanAnomaly)
        )
        return Solar(declination, equationOfTime)
    }
}
