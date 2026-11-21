package com.cyberphantom.osintworkbench.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.cyberphantom.osintworkbench.data.LocalStore
import com.cyberphantom.osintworkbench.data.ScanRow
import com.cyberphantom.osintworkbench.domain.HtmlReportBuilder
import com.cyberphantom.osintworkbench.domain.ScanReport
import com.cyberphantom.osintworkbench.ui.design.ActionButton
import com.cyberphantom.osintworkbench.ui.design.DataText
import com.cyberphantom.osintworkbench.ui.design.GhostButton
import com.cyberphantom.osintworkbench.ui.design.Hairline
import com.cyberphantom.osintworkbench.ui.design.IntelColors
import com.cyberphantom.osintworkbench.ui.design.IntelPanel
import com.cyberphantom.osintworkbench.ui.design.MicroLabel
import com.cyberphantom.osintworkbench.ui.design.NoteBlock
import com.cyberphantom.osintworkbench.ui.design.Pill
import com.cyberphantom.osintworkbench.ui.design.SectionBar
import com.cyberphantom.osintworkbench.ui.util.formatTime
import com.cyberphantom.osintworkbench.ui.util.safeFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ReportActions(report: ScanReport) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var shareAfterSave by remember { mutableStateOf(false) }
    var pendingHtml by remember { mutableStateOf("") }

    val htmlLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        if (uri == null) {
            shareAfterSave = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val body = pendingHtml
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(body) }
                        ?: error("output stream unavailable")
                }.isSuccess
            }
            Toast.makeText(context, if (saved) "تم حفظ تقرير العملية" else "تعذر حفظ التقرير", Toast.LENGTH_LONG).show()
            if (saved && shareAfterSave) {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/html"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { context.startActivity(Intent.createChooser(send, "مشاركة تقرير OSINT")) }
            }
            shareAfterSave = false
        }
    }

    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val body = withContext(Dispatchers.IO) { runCatching { report.toJson() }.getOrDefault("{}") }
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(body) }
                        ?: error("output stream unavailable")
                }.isSuccess
            }
            Toast.makeText(context, if (saved) "تم حفظ JSON" else "تعذر حفظ JSON", Toast.LENGTH_LONG).show()
        }
    }

    IntelPanel(accent = IntelColors.Signal) {
        MicroLabel("CHAIN OF CUSTODY / EXPORT", IntelColors.Signal)
        Text(
            "تقرير هذه العملية فقط",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = IntelColors.Text
        )
        Text(
            "يتضمن الوقت والمصادر والملاحظات. لن يضم عمليات أخرى من قاعدة البيانات.",
            fontSize = 9.sp,
            color = IntelColors.TextFaint,
            lineHeight = 14.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionButton(
                text = "حفظ HTML",
                onClick = {
                    pendingHtml = HtmlReportBuilder.scanReport(report)
                    shareAfterSave = false
                    htmlLauncher.launch("${safeFileName(report.target)}-report.html")
                },
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.SaveAlt
            )
            GhostButton(
                text = "مشاركة",
                onClick = {
                    pendingHtml = HtmlReportBuilder.scanReport(report)
                    shareAfterSave = true
                    htmlLauncher.launch("${safeFileName(report.target)}-report.html")
                },
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.IosShare
            )
            GhostButton(
                text = "JSON",
                onClick = { jsonLauncher.launch("${safeFileName(report.target)}-report.json") },
                icon = Icons.Filled.Code
            )
        }
    }
}

