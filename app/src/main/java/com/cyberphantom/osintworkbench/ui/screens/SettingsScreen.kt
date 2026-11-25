package com.cyberphantom.osintworkbench.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberphantom.osintworkbench.BuildConfig
import com.cyberphantom.osintworkbench.data.LocalStore
import com.cyberphantom.osintworkbench.data.StoreStats
import com.cyberphantom.osintworkbench.domain.SourceRegistry
import com.cyberphantom.osintworkbench.ui.design.GhostButton
import com.cyberphantom.osintworkbench.ui.design.IntelColors
import com.cyberphantom.osintworkbench.ui.design.IntelPanel
import com.cyberphantom.osintworkbench.ui.design.KeyValueRow
import com.cyberphantom.osintworkbench.ui.design.MicroLabel
import com.cyberphantom.osintworkbench.ui.design.NoteBlock
import com.cyberphantom.osintworkbench.ui.design.SectionBar
import com.cyberphantom.osintworkbench.ui.design.StatTile
import com.cyberphantom.osintworkbench.ui.telegram.PrivateSourcesSettings

private enum class SettingsPage {
    HOME, API_KEYS, PRIVATE_SOURCES, DATA, THEMES, ABOUT
}

@Composable
fun SettingsScreen(stats: StoreStats, store: LocalStore, dataVersion: Int, onDataChanged: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.HOME) }

    BackHandler(enabled = page != SettingsPage.HOME) {
        page = SettingsPage.HOME
    }

    ScreenColumn {
        when (page) {
            SettingsPage.HOME -> {
                ScreenLead(
                    code = "MODULE 06 / SYSTEM",
                    title = "الإعدادات",
                    subtitle = "اختر فئة إعدادات مستقلة؛ كل فئة تفتح شاشة خاصة بها",
                    icon = Icons.Filled.Settings,
                    accent = IntelColors.TextDim
                )
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    StatTile("VERSION", BuildConfig.VERSION_NAME, IntelColors.Signal, Modifier.weight(1f))
                    StatTile("ENTITIES", stats.entities.toString(), IntelColors.Violet, Modifier.weight(1f))
                    StatTile("SCANS", stats.scans.toString(), IntelColors.Blue, Modifier.weight(1f))
                }
                SectionBar("01", "إدارة النظام", "SETTINGS")
                SettingsNavigationCard(
                    title = "إعداد مفاتيح المصادر الخاصة",
                    detail = "مفاتيح البحث الشامل والتتبع، الأخبار والتقارير، وثلاثة مفاتيح لكل مزود.",
                    meta = "9 API PROVIDERS",
                    accent = IntelColors.Violet,
                    onClick = { page = SettingsPage.API_KEYS }
                )
                SettingsNavigationCard(
                    title = "مصادر Telegram الخاصة",
                    detail = "إعداد المصادر، أوامر البحث، جلسة Telegram، ومصادر الاستعلام الخاصة.",
                    meta = "PRIVATE SOURCES",
                    accent = IntelColors.Signal,
                    onClick = { page = SettingsPage.PRIVATE_SOURCES }
                )
                SettingsNavigationCard(
                    title = "البيانات والنسخ الاحتياطي",
                    detail = "تصدير واستعادة البيانات المحلية، السجلات، وإجراءات المسح.",
                    meta = "DATA / BACKUP",
                    accent = IntelColors.Blue,
                    onClick = { page = SettingsPage.DATA }
                )
                SettingsNavigationCard(
                    title = "الثيمات والمؤثرات",
                    detail = "اختر مظهر النظام، الحركة المتوهجة، أصوات الواجهة وتنبيهات اكتمال البحث.",
                    meta = "VISUAL THEMES / EFFECTS",
                    accent = IntelColors.Red,
                    onClick = { page = SettingsPage.THEMES }
                )
                SettingsNavigationCard(
                    title = "حول التطبيق والأمان",
                    detail = "الإصدار، الحزمة، قاعدة البيانات، وملاحظات الخصوصية والأمان.",
                    meta = "ABOUT / SECURITY",
                    accent = IntelColors.TextDim,
                    onClick = { page = SettingsPage.ABOUT }
                )
                NoteBlock(
                    "لم تعد الإعدادات الفرعية تُعرض متتابعة داخل هذه الصفحة. افتح الفئة المطلوبة للانتقال إلى شاشة مستقلة، ثم استخدم زر الرجوع للعودة.",
                    IntelColors.Green,
                    "NAVIGATION"
                )
            }

            SettingsPage.API_KEYS -> {
                ApiKeySourcesSettings(onBack = { page = SettingsPage.HOME })
            }

            SettingsPage.PRIVATE_SOURCES -> {
                PrivateSourcesSettings(
                    localStore = store,
                    onSaved = onDataChanged,
                    onBack = { page = SettingsPage.HOME }
                )
            }

            SettingsPage.DATA -> {
                DataSection(
                    store = store,
                    dataVersion = dataVersion,
                    onDataChanged = onDataChanged,
                    onBack = { page = SettingsPage.HOME }
                )
            }

            SettingsPage.THEMES -> {
                ThemesSettingsScreen(onBack = { page = SettingsPage.HOME })
            }

            SettingsPage.ABOUT -> {
                SettingsSubpageHeader(
                    title = "حول التطبيق والأمان",
                    detail = "معلومات الإصدار والخصوصية",
                    onBack = { page = SettingsPage.HOME }
                )
                IntelPanel {
                    MicroLabel("OSINT WORKBENCH · LOCAL BUILD", IntelColors.TextFaint)
                    KeyValueRow("الإصدار", BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")")
                    KeyValueRow("الحزمة", BuildConfig.APPLICATION_ID)
                    KeyValueRow("قاعدة البيانات", "SQLite محلية · " + stats.relationships + " علاقة · " + stats.findings + " دليل")
                    KeyValueRow("المصادر المسجلة", SourceRegistry.total.toString())
                    KeyValueRow("الخادم", "لا يوجد · بلا خادم خاص", IntelColors.Green)
                    Spacer(Modifier.size(2.dp))
                }
                NoteBlock(
                    "المفاتيح الخارجية تُشفّر محلياً بمفتاح Android Keystore. تخزينها محلياً يقلل التعرض لكنه لا يمنع استخراجها بالكامل من جهاز مخترق أو أثناء تشغيل التطبيق.",
                    IntelColors.Amber,
                    "API KEY SECURITY"
                )
                NoteBlock(
                    "قاعدة SQLite المحلية غير مشفرة عند السكون. احمِ الجهاز، واحتفظ بنسخة احتياطية في مكان آمن.",
                    IntelColors.Amber,
                    "LOCAL DATA RISK"
                )
                NoteBlock(
                    "الفحوص النشطة تتطلب تفويضاً صريحاً. نتائج البحث العامة قرائن يجب التحقق منها، ولا تثبت وحدها هوية شخص أو ملكية حساب.",
                    IntelColors.Signal,
                    "AUTHORIZED USE"
                )
            }
        }
        Spacer(Modifier.size(4.dp))
    }
}

@Composable
private fun SettingsNavigationCard(
    title: String,
    detail: String,
    meta: String,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    IntelPanel(
        modifier = Modifier.clickable(onClick = onClick),
        accent = accent,
        contentPadding = 13
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = IntelColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(detail, color = IntelColors.TextFaint, fontSize = 9.sp, lineHeight = 14.sp)
            }
            MicroLabel("›", accent)
        }
        MicroLabel(meta, accent)
    }
}

@Composable
private fun SettingsSubpageHeader(title: String, detail: String, onBack: () -> Unit) {
    GhostButton("رجوع إلى الإعدادات", onBack, accent = IntelColors.TextDim)
    ScreenLead(
        code = "MODULE 06 / SETTINGS",
        title = title,
        subtitle = detail,
        icon = Icons.Filled.Settings,
        accent = IntelColors.TextDim
    )
}
