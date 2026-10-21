package cc.zohn.tvlauncher

import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeTest {
    private val day = 24 * 60 * 60_000L
    private val now = 100 * day

    private fun entry(pkg: String, watchedAt: Long = now, title: String? = "Ep") =
        ResumeEntry(pkg, title, positionMs = 60_000, durationMs = null, watchedAt = watchedAt)

    @Test fun upsertAddsNewPackageFirst() {
        val list = listOf(entry("a", now - 2), entry("b", now - 1))
        val result = upsert(list, entry("c"))
        assertEquals(listOf("c", "a", "b"), result.map { it.packageName })
    }

    @Test fun upsertReplacesSamePackage() {
        val list = listOf(entry("a", now - 2, "Old"), entry("b", now - 1))
        val result = upsert(list, entry("a", now, "New"))
        assertEquals(listOf("a", "b"), result.map { it.packageName })
        assertEquals("New", result.first().title)
    }

    @Test fun visibleNewestFirstTopThree() {
        val list = listOf(entry("a", now - 4), entry("b", now - 1), entry("c", now - 3), entry("d", now - 2))
        assertEquals(listOf("b", "d", "c"), visibleResumes(list, emptySet(), now).map { it.packageName })
    }

    @Test fun visibleDropsOlderThanSevenDays() {
        val list = listOf(entry("old", now - 7 * day - 1), entry("edge", now - 7 * day), entry("new", now))
        assertEquals(listOf("new", "edge"), visibleResumes(list, emptySet(), now).map { it.packageName })
    }

    @Test fun visibleDropsNeverTracked() {
        val list = listOf(entry("a", now - 1), entry("b", now))
        assertEquals(listOf("a"), visibleResumes(list, setOf("b"), now).map { it.packageName })
    }

    @Test fun recordsFromTwoMinutes() {
        assertFalse(shouldRecord(119_999))
        assertTrue(shouldRecord(120_000))
        assertTrue(shouldRecord(3_600_000))
    }

    @Test fun subtitleJustStartedUnderOneMinute() {
        assertEquals("Peacock · just started", resumeSubtitle("Peacock", 0))
        assertEquals("Peacock · just started", resumeSubtitle("Peacock", 59_999))
    }

    @Test fun subtitleMinutes() {
        assertEquals("Peacock · 1 min in", resumeSubtitle("Peacock", 60_000))
        assertEquals("Peacock · 12 min in", resumeSubtitle("Peacock", 12 * 60_000 + 59_000))
        assertEquals("Peacock · 59 min in", resumeSubtitle("Peacock", 59 * 60_000))
    }

    @Test fun subtitleHours() {
        assertEquals("Peacock · 1 h 5 min in", resumeSubtitle("Peacock", 65 * 60_000))
        assertEquals("Peacock · 2 h in", resumeSubtitle("Peacock", 120 * 60_000))
    }

    @Test fun stopAndIdleStatesHaveNoUsablePosition() {
        assertTrue(hasUsablePosition(PlaybackState.STATE_PLAYING, 1_000))
        assertTrue(hasUsablePosition(PlaybackState.STATE_PAUSED, 0))
        assertTrue(hasUsablePosition(PlaybackState.STATE_BUFFERING, 5_000))
        assertFalse(hasUsablePosition(PlaybackState.STATE_PAUSED, -1))
        assertFalse(hasUsablePosition(PlaybackState.STATE_STOPPED, 2_640_000))
        assertFalse(hasUsablePosition(PlaybackState.STATE_NONE, 0))
        assertFalse(hasUsablePosition(PlaybackState.STATE_ERROR, 10_000))
    }

    @Test fun bufferingAndSeekingStayInThePlayingRun() {
        for (s in listOf(
            PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING,
            PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING,
            PlaybackState.STATE_SKIPPING_TO_NEXT, PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
        )) assertTrue("$s", isPlaybackRun(s))
        for (s in listOf(PlaybackState.STATE_PAUSED, PlaybackState.STATE_STOPPED, PlaybackState.STATE_NONE, PlaybackState.STATE_ERROR)) {
            assertFalse("$s", isPlaybackRun(s))
        }
    }

    @Test fun extrapolatesWhilePlaying() {
        assertEquals(70_000, extrapolatePosition(60_000, elapsedMs = 10_000, speed = 1f, durationMs = null))
        assertEquals(80_000, extrapolatePosition(60_000, elapsedMs = 10_000, speed = 2f, durationMs = null))
    }

    @Test fun extrapolationClampsToDurationAndIgnoresNegativeElapsed() {
        assertEquals(100_000, extrapolatePosition(95_000, elapsedMs = 60_000, speed = 1f, durationMs = 100_000))
        assertEquals(60_000, extrapolatePosition(60_000, elapsedMs = -5_000, speed = 1f, durationMs = null))
    }

    @Test fun positionTextAlone() {
        assertEquals("12 min in", resumePosition(12 * 60_000))
        assertEquals("just started", resumePosition(-5))
    }
}
