package com.cyberphantom.osintworkbench.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberphantom.osintworkbench.data.CaseRow
import com.cyberphantom.osintworkbench.data.FindingRow
import com.cyberphantom.osintworkbench.data.StoreStats
import com.cyberphantom.osintworkbench.domain.SourceRegistry
import com.cyberphantom.osintworkbench.ui.WorkbenchTab
import com.cyberphantom.osintworkbench.ui.design.ActionButton
import com.cyberphantom.osintworkbench.ui.design.DataText
import com.cyberphantom.osintworkbench.ui.design.EmptyBlock
import com.cyberphantom.osintworkbench.ui.design.IntelColors
import com.cyberphantom.osintworkbench.ui.design.IntelPanel
import com.cyberphantom.osintworkbench.ui.design.KeyValueRow
import com.cyberphantom.osintworkbench.ui.design.MicroLabel
import com.cyberphantom.osintworkbench.ui.design.ModeChip
import com.cyberphantom.osintworkbench.ui.design.NoteBlock
import com.cyberphantom.osintworkbench.ui.design.SectionBar
import com.cyberphantom.osintworkbench.ui.design.StatTile
import com.cyberphantom.osintworkbench.ui.util.formatTime

private enum class QuickMode(val label: String) {
    DOMAIN("نطاق"),
    NAME("اسم"),
    USERNAME("مستخدم"),
    PHONE("هاتف"),
    EMAIL("بريد"),
    NEWS("أخبار")
}

