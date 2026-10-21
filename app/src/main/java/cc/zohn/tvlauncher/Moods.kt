package cc.zohn.tvlauncher

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import cc.zohn.tvlauncher.ui.theme.JetBrainsMono
import cc.zohn.tvlauncher.ui.theme.LauncherPalette
import cc.zohn.tvlauncher.ui.theme.Orbitron
import cc.zohn.tvlauncher.ui.theme.Playfair
import java.time.Instant

/** Temporarily replaces the mood palette (game day fills this; see the moods spec §6). */
data class ThemeOverride(val palette: LauncherPalette, val heroLine: String?, val until: Instant) {
    fun activeAt(now: Instant): Boolean = now.isBefore(until)
}

/** WCAG contrast ratio (1..21) between two opaque colors. */
fun contrastRatio(x: Color, y: Color): Float {
    val l1 = x.luminance()
    val l2 = y.luminance()
    return (maxOf(l1, l2) + 0.05f) / (minOf(l1, l2) + 0.05f)
}

private fun isLight(c: Color) = c.luminance() > 0.2f

/**
 * Zohn's pick for Sydney's light<->dark blends ("lean toward ink"): no lily color reads on the
 * mid-grey halfway point, so mix [color] toward the text color just enough to be as readable as
 * the weaker endpoint (capped at 3:1). Endpoints and dark-to-dark blends are left alone.
 */
private fun inked(
    color: Color,
    text: Color,
    background: Color,
    crossesLightDark: Boolean,
    from: Pair<Color, Color>,
    to: Pair<Color, Color>,
): Color {
    if (!crossesLightDark) return color
    val target = minOf(3f, contrastRatio(from.first, from.second), contrastRatio(to.first, to.second))
    for (step in 0..20) {
        val mixed = lerp(color, text, step / 20f)
        if (contrastRatio(mixed, background) >= target) return mixed
    }
    return text
}

fun blendPalettes(a: LauncherPalette, b: LauncherPalette, t: Float): LauncherPalette {
    if (t <= 0f) return a
    if (t >= 1f) return b
    fun c(x: Color, y: Color) = lerp(x, y, t)
    fun f(x: Float, y: Float) = x + (y - x) * t
    val second = t >= 0.5f
    val background = c(a.background, b.background)
    // Foreground colors on a mid-blend light<->dark background would vanish (Sydney): keep whichever
    // endpoint reads better. Dark-to-dark blends (all of Zohn's) just glide; picking would snap.
    val crossesLightDark = isLight(a.background) != isLight(b.background)
    fun readable(x: Color, y: Color) = when {
        !crossesLightDark -> c(x, y)
        contrastRatio(x, background) >= contrastRatio(y, background) -> x
        else -> y
    }
    val text = readable(a.text, b.text)
    return LauncherPalette(
        background = background,
        surface = c(a.surface, b.surface),
        accent = inked(c(a.accent, b.accent), text, background, crossesLightDark, a.accent to a.background, b.accent to b.background),
        glow = inked(c(a.glow, b.glow), text, background, crossesLightDark, a.glow to a.background, b.glow to b.background),
        secondary = c(a.secondary, b.secondary),
        text = text,
        display = if (second) b.display else a.display,
        data = if (second) b.data else a.data,
        wordmark = readable(a.wordmark, b.wordmark),
        ornament = if (second) b.ornament else a.ornament,
        italicGreeting = if (second) b.italicGreeting else a.italicGreeting,
        sky1 = c(a.sky1, b.sky1),
        sky2 = c(a.sky2, b.sky2),
        sky3 = c(a.sky3, b.sky3),
        horizon = c(a.horizon, b.horizon),
        ground = c(a.ground, b.ground),
        sunTop = c(a.sunTop, b.sunTop),
        sunMid = c(a.sunMid, b.sunMid),
        sunBottom = c(a.sunBottom, b.sunBottom),
        gridH = c(a.gridH, b.gridH),
        gridV = c(a.gridV, b.gridV),
        starAlpha = f(a.starAlpha, b.starAlpha),
        gridAlpha = f(a.gridAlpha, b.gridAlpha),
        glowStrength = f(a.glowStrength, b.glowStrength),
    )
}

object Moods {
    fun palette(profileId: String, phase: Phase): LauncherPalette =
        (if (profileId == Profile.SYDNEY.id) SYDNEY else ZOHN).getValue(phase)

    fun effective(profileId: String, state: MoodState): LauncherPalette =
        blendPalettes(palette(profileId, state.from), palette(profileId, state.to), state.blend)

