package cc.zohn.tvlauncher

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import cc.zohn.tvlauncher.ui.theme.LauncherPalette
import cc.zohn.tvlauncher.ui.theme.LocalPalette
import kotlinx.coroutines.delay

class LauncherActions(
    val launch: (AppEntry) -> Unit,
    val setHidden: (AppEntry, hidden: Boolean) -> Unit,
    val appInfo: (AppEntry) -> Unit,
    val toggleShowHidden: () -> Unit,
    val openInput: (InputEntry) -> Unit,
    val openSettings: (action: String) -> Unit,
    val switchProfile: () -> Unit,
    // Any remote key press (drives the drift pause and the gallery idle timer).
    val onInput: () -> Unit,
    val galleryShowing: (Boolean) -> Unit,
    val loadGallery: suspend () -> List<Artwork>,
    val galleryImage: suspend (Artwork) -> ImageBitmap?,
    val toggleFastDay: () -> Unit,
)

/** Today's painting for an art-focused profile, ready to draw. */
class DailyArt(val artwork: Artwork, val image: ImageBitmap)

/**
 * What has focus. Only [Hero] and the backdrop read it, so moving focus recomposes those two
 * and nothing else on screen — the Mali-G31 TV has no frame budget to spare.
 */
@Stable
private class FocusInfo {
    var app by mutableStateOf<AppEntry?>(null)
    // Label of a focused quick action, shown in the hero instead of an app name.
    var action by mutableStateOf<String?>(null)
}

@Composable
fun LauncherScreen(
    palette: LauncherPalette,
    mood: MoodState,
    profile: Profile,
    apps: List<AppEntry>,
    hidden: Set<String>,
    showHidden: Boolean,
    inputs: List<InputEntry>,
    art: DailyArt?,
    greeting: String?,
    heroLine: String?,
    lastInputAt: () -> Long,
    galleryIdleMs: Long,
    isResumed: () -> Boolean,
    actions: LauncherActions,
) = CompositionLocalProvider(LocalPalette provides palette) {
    val focus = remember { FocusInfo() }
    var menuApp by remember { mutableStateOf<AppEntry?>(null) }
    var inputsOpen by remember { mutableStateOf(false) }
    var refocusDock by remember { mutableIntStateOf(0) }
    var galleryOn by remember(profile) { mutableStateOf(false) }
    // The divider's vertical center as a fraction of the screen: Zohn's horizon sits on it.
    var horizonFraction by remember { mutableFloatStateOf(0.6f) }
    var screenHeight by remember { mutableFloatStateOf(0f) }

    // Art-focused profiles turn into a gallery wall after a stretch of no remote input.
    if (profile.artFocus) {
        LaunchedEffect(profile, galleryIdleMs) {
            while (true) {
                val idle = SystemClock.uptimeMillis() - lastInputAt()
                if (!galleryOn && idle >= galleryIdleMs && isResumed()) galleryOn = true
                delay(if (idle >= galleryIdleMs) 1_000 else galleryIdleMs - idle)
            }
        }
    }
    LaunchedEffect(galleryOn) { actions.galleryShowing(galleryOn) }

    Box(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .onSizeChanged { screenHeight = it.height.toFloat() }
            .onPreviewKeyEvent {
                actions.onInput()
                false
            },
    ) {
        if (galleryOn) {
            // The rest of the UI isn't composed meanwhile, so it costs nothing to draw.
            ArtGallery(
                load = actions.loadGallery,
                image = actions.galleryImage,
                onDismiss = { galleryOn = false },
            )
        } else {
            BackgroundLayers(
                profile = profile,
                mood = mood,
                artwork = art?.image,
                horizonFraction = horizonFraction,
                lastInputAt = lastInputAt,
            )
            Column(Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 36.dp)) {
                TopBar(
                    profile = profile,
                    showInputs = inputs.isNotEmpty(),
                    onInputs = { inputsOpen = true },
                    onOpenSettings = actions.openSettings,
                    onSwitchProfile = actions.switchProfile,
                    onToggleFastDay = actions.toggleFastDay,
                    onActionFocused = { focus.action = it },
                    // Under the name, where the screen is otherwise empty, so the hero keeps its width.
                    belowName = { if (art != null) ArtCard(art.artwork) },
                )
                Spacer(Modifier.weight(1f))
                Hero(focus, fallback = apps.firstOrNull(), hidden = hidden)
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier.onGloballyPositioned {
                        if (screenHeight > 0f) {
                            horizonFraction = (it.positionInRoot().y + it.size.height / 2f) / screenHeight
                        }
                    }
                ) { OrnamentDivider() }
                Dock(
                    apps = apps,
                    hidden = hidden,
                    refocusRequest = refocusDock,
                    onFocused = {
                        focus.app = it
                        focus.action = null
                    },
                    onLaunch = actions.launch,
                    onMenu = { menuApp = it },
                )
            }
        }
    }

    menuApp?.let { app ->
        val isHidden = app.packageName in hidden
        AppMenuDialog(
            app = app,
            isHidden = isHidden,
            showingHidden = showHidden,
            onToggleHidden = {
                actions.setHidden(app, !isHidden)
                // The focused tile disappears when hidden; put focus somewhere real.
                if (!isHidden && !showHidden) refocusDock++
            },
            onAppInfo = { actions.appInfo(app) },
            onToggleShowHidden = {
                actions.toggleShowHidden()
                refocusDock++
            },
            onDismiss = { menuApp = null },
        )
    }
    if (inputsOpen) {
        InputPickerDialog(inputs, onPick = actions.openInput, onDismiss = { inputsOpen = false })
    }
}

