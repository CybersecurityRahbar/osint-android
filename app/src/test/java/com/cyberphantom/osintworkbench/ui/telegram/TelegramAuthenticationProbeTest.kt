package com.cyberphantom.osintworkbench.ui.telegram

import com.cyberphantom.osintworkbench.domain.TelegramAuthenticationProbe
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramAuthenticationProbeTest {
    @Test
    fun visibleTelegramChatShellConfirmsExistingSession() {
        assertTrue(TelegramAuthenticationProbe.isAuthenticated(false, true, false, false))
    }

    @Test
    fun visibleChatHeaderAndComposerConfirmSelectedConversation() {
        assertTrue(TelegramAuthenticationProbe.isAuthenticated(false, false, true, true))
    }

    @Test
    fun loginScreenAlwaysOverridesOtherSignals() {
        assertFalse(TelegramAuthenticationProbe.isAuthenticated(true, true, true, true))
    }

    @Test
    fun incompleteSignalsDoNotClaimSessionIsAuthenticated() {
        assertFalse(TelegramAuthenticationProbe.isAuthenticated(false, false, true, false))
    }
}
