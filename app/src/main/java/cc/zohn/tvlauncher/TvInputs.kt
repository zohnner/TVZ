package cc.zohn.tvlauncher

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.util.Log

private const val TAG = "TvLauncher"

data class InputEntry(val id: String, val label: String)

// Physical HDMI ports only; CEC devices behind a port have a parentId and are skipped.
fun loadHdmiInputs(context: Context): List<InputEntry> {
    val manager = context.getSystemService(TvInputManager::class.java) ?: return emptyList()
    return try {
        manager.tvInputList
            .filter { it.type == TvInputInfo.TYPE_HDMI && it.parentId == null }
            .map { info ->
                val custom = info.loadCustomLabel(context)?.toString()
                InputEntry(info.id, custom?.takeIf { it.isNotBlank() } ?: info.loadLabel(context).toString())
            }
            .sortedBy { it.label }
    } catch (e: Exception) {
        Log.w(TAG, "couldn't list TV inputs", e)
        emptyList()
    }
}

private const val CHANNEL_MIME = "vnd.android.cursor.item/channel"

// More than one app can play a passthrough input (here: Google's input player and the Realtek TV
// app), which would pop a chooser every time. Target the highest-ranked handler directly.
fun inputIntent(context: Context, input: InputEntry): Intent {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(TvContract.buildChannelUriForPassthroughInput(input.id), CHANNEL_MIME)
    val handler = context.packageManager.queryIntentActivities(intent, 0).firstOrNull()?.activityInfo
    if (handler != null) intent.setClassName(handler.packageName, handler.name)
    return intent
}
