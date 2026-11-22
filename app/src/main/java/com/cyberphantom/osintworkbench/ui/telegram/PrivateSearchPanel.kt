package com.cyberphantom.osintworkbench.ui.telegram

import android.webkit.WebView
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.viewinterop.AndroidView
import com.cyberphantom.osintworkbench.data.LocalStore
import com.cyberphantom.osintworkbench.data.PrivateSourceBot
import com.cyberphantom.osintworkbench.data.PrivateSourceKind
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
fun PrivateSearchPanel(store: LocalStore, onSaved: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val configStore = remember { PrivateSourcesStore(context) }
    var bots by remember { mutableStateOf(configStore.loadBots()) }
    var selectedBotId by rememberSaveable { mutableStateOf<String?>(bots.firstOrNull()?.id) }
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(PrivateSourceKind.NUMBER) }
    var directSend by rememberSaveable { mutableStateOf(false) }
    var saveReplies by rememberSaveable { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("أدخل القيمة ثم شغّل الطلب؛ سيتابع المصدر المختار ويجمع الاستجابة.") }
    var result by remember { mutableStateOf<String?>(null) }
    var log by remember { mutableStateOf(emptyList<String>()) }
    var diagnostics by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
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
            status = "نافذة جلسة المصدر لم تجهز بعد. انتظر ثانية ثم أعد المحاولة."
            return
        }
        if (query.isBlank()) {
            status = "أدخل القيمة المطلوبة أولاً."
            return
        }
        val command = bot.command(kind).trim()
        if (command.isBlank()) {
            status = "هذا النوع غير مضبوط لهذا المصدر. راجع إعدادات المصادر الخاصة."
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
                addLog("المصدر المحدد: ${bot.name}")
                val opened = TelegramWeb.openChat(view, bot.username) { addLog(it) }
                if (!TelegramWeb.chatMatches(opened, bot.username, bot.name)) {
                    throw IllegalStateException("تعذر التحقق من أن الجلسة تعرض المصدر المحدد. لم يتم إرسال أي طلب.")
                }
                addLog("تم التحقق من نافذة المصدر.")

                val beforeCommand = opened.messages.map { it.key }.toSet()
                addLog("إرسال الأمر: $command")
                val sent = TelegramWeb.send(view, command)
                addLog("تم الإرسال عبر ${sent.via}${if (sent.composerCleared) "" else " (المحرر لم يُفرغ فوراً)"}")
                val echo = TelegramWeb.waitFor(view, 15_000L) { snapshot ->
                    snapshot.messages.any { it.outgoing && it.text.trim() == command && it.key !in beforeCommand }
                }
                if (echo == null) {
                    throw IllegalStateException("لم يظهر تأكيد إرسال الأمر في سجل المحادثة. أوقفنا التنفيذ لتجنب إرسال القيمة إلى محادثة غير مؤكدة.")
                }

                val beforeValue = beforeCommand
                var promptText: String? = null
                if (directSend) {
                    addLog("وضع الإرسال المباشر: انتظار قصير ثم إرسال القيمة.")
                    delay(1_200L)
                } else {
                    addLog("انتظار مطالبة المصدر بالقيمة…")
                    val prompt = TelegramWeb.waitFor(view, 30_000L) { snapshot ->
                        snapshot.messages.any { message ->
                            !message.outgoing && message.key !in beforeValue &&
                                TelegramWeb.looksLikePrompt(message.text, searchKind, bot.prompt(searchKind))
                        }
                    }
                    if (prompt == null) {
                        throw IllegalStateException("لم تصل مطالبة واضحة من المصدر. راجع نص المطالبة في إعداد المصدر أو فعّل خيار الإرسال المباشر إذا كان المصدر لا يطلب القيمة.")
                    } else {
                        promptText = prompt.messages.lastOrNull { !it.outgoing && it.key !in beforeValue }?.text
                        addLog("وصلت المطالبة: ${promptText?.take(80)}")
                    }
                }

                val beforeReply = TelegramWeb.snapshot(view).messages.map { it.key }.toSet()
                addLog("إرسال القيمة: $value")
                val valueSent = TelegramWeb.send(view, value)
                addLog("تم إرسال القيمة عبر ${valueSent.via}")
                val valueEcho = TelegramWeb.waitFor(view, 15_000L) { snapshot ->
                    snapshot.messages.any { it.outgoing && it.text.trim() == value && it.key !in beforeReply }
                }
                if (valueEcho == null) {
                    throw IllegalStateException("لم يظهر تأكيد إرسال القيمة في سجل المحادثة. لم نتابع انتظار الرد حتى لا ننسب رداً غير مرتبط بالطلب.")
                }

                addLog("جمع استجابة المصدر…")
                val reply = collectReply(view, beforeReply, promptText)
                if (reply.isBlank()) {
                    val diag = runCatching { TelegramWeb.diagnostics(view) }.getOrNull()
                    diagnostics = diag?.summary()?.replace("web.telegram.org", "جلسة المصدر")
                    throw IllegalStateException("لم تصل استجابة نصية خلال المهلة. قد تكون النتيجة صورة أو ملفاً، أو ما زال المصدر يعالج الطلب. راجع التشخيص.")
                }
                result = reply
                status = "اكتمل الطلب. النتيجة من ${bot.name}."
                addLog("تم جمع ${reply.length} حرفاً.")
                if (saveReplies) {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val node = store.upsertEntity(
                                value = value,
                                kind = "PRIVATE_SOURCE_LOOKUP@${bot.id}",
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
                    }
                    onSaved()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                status = error.message ?: "تعذر إكمال الطلب."
                diagnostics = runCatching { TelegramWeb.diagnostics(view) }.getOrNull()?.summary()?.replace("web.telegram.org", "جلسة المصدر")
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
                    if (selectedBot?.numberCommand?.isNotBlank() == true) {
                        ModeChip(
                            selected = kind == PrivateSourceKind.NUMBER,
                            label = "رقم",
                            onClick = { kind = PrivateSourceKind.NUMBER },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (selectedBot?.nameCommand?.isNotBlank() == true) {
                        ModeChip(
                            selected = kind == PrivateSourceKind.NAME,
                            label = "اسم",
                            onClick = { kind = PrivateSourceKind.NAME },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                IntelField(
                    value = query,
                    onValueChange = { query = it; result = null },
                    label = if (kind == PrivateSourceKind.NUMBER) "القيمة الرقمية" else "الاسم",
                    placeholder = if (kind == PrivateSourceKind.NUMBER) "+967…" else "الاسم الكامل"
                )
                ToggleRow(
                    checked = directSend,
                    onCheckedChange = { directSend = it },
                    title = "إرسال القيمة مباشرة",
                    detail = "يرسل الأمر ثم القيمة مباشرة دون انتظار مطالبة. استخدمه فقط عندما لا يطلب المصدر قيمة منفصلة."
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
                    enabled = !busy && selectedBot != null && query.isNotBlank()
                )
                Text(
                    status,
                    fontSize = 9.sp,
                    lineHeight = 14.sp,
                    color = if (status.startsWith("تعذر") || status.startsWith("لم")) IntelColors.Amber else IntelColors.TextDim
                )
            }
        }

        SectionBar("02", "جلسة المصدر", "SESSION")
        IntelPanel(contentPadding = 8) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Filled.Public, contentDescription = null, tint = IntelColors.Signal, modifier = Modifier.size(14.dp))
                MicroLabel("PRIVATE SESSION · تسجيل الدخول يتم هنا مرة واحدة", IntelColors.TextFaint)
            }
            Surface(
                modifier = Modifier.fillMaxWidth().height(300.dp),
                shape = RoundedCornerShape(2.dp),
                border = BorderStroke(1.dp, IntelColors.HairlineStrong),
                color = IntelColors.Void
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    factory = { webContext ->
                        SessionWebView(webContext).apply {
                            TelegramWeb.configure(this)
                            webView = this
                        }
                    },
                    update = { view ->
                        if (webView !== view) webView = view
                    }
                )
            }
            Text(
                "هذه نافذة الجلسة التي يستخدمها تنفيذ المصادر الخاصة. أكمل تسجيل الدخول فيها مرة واحدة واتركها جاهزة أثناء تنفيذ الطلبات.",
                fontSize = 9.sp,
                color = IntelColors.TextFaint,
                lineHeight = 13.sp
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
