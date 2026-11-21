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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberphantom.osintworkbench.data.PrivateSourceBot
import com.cyberphantom.osintworkbench.data.PrivateSourcesStore
import com.cyberphantom.osintworkbench.ui.design.DataText
import com.cyberphantom.osintworkbench.ui.design.GhostButton
import com.cyberphantom.osintworkbench.ui.design.Hairline
import com.cyberphantom.osintworkbench.ui.design.IntelColors
import com.cyberphantom.osintworkbench.ui.design.IntelField
import com.cyberphantom.osintworkbench.ui.design.IntelPanel
import com.cyberphantom.osintworkbench.ui.design.MicroLabel
import com.cyberphantom.osintworkbench.ui.design.SectionBar
import java.util.UUID

@Composable
fun PrivateSourcesSettings() {
    val context = LocalContext.current
    val store = remember { PrivateSourcesStore(context) }
    var bots by remember { mutableStateOf(store.loadBots()) }
    var editing by remember { mutableStateOf<PrivateSourceBot?>(null) }
    var draft by remember { mutableStateOf(PrivateSourceBot("", "", "", "/search_by_number", "/search_by_name", "الرجاء ارسال الرقم", "الرجاء ارسال الاسم")) }

    fun persist(next: List<PrivateSourceBot>) {
        bots = next
        store.saveBots(next)
    }

    SectionBar("01", "المصادر الخاصة / Telegram", "${bots.size} بوت")
    IntelPanel {
        MicroLabel("BOT AUTOMATION", IntelColors.Violet)
        Text(
            "اضبط لكل بوت أمر البحث بالرقم وبالاسم، ونص المطالبة الذي يرسله البوت. المحرك يحاول مطابقة النص، ثم يقبل أي مطالبة تحتوي كلمة مفتاحية (رقم/اسم)، ويمكنك تفعيل الإرسال المباشر من شاشة البحث.",
            fontSize = 9.sp,
            color = IntelColors.TextFaint,
            lineHeight = 14.sp
        )
        bots.forEach { bot ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(IntelColors.PanelInset, RoundedCornerShape(2.dp))
                    .padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(bot.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IntelColors.Text)
                        DataText("@" + bot.username, IntelColors.Violet, size = 9, maxLines = 1)
                    }
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "تعديل",
                        tint = IntelColors.TextDim,
                        modifier = Modifier.size(15.dp).clickable {
                            editing = bot
                            draft = bot
                        }
                    )
                    Spacer(Modifier.size(12.dp))
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "حذف",
                        tint = IntelColors.Red,
                        modifier = Modifier.size(15.dp).clickable { persist(bots.filterNot { it.id == bot.id }) }
                    )
                }
                DataText(
                    "رقم: ${bot.numberCommand.ifBlank { "—" }} · اسم: ${bot.nameCommand.ifBlank { "—" }}",
                    IntelColors.TextFaint,
                    size = 8,
                    maxLines = 2
                )
            }
        }
        Hairline()
        GhostButton(
            text = "إضافة بوت",
            onClick = {
                editing = PrivateSourceBot("", "", "", "/search_by_number", "/search_by_name", "الرجاء ارسال الرقم", "الرجاء ارسال الاسم")
                draft = PrivateSourceBot(
                    id = UUID.randomUUID().toString(),
                    name = "",
                    username = "",
                    numberCommand = "/search_by_number",
                    nameCommand = "/search_by_name",
                    numberPrompt = "الرجاء ارسال الرقم",
                    namePrompt = "الرجاء ارسال الاسم"
                )
            },
            icon = Icons.Filled.Add
        )
    }

    editing?.let {
        IntelPanel(accent = IntelColors.Violet) {
            MicroLabel("BOT PROFILE", IntelColors.Violet)
            IntelField(draft.name, { draft = draft.copy(name = it) }, "الاسم الظاهر")
            IntelField(draft.username, { draft = draft.copy(username = it) }, "اسم المستخدم (بدون @)")
            IntelField(draft.numberCommand, { draft = draft.copy(numberCommand = it) }, "أمر البحث بالرقم")
            IntelField(draft.nameCommand, { draft = draft.copy(nameCommand = it) }, "أمر البحث بالاسم")
            IntelField(draft.numberPrompt, { draft = draft.copy(numberPrompt = it) }, "نص مطالبة الرقم من البوت")
            IntelField(draft.namePrompt, { draft = draft.copy(namePrompt = it) }, "نص مطالبة الاسم من البوت")
            Text(
                "اترك أمراً فارغاً إذا كان البوت لا يدعم ذلك النوع.",
                fontSize = 8.sp,
                color = IntelColors.TextFaint
            )
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                GhostButton(
                    text = "حفظ",
                    onClick = {
                        val clean = draft.copy(
                            name = draft.name.trim(),
                            username = draft.username.trim().removePrefix("@"),
                            numberCommand = draft.numberCommand.trim(),
                            nameCommand = draft.nameCommand.trim()
                        )
                        if (clean.name.isNotBlank() && clean.username.isNotBlank()) {
                            val next = if (bots.any { it.id == clean.id }) {
                                bots.map { if (it.id == clean.id) clean else it }
                            } else bots + clean
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