@Composable
fun HomeScreen(
    stats: StoreStats,
    findings: List<FindingRow>,
    cases: List<CaseRow>,
    onRecon: (String) -> Unit,
    onResearch: (String, Boolean) -> Unit,
    onOpenTab: (WorkbenchTab) -> Unit
) {
    var quickTarget by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(QuickMode.DOMAIN) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 13.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        IntelPanel(accent = IntelColors.Signal) {
            MicroLabel("OPERATIONS / TARGET ACQUISITION", IntelColors.Signal)
            Text(
                "غرفة العمليات",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = IntelColors.Text
            )
            Text(
                "حدد أصلاً أو معرّفاً واحداً. يجمع المحرك الأدلة العامة من مصادر متعددة، ويحفظ كل نتيجة مع مصدرها ووقت جمعها.",
                fontSize = 10.sp,
                lineHeight = 16.sp,
                color = IntelColors.TextDim
            )
            androidx.compose.material3.OutlinedTextField(
                value = quickTarget,
                onValueChange = { quickTarget = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(3.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = IntelColors.Text
                ),
                placeholder = {
                    Text(
                        when (mode) {
                            QuickMode.DOMAIN -> "example.com"
                            QuickMode.NAME -> "الاسم الكامل"
                            QuickMode.USERNAME -> "@username"
                            QuickMode.PHONE -> "+967…"
                            QuickMode.EMAIL -> "name@example.com"
                            QuickMode.NEWS -> "كلمات الخبر"
                        },
                        fontSize = 11.sp,
                        color = IntelColors.TextFaint
                    )
                },
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = IntelColors.Signal,
                    unfocusedBorderColor = IntelColors.HairlineStrong,
                    focusedContainerColor = IntelColors.PanelInset,
                    unfocusedContainerColor = IntelColors.PanelInset,
                    cursorColor = IntelColors.Cursor,
                    focusedTextColor = IntelColors.Text,
                    unfocusedTextColor = IntelColors.Text
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                QuickMode.entries.take(5).forEach { item ->
                    ModeChip(
                        selected = mode == item,
                        label = item.label,
                        onClick = { mode = item },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActionButton(
                    text = if (mode == QuickMode.DOMAIN) "استطلاع الأصل" else "بحث في الفهارس",
                    onClick = {
                        val target = quickTarget.trim()
                        if (target.isNotBlank()) {
                            if (mode == QuickMode.DOMAIN) onRecon(target) else onResearch(target, false)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.ArrowForward
                )
                ActionButton(
                    text = "أخبار",
                    onClick = {
                        val target = quickTarget.trim()
                        if (target.isNotBlank()) onResearch(target, true)
                    },
                    modifier = Modifier.width(96.dp),
                    icon = Icons.Filled.Newspaper,
                    accent = IntelColors.Blue
                )
            }
            MicroLabel(
                "الاستخدام: أهداف تملكها أو لديك تفويض بفحصها · النتائج العامة قد تكون ناقصة",
                IntelColors.TextFaint
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            StatTile("ENTITIES", stats.entities.toString(), IntelColors.Signal, Modifier.weight(1f), "كيان محفوظ")
            StatTile("RELATIONS", stats.relationships.toString(), IntelColors.Violet, Modifier.weight(1f), "علاقة موثقة")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            StatTile("SCANS", stats.scans.toString(), IntelColors.Blue, Modifier.weight(1f), "عملية جمع")
            StatTile("CASES", stats.cases.toString(), IntelColors.Amber, Modifier.weight(1f), "مساحة عمل")
        }

        SectionBar("01", "قدرات جاهزة", "${SourceRegistry.total} مصدر")
        CapabilityRow(
            icon = Icons.Filled.Dns,
            title = "استطلاع نطاق",
            detail = "DNS · شهادات · RDAP · أرشيف · سياسات بريد · HTTPS",
            accent = IntelColors.Signal,
            onClick = { onOpenTab(WorkbenchTab.RECON) }
        )
        CapabilityRow(
            icon = Icons.Filled.PersonSearch,
            title = "بحث المعرّفات",
            detail = "اسم · مستخدم · هاتف · بريد عبر فهارس متعددة",
            accent = IntelColors.Violet,
            onClick = { onOpenTab(WorkbenchTab.RESEARCH) }
        )
        CapabilityRow(
            icon = Icons.Filled.Newspaper,
            title = "الأخبار متعددة المصادر",
            detail = "GDELT · Google News · Bing News · RSS ناشرين",
            accent = IntelColors.Blue,
            onClick = { onOpenTab(WorkbenchTab.RESEARCH) }
        )
        CapabilityRow(
            icon = Icons.Filled.AccountTree,
            title = "شبكة العلاقات",
            detail = "أدلة مرتبطة بمصادرها ودرجات ثقة إرشادية",
            accent = IntelColors.Amber,
            onClick = { onOpenTab(WorkbenchTab.NETWORK) }
        )

        SectionBar("02", "آخر الأدلة", "LIVE FEED")
        if (findings.isEmpty()) {
            EmptyBlock(
                icon = Icons.Filled.FolderOpen,
                title = "لا توجد أدلة بعد",
                message = "نفّذ استطلاعاً أو بحثاً؛ تُسجَّل كل نتيجة مع مصدرها ووقتها محلياً."
            )
        } else {
            findings.take(4).forEach { finding ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(IntelColors.Panel, RoundedCornerShape(2.dp))
                        .clickable { onOpenTab(WorkbenchTab.NETWORK) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .background(if (finding.severity == "warning") IntelColors.Amber else IntelColors.Signal)
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            finding.title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = IntelColors.Text,
                            maxLines = 1
                        )
                        DataText(
                            "${finding.source} · ${formatTime(finding.observedAt)}",
                            IntelColors.TextFaint,
                            size = 9,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        SectionBar("03", "حالة النظام", "SYS")
        IntelPanel {
            KeyValueRow("قاعدة البيانات", "SQLite محلية · ${stats.entities} كيان")
            KeyValueRow("التشفير عند السكون", "غير مُفعّل بعد", IntelColors.Amber)
            KeyValueRow("الخادم الخلفي", "لا يوجد · معالجة على الجهاز", IntelColors.Green)
            KeyValueRow("مساحات العمل", "${cases.size} حالة محفوظة")
        }
        NoteBlock(
            "كل ما تجمعه يبقى على هذا الجهاز. لا تشارك تقريراً قبل مراجعته؛ قد يحتوي معرّفات وملاحظات أدخلتها بنفسك.",
            IntelColors.Amber,
            "PRIVACY"
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun CapabilityRow(
    icon: ImageVector,
    title: String,
    detail: String,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(IntelColors.Panel, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(30.dp).background(accent.copy(alpha = 0.12f), RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IntelColors.Text)
            Text(detail, fontSize = 9.sp, color = IntelColors.TextFaint, lineHeight = 13.sp)
        }
        Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = IntelColors.TextFaint, modifier = Modifier.size(14.dp))
    }
}
