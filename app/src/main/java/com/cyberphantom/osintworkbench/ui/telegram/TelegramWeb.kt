package com.cyberphantom.osintworkbench.ui.telegram

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.cyberphantom.osintworkbench.data.PrivateSourceKind
import com.cyberphantom.osintworkbench.data.PrivateSourceTextMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

class SessionWebView(context: Context) : WebView(context) {
    private var previousY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                previousY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaY = event.y - previousY
                val atTop = scrollY <= 0
                val atBottom = scrollY + height >= computeVerticalScrollRange() - 2
                val handToParent = (atTop && deltaY > 0) || (atBottom && deltaY < 0)
                parent?.requestDisallowInterceptTouchEvent(!handToParent)
                previousY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.onTouchEvent(event)
    }
}

data class TelegramMessage(
    val key: String,
    val text: String,
    val outgoing: Boolean
)

data class TelegramSnapshot(
    val url: String,
    val title: String,
    val hasComposer: Boolean,
    val hasSendButton: Boolean,
    val editorText: String,
    val readyState: String,
    val messages: List<TelegramMessage>
)

data class TelegramDiagnostics(
    val snapshot: TelegramSnapshot,
    val incoming: Int,
    val outgoing: Int,
    val lastIncoming: String,
    val lastOutgoing: String
) {
    fun summary(): String = buildString {
        appendLine("URL: ${snapshot.url}")
        appendLine("TITLE: ${snapshot.title.ifBlank { "(فارغ)" }}")
        appendLine("READY: ${snapshot.readyState} · COMPOSER: ${snapshot.hasComposer} · SEND: ${snapshot.hasSendButton}")
        appendLine("EDITOR: ${snapshot.editorText.ifBlank { "(فارغ)" }}")
        appendLine("MESSAGES: ${snapshot.messages.size} (in=$incoming / out=$outgoing)")
        appendLine("LAST IN: ${lastIncoming.ifBlank { "—" }}")
        append("LAST OUT: ${lastOutgoing.ifBlank { "—" }}")
    }
}

data class TelegramSendOutcome(val via: String, val composerCleared: Boolean)

/**
 * Drives Telegram Web K through its own DOM instead of the Bot API: the user is already signed in,
 * so the app reuses that session, sends the bot command, waits for its value prompt and captures the
 * reply. Every step is observable and every failure carries a DOM diagnostic rather than a guess.
 */
@SuppressLint("SetJavaScriptEnabled")
object TelegramWeb {
    private const val TELEGRAM_URL = "https://web.telegram.org/k/"

