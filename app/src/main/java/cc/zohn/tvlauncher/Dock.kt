package cc.zohn.tvlauncher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import cc.zohn.tvlauncher.ui.theme.LocalPalette

private val TileShape = RoundedCornerShape(10.dp)
private val TileWidth = 208.dp

/**
 * @param refocusRequest bump to move focus back to the first tile (e.g. after the focused app is hidden).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Dock(
    apps: List<AppEntry>,
    hidden: Set<String>,
    refocusRequest: Int,
    onFocused: (AppEntry) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
) {
    val firstTile = remember { FocusRequester() }
    var initialFocusDone by remember { mutableStateOf(false) }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(26.dp),
        // Room for the focused tile's scale + glow so it isn't clipped.
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 34.dp),
        // Coming back down from the quick actions lands on the tile you left, not the nearest one.
        modifier = Modifier.fillMaxWidth().focusRestorer(firstTile),
    ) {
        itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
            DockTile(
                app = app,
                isHidden = app.packageName in hidden,
                focusRequester = if (index == 0) firstTile else null,
                onFocused = { onFocused(app) },
                onClick = { onLaunch(app) },
                onMenu = { onMenu(app) },
            )
        }
    }

    // Focus the first tile on first load, and whenever a refocus is requested.
    LaunchedEffect(apps.isNotEmpty(), refocusRequest) {
        if (apps.isEmpty() || (initialFocusDone && refocusRequest == 0)) return@LaunchedEffect
        withFrameNanos { }
        if (runCatching { firstTile.requestFocus() }.isSuccess) initialFocusDone = true
    }
}

@Composable
private fun DockTile(
    app: AppEntry,
    isHidden: Boolean,
    focusRequester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onMenu: () -> Unit,
) {
    val palette = LocalPalette.current
    var focused by remember { mutableStateOf(false) }
    // OK only counts if this tile saw the press start (a release left over from closing the gallery
    // or a dialog must not launch anything), and not once a held OK has opened the menu.
    var pressed by remember { mutableStateOf(false) }
    var longPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "tileScale")
    val glow by animateFloatAsState(if (focused) 1f else 0f, label = "tileGlow")
    val restAlpha = if (isHidden) 0.3f else 0.72f

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(TileWidth)
            .aspectRatio(16f / 9f)
            // Scale/alpha/glow are read only in layer and draw blocks, so the focus animation
            // re-draws the tile without recomposing or re-laying-out anything.
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = restAlpha + (1f - restAlpha) * glow
            }
            // Bloom: stacked rounded rects fading outward, kept inside the dock's padding so it
            // isn't clipped. (Cheaper on the Mali-G31 than an animated shadow.)
            .drawBehind {
                if (glow > 0f) {
                    val steps = 8
                    val spread = 18.dp.toPx()
                    for (i in steps downTo 1) {
                        val grow = spread * i / steps
                        drawRoundRect(
                            color = palette.glow.copy(alpha = 0.07f * glow),
                            topLeft = Offset(-grow, -grow),
                            size = Size(size.width + grow * 2, size.height + grow * 2),
                            cornerRadius = CornerRadius(10.dp.toPx() + grow),
                        )
                    }
                }
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            // OK = open, hold OK or Menu = app menu.
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                when {
                    event.key == Key.Menu -> {
                        if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) onMenu()
                        true
                    }
                    event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter ->
                        when (event.type) {
                            KeyEventType.KeyDown -> {
                                if (native.repeatCount == 0) {
                                    pressed = true
                                    longPressed = false
                                } else if (pressed && !longPressed) {
                                    longPressed = true
                                    onMenu()
                                }
                                true
                            }
                            KeyEventType.KeyUp -> {
                                if (pressed && !longPressed) onClick()
                                pressed = false
                                longPressed = false
                                true
                            }
                            else -> false
                        }
                    else -> false
                }
            }
            .focusable()
            .clip(TileShape)
            .background(palette.surface)
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) palette.glow else palette.accent.copy(alpha = 0.22f),
                shape = TileShape,
            ),
    ) {
        if (app.isBanner) {
            Image(app.image, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Image(app.image, app.label, Modifier.fillMaxHeight(0.62f).aspectRatio(1f))
        }
    }
}
