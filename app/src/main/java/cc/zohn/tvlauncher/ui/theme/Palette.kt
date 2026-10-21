package cc.zohn.tvlauncher.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import cc.zohn.tvlauncher.R

/**
 * Everything that differs visually between profiles and moods. See DESIGN.md and
 * docs/superpowers/specs/2026-10-20-moods-design.md. Scene fields are only used by Zohn's
 * Sunset grid; Sydney's palettes leave them at their defaults.
 */
@Immutable
data class LauncherPalette(
    val background: Color,
    val surface: Color,
    // Frames, lines, date, unfocused icons.
    val accent: Color,
    // Focus frame + glow.
    val glow: Color,
    val secondary: Color,
    val text: Color,
    // Name, clock, hero.
    val display: FontFamily,
    // Small data lines: date, weather, hints.
    val data: FontFamily = FontFamily.Default,
    val wordmark: Color = accent,
    // Draw the ornament divider (Sydney); Zohn's scene draws a horizon instead.
    val ornament: Boolean = true,
    val italicGreeting: Boolean = false,
    val sky1: Color = Color.Transparent,
    val sky2: Color = Color.Transparent,
    val sky3: Color = Color.Transparent,
    val horizon: Color = Color.Transparent,
    val ground: Color = Color.Transparent,
    val sunTop: Color = Color.Transparent,
    val sunMid: Color = Color.Transparent,
    val sunBottom: Color = Color.Transparent,
    val gridH: Color = Color.Transparent,
    val gridV: Color = Color.Transparent,
    val starAlpha: Float = 0f,
    val gridAlpha: Float = 0f,
    // 0 = no focus bloom (night low-glare).
    val glowStrength: Float = 1f,
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Playfair = FontFamily(
    Font(R.font.playfair, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.playfair, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(
        R.font.playfair_italic,
        FontWeight.Normal,
        FontStyle.Italic,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Orbitron = FontFamily(
    Font(R.font.orbitron, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.orbitron, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
)

val LocalPalette = staticCompositionLocalOf<LauncherPalette> { error("No palette provided") }