    fun configure(view: WebView) {
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(view, true)
        }
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                CookieManager.getInstance().flush()
            }
        }
        view.webChromeClient = WebChromeClient()
        view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        if (view.url.isNullOrBlank()) view.loadUrl(TELEGRAM_URL)
    }

    suspend fun openChat(view: WebView, username: String, onStatus: (String) -> Unit): TelegramSnapshot {
        val handle = username.trim().removePrefix("@")
        require(handle.matches(Regex("[A-Za-z0-9_]{5,32}"))) { "اسم مستخدم البوت غير صالح: $username" }
        onStatus("فتح المحادثة مع @$handle…")
        withContext(Dispatchers.Main) {
            view.loadUrl("$TELEGRAM_URL#$handle")
        }
        val deadline = SystemClock.elapsedRealtime() + 40_000L
        var last: TelegramSnapshot? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            val snapshot = snapshot(view)
            last = snapshot
            val routeMatches = snapshot.url.lowercase().contains(handle.lowercase())
            if (snapshot.hasComposer && snapshot.hasSendButton && snapshot.title.isNotBlank()) {
                // Give the message list a moment to hydrate before capturing a baseline.
                delay(900L)
                return snapshot(view).copy(url = if (routeMatches || snapshot.url.isNotBlank()) snapshot.url else "")
            }
            delay(700L)
        }
        val fallback = last
        if (fallback != null && fallback.hasComposer && fallback.hasSendButton) return fallback
        throw IllegalStateException(
            "تعذر تجهيز محادثة @$handle خلال 40 ثانية. " +
                "تأكد أن جلسة المصدر مفتوحة وأن معرّف المصدر صحيح. " +
                (fallback?.let { " (المحرر: ${it.hasComposer} · الإرسال: ${it.hasSendButton} · الصفحة: ${it.url})" } ?: "")
        )
    }

    suspend fun send(view: WebView, text: String): TelegramSendOutcome {
        require(text.isNotBlank()) { "لا يمكن إرسال نص فارغ." }
        val payload = JSONObject.quote(text)
        val script = """
            (function(message) {
              function findEditor() {
                return document.querySelector('.input-message-input[contenteditable="true"]')
                  || document.querySelector('#editable-message-text[contenteditable="true"]')
                  || document.querySelector('.chat-input [contenteditable="true"]')
                  || document.querySelector('[contenteditable="true"]');
              }
              var editor = findEditor();
              if (!editor) return JSON.stringify({ok:false,error:'composer_not_found'});
              editor.focus();
              try {
                var selection = window.getSelection();
                var range = document.createRange();
                range.selectNodeContents(editor);
                selection.removeAllRanges();
                selection.addRange(range);
              } catch (e) {}
              try { document.execCommand('insertText', false, message); } catch (e) {}
              var value = (editor.innerText || editor.textContent || '');
              if (value.trim() !== message.trim()) {
                try {
                  editor.dispatchEvent(new InputEvent('beforeinput', {bubbles:true, cancelable:true, inputType:'insertText', data:message}));
                } catch (e) {}
                editor.textContent = message;
                try {
                  editor.dispatchEvent(new InputEvent('input', {bubbles:true, inputType:'insertText', data:message}));
                } catch (e) {}
                value = (editor.innerText || editor.textContent || '');
              }
              if (value.trim() !== message.trim()) {
                return JSON.stringify({ok:false,error:'composer_value_mismatch',value:String(value).slice(0,120)});
              }
              var button = document.querySelector('.btn-send')
                || document.querySelector('button.main-button.send')
                || document.querySelector('.chat-input .btn-send');
              if (button && !button.disabled && button.getAttribute('aria-disabled') !== 'true') {
                button.click();
                return JSON.stringify({ok:true,via:'button'});
              }
              ['keydown','keypress','keyup'].forEach(function(type) {
                editor.dispatchEvent(new KeyboardEvent(type, {
                  key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true, cancelable:true
                }));
              });
              return JSON.stringify({ok:true,via:'enter'});
            })($payload)
        """.trimIndent()
        val response = runCatching { JSONObject(evaluate(view, script)) }.getOrNull()
            ?: throw IllegalStateException("تعذر تنفيذ الإرسال داخل Telegram Web.")
        if (response.optBoolean("ok") != true) {
            throw IllegalStateException(
                when (response.optString("error")) {
                    "composer_not_found" -> "لم أجد حقل كتابة الرسائل. افتح Telegram وأكمل تسجيل الدخول ثم أعد المحاولة."
                    "composer_value_mismatch" -> "رفض محرر Telegram النص (${response.optString("value")}). أعد المحاولة أو أرسل النص يدوياً."
                    else -> "تعذر الإرسال عبر واجهة Telegram (${response.optString("error")})."
                }
            )
        }
        val via = response.optString("via", "button")
        val cleared = waitForComposerCleared(view, text)
        return TelegramSendOutcome(via, cleared)
    }

    private suspend fun waitForComposerCleared(view: WebView, sent: String): Boolean {
        val deadline = SystemClock.elapsedRealtime() + 6_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            val state = snapshot(view)
            if (state.editorText.trim().isEmpty() || state.editorText.trim() != sent.trim()) return true
            delay(400L)
        }
        return false
    }

    suspend fun snapshot(view: WebView): TelegramSnapshot {
        val script = """
            (function() {
              var titleNode = document.querySelector('.chat-info .peer-title')
                || document.querySelector('.MiddleHeader .peer-title')
                || document.querySelector('.MiddleHeader .fullName')
                || document.querySelector('.MiddleHeader .info .title');
              var composer = document.querySelector('.input-message-input[contenteditable="true"]')
                || document.querySelector('#editable-message-text[contenteditable="true"]')
                || document.querySelector('.chat-input [contenteditable="true"]');
              var send = document.querySelector('.btn-send')
                || document.querySelector('button.main-button.send')
                || document.querySelector('.chat-input .btn-send');
              var bubbles = Array.prototype.slice.call(document.querySelectorAll('.bubble:not(.service):not(.is-date)'));
              var messages = bubbles.slice(-140).map(function(bubble, index) {
                var node = bubble.querySelector('.message') || bubble.querySelector('.text-content') || bubble;
                var text = String(node.innerText || node.textContent || '').trim();
                var outgoing = bubble.classList.contains('is-out') || bubble.classList.contains('out');
                var timeNode = bubble.querySelector('time') || bubble.querySelector('[title]');
                var timeKey = timeNode ? (timeNode.getAttribute('datetime') || timeNode.getAttribute('title') || '') : '';
                var stableId = bubble.getAttribute('data-mid') || bubble.getAttribute('data-message-id')
                  || bubble.getAttribute('data-id') || bubble.id || '';
                var seed = (outgoing ? 'out:' : 'in:') + timeKey + ':' + text;
                var hash = 0;
                for (var i = 0; i < seed.length; i++) hash = ((hash << 5) - hash + seed.charCodeAt(i)) | 0;
                var key = stableId || ('msg:' + hash.toString(36));
                return {key:String(key), text:text, outgoing:!!outgoing};
              }).filter(function(item) { return item.text.length > 0; });
              return JSON.stringify({
                url:String(location.href || ''),
                title:String(titleNode ? (titleNode.innerText || titleNode.textContent || '') : '').trim(),
                hasComposer:!!composer,
                hasSendButton:!!send,
                editorText:String(composer ? (composer.innerText || composer.textContent || '') : '').trim(),
                readyState:String(document.readyState || ''),
                messages:messages
              });
            })()
        """.trimIndent()
        val raw = evaluate(view, script)
        val root = runCatching { JSONObject(raw) }.getOrElse {
            throw IllegalStateException("تعذر قراءة حالة Telegram Web: ${raw.take(120)}")
        }
        val array = root.optJSONArray("messages") ?: JSONArray()
        val messages = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    TelegramMessage(
                        key = item.optString("key"),
                        text = item.optString("text"),
                        outgoing = item.optBoolean("outgoing")
                    )
                )
            }
        }
        return TelegramSnapshot(
            url = root.optString("url"),
            title = root.optString("title"),
            hasComposer = root.optBoolean("hasComposer"),
            hasSendButton = root.optBoolean("hasSendButton"),
            editorText = root.optString("editorText"),
            readyState = root.optString("readyState"),
            messages = messages
        )
    }

    suspend fun diagnostics(view: WebView): TelegramDiagnostics {
        val snapshot = snapshot(view)
        return TelegramDiagnostics(
            snapshot = snapshot,
            incoming = snapshot.messages.count { !it.outgoing },
            outgoing = snapshot.messages.count { it.outgoing },
            lastIncoming = snapshot.messages.lastOrNull { !it.outgoing }?.text.orEmpty(),
            lastOutgoing = snapshot.messages.lastOrNull { it.outgoing }?.text.orEmpty()
        )
    }

    suspend fun waitFor(
        view: WebView,
        timeoutMillis: Long,
        predicate: (TelegramSnapshot) -> Boolean
    ): TelegramSnapshot? {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            val current = try {
                snapshot(view)
            } catch (error: Exception) {
                delay(600L)
                null
            }
            if (current != null && predicate(current)) return current
            delay(650L)
        }
        return null
    }

    /**
     * Prompt matching is intentionally tolerant: bots word their request differently across
     * languages and updates. The configured phrase is tried first, then semantic keywords.
     */
    fun looksLikePrompt(message: String, kind: PrivateSourceKind, configuredPhrase: String): Boolean {
        if (PrivateSourceTextMatcher.matchesPrompt(message, configuredPhrase)) return true
        val normalized = PrivateSourceTextMatcher.normalize(message)
        if (normalized.length > 400) return false
        val keywords = when (kind) {
            PrivateSourceKind.NUMBER -> listOf("رقم", "هاتف", "phone", "number", "msisdn")
            PrivateSourceKind.NAME -> listOf("اسم", "name", "الاسم", "full name")
        }
        return keywords.any { normalized.contains(PrivateSourceTextMatcher.normalize(it)) }
    }

    fun chatMatches(snapshot: TelegramSnapshot, username: String, configuredName: String): Boolean {
        val handle = username.trim().removePrefix("@").lowercase()
        if (handle.isNotBlank() && snapshot.url.lowercase().contains(handle)) return true
        val title = PrivateSourceTextMatcher.normalize(snapshot.title)
        val expected = PrivateSourceTextMatcher.normalize(configuredName)
        if (expected.isNotBlank() && title.isNotBlank() && (title.contains(expected) || expected.contains(title))) return true
        val handleNormalized = PrivateSourceTextMatcher.normalize(handle)
        return handleNormalized.isNotBlank() && title.contains(handleNormalized)
    }

    private suspend fun evaluate(view: WebView, script: String): String =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                view.evaluateJavascript(script) { raw ->
                    if (continuation.isActive) {
                        val decoded = runCatching {
                            JSONTokener(raw ?: "null").nextValue() as? String
                        }.getOrNull()
                        continuation.resume(decoded ?: raw.orEmpty())
                    }
                }
            }
        }
}
