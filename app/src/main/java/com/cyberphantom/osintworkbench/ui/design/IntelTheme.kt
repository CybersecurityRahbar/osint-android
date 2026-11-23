package com.cyberphantom.osintworkbench.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

enum class WorkbenchVisualTheme(val title: String, val subtitle: String) {
    STEEL("STEEL COMMAND", "الثيم الأساسي · فولاذ داكن ولمسات فيروزية"),
    NEON("NEON FUTURE", "ثيم مستقبلي متوهج · أحمر وأخضر وأبيض")
}

/**
 * Snapshot-backed palette. A theme switch updates the full Compose UI immediately,
 * without recreating the Activity or resetting search/navigation state.
 */
object IntelColors {
    var activeTheme: WorkbenchVisualTheme by mutableStateOf(WorkbenchVisualTheme.STEEL)
        private set

    var Void: Color by mutableStateOf(Color(0xFF03070B))
        private set
    var Base: Color by mutableStateOf(Color(0xFF071019))
        private set
    var Panel: Color by mutableStateOf(Color(0xFF101C27))
        private set
    var PanelHigh: Color by mutableStateOf(Color(0xFF1A2B39))
        private set
    var PanelInset: Color by mutableStateOf(Color(0xFF09131D))
        private set

    var SteelDark: Color by mutableStateOf(Color(0xFF263B4B))
        private set
    var Steel: Color by mutableStateOf(Color(0xFF526D80))
        private set
    var SteelBright: Color by mutableStateOf(Color(0xFFA7C1D0))
        private set
    var SteelGlow: Color by mutableStateOf(Color(0xFFD4E6EC))
        private set
    var Hairline: Color by mutableStateOf(Color(0xFF344C5D))
        private set
    var HairlineStrong: Color by mutableStateOf(Color(0xFF5C7789))
        private set
    var Cursor: Color by mutableStateOf(Color(0xFF8BFFF0))
        private set

    var Text: Color by mutableStateOf(Color(0xFFF0F7FA))
        private set
    var TextDim: Color by mutableStateOf(Color(0xFFB0C3CF))
        private set
    var TextFaint: Color by mutableStateOf(Color(0xFF78909F))
        private set

    var Signal: Color by mutableStateOf(Color(0xFF4DF5E2))
        private set
    var SignalDim: Color by mutableStateOf(Color(0xFF124A50))
        private set
    var Amber: Color by mutableStateOf(Color(0xFFFFC66E))
        private set
    var Red: Color by mutableStateOf(Color(0xFFFF7182))
        private set
    var Blue: Color by mutableStateOf(Color(0xFF63D7FF))
        private set
    var Violet: Color by mutableStateOf(Color(0xFFC1A1FF))
        private set
    var Green: Color by mutableStateOf(Color(0xFF8AF2A9))
        private set

    fun apply(theme: WorkbenchVisualTheme) {
        activeTheme = theme
        when (theme) {
            WorkbenchVisualTheme.STEEL -> {
                Void = Color(0xFF03070B)
                Base = Color(0xFF071019)
                Panel = Color(0xFF101C27)
                PanelHigh = Color(0xFF1A2B39)
                PanelInset = Color(0xFF09131D)
                SteelDark = Color(0xFF263B4B)
                Steel = Color(0xFF526D80)
                SteelBright = Color(0xFFA7C1D0)
                SteelGlow = Color(0xFFD4E6EC)
                Hairline = Color(0xFF344C5D)
                HairlineStrong = Color(0xFF5C7789)
                Cursor = Color(0xFF8BFFF0)
                Text = Color(0xFFF0F7FA)
                TextDim = Color(0xFFB0C3CF)
                TextFaint = Color(0xFF78909F)
                Signal = Color(0xFF4DF5E2)
                SignalDim = Color(0xFF124A50)
                Amber = Color(0xFFFFC66E)
                Red = Color(0xFFFF7182)
                Blue = Color(0xFF63D7FF)
                Violet = Color(0xFFC1A1FF)
                Green = Color(0xFF8AF2A9)
            }
            WorkbenchVisualTheme.NEON -> {
                Void = Color(0xFF03050A)
                Base = Color(0xFF070B12)
                Panel = Color(0xFF101923)
                PanelHigh = Color(0xFF192633)
                PanelInset = Color(0xFF090F17)
                SteelDark = Color(0xFF26394A)
                Steel = Color(0xFF637D91)
                SteelBright = Color(0xFFFFFFFF)
                SteelGlow = Color(0xFFFFFFFF)
                Hairline = Color(0xFF465D70)
                HairlineStrong = Color(0xFF94B0C4)
                Cursor = Color(0xFFFFFFFF)
                Text = Color(0xFFFFFFFF)
                TextDim = Color(0xFFE0EAF2)
                TextFaint = Color(0xFFA9BDCE)
                Signal = Color(0xFF39FF88)
                SignalDim = Color(0xFF0D6840)
                Amber = Color(0xFFFFD84D)
                Red = Color(0xFFFF3158)
                Blue = Color(0xFF45E7FF)
                Violet = Color(0xFFD28BFF)
                Green = Color(0xFF39FF88)
            }
        }
    }

    fun severity(level: String): Color = when (level.lowercase()) {
        "critical", "high" -> Red
        "warning", "medium" -> Amber
        "ok", "low" -> Green
        "info" -> Blue
        else -> TextDim
    }
}

private fun commandPalette() = darkColorScheme(
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
    MaterialTheme(colorScheme = commandPalette(), content = content)
}
