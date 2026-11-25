package com.cyberphantom.osintworkbench.domain

/**
 * Interprets Telegram Web K DOM signals without treating a merely loaded page as a
 * signed-in session. The DOM adapter supplies only visible, non-hidden elements.
 */
object TelegramAuthenticationProbe {
    fun isAuthenticated(
        isLoginPage: Boolean,
        hasVisibleChatShell: Boolean,
        hasVisibleChatTitle: Boolean,
        hasVisibleComposer: Boolean
    ): Boolean {
        if (isLoginPage) return false
        return hasVisibleChatShell || (hasVisibleChatTitle && hasVisibleComposer)
    }
}
