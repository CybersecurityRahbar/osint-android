package com.cyberphantom.osintworkbench.ui.telegram

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
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

class SessionWebView(context: Context) : WebView(context)

object PrivateSessionHost {
    @Volatile private var retainedView: SessionWebView? = null

    @Synchronized
    fun getOrCreate(context: Context): SessionWebView {
        retainedView?.let { return it }
        return SessionWebView(context).also {
            TelegramWeb.configure(it)
            retainedView = it
        }
    }

    fun peek(): SessionWebView? = retainedView

    fun attach(view: SessionWebView): SessionWebView {
        (view.parent as? ViewGroup)?.removeView(view)
        view.onResume()
        return view
    }

    fun resume() {
        retainedView?.onResume()
    }

    fun pause() {
        retainedView?.let {
            CookieManager.getInstance().flush()
            it.onPause()
        }
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
        appendLine("SESSION: ${if (snapshot.url.isBlank()) "غير متصل" else "متصل"}")
        appendLine("WINDOW: ${if (snapshot.title.isBlank()) "غير محدد" else "محدد"}")
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

    suspend fun openChat(
        view: WebView,
        username: String,
        configuredName: String = username,
        onStatus: (String) -> Unit
    ): TelegramSnapshot {
        val handle = username.trim().removePrefix("@")
        require(handle.matches(Regex("[A-Za-z0-9_]{5,32}"))) { "معرّف المصدر غير صالح: $username" }
        onStatus("فتح نافذة المصدر المحدد…")
        withContext(Dispatchers.Main) {
            view.onResume()
            // The Web K public peer route includes @ before the username.
            view.loadUrl("$TELEGRAM_URL#@$handle")
        }
        val deadline = SystemClock.elapsedRealtime() + 55_000L
        var last: TelegramSnapshot? = null
        var lastReadError: String? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            val current = try {
                snapshot(view)
            } catch (error: Exception) {
                lastReadError = error.message
                delay(800L)
                continue
            }
            last = current
            val route = current.url.substringAfter('#').trimStart('@').substringBefore('?').trimEnd('/')
            val routeMatches = route.equals(handle, ignoreCase = true)
            val titleMatches = chatTitleMatches(current.title, handle, configuredName)
            if (routeMatches && titleMatches && current.hasComposer) {
                delay(1_000L)
                val hydrated = snapshot(view)
                if (hydrated.hasComposer && hydrated.title.isNotBlank()) return hydrated
            }
            delay(800L)
        }
        val fallback = last
        val details = fallback?.let {
            " (العنوان: ${it.title.ifBlank { "غير ظاهر" }} · المحرر: ${it.hasComposer} · زر الإرسال: ${it.hasSendButton} · الرسائل: ${it.messages.size} · المسار: ${it.url.substringAfter('#').take(50)})"
        }.orEmpty()
        throw IllegalStateException(
            "لم تفتح نافذة المحادثة المطلوبة خلال 55 ثانية. تأكد من إكمال تسجيل الدخول، ثم تحقق من اسم المصدر ومعرّفه." +
                details + (lastReadError?.let { " · تعذر قراءة الصفحة: $it" } ?: "")
        )
    }

