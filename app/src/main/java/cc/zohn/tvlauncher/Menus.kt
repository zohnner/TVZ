package cc.zohn.tvlauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import cc.zohn.tvlauncher.ui.theme.LocalPalette

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
    onInput: () -> Unit,
    onDismiss: () -> Unit,
) {
    MenuPanel(app.label, onInput, onDismiss) {
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
fun ResumeMenuDialog(
    item: DockItem.Resume,
    onDismissEntry: () -> Unit,
    onNeverTrack: () -> Unit,
    onInput: () -> Unit,
    onDismiss: () -> Unit,
) {
    MenuPanel(item.title, onInput, onDismiss) {
        MenuButton("Dismiss", initialFocus = true) { onDismissEntry(); onDismiss() }
        MenuButton("Never track ${item.app.label}") { onNeverTrack(); onDismiss() }
        MenuButton("Cancel", onClick = onDismiss)
    }
}

@Composable
fun InputPickerDialog(inputs: List<InputEntry>, onPick: (InputEntry) -> Unit, onInput: () -> Unit, onDismiss: () -> Unit) {
    MenuPanel("Inputs", onInput, onDismiss) {
        inputs.forEachIndexed { index, input ->
            MenuButton(input.menuLabel, initialFocus = index == 0) { onPick(input); onDismiss() }
        }
        MenuButton("Cancel", initialFocus = inputs.isEmpty(), onClick = onDismiss)
    }
}

@Composable
fun InputMenuDialog(
    input: InputEntry,
    isHidden: Boolean,
    onRename: () -> Unit,
    onToggleHidden: () -> Unit,
    onInput: () -> Unit,
    onDismiss: () -> Unit,
) {
    MenuPanel(input.title, onInput, onDismiss) {
        MenuButton("Rename…", initialFocus = true) { onRename(); onDismiss() }
        MenuButton(if (isHidden) "Unhide" else "Hide from Zohn TV") { onToggleHidden(); onDismiss() }
        MenuButton("Cancel", onClick = onDismiss)
    }
}

/** [onPick] gets a kind name, or null for "Use automatic name"; [onType] opens the text dialog. */
@Composable
fun InputRenameDialog(
    input: InputEntry,
    onPick: (String?) -> Unit,
    onType: () -> Unit,
    onInput: () -> Unit,
    onDismiss: () -> Unit,
) {
    MenuPanel("Name for ${input.label}", onInput, onDismiss) {
        InputKind.entries.filter { it != InputKind.UNKNOWN }.forEachIndexed { index, kind ->
            MenuButton(kind.display, initialFocus = index == 0, compact = true) { onPick(kind.name); onDismiss() }
        }
        MenuButton("Type a name…", compact = true) { onType(); onDismiss() }
        MenuButton("Use automatic name", compact = true) { onPick(null); onDismiss() }
        MenuButton("Cancel", compact = true, onClick = onDismiss)
    }
}

@Composable
fun InputNameDialog(input: InputEntry, onSave: (String) -> Unit, onInput: () -> Unit, onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    var text by remember { mutableStateOf(TextFieldValue(input.stored?.takeIf { storedKind(it) == null } ?: "")) }
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    fun save() {
        val name = text.text.trim()
        if (name.isNotEmpty()) onSave(name)
        onDismiss()
    }
    MenuPanel("Name for ${input.label}", onInput, onDismiss) {
        var focused by remember { mutableStateOf(false) }
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = TextStyle(color = palette.text, fontSize = 22.sp),
            cursorBrush = SolidColor(palette.glow),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .focusRequester(field)
                .onFocusChanged { focused = it.isFocused }
                .clip(ButtonShape)
                .background(if (focused) palette.glow.copy(alpha = 0.2f) else Color.Transparent)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) palette.glow else palette.accent.copy(alpha = 0.15f),
                    shape = ButtonShape,
                )
                .padding(horizontal = 20.dp, vertical = 14.dp),
        )
        MenuButton("OK") { save() }
        MenuButton("Cancel", onClick = onDismiss)
    }
    LaunchedEffect(Unit) {
        // The dialog window attaches a frame or two after composition.
        withFrameNanos { }
        withFrameNanos { }
        runCatching { field.requestFocus() }
        keyboard?.show()
    }
}

@Composable
private fun MenuPanel(title: String, onInput: () -> Unit, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    // Dialogs are separate windows: re-provide the palette inside.
    val palette = LocalPalette.current
    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalPalette provides palette) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .onPreviewKeyEvent {
                    // Dialog keys are remote activity too (keeps the greeting/gallery timers honest).
                    if (it.type == KeyEventType.KeyDown) onInput()
                    false
                }
                .width(460.dp)
                .clip(PanelShape)
                .background(palette.surface)
                .border(1.dp, palette.accent.copy(alpha = 0.5f), PanelShape)
                // Long lists (the rename picker) scroll as focus moves down them.
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
        ) {
            Text(
                title,
                fontFamily = palette.display,
                fontSize = 30.sp,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            content()
        }
        }
    }
}

@Composable
private fun MenuButton(label: String, initialFocus: Boolean = false, compact: Boolean = false, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 44.dp else 56.dp)
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .clip(ButtonShape)
            .background(if (focused) palette.glow.copy(alpha = 0.2f) else Color.Transparent)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) palette.glow else palette.accent.copy(alpha = 0.15f),
                shape = ButtonShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp),
    ) {
        Text(label, fontSize = 20.sp, color = palette.text)
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
