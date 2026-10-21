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

/** Everything that differs visually between profiles. See DESIGN.md. */
@Immutable
data class LauncherPalette(
    val background: Color,
    val surface: Color,
    // Ornament, frames, unfocused icons.
    val accent: Color,
    // Focus frame + glow.
    val glow: Color,
    val secondary: Color,
    val text: Color,
    val display: FontFamily,
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

// Zohn: baroque-futurist, gold on black.
val ZohnPalette = LauncherPalette(
    background = Ink,
    surface = Walnut,
    accent = Gold,
    glow = Gold,
    secondary = Oxblood,
    text = Ivory,
    display = Cinzel,
)

// Sydney: Monet's Water Lilies, daylight pond.
val SydneyPalette = LauncherPalette(
    background = Color(0xFFDCE7EE), // pale water / sky
    surface = Color(0xFFEEF2EC),    // mist
    accent = Color(0xFF6E8F7A),     // sage green
    glow = Color(0xFF8C7BC0),       // violet
    secondary = Color(0xFFE6A3B4),  // lily pink
    text = Color(0xFF22313A),       // deep blue-grey
    display = Playfair,
)

val LocalPalette = staticCompositionLocalOf { ZohnPalette }
