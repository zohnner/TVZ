package cc.zohn.tvlauncher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import cc.zohn.tvlauncher.ui.theme.Cinzel
import cc.zohn.tvlauncher.ui.theme.Gold
import cc.zohn.tvlauncher.ui.theme.Ink
import cc.zohn.tvlauncher.ui.theme.Ivory

class LauncherActions(
    val launch: (AppEntry) -> Unit,
    val setHidden: (AppEntry, hidden: Boolean) -> Unit,
    val appInfo: (AppEntry) -> Unit,
    val toggleShowHidden: () -> Unit,
    val openInput: (InputEntry) -> Unit,
    val openSettings: (action: String) -> Unit,
)

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
    apps: List<AppEntry>,
    hidden: Set<String>,
    showHidden: Boolean,
    inputs: List<InputEntry>,
    actions: LauncherActions,
) {
    val focus = remember { FocusInfo() }
    var menuApp by remember { mutableStateOf<AppEntry?>(null) }
    var inputsOpen by remember { mutableStateOf(false) }
    var refocusDock by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize().background(Ink)) {
        BackgroundLayers(focusedBackdrop = { focus.app?.backdrop })
        Column(Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 36.dp)) {
            TopBar(
                showInputs = inputs.isNotEmpty(),
                onInputs = { inputsOpen = true },
                onOpenSettings = actions.openSettings,
                onActionFocused = { focus.action = it },
            )
            Spacer(Modifier.weight(1f))
            Hero(focus, fallback = apps.firstOrNull(), hidden = hidden)
            Spacer(Modifier.height(20.dp))
            OrnamentDivider()
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
    val action = focus.action
    val app = focus.app ?: fallback
    Column {
        Text(
            action ?: app?.label ?: "",
            fontFamily = Cinzel,
            fontWeight = FontWeight.SemiBold,
            fontSize = 64.sp,
            color = Ivory,
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
            fontSize = 18.sp,
            letterSpacing = 2.sp,
            color = Ivory.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun OrnamentDivider() {
    Canvas(Modifier.fillMaxWidth().height(16.dp)) {
        val y = size.height / 2
        val cx = size.width / 2
        val gap = 14.dp.toPx()
        val d = 5.dp.toPx()
        val fadeLeft = Brush.horizontalGradient(listOf(Color.Transparent, Gold.copy(alpha = 0.7f)), 0f, cx - gap)
        val fadeRight = Brush.horizontalGradient(listOf(Gold.copy(alpha = 0.7f), Color.Transparent), cx + gap, size.width)
        drawLine(fadeLeft, Offset(0f, y), Offset(cx - gap, y), strokeWidth = 1.dp.toPx())
        drawLine(fadeRight, Offset(cx + gap, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        val diamond = Path().apply {
            moveTo(cx, y - d); lineTo(cx + d, y); lineTo(cx, y + d); lineTo(cx - d, y); close()
        }
        drawPath(diamond, Gold)
    }
}
