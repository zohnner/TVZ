package cc.zohn.tvlauncher

import android.media.session.PlaybackState

internal const val RECORD_AFTER_MS = 2 * 60_000L
private const val KEEP_MS = 7 * 24 * 60 * 60_000L
private const val MAX_VISIBLE = 3

/** The last thing watched in one app, for one profile. [watchedAt] is epoch ms. */
data class ResumeEntry(
    val packageName: String,
    val title: String?,
    val positionMs: Long,
    val durationMs: Long?,
    val watchedAt: Long,
)

/** One entry per app: [entry] replaces any entry for the same package and goes first. */
fun upsert(list: List<ResumeEntry>, entry: ResumeEntry): List<ResumeEntry> =
    listOf(entry) + list.filter { it.packageName != entry.packageName }

/** What the dock shows: watched in the last 7 days, not never-tracked, newest first, at most 3. */
fun visibleResumes(list: List<ResumeEntry>, neverTrack: Set<String>, now: Long): List<ResumeEntry> =
    list.filter { now - it.watchedAt <= KEEP_MS && it.packageName !in neverTrack }
        .sortedByDescending { it.watchedAt }
        .take(MAX_VISIBLE)

/** Only something actually watched for a while is worth resuming. */
fun shouldRecord(playedMs: Long): Boolean = playedMs >= RECORD_AFTER_MS

/**
 * Whether a PlaybackState's position means anything. Apps leaving the player often report
 * STOPPED/NONE with position 0 or -1: saving that would wipe an hour of progress.
 */
fun hasUsablePosition(state: Int, positionMs: Long): Boolean =
    positionMs >= 0 && state != PlaybackState.STATE_STOPPED && state != PlaybackState.STATE_NONE &&
        state != PlaybackState.STATE_ERROR

/** Still watching: buffering and seeking don't end a playing run (no save for each one). */
fun isPlaybackRun(state: Int): Boolean = when (state) {
    PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING,
    PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING,
    PlaybackState.STATE_SKIPPING_TO_NEXT, PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
    PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> true
    else -> false
}

/** Where a playing session is now, [elapsedMs] after its last position update. */
fun extrapolatePosition(positionMs: Long, elapsedMs: Long, speed: Float, durationMs: Long?): Long {
    val at = positionMs + (elapsedMs.coerceAtLeast(0) * speed).toLong()
    return if (durationMs != null) at.coerceIn(0, durationMs) else at.coerceAtLeast(0)
}

/** "12 min in", "1 h 5 min in", or "just started" under a minute. */
fun resumePosition(positionMs: Long): String {
    val minutes = positionMs / 60_000
    if (minutes < 1) return "just started"
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0L -> "$m min in"
        m == 0L -> "$h h in"
        else -> "$h h $m min in"
    }
}

/** Hero line: "Peacock · 12 min in". */
fun resumeSubtitle(appLabel: String, positionMs: Long): String = "$appLabel · ${resumePosition(positionMs)}"