@Composable
private fun Hero(focus: FocusInfo, fallback: AppEntry?, hidden: Set<String>) {
    val palette = LocalPalette.current
    val action = focus.action
    val app = focus.app ?: fallback
    Column {
        Text(
            action ?: app?.label ?: "",
            fontFamily = palette.display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 64.sp,
            color = palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            when {
                action != null -> "Press OK"
                app == null -> "Loading…"
                app.packageName in hidden -> "Hidden  ·  hold OK for options"
                else -> "Press OK to open  ·  hold for options"
            },
            fontFamily = palette.data,
            fontSize = 18.sp,
            letterSpacing = 2.sp,
            color = palette.text.copy(alpha = 0.6f),
        )
    }
}

// Museum-label card for today's painting: title, artist, year, and a short fact.
@Composable
private fun ArtCard(art: Artwork) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .width(520.dp)
            .clip(shape)
            .background(palette.surface.copy(alpha = 0.82f))
            .border(1.dp, palette.accent.copy(alpha = 0.5f), shape)
            .padding(22.dp),
    ) {
        Text(
            art.title,
            fontFamily = palette.display,
            fontStyle = FontStyle.Italic,
            fontSize = 24.sp,
            color = palette.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${art.artist}  ·  ${art.date}".uppercase(),
            fontSize = 13.sp,
            letterSpacing = 2.sp,
            color = palette.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        art.fact?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                it,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                color = palette.text.copy(alpha = 0.85f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OrnamentDivider() {
    val palette = LocalPalette.current
    if (!palette.ornament) {
        Spacer(Modifier.fillMaxWidth().height(16.dp))
        return
    }
    val accent = palette.accent
    Canvas(Modifier.fillMaxWidth().height(16.dp)) {
        val y = size.height / 2
        val cx = size.width / 2
        val gap = 14.dp.toPx()
        val d = 5.dp.toPx()
        val fadeLeft = Brush.horizontalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.7f)), 0f, cx - gap)
        val fadeRight = Brush.horizontalGradient(listOf(accent.copy(alpha = 0.7f), Color.Transparent), cx + gap, size.width)
        drawLine(fadeLeft, Offset(0f, y), Offset(cx - gap, y), strokeWidth = 1.dp.toPx())
        drawLine(fadeRight, Offset(cx + gap, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        val diamond = Path().apply {
            moveTo(cx, y - d); lineTo(cx + d, y); lineTo(cx, y + d); lineTo(cx - d, y); close()
        }
        drawPath(diamond, accent)
    }
}
