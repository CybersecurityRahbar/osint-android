package com.cyberphantom.osintworkbench.ui.telegram

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberphantom.osintworkbench.data.LocalStore
import com.cyberphantom.osintworkbench.data.PrivateSourceBot
import com.cyberphantom.osintworkbench.data.PrivateSourceKind
import com.cyberphantom.osintworkbench.data.PrivateSourcesStore
import com.cyberphantom.osintworkbench.ui.design.ActionButton
import com.cyberphantom.osintworkbench.ui.design.DataText
import com.cyberphantom.osintworkbench.ui.design.GhostButton
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PrivateSearchPanel(
    store: LocalStore,
    onSaved: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val configStore = remember(context) { PrivateSourcesStore(context) }
    var bots by remember { mutableStateOf(configStore.loadBots()) }
    var selectedBotId by rememberSaveable { mutableStateOf(bots.firstOrNull()?.id) }
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(PrivateSourceKind.NUMBER) }
    var saveReplies by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var status by remember {
        mutableStateOf("أدخل قيمة واحدة. سيتحقق المحرك من جلسة Telegram والوجهة قبل تنفيذ أي إرسال.")
    }
    var result by remember { mutableStateOf<TelegramSearchResult?>(null) }
    var log by remember { mutableStateOf(emptyList<String>()) }
    var diagnostics by remember { mutableStateOf<String?>(null) }
    var currentStage by remember { mutableStateOf<TelegramSearchStage?>(null) }
    val webView by PrivateSessionHost.sessionView
    val scope = rememberCoroutineScope()
    val controller = remember { TelegramSessionRuntime.controller }
    val selectedBot = bots.firstOrNull { it.id == selectedBotId }

    LaunchedEffect(bots) {
        if (bots.none { it.id == selectedBotId }) {
            selectedBotId = bots.firstOrNull()?.id
        }
    }

    fun addLog(message: String) {
        val stamp = formatClock(System.currentTimeMillis())
        log = (log + "$stamp  $message").takeLast(18)
    }

    fun beginSearch() {
        val bot = selectedBot ?: return
        val value = query.trim()
        if (webView == null) {
            status = "مضيف جلسة Telegram غير جاهز بعد. لم يُطلب تسجيل دخول جديد."
            return
        }
        if (value.isBlank()) {
            status = "أدخل قيمة البحث أولاً."
            return
        }
        if (busy) return

        busy = true
        result = null
        diagnostics = null
        currentStage = null
        log = emptyList()
        status = "تهيئة الطلب والتحقق من الجلسة المحفوظة…"

        activeJob = scope.launch {
            try {
                addLog("بدأ طلب واحد للمصدر المحدد؛ لن تُسجّل قيمة البحث في سجل الخطوات.")
                val completed = controller.search(
                    TelegramSearchRequest(
                        bot = bot,
                        kind = kind,
                        value = value
                    )
                ) { update ->
                    currentStage = update.stage
                    status = update.message
                    addLog("${update.stage.name}: ${update.message}")
                }

                result = completed
                status = when {
                    completed.hasMedia ->
                        "وصل الرد. يحتوي على نص و/أو وسائط أو أزرار؛ راجع نوع كل رسالة أدناه."
                    else -> "اكتمل البحث ووصل رد جديد من ${completed.sourceName}."
                }
                addLog("اكتمل الطلب: ${completed.messageCount} رسالة جديدة.")
                if (saveReplies) {
                    runCatching {
                        val detail = completed.displayText
                        withContext(Dispatchers.IO) {
                            val node = store.upsertEntity(
                                value = value,
                                kind = "PRIVATE_SOURCE_LOOKUP@" + bot.id,
                                source = "مصدر خاص · ${bot.name}",
                                confidence = 55
                            )
                            store.addFinding(
                                entityId = node,
                                title = "رد ${bot.name}",
                                detail = detail,
                                source = "مصدر خاص · ${bot.name}",
                                severity = "info"
                            )
                        }
                        onSaved()
                    }.onFailure { saveError ->
                        addLog("وصل الرد لكن تعذر حفظه: ${saveError.message ?: "خطأ تخزين"}.")
                    }
                }
            } catch (cancelled: CancellationException) {
                status = "أُلغي الطلب. لم يُعد المحرك إرسال الأمر أو القيمة تلقائياً."
                addLog("أُلغي التنفيذ بناءً على طلب المستخدم.")
                throw cancelled
            } catch (error: Exception) {
                status = error.message ?: "تعذر إكمال طلب المصدر."
                addLog("توقف الطلب: ${error.message ?: error.javaClass.simpleName}")
                val view = PrivateSessionHost.peek()
                diagnostics = view?.let {
                    runCatching { TelegramWeb.diagnostics(it).summary() }.getOrNull()
                }
            } finally {
                busy = false
                activeJob = null
            }
        }
    }

    ScreenBlock {
        SectionBar(
            "01",
            "المصادر الخاصة",
            if (bots.isEmpty()) "غير مضبوط" else "${bots.size} مصدر"
        )

        if (bots.isEmpty()) {
            NoteBlock(
                "لا توجد مصادر مضبوطة. أضف مصدراً من إعدادات المصادر الخاصة أولاً.",
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
                                if (!busy) {
                                    selectedBotId = bot.id
                                    result = null
                                    diagnostics = null
                                }
                            },
                            accent = IntelColors.Violet
                        )
                    }
                }

                selectedBot?.let { bot ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Pill(
                            if (kind == PrivateSourceKind.NUMBER) "بحث رقمي" else "بحث بالاسم",
                            IntelColors.Violet
                        )
                        MicroLabel("المصدر: ${bot.name}", IntelColors.TextFaint)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    ModeChip(
                        selected = kind == PrivateSourceKind.NUMBER,
                        label = "رقم",
                        onClick = {
                            if (!busy) {
                                kind = PrivateSourceKind.NUMBER
                                result = null
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ModeChip(
                        selected = kind == PrivateSourceKind.NAME,
                        label = "اسم",
                        onClick = {
                            if (!busy) {
                                kind = PrivateSourceKind.NAME
                                result = null
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                IntelField(
                    value = query,
                    onValueChange = {
                        if (!busy) {
                            query = it
                            result = null
                        }
                    },
                    label = if (kind == PrivateSourceKind.NUMBER) "القيمة الرقمية" else "الاسم",
                    placeholder = if (kind == PrivateSourceKind.NUMBER) "+967…" else "الاسم الكامل"
                )

                NoteBlock(
                    "تدفق هذا المصدر: ${selectedBot?.flow(kind)?.label() ?: "غير محدد"}. " +
                        "يدعم المحرك الأنماط الثلاثة المحفوظة في الإعدادات.",
                    IntelColors.Violet,
                    "REQUEST FLOW"
                )

                ToggleRow(
                    checked = saveReplies,
                    onCheckedChange = { saveReplies = it },
                    title = "حفظ الرد في قاعدة الأدلة",
                    detail = "مغلق افتراضياً لأن الرد وقيمة البحث قد يتضمنان بيانات شخصية."
                )

                ActionButton(
                    text = "تنفيذ الطلب عبر جلسة Telegram",
                    onClick = { beginSearch() },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Send,
                    loading = busy,
                    enabled = !busy && selectedBot != null &&
                        query.isNotBlank() && webView != null
                )

                if (busy) {
                    GhostButton(
                        text = "إلغاء الطلب الحالي",
                        onClick = {
                            status = "جارٍ إلغاء الطلب…"
                            addLog("طلب المستخدم إلغاء العملية.")
                            activeJob?.cancel()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Filled.Close,
                        accent = IntelColors.Amber
                    )
                }

                Text(
                    status,
                    fontSize = 9.sp,
                    lineHeight = 14.sp,
                    color = if (
                        status.contains("لم ") ||
                        status.contains("تعذر") ||
                        status.contains("انتهت") ||
                        status.contains("فشل")
                    ) IntelColors.Amber else IntelColors.TextDim
                )
            }
        }

        if (webView == null) {
            NoteBlock(
                "مضيف Telegram الدائم لم يُنشأ بعد. لا تحذف بيانات التطبيق ولا تسجّل الخروج؛ سيستخدم البحث جلسة الويب الحالية نفسها.",
                IntelColors.Amber,
                "SESSION HOST"
            )
            GhostButton(
                text = "فتح إعدادات جلسة Telegram",
                onClick = onOpenSettings,
                accent = IntelColors.Signal
            )
        } else {
            NoteBlock(
                "جلسة Telegram الموجودة محفوظة كما هي. سيجري التحقق من المصادقة والمحادثة قبل أي إرسال؛ وجود WebView وحده لا يُعد دليلاً على الجاهزية.",
                IntelColors.Signal,
                "EXISTING SESSION"
            )
        }

        if (log.isNotEmpty()) {
            SectionBar(
                "02",
                "سجل مراحل المحرك",
                currentStage?.name ?: "TRACE",
                IntelColors.Signal
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IntelColors.PanelInset, RoundedCornerShape(2.dp))
                    .padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                log.forEach { line ->
                    Text(
                        line,
                        color = if (
                            line.contains("فشل") ||
                            line.contains("توقف") ||
                            line.contains("أُلغي")
                        ) IntelColors.Amber else IntelColors.TextFaint,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 12.sp
                    )
                }
            }
        }

        result?.let { completed ->
            SectionBar("03", "نتيجة المصدر", "${completed.messageCount} رسائل", IntelColors.Green)
            IntelPanel(accent = IntelColors.Green) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MicroLabel("CAPTURED RESPONSE · ${completed.sourceName}", IntelColors.Green)
                    Spacer(Modifier.weight(1f))
                    GhostButton(
                        text = "نسخ",
                        onClick = {
                            clipboard.setText(AnnotatedString(completed.displayText))
                        },
                        icon = Icons.Filled.ContentCopy
                    )
                }
                DataText(
                    "المحادثة: ${completed.peerTitle} · النمط: ${completed.flow.label()}",
                    IntelColors.TextFaint,
                    size = 8,
                    maxLines = 2
                )
                Text(
                    completed.displayText,
                    fontSize = 11.sp,
                    lineHeight = 18.sp,
                    color = IntelColors.Text
                )
                if (completed.hasMedia) {
                    NoteBlock(
                        "التقط المحرك وجود وسائط أو أزرار تفاعل، لكنه لا يحوّل محتوى الوسائط نفسه إلى نص. افتح محادثة المصدر لعرض الأصل.",
                        IntelColors.Amber,
                        "NON-TEXT CONTENT"
                    )
                }
            }
        }

        diagnostics?.let { text ->
            SectionBar("04", "حالة WebView عند التوقف", "SAFE DIAGNOSTICS", IntelColors.Amber)
            IntelPanel(contentPadding = 10) {
                Text(
                    text,
                    fontSize = 8.sp,
                    lineHeight = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = IntelColors.TextDim
                )
            }
        }
    }
}

private class RetainedWebViewTelegramDriver : TelegramSessionDriver {
    private fun retainedView(): SessionWebView {
        val view = PrivateSessionHost.peek()
            ?: throw IllegalStateException("جلسة Telegram غير مهيأة في مضيف التطبيق.")
        PrivateSessionHost.ensureMounted(view)
        return view
    }

    override suspend fun checkSession(): TelegramSnapshot =
        TelegramWeb.checkSession(retainedView())

    override suspend fun openTarget(username: String, configuredName: String) {
        TelegramWeb.openTarget(retainedView(), username, configuredName)
    }

    override suspend fun snapshot(): TelegramSnapshot =
        TelegramWeb.snapshot(retainedView())

    override suspend fun startBotIfAvailable(): Boolean =
        TelegramWeb.startBotIfAvailable(retainedView())

    override suspend fun send(text: String): TelegramSendAttempt =
        TelegramWeb.send(retainedView(), text)
}

@Composable
private fun ScreenBlock(
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(13.dp), content = content)
}


private object TelegramSessionRuntime {
    /** A process-wide lock prevents two recomposed search panels from driving one WebView. */
    val controller = TelegramSessionController(RetainedWebViewTelegramDriver())
}
