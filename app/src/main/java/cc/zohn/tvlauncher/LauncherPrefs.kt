package cc.zohn.tvlauncher

import android.content.Context

private const val KEY_HIDDEN = "hidden"
private const val KEY_PACKAGE_SEQ = "pkgSeq"
private const val KEY_BOOT_COUNT = "bootCount"
private const val KEY_PROFILE = "profile"
private const val COUNT_PREFIX = "count:"
private const val INPUT_NAME_PREFIX = "inputName:"

/**
 * Per-profile keys are prefixed "<profile id>:". Zohn's use the bare keys (his was the only
 * profile at first), so his history carried over untouched.
 */
class LauncherPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    // The TV opens in whoever used it last.
    var activeProfile: Profile
        get() = Profile.fromId(prefs.getString(KEY_PROFILE, null))
        set(value) = prefs.edit().putString(KEY_PROFILE, value.id).apply()

    fun hidden(profile: Profile): Set<String> =
        prefs.getStringSet(key(profile, KEY_HIDDEN), emptySet())!!.toSet()

    fun setHidden(profile: Profile, value: Set<String>) =
        prefs.edit().putStringSet(key(profile, KEY_HIDDEN), value).apply()

    // Only launches made from Zohn TV are counted (no usage-access permission).
    fun launchCounts(profile: Profile): Map<String, Int> {
        val prefix = key(profile, COUNT_PREFIX)
        return prefs.all.entries
            .filter { it.key.startsWith(prefix) && it.value is Int }
            .associate { it.key.removePrefix(prefix) to it.value as Int }
    }

    fun recordLaunch(profile: Profile, packageName: String) {
        val key = key(profile, COUNT_PREFIX + packageName)
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    // Input names describe the physical port, so both profiles share them.
    fun inputName(id: String): String? = prefs.getString(INPUT_NAME_PREFIX + id, null)

    fun setInputName(id: String, value: String?) =
        prefs.edit().apply { if (value == null) remove(INPUT_NAME_PREFIX + id) else putString(INPUT_NAME_PREFIX + id, value) }.apply()

    var packageSeq: Int
        get() = prefs.getInt(KEY_PACKAGE_SEQ, 0)
        set(value) = prefs.edit().putInt(KEY_PACKAGE_SEQ, value).apply()

    var bootCount: Int
        get() = prefs.getInt(KEY_BOOT_COUNT, -1)
        set(value) = prefs.edit().putInt(KEY_BOOT_COUNT, value).apply()

    private fun key(profile: Profile, base: String) =
        if (profile == Profile.ZOHN) base else "${profile.id}:$base"
}
