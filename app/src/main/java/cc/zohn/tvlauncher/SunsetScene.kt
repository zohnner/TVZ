package cc.zohn.tvlauncher

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import cc.zohn.tvlauncher.ui.theme.LauncherPalette
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random

// Half of 1080p: smooth when stretched, ~2 MB.
private const val SCENE_W = 960
private const val SCENE_H = 540
private const val SUN_CENTER_X = 0.76f // low-right, never behind the hero text
private const val SUN_RADIUS = 0.11f // of width
private const val SUN_FULL_ELEVATION = 25.0 // degrees at which the sun reaches its high spot
private const val SUN_HIDDEN_BELOW = -4.0
private const val STAR_COUNT = 70
private const val GRID_LINES = 14
private const val GRID_COLUMNS = 22
private const val GRID_STEP_MS = 4_000
private const val GRID_IDLE_MS = 2_000L

/**
 * Zohn's sky, sun, stars, horizon and ground, drawn on the CPU once per mood tick (never per
 * frame). [horizonFraction] is where the hero/dock divider sits, as a fraction of screen height.
 */
fun renderSunsetScene(p: LauncherPalette, sunElevation: Double, horizonFraction: Float): ImageBitmap {
    val bitmap = ImageBitmap(SCENE_W, SCENE_H)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(bitmap), Size(SCENE_W.toFloat(), SCENE_H.toFloat())) {
        val w = size.width
        val horizonY = size.height * horizonFraction
        val sky = Brush.verticalGradient(
            0f to p.sky1, 0.67f to p.sky2, 0.9f to p.sky3, 1f to p.horizon,
            startY = 0f, endY = horizonY,
        )
        drawRect(sky, size = Size(w, horizonY))

        // Stars: fixed positions so they don't jump between renders.
        if (p.starAlpha > 0f) {
            val random = Random(7)
            repeat(STAR_COUNT) {
                val x = random.nextFloat() * w
                val y = random.nextFloat() * horizonY * 0.85f
                drawCircle(Color.White.copy(alpha = p.starAlpha * (0.4f + 0.6f * random.nextFloat())), 0.8f + random.nextFloat(), Offset(x, y))
            }
        }

        // Sun: height follows real elevation; striped lower half; clipped at the horizon.
        if (sunElevation > SUN_HIDDEN_BELOW) {
            val radius = w * SUN_RADIUS
            // Above the horizon the sun climbs with elevation (center on the horizon at 0 degrees);
            // below it sinks linearly so its top edge meets the horizon exactly when it is hidden.
            val centerY = if (sunElevation >= 0.0) {
                val lift = (sunElevation / SUN_FULL_ELEVATION).coerceAtMost(1.0).toFloat()
                horizonY - lift * horizonY * 0.55f
            } else {
                horizonY + radius * (sunElevation / SUN_HIDDEN_BELOW).toFloat()
            }
            val center = Offset(w * SUN_CENTER_X, centerY)
            clipRect(bottom = horizonY) {
                val sunBrush = Brush.verticalGradient(
                    0f to p.sunTop, 0.6f to p.sunMid, 1f to p.sunBottom,
                    startY = center.y - radius, endY = center.y + radius,
                )
                drawCircle(sunBrush, radius, center)
                // Stripe gaps are painted with the sky brush, identical to the sky behind them.
                val stripes = listOf(0.45f to 0.49f, 0.58f to 0.62f, 0.70f to 0.74f, 0.81f to 0.86f)
                val circle = Path().apply { addOval(Rect(center, radius)) }
                clipPath(circle) {
                    for ((from, to) in stripes) {
                        val top = center.y - radius + 2 * radius * from
                        val bottom = center.y - radius + 2 * radius * to
                        drawRect(sky, topLeft = Offset(center.x - radius, top), size = Size(2 * radius, bottom - top))
                    }
                }
            }
        }

        // Ground + horizon line (this is the divider between hero and dock in Zohn's profile).
        drawRect(p.ground, topLeft = Offset(0f, horizonY), size = Size(w, size.height - horizonY))
        drawLine(p.horizon, Offset(0f, horizonY), Offset(w, horizonY), strokeWidth = 2f)

        // Soft dark veil behind the hero text.
        drawRect(
            Brush.radialGradient(
                listOf(p.ground.copy(alpha = 0.5f), Color.Transparent),
                center = Offset(w * 0.22f, size.height * 0.5f),
                radius = w * 0.4f,
            )
        )
    }
    return bitmap
}

/**
 * Perspective grid below the horizon, drawn as ~36 lines per frame (no full-screen fill).
 * Scrolls toward the viewer while the remote is idle; frozen while navigating and when
 * [animate] is false (night).
 */
@Composable
fun GridFloor(p: LauncherPalette, horizonFraction: Float, animate: Boolean, lastInputAt: () -> Long) {
    val offset = remember { Animatable(0f) }
    if (animate) LaunchedEffect(Unit) { scrollWhileIdle(offset, lastInputAt) }

    Canvas(Modifier.fillMaxSize()) {
        if (p.gridAlpha <= 0f) return@Canvas
        val horizonY = size.height * horizonFraction
        val depth = size.height - horizonY
        // Horizontal lines bunch up toward the horizon and fade into it.
        for (i in 0..GRID_LINES) {
            val t = ((i + offset.value) / GRID_LINES).coerceIn(0f, 1f)
            val y = horizonY + depth * t * t
            drawLine(p.gridH.copy(alpha = p.gridH.alpha * p.gridAlpha * t), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
        }
        // Vertical lines converge on a vanishing point at the horizon's center.
        val vanishing = Offset(size.width / 2, horizonY)
        val columnBrush = Brush.verticalGradient(
            listOf(Color.Transparent, p.gridV.copy(alpha = p.gridV.alpha * p.gridAlpha)),
            startY = horizonY, endY = size.height,
        )
        for (i in -GRID_COLUMNS / 2..GRID_COLUMNS / 2) {
            val bottomX = size.width / 2 + i * size.width * 2f / GRID_COLUMNS
            drawLine(columnBrush, vanishing, Offset(bottomX, size.height), strokeWidth = 1.5f)
        }
    }
}

private suspend fun scrollWhileIdle(offset: Animatable<Float, *>, lastInputAt: () -> Long) {
    while (true) {
        val idleFor = SystemClock.uptimeMillis() - lastInputAt()
        if (idleFor < GRID_IDLE_MS) {
            delay(GRID_IDLE_MS - idleFor)
            continue
        }
        coroutineScope {
            val scroll = launch {
                while (true) {
                    offset.animateTo(1f, tween(((1f - offset.value) * GRID_STEP_MS).toInt().coerceAtLeast(1), easing = LinearEasing))
                    offset.snapTo(0f)
                }
            }
            val watcher = launch {
                snapshotFlow { lastInputAt() }.drop(1).first()
                scroll.cancel()
            }
            scroll.join()
            watcher.cancel()
        }
    }
}
