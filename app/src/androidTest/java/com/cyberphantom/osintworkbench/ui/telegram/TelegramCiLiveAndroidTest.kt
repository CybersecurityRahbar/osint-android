package com.cyberphantom.osintworkbench.ui.telegram

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cyberphantom.osintworkbench.MainActivity
import com.cyberphantom.osintworkbench.data.PrivateSourceBot
import com.cyberphantom.osintworkbench.data.PrivateSourceFlow
import com.cyberphantom.osintworkbench.data.PrivateSourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Android WebView integration test. It reuses the authenticated WebView profile
 * restored on the CI emulator and sends only a synthetic marker to a user-owned echo bot.
 */
@RunWith(AndroidJUnit4::class)
class TelegramCiLiveAndroidTest {
    @Test
    fun retainedTelegramWebViewCompletesDirectMessageEchoRoundTrip() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val botUsername = arguments.getString("telegramCiEchoBotUsername").orEmpty()
            .trim().removePrefix("@")
        val testValue = arguments.getString("telegramCiTestValue").orEmpty().trim()
        val expectedPrefix = arguments.getString("telegramCiExpectedReplyPrefix")
            .orEmpty().ifBlank { "OSINT_CI_ECHO_OK:" }

        assumeTrue("CI echo bot username was not supplied; live test is opt-in.", botUsername.isNotBlank())
        assumeTrue("CI test value was not supplied.", testValue.startsWith("OSINT_CI_PING_"))
        assumeTrue("CI echo bot username is invalid.", botUsername.matches(Regex("[A-Za-z0-9_]{5,32}")))

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            withTimeout(30_000L) {
                while (true) {
                    val candidate = withContext(Dispatchers.Main) { PrivateSessionHost.peek() }
                    if (candidate != null && candidate.isAttachedToWindow) {
                        withContext(Dispatchers.Main) { PrivateSessionHost.ensureMounted(candidate) }
                        break
                    }
                    delay(100L)
                }
            }

            val driver = AndroidTestTelegramDriver()
            val bot = PrivateSourceBot(
                id = "telegram-ci-echo",
                name = "CI Echo Bot",
                username = botUsername,
                numberCommand = "",
                nameCommand = "",
                numberPrompt = "",
                namePrompt = "",
                numberFlow = PrivateSourceFlow.DIRECT_MESSAGE,
                nameFlow = PrivateSourceFlow.DIRECT_MESSAGE,
                chatTitle = ""
            )
            val result = TelegramSessionController(
                driver,
                TelegramStageTimeouts(
                    checkSessionMillis = 25_000L,
                    openTargetMillis = 12_000L,
                    verifyPeerMillis = 70_000L,
                    verifyComposerMillis = 45_000L,
                    sendMillis = 25_000L,
                    valueEchoMillis = 25_000L,
                    collectReplyMillis = 90_000L,
                    pollIntervalMillis = 500L,
                    replyQuietMillis = 3_000L,
                    replyMinimumMillis = 5_000L
                )
            ).search(
                TelegramSearchRequest(
                    bot = bot,
                    kind = PrivateSourceKind.NAME,
                    value = testValue
                )
            )

            val expected = expectedPrefix + testValue
            assertTrue(
                "The authenticated WebView completed the request but did not capture the synthetic echo reply. Reply count=" +
                    result.messageCount,
                result.messages.any { it.text.contains(expected, ignoreCase = true) }
            )
        } finally {
            scenario.close()
        }
    }
}

private class AndroidTestTelegramDriver : TelegramSessionDriver {
    private suspend fun view(): SessionWebView = withContext(Dispatchers.Main) {
        val retained = PrivateSessionHost.peek()
            ?: throw IllegalStateException("CI WebView host has not been created.")
        PrivateSessionHost.ensureMounted(retained)
        retained
    }

    override suspend fun checkSession(): TelegramSnapshot =
        TelegramWeb.checkSession(view())

    override suspend fun openTarget(username: String, configuredName: String) {
        TelegramWeb.openTarget(view(), username, configuredName)
    }

    override suspend fun snapshot(): TelegramSnapshot =
        TelegramWeb.snapshot(view())

    override suspend fun startBotIfAvailable(): Boolean =
        TelegramWeb.startBotIfAvailable(view())

    override suspend fun send(text: String): TelegramSendAttempt =
        TelegramWeb.send(view(), text)
}
