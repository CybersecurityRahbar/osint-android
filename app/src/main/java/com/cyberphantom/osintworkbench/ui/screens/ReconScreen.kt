package com.cyberphantom.osintworkbench.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cyberphantom.osintworkbench.data.LocalStore
import com.cyberphantom.osintworkbench.data.isRisk
import com.cyberphantom.osintworkbench.data.persistReconReport
import com.cyberphantom.osintworkbench.data.reconCategory
import com.cyberphantom.osintworkbench.domain.ReconEngine
import com.cyberphantom.osintworkbench.domain.ReconRecord
import com.cyberphantom.osintworkbench.domain.ScanReport
import com.cyberphantom.osintworkbench.ui.design.ActionButton
import com.cyberphantom.osintworkbench.ui.design.ConfidenceMeter
import com.cyberphantom.osintworkbench.ui.design.DataText
import com.cyberphantom.osintworkbench.ui.design.EmptyBlock
import com.cyberphantom.osintworkbench.ui.design.Hairline
import com.cyberphantom.osintworkbench.ui.design.IntelColors
import com.cyberphantom.osintworkbench.ui.design.IntelField
import com.cyberphantom.osintworkbench.ui.design.IntelPanel
import com.cyberphantom.osintworkbench.ui.design.MicroLabel
import com.cyberphantom.osintworkbench.ui.design.ModeChip
import com.cyberphantom.osintworkbench.ui.design.NoteBlock
import com.cyberphantom.osintworkbench.ui.design.Pill
import com.cyberphantom.osintworkbench.ui.design.SectionBar
import com.cyberphantom.osintworkbench.ui.design.StatTile
import com.cyberphantom.osintworkbench.ui.design.ToggleRow
import com.cyberphantom.osintworkbench.ui.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ReconScreen(store: LocalStore, initialTarget: String, onSaved: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var domain by rememberSaveable { mutableStateOf(initialTarget) }
    var includeCt by rememberSaveable { mutableStateOf(true) }
    var includeAdvanced by rememberSaveable { mutableStateOf(true) }
    var includeActive by rememberSaveable { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<ScanReport?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var elapsedMs by remember { mutableStateOf(0L) }
    var filter by remember { mutableStateOf("ALL") }
    var onlyRisk by remember { mutableStateOf(false) }

    LaunchedEffect(initialTarget) {
        if (initialTarget.isNotBlank()) domain = initialTarget
    }

    LaunchedEffect(scanning) {
        if (!scanning) return@LaunchedEffect
        val started = System.currentTimeMillis()
        while (true) {
            elapsedMs = System.currentTimeMillis() - started
            delay(250)
        }
    }

    ScreenColumn {
        ScreenLead(
            code = "MODULE 02 / RECON",
            title = "استطلاع الأصل",
            subtitle = "جمع الأدلة العامة من طبقات متعددة، مع فحوص اتصال اختيارية للأصول المصرح بها",
            icon = Icons.Filled.Dns
        )

        IntelPanel(accent = IntelColors.Signal) {
            MicroLabel("TARGET / DOMAIN", IntelColors.Signal)
            IntelField(
                value = domain,
                onValueChange = { domain = it; error = null },
                label = "النطاق (example.com)",
                placeholder = "example.com"
            )
            ToggleRow(
                checked = includeCt,
                onCheckedChange = { includeCt = it },
                title = "شهادات TLS والنطاقات المرتبطة",
                detail = "Certificate Transparency عبر crt.sh + تحقق DNS للنطاقات الفرعية"
            )
            ToggleRow(
                checked = includeAdvanced,
                onCheckedChange = { includeAdvanced = it },
                title = "التوسيع الكامل",
                detail = "RDAP · أرشيف Wayback · كتالوغ Archive.org · SPF/DMARC/DKIM · HTTPS · robots/sitemap/security.txt · GeoIP · فهرس الويب"
            )
            ToggleRow(
                checked = includeActive,
                onCheckedChange = { includeActive = it },
                title = "فحوص اتصال محدودة",
                detail = "23 منفذاً شائعاً + 7 مؤشرات مسارات عامة. فعّلها فقط لما تملكه أو لديك تفويض بفحصه.",
                accent = IntelColors.Amber
            )
            if (includeActive) {
                NoteBlock(
                    "ليست ماسحاً شاملاً: لا تجرب بيانات دخول، لا تتجاوز حماية، ولا تثبت سلامة النظام عند عدم العثور على شيء.",
                    IntelColors.Amber,
                    "AUTHORIZED USE"
                )
            }
            error?.let {
                Text(it, color = IntelColors.Red, fontSize = 10.sp, lineHeight = 15.sp)
            }
            ActionButton(
                text = "تشغيل الاستطلاع",
                onClick = {
                    scope.launch {
                        scanning = true
                        error = null
                        report = null
                        filter = "ALL"
                        elapsedMs = 0
                        try {
                            val result = ReconEngine().scanDomain(domain, includeCt, includeAdvanced, includeActive)
                            report = result
                            runCatching { persistReconReport(store, result) }
                                .onFailure { error = "تعذر حفظ النتائج محلياً: ${it.message ?: "خطأ قاعدة بيانات"}" }
                            onSaved()
                        } catch (exception: Exception) {
                            error = exception.message ?: "تعذر إكمال الاستطلاع."
                        } finally {
                            scanning = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Filled.Search,
                loading = scanning,
                enabled = domain.isNotBlank()
            )
        }

        if (scanning) {
            IntelPanel(accent = IntelColors.Signal) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = IntelColors.Signal)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("جمع الأدلة من المصادر العامة", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IntelColors.Text)
                        MicroLabel("T+ ${formatDuration(elapsedMs)} · ${domain.trim()}", IntelColors.TextFaint)
                    }
                }
                DataText(
                    "DNS · CT · RDAP · ARCHIVE · EMAIL POSTURE · HTTPS · DISCOVERY · GEO",
                    IntelColors.TextFaint,
                    size = 9,
                    maxLines = 2
                )
            }
        }

        report?.let { result ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                StatTile("RECORDS", result.records.size.toString(), IntelColors.Signal, Modifier.weight(1f))
                StatTile("SOURCES", result.sourceCount.toString(), IntelColors.Blue, Modifier.weight(1f))
                StatTile("WARN", result.warnings.size.toString(), IntelColors.Amber, Modifier.weight(1f))
            }
            IntelPanel {
                KeyLine("TARGET", result.target)
                KeyLine("ELAPSED", formatDuration(result.elapsedMs))
                KeyLine("MODE", if (result.records.any { it.kind == "PORT_SCAN_SUMMARY" }) "سلبي + فحوص محدودة" else "سلبي")
                KeyLine("FINISHED", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(result.finishedAt)))
            }
            ReportActions(result)

            if (result.records.isNotEmpty()) {
                val categories = listOf("ALL") + result.records.map { reconCategory(it.kind) }.distinct().sorted()
                SectionBar("01", "النتائج", "${result.records.size} سجل")
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    categories.forEach { category ->
                        ModeChip(
                            selected = filter == category && !onlyRisk,
                            label = if (category == "ALL") "الكل" else category,
                            onClick = { filter = category; onlyRisk = false }
                        )
                    }
                    ModeChip(
                        selected = onlyRisk,
                        label = "مؤشرات",
                        onClick = { onlyRisk = !onlyRisk },
                        accent = IntelColors.Amber
                    )
                }
                val visible = result.records.filter { record ->
                    val categoryOk = filter == "ALL" || reconCategory(record.kind) == filter
                    categoryOk && (!onlyRisk || record.isRisk())
                }
                if (visible.isEmpty()) {
                    EmptyBlock(
                        icon = Icons.Filled.Search,
                        title = "لا توجد سجلات في هذا التصنيف",
                        message = "اختر تصنيفاً آخر أو أعد الاستطلاع مع خيارات توسيع إضافية."
                    )
                } else {
                    visible.forEach { record ->
                        RecordRow(record = record, onOpen = {
                            runCatching { uriHandler.openUri(record.sourceUrl) }
                        })
                    }
                }
            } else {
                EmptyBlock(
                    icon = Icons.Filled.Search,
                    title = "لم تُعد المصادر سجلات",
                    message = "قد يكون النطاق غير موجود، أو أن المصدر حدّ من الطلبات. راجع ملاحظات المصادر أدناه."
                )
            }

            if (result.warnings.isNotEmpty()) {
                SectionBar("02", "ملاحظات المصادر", "${result.warnings.size}")
                result.warnings.forEach { warning ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(IntelColors.Amber.copy(alpha = 0.05f), RoundedCornerShape(2.dp))
                            .padding(9.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = IntelColors.Amber, modifier = Modifier.size(14.dp))
                        Text(warning, color = IntelColors.TextDim, fontSize = 9.sp, lineHeight = 14.sp)
                    }
                }
            }
        }
        Spacer(Modifier.size(4.dp))
    }
}

