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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import cc.zohn.tvlauncher.ui.theme.Cinzel
import cc.zohn.tvlauncher.ui.theme.Gold
import cc.zohn.tvlauncher.ui.theme.Ivory
import cc.zohn.tvlauncher.ui.theme.Walnut
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val WEATHER_REFRESH_MS = 30 * 60_000L

// ---- Top bar: wordmark (left); clock, weather, quick actions (right) ----

@Composable
fun TopBar(
    showInputs: Boolean,
    onInputs: () -> Unit,
    onOpenSettings: (action: String) -> Unit,
    onActionFocused: (label: String) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            "ZOHN",
            fontFamily = Cinzel,
            fontWeight = FontWeight.SemiBold,
            fontSize = 26.sp,
            letterSpacing = 8.sp,
            color = Gold,
        )
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

@Composable
private fun Clock() {
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
        fontFamily = Cinzel,
        fontSize = 48.sp,
        color = Ivory,
    )
    Text(
        now.format(DateTimeFormatter.ofPattern("EEEE  ·  d MMMM")).uppercase(),
        fontSize = 15.sp,
        letterSpacing = 3.sp,
        color = Gold.copy(alpha = 0.85f),
    )
}

@Composable
private fun WeatherLine() {
    if (WEATHER_CITY.isBlank()) return
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
            fontSize = 15.sp,
            letterSpacing = 3.sp,
            color = Ivory.copy(alpha = 0.8f),
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
            .background(if (focused) Gold.copy(alpha = 0.22f) else Walnut.copy(alpha = 0.6f))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Gold else Gold.copy(alpha = 0.35f),
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
            colorFilter = ColorFilter.tint(if (focused) Ivory else Gold),
            modifier = Modifier.size(24.dp),
        )
    }
}
