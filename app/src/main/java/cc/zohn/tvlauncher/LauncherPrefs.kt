package cc.zohn.tvlauncher

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private const val KEY_HIDDEN = "hidden"
private const val KEY_RESUMES = "resumes"
private const val KEY_NEVER_TRACK = "neverTrack"
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

    // Resume tiles: the last thing watched per app (written by ResumeProbeListener), as JSON.
    fun resumes(profile: Profile): List<ResumeEntry> =
        decodeResumes(prefs.getString(key(profile, KEY_RESUMES), null))

    fun recordResume(profile: Profile, entry: ResumeEntry) = setResumes(profile, upsert(resumes(profile), entry))

    fun dismissResume(profile: Profile, packageName: String) =
        setResumes(profile, resumes(profile).filter { it.packageName != packageName })

    // Apps the profile never wants a Resume tile for (nothing is recorded for them either).
    fun neverTrack(profile: Profile): Set<String> =
        prefs.getStringSet(key(profile, KEY_NEVER_TRACK), emptySet())!!.toSet()

    fun addNeverTrack(profile: Profile, packageName: String) {
        prefs.edit().putStringSet(key(profile, KEY_NEVER_TRACK), neverTrack(profile) + packageName).apply()
        dismissResume(profile, packageName)
    }

    /** Whether a changed key affects any profile's Resume tiles. */
    fun isResumeKey(changed: String?): Boolean =
        changed != null && listOf(KEY_RESUMES, KEY_NEVER_TRACK).any { changed == it || changed.endsWith(":$it") }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    private fun setResumes(profile: Profile, list: List<ResumeEntry>) =
        prefs.edit().putString(key(profile, KEY_RESUMES), encodeResumes(list)).apply()

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

private fun encodeResumes(list: List<ResumeEntry>): String =
    JSONArray().apply {
        for (e in list) {
            put(JSONObject().apply {
                put("pkg", e.packageName)
                e.title?.let { put("title", it) }
                put("pos", e.positionMs)
                e.durationMs?.let { put("dur", it) }
                put("at", e.watchedAt)
            })
        }
    }.toString()

private fun decodeResumes(json: String?): List<ResumeEntry> {
    if (json == null) return emptyList()
    return try {
        val a = JSONArray(json)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            ResumeEntry(
                packageName = o.getString("pkg"),
                title = if (o.has("title")) o.getString("title") else null,
                positionMs = o.optLong("pos", 0),
                durationMs = o.optLong("dur", 0).takeIf { it > 0 },
                watchedAt = o.optLong("at", 0),
            )
        }
    } catch (e: JSONException) {
        Log.w("TvLauncher", "bad resume list, starting fresh", e)
        emptyList()
    }
}