@Composable
internal fun KeyLine(key: String, value: String, valueColor: androidx.compose.ui.graphics.Color = IntelColors.Text) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MicroLabel(key, IntelColors.TextFaint)
        Spacer(Modifier.width(10.dp))
        DataText(value, valueColor, size = 10, maxLines = 2, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RecordRow(record: ReconRecord, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(IntelColors.Panel, RoundedCornerShape(2.dp))
            .clickable(onClick = onOpen)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Pill(reconCategory(record.kind), if (record.isRisk()) IntelColors.Amber else IntelColors.Signal)
            MicroLabel(record.kind.replace('_', ' '), IntelColors.TextFaint)
            Spacer(Modifier.weight(1f))
            ConfidenceMeter(record.confidence, if (record.confidence >= 70) IntelColors.Signal else IntelColors.Amber)
        }
        DataText(record.value, IntelColors.Text, size = 11, maxLines = 4)
        if (!record.previewImageUrl.isNullOrBlank()) {
            AsyncImage(
                model = record.previewImageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 150.dp)
                    .background(IntelColors.PanelInset, RoundedCornerShape(2.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }
        Hairline()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                record.source,
                color = IntelColors.TextFaint,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Filled.OpenInNew, contentDescription = "فتح المصدر", tint = IntelColors.Signal, modifier = Modifier.size(13.dp))
        }
        if (record.detail.isNotBlank()) {
            Text(record.detail, color = IntelColors.TextFaint, fontSize = 9.sp, lineHeight = 13.sp, maxLines = 4)
        }
    }
}
