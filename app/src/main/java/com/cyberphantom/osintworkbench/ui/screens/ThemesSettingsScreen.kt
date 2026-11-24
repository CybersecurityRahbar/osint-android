package com.cyberphantom.osintworkbench.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberphantom.osintworkbench.ui.design.GhostButton
import com.cyberphantom.osintworkbench.ui.design.IntelColors
import com.cyberphantom.osintworkbench.ui.design.IntelPanel
import com.cyberphantom.osintworkbench.ui.design.MicroLabel
import com.cyberphantom.osintworkbench.ui.design.NoteBlock
import com.cyberphantom.osintworkbench.ui.design.Pill
import com.cyberphantom.osintworkbench.ui.design.SectionBar
import com.cyberphantom.osintworkbench.ui.design.ToggleRow
import com.cyberphantom.osintworkbench.ui.design.WorkbenchThemeState
import com.cyberphantom.osintworkbench.ui.design.WorkbenchVisualTheme

@Composable
fun ThemesSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current

    // SettingsScreen already owns the vertical scroll container for this subpage.
    // A second verticalScroll here receives unbounded height constraints and crashes Compose.
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        GhostButton("رجوع إلى الإعدادات", onBack, accent = IntelColors.TextDim)
        ScreenLead(
            code = "MODULE 06 / VISUAL SYSTEM",
            title = "الثيمات والمؤثرات",
            subtitle = "تخصيص لوحة الألوان والحركة والصوت والتنبيهات",
            icon = Icons.Filled.Palette,
            accent = IntelColors.Red
        )
        SectionBar("THEME 01", "المظهر الأساسي", "DEFAULT")
        ThemeOption(
            theme = WorkbenchVisualTheme.STEEL,
            selected = WorkbenchThemeState.theme == WorkbenchVisualTheme.STEEL,
            onSelect = {
                WorkbenchThemeState.selectTheme(context, WorkbenchVisualTheme.STEEL)
                WorkbenchThemeState.tap(view)
            }
        )
        SectionBar("THEME 02", "المظهر المستقبلي الجديد", "NEON")
        ThemeOption(
            theme = WorkbenchVisualTheme.NEON,
            selected = WorkbenchThemeState.theme == WorkbenchVisualTheme.NEON,
            onSelect = {
                WorkbenchThemeState.selectTheme(context, WorkbenchVisualTheme.NEON)
                WorkbenchThemeState.tap(view)
            }
        )
        SectionBar("MOTION / AUDIO", "المؤثرات", "LOCAL")
        ToggleRow(
            checked = WorkbenchThemeState.motionEnabled,
            onCheckedChange = { WorkbenchThemeState.setMotion(context, it) },
            title = "الحركة والإضاءة المتوهجة",
            detail = "تفعيل نبضات الضوء وخط المسح المتحرك في الثيم المستقبلي.",
            accent = IntelColors.Green
        )
        ToggleRow(
            checked = WorkbenchThemeState.soundEnabled,
            onCheckedChange = { WorkbenchThemeState.setSound(context, it) },
            title = "أصوات الواجهة",
            detail = "نغمات قصيرة عند اختيار العناصر وتشغيل البحث؛ مغلقة افتراضياً حتى لا تصدر أصواتاً دون رغبتك.",
            accent = IntelColors.Blue
        )
        ToggleRow(
            checked = WorkbenchThemeState.completionAlertsEnabled,
            onCheckedChange = { WorkbenchThemeState.setCompletionAlerts(context, it) },
            title = "تنبيهات اكتمال البحث",
            detail = "تنبيه مرئي مع نغمة واهتزاز عند نجاح البحث أو فشله أثناء بقاء التطبيق مفتوحاً.",
            accent = IntelColors.Red
        )
        ToggleRow(
            checked = WorkbenchThemeState.hapticsEnabled,
            onCheckedChange = { WorkbenchThemeState.setHaptics(context, it) },
            title = "الاستجابة اللمسية",
            detail = "اهتزاز خفيف عند الاختيار والتنبيه، وفق إعدادات الاهتزاز في Android.",
            accent = IntelColors.Green
        )
        NoteBlock(
            "التنبيهات هنا داخل التطبيق أثناء تشغيله في الواجهة؛ لا نرسل إشعارات Android خارج التطبيق ولا نشغّل خدمة خلفية. يمكنك تعطيل الصوت أو الحركة بشكل مستقل.",
            IntelColors.Amber,
            "POWER / PRIVACY"
        )
    }
}

@Composable
private fun ThemeOption(
    theme: WorkbenchVisualTheme,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val neon = theme == WorkbenchVisualTheme.NEON
    val accent = if (neon) IntelColors.Green else IntelColors.Signal
    IntelPanel(
        modifier = Modifier.clickable(onClick = onSelect),
        accent = if (neon) IntelColors.Red else IntelColors.Signal,
        contentPadding = 13
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    theme.title,
                    color = IntelColors.Text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.1.sp
                )
                Text(theme.subtitle, color = IntelColors.TextFaint, fontSize = 9.sp, lineHeight = 14.sp)
            }
            if (selected) {
                Pill("ACTIVE", accent, filled = true)
            } else {
                GhostButton("تفعيل", onSelect, accent = accent)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            val colors = if (neon) {
                listOf(Color(0xFFFF3158), Color(0xFF39FF88), Color.White, Color(0xFF45E7FF))
            } else {
                listOf(Color(0xFF4DF5E2), Color(0xFFA7C1D0), Color(0xFF526D80), Color(0xFF071019))
            }
            colors.forEach { swatch ->
                Box(
                    Modifier
                        .size(18.dp)
                        .background(swatch, RoundedCornerShape(4.dp))
                        .border(1.dp, IntelColors.SteelBright.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                )
            }
            Spacer(Modifier.weight(1f))
            MicroLabel(if (neon) "RED / GREEN / WHITE" else "STEEL / CYAN", accent)
        }
    }
}
