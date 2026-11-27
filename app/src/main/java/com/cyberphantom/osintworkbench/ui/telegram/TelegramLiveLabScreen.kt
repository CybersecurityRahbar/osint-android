package com.cyberphantom.osintworkbench.ui.telegram

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberphantom.osintworkbench.data.PrivateSourceBot
import com.cyberphantom.osintworkbench.data.PrivateSourceFlow
import com.cyberphantom.osintworkbench.data.PrivateSourceKind
import com.cyberphantom.osintworkbench.ui.design.GhostButton
import com.cyberphantom.osintworkbench.ui.design.IntelColors
import com.cyberphantom.osintworkbench.ui.design.IntelField
import com.cyberphantom.osintworkbench.ui.design.IntelPanel
import com.cyberphantom.osintworkbench.ui.design.MicroLabel
import com.cyberphantom.osintworkbench.ui.design.ModeChip
import com.cyberphantom.osintworkbench.ui.design.NoteBlock
import com.cyberphantom.osintworkbench.ui.design.SectionBar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Device-side live integration lab. It uses the same retained WebView and controller
 * as normal private-source searches. Account credentials never enter this profile.
 */
@Composable
fun TelegramLiveLabScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val configStore = remember { TelegramLiveLabSettingsStore(context) }
    var profile by remember { mutableStateOf(configStore.load()) }
    var authorized by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember {
        mutableStateOf("اضبط بوت اختبار تملكه أو لديك تفويض لاختباره، ثم افحص جلسة WebView الحالية.")
    }
    var report by remember { mutableStateOf<String?>(null) }
    var trace by remember { mutableStateOf(emptyList<String>()) }
    val sessionVisible by PrivateSessionHost.sessionVisible
    val scope = rememberCoroutineScope()

    fun addTrace(message: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        trace = (trace + "$stamp  $message").takeLast(30)
    }

    fun updateProfile(next: TelegramLiveLabProfile) {
        profile = next
        report = null
    }

    fun saveProfile() {
        val problem = validateTelegramLiveLabProfile(profile)
        if (problem != null) {
            status = problem
            return
        }
        configStore.save(profile)
        status = "تم حفظ إعدادات المختبر محلياً على هذا الجهاز."
        addTrace("LAB_CONFIG_SAVED")
    }

    fun inspectSession() {
        if (busy) return
        busy = true
        status = "جارٍ فحص جلسة Telegram WebView الفعلية..."
        report = null
        scope.launch {
            val view = PrivateSessionHost.peek()
            try {
                val retained = view ?: throw IllegalStateException(
                    "لم يتم إنشاء مضيف Telegram بعد. افتح نافذة الجلسة ثم أعد الفحص."
                )
                report = TelegramWeb.diagnostics(retained).summary()
                addTrace("DOM_DIAGNOSTICS_CAPTURED")
                val verified = TelegramWeb.checkSession(retained)
                report = TelegramWeb.diagnostics(retained).summary()
                status = "SESSION PASS — تم إثبات جلسة Telegram الحالية. عنوان المحادثة: " +
                    verified.title.ifBlank { "لا توجد محادثة مفتوحة" }
                addTrace("CHECK_SESSION_PASS · " + verified.authEvidence.ifBlank { "no-auth-evidence-label" })
            } catch (error: Exception) {
                status = "SESSION FAIL — " + (error.message ?: error.javaClass.simpleName)
                addTrace("CHECK_SESSION_FAIL · " + error.javaClass.simpleName)
                report = view?.let {
                    runCatching { TelegramWeb.diagnostics(it).summary() }.getOrNull()
                } ?: "WEBVIEW: NOT_CREATED"
            } finally {
                busy = false
            }
        }
    }

    fun runFullLiveTest() {
        if (busy) return
        if (!authorized) {
            status = "أكد أولاً أن البوت مملوك لك أو أن لديك إذناً صريحاً لاختباره."
            return
        }
        val problem = validateTelegramLiveLabProfile(profile)
        if (problem != null) {
            status = problem
            return
        }
        configStore.save(profile)
        busy = true
        report = null
        trace = emptyList()
        status = "جارٍ تشغيل اختبار حي واحد عبر WebView المحفوظة..."
        scope.launch {
            TelegramSessionRuntime.isSearchActive = true
            try {
                val bot = PrivateSourceBot(
                    id = "telegram-live-lab",
                    name = profile.botName.trim(),
                    username = profile.botUsername.trim().removePrefix("@"),
                    numberCommand = if (profile.kind == PrivateSourceKind.NUMBER) profile.command.trim() else "",
                    nameCommand = if (profile.kind == PrivateSourceKind.NAME) profile.command.trim() else "",
                    numberPrompt = profile.prompt.trim(),
                    namePrompt = profile.prompt.trim(),
                    numberFlow = profile.flow,
                    nameFlow = profile.flow,
                    chatTitle = profile.chatTitle.trim()
                )
                val result = TelegramSessionRuntime.controller.search(
                    TelegramSearchRequest(
                        bot = bot,
                        kind = profile.kind,
                        value = profile.testValue.trim()
                    )
                ) { update ->
                    status = update.message
                    addTrace(update.stage.name + " · " + update.message)
                }
                val replyText = result.messages.joinToString("\n") { it.text }
                val markerMatched = profile.expectedReply.isBlank() ||
                    replyText.contains(profile.expectedReply.trim(), ignoreCase = true)
                status = if (markerMatched) {
                    "LIVE PASS — اكتمل تدفق المحادثة ووصلت " + result.messageCount + " رسالة رد جديدة."
                } else {
                    "LIVE PARTIAL — وصل رد جديد، لكن علامة الرد المتوقعة لم تظهر."
                }
                addTrace("REPLY_CAPTURED · count=" + result.messageCount + " · expected-marker=" + markerMatched)
                val currentView = PrivateSessionHost.peek()
                report = currentView?.let { TelegramWeb.diagnostics(it).summary() }
            } catch (error: Exception) {
                status = "LIVE FAIL — " + (error.message ?: error.javaClass.simpleName)
                addTrace("LIVE_TEST_FAIL · " + error.javaClass.simpleName)
                report = PrivateSessionHost.peek()?.let {
                    runCatching { TelegramWeb.diagnostics(it).summary() }.getOrNull()
                } ?: "WEBVIEW: NOT_CREATED"
            } finally {
                TelegramSessionRuntime.isSearchActive = false
                busy = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GhostButton(
            text = "رجوع إلى إعدادات المصادر الخاصة",
            onClick = onBack,
            accent = IntelColors.TextDim
        )
        SectionBar("05", "مختبر Telegram الحي", "DEVICE E2E LAB", IntelColors.Signal)

        IntelPanel(accent = IntelColors.Signal) {
            MicroLabel("AUTHENTICATION · EXISTING WEBVIEW", IntelColors.Signal)
            Text(
                "تسجيل الدخول يتم من صفحة Telegram Web الرسمية داخل التطبيق. افتح الجلسة وسجّل الدخول بالطريقة المعتادة عند الحاجة؛ سيبقى ملف الجلسة نفسه مستخدماً في الاختبار والبحث. لا توجد حقول لكلمة المرور أو رمز التحقق أو Cookies.",
                color = IntelColors.TextDim,
                fontSize = 9.sp,
                lineHeight = 15.sp
            )
            GhostButton(
                text = if (sessionVisible) "إخفاء نافذة Telegram والعودة للمختبر" else "فتح Telegram Web لتسجيل الدخول أو المراجعة",
                onClick = {
                    if (sessionVisible) PrivateSessionHost.hideSession()
                    else PrivateSessionHost.showSession()
                },
                accent = IntelColors.Signal,
                modifier = Modifier.fillMaxWidth()
            )
            NoteBlock(
                "المختبر يعمل على الهاتف باستخدام WebView نفسها التي يستخدمها التطبيق. لا ينشئ جلسة ثانية ولا يحذف بيانات الجلسة الحالية.",
                IntelColors.Green,
                "SAME PERSISTENT SESSION"
            )
        }

        IntelPanel(accent = IntelColors.Violet) {
            MicroLabel("LIVE TEST PROFILE · STORED LOCALLY", IntelColors.Violet)
            Text(
                "هذه إعدادات بوت الاختبار فقط، وهي منفصلة عن قائمة مصادر البحث العادية. تُحفظ محلياً في مساحة التطبيق ولا تُرسل إلى GitHub.",
                color = IntelColors.TextFaint,
                fontSize = 9.sp,
                lineHeight = 14.sp
            )
            IntelField(profile.botName, { updateProfile(profile.copy(botName = it)) }, "اسم بوت الاختبار")
            IntelField(profile.botUsername, { updateProfile(profile.copy(botUsername = it)) }, "معرّف البوت بدون @")
            IntelField(profile.chatTitle, { updateProfile(profile.copy(chatTitle = it)) }, "عنوان المحادثة المتوقع (اختياري)")
            MicroLabel("نوع التدفق التجريبي", IntelColors.TextDim)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ModeChip(
                    selected = profile.kind == PrivateSourceKind.NUMBER,
                    label = "مسار الرقم",
                    onClick = { updateProfile(profile.copy(kind = PrivateSourceKind.NUMBER)) },
                    modifier = Modifier.weight(1f),
                    accent = IntelColors.Violet
                )
                ModeChip(
                    selected = profile.kind == PrivateSourceKind.NAME,
                    label = "مسار الاسم",
                    onClick = { updateProfile(profile.copy(kind = PrivateSourceKind.NAME)) },
                    modifier = Modifier.weight(1f),
                    accent = IntelColors.Violet
                )
            }
            MicroLabel("آلية البوت", IntelColors.TextDim)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PrivateSourceFlow.entries.forEach { flow ->
                    ModeChip(
                        selected = profile.flow == flow,
                        label = when (flow) {
                            PrivateSourceFlow.COMMAND_AND_PROMPT -> "أمر + مطالبة"
                            PrivateSourceFlow.COMMAND_THEN_VALUE -> "أمر + قيمة"
                            PrivateSourceFlow.DIRECT_MESSAGE -> "رسالة مباشرة"
                            PrivateSourceFlow.VALUE_ONLY -> "قيمة فقط (قديم)"
                        },
                        onClick = { updateProfile(profile.copy(flow = flow)) },
                        modifier = Modifier.weight(1f),
                        accent = IntelColors.Violet
                    )
                }
            }
            IntelField(
                profile.command,
                { updateProfile(profile.copy(command = it)) },
                "أمر الاختبار (يُترك فارغاً في نمط قيمة فقط)"
            )
            IntelField(
                profile.prompt,
                { updateProfile(profile.copy(prompt = it)) },
                "نص المطالبة المتوقع (لنمط أمر + مطالبة)"
            )
            IntelField(
                profile.testValue,
                { updateProfile(profile.copy(testValue = it)) },
                "قيمة اختبار اصطناعية فقط"
            )
            IntelField(
                profile.expectedReply,
                { updateProfile(profile.copy(expectedReply = it)) },
                "عبارة متوقعة داخل الرد (اختياري)"
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = authorized,
                    onCheckedChange = { authorized = it }
                )
                Text(
                    "أؤكد أن البوت مملوك لي أو أن لدي تفويضاً لاختباره، وأن قيمة الاختبار لا تتضمن بيانات شخص آخر.",
                    color = IntelColors.TextDim,
                    fontSize = 8.sp,
                    lineHeight = 13.sp
                )
            }
            NoteBlock(
                "استخدم بوت اختبار تملكه أو بوت Echo تجريبياً وقيمة اصطناعية مثل OSINT_LAB_PING_2026. لا تختبر باستخدام أرقام أو أسماء أشخاص حقيقيين، ولا ترسل بيانات دخول Telegram.",
                IntelColors.Amber,
                "SAFE TEST DATA"
            )
            GhostButton(
                text = "حفظ إعدادات المختبر على الجهاز",
                onClick = { saveProfile() },
                accent = IntelColors.Violet,
                modifier = Modifier.fillMaxWidth()
            )
            GhostButton(
                text = if (busy) "الاختبار قيد التنفيذ..." else "1 · فحص الجلسة الحالية",
                onClick = { inspectSession() },
                accent = IntelColors.Signal,
                modifier = Modifier.fillMaxWidth()
            )
            GhostButton(
                text = if (busy) "الاختبار قيد التنفيذ..." else "2 · تنفيذ اختبار المحادثة الحي الكامل",
                onClick = { runFullLiveTest() },
                accent = IntelColors.Green,
                modifier = Modifier.fillMaxWidth()
            )
        }

        IntelPanel(accent = if (status.contains("FAIL")) IntelColors.Amber else IntelColors.Signal) {
            MicroLabel("TEST STATUS", IntelColors.Signal)
            Text(status, color = IntelColors.Text, fontSize = 10.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
            if (busy) {
                Spacer(Modifier.height(3.dp))
                Text("جارٍ تنفيذ المرحلة الحالية عبر جلسة الجهاز...", color = IntelColors.Amber, fontSize = 8.sp)
            }
        }

        if (trace.isNotEmpty()) {
            SectionBar("06", "تسلسل الاختبار", "LIVE TRACE", IntelColors.Blue)
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(IntelColors.PanelInset, RoundedCornerShape(3.dp))
                    .padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                trace.forEach { line ->
                    Text(
                        line,
                        color = if (line.contains("FAIL")) IntelColors.Amber else IntelColors.TextFaint,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        lineHeight = 12.sp
                    )
                }
            }
        }

        report?.let {
            SectionBar("07", "تقرير WebView الآمن", "DOM / SESSION DIAGNOSTICS", IntelColors.Amber)
            IntelPanel(contentPadding = 10) {
                Text(
                    it,
                    color = IntelColors.TextDim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    lineHeight = 12.sp
                )
            }
        }
    }
}

