package com.cyberphantom.osintworkbench.ui.telegram

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cyberphantom.osintworkbench.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Authenticated Android WebView smoke test.
 *
 * It opens the explicitly configured Telegram bot by public username, verifies the
 * selected peer, sends only /start (or taps Telegram's native Start button, which
 * sends the same command), and confirms that a new bot response arrives.
 * No Bot API token or personal lookup value is used by this test.
 */
@RunWith(AndroidJUnit4::class)
class TelegramCiLiveAndroidTest {
    @Test
    fun retainedTelegramWebViewCanStartConfiguredBotAndReceiveReply() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val botUsername = arguments.getString("telegramCiBotUsername")
            .orEmpty()
            .trim()
            .removePrefix("@")

        assertTrue(
            "Set telegramCiBotUsername to a Telegram bot username.",
            botUsername.matches(Regex("[A-Za-z0-9_]{5,32}"))
        )

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val view = withTimeout(30_000L) {
                while (true) {
                    val candidate = withContext(Dispatchers.Main) { PrivateSessionHost.peek() }
                    if (candidate != null && candidate.isAttachedToWindow) {
                        withContext(Dispatchers.Main) {
                            PrivateSessionHost.ensureMounted(candidate)
                            PrivateSessionHost.showSession()
                        }
                        return@withTimeout candidate
                    }
                    delay(100L)
                }
                error("Telegram WebView host was not created.")
            }

            val session = TelegramWeb.checkSession(view)
            assertTrue(
                "The saved Telegram WebView session is not authenticated.",
                session.isAuthenticated && !session.isLoginPage
            )

            TelegramWeb.openTarget(view, botUsername, "")

            val target = withTimeout(70_000L) {
                while (true) {
                    val current = TelegramWeb.snapshot(view)
                    assertTrue(
                        "Telegram left the authenticated app while opening the configured bot.",
                        current.isAuthenticated && !current.isLoginPage
                    )
                    if (TelegramPeerMatcher.isTargetIdentity(current, botUsername, "")) {
                        return@withTimeout current
                    }
                    delay(500L)
                }
                error("Bot target was not resolved.")
            }

            val baselineKeys = target.messages.map { it.key }.toSet()

            if (target.hasComposer) {
                TelegramWeb.send(view, "/start")
            } else {
                val startClicked = TelegramWeb.startBotIfAvailable(view)
                assertTrue(
                    "The bot chat had no message composer and Telegram's Start button was not found.",
                    startClicked
                )
            }

            val outgoingStart = withTimeout(35_000L) {
                while (true) {
                    val current = TelegramWeb.snapshot(view)
                    assertTrue(
                        "The selected Telegram chat changed while sending /start.",
                        TelegramPeerMatcher.isTargetIdentity(current, botUsername, "")
                    )
                    val sent = current.messages.firstOrNull {
                        it.outgoing &&
                            it.key !in baselineKeys &&
                            it.text.trim().startsWith("/start", ignoreCase = true)
                    }
                    if (sent != null) return@withTimeout sent
                    delay(500L)
                }
                error("The outgoing /start message was not confirmed.")
            }
            assertTrue("The outgoing /start message was empty.", outgoingStart.text.isNotBlank())

            val response = withTimeout(60_000L) {
                while (true) {
                    val current = TelegramWeb.snapshot(view)
                    assertTrue(
                        "The selected Telegram chat changed while waiting for the bot response.",
                        TelegramPeerMatcher.isTargetIdentity(current, botUsername, "")
                    )
                    val incoming = current.messages.filter {
                        !it.outgoing && it.key !in baselineKeys
                    }
                    if (incoming.isNotEmpty()) return@withTimeout incoming
                    delay(500L)
                }
                error("No new incoming response arrived after /start.")
            }

            // Do not print or persist bot response text in CI output.
            assertTrue(
                "Telegram received no usable new response messages after /start.",
                response.any { it.text.isNotBlank() || it.contentType != "text" || it.mediaCount > 0 }
            )
        } finally {
            scenario.close()
        }
    }
}
