package cc.zohn.tvlauncher

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import cc.zohn.tvlauncher.ui.theme.LocalPalette
import kotlinx.coroutines.delay

private const val SLIDE_MS = 20_000L
private const val FADE_MS = 2_000

private class Slide(val art: Artwork, val image: ImageBitmap)

/**
 * Idle gallery wall: paintings shown whole (not cropped) on the profile's background, with a
 * museum-style caption, cross-fading every [SLIDE_MS]. Any key press calls [onDismiss] (and is
 * swallowed, so the press that wakes it doesn't also act on the launcher).
 */
@Composable
fun ArtGallery(
    load: suspend () -> List<Artwork>,
    image: suspend (Artwork) -> ImageBitmap?,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    var slide by remember { mutableStateOf<Slide?>(null) }
    // Holds focus so key presses reach it at all; nothing else is composed meanwhile.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }

    LaunchedEffect(Unit) {
        val works = load()
        if (works.isEmpty()) return@LaunchedEffect
        var index = 0
        while (true) {
            val art = works[index++ % works.size]
            val bitmap = image(art)
            if (bitmap == null) {
                delay(1_000)
                continue
            }
            slide = Slide(art, bitmap)
            delay(SLIDE_MS)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .focusRequester(focus)
            .onKeyEvent {
                if (it.type == KeyEventType.KeyDown) onDismiss()
                true
            }
            .focusable(),
    ) {
        Crossfade(targetState = slide, animationSpec = tween(FADE_MS), label = "gallery") { current ->
            if (current != null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 96.dp, vertical = 48.dp),
                ) {
                    Image(
                        bitmap = current.image,
                        contentDescription = current.art.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                    Column(Modifier.align(Alignment.CenterHorizontally)) {
                        Text(
                            current.art.title,
                            fontFamily = palette.display,
                            fontStyle = FontStyle.Italic,
                            fontSize = 26.sp,
                            color = palette.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                        Text(
                            "${current.art.artist}  ·  ${current.art.date}".uppercase(),
                            fontSize = 14.sp,
                            letterSpacing = 3.sp,
                            color = palette.accent,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                }
            }
        }
    }
}
