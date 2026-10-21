package cc.zohn.tvlauncher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import cc.zohn.tvlauncher.ui.theme.TvzTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "TvLauncher"

class MainActivity : ComponentActivity() {
    private lateinit var prefs: LauncherPrefs
    private var apps by mutableStateOf<List<AppEntry>>(emptyList())
    private var hidden by mutableStateOf<Set<String>>(emptySet())
    private var showHidden by mutableStateOf(false)
    private var inputs by mutableStateOf<List<InputEntry>>(emptyList())
    private var refreshJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = LauncherPrefs(this)
        hidden = prefs.hidden

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
        )
        setContent {
            TvzTheme(isInDarkTheme = true) {
                LauncherScreen(
                    apps = apps.filter { showHidden || it.packageName !in hidden },
                    hidden = hidden,
                    showHidden = showHidden,
                    inputs = inputs,
                    actions = actions,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        apps = sortByUse(apps)
        refresh()
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
        val counts = prefs.launchCounts()
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
        prefs.recordLaunch(app.packageName)
        start(intent, app.label)
    }

    private fun setHidden(app: AppEntry, hide: Boolean) {
        hidden = if (hide) hidden + app.packageName else hidden - app.packageName
        prefs.hidden = hidden
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
