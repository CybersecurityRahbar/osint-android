package com.cyberphantom.osintworkbench.ui.telegram

import com.cyberphantom.osintworkbench.data.PrivateSourceFlow
import com.cyberphantom.osintworkbench.data.PrivateSourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelegramLiveLabProfileTest {
    private fun profile(
        flow: PrivateSourceFlow = PrivateSourceFlow.VALUE_ONLY,
        username: String = "my_echo_test_bot",
        command: String = "",
        prompt: String = ""
    ) = TelegramLiveLabProfile(
        botName = "Owned test bot",
        botUsername = username,
        kind = PrivateSourceKind.NAME,
        flow = flow,
        command = command,
        prompt = prompt,
        testValue = "OSINT_LAB_PING_2026"
    )

    @Test
    fun validValueOnlyProfilePasses() {
        assertNull(validateTelegramLiveLabProfile(profile()))
    }

    @Test
    fun usernameMustBeAValidTelegramHandle() {
        assertEquals(
            "أدخل معرّف بوت Telegram صحيحاً (5 إلى 32 حرفاً أو رقماً أو _).",
            validateTelegramLiveLabProfile(profile(username = "bad handle"))
        )
    }

    @Test
    fun commandFlowsRequireACommand() {
        assertEquals(
            "أدخل الأمر المطلوب لهذا التدفق أو اختر نمط قيمة فقط.",
            validateTelegramLiveLabProfile(profile(flow = PrivateSourceFlow.COMMAND_THEN_VALUE))
        )
    }

    @Test
    fun promptFlowRequiresTheExpectedPrompt() {
        assertEquals(
            "أدخل نص المطالبة المتوقع أو اختر تدفقاً لا يحتاج إلى مطالبة.",
            validateTelegramLiveLabProfile(
                profile(
                    flow = PrivateSourceFlow.COMMAND_AND_PROMPT,
                    command = "/echo",
                    prompt = ""
                )
            )
        )
    }

    @Test
    fun completePromptFlowPasses() {
        assertNull(
            validateTelegramLiveLabProfile(
                profile(
                    flow = PrivateSourceFlow.COMMAND_AND_PROMPT,
                    command = "/echo",
                    prompt = "send test text"
                )
            )
        )
    }
}
