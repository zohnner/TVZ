package cc.zohn.tvlauncher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import cc.zohn.tvlauncher.ui.theme.TvzTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "TvLauncher"
private const val GALLERY_IDLE_MS = 10 * 60_000L
private const val SCREENSAVER_MARGIN_MS = 30_000L

class MainActivity : ComponentActivity() {
    private lateinit var prefs: LauncherPrefs
    private var profile by mutableStateOf(Profile.ZOHN)
    private var apps by mutableStateOf<List<AppEntry>>(emptyList())
    private var hidden by mutableStateOf<Set<String>>(emptySet())
    private var showHidden by mutableStateOf(false)
    private var inputs by mutableStateOf<List<InputEntry>>(emptyList())
    private var art by mutableStateOf<DailyArt?>(null)
    // Uptime of the last remote key press. Read only from coroutines/draw, never in composition.
    private val lastInputAt = mutableLongStateOf(SystemClock.uptimeMillis())
    private var refreshJob: Job? = null
    private var artJob: Job? = null
    private var keepAwakeJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = LauncherPrefs(this)
        profile = prefs.activeProfile
        hidden = prefs.hidden(profile)

        // This is the Home screen: Back has nowhere to go.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        val actions = LauncherActions(
            launch = ::launch,
            setHidden = ::setHidden,
            appInfo = {
                start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", it.packageName, null)), "App info")
            },
            toggleShowHidden = { showHidden = !showHidden },
            openInput = { start(inputIntent(this, it), it.label) },
            openSettings = { start(Intent(it), "Settings") },
            switchProfile = ::switchProfile,
            onInput = { lastInputAt.longValue = SystemClock.uptimeMillis() },
            galleryShowing = ::keepAwakeForGallery,
            loadGallery = { ArtRepository.gallery() },
            galleryImage = { ArtRepository.image(this, it) },
        )
        val galleryIdleMs = galleryIdleMs()
        setContent {
            TvzTheme(isInDarkTheme = true) {
                LauncherScreen(
                    profile = profile,
                    apps = apps.filter { showHidden || it.packageName !in hidden },
                    hidden = hidden,
                    showHidden = showHidden,
                    inputs = inputs,
                    art = art,
                    lastInputAt = { lastInputAt.longValue },
                    galleryIdleMs = galleryIdleMs,
                    isResumed = { lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) },
                    actions = actions,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Coming back from an app counts as activity: no gallery the moment you return.
        lastInputAt.longValue = SystemClock.uptimeMillis()
        apps = sortByUse(apps)
        refresh()
        loadArt()
    }

    override fun onPause() {
        super.onPause()
        keepAwakeForGallery(false)
    }

    private fun switchProfile() {
        profile = profile.next()
        prefs.activeProfile = profile
        hidden = prefs.hidden(profile)
        showHidden = false
        apps = sortByUse(apps)
        if (profile.artFocus) loadArt() else art = null // free the painting's memory
    }

    // Draw from the disk cache first, then rescan packages only if something was installed,
    // updated or removed since the last scan.
    private fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = lifecycleScope.launch {
            if (apps.isEmpty()) {
                apps = sortByUse(withContext(Dispatchers.IO) { AppCache.load(this@MainActivity) })
            }
            val stamp = packageStamp()
            if (apps.isEmpty() || stamp.changed) {
                val fresh = withContext(Dispatchers.IO) {
                    loadApps(packageManager, packageName).also { AppCache.save(this@MainActivity, it) }
                }
                apps = sortByUse(fresh)
                Log.i(TAG, "rescanned ${fresh.size} apps")
            }
            // Committed only after a successful rescan, so an interrupted one is retried.
            prefs.bootCount = stamp.bootCount
            prefs.packageSeq = stamp.packageSeq
            if (inputs.isEmpty()) {
                inputs = withContext(Dispatchers.IO) { loadHdmiInputs(this@MainActivity) }
            }
        }
    }

    // Today's painting for art-focused profiles; a no-op if it's already showing.
    private fun loadArt() {
        if (!profile.artFocus || artJob?.isActive == true) return
        artJob = lifecycleScope.launch {
            val daily = ArtRepository.daily(this@MainActivity) ?: return@launch
            if (art?.artwork?.id == daily.id) return@launch
            val image = ArtRepository.image(this@MainActivity, daily) ?: return@launch
            if (profile.artFocus) art = DailyArt(daily, image)
        }
    }

    // The TV's own screensaver starts after screen_off_timeout (10 min here). Start the gallery
    // just before it would, otherwise the system screensaver always wins.
    private fun galleryIdleMs(): Long {
        val screenOff = Settings.System.getLong(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, -1L)
        return if (screenOff > 0) {
            minOf(GALLERY_IDLE_MS, screenOff - SCREENSAVER_MARGIN_MS).coerceAtLeast(60_000L)
        } else {
            GALLERY_IDLE_MS
        }
    }

    // Keep the screen on while the gallery shows, but only until the TV's own sleep timer would
    // have turned it off; then let go so the TV still sleeps on schedule.
    private fun keepAwakeForGallery(showing: Boolean) {
        keepAwakeJob?.cancel()
        if (!showing) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val sleep = Settings.Secure.getLong(contentResolver, "sleep_timeout", -1L)
        val awakeFor = if (sleep > 0) (sleep - galleryIdleMs()).coerceAtLeast(60_000L) else GALLERY_IDLE_MS
        keepAwakeJob = lifecycleScope.launch {
            delay(awakeFor)
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private class PackageStamp(val bootCount: Int, val packageSeq: Int, val changed: Boolean)

    // Package-change sequence numbers restart at every boot, so a new boot always counts as changed.
    private fun packageStamp(): PackageStamp {
        val boot = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
        val sameBoot = boot == prefs.bootCount
        val changes = packageManager.getChangedPackages(if (sameBoot) prefs.packageSeq else 0)
        val seq = changes?.sequenceNumber ?: if (sameBoot) prefs.packageSeq else 0
        return PackageStamp(boot, seq, changed = !sameBoot || changes != null)
    }

    private fun sortByUse(list: List<AppEntry>): List<AppEntry> {
        val counts = prefs.launchCounts(profile)
        return list.sortedWith(
            compareByDescending<AppEntry> { counts[it.packageName] ?: 0 }.thenBy { it.label.lowercase() }
        )
    }

    private fun launch(app: AppEntry) {
        val intent = launchIntentFor(packageManager, app.packageName)
        if (intent == null) {
            Log.w(TAG, "no launch intent: ${app.packageName}")
            Toast.makeText(this, "Can't open ${app.label}", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.recordLaunch(profile, app.packageName)
        start(intent, app.label)
    }

    private fun setHidden(app: AppEntry, hide: Boolean) {
        hidden = if (hide) hidden + app.packageName else hidden - app.packageName
        prefs.setHidden(profile, hidden)
    }

    private fun start(intent: Intent, label: String) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "start failed: $intent", e)
            Toast.makeText(this, "Can't open $label", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Log.w(TAG, "start failed: $intent", e)
            Toast.makeText(this, "Can't open $label", Toast.LENGTH_SHORT).show()
        }
    }
}
