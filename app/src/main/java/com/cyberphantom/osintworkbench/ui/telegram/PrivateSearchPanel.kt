package com.cyberphantom.osintworkbench.ui.telegram

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberphantom.osintworkbench.data.LocalStore
import com.cyberphantom.osintworkbench.data.PrivateSourceBot
import com.cyberphantom.osintworkbench.data.PrivateSourceKind
import com.cyberphantom.osintworkbench.data.PrivateSourceFlow
import com.cyberphantom.osintworkbench.data.PrivateSourcesStore
import com.cyberphantom.osintworkbench.ui.design.ActionButton
import com.cyberphantom.osintworkbench.ui.design.DataText
import com.cyberphantom.osintworkbench.ui.design.GhostButton
import com.cyberphantom.osintworkbench.ui.design.Hairline
import com.cyberphantom.osintworkbench.ui.design.IntelColors
import com.cyberphantom.osintworkbench.ui.design.IntelField
import com.cyberphantom.osintworkbench.ui.design.IntelPanel
import com.cyberphantom.osintworkbench.ui.design.MicroLabel
import com.cyberphantom.osintworkbench.ui.design.ModeChip
import com.cyberphantom.osintworkbench.ui.design.NoteBlock
import com.cyberphantom.osintworkbench.ui.design.Pill
import com.cyberphantom.osintworkbench.ui.design.SectionBar
import com.cyberphantom.osintworkbench.ui.design.ToggleRow
import com.cyberphantom.osintworkbench.ui.util.formatClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun PrivateSearchPanel(store: LocalStore, onSaved: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val configStore = remember { PrivateSourcesStore(context) }
    var bots by remember { mutableStateOf(configStore.loadBots()) }
    var selectedBotId by rememberSaveable { mutableStateOf<String?>(bots.firstOrNull()?.id) }
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(PrivateSourceKind.NUMBER) }
    var saveReplies by rememberSaveable { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("أدخل القيمة ثم شغّل الطلب؛ سيتابع المصدر المختار ويجمع الاستجابة.") }
    var result by remember { mutableStateOf<String?>(null) }
    var log by remember { mutableStateOf(emptyList<String>()) }
    var diagnostics by remember { mutableStateOf<String?>(null) }
    val webView = remember { PrivateSessionHost.peek() }
    val scope = rememberCoroutineScope()
    val selectedBot = bots.firstOrNull { it.id == selectedBotId }

    LaunchedEffect(bots) {
        if (bots.none { it.id == selectedBotId }) selectedBotId = bots.firstOrNull()?.id
    }

    fun addLog(message: String) {
        val stamp = formatClock(System.currentTimeMillis())
        log = (log + "$stamp  $message").takeLast(14)
    }

    fun start() {
        val bot = selectedBot ?: return
        val view = webView
        if (view == null) {
            status = "لم تُهيأ جلسة المصدر بعد. افتح نافذة الجلسة من الإعدادات أولاً."
            return
        }
        if (query.isBlank()) {
            status = "أدخل القيمة المطلوبة أولاً."
            return
        }

        val flow = bot.flow(kind)
        val command = bot.command(kind).trim()
        if (flow != PrivateSourceFlow.VALUE_ONLY && command.isBlank()) {
            status = "هذا النمط يحتاج إلى أمر لهذه القيمة. عدّل إعداد المصدر أو اختر «قيمة فقط»."
            return
        }

        val value = query.trim()
        val searchKind = kind
        busy = true
        result = null
        diagnostics = null
        log = emptyList()
        scope.launch {
            try {
                withContext(Dispatchers.Main) { view.onResume() }
                addLog("المصدر المحدد: ${bot.name}")
                val opened = TelegramWeb.openChat(view, bot.username) { addLog(it) }
                if (!TelegramWeb.chatMatches(opened, bot.username, bot.name)) {
                    throw IllegalStateException("لم نتأكد من فتح المحادثة المحددة. لم يُرسل أي طلب.")
                }
                addLog("تم التحقق من المحادثة وحقل الإدخال.")

                var promptText: String? = null
                if (flow != PrivateSourceFlow.VALUE_ONLY) {
                    val beforeCommand = TelegramWeb.snapshot(view).messages.map { it.key }.toSet()
                    addLog("إرسال أمر المصدر.")
                    val commandSent = TelegramWeb.send(view, command)
                    addLog("تم إرسال الأمر عبر ${commandSent.via}.")
                    val commandEcho = TelegramWeb.waitFor(view, 20_000L) { snapshot ->
                        snapshot.messages.any {
                            it.outgoing && it.text.trim() == command && it.key !in beforeCommand
                        }
                    }
                    if (commandEcho == null) {
                        throw IllegalStateException("لم يظهر تأكيد الأمر في سجل الرسائل؛ لم نرسل القيمة.")
                    }

                    if (flow == PrivateSourceFlow.COMMAND_AND_PROMPT) {
                        val promptBaseline = TelegramWeb.snapshot(view).messages.map { it.key }.toSet()
                        addLog("انتظار مطالبة المصدر بالقيمة…")
                        val prompt = TelegramWeb.waitFor(view, 35_000L) { snapshot ->
                            snapshot.messages.any { message ->
                                !message.outgoing &&
                                    message.key !in promptBaseline &&
                                    TelegramWeb.looksLikePrompt(message.text, searchKind, bot.prompt(searchKind))
                            }
                        }
                        if (prompt == null) {
                            throw IllegalStateException(
                                "لم تصل مطالبة مطابقة خلال المهلة. راجع نص المطالبة أو غيّر النمط إلى «أمر + قيمة» إذا كان المصدر لا يطلب القيمة منفصلة."
                            )
                        }
                        promptText = prompt.messages.lastOrNull {
                            !it.outgoing && it.key !in promptBaseline
                        }?.text
                        addLog("تم التعرف على مطالبة المصدر.")
                    } else {
                        addLog("النمط المحدد: أمر ثم قيمة؛ لا توجد خطوة مطالبة.")
                        delay(700L)
                    }
                } else {
                    addLog("النمط المحدد: إرسال القيمة فقط دون أمر.")
                }

                val beforeValue = TelegramWeb.snapshot(view).messages.map { it.key }.toSet()
                addLog("إرسال القيمة.")
                val valueSent = TelegramWeb.send(view, value)
                addLog("تم إرسال القيمة عبر ${valueSent.via}.")
                val valueEcho = TelegramWeb.waitFor(view, 20_000L) { snapshot ->
                    snapshot.messages.any {
                        it.outgoing && it.text.trim() == value && it.key !in beforeValue
                    }
                }
                if (valueEcho == null) {
                    throw IllegalStateException("لم يظهر تأكيد إرسال القيمة في سجل الرسائل؛ لم ننسب أي رد إلى هذا الطلب.")
                }

                addLog("جمع استجابة المصدر…")
                val reply = collectReply(view, beforeValue, promptText)
                if (reply.isBlank()) {
                    diagnostics = runCatching { TelegramWeb.diagnostics(view) }
                        .getOrNull()?.summary()?.replace("web.telegram.org", "جلسة المصدر")
                    throw IllegalStateException("لم تصل استجابة نصية خلال 90 ثانية. قد تكون النتيجة وسائط أو أن المصدر لم يرد. راجع التشخيص.")
                }

                result = reply
                status = "اكتمل الطلب. النتيجة من ${bot.name}."
                addLog("تم جمع ${reply.length} حرفاً.")
                if (saveReplies) {
                    withContext(Dispatchers.IO) {
                        val node = store.upsertEntity(
                            value = value,
                            kind = "PRIVATE_SOURCE_LOOKUP@@" + bot.id,
                            source = "مصدر خاص · ${bot.name}",
                            confidence = 55
                        )
                        store.addFinding(
                            entityId = node,
                            title = "رد ${bot.name}",
                            detail = reply,
                            source = "مصدر خاص · ${bot.name}",
                            severity = "info"
                        )
                    }
                    onSaved()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                status = error.message ?: "تعذر إكمال الطلب."
                diagnostics = runCatching { TelegramWeb.diagnostics(view) }
                    .getOrNull()?.summary()?.replace("web.telegram.org", "جلسة المصدر")
                addLog("فشل: ${error.message}")
            } finally {
                busy = false
            }
        }
    }

    ScreenBlock {
        SectionBar("01", "المصادر الخاصة", if (bots.isEmpty()) "غير مضبوط" else "${bots.size} مصدر")
        if (bots.isEmpty()) {
            NoteBlock(
                "لا توجد مصادر مضبوطة. أضف بيانات المصدر وأوامر البحث من تبويب الإعدادات ثم ابدأ التنفيذ.",
                IntelColors.Amber,
                "SETUP"
            )
        } else {
            IntelPanel {
                MicroLabel("PRIVATE SOURCE SELECT", IntelColors.Violet)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    bots.forEach { bot ->
                        ModeChip(
                            selected = bot.id == selectedBotId,
                            label = bot.name.take(24),
                            onClick = {
                                selectedBotId = bot.id
                                result = null
                                diagnostics = null
                                if (bot.command(kind).isBlank()) {
                                    kind = if (bot.numberCommand.isNotBlank()) PrivateSourceKind.NUMBER else PrivateSourceKind.NAME
                                }
                            },
                            accent = IntelColors.Violet
                        )
                    }
                }
                selectedBot?.let { bot ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Pill(if (kind == PrivateSourceKind.NUMBER) "بحث رقمي" else "بحث بالاسم", IntelColors.Violet)
                        MicroLabel("مصدر: " + bot.name, IntelColors.TextFaint)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    ModeChip(
                        selected = kind == PrivateSourceKind.NUMBER,
                        label = "رقم",
                        onClick = { kind = PrivateSourceKind.NUMBER },
                        modifier = Modifier.weight(1f)
                    )
                    ModeChip(
                        selected = kind == PrivateSourceKind.NAME,
                        label = "اسم",
                        onClick = { kind = PrivateSourceKind.NAME },
                        modifier = Modifier.weight(1f)
                    )
                }
                IntelField(
                    value = query,
                    onValueChange = { query = it; result = null },
                    label = if (kind == PrivateSourceKind.NUMBER) "القيمة الرقمية" else "الاسم",
                    placeholder = if (kind == PrivateSourceKind.NUMBER) "+967…" else "الاسم الكامل"
                )
                NoteBlock(
                    "نمط هذا البحث: ${selectedBot?.flow(kind)?.label() ?: "غير محدد"}. غيّره من إعدادات المصادر الخاصة.",
                    IntelColors.Violet,
                    "REQUEST FLOW"
                )
                ToggleRow(
                    checked = saveReplies,
                    onCheckedChange = { saveReplies = it },
                    title = "حفظ الرد في قاعدة الأدلة",
                    detail = "يُسجَّل الرد مرتبطاً بالقيمة واسم المصدر المختار."
                )
                ActionButton(
                    text = "تنفيذ الطلب التلقائي",
                    onClick = { start() },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Send,
                    loading = busy,
                    enabled = !busy && selectedBot != null && query.isNotBlank() && webView != null
                )
                Text(
                    status,
                    fontSize = 9.sp,
                    lineHeight = 14.sp,
                    color = if (status.startsWith("تعذر") || status.startsWith("لم")) IntelColors.Amber else IntelColors.TextDim
                )
            }
        }

        if (webView == null) {
            NoteBlock(
                "لم تُهيأ جلسة المصدر بعد. افتح الإعدادات ← إعدادات المصادر الخاصة ← نافذة الجلسة، ثم سجّل الدخول مرة واحدة.",
                IntelColors.Amber,
                "SESSION REQUIRED"
            )
            GhostButton(
                text = "فتح إعدادات الجلسة",
                onClick = onOpenSettings,
                accent = IntelColors.Signal
            )
        } else {
            NoteBlock(
                "جلسة المصدر التي أعددتها من الإعدادات متاحة لمحرك البحث.",
                IntelColors.Signal,
                "SESSION READY"
            )
        }

        if (log.isNotEmpty()) {
            SectionBar("03", "سجل الخطوات", "TRACE")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IntelColors.PanelInset, RoundedCornerShape(2.dp))
                    .padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                log.forEach { line ->
                    Text(
                        line,
                        color = if (line.contains("تحذير") || line.contains("فشل")) IntelColors.Amber else IntelColors.TextFaint,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 11.sp
                    )
                }
            }
        }

        result?.let { text ->
            SectionBar("04", "النتيجة", "OUTPUT")
            IntelPanel(accent = IntelColors.Green) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MicroLabel("CAPTURED REPLY", IntelColors.Green)
                    Spacer(Modifier.weight(1f))
                    GhostButton(
                        text = "نسخ",
                        onClick = { clipboard.setText(AnnotatedString(text)) },
                        icon = Icons.Filled.ContentCopy
                    )
                }
                Text(text, fontSize = 11.sp, lineHeight = 18.sp, color = IntelColors.Text)
            }
        }

        diagnostics?.let { text ->
            SectionBar("05", "تشخيص DOM", "DEBUG")
            IntelPanel(contentPadding = 10) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.BugReport, contentDescription = null, tint = IntelColors.Amber, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    MicroLabel("LIVE SNAPSHOT", IntelColors.Amber)
                }
                Text(text, fontSize = 8.sp, lineHeight = 12.sp, fontFamily = FontFamily.Monospace, color = IntelColors.TextDim)
            }
        }
    }
}

