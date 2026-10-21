package cc.zohn.tvlauncher

/** What kind of device sits behind an HDMI port. Pure naming logic lives here so it can be unit-tested. */
enum class InputKind(val display: String) {
    BLU_RAY("Blu-ray"),
    GAME_CONSOLE("Game console"),
    CABLE_BOX("Cable box"),
    STREAMING_STICK("Streaming stick"),
    SOUNDBAR("Soundbar"),
    PC("PC"),
    CAMERA("Camera"),
    UNKNOWN("Unknown"),
}

private fun tokenRegex(alternatives: String) = Regex("(?<![a-z0-9])(?:$alternatives)(?![a-z0-9])")

private val BLU_RAY_NAME = tokenRegex("bd|bdp|dvd|blu[- ]?ray")
private val GAME_NAME = tokenRegex("playstation|ps[2-5]|xbox|nintendo|switch")
private val STREAMING_NAME = tokenRegex("roku|fire ?tv|chromecast|apple ?tv|shield")

private const val CEC_TUNER = 3
private const val CEC_AUDIO_SYSTEM = 5

/** Name rules win over CEC device-type rules. */
fun inputKind(cecName: String?, cecDeviceType: Int?): InputKind {
    val name = cecName?.lowercase().orEmpty()
    return when {
        BLU_RAY_NAME.containsMatchIn(name) -> InputKind.BLU_RAY
        GAME_NAME.containsMatchIn(name) -> InputKind.GAME_CONSOLE
        STREAMING_NAME.containsMatchIn(name) -> InputKind.STREAMING_STICK
        cecDeviceType == CEC_AUDIO_SYSTEM -> InputKind.SOUNDBAR
        cecDeviceType == CEC_TUNER -> InputKind.CABLE_BOX
        else -> InputKind.UNKNOWN
    }
}

/** The system names ports "HDMI3"; show "HDMI 3". */
fun portLabel(raw: String): String = raw.replace(Regex("^(HDMI)([0-9])", RegexOption.IGNORE_CASE), "$1 $2")

fun showInputTile(hasCecDevice: Boolean, connected: Boolean, userNamed: Boolean): Boolean =
    hasCecDevice || connected || userNamed

/** The stored per-input name is either an [InputKind] name or a custom string. */
fun storedKind(stored: String?): InputKind? = InputKind.entries.firstOrNull { it.name == stored }

fun inputTitle(stored: String?, detected: InputKind, cecName: String?, portLabel: String): String {
    val user = stored?.takeIf { it.isNotBlank() }
    if (user != null) return storedKind(user)?.display ?: user
    if (detected != InputKind.UNKNOWN) return detected.display
    return cecName?.takeIf { it.isNotBlank() } ?: portLabel
}

fun inputMenuLabel(title: String, portLabel: String): String =
    if (title == portLabel) portLabel else "$title · $portLabel"

/** Most-used first, ties alphabetical. */
fun <T> sortedByUse(items: List<T>, counts: Map<String, Int>, key: (T) -> String, title: (T) -> String): List<T> =
    items.sortedWith(compareByDescending<T> { counts[key(it)] ?: 0 }.thenBy { title(it).lowercase() })
