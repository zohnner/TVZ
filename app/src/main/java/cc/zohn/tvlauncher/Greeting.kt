package cc.zohn.tvlauncher

const val GREETING_MS = 4_000L
private const val WAKE_AFTER_MS = 30 * 60_000L

fun greetingFor(phase: Phase, name: String): String = when (phase) {
    Phase.MORNING -> "Good morning, $name"
    Phase.DAY -> "Good afternoon, $name"
    Phase.EVENING -> "Good evening, $name"
    Phase.NIGHT -> "Late night, $name"
}

// Greet only after the screen was really off/asleep for a while, not on every return from an app.
fun shouldGreet(screenOffAtMs: Long?, nowMs: Long): Boolean =
    screenOffAtMs != null && nowMs - screenOffAtMs >= WAKE_AFTER_MS
