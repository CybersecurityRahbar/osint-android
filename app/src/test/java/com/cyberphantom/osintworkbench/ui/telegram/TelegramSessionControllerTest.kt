package com.cyberphantom.osintworkbench.ui.telegram

import com.cyberphantom.osintworkbench.data.PrivateSourceBot
import com.cyberphantom.osintworkbench.data.PrivateSourceFlow
import com.cyberphantom.osintworkbench.data.PrivateSourceKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramSessionControllerTest {

    @Test
    fun commandAndPromptFlowSendsCommandThenValueAndCollectsMultipleReplies() = runBlocking {
        val driver = FakeTelegramDriver(PrivateSourceFlow.COMMAND_AND_PROMPT)
        val result = controller(driver).search(request(PrivateSourceFlow.COMMAND_AND_PROMPT))

        assertEquals(listOf("/lookup_phone", "test-value"), driver.sent)
        assertEquals("الاسم: أحمد\n\nالمدينة: صنعاء", result.displayText)
        assertEquals(2, result.messageCount)
    }

    @Test
    fun nameSearchUsesItsOwnCommandAndConfiguredPrompt() = runBlocking {
        val driver = FakeTelegramDriver(
            flow = PrivateSourceFlow.COMMAND_AND_PROMPT,
            kind = PrivateSourceKind.NAME
        )
        val nameRequest = request(
            flow = PrivateSourceFlow.COMMAND_AND_PROMPT,
            kind = PrivateSourceKind.NAME
        )
        val result = controller(driver).search(nameRequest)

        assertEquals(listOf("/lookup_name", "test-value"), driver.sent)
        assertTrue(result.displayText.contains("الاسم: أحمد"))
    }

    @Test
    fun commandThenValueFlowDoesNotWaitForOrRequirePrompt() = runBlocking {
        val driver = FakeTelegramDriver(PrivateSourceFlow.COMMAND_THEN_VALUE)
        val result = controller(driver).search(request(PrivateSourceFlow.COMMAND_THEN_VALUE))

        assertEquals(listOf("/lookup_phone", "test-value"), driver.sent)
        assertTrue(result.displayText.contains("الاسم: أحمد"))
    }

    @Test
    fun valueOnlyFlowNeverSendsConfiguredCommand() = runBlocking {
        val driver = FakeTelegramDriver(PrivateSourceFlow.VALUE_ONLY)
        val result = controller(driver).search(request(PrivateSourceFlow.VALUE_ONLY))

        assertEquals(listOf("test-value"), driver.sent)
        assertEquals(2, result.messageCount)
    }

    @Test
    fun refusesToSendWhenTheExistingSessionIsNotAuthenticated() = runBlocking {
        val driver = FakeTelegramDriver(
            flow = PrivateSourceFlow.VALUE_ONLY,
            authenticated = false
        )
        val failure = runCatching {
            controller(driver).search(request(PrivateSourceFlow.VALUE_ONLY))
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(driver.sent.isEmpty())
    }

    @Test
    fun numericPeerRouteRequiresTheConfiguredChatTitle() {
        val wrongPeer = snapshot(
            route = "123456789",
            title = "A different conversation"
        )
        assertFalse(
            TelegramPeerMatcher.isTargetIdentity(
                wrongPeer,
                username = "test_bot",
                configuredTitle = "Directory Bot"
            )
        )

        val expectedPeer = wrongPeer.copy(title = "Directory Bot")
        assertTrue(
            TelegramPeerMatcher.chatMatches(
                expectedPeer,
                username = "test_bot",
                configuredTitle = "Directory Bot"
            )
        )
        assertFalse(
            TelegramPeerMatcher.isTargetIdentity(
                snapshot(route = "@test_bot", title = "A stale previous chat"),
                username = "test_bot",
                configuredTitle = "Directory Bot"
            )
        )
    }

    @Test
    fun ambiguousCommandEchoStopsBeforeSendingValueAndNeverRetries() = runBlocking {
        val driver = FakeTelegramDriver(
            flow = PrivateSourceFlow.COMMAND_THEN_VALUE,
            omitOutgoingEchoFor = "/lookup_phone"
        )
        val failure = runCatching {
            controller(
                driver,
                TelegramStageTimeouts(
                    commandEchoMillis = 25,
                    pollIntervalMillis = 1
                )
            ).search(request(PrivateSourceFlow.COMMAND_THEN_VALUE))
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(listOf("/lookup_phone"), driver.sent)
    }

    @Test
    fun missingPromptStopsCommandAndDoesNotSendTheSearchValue() = runBlocking {
        val driver = FakeTelegramDriver(
            flow = PrivateSourceFlow.COMMAND_AND_PROMPT,
            emitPrompt = false
        )
        val failure = runCatching {
            controller(
                driver,
                TelegramStageTimeouts(
                    promptMillis = 25,
                    pollIntervalMillis = 1
                )
            ).search(request(PrivateSourceFlow.COMMAND_AND_PROMPT))
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(listOf("/lookup_phone"), driver.sent)
    }

    @Test
    fun ambiguousValueEchoIsNotRetried() = runBlocking {
        val driver = FakeTelegramDriver(
            flow = PrivateSourceFlow.VALUE_ONLY,
            omitOutgoingEchoFor = "test-value"
        )
        val failure = runCatching {
            controller(
                driver,
                TelegramStageTimeouts(
                    valueEchoMillis = 25,
                    pollIntervalMillis = 1
                )
            ).search(request(PrivateSourceFlow.VALUE_ONLY))
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(listOf("test-value"), driver.sent)
    }

    @Test
    fun onlyOneRequestCanControlTheRetainedSessionAtATime() = runBlocking {
        val enteredSessionCheck = CompletableDeferred<Unit>()
        val releaseSessionCheck = CompletableDeferred<Unit>()
        val driver = FakeTelegramDriver(
            flow = PrivateSourceFlow.VALUE_ONLY,
            onSessionCheck = {
                enteredSessionCheck.complete(Unit)
                releaseSessionCheck.await()
            }
        )
        val engine = controller(driver)
        val first = async { engine.search(request(PrivateSourceFlow.VALUE_ONLY)) }

        withTimeout(1_000) { enteredSessionCheck.await() }
        val secondFailure = runCatching {
            engine.search(request(PrivateSourceFlow.VALUE_ONLY))
        }.exceptionOrNull()

        assertTrue(secondFailure is TelegramRequestAlreadyRunningException)
        releaseSessionCheck.complete(Unit)
        first.await()
        assertEquals(listOf("test-value"), driver.sent)
    }

    @Test
    fun cancellingActiveSearchStopsBeforeAnyLaterSend() = runBlocking {
        val enteredSessionCheck = CompletableDeferred<Unit>()
        val neverRelease = CompletableDeferred<Unit>()
        val driver = FakeTelegramDriver(
            flow = PrivateSourceFlow.VALUE_ONLY,
            onSessionCheck = {
                enteredSessionCheck.complete(Unit)
                neverRelease.await()
            }
        )
        val updates = mutableListOf<TelegramSearchStage>()
        val engine = controller(driver)
        val running = async {
            engine.search(request(PrivateSourceFlow.VALUE_ONLY)) { updates += it.stage }
        }

        withTimeout(1_000) { enteredSessionCheck.await() }
        running.cancel()
        runCatching { running.await() }

        assertTrue(updates.contains(TelegramSearchStage.CANCELLED))
        assertTrue(driver.sent.isEmpty())
    }

    @Test
    fun replyFormatterReportsMediaAndInlineButtonsInsteadOfDroppingThem() {
        val formatted = TelegramReplyFormatter.format(
            listOf(
                TelegramMessage(
                    key = "photo",
                    text = "هذه صورة",
                    outgoing = false,
                    contentType = "image",
                    mediaCount = 1
                ),
                TelegramMessage(
                    key = "buttons",
                    text = "",
                    outgoing = false,
                    contentType = "buttons",
                    hasInlineButtons = true
                )
            )
        )

        assertTrue(formatted.contains("هذه صورة"))
        assertTrue(formatted.contains("مرفق صورة"))
        assertTrue(formatted.contains("أزرار تفاعل"))
    }

    private fun controller(
        driver: FakeTelegramDriver,
        timeouts: TelegramStageTimeouts = TelegramStageTimeouts(
            checkSessionMillis = 200,
            openTargetMillis = 200,
            verifyPeerMillis = 200,
            verifyComposerMillis = 200,
            sendMillis = 200,
            commandEchoMillis = 200,
            promptMillis = 200,
            valueEchoMillis = 200,
            collectReplyMillis = 500,
            pollIntervalMillis = 1,
            replyQuietMillis = 5,
            replyMinimumMillis = 0
        )
    ) = TelegramSessionController(driver, timeouts)

    private fun request(
        flow: PrivateSourceFlow,
        kind: PrivateSourceKind = PrivateSourceKind.NUMBER
    ) = TelegramSearchRequest(
        bot = PrivateSourceBot(
            id = "test",
            name = "Directory Bot",
            username = "test_bot",
            numberCommand = "/lookup_phone",
            nameCommand = "/lookup_name",
            numberPrompt = "أرسل رقم الهاتف",
            namePrompt = "أرسل الاسم",
            numberFlow = flow,
            nameFlow = flow,
            chatTitle = "Directory Bot"
        ),
        kind = kind,
        value = "test-value"
    )

    private class FakeTelegramDriver(
        private val flow: PrivateSourceFlow,
        private val kind: PrivateSourceKind = PrivateSourceKind.NUMBER,
        private val authenticated: Boolean = true,
        private val omitOutgoingEchoFor: String? = null,
        private val emitPrompt: Boolean = true,
        private val onSessionCheck: suspend () -> Unit = {}
    ) : TelegramSessionDriver {
        val sent = mutableListOf<String>()
        private val messages = mutableListOf<TelegramMessage>()
        private var currentPeer = false
        private var sequence = 0

        override suspend fun checkSession(): TelegramSnapshot {
            onSessionCheck()
            return currentSnapshot().copy(
                isAuthenticated = authenticated,
                isLoginPage = !authenticated
            )
        }

        override suspend fun openTarget(username: String, configuredName: String) {
            currentPeer = true
        }

        override suspend fun snapshot(): TelegramSnapshot = currentSnapshot()

        override suspend fun startBotIfAvailable(): Boolean = false

        override suspend fun send(text: String): TelegramSendAttempt {
            sent += text
            if (text != omitOutgoingEchoFor) {
                messages += TelegramMessage(
                    key = "out-${++sequence}",
                    text = text,
                    outgoing = true
                )
            }
            if (emitPrompt && flow == PrivateSourceFlow.COMMAND_AND_PROMPT && text.startsWith("/")) {
                messages += TelegramMessage(
                    key = "prompt-${++sequence}",
                    text = if (kind == PrivateSourceKind.NUMBER) {
                        "أرسل رقم الهاتف للمتابعة"
                    } else {
                        "أرسل الاسم للبحث عنه"
                    },
                    outgoing = false
                )
            }
            if (text == "test-value") {
                messages += TelegramMessage(
                    key = "reply-${++sequence}",
                    text = "الاسم: أحمد",
                    outgoing = false
                )
                messages += TelegramMessage(
                    key = "reply-${++sequence}",
                    text = "المدينة: صنعاء",
                    outgoing = false
                )
            }
            return TelegramSendAttempt("fake")
        }

        private fun currentSnapshot(): TelegramSnapshot =
            snapshot(
                route = if (currentPeer) "@test_bot" else "",
                title = if (currentPeer) "Directory Bot" else "Telegram",
                messages = messages.toList(),
                authenticated = authenticated
            )
    }
}

private fun snapshot(
    route: String = "@test_bot",
    title: String = "Directory Bot",
    messages: List<TelegramMessage> = emptyList(),
    authenticated: Boolean = true
) = TelegramSnapshot(
    url = "https://web.telegram.org/k/#$route",
    title = title,
    hasComposer = true,
    hasSendButton = true,
    editorText = "",
    readyState = "complete",
    messages = messages,
    peerRoute = route,
    isAuthenticated = authenticated,
    isLoginPage = !authenticated,
    hasAppShell = authenticated
)