internal data class TelegramLiveLabProfile(
    val botName: String = "Telegram Lab Bot",
    val botUsername: String = "",
    val chatTitle: String = "",
    val kind: PrivateSourceKind = PrivateSourceKind.NAME,
    val flow: PrivateSourceFlow = PrivateSourceFlow.DIRECT_MESSAGE,
    val command: String = "",
    val prompt: String = "",
    val testValue: String = "OSINT_LAB_PING_2026",
    val expectedReply: String = ""
)

internal fun validateTelegramLiveLabProfile(profile: TelegramLiveLabProfile): String? {
    if (profile.botName.isBlank()) return "أدخل اسماً لبوت الاختبار."
    if (!profile.botUsername.trim().removePrefix("@").matches(Regex("[A-Za-z0-9_]{5,32}"))) {
        return "أدخل معرّف بوت Telegram صحيحاً (5 إلى 32 حرفاً أو رقماً أو _)."
    }
    if ((profile.flow == PrivateSourceFlow.COMMAND_AND_PROMPT ||
            profile.flow == PrivateSourceFlow.COMMAND_THEN_VALUE) && profile.command.isBlank()) {
        return "أدخل الأمر المطلوب لهذا التدفق أو اختر نمط قيمة فقط."
    }
    if (profile.flow == PrivateSourceFlow.COMMAND_AND_PROMPT && profile.prompt.isBlank()) {
        return "أدخل نص المطالبة المتوقع أو اختر تدفقاً لا يحتاج إلى مطالبة."
    }
    if (profile.testValue.isBlank()) return "أدخل قيمة اختبار اصطناعية."
    return null
}

