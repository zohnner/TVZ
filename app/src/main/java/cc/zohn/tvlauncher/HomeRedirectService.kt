package cc.zohn.tvlauncher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent

private const val TAG = "TvLauncher"
private const val DEBOUNCE_MS = 500L

/**
 * Google TV routes the Home key straight to its own launcher even when another app holds the
 * HOME role. Disabling the stock launcher is off-limits (RULES.md), so instead: when the stock
 * launcher's *home screen* comes to the front, bring Zohn TV forward.
 *
 * Only the exact stock HOME activity is matched, so the stock launcher's other screens
 * (quick settings, input picker, etc.) keep working. Turning this service off in
 * Settings > Accessibility, or uninstalling the app, restores stock behavior.
 */
class HomeRedirectService : AccessibilityService() {
    private var stockHomes: Set<ComponentName> = emptySet()
    private var lastRedirectAt = 0L

    override fun onServiceConnected() {
        stockHomes = findStockHomes()
        Log.i(TAG, "home redirect watching $stockHomes")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: return
        if (ComponentName(pkg, cls) !in stockHomes) return

        val now = SystemClock.uptimeMillis()
        if (now - lastRedirectAt < DEBOUNCE_MS) return
        lastRedirectAt = now

        Log.i(TAG, "stock home shown ($cls), redirecting")
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        )
    }

    override fun onInterrupt() = Unit

    // The stock launcher is the other HOME app with the highest priority; fallback/recovery homes
    // rank lower and are deliberately left alone.
    private fun findStockHomes(): Set<ComponentName> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val others = packageManager.queryIntentActivities(home, 0)
            .filter { it.activityInfo.packageName != packageName }
        val top = others.maxOfOrNull { it.priority } ?: return emptySet()
        return others
            .filter { it.priority == top }
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
            .toSet()
    }
}