@Composable
fun DataSection(store: LocalStore, dataVersion: Int, onDataChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scans by remember { mutableStateOf(emptyList<ScanRow>()) }
    var status by remember { mutableStateOf<String?>(null) }
    var confirmErase by remember { mutableStateOf(false) }

    LaunchedEffect(dataVersion) {
        scans = withContext(Dispatchers.IO) { runCatching { store.scans(20) }.getOrDefault(emptyList()) }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val body = withContext(Dispatchers.IO) { runCatching { store.exportSnapshotJson() }.getOrDefault("{}") }
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(body) }
                        ?: error("output stream unavailable")
                }.isSuccess
            }
            status = if (saved) "تم تصدير المساحة المحلية بالكامل." else "تعذر تصدير البيانات."
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                }.getOrNull()
            }
            if (json.isNullOrBlank()) {
                status = "تعذر قراءة الملف المحدد."
                return@launch
            }
            val result = withContext(Dispatchers.IO) { runCatching { store.restoreSnapshotJson(json) } }
            result.onSuccess { restored ->
                status = "تمت الاستعادة: ${restored.entities} كيان · ${restored.relationships} علاقة · ${restored.cases} حالة."
                onDataChanged()
            }.onFailure { status = "فشلت الاستعادة: ${it.message ?: "ملف غير صالح"}" }
        }
    }

    IntelPanel {
        MicroLabel("DATA / BACKUP", IntelColors.Blue)
        Text("نسخ واستعادة المساحة المحلية", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IntelColors.Text)
        Text(
            "التصدير يحفظ الكيانات بسياقها الكامل (النوع + الهدف) مع العلاقات والأدلة والحالات وسجل العمليات.",
            fontSize = 9.sp,
            color = IntelColors.TextFaint,
            lineHeight = 14.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ActionButton(
                text = "تصدير JSON",
                onClick = { exportLauncher.launch("osint-workbench-${System.currentTimeMillis()}.json") },
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Download
            )
            GhostButton(
                text = "استعادة",
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.UploadFile
            )
        }
        status?.let {
            Text(it, color = IntelColors.TextDim, fontSize = 9.sp, lineHeight = 14.sp)
        }
        Hairline()
        Row(verticalAlignment = Alignment.CenterVertically) {
            MicroLabel("DANGER ZONE", IntelColors.Red)
            Spacer(Modifier.weight(1f))
            GhostButton(
                text = "مسح كل البيانات",
                onClick = { confirmErase = true },
                icon = Icons.Filled.DeleteSweep,
                accent = IntelColors.Red
            )
        }
        Text(
            "المسح نهائي ويحذف الكيانات والعلاقات والأدلة والعمليات والحالات من هذا الجهاز. صدّر نسخة أولاً إن كنت تحتاجها.",
            fontSize = 9.sp,
            color = IntelColors.TextFaint,
            lineHeight = 14.sp
        )
    }

    SectionBar("03", "سجل العمليات", "${scans.size} أحدث")
    if (scans.isEmpty()) {
        NoteBlock("لا توجد عمليات محفوظة بعد. شغّل استطلاعاً أو بحثاً لتظهر هنا.", IntelColors.Blue, "LOG")
    } else {
        scans.forEach { scan ->
            Surface(
                color = IntelColors.Panel,
                shape = RoundedCornerShape(2.dp),
                border = BorderStroke(1.dp, IntelColors.Hairline)
            ) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Pill(scan.status.uppercase(), if (scan.status == "completed") IntelColors.Green else IntelColors.Amber)
                        DataText(scan.target, IntelColors.Text, size = 11, maxLines = 1, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { store.deleteScan(scan.id) }
                                scans = withContext(Dispatchers.IO) { store.scans(20) }
                                onDataChanged()
                            }
                        }) {
                            Text("حذف", color = IntelColors.Red, fontSize = 9.sp)
                        }
                    }
                    DataText(scan.summary.ifBlank { "${scan.recordsCount} سجل" }, IntelColors.TextDim, size = 9, maxLines = 2)
                    Text(
                        "${formatTime(scan.startedAt)} · ${scan.recordsCount} سجل",
                        color = IntelColors.TextFaint,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("مسح كل البيانات المحلية؟", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "سيُحذف كل ما جمعته من كيانات وعلاقات وأدلة وعمليات وحالات. لا يمكن التراجع.",
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmErase = false
                    scope.launch {
                        val done = withContext(Dispatchers.IO) { runCatching { store.clearAllData() }.isSuccess }
                        status = if (done) "تم مسح قاعدة البيانات المحلية." else "تعذر مسح البيانات."
                        scans = withContext(Dispatchers.IO) { runCatching { store.scans(20) }.getOrDefault(emptyList()) }
                        onDataChanged()
                    }
                }) { Text("مسح نهائي", color = IntelColors.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmErase = false }) { Text("إلغاء") } },
            containerColor = IntelColors.PanelHigh
        )
    }
}
