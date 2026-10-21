package cc.zohn.tvlauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import cc.zohn.tvlauncher.ui.theme.Cinzel
import cc.zohn.tvlauncher.ui.theme.Gold
import cc.zohn.tvlauncher.ui.theme.Ivory
import cc.zohn.tvlauncher.ui.theme.Walnut

private val PanelShape = RoundedCornerShape(14.dp)
private val ButtonShape = RoundedCornerShape(10.dp)

@Composable
fun AppMenuDialog(
    app: AppEntry,
    isHidden: Boolean,
    showingHidden: Boolean,
    onToggleHidden: () -> Unit,
    onAppInfo: () -> Unit,
    onToggleShowHidden: () -> Unit,
    onDismiss: () -> Unit,
) {
    MenuPanel(app.label, onDismiss) {
        MenuButton(if (isHidden) "Unhide" else "Hide from Zohn TV", initialFocus = true) {
            onToggleHidden(); onDismiss()
        }
        MenuButton("App info") { onAppInfo(); onDismiss() }
        MenuButton(if (showingHidden) "Stop showing hidden apps" else "Show hidden apps") {
            onToggleShowHidden(); onDismiss()
        }
        MenuButton("Cancel", onClick = onDismiss)
    }
}

@Composable
fun InputPickerDialog(inputs: List<InputEntry>, onPick: (InputEntry) -> Unit, onDismiss: () -> Unit) {
    MenuPanel("Inputs", onDismiss) {
        inputs.forEachIndexed { index, input ->
            MenuButton(input.label, initialFocus = index == 0) { onPick(input); onDismiss() }
        }
        MenuButton("Cancel", initialFocus = inputs.isEmpty(), onClick = onDismiss)
    }
}

@Composable
private fun MenuPanel(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .width(460.dp)
                .clip(PanelShape)
                .background(Walnut)
                .border(1.dp, Gold.copy(alpha = 0.5f), PanelShape)
                .padding(28.dp),
        ) {
            Text(
                title,
                fontFamily = Cinzel,
                fontSize = 30.sp,
                color = Ivory,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun MenuButton(label: String, initialFocus: Boolean = false, onClick: () -> Unit) {
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .clip(ButtonShape)
            .background(if (focused) Gold.copy(alpha = 0.2f) else Color.Transparent)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Gold else Gold.copy(alpha = 0.15f),
                shape = ButtonShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp),
    ) {
        Text(label, fontSize = 20.sp, color = Ivory)
    }

    if (initialFocus) {
        LaunchedEffect(Unit) {
            // The dialog window attaches a frame or two after composition.
            withFrameNanos { }
            withFrameNanos { }
            runCatching { requester.requestFocus() }
        }
    }
}