private class TelegramLiveLabSettingsStore(context: android.content.Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "telegram_live_lab",
        android.content.Context.MODE_PRIVATE
    )

    fun load(): TelegramLiveLabProfile = TelegramLiveLabProfile(
        botName = preferences.getString("bot_name", "Telegram Lab Bot").orEmpty(),
        botUsername = preferences.getString("bot_username", "").orEmpty(),
        chatTitle = preferences.getString("chat_title", "").orEmpty(),
        kind = runCatching {
            PrivateSourceKind.valueOf(preferences.getString("kind", PrivateSourceKind.NAME.name).orEmpty())
        }.getOrDefault(PrivateSourceKind.NAME),
        flow = PrivateSourceFlow.fromStored(
            preferences.getString("flow", PrivateSourceFlow.DIRECT_MESSAGE.name),
            PrivateSourceFlow.DIRECT_MESSAGE
        ),
        command = preferences.getString("command", "").orEmpty(),
        prompt = preferences.getString("prompt", "").orEmpty(),
        testValue = preferences.getString("test_value", "OSINT_LAB_PING_2026").orEmpty(),
        expectedReply = preferences.getString("expected_reply", "").orEmpty()
    )

    fun save(profile: TelegramLiveLabProfile) {
        preferences.edit()
            .putString("bot_name", profile.botName.trim())
            .putString("bot_username", profile.botUsername.trim().removePrefix("@"))
            .putString("chat_title", profile.chatTitle.trim())
            .putString("kind", profile.kind.name)
            .putString("flow", profile.flow.name)
            .putString("command", profile.command.trim())
            .putString("prompt", profile.prompt.trim())
            .putString("test_value", profile.testValue.trim())
            .putString("expected_reply", profile.expectedReply.trim())
            .apply()
    }
}
