package cc.zohn.tvlauncher

import android.content.Context

private const val KEY_HIDDEN = "hidden"
private const val KEY_PACKAGE_SEQ = "pkgSeq"
private const val KEY_BOOT_COUNT = "bootCount"
private const val COUNT_PREFIX = "count:"

class LauncherPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    var hidden: Set<String>
        get() = prefs.getStringSet(KEY_HIDDEN, emptySet())!!.toSet()
        set(value) = prefs.edit().putStringSet(KEY_HIDDEN, value).apply()

    // Only launches made from Zohn TV are counted (no usage-access permission).
    fun launchCounts(): Map<String, Int> =
        prefs.all.entries
            .filter { it.key.startsWith(COUNT_PREFIX) && it.value is Int }
            .associate { it.key.removePrefix(COUNT_PREFIX) to it.value as Int }

    fun recordLaunch(packageName: String) {
        val key = COUNT_PREFIX + packageName
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    var packageSeq: Int
        get() = prefs.getInt(KEY_PACKAGE_SEQ, 0)
        set(value) = prefs.edit().putInt(KEY_PACKAGE_SEQ, value).apply()

    var bootCount: Int
        get() = prefs.getInt(KEY_BOOT_COUNT, -1)
        set(value) = prefs.edit().putInt(KEY_BOOT_COUNT, value).apply()
}
