package cc.zohn.tvlauncher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Input
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Videocam
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import cc.zohn.tvlauncher.ui.theme.LocalPalette

private val TileShape = RoundedCornerShape(10.dp)
private val TileWidth = 208.dp

/** What a dock tile can be: an app, or an HDMI input. [key] is the id used for counts and hiding. */
sealed interface DockItem {
    val key: String
    val title: String

    data class App(val app: AppEntry) : DockItem {
        override val key get() = app.packageName
        override val title get() = app.label
    }

    data class Input(val input: InputEntry) : DockItem {
        override val key get() = input.hiddenKey
        override val title get() = input.title
    }
}

/**
 * @param refocusRequest bump to move focus back to the first tile (e.g. after the focused app is hidden).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Dock(
    items: List<DockItem>,
    hidden: Set<String>,
    refocusRequest: Int,
    onFocused: (DockItem) -> Unit,
    onLaunch: (DockItem) -> Unit,
    onMenu: (DockItem) -> Unit,
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
        itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
            DockTile(
                isHidden = item.key in hidden,
                focusRequester = if (index == 0) firstTile else null,
                onFocused = { onFocused(item) },
                onClick = { onLaunch(item) },
                onMenu = { onMenu(item) },
            ) { focused ->
                when (item) {
                    is DockItem.App -> AppTileContent(item.app)
                    is DockItem.Input -> InputTileContent(item.input, focused)
                }
            }
        }
    }

    // Focus the first tile on first load, and whenever a refocus is requested.
    LaunchedEffect(items.isNotEmpty(), refocusRequest) {
        if (items.isEmpty() || (initialFocusDone && refocusRequest == 0)) return@LaunchedEffect
        withFrameNanos { }
        if (runCatching { firstTile.requestFocus() }.isSuccess) initialFocusDone = true
    }
}

@Composable
private fun DockTile(
    isHidden: Boolean,
    focusRequester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
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
                if (glow > 0f && palette.glowStrength > 0f) {
                    val steps = 8
                    val spread = 18.dp.toPx()
                    for (i in steps downTo 1) {
                        val grow = spread * i / steps
                        drawRoundRect(
                            color = palette.glow.copy(alpha = 0.07f * glow * palette.glowStrength),
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
        content(focused)
    }
}

@Composable
private fun AppTileContent(app: AppEntry) {
    if (app.isBanner) {
        Image(app.image, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } else {
        Image(app.image, app.label, Modifier.fillMaxHeight(0.62f).aspectRatio(1f))
    }
}

private fun InputKind.icon(): ImageVector = when (this) {
    InputKind.BLU_RAY -> Icons.Outlined.Album
    InputKind.GAME_CONSOLE -> Icons.Outlined.SportsEsports
    InputKind.CABLE_BOX -> Icons.Outlined.LiveTv
    InputKind.STREAMING_STICK -> Icons.Outlined.Cast
    InputKind.SOUNDBAR -> Icons.Outlined.Speaker
    InputKind.PC -> Icons.Outlined.Computer
    InputKind.CAMERA -> Icons.Outlined.Videocam
    InputKind.UNKNOWN -> Icons.Outlined.Input
}

// Drawn in the active palette only: surface is the tile's background (set by the shared chrome).
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BoxScope.InputTileContent(input: InputEntry, focused: Boolean) {
    val palette = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 10.dp)) {
        Icon(
            input.kind.icon(),
            contentDescription = input.title,
            tint = if (focused) palette.text else palette.accent,
            modifier = Modifier.size(52.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            input.title,
            fontFamily = palette.display,
            fontSize = 20.sp,
            color = palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            input.label,
            fontFamily = palette.data,
            fontSize = 13.sp,
            color = palette.text.copy(alpha = 0.7f),
            maxLines = 1,
        )
    }
}
