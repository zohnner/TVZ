package cc.zohn.tvlauncher

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZonedDateTime

class UsageLogTest {
    private val now = ZonedDateTime.parse("2026-10-20T21:14:03-05:00[America/Chicago]")

    @Test fun formatsOneCsvLine() {
        assertEquals(
            "2026-10-20T21:14:03-05:00,TUESDAY,21,EVENING,zohn,com.netflix.ninja",
            UsageLog.line(now, Phase.EVENING, "zohn", "com.netflix.ninja"),
        )
    }

    @Test fun trimKeepsNinetyDaysAndDropsGarbage() {
        val old = UsageLog.line(now.minusDays(91), Phase.DAY, "zohn", "a")
        val recent = UsageLog.line(now.minusDays(89), Phase.DAY, "sydney", "b")
        assertEquals(listOf(recent), UsageLog.trim(listOf(old, "not,a,valid,line", recent), now))
    }
}
