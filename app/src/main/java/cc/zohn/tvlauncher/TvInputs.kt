package cc.zohn.tvlauncher

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.util.Log

private const val TAG = "TvLauncher"

/**
 * One physical HDMI port. [label] is the port name ("HDMI 3"); [stored] is the user's override
 * (an [InputKind] name or custom text, shared by both profiles); [detected] comes from the CEC
 * device behind the port.
 */
data class InputEntry(
    val id: String,
    val label: String,
    val cecName: String? = null,
    val hasCecDevice: Boolean = false,
    val detected: InputKind = InputKind.UNKNOWN,
    val connected: Boolean = false,
    val stored: String? = null,
) {
    val title: String get() = inputTitle(stored, detected, cecName, label)
    // A kind the user picked wins; a custom name keeps the detected icon.
    val kind: InputKind get() = storedKind(stored) ?: detected
    val menuLabel: String get() = inputMenuLabel(title, label)
    val hiddenKey: String get() = "input:$id"
    val tileVisible: Boolean get() = showInputTile(hasCecDevice, connected, !stored.isNullOrBlank())
}

// TvInputInfo.getHdmiDeviceInfo() is a system API (not in the public SDK), so read it by reflection;
// if the platform refuses, the caller falls back to the input's label with no device type.
private fun cecDeviceInfo(input: TvInputInfo): Pair<String?, Int?>? = runCatching {
    val info = TvInputInfo::class.java.getMethod("getHdmiDeviceInfo").invoke(input) ?: return null
    val name = info.javaClass.getMethod("getDisplayName").invoke(info) as? String
    val type = info.javaClass.getMethod("getDeviceType").invoke(info) as? Int
    name to type
}.getOrNull()

// Physical HDMI ports; a CEC device behind a port is a child input (parentId = the port) and
// only contributes its name and type to the port.
fun loadHdmiInputs(context: Context): List<InputEntry> {
    val manager = context.getSystemService(TvInputManager::class.java) ?: return emptyList()
    return try {
        val hdmi = manager.tvInputList.filter { it.type == TvInputInfo.TYPE_HDMI }
        hdmi.filter { it.parentId == null }
            .map { port ->
                val custom = port.loadCustomLabel(context)?.toString()
                val label = portLabel(custom?.takeIf { it.isNotBlank() } ?: port.loadLabel(context).toString())
                val children = hdmi.filter { it.parentId == port.id }
                val cec = children.firstOrNull()
                val cecInfo = cec?.let { cecDeviceInfo(it) }
                val cecName = cecInfo?.first?.takeIf { it.isNotBlank() }
                    ?: cec?.loadLabel(context)?.toString()?.takeIf { it.isNotBlank() }
                val cecType = cecInfo?.second
                InputEntry(
                    id = port.id,
                    label = label,
                    cecName = cecName,
                    hasCecDevice = cec != null,
                    detected = if (cec != null) inputKind(cecName, cecType) else InputKind.UNKNOWN,
                    connected = manager.getInputState(port.id) == TvInputManager.INPUT_STATE_CONNECTED,
                )
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
