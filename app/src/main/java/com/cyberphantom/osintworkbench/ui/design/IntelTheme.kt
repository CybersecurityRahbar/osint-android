package com.cyberphantom.osintworkbench.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Signal-room palette: near-black graphite, hairline structure, one analytical accent
 * (signal teal) plus severity colours. No decorative gradients; contrast is reserved for data.
 */
object IntelColors {
    val Void = Color(0xFF04070A)
    val Base = Color(0xFF070C11)
    val Panel = Color(0xFF0B1218)
    val PanelHigh = Color(0xFF101A22)
    val PanelInset = Color(0xFF080F15)

    val Hairline = Color(0xFF1A2836)
    val HairlineStrong = Color(0xFF263C4E)
    val Cursor = Color(0xFF3ED6C6)

    val Text = Color(0xFFE7EEF5)
    val TextDim = Color(0xFF97A9BB)
    val TextFaint = Color(0xFF5D7183)

    val Signal = Color(0xFF3ED6C6)
    val SignalDim = Color(0xFF0E3A3A)
    val Amber = Color(0xFFE3AC57)
    val Red = Color(0xFFFF6B6B)
    val Blue = Color(0xFF63A0FF)
    val Violet = Color(0xFF9C87F2)
    val Green = Color(0xFF57D18C)

    fun severity(level: String): Color = when (level.lowercase()) {
        "critical", "high" -> Red
        "warning", "medium" -> Amber
        "ok", "low" -> Green
        "info" -> Blue
        else -> TextDim
    }
}

private val CommandPalette = darkColorScheme(
    primary = IntelColors.Signal,
    onPrimary = Color(0xFF03211E),
    secondary = IntelColors.Blue,
    onSecondary = Color(0xFF04131F),
    tertiary = IntelColors.Amber,
    background = IntelColors.Base,
    onBackground = IntelColors.Text,
    surface = IntelColors.Panel,
    onSurface = IntelColors.Text,
    surfaceVariant = IntelColors.PanelHigh,
    onSurfaceVariant = IntelColors.TextDim,
    outline = IntelColors.HairlineStrong,
    error = IntelColors.Red
)

@Composable
fun WorkbenchTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CommandPalette, content = content)
}