/**
 * Captures every new incoming bubble until either the reply goes quiet (4s, with an 8s minimum collection window) or the budget ends.
 * A prompt message is excluded so the bot's question is not mistaken for its answer.
 */
private suspend fun collectReply(
    view: WebView,
    baseline: Set<String>,
    promptText: String?
): String {
    val captured = linkedMapOf<String, String>()
    var lastNewAt = 0L
    var firstNewAt = 0L
    val deadline = android.os.SystemClock.elapsedRealtime() + 90_000L
    while (android.os.SystemClock.elapsedRealtime() < deadline) {
        val snapshot = runCatching { TelegramWeb.snapshot(view) }.getOrElse { break }
        snapshot.messages
            .filter { message ->
                !message.outgoing && message.key !in baseline && message.text.isNotBlank() &&
                    message.text.trim() != promptText?.trim()
            }
            .forEach { message ->
                if (!captured.containsKey(message.key)) {
                    captured[message.key] = message.text.trim()
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (firstNewAt == 0L) firstNewAt = now
                    lastNewAt = now
                }
            }
        if (captured.isNotEmpty() && android.os.SystemClock.elapsedRealtime() - lastNewAt >= 4_000L && android.os.SystemClock.elapsedRealtime() - firstNewAt >= 8_000L) break
        delay(700L)
    }
    return captured.values.take(20).joinToString("\n\n").trim()
}

@Composable
private fun ScreenBlock(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(13.dp), content = content)
}
