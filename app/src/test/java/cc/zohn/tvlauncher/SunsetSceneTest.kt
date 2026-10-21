package cc.zohn.tvlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SunsetSceneTest {
    // Scene-sized numbers: 540 px tall, horizon at 62%, cap just below the icon row (40%).
    private val horizonY = 335f
    private val radius = 72f
    private val capTopY = 216f

    private fun centerAt(elevation: Double) = sunCenterY(elevation, horizonY, radius, capTopY)

    @Test fun highSunStopsJustBelowTheIcons() {
        assertEquals(capTopY, centerAt(60.0) - radius, 0.01f)
        assertEquals(capTopY, centerAt(25.0) - radius, 0.01f)
    }

    @Test fun centeredOnTheHorizonAtSunrise() = assertEquals(horizonY, centerAt(0.0), 0.01f)

    @Test fun fullyBelowTheHorizonWhenHidden() = assertEquals(horizonY, centerAt(-4.0) - radius, 0.01f)

    @Test fun risesSteadilyWithElevation() {
        val heights = listOf(-4.0, -2.0, 0.0, 5.0, 12.0, 20.0, 25.0).map { centerAt(it) }
        assertTrue("sun should climb as elevation grows: $heights", heights.zipWithNext().all { (a, b) -> b < a })
    }

    @Test fun capBelowHorizonNeverPushesTheSunDown() =
        assertEquals(horizonY, sunCenterY(30.0, horizonY, radius, capTopY = 400f), 0.01f)
}
