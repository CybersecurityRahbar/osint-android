package com.cyberphantom.osintworkbench.ui.telegram

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

class SessionWebView(context: Context) : WebView(context)

/**
 * Owns one Telegram WebView and attaches it to a permanent AndroidView host at the
 * WorkbenchApp root. Hiding the app's Telegram surface never removes this WebView
 * from the Android view tree, so the existing signed-in site profile remains in use.
 */
object PrivateSessionHost {
    @Volatile private var retainedView: SessionWebView? = null
    @Volatile private var rootContainer: WeakReference<FrameLayout>? = null
    private val observableView = mutableStateOf<SessionWebView?>(null)
    private val visibleState = mutableStateOf(false)

    val sessionView: State<SessionWebView?> get() = observableView
    val sessionVisible: State<Boolean> get() = visibleState

    @Synchronized
    fun getOrCreate(
        context: Context,
        loadTelegramOnCreate: Boolean = true
    ): SessionWebView {
        retainedView?.let { return it }
        return SessionWebView(context).also { view ->
            TelegramWeb.configure(view, loadInitialPage = loadTelegramOnCreate)
            retainedView = view
            observableView.value = view
        }
    }

    @Synchronized
    fun mountRoot(
        container: FrameLayout,
        loadTelegramOnCreate: Boolean = true
    ) {
        rootContainer = WeakReference(container)
        val view = getOrCreate(container.context, loadTelegramOnCreate)
        if (view.parent !== container) {
            (view.parent as? ViewGroup)?.removeView(view)
            container.addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            view.onResume()
        } else if (view.layoutParams?.width != ViewGroup.LayoutParams.MATCH_PARENT ||
            view.layoutParams?.height != ViewGroup.LayoutParams.MATCH_PARENT
        ) {
            view.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    @Synchronized
    fun ensureMounted(view: SessionWebView = retainedView
        ?: throw IllegalStateException("جلسة Telegram لم تُهيأ بعد.")) {
        val container = rootContainer?.get()
            ?: throw IllegalStateException("مضيف جلسة Telegram غير متصل بواجهة التطبيق.")
        if (view.parent !== container) {
            (view.parent as? ViewGroup)?.removeView(view)
            container.addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            view.onResume()
        }
    }

    fun peek(): SessionWebView? = observableView.value

    @Synchronized
    fun showSession() {
        retainedView?.clearFocus()
        retainedView?.onResume()
        visibleState.value = true
    }

    @Synchronized
    fun hideSession() {
        visibleState.value = false
        retainedView?.clearFocus()
        retainedView?.onResume()
    }

    fun resume() {
        retainedView?.onResume()
    }

    fun pause() {
        retainedView?.let { view ->
            // Persist the existing cookie jar only. No sign-out or site-data clearing occurs.
            CookieManager.getInstance().flush()
            view.onPause()
        }
    }

    @Synchronized
    fun destroy() {
        val view = retainedView ?: return
        visibleState.value = false
        (view.parent as? ViewGroup)?.removeView(view)
        runCatching { view.stopLoading() }
        runCatching { CookieManager.getInstance().flush() }
        runCatching { view.removeAllViews() }
        runCatching { view.destroy() }
        retainedView = null
        rootContainer = null
        observableView.value = null
    }
}

data class TelegramMessage(
    val key: String,
    val text: String,
    val outgoing: Boolean,
    val contentType: String = "text",
    val mediaCount: Int = 0,
    val hasInlineButtons: Boolean = false,
    val timestamp: String = ""
)

data class TelegramSnapshot(
    val url: String,
    val title: String,
    val hasComposer: Boolean,
    val hasSendButton: Boolean,
    val editorText: String,
    val readyState: String,
    val messages: List<TelegramMessage>,
    val peerRoute: String = "",
    val isAuthenticated: Boolean = false,
    val isLoginPage: Boolean = false,
    val hasAppShell: Boolean = false
)

data class TelegramDiagnostics(
    val snapshot: TelegramSnapshot,
    val attachedToWindow: Boolean,
    val incoming: Int,
    val outgoing: Int,
    val mediaMessages: Int,
    val lastIssue: String?
) {
    /** Diagnostics intentionally exclude message text, composer contents, cookies and tokens. */
    fun summary(): String = buildString {
        appendLine("WEBVIEW ATTACHED: $attachedToWindow")
        appendLine(
            "SAVED SESSION: ${
                if (snapshot.isAuthenticated) "AUTHENTICATED"
                else if (snapshot.isLoginPage) "LOGIN SCREEN"
                else "NOT VERIFIED"
            }"
        )
        appendLine("PAGE: ${snapshot.readyState} · APP SHELL: ${snapshot.hasAppShell}")
        appendLine("TARGET ROUTE: ${snapshot.peerRoute.take(40).ifBlank { "غير محدد" }}")
        appendLine("CHAT TITLE DETECTED: ${snapshot.title.isNotBlank()}")
        appendLine("COMPOSER: ${snapshot.hasComposer} · SEND CONTROL: ${snapshot.hasSendButton}")
        appendLine("MESSAGES: ${snapshot.messages.size} (incoming=$incoming / outgoing=$outgoing)")
        appendLine("MEDIA MESSAGES: $mediaMessages")
        append("LAST WEBVIEW ISSUE: ${lastIssue ?: "لا يوجد خطأ مسجل"}")
    }
}

data class TelegramSendAttempt(val method: String)

/** Telegram Web K DOM adapter. It always uses the one existing authenticated WebView. */
@SuppressLint("SetJavaScriptEnabled")
object TelegramWeb {
    private const val TELEGRAM_URL = "https://web.telegram.org/k/"
    @Volatile private var lastWebViewIssue: String? = null

