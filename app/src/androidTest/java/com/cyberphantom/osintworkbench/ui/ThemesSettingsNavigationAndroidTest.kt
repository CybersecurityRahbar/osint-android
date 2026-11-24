package com.cyberphantom.osintworkbench.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cyberphantom.osintworkbench.data.LocalStore
import com.cyberphantom.osintworkbench.data.StoreStats
import com.cyberphantom.osintworkbench.ui.design.WorkbenchTheme
import com.cyberphantom.osintworkbench.ui.design.WorkbenchThemeState
import com.cyberphantom.osintworkbench.ui.screens.SettingsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemesSettingsNavigationAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun themesSettingsPageOpensWithoutNestedScrollCrash() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = LocalStore(context)
        WorkbenchThemeState.initialize(context)

        composeRule.setContent {
            WorkbenchTheme {
                SettingsScreen(
                    stats = StoreStats(),
                    store = store,
                    dataVersion = 0,
                    onDataChanged = {}
                )
            }
        }

        composeRule.onNodeWithText("الثيمات والمؤثرات").performClick()
        composeRule.onNodeWithText("المظهر الأساسي").assertIsDisplayed()
        composeRule.onNodeWithText("المظهر المستقبلي الجديد").assertIsDisplayed()
    }
}