    private fun chatTitleMatches(title: String, handle: String, configuredName: String): Boolean {
        val normalizedTitle = PrivateSourceTextMatcher.normalize(title)
        val normalizedHandle = PrivateSourceTextMatcher.normalize(handle.removePrefix("@"))
        val normalizedName = PrivateSourceTextMatcher.normalize(configuredName)
        return normalizedTitle.isNotBlank() && (
            (normalizedHandle.isNotBlank() && normalizedTitle.contains(normalizedHandle)) ||
                (normalizedName.isNotBlank() && normalizedTitle.contains(normalizedName))
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
                  || document.querySelector('[contenteditable="true"][data-placeholder]')
                  || document.querySelector('div[contenteditable="true"]')
                  || document.querySelector('textarea');
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
                || document.querySelector('.chat-input .btn-send')
                || document.querySelector('button[aria-label*="Send" i]')
                || document.querySelector('button[title*="Send" i]')
                || document.querySelector('button[aria-label*="إرسال"]')
                || document.querySelector('button[class*="send" i]');
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
            ?: throw IllegalStateException("تعذر تنفيذ الإرسال داخل جلسة المصدر.")
        if (response.optBoolean("ok") != true) {
            throw IllegalStateException(
                when (response.optString("error")) {
                    "composer_not_found" -> "لم أجد حقل الإدخال. افتح الجلسة وأكمل تسجيل الدخول ثم أعد المحاولة."
                    "composer_value_mismatch" -> "رفض حقل الإدخال النص (${response.optString("value")}). أعد المحاولة."
                    else -> "تعذر الإرسال عبر واجهة الجلسة (${response.optString("error")})."
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
              function first(selectors) {
                for (var i = 0; i < selectors.length; i++) {
                  var node = document.querySelector(selectors[i]);
                  if (node) return node;
                }
                return null;
              }
              var titleNode = first([
                '.chat-info .peer-title',
                '.MiddleHeader .peer-title',
                '.MiddleHeader .fullName',
                '.MiddleHeader .info .title',
                '[class*="middleHeader" i] [class*="title" i]',
                '[class*="chatInfo" i] [class*="title" i]',
                'header [class*="title" i]'
              ]);
              var composer = first([
                '.input-message-input[contenteditable="true"]',
                '#editable-message-text[contenteditable="true"]',
                '.chat-input [contenteditable="true"]',
                '[contenteditable="true"][data-placeholder]',
                'div[contenteditable="true"]',
                'textarea'
              ]);
              var send = first([
                '.btn-send',
                'button.main-button.send',
                '.chat-input .btn-send',
                'button[aria-label*="Send" i]',
                'button[title*="Send" i]',
                'button[aria-label*="إرسال"]',
                'button[class*="send" i]'
              ]);
              var candidates = Array.prototype.slice.call(document.querySelectorAll(
                '.bubble:not(.service):not(.is-date), [data-mid], [data-message-id], [id^="message-"]'
              ));
              var seen = {};
              var messages = candidates.slice(-180).map(function(bubble) {
                var node = bubble.querySelector('.message') || bubble.querySelector('.text-content')
                  || bubble.querySelector('[class*="messageText" i]') || bubble;
                var text = String(node.innerText || node.textContent || '').trim();
                if (!text) return null;
                var classes = String(bubble.className || '');
                var outgoing = bubble.classList.contains('is-out') || bubble.classList.contains('out')
                  || /(^|\\s)(is-out|outgoing|message-out)(\\s|$)/i.test(classes);
                var timeNode = bubble.querySelector('time') || bubble.querySelector('[title]');
                var timeKey = timeNode ? (timeNode.getAttribute('datetime') || timeNode.getAttribute('title') || '') : '';
                var stableId = bubble.getAttribute('data-mid') || bubble.getAttribute('data-message-id')
                  || bubble.getAttribute('data-id') || bubble.id || '';
                var seed = (outgoing ? 'out:' : 'in:') + timeKey + ':' + text;
                var hash = 0;
                for (var i = 0; i < seed.length; i++) hash = ((hash << 5) - hash + seed.charCodeAt(i)) | 0;
                var key = stableId || ('msg:' + hash.toString(36));
                if (seen[key]) return null;
                seen[key] = true;
                return {key:String(key), text:text, outgoing:!!outgoing};
              }).filter(Boolean);
              return JSON.stringify({
                url:String(location.href || ''),
                title:String(titleNode ? (titleNode.innerText || titleNode.textContent || '') : '').trim(),
                hasComposer:!!composer,
                hasSendButton:!!send,
                editorText:String(composer ? (composer.value || composer.innerText || composer.textContent || '') : '').trim(),
                readyState:String(document.readyState || ''),
                messages:messages
              });
            })()
        """.trimIndent()
        val raw = evaluate(view, script)
        val root = runCatching { JSONObject(raw) }.getOrElse {
            throw IllegalStateException("تعذر قراءة حالة جلسة المصدر: ${raw.take(120)}")
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
        val route = snapshot.url.substringAfter('#').trimStart('@').substringBefore('?').lowercase()
        val routeMatches = route == handle || route.startsWith("$handle/")
        val title = PrivateSourceTextMatcher.normalize(snapshot.title)
        val expected = PrivateSourceTextMatcher.normalize(configuredName)
        val titleMatches = expected.isNotBlank() && title.isNotBlank() &&
            (title.contains(expected) || expected.contains(title))
        val handleNormalized = PrivateSourceTextMatcher.normalize(handle)
        val handleMatchesTitle = handleNormalized.isNotBlank() && title.contains(handleNormalized)
        return snapshot.hasComposer && (routeMatches || titleMatches || handleMatchesTitle)
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
