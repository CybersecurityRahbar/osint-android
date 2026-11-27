package com.cyberphantom.osintworkbench.ui.telegram

import com.cyberphantom.osintworkbench.data.PrivateSourceBot
import com.cyberphantom.osintworkbench.data.PrivateSourceFlow
import com.cyberphantom.osintworkbench.data.PrivateSourceKind
import com.cyberphantom.osintworkbench.data.PrivateSourceTextMatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import java.util.UUID

enum class TelegramSearchStage(val label: String) {
    CHECK_SESSION("التحقق من جلسة Telegram المحفوظة"),
    OPEN_TARGET("فتح محادثة المصدر"),
    VERIFY_PEER("التحقق من هوية المحادثة"),
    VERIFY_COMPOSER("التحقق من جاهزية محرر الرسائل"),
    SEND_COMMAND("إرسال أمر المصدر"),
    WAIT_COMMAND_ECHO("تأكيد ظهور أمر المصدر"),
    WAIT_PROMPT("انتظار مطالبة المصدر"),
    SEND_VALUE("إرسال قيمة البحث"),
    WAIT_VALUE_ECHO("تأكيد ظهور قيمة البحث"),
    COLLECT_REPLY("جمع ردود المصدر الجديدة"),
    PRESENT_RESULT("عرض النتيجة"),
    COMPLETED("اكتمل البحث"),
    FAILED("فشل البحث"),
    CANCELLED("أُلغي البحث")
}

data class TelegramStageUpdate(
    val requestId: String,
    val stage: TelegramSearchStage,
    val message: String
)

data class TelegramSearchRequest(
    val bot: PrivateSourceBot,
    val kind: PrivateSourceKind,
    val value: String
)

data class TelegramSearchResult(
    val requestId: String,
    val sourceName: String,
    val peerTitle: String,
    val flow: PrivateSourceFlow,
    val messages: List<TelegramMessage>,
    val completedAtMillis: Long
) {
    val displayText: String get() = TelegramReplyFormatter.format(messages)
    val hasMedia: Boolean get() = messages.any { it.contentType != "text" || it.mediaCount > 0 }
    val messageCount: Int get() = messages.size
}

interface TelegramSessionDriver {
    suspend fun checkSession(): TelegramSnapshot
    suspend fun openTarget(username: String, configuredName: String)
    suspend fun snapshot(): TelegramSnapshot
    suspend fun startBotIfAvailable(): Boolean
    suspend fun send(text: String): TelegramSendAttempt
}

data class TelegramStageTimeouts(
    val checkSessionMillis: Long = 15_000L,
    val openTargetMillis: Long = 8_000L,
    val verifyPeerMillis: Long = 55_000L,
    val verifyComposerMillis: Long = 35_000L,
    val sendMillis: Long = 20_000L,
    val commandEchoMillis: Long = 20_000L,
    val promptMillis: Long = 35_000L,
    val valueEchoMillis: Long = 20_000L,
    val collectReplyMillis: Long = 90_000L,
    val pollIntervalMillis: Long = 500L,
    val replyQuietMillis: Long = 4_000L,
    val replyMinimumMillis: Long = 8_000L
) {
    fun forStage(stage: TelegramSearchStage): Long = when (stage) {
        TelegramSearchStage.CHECK_SESSION -> checkSessionMillis
        TelegramSearchStage.OPEN_TARGET -> openTargetMillis
        TelegramSearchStage.VERIFY_PEER -> verifyPeerMillis
        TelegramSearchStage.VERIFY_COMPOSER -> verifyComposerMillis
        TelegramSearchStage.SEND_COMMAND,
        TelegramSearchStage.SEND_VALUE -> sendMillis
        TelegramSearchStage.WAIT_COMMAND_ECHO -> commandEchoMillis
        TelegramSearchStage.WAIT_PROMPT -> promptMillis
        TelegramSearchStage.WAIT_VALUE_ECHO -> valueEchoMillis
        TelegramSearchStage.COLLECT_REPLY -> collectReplyMillis
        else -> 10_000L
    }
}

