package com.cyberphantom.osintworkbench.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cyberphantom.osintworkbench.ui.design.WorkbenchVisualTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkbenchThemePreferencesStoreAndroidTest {
    @Test
    fun visualThemeAndEffectsArePersisted() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = WorkbenchThemePreferencesStore(context)
        try {
            store.setSelectedTheme(WorkbenchVisualTheme.NEON)
            store.setMotionEnabled(false)
            store.setSoundEnabled(true)
            store.setCompletionAlertsEnabled(false)
            store.setHapticsEnabled(false)

            assertEquals(WorkbenchVisualTheme.NEON, store.selectedTheme())
            assertFalse(store.motionEnabled())
            assertTrue(store.soundEnabled())
            assertFalse(store.completionAlertsEnabled())
            assertFalse(store.hapticsEnabled())
        } finally {
            store.setSelectedTheme(WorkbenchVisualTheme.STEEL)
            store.setMotionEnabled(true)
            store.setSoundEnabled(false)
            store.setCompletionAlertsEnabled(true)
            store.setHapticsEnabled(true)
        }
    }
}