    // Zohn — Sunset grid. Two hues: orange (glow, wordmark) + purple (accent). Spec §2.
    private fun zohn(
        sky1: Long, sky2: Long, sky3: Long, horizon: Long, ground: Long,
        sunTop: Long, sunMid: Long, sunBottom: Long,
        orange: Long, purple: Long, text: Long,
        gridH: Color, gridV: Color, gridAlpha: Float, starAlpha: Float, glowStrength: Float,
    ) = LauncherPalette(
        background = Color(ground),
        surface = Color(ground).copy(alpha = 0.88f),
        accent = Color(purple),
        glow = Color(orange),
        secondary = Color(horizon),
        text = Color(text),
        display = Orbitron,
        data = JetBrainsMono,
        wordmark = Color(orange),
        ornament = false,
        sky1 = Color(sky1), sky2 = Color(sky2), sky3 = Color(sky3),
        horizon = Color(horizon), ground = Color(ground),
        sunTop = Color(sunTop), sunMid = Color(sunMid), sunBottom = Color(sunBottom),
        gridH = gridH, gridV = gridV, gridAlpha = gridAlpha, starAlpha = starAlpha,
        glowStrength = glowStrength,
    )

    private val ZOHN = mapOf(
        Phase.MORNING to zohn(
            0xFF1C0F3A, 0xFF5A2C7A, 0xFFC9668A, 0xFFFFB27A, 0xFF120821,
            0xFFFFE0A3, 0xFFFF9E5E, 0xFFE0567A,
            0xFFFF8A3D, 0xFFD8B4FE, 0xFFFFF1E6,
            gridH = Color(255, 158, 94, 128), gridV = Color(168, 85, 247, 128),
            gridAlpha = 0.55f, starAlpha = 0.15f, glowStrength = 1f,
        ),
        Phase.DAY to zohn(
            0xFF2A0E55, 0xFF6B2A8F, 0xFFB9417F, 0xFFFF7A3D, 0xFF14061F,
            0xFFFFD07A, 0xFFFF7A3D, 0xFFD6336C,
            0xFFFF8A3D, 0xFFD8B4FE, 0xFFFFF1E6,
            gridH = Color(255, 122, 61, 140), gridV = Color(168, 85, 247, 153),
            gridAlpha = 0.8f, starAlpha = 0f, glowStrength = 1f,
        ),
        Phase.EVENING to zohn(
            0xFF170730, 0xFF4A1260, 0xFF9C1F5E, 0xFFFF5E3A, 0xFF100418,
            0xFFFFB347, 0xFFFF5E3A, 0xFFC2185B,
            0xFFFF6B35, 0xFFC4A1F5, 0xFFFFE9DC,
            gridH = Color(255, 94, 58, 153), gridV = Color(168, 85, 247, 140),
            gridAlpha = 0.75f, starAlpha = 0.25f, glowStrength = 1f,
        ),
        Phase.NIGHT to zohn(
            0xFF05030D, 0xFF120826, 0xFF24103D, 0xFF3A1A55, 0xFF06030C,
            0xFFFFB347, 0xFFFF5E3A, 0xFFC2185B, // sun is below the horizon at night
            0xFFB26A3D, 0xFF8E7AB8, 0xFFC9BFD6,
            gridH = Color(168, 85, 247, 89), gridV = Color(168, 85, 247, 77),
            gridAlpha = 0.35f, starAlpha = 0.9f, glowStrength = 0f,
        ),
    )

    // Sydney — Water Lilies. Spec §2.
    private fun sydney(background: Long, surface: Long, accent: Long, glow: Long, text: Long, secondary: Long, glowStrength: Float = 1f) =
        LauncherPalette(
            background = Color(background), surface = Color(surface), accent = Color(accent),
            glow = Color(glow), secondary = Color(secondary), text = Color(text),
            display = Playfair, italicGreeting = true, glowStrength = glowStrength,
        )

    private val SYDNEY = mapOf(
        Phase.MORNING to sydney(0xFFE6E3EE, 0xFFF3F0F2, 0xFF7C9A8A, 0xFF9B86C8, 0xFF2A3440, 0xFFE6A3B4),
        Phase.DAY to sydney(0xFFDCE7EE, 0xFFEEF2EC, 0xFF6E8F7A, 0xFF8C7BC0, 0xFF22313A, 0xFFE6A3B4),
        Phase.EVENING to sydney(0xFFF0DCCB, 0xFFF6E9DE, 0xFF9A7B5C, 0xFFB0679A, 0xFF3A2A2E, 0xFFE6A3B4),
        Phase.NIGHT to sydney(0xFF10202A, 0xFF1B3038, 0xFFA99BD0, 0xFFEBA9BE, 0xFFF4EFE6, 0xFF8FB59A, glowStrength = 0f),
    )
}