    fun configure(view: WebView, loadInitialPage: Boolean = true) {
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
                lastWebViewIssue = null
                CookieManager.getInstance().flush()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    lastWebViewIssue = "MAIN_FRAME_LOAD_${error.errorCode}"
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: android.webkit.WebResourceResponse
            ) {
                if (request.isForMainFrame) {
                    lastWebViewIssue = "MAIN_FRAME_HTTP_${errorResponse.statusCode}"
                }
            }
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    // Do not retain console text: it may contain user data or page contents.
                    lastWebViewIssue = "JAVASCRIPT_ERROR_LINE_${consoleMessage.lineNumber()}"
                }
                return true
            }
        }
        view.setBackgroundColor(Color.TRANSPARENT)
        if (loadInitialPage && view.url.isNullOrBlank()) view.loadUrl(TELEGRAM_URL)
    }

    /**
     * Verifies the already authenticated site state. This method never logs out,
     * clears storage, starts QR authorization, or asks for credentials.
     */
    suspend fun checkSession(view: WebView): TelegramSnapshot {
        if (!view.isAttachedToWindow) {
            throw IllegalStateException("مضيف جلسة Telegram غير متصل بواجهة التطبيق.")
        }
        val deadline = android.os.SystemClock.elapsedRealtime() + 15_000L
        var latest: TelegramSnapshot? = null
        var readFailure: String? = null
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            val current = try {
                snapshot(view)
            } catch (error: Exception) {
                readFailure = error.message
                delay(450L)
                continue
            }
            latest = current
            if (current.isLoginPage) {
                throw IllegalStateException(
                    "Telegram يعرض شاشة تسجيل الدخول حالياً. احتفظنا ببيانات الجلسة كما هي؛ افتح الجلسة الحالية وتحقق منها يدوياً."
                )
            }
            if (current.isAuthenticated) return current
            delay(450L)
        }
        throw IllegalStateException(
            "لم أستطع إثبات أن جلسة Telegram الحالية مسجّلة الدخول خلال 15 ثانية. لم يتم حذف الجلسة أو بدء تسجيل دخول جديد." +
                (latest?.let { " · حالة الصفحة: ${it.readyState}; واجهة Telegram: ${it.hasAppShell}" } ?: "") +
                (readFailure?.let { " · تعذر قراءة الصفحة: $it" } ?: "")
        )
    }

    suspend fun openTarget(view: WebView, username: String, configuredTitle: String) {
        val handle = username.trim().removePrefix("@")
        require(handle.matches(Regex("[A-Za-z0-9_]{5,32}"))) {
            "معرّف المصدر غير صالح."
        }
        if (!view.isAttachedToWindow) {
            throw IllegalStateException("جلسة Telegram غير متصلة بمضيف التطبيق.")
        }
        val current = snapshot(view)
        if (TelegramPeerMatcher.isTargetIdentity(current, handle, configuredTitle)) return
        withContext(Dispatchers.Main) {
            view.onResume()
            view.loadUrl("$TELEGRAM_URL#@$handle")
        }
    }

    suspend fun startBotIfAvailable(view: WebView): Boolean {
        val script = """
            (function() {
              function visible(node) {
                if (!node) return false;
                var style = window.getComputedStyle(node);
                var rect = node.getBoundingClientRect();
                return style.display !== 'none' && style.visibility !== 'hidden' &&
                  rect.width > 0 && rect.height > 0;
              }
              var candidates = Array.prototype.slice.call(
                document.querySelectorAll('.chat-input-control-button, button, [role="button"]')
              );
              var start = candidates.find(function(button) {
                if (!visible(button) || button.disabled) return false;
                var label = String(button.innerText || button.textContent || '').trim().toLowerCase();
                return /^(start|start bot|ابدأ|بدء|ابدأ المحادثة|بدء المحادثة)$/.test(label);
              });
              if (!start) return 'not_found';
              start.click();
              return 'clicked';
            })()
        """.trimIndent()
        return evaluate(view, script) == "clicked"
    }

    /**
     * Invokes the send control only after the controller has verified the target peer.
     * The return value is an attempted UI action, never proof of delivery. The controller
     * must observe a new outgoing bubble before advancing; it will not retry on ambiguity.
     */
    suspend fun send(view: WebView, text: String): TelegramSendAttempt {
        require(text.isNotBlank()) { "لا يمكن إرسال نص فارغ." }
        val payload = JSONObject.quote(text)
        val script = """
            (function(message) {
              function visible(node) {
                if (!node) return false;
                var style = window.getComputedStyle(node);
                var rect = node.getBoundingClientRect();
                return style.display !== 'none' && style.visibility !== 'hidden' &&
                  rect.width > 0 && rect.height > 0;
              }
              var editor = document.querySelector('.input-message-input[contenteditable="true"]')
                || document.querySelector('#editable-message-text[contenteditable="true"]')
                || document.querySelector('.chat-input [contenteditable="true"]')
                || document.querySelector('[contenteditable="true"][data-placeholder]')
                || document.querySelector('textarea');
              if (!visible(editor)) return JSON.stringify({ok:false,error:'composer_missing'});
              if (editor.disabled || editor.getAttribute('aria-disabled') === 'true') {
                return JSON.stringify({ok:false,error:'composer_disabled'});
              }

              editor.focus();
              if (editor.isContentEditable) {
                try {
                  var selection = window.getSelection();
                  var range = document.createRange();
                  range.selectNodeContents(editor);
                  selection.removeAllRanges();
                  selection.addRange(range);
                } catch (error) {}
                try { document.execCommand('insertText', false, message); } catch (error) {}
              } else {
                var prototype = editor.tagName === 'TEXTAREA'
                  ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
                var descriptor = Object.getOwnPropertyDescriptor(prototype, 'value');
                if (!descriptor || !descriptor.set) {
                  return JSON.stringify({ok:false,error:'unsupported_editor'});
                }
                descriptor.set.call(editor, message);
                editor.dispatchEvent(new Event('input', {bubbles:true}));
                editor.dispatchEvent(new Event('change', {bubbles:true}));
              }

              var currentText = String(editor.value || editor.innerText || editor.textContent || '').trim();
              if (currentText !== String(message).trim()) {
                return JSON.stringify({ok:false,error:'text_not_accepted'});
              }

              var composer = editor.closest('.chat-input') ||
                editor.closest('.Composer') || editor.closest('form');
              if (!composer) return JSON.stringify({ok:false,error:'composer_container_missing'});
              var selectors = [
                '.btn-send',
                'button.main-button.send',
                'button[aria-label*="Send" i]',
                'button[title*="Send" i]',
                'button[aria-label*="إرسال"]',
                'button[title*="إرسال"]',
                'button[class*="send" i]'
              ];
              var send = null;
              for (var i = 0; i < selectors.length && !send; i++) {
                send = composer.querySelector(selectors[i]);
              }
              if (!visible(send)) return JSON.stringify({ok:false,error:'send_control_missing'});
              if (send.disabled || send.getAttribute('aria-disabled') === 'true') {
                return JSON.stringify({ok:false,error:'send_control_disabled'});
              }
              send.click();
              return JSON.stringify({ok:true,method:'composer_send_control'});
            })($payload)
        """.trimIndent()

        val response = runCatching { JSONObject(evaluate(view, script)) }.getOrNull()
            ?: throw IllegalStateException("تعذر قراءة نتيجة إجراء الإرسال في Telegram.")
        if (!response.optBoolean("ok")) {
            val message = when (response.optString("error")) {
                "composer_missing" -> "لم أجد محرر الرسائل المرئي في المحادثة."
                "composer_disabled" -> "محرر الرسائل غير متاح حالياً."
                "unsupported_editor" -> "نوع محرر Telegram الحالي غير مدعوم."
                "text_not_accepted" -> "لم يقبل محرر Telegram النص كاملاً؛ لم يتم الضغط على إرسال."
                "composer_container_missing" -> "لم أستطع تحديد منطقة كتابة المحادثة؛ لم يتم الضغط على أي زر."
                "send_control_missing" -> "لم أجد زر الإرسال داخل منطقة كتابة المحادثة."
                "send_control_disabled" -> "زر الإرسال غير مفعّل حالياً."
                else -> "تعذر تجهيز الرسالة في Telegram (${response.optString("error")})."
            }
            throw IllegalStateException(message)
        }
        return TelegramSendAttempt(response.optString("method", "composer_send_control"))
    }

    suspend fun snapshot(view: WebView): TelegramSnapshot {
        val script = """
            (function() {
              function first(selectors, root) {
                root = root || document;
                for (var i = 0; i < selectors.length; i++) {
                  var node = root.querySelector(selectors[i]);
                  if (node) return node;
                }
                return null;
              }
              function visible(node) {
                if (!node) return false;
                var style = window.getComputedStyle(node);
                var rect = node.getBoundingClientRect();
                return style.display !== 'none' && style.visibility !== 'hidden' &&
                  rect.width > 0 && rect.height > 0;
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
              if (!visible(titleNode)) titleNode = null;

              var composer = first([
                '.input-message-input[contenteditable="true"]',
                '#editable-message-text[contenteditable="true"]',
                '.chat-input [contenteditable="true"]',
                '[contenteditable="true"][data-placeholder]',
                'div[contenteditable="true"]',
                'textarea'
              ]);
              if (!visible(composer)) composer = null;

              var sendButton = first([
                '.btn-send',
                'button.main-button.send',
                '.chat-input .btn-send',
                'button[aria-label*="Send" i]',
                'button[title*="Send" i]',
                'button[aria-label*="إرسال"]',
                'button[class*="send" i]'
              ]);
              if (!visible(sendButton)) sendButton = null;

              var authRoot = first(['#auth-pages', '.auth-pages', '.auth-form', '[class*="authForm" i]']);
              var phoneInput = first(['#auth-pages input[type="tel"]', '.auth-form input[type="tel"]']);
              var bodyText = String(document.body ? document.body.innerText : '').slice(0, 3500);
              var loginLanguage = /log in to telegram|enter your phone number|sign in to telegram|تسجيل الدخول إلى تيليجرام|أدخل رقم هاتفك/i.test(bodyText);
              var isLoginPage = visible(authRoot) || (visible(phoneInput) && loginLanguage);

              var appShell = first([
                '#column-left',
                '.sidebar-left',
                '.Sidebar',
                '.left-column',
                '[class*="sidebar-left" i]',
                '[class*="LeftColumn" i]',
                '[class*="chatList" i]',
                '.chatlist'
              ]);
              var hasAppShell = visible(appShell) && !isLoginPage;
              var isAuthenticated = !isLoginPage &&
                (hasAppShell || (!!titleNode && !!composer));

              var candidates = Array.prototype.slice.call(document.querySelectorAll(
                '.bubble:not(.service):not(.is-date), [data-mid], [data-message-id], [id^="message-"]'
              ));
              var seen = {};
              var messages = candidates.slice(-240).map(function(bubble) {
                var textNode = bubble.querySelector('.message') ||
                  bubble.querySelector('.text-content') ||
                  bubble.querySelector('[class*="messageText" i]') ||
                  bubble.querySelector('[class*="caption" i]') || bubble;
                var text = String(textNode.innerText || textNode.textContent || '').trim();
                var mediaNodes = bubble.querySelectorAll(
                  '[class*="media" i], [class*="photo" i], [class*="video" i], ' +
                  '[class*="document" i], video, audio'
                );
                var imageCount = bubble.querySelectorAll('img:not([class*="avatar" i])').length;
                var videoCount = bubble.querySelectorAll('video').length;
                var audioCount = bubble.querySelectorAll('audio').length;
                var mediaCount = Math.max(mediaNodes.length, imageCount + videoCount + audioCount);
                var inlineButtons = !!bubble.querySelector(
                  '.reply-markup, [class*="replyMarkup" i], [class*="inlineKeyboard" i], ' +
                  '[class*="keyboard" i] button, button[data-row], [role="button"]'
                );
                if (!text && mediaCount === 0 && !inlineButtons) return null;

                var classes = String(bubble.className || '');
                var outgoing = bubble.classList.contains('is-out') ||
                  bubble.classList.contains('out') ||
                  /(^|\\s)(is-out|outgoing|message-out)(\\s|$)/i.test(classes);
                var timeNode = bubble.querySelector('time') || bubble.querySelector('[title]');
                var timestamp = timeNode
                  ? (timeNode.getAttribute('datetime') || timeNode.getAttribute('title') || '')
                  : '';
                var stableId = bubble.getAttribute('data-mid') ||
                  bubble.getAttribute('data-message-id') ||
                  bubble.getAttribute('data-id') || bubble.id || '';
                var type = 'text';
                if (videoCount > 0) type = 'video';
                else if (audioCount > 0) type = 'audio';
                else if (imageCount > 0 || /photo/i.test(classes)) type = 'image';
                else if (mediaCount > 0) type = 'file';
                else if (!text && inlineButtons) type = 'buttons';
                if (text && mediaCount > 0) type = 'mixed';

                var seed = (outgoing ? 'out:' : 'in:') + timestamp + ':' + type + ':' + text;
                var hash = 0;
                for (var i = 0; i < seed.length; i++) {
                  hash = ((hash << 5) - hash + seed.charCodeAt(i)) | 0;
                }
                var key = stableId || ('msg:' + hash.toString(36));
                if (seen[key]) return null;
                seen[key] = true;
                return {
                  key:String(key),
                  text:text,
                  outgoing:!!outgoing,
                  contentType:type,
                  mediaCount:mediaCount,
                  hasInlineButtons:inlineButtons,
                  timestamp:String(timestamp)
                };
              }).filter(Boolean);

              var route = String(location.hash || '').replace(/^#/, '')
                .split('?')[0].replace(/^\\/+|\\/+$/g, '');
              return JSON.stringify({
                url:String(location.href || ''),
                title:String(titleNode ? (titleNode.innerText || titleNode.textContent || '') : '').trim(),
                peerRoute:route,
                hasComposer:!!composer,
                hasSendButton:!!sendButton,
                editorText:String(composer ? (composer.value || composer.innerText || composer.textContent || '') : '').trim(),
                readyState:String(document.readyState || ''),
                isAuthenticated:!!isAuthenticated,
                isLoginPage:!!isLoginPage,
                hasAppShell:!!hasAppShell,
                messages:messages
              });
            })()
        """.trimIndent()

        val raw = evaluate(view, script)
        val root = runCatching { JSONObject(raw) }.getOrElse {
            throw IllegalStateException("تعذر تحليل DOM الحالي في Telegram Web.")
        }
        val array = root.optJSONArray("messages") ?: JSONArray()
        val messages = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    TelegramMessage(
                        key = item.optString("key"),
                        text = item.optString("text"),
                        outgoing = item.optBoolean("outgoing"),
                        contentType = item.optString("contentType", "text"),
                        mediaCount = item.optInt("mediaCount"),
                        hasInlineButtons = item.optBoolean("hasInlineButtons"),
                        timestamp = item.optString("timestamp")
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
            messages = messages,
            peerRoute = root.optString("peerRoute"),
            isAuthenticated = root.optBoolean("isAuthenticated"),
            isLoginPage = root.optBoolean("isLoginPage"),
            hasAppShell = root.optBoolean("hasAppShell")
        )
    }

    suspend fun diagnostics(view: WebView): TelegramDiagnostics {
        val current = snapshot(view)
        return TelegramDiagnostics(
            snapshot = current,
            attachedToWindow = view.isAttachedToWindow,
            incoming = current.messages.count { !it.outgoing },
            outgoing = current.messages.count { it.outgoing },
            mediaMessages = current.messages.count { it.contentType != "text" || it.mediaCount > 0 },
            lastIssue = lastWebViewIssue
        )
    }

    private suspend fun evaluate(view: WebView, script: String): String =
        withTimeout(12_000L) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    if (!view.isAttachedToWindow) {
                        continuation.resume("null")
                        return@suspendCancellableCoroutine
                    }
                    runCatching {
                        view.evaluateJavascript(script) { raw ->
                            if (continuation.isActive) {
                                val decoded = runCatching {
                                    JSONTokener(raw ?: "null").nextValue() as? String
                                }.getOrNull()
                                continuation.resume(decoded ?: raw.orEmpty())
                            }
                        }
                    }.onFailure {
                        if (continuation.isActive) continuation.resume("null")
                    }
                }
            }
        }
}
