package com.cyberphantom.osintworkbench.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberphantom.osintworkbench.data.CaseRow
import com.cyberphantom.osintworkbench.data.LocalStore
import com.cyberphantom.osintworkbench.ui.design.ActionButton
import com.cyberphantom.osintworkbench.ui.design.DataText
import com.cyberphantom.osintworkbench.ui.design.EmptyBlock
import com.cyberphantom.osintworkbench.ui.design.IntelColors
import com.cyberphantom.osintworkbench.ui.design.IntelField
import com.cyberphantom.osintworkbench.ui.design.IntelPanel
import com.cyberphantom.osintworkbench.ui.design.MicroLabel
import com.cyberphantom.osintworkbench.ui.design.NoteBlock
import com.cyberphantom.osintworkbench.ui.design.SectionBar
import com.cyberphantom.osintworkbench.ui.util.formatTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CasesScreen(cases: List<CaseRow>, store: LocalStore, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }
    var scopeText by rememberSaveable { mutableStateOf("") }

    ScreenColumn {
        ScreenLead(
            code = "MODULE 05 / CASES",
            title = "الحالات",
            subtitle = "مساحات عمل لفصل الأهداف والملاحظات؛ كل حالة محلية وليست تقريراً نهائياً",
            icon = Icons.Filled.FolderOpen,
            accent = IntelColors.Amber
        )
        ActionButton(
            text = "حالة جديدة",
            onClick = { creating = true },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Filled.Add,
            accent = IntelColors.Amber
        )
        if (cases.isEmpty()) {
            EmptyBlock(
                icon = Icons.Filled.FolderOpen,
                title = "لا توجد حالات",
                message = "أنشئ حالة لكل هدف أو تحقيق لتفصل الأدلة والملاحظات عن بقية العمل."
            )
        } else {
            SectionBar("01", "الحالات المحفوظة", "${cases.size}")
            cases.forEach { item ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(IntelColors.Panel, RoundedCornerShape(2.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = IntelColors.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "حذف الحالة",
                            tint = IntelColors.Red,
                            modifier = Modifier
                                .size(15.dp)
                                .padding(0.dp)
                        )
                        TextButton(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { store.deleteCase(item.id) }
                                onSaved()
                            }
                        }) { Text("حذف", color = IntelColors.Red, fontSize = 9.sp) }
                    }
                    DataText(item.target, IntelColors.Signal, size = 11, maxLines = 2)
                    if (item.scope.isNotBlank()) {
                        Text(item.scope, fontSize = 9.sp, color = IntelColors.TextDim, lineHeight = 13.sp)
                    }
                    MicroLabel(formatTime(item.createdAt), IntelColors.TextFaint)
                }
            }
        }
        NoteBlock(
            "الحالات لا تغيّر صلاحية الجمع. استخدم فقط أهدافاً تملكها أو لديك تفويض صريح بفحصها.",
            IntelColors.Amber,
            "SCOPE"
        )
        Spacer(Modifier.size(4.dp))
    }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("حالة جديدة", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IntelField(title, { title = it }, "عنوان الحالة")
                    IntelField(target, { target = it }, "الهدف")
                    IntelField(scopeText, { scopeText = it }, "النطاق / التفويض")
                    Text(
                        "اذكر أساس التفويض: ملكية، عقد، أو مهمة مصرح بها.",
                        fontSize = 9.sp,
                        color = IntelColors.TextFaint,
                        lineHeight = 13.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isNotBlank() && target.isNotBlank()) {
                        creating = false
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                store.createCase(title, target, scopeText)
                            }
                            title = ""
                            target = ""
                            scopeText = ""
                            onSaved()
                        }
                    }
                }) { Text("إنشاء") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("إلغاء") } },
            containerColor = IntelColors.PanelHigh
        )
    }
}