class TelegramRequestAlreadyRunningException :
    IllegalStateException("هناك طلب مصادر خاصة قيد التنفيذ بالفعل. انتظر اكتماله أو ألغِه أولاً.")

class TelegramStageTimeoutException(val stage: TelegramSearchStage) :
    IllegalStateException(timeoutMessage(stage))

private fun timeoutMessage(stage: TelegramSearchStage): String = when (stage) {
    TelegramSearchStage.CHECK_SESSION ->
        "انتهت مهلة التحقق من جلسة Telegram المحفوظة. لم يتم تسجيل الخروج أو بدء تسجيل دخول جديد."
    TelegramSearchStage.OPEN_TARGET,
    TelegramSearchStage.VERIFY_PEER ->
        "انتهت مهلة فتح محادثة المصدر والتحقق من هويتها؛ لم يتم إرسال أي قيمة إلى محادثة غير مؤكدة."
    TelegramSearchStage.VERIFY_COMPOSER ->
        "انتهت مهلة انتظار محرر الرسائل. لم يتم إرسال الأمر أو القيمة."
    TelegramSearchStage.SEND_COMMAND,
    TelegramSearchStage.SEND_VALUE ->
        "تعذر تنفيذ إجراء الإرسال داخل Telegram. لم يحاول المحرك إعادة إرسال النص تلقائياً."
    TelegramSearchStage.WAIT_COMMAND_ECHO ->
        "لم يظهر تأكيد أمر المصدر في المحادثة. توقفت العملية ولم أرسل قيمة البحث، ولن أعيد إرسال الأمر تلقائياً منعاً للتكرار."
    TelegramSearchStage.WAIT_PROMPT ->
        "لم تصل مطالبة المصدر خلال المهلة. لم يتم إرسال قيمة البحث."
    TelegramSearchStage.WAIT_VALUE_ECHO ->
        "لم يظهر تأكيد قيمة البحث في المحادثة. لم أعد إرسالها تلقائياً منعاً لتكرار الطلب."
    TelegramSearchStage.COLLECT_REPLY ->
        "انتهت مهلة انتظار رد المصدر. لم يتم العثور على رسائل جديدة قابلة للربط بهذا الطلب."
    else -> "انتهت مهلة المرحلة: ${stage.label}."
}

/**
 * Serializes all actions against the one retained Telegram WebView. A timeout or
 * ambiguous send acknowledgement is terminal for that step: the controller never
 * retries the command/value automatically.
 */
