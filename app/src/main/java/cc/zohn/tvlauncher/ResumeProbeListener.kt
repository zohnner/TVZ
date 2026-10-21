package cc.zohn.tvlauncher

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.util.Log

private const val TAG = "TvLauncher"
private const val MIN_WRITE_GAP_MS = 30_000L
private const val PERIODIC_SAVE_MS = 5 * 60_000L

/**
 * Records what's playing for the Resume tiles, from other apps' media sessions (the TV's Watch
 * Next provider stays empty). Reading sessions needs notification access, which Zohn granted on
 * the TV to this exact component: keep the class name, the feasibility probe's, or access is lost.
 *
 * Playing time is wall time while PLAYING; position ticks (~2/s) do nothing but a state compare.
 */
class ResumeProbeListener : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { LauncherPrefs(this) }
    private var sessionManager: MediaSessionManager? = null
    private val tracked = HashMap<MediaSession.Token, Tracked>()
    // Per package: last write (elapsed ms), and the latest entry waiting out the 30 s gap.
    private val lastWriteAt = HashMap<String, Long>()
    private val pending = HashMap<String, Pair<Profile, ResumeEntry>>()
    private val pendingRuns = HashMap<String, Runnable>()
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { sync(it.orEmpty()) }

    override fun onListenerConnected() {
        try {
            val manager = getSystemService(MediaSessionManager::class.java)
            val component = ComponentName(this, ResumeProbeListener::class.java)
            sessionManager = manager
            sync(manager.getActiveSessions(component))
            manager.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
        } catch (e: RuntimeException) {
            // SecurityException without notification access; anything else must not crash the service.
            Log.w(TAG, "resume: no media session access", e)
        }
    }

    override fun onListenerDisconnected() {
        sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        sessionManager = null
        tracked.values.forEach { it.end() }
        tracked.clear()
        // Flush what was waiting: a disconnect is no reason to lose the final position.
        pendingRuns.values.forEach(handler::removeCallbacks)
        pendingRuns.clear()
        pending.keys.toList().forEach(::writePending)
    }

    private fun sync(list: List<MediaController>) {
        val live = list.map { it.sessionToken }.toSet()
        tracked.keys.filter { it !in live }.forEach { tracked.remove(it)?.end() }
        for (c in list) {
            if (c.packageName == packageName || c.sessionToken in tracked) continue
            tracked[c.sessionToken] = Tracked(c).also { it.start() }
        }
    }

    // At most one write per app per 30 s; a write inside the gap is deferred to its end, latest wins.
    private fun requestWrite(profile: Profile, entry: ResumeEntry) {
        val pkg = entry.packageName
        pending[pkg] = profile to entry
        if (pkg in pendingRuns) return
        val wait = (lastWriteAt[pkg] ?: Long.MIN_VALUE / 2) + MIN_WRITE_GAP_MS - SystemClock.elapsedRealtime()
        if (wait <= 0) {
            writePending(pkg)
        } else {
            val run = Runnable { pendingRuns.remove(pkg); writePending(pkg) }
            pendingRuns[pkg] = run
            handler.postDelayed(run, wait)
        }
    }

    private fun writePending(pkg: String) {
        val (profile, entry) = pending.remove(pkg) ?: return
        lastWriteAt[pkg] = SystemClock.elapsedRealtime()
        if (pkg in prefs.neverTrack(profile) || entry.watchedAt <= prefs.resumeDismissedAt(profile, pkg)) return
        prefs.recordResume(profile, entry)
        Log.i(TAG, "resume: $pkg \"${entry.title}\" at ${entry.positionMs / 1000}s for ${profile.id}")
    }

    /**
     * One media session. Playing time counts only while PLAYING, per title. Saves happen when a
     * playing run ends (pause/stop; buffering and seeking don't count), when the title changes
     * (for the old title), at the 2-minute mark, and every 5 min while playing (a TV switched off
     * mid-show never sends a pause).
     */
    private inner class Tracked(private val controller: MediaController) : MediaController.Callback() {
        private val pkg = controller.packageName
        // Last state with a real position; STOPPED/NONE often report 0.
        private var lastGood: PlaybackState? = null
        private var inRun = false
        private var playingSince: Long? = null
        private var playedMs = 0L
        private var title = titleOf(controller.metadata)
        private var durationMs = durationOf(controller.metadata)
        private val tick = object : Runnable {
            override fun run() {
                save()
                scheduleTick()
            }
        }

        fun start() {
            controller.registerCallback(this, handler)
            onPlaybackStateChanged(controller.playbackState)
        }

        fun end() {
            stopCounting()
            controller.unregisterCallback(this)
            save()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            val s = state?.state ?: PlaybackState.STATE_NONE
            if (state != null && hasUsablePosition(s, state.position)) lastGood = state
            val playing = s == PlaybackState.STATE_PLAYING
            if (playing && playingSince == null) {
                playingSince = SystemClock.elapsedRealtime()
                scheduleTick()
            } else if (!playing && playingSince != null) {
                stopCounting()
            }
            val wasInRun = inRun
            inRun = isPlaybackRun(s)
            if (wasInRun && !inRun) save() // paused / stopped
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            // Teardown and episode switches can send empty metadata: keep what we know.
            val newDuration = durationOf(metadata)
            val newTitle = titleOf(metadata) ?: run { if (newDuration != null) durationMs = newDuration; return }
            if (title == null || newTitle == title) {
                title = newTitle
                if (newDuration != null) durationMs = newDuration
                return
            }
            // Next episode: the old one is saved where it ended; the new one earns its own 2 minutes.
            save()
            title = newTitle
            durationMs = newDuration
            lastGood = null
            playedMs = 0
            if (playingSince != null) {
                playingSince = SystemClock.elapsedRealtime()
                scheduleTick()
            }
        }

        override fun onSessionDestroyed() {
            tracked.remove(controller.sessionToken)
            end()
        }

        // Next save while playing: at the 2-minute mark, then every 5 min.
        private fun scheduleTick() {
            handler.removeCallbacks(tick)
            if (playingSince == null) return
            val played = played()
            handler.postDelayed(tick, if (shouldRecord(played)) PERIODIC_SAVE_MS else RECORD_AFTER_MS - played)
        }

        private fun stopCounting() {
            handler.removeCallbacks(tick)
            playingSince?.let { playedMs += SystemClock.elapsedRealtime() - it }
            playingSince = null
        }

        private fun played() = playedMs + (playingSince?.let { SystemClock.elapsedRealtime() - it } ?: 0L)

        private fun save() {
            if (!shouldRecord(played())) return
            val entry = ResumeEntry(pkg, title, positionNow(lastGood, durationMs), durationMs, System.currentTimeMillis())
            requestWrite(prefs.activeProfile, entry)
        }
    }
}

private fun titleOf(m: MediaMetadata?): String? =
    listOf(MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        .firstNotNullOfOrNull { k -> m?.getString(k)?.trim()?.takeIf { it.isNotEmpty() } }

private fun durationOf(m: MediaMetadata?): Long? =
    m?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 }

// PlaybackState.position is as of lastPositionUpdateTime; extrapolate while playing.
private fun positionNow(s: PlaybackState?, durationMs: Long?): Long {
    if (s == null) return 0
    if (s.state != PlaybackState.STATE_PLAYING) return s.position
    return extrapolatePosition(s.position, SystemClock.elapsedRealtime() - s.lastPositionUpdateTime, s.playbackSpeed, durationMs)
}
