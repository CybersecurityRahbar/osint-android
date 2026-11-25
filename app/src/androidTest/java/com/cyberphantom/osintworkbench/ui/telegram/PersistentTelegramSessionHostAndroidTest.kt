package com.cyberphantom.osintworkbench.ui.telegram

import android.widget.FrameLayout
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistentTelegramSessionHostAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hidingAndShowingSessionKeepsSameWebViewAttachedToPermanentHost() {
        composeRule.setContent {
            AndroidView(
                factory = { viewContext ->
                    FrameLayout(viewContext).apply {
                        PrivateSessionHost.mountRoot(this)
                    }
                },
                update = { PrivateSessionHost.mountRoot(it) }
            )
        }

        try {
            composeRule.waitForIdle()
            val retainedBefore = PrivateSessionHost.peek()
            assertNotNull(retainedBefore)
            val parentBefore = retainedBefore!!.parent
            assertNotNull(parentBefore)
            assertTrue(retainedBefore.isAttachedToWindow)

            composeRule.runOnIdle {
                PrivateSessionHost.showSession()
                PrivateSessionHost.hideSession()
                PrivateSessionHost.showSession()
            }
            composeRule.waitForIdle()

            val retainedAfter = PrivateSessionHost.peek()
            assertSame(retainedBefore, retainedAfter)
            assertSame(parentBefore, retainedAfter!!.parent)
            assertTrue(retainedAfter.isAttachedToWindow)
        } finally {
            composeRule.runOnUiThread { PrivateSessionHost.destroy() }
        }
    }
}
