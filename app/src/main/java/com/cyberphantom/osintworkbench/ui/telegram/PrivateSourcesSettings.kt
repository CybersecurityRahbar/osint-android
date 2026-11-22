package com.cyberphantom.osintworkbench.ui.telegram

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberphantom.osintworkbench.data.LocalStore
import com.cyberphantom.osintworkbench.data.PrivateSourceBot
import com.cyberphantom.osintworkbench.data.PrivateSourcesStore
import com.cyberphantom.osintworkbench.ui.design.DataText
import com.cyberphantom.osintworkbench.ui.design.GhostButton
import com.cyberphantom.osintworkbench.ui.design.Hairline
import com.cyberphantom.osintworkbench.ui.design.IntelColors
import com.cyberphantom.osintworkbench.ui.design.IntelField
import com.cyberphantom.osintworkbench.ui.design.IntelPanel
import com.cyberphantom.osintworkbench.ui.design.MicroLabel
import com.cyberphantom.osintworkbench.ui.design.ModeChip
import com.cyberphantom.osintworkbench.ui.design.SectionBar
import java.util.UUID

@Composable
fun PrivateSourcesSettings(localStore: LocalStore, onSaved: () -> Unit) {
    val context = LocalContext.current
    val configStore = remember { PrivateSourcesStore(context) }
    var sources by remember { mutableStateOf(configStore.loadBots()) }
    var editing by remember { mutableStateOf<PrivateSourceBot?>(null) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var draft by remember {
        mutableStateOf(
            PrivateSourceBot(
                "",
                "",
                "",
                "/search_by_number",
                "/search_by_name",
                "الرجاء ارسال الرقم",
                "الرجاء ارسال الاسم"
            )
        )
    }

    fun persist(next: List<PrivateSourceBot>) {
        sources = next
        configStore.saveBots(next)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionBar("01", "إعداد المصادر الخاصة", "${sources.size} مصدر")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ModeChip(
                selected = selectedTab == 0,
                label = "إعداد المصادر",
                onClick = { selectedTab = 0 },
                modifier = Modifier.weight(1f),
                accent = IntelColors.Violet
            )
            ModeChip(
                selected = selectedTab == 1,
                label = "الجلسة والتنفيذ",
                onClick = { selectedTab = 1 },
                modifier = Modifier.weight(1f),
                accent = IntelColors.Signal
            )
        }

        if (selectedTab == 1) {
            PrivateSearchPanel(store = localStore, onSaved = onSaved)
        } else {
            IntelPanel {
                MicroLabel("PRIVATE SOURCE REGISTRY", IntelColors.Violet)
                Text(
                    "أضف مصادر البحث وحدد أوامر البحث ونص المطالبة المتوقع لكل نوع. تُستخدم هذه الإعدادات في محرك التنفيذ التلقائي داخل تبويب الجلسة.",
                    fontSize = 9.sp,
                    color = IntelColors.TextFaint,
                    lineHeight = 14.sp
                )
                if (sources.isEmpty()) {
                    Text(
                        "لا توجد مصادر بعد. أضف مصدراً للبدء.",
                        color = IntelColors.TextDim,
                        fontSize = 10.sp
                    )
                }
                sources.forEach { source ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(IntelColors.PanelInset, RoundedCornerShape(3.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    source.name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IntelColors.Text
                                )
                                DataText(
                                    "معرّف المصدر: ${source.username}",
                                    IntelColors.Violet,
                                    size = 9,
                                    maxLines = 1
                                )
                            }
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "تعديل",
                                tint = IntelColors.TextDim,
                                modifier = Modifier.size(17.dp).clickable {
                                    editing = source
                                    draft = source
                                }
                            )
                            Spacer(Modifier.size(12.dp))
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "حذف",
                                tint = IntelColors.Red,
                                modifier = Modifier.size(17.dp).clickable {
                                    persist(sources.filterNot { it.id == source.id })
                                }
                            )
                        }
                        DataText(
                            "رقم: ${source.numberCommand.ifBlank { "—" }} · اسم: ${source.nameCommand.ifBlank { "—" }}",
                            IntelColors.TextFaint,
                            size = 8,
                            maxLines = 2
                        )
                    }
                }
                Hairline()
                GhostButton(
                    text = "إضافة مصدر",
                    onClick = {
                        draft = PrivateSourceBot(
                            id = UUID.randomUUID().toString(),
                            name = "",
                            username = "",
                            numberCommand = "/search_by_number",
                            nameCommand = "/search_by_name",
                            numberPrompt = "الرجاء ارسال الرقم",
                            namePrompt = "الرجاء ارسال الاسم"
                        )
                        editing = draft
                    },
                    icon = Icons.Filled.Add,
                    accent = IntelColors.Violet
                )
            }

            editing?.let {
                IntelPanel(accent = IntelColors.Violet) {
                    MicroLabel("SOURCE CONFIGURATION", IntelColors.Violet)
                    IntelField(draft.name, { draft = draft.copy(name = it) }, "اسم المصدر")
                    IntelField(draft.username, { draft = draft.copy(username = it) }, "معرّف المصدر (بدون @)")
                    IntelField(draft.numberCommand, { draft = draft.copy(numberCommand = it) }, "أمر البحث بالرقم")
                    IntelField(draft.nameCommand, { draft = draft.copy(nameCommand = it) }, "أمر البحث بالاسم")
                    IntelField(draft.numberPrompt, { draft = draft.copy(numberPrompt = it) }, "نص مطالبة الرقم المتوقعة")
                    IntelField(draft.namePrompt, { draft = draft.copy(namePrompt = it) }, "نص مطالبة الاسم المتوقعة")
                    Text(
                        "اترك الأمر فارغاً إذا كان المصدر لا يدعم هذا النوع. فعّل الإرسال المباشر من تبويب الجلسة فقط عندما لا يطلب المصدر قيمة بعد الأمر.",
                        fontSize = 8.sp,
                        color = IntelColors.TextFaint,
                        lineHeight = 12.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        GhostButton(
                            text = "حفظ",
                            onClick = {
                                val clean = draft.copy(
                                    name = draft.name.trim(),
                                    username = draft.username.trim().removePrefix("@"),
                                    numberCommand = draft.numberCommand.trim(),
                                    nameCommand = draft.nameCommand.trim(),
                                    numberPrompt = draft.numberPrompt.trim(),
                                    namePrompt = draft.namePrompt.trim()
                                )
                                if (clean.name.isNotBlank() && clean.username.matches(Regex("[A-Za-z0-9_]{5,32}"))) {
                                    val next = if (sources.any { it.id == clean.id }) {
                                        sources.map { if (it.id == clean.id) clean else it }
                                    } else {
                                        sources + clean
                                    }
                                    persist(next)
                                    editing = null
                                }
                            },
                            icon = Icons.Filled.Save,
                            accent = IntelColors.Violet
                        )
                        GhostButton(text = "إلغاء", onClick = { editing = null })
                    }
                }
            }
        }
    }
}