class TelegramSessionController(
    private val driver: TelegramSessionDriver,
    private val timeouts: TelegramStageTimeouts = TelegramStageTimeouts()
) {
    private val requestMutex = Mutex()

    suspend fun search(
        request: TelegramSearchRequest,
        onUpdate: (TelegramStageUpdate) -> Unit = {}
    ): TelegramSearchResult {
        if (!requestMutex.tryLock()) throw TelegramRequestAlreadyRunningException()
        val requestId = UUID.randomUUID().toString()
        try {
            require(request.value.isNotBlank()) { "أدخل قيمة البحث أولاً." }
            val value = request.value.trim()
            val bot = request.bot
            val flow = bot.flow(request.kind)
            val command = bot.command(request.kind).trim()
            val usesCommand = flow == PrivateSourceFlow.COMMAND_AND_PROMPT ||
                flow == PrivateSourceFlow.COMMAND_THEN_VALUE
            if (usesCommand && command.isBlank()) {
                throw IllegalArgumentException("نمط المصدر المحدد يحتاج إلى أمر. راجع إعدادات هذا المصدر.")
            }

            val session = runStage(requestId, TelegramSearchStage.CHECK_SESSION, onUpdate) {
                driver.checkSession()
            }
            if (!session.isAuthenticated || session.isLoginPage) {
                throw IllegalStateException(
                    "لم يتم التحقق من جلسة Telegram الحالية. افتح الجلسة المحفوظة وتحقق منها؛ لم يحذف التطبيق بياناتها ولم يبدأ تسجيل دخول جديداً."
                )
            }

            runStage(requestId, TelegramSearchStage.OPEN_TARGET, onUpdate) {
                driver.openTarget(bot.username, bot.chatTitle)
            }

            val peer = runStage(requestId, TelegramSearchStage.VERIFY_PEER, onUpdate) {
                pollForSnapshot { current ->
                    if (current.isLoginPage) {
                        throw IllegalStateException("Telegram يعرض شاشة تسجيل الدخول؛ أوقفت العملية وحافظت على الجلسة الحالية.")
                    }
                    if (TelegramPeerMatcher.isTargetIdentity(
                            current,
                            bot.username,
                            bot.chatTitle
                        )
                    ) current else null
                } ?: throw IllegalStateException(
                    "لم أستطع إثبات أن المحادثة المفتوحة تخص @${bot.username}. لم يتم إرسال أي رسالة."
                )
            }

            runStage(requestId, TelegramSearchStage.VERIFY_COMPOSER, onUpdate) {
                var startControlChecked = false
                pollForSnapshot { current ->
                    if (!TelegramPeerMatcher.isTargetIdentity(
                            current,
                            bot.username,
                            bot.chatTitle
                        )
                    ) {
                        throw IllegalStateException("تغيرت المحادثة أثناء التحقق؛ أوقفت العملية قبل الإرسال.")
                    }
                    if (current.hasComposer) current
                    else {
                        if (!startControlChecked) {
                            startControlChecked = true
                            driver.startBotIfAvailable()
                        }
                        null
                    }
                } ?: throw IllegalStateException(
                    "تم التعرف على المحادثة، لكن محرر الرسائل لم يصبح جاهزاً. لم يُرسل الأمر أو القيمة."
                )
            }

            if (usesCommand) {
                val commandBaseline = runStage(
                    requestId,
                    TelegramSearchStage.SEND_COMMAND,
                    onUpdate
                ) {
                    ensurePeerStillSelected(bot)
                    val baseline = driver.snapshot().messages.map { it.key }.toSet()
                    driver.send(command)
                    baseline
                }

                runStage(requestId, TelegramSearchStage.WAIT_COMMAND_ECHO, onUpdate) {
                    pollForSnapshot { current ->
                        ensurePeer(current, bot)
                        current.messages.firstOrNull {
                            it.outgoing && it.key !in commandBaseline && it.text.trim() == command
                        }
                    } ?: throw IllegalStateException(
                        "تم تنفيذ محاولة الإرسال لكن لم يظهر تأكيد الأمر في المحادثة. توقفت العملية ولن أعيد الإرسال تلقائياً."
                    )
                }

                if (flow == PrivateSourceFlow.COMMAND_AND_PROMPT) {
                    runStage(requestId, TelegramSearchStage.WAIT_PROMPT, onUpdate) {
                        pollForSnapshot { current ->
                            ensurePeer(current, bot)
                            current.messages.firstOrNull { message ->
                                !message.outgoing &&
                                    message.key !in commandBaseline &&
                                    TelegramTextClassifier.looksLikePrompt(
                                        message.text,
                                        request.kind,
                                        bot.prompt(request.kind)
                                    )
                            }
                        } ?: throw IllegalStateException(
                            "لم تصل مطالبة متوافقة من المصدر. لم أرسل قيمة البحث."
                        )
                    }
                }
            }

            val valueBaseline = runStage(requestId, TelegramSearchStage.SEND_VALUE, onUpdate) {
                ensurePeerStillSelected(bot)
                val baseline = driver.snapshot().messages.map { it.key }.toSet()
                driver.send(value)
                baseline
            }

            runStage(requestId, TelegramSearchStage.WAIT_VALUE_ECHO, onUpdate) {
                pollForSnapshot { current ->
                    ensurePeer(current, bot)
                    current.messages.firstOrNull {
                        it.outgoing && it.key !in valueBaseline && it.text.trim() == value
                    }
                } ?: throw IllegalStateException(
                    "تم تنفيذ محاولة إرسال قيمة البحث لكن لم يظهر تأكيدها. لم أعد إرسال القيمة تلقائياً منعاً لتكرار الطلب."
                )
            }

            val replies = runStage(requestId, TelegramSearchStage.COLLECT_REPLY, onUpdate) {
                collectNewIncoming(bot, valueBaseline)
            }
            if (replies.isEmpty()) {
                throw IllegalStateException(
                    "لم يصل رد جديد من المصدر ضمن المهلة. لم يتم احتساب المطالبة أو الرسائل القديمة كنتيجة."
                )
            }

            emit(
                requestId,
                TelegramSearchStage.PRESENT_RESULT,
                "وصلت ${replies.size} رسالة جديدة من المحادثة المحددة.",
                onUpdate
            )
            val result = TelegramSearchResult(
                requestId = requestId,
                sourceName = bot.name,
                peerTitle = peer.title,
                flow = flow,
                messages = replies,
                completedAtMillis = System.currentTimeMillis()
            )
            emit(requestId, TelegramSearchStage.COMPLETED, "اكتمل البحث.", onUpdate)
            return result
        } catch (cancelled: CancellationException) {
            emit(requestId, TelegramSearchStage.CANCELLED, "أُلغي الطلب. لن يعاد إرسال أي رسالة تلقائياً.", onUpdate)
            throw cancelled
        } catch (error: Exception) {
            emit(requestId, TelegramSearchStage.FAILED, error.message ?: "فشل الطلب.", onUpdate)
            throw error
        } finally {
            requestMutex.unlock()
        }
    }

    private suspend fun ensurePeerStillSelected(bot: PrivateSourceBot) {
        val current = driver.snapshot()
        ensurePeer(current, bot)
        if (!current.hasComposer) {
            throw IllegalStateException("اختفى محرر الرسائل قبل الإرسال؛ أوقفت العملية.")
        }
    }

    private fun ensurePeer(snapshot: TelegramSnapshot, bot: PrivateSourceBot) {
        if (!TelegramPeerMatcher.chatMatches(
                snapshot,
                bot.username,
                bot.chatTitle
            )
        ) {
            throw IllegalStateException(
                "لم تعد المحادثة النشطة تطابق @${bot.username}. أوقفت العملية لحماية الرسالة من الإرسال إلى محادثة أخرى."
            )
        }
    }

    private suspend fun <T> runStage(
        requestId: String,
        stage: TelegramSearchStage,
        onUpdate: (TelegramStageUpdate) -> Unit,
        block: suspend () -> T
    ): T {
        emit(requestId, stage, stage.label, onUpdate)
        return try {
            withTimeout(timeouts.forStage(stage)) { block() }
        } catch (timeout: TimeoutCancellationException) {
            throw TelegramStageTimeoutException(stage)
        }
    }

    private suspend fun <T> pollForSnapshot(
        predicate: suspend (TelegramSnapshot) -> T?
    ): T? {
        val deadline = System.nanoTime() + timeouts.forStage(TelegramSearchStage.VERIFY_PEER) * 1_000_000L
        while (System.nanoTime() < deadline) {
            val current = driver.snapshot()
            val accepted = predicate(current)
            if (accepted != null) return accepted
            delay(timeouts.pollIntervalMillis.coerceAtLeast(1L))
        }
        return null
    }

    private suspend fun collectNewIncoming(
        bot: PrivateSourceBot,
        baseline: Set<String>
    ): List<TelegramMessage> {
        val deadline = System.nanoTime() +
            timeouts.collectReplyMillis.coerceAtLeast(1L) * 1_000_000L
        val collected = linkedMapOf<String, TelegramMessage>()
        var firstNewAtNanos = 0L
        var lastNewAtNanos = 0L

        while (System.nanoTime() < deadline) {
            val current = driver.snapshot()
            ensurePeer(current, bot)
            current.messages
                .filter { !it.outgoing && it.key !in baseline }
                .forEach { message ->
                    if (!collected.containsKey(message.key)) {
                        collected[message.key] = message
                        val now = System.nanoTime()
                        if (firstNewAtNanos == 0L) firstNewAtNanos = now
                        lastNewAtNanos = now
                    }
                }

            if (collected.isNotEmpty()) {
                val now = System.nanoTime()
                val quietMillis = (now - lastNewAtNanos) / 1_000_000L
                val totalMillis = (now - firstNewAtNanos) / 1_000_000L
                if (quietMillis >= timeouts.replyQuietMillis &&
                    totalMillis >= timeouts.replyMinimumMillis
                ) break
            }
            delay(timeouts.pollIntervalMillis.coerceAtLeast(1L))
        }
        return collected.values.take(40)
    }

    private fun emit(
        requestId: String,
        stage: TelegramSearchStage,
        message: String,
        callback: (TelegramStageUpdate) -> Unit
    ) {
        callback(TelegramStageUpdate(requestId, stage, message))
    }
}

