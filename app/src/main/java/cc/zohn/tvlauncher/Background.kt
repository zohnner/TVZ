package cc.zohn.tvlauncher

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import cc.zohn.tvlauncher.ui.theme.Gold
import cc.zohn.tvlauncher.ui.theme.Ink
import cc.zohn.tvlauncher.ui.theme.Oxblood

// ---- Background: wallpaper → focused-app backdrop → drifting tint → scrim ----
// The TV's Mali-G31 can't shade several full-screen gradients per frame, so each static layer is
// rendered once into a small bitmap and stretched. Smooth gradients survive the upscale, and a
// bitmap blit is cheap.

private const val LAYER_W = 320
private const val LAYER_H = 180

// The tint layer is drawn oversized so its edges never slide into view while it drifts.
private const val TINT_SCALE = 1.5f

/**
 * Every full-screen layer costs a full 1080p fill on the GPU each frame. Wallpaper, the focused
 * app's backdrop and the scrim never move relative to each other, so they're pre-composited into
 * one small bitmap per focused app (~1 ms on focus change) — two full-screen layers instead of four.
 *
 * [focusedBackdrop] is read lazily so focus moves recompose only this, not the whole screen.
 */
@Composable
fun BackgroundLayers(focusedBackdrop: () -> ImageBitmap?) {
    val tint = remember { renderTint() }

    // Layer 1: wallpaper + focused app's banner (tiny copy, so stretching blurs it) + scrim,
    // crossfading on focus change.
    Crossfade(targetState = focusedBackdrop(), label = "backdrop") { backdrop ->
        val base = remember(backdrop) { renderBase(backdrop) }
        StretchedLayer(base)
    }

    // Layer 2: gold / oxblood light, slid slowly via layer translation (no re-render per frame).
    val drift by rememberInfiniteTransition(label = "drift").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(30_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "driftT",
    )
    StretchedLayer(
        tint,
        Modifier.graphicsLayer {
            scaleX = TINT_SCALE
            scaleY = TINT_SCALE
            translationX = drift * size.width * 0.18f
            translationY = drift * size.height * -0.08f
        },
    )
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
    CanvasDrawScope().draw(
        Density(1f),
        LayoutDirection.Ltr,
        Canvas(bitmap),
        Size(LAYER_W.toFloat(), LAYER_H.toFloat()),
        block,
    )
    return bitmap
}

// Wallpaper → backdrop → scrim, flattened. Wallpaper is a placeholder until Zohn supplies an
// image (DESIGN.md); a real photo will need its own full-resolution layer underneath.
private fun renderBase(backdrop: ImageBitmap?) = renderLayer {
    drawRect(Ink)
    drawRect(
        Brush.radialGradient(
            0f to Oxblood.copy(alpha = 0.45f),
            1f to Color.Transparent,
            center = Offset(size.width * 0.73f, size.height * 0.18f),
            radius = size.width * 0.95f,
        )
    )
    if (backdrop != null) {
        drawImage(
            backdrop,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            alpha = 0.22f,
            filterQuality = FilterQuality.Low,
        )
    }
    // Scrim: pulls bright backdrops back toward the gold-on-black palette and keeps text legible.
    drawRect(Ink.copy(alpha = 0.35f))
    drawRect(
        Brush.verticalGradient(
            0f to Ink.copy(alpha = 0.55f),
            0.25f to Color.Transparent,
            0.5f to Color.Transparent,
            1f to Ink.copy(alpha = 0.92f),
        )
    )
}

// Sits above the scrim now (it used to sit below), so it's a touch fainter than before.
private fun renderTint() = renderLayer {
    drawRect(
        Brush.radialGradient(
            listOf(Gold.copy(alpha = 0.08f), Color.Transparent),
            center = Offset(size.width * 0.38f, size.height * 0.4f),
            radius = size.width * 0.32f,
        )
    )
    drawRect(
        Brush.radialGradient(
            listOf(Oxblood.copy(alpha = 0.16f), Color.Transparent),
            center = Offset(size.width * 0.62f, size.height * 0.6f),
            radius = size.width * 0.34f,
        )
    )
}
