package cc.zohn.tvlauncher

import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
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
import cc.zohn.tvlauncher.ui.theme.LauncherPalette
import cc.zohn.tvlauncher.ui.theme.LocalPalette
import cc.zohn.tvlauncher.ui.theme.Oxblood
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

// ---- Background: wallpaper → focused-app backdrop → drifting tint → scrim ----
// The TV's Mali-G31 can't shade several full-screen gradients per frame, so each static layer is
// rendered once into a small bitmap and stretched. Smooth gradients survive the upscale, and a
// bitmap blit is cheap.

private const val LAYER_W = 320
private const val LAYER_H = 180

// The tint layer is drawn oversized so its edges never slide into view while it drifts.
private const val TINT_SCALE = 1.5f
private const val DRIFT_SWEEP_MS = 30_000
private const val DRIFT_IDLE_MS = 2_000L

/**
 * [artwork] is today's painting for art-focused profiles (null while it loads).
 * [focusedBackdrop] is read lazily so focus moves recompose only the backdrop, not the screen.
 * [lastInputAt] is the uptime of the last key press (see [runDrift]).
 */
@Composable
fun BackgroundLayers(
    profile: Profile,
    artwork: ImageBitmap?,
    focusedBackdrop: () -> ImageBitmap?,
    lastInputAt: () -> Long,
) {
    if (profile.artFocus) ArtBackground(artwork) else BaroqueBackground(focusedBackdrop, lastInputAt)
}

/**
 * Sydney: the painting full-screen, under a soft mist that keeps text legible. No app backdrop
 * and no drift — the painting is the star — which keeps it at two full-screen layers.
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

/**
 * Zohn: every full-screen layer costs a full 1080p fill on the GPU each frame. Wallpaper, the
 * focused app's backdrop and the scrim never move relative to each other, so they're
 * pre-composited into one small bitmap per focused app (~1 ms on focus change) — two full-screen
 * layers instead of four.
 */
@Composable
private fun BaroqueBackground(focusedBackdrop: () -> ImageBitmap?, lastInputAt: () -> Long) {
    val tint = remember { renderTint() }

    // Layer 1: wallpaper + focused app's banner (tiny copy, so stretching blurs it) + scrim,
    // crossfading on focus change.
    Crossfade(targetState = focusedBackdrop(), label = "backdrop") { backdrop ->
        val base = remember(backdrop) { renderBase(backdrop) }
        StretchedLayer(base)
    }

    // Layer 2: gold / oxblood light, slid slowly via layer translation (no re-render per frame).
    val drift = remember { Animatable(-1f) }
    LaunchedEffect(Unit) { runDrift(drift, lastInputAt) }
    StretchedLayer(
        tint,
        Modifier.graphicsLayer {
            scaleX = TINT_SCALE
            scaleY = TINT_SCALE
            translationX = drift.value * size.width * 0.18f
            translationY = drift.value * size.height * -0.08f
        },
    )
}

/**
 * Drifts the tint back and forth (one sweep per [DRIFT_SWEEP_MS]), but only while the remote is
 * idle. A running animation redraws the whole screen every frame, which steals frames from focus
 * moves on this TV, so any key press freezes the drift until [DRIFT_IDLE_MS] of quiet.
 */
private suspend fun runDrift(drift: Animatable<Float, *>, lastInputAt: () -> Long) {
    var target = 1f
    while (true) {
        val idleFor = SystemClock.uptimeMillis() - lastInputAt()
        if (idleFor < DRIFT_IDLE_MS) {
            delay(DRIFT_IDLE_MS - idleFor)
            continue
        }
        val remaining = abs(target - drift.value)
        if (remaining < 0.001f) {
            target = -target
            continue
        }
        coroutineScope {
            val sweep = launch {
                drift.animateTo(target, tween((remaining / 2f * DRIFT_SWEEP_MS).toInt(), easing = LinearEasing))
            }
            val watcher = launch {
                snapshotFlow { lastInputAt() }.drop(1).first()
                sweep.cancel()
            }
            sweep.join()
            watcher.cancel()
        }
    }
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

// Water-lily pond colors while today's painting downloads (or if the TV is offline with no cache).
private fun renderPondPlaceholder(palette: LauncherPalette) = renderLayer {
    drawRect(palette.background)
    drawRect(
        Brush.radialGradient(
            listOf(palette.accent.copy(alpha = 0.35f), Color.Transparent),
            center = Offset(size.width * 0.3f, size.height * 0.7f),
            radius = size.width * 0.45f,
        )
    )
    drawRect(
        Brush.radialGradient(
            listOf(palette.glow.copy(alpha = 0.3f), Color.Transparent),
            center = Offset(size.width * 0.75f, size.height * 0.3f),
            radius = size.width * 0.4f,
        )
    )
    drawRect(
        Brush.radialGradient(
            listOf(palette.secondary.copy(alpha = 0.25f), Color.Transparent),
            center = Offset(size.width * 0.55f, size.height * 0.55f),
            radius = size.width * 0.25f,
        )
    )
}

// Soft light veil where text sits: clock/date/weather (top right), hero (left), dock (bottom).
private fun renderMist(palette: LauncherPalette) = renderLayer {
    val mist = palette.background
    drawRect(Brush.verticalGradient(0f to mist.copy(alpha = 0.6f), 0.3f to Color.Transparent))
    drawRect(
        Brush.radialGradient(
            listOf(mist.copy(alpha = 0.85f), mist.copy(alpha = 0.6f), Color.Transparent),
            center = Offset(size.width * 0.9f, size.height * 0.2f),
            radius = size.width * 0.28f,
        )
    )
    drawRect(Brush.horizontalGradient(0f to mist.copy(alpha = 0.55f), 0.55f to Color.Transparent))
    drawRect(Brush.verticalGradient(0.5f to Color.Transparent, 1f to mist.copy(alpha = 0.9f)))
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