object TelegramPeerMatcher {
    fun isTargetIdentity(
        snapshot: TelegramSnapshot,
        username: String,
        configuredTitle: String
    ): Boolean {
        if (!snapshot.isAuthenticated || snapshot.isLoginPage || snapshot.title.isBlank()) return false
        val handle = username.trim().removePrefix("@").lowercase()
        val route = snapshot.peerRoute
            .substringBefore('?')
            .trim('/')
            .removePrefix("@")
            .lowercase()
        val directHandleRoute = route == handle || route.startsWith("$handle/")
        val numericPeerRoute = route.matches(Regex("\\d{5,20}"))
        if (!directHandleRoute && !numericPeerRoute) return false

        // A direct #@username route identifies the selected public peer even when
        // the friendly source label differs from Telegram's actual display title.
        // An explicit chatTitle remains an additional verification requirement.
        // A resolved numeric route is accepted only with that explicit title because
        // the URL no longer exposes the username.
        return when {
            configuredTitle.isNotBlank() -> titleMatches(snapshot.title, configuredTitle)
            directHandleRoute -> true
            else -> false
        }
    }

    fun chatMatches(
        snapshot: TelegramSnapshot,
        username: String,
        configuredTitle: String
    ): Boolean =
        snapshot.hasComposer &&
            isTargetIdentity(snapshot, username, configuredTitle)

