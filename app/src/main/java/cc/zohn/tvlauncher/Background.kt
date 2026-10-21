package cc.zohn.tvlauncher

import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import cc.zohn.tvlauncher.ui.theme.LauncherPalette
import cc.zohn.tvlauncher.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "TvLauncher"

// Sydney's static layers are drawn once into small bitmaps and stretched; see DESIGN.md.
private const val LAYER_W = 320
private const val LAYER_H = 180

/**
 * Two full-screen layers at most:
 * Zohn — sunset scene bitmap + line-drawn grid; Sydney — painting + mist.
 */
@Composable
fun BackgroundLayers(
    profile: Profile,
    mood: MoodState,
    artwork: ImageBitmap?,
    horizonFraction: Float,
    lastInputAt: () -> Long,
) {
    if (profile.artFocus) ArtBackground(artwork) else SunsetBackground(mood, horizonFraction, lastInputAt)
}

@Composable
private fun SunsetBackground(mood: MoodState, horizonFraction: Float, lastInputAt: () -> Long) {
    val palette = LocalPalette.current
    // Rendered off the main thread; the previous scene stays up until the new one is ready,
    // and also if a render fails.
    val scene by produceState<ImageBitmap?>(null, palette, mood.sunElevation, horizonFraction) {
        val rendered = withContext(Dispatchers.Default) {
            runCatching { renderSunsetScene(palette, mood.sunElevation, horizonFraction) }
                .onFailure { Log.w(TAG, "sunset scene render failed", it) }
                .getOrNull()
        }
        if (rendered != null) value = rendered
    }
    scene?.let { StretchedLayer(it) }
    GridFloor(palette, horizonFraction, animate = mood.phase != Phase.NIGHT, lastInputAt = lastInputAt)
}

/**
 * Sydney: the painting full-screen, under a soft mist that keeps text legible. No app backdrop
 * and no drift — the painting is the star.
 */
@Composable
private fun ArtBackground(artwork: ImageBitmap?) {
    val palette = LocalPalette.current
    val placeholder = remember(palette) { renderPondPlaceholder(palette) }
    val mist = remember(palette) { renderMist(palette) }

    Crossfade(targetState = artwork, label = "painting") { image ->
        if (image != null) {
            Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            StretchedLayer(placeholder)
        }
    }
    StretchedLayer(mist)
}

@Composable
private fun StretchedLayer(bitmap: ImageBitmap, modifier: Modifier = Modifier) {
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        filterQuality = FilterQuality.Low,
        modifier = Modifier.fillMaxSize().then(modifier),
    )
}

private fun renderLayer(block: DrawScope.() -> Unit): ImageBitmap {
    val bitmap = ImageBitmap(LAYER_W, LAYER_H)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(LAYER_W.toFloat(), LAYER_H.toFloat()), block)
    return bitmap
}

// Water-lily pond colors while the painting downloads (or if the TV is offline with no cache).
private fun renderPondPlaceholder(palette: LauncherPalette) = renderLayer {
    drawRect(palette.background)
    drawRect(Brush.radialGradient(listOf(palette.accent.copy(alpha = 0.35f), Color.Transparent), center = Offset(size.width * 0.3f, size.height * 0.7f), radius = size.width * 0.45f))
    drawRect(Brush.radialGradient(listOf(palette.glow.copy(alpha = 0.3f), Color.Transparent), center = Offset(size.width * 0.75f, size.height * 0.3f), radius = size.width * 0.4f))
    drawRect(Brush.radialGradient(listOf(palette.secondary.copy(alpha = 0.25f), Color.Transparent), center = Offset(size.width * 0.55f, size.height * 0.55f), radius = size.width * 0.25f))
}

// Soft veil where text sits: clock/date/weather (top right), hero (left), dock (bottom).
private fun renderMist(palette: LauncherPalette) = renderLayer {
    val mist = palette.background
    drawRect(Brush.verticalGradient(0f to mist.copy(alpha = 0.6f), 0.3f to Color.Transparent))
    drawRect(Brush.radialGradient(listOf(mist.copy(alpha = 0.85f), mist.copy(alpha = 0.6f), Color.Transparent), center = Offset(size.width * 0.9f, size.height * 0.2f), radius = size.width * 0.28f))
    drawRect(Brush.horizontalGradient(0f to mist.copy(alpha = 0.55f), 0.55f to Color.Transparent))
    drawRect(Brush.verticalGradient(0.5f to Color.Transparent, 1f to mist.copy(alpha = 0.9f)))
}
