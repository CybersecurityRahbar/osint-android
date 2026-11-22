package com.cyberphantom.osintworkbench.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Brushed-steel console palette: deep graphite, cool metallic edges and restrained
 * luminous accents. Bright colours indicate state and evidence class, not decoration.
 */
object IntelColors {
    val Void = Color(0xFF03070B)
    val Base = Color(0xFF071019)
    val Panel = Color(0xFF101C27)
    val PanelHigh = Color(0xFF1A2B39)
    val PanelInset = Color(0xFF09131D)

    val SteelDark = Color(0xFF263B4B)
    val Steel = Color(0xFF526D80)
    val SteelBright = Color(0xFFA7C1D0)
    val SteelGlow = Color(0xFFD4E6EC)
    val Hairline = Color(0xFF344C5D)
    val HairlineStrong = Color(0xFF5C7789)
    val Cursor = Color(0xFF8BFFF0)

    val Text = Color(0xFFF0F7FA)
    val TextDim = Color(0xFFB0C3CF)
    val TextFaint = Color(0xFF78909F)

    val Signal = Color(0xFF4DF5E2)
    val SignalDim = Color(0xFF124A50)
    val Amber = Color(0xFFFFC66E)
    val Red = Color(0xFFFF7182)
    val Blue = Color(0xFF63D7FF)
    val Violet = Color(0xFFC1A1FF)
    val Green = Color(0xFF8AF2A9)

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