    private fun titleMatches(actual: String, expected: String): Boolean {
        val title = PrivateSourceTextMatcher.normalize(actual)
        val target = PrivateSourceTextMatcher.normalize(expected)
        if (title.isBlank() || target.length < 3) return false
        return title == target || title.contains(target) || target.contains(title)
    }
}

object TelegramTextClassifier {
    fun looksLikePrompt(
        message: String,
        kind: PrivateSourceKind,
        configuredPhrase: String
    ): Boolean {
        if (PrivateSourceTextMatcher.matchesPrompt(message, configuredPhrase)) return true
        val normalized = PrivateSourceTextMatcher.normalize(message)
        if (normalized.isBlank() || normalized.length > 400) return false
        val keywords = when (kind) {
            PrivateSourceKind.NUMBER -> listOf("رقم", "هاتف", "phone", "number", "msisdn")
            PrivateSourceKind.NAME -> listOf("اسم", "الاسم", "name", "full name")
        }
        return keywords.any { normalized.contains(PrivateSourceTextMatcher.normalize(it)) }
    }
}

object TelegramReplyFormatter {
    fun format(messages: List<TelegramMessage>): String =
        messages.joinToString("\n\n") { message ->
            val text = message.text.trim()
            val annotations = buildList {
                if (message.mediaCount > 0 || message.contentType in setOf("image", "video", "audio", "file", "mixed")) {
                    add(
                        when (message.contentType) {
                            "image" -> "مرفق صورة"
                            "video" -> "مرفق فيديو"
                            "audio" -> "مرفق صوت"
                            "file" -> "مرفق ملف"
                            "mixed" -> "نص مع وسائط مرفقة"
                            else -> "مرفق وسائط"
                        }
                    )
                }
                if (message.hasInlineButtons || message.contentType == "buttons") {
                    add("تتضمن الرسالة أزرار تفاعل")
                }
            }
            when {
                text.isNotBlank() && annotations.isNotEmpty() ->
                    text + "\n[" + annotations.joinToString(" · ") + "]"
                text.isNotBlank() -> text
                annotations.isNotEmpty() -> "[" + annotations.joinToString(" · ") + " — محتوى الرسالة غير النصي غير قابل للعرض كنص]"
                else -> "[رسالة واردة غير قابلة للعرض كنص]"
            }
        }.trim()
}
