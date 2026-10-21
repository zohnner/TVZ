package cc.zohn.tvlauncher

import android.content.Context
import android.util.Log
import java.io.File
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private const val TAG = "TvLauncher"
private const val KEEP_DAYS = 90L

/** Launch history for the future Habits feature. Stays on the TV; nothing reads it yet. */
object UsageLog {
    fun line(at: ZonedDateTime, phase: Phase, profileId: String, packageName: String): String =
        listOf(
            at.toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            at.dayOfWeek.name,
            at.hour.toString(),
            phase.name,
            profileId,
            packageName,
        ).joinToString(",")

    fun trim(lines: List<String>, now: ZonedDateTime): List<String> {
        val cutoff = now.minusDays(KEEP_DAYS).toOffsetDateTime()
        return lines.filter { line ->
            runCatching { OffsetDateTime.parse(line.substringBefore(',')) }.getOrNull()?.isAfter(cutoff) == true
        }
    }

    fun append(context: Context, line: String) {
        try {
            file(context).appendText(line + "\n")
        } catch (e: Exception) {
            Log.w(TAG, "usage log write failed", e)
        }
    }

    fun trimFile(context: Context, now: ZonedDateTime) {
        try {
            val file = file(context)
            if (file.exists()) file.writeText(trim(file.readLines(), now).joinToString("") { it + "\n" })
        } catch (e: Exception) {
            Log.w(TAG, "usage log trim failed", e)
        }
    }

    private fun file(context: Context) = File(context.filesDir, "usage.log")
}
