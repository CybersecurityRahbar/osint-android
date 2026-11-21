package com.cyberphantom.osintworkbench.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cyberphantom.osintworkbench.BuildConfig
import com.cyberphantom.osintworkbench.data.LocalStore
import com.cyberphantom.osintworkbench.data.StoreStats
import com.cyberphantom.osintworkbench.domain.SourceRegistry
import com.cyberphantom.osintworkbench.ui.design.IntelColors
import com.cyberphantom.osintworkbench.ui.design.IntelPanel
import com.cyberphantom.osintworkbench.ui.design.KeyValueRow
import com.cyberphantom.osintworkbench.ui.design.MicroLabel
import com.cyberphantom.osintworkbench.ui.design.NoteBlock
import com.cyberphantom.osintworkbench.ui.design.SectionBar
import com.cyberphantom.osintworkbench.ui.design.StatTile
import com.cyberphantom.osintworkbench.ui.telegram.PrivateSourcesSettings

@Composable
fun SettingsScreen(stats: StoreStats, store: LocalStore, dataVersion: Int, onDataChanged: () -> Unit) {
    ScreenColumn {
        ScreenLead(
            code = "MODULE 06 / SYSTEM",
            title = "الإعدادات",
            subtitle = "المصادر الخاصة، النسخ الاحتياطي، وحدود الاستخدام",
            icon = Icons.Filled.Settings,
            accent = IntelColors.TextDim
        )

        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            StatTile("VERSION", BuildConfig.VERSION_NAME, IntelColors.Signal, Modifier.weight(1f))
            StatTile("ENTITIES", stats.entities.toString(), IntelColors.Violet, Modifier.weight(1f))
            StatTile("SCANS", stats.scans.toString(), IntelColors.Blue, Modifier.weight(1f))
        }

        PrivateSourcesSettings()

        SectionBar("02", "إدارة البيانات", "LOCAL")
        DataSection(store = store, dataVersion = dataVersion, onDataChanged = onDataChanged)

        SectionBar("03", "حول", "ABOUT")
        IntelPanel {
            MicroLabel("OSINT WORKBENCH · LOCAL BUILD", IntelColors.TextFaint)
            KeyValueRow("الإصدار", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            KeyValueRow("الحزمة", BuildConfig.APPLICATION_ID)
            KeyValueRow("قاعدة البيانات", "SQLite محلية · ${stats.relationships} علاقة · ${stats.findings} دليل")
            KeyValueRow("المصادر المسجلة", "${SourceRegistry.total}")
            KeyValueRow("الخادم", "لا يوجد · بلا حساب وبلا رفع بيانات", IntelColors.Green)
            Spacer(Modifier.size(2.dp))
        }
        NoteBlock(
            "البيانات غير مشفرة عند السكون داخل قاعدة SQLite. إن كان الجهاز مشتركاً أو غير موثوق، صدّر ما تحتاجه ثم امسح البيانات.",
            IntelColors.Amber,
            "RISK"
        )
        NoteBlock(
            "الاستخدام المسؤول: التطبيق لا يتجاوز حماية ولا يجمع بيانات دخول. الفحوص النشطة تتطلب تفويضاً صريحاً.",
            IntelColors.Signal,
            "AUTHORIZED USE"
        )
        Spacer(Modifier.size(4.dp))
    }
}
