package cc.zohn.tvlauncher

import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Input
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import cc.zohn.tvlauncher.ui.theme.LocalPalette
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val WEATHER_REFRESH_MS = 30 * 60_000L

// ---- Top bar: profile name (left); clock, weather, quick actions (right) ----

@Composable
fun TopBar(
    profile: Profile,
    showInputs: Boolean,
    onInputs: () -> Unit,
    onOpenSettings: (action: String) -> Unit,
    onSwitchProfile: () -> Unit,
    onActionFocused: (label: String) -> Unit,
    onToggleFastDay: () -> Unit,
    belowName: @Composable () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column {
            ProfileName(profile, onSwitchProfile, onActionFocused, onToggleFastDay)
            Spacer(Modifier.height(18.dp))
            belowName()
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Clock()
            WeatherLine()
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (showInputs) QuickAction(Icons.Outlined.Input, "Inputs", onActionFocused, onInputs)
                QuickAction(Icons.Outlined.Wifi, "Wi-Fi", onActionFocused) {
                    onOpenSettings(Settings.ACTION_WIFI_SETTINGS)
                }
                QuickAction(Icons.Outlined.Bluetooth, "Bluetooth", onActionFocused) {
                    onOpenSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
                }
                QuickAction(Icons.Outlined.Settings, "Settings", onActionFocused) {
                    onOpenSettings(Settings.ACTION_SETTINGS)
                }
            }
        }
    }
}

// The wordmark doubles as the profile switch: focus it (Up, then Left) and press OK.
@Composable
private fun ProfileName(
    profile: Profile,
    onSwitch: () -> Unit,
    onFocused: (String) -> Unit,
    onToggleFastDay: () -> Unit,
) {
    val palette = LocalPalette.current
    var focused by remember { mutableStateOf(false) }
    // Re-announce after a switch: focus stays here, so onFocusChanged doesn't fire again.
    LaunchedEffect(profile, focused) {
        if (focused) onFocused("Switch to ${profile.next().title}")
    }
    Text(
        profile.wordmark,
        fontFamily = palette.display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        letterSpacing = 8.sp,
        color = if (focused) palette.glow else palette.wordmark,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            // Debug: hold Menu on the name to play a whole day in two minutes.
            .onPreviewKeyEvent {
                if (it.key == Key.Menu && it.type == KeyEventType.KeyDown && it.nativeKeyEvent.repeatCount == 1) {
                    onToggleFastDay()
                    true
                } else {
                    it.key == Key.Menu
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSwitch,
            )
            .drawBehind {
                if (focused) {
                    val y = size.height - 1.dp.toPx()
                    drawLine(palette.glow, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
                }
            }
            .padding(bottom = 6.dp),
    )
}

@Composable
private fun Clock() {
    val palette = LocalPalette.current
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            // Wake at the next minute boundary; the display has no seconds.
            delay(60_000 - System.currentTimeMillis() % 60_000)
        }
    }
    val timePattern = if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm"
    Text(
        now.format(DateTimeFormatter.ofPattern(timePattern)),
        fontFamily = palette.display,
        fontSize = 48.sp,
        color = palette.text,
    )
    Text(
        now.format(DateTimeFormatter.ofPattern("EEEE  ·  d MMMM")).uppercase(),
        fontFamily = palette.data,
        fontSize = 15.sp,
        letterSpacing = 3.sp,
        color = palette.accent.copy(alpha = 0.85f),
    )
}

@Composable
private fun WeatherLine() {
    if (WEATHER_CITY.isBlank()) return
    val palette = LocalPalette.current
    var weather by remember { mutableStateOf<WeatherNow?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            fetchWeather(WEATHER_CITY)?.let { weather = it }
            delay(WEATHER_REFRESH_MS)
        }
    }
    weather?.let {
        Text(
            "${it.tempF}°F  ·  ${it.summary}".uppercase(),
            fontFamily = palette.data,
            fontSize = 15.sp,
            letterSpacing = 3.sp,
            color = palette.text.copy(alpha = 0.8f),
            modifier = Modifier.graphicsLayer { translationY = 6.dp.toPx() },
        )
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    onFocused: (String) -> Unit,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.15f else 1f, label = "actionScale")

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused(label)
            }
            .clip(CircleShape)
            .background(if (focused) palette.glow.copy(alpha = 0.22f) else palette.surface.copy(alpha = 0.6f))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) palette.glow else palette.accent.copy(alpha = 0.35f),
                shape = CircleShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Image(
            imageVector = icon,
            contentDescription = label,
            colorFilter = ColorFilter.tint(if (focused) palette.text else palette.accent),
            modifier = Modifier.size(24.dp),
        )
    }
}
