package com.cyberphantom.osintworkbench.ui

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cyberphantom.osintworkbench.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemesSettingsNavigationAndroidTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun themesSettingsPageOpensWithoutNestedScrollCrash() {
        composeRule.onNodeWithText("الإعدادات").performClick()
        composeRule.onNodeWithText("الثيمات والمؤثرات").performClick()

        composeRule.onNodeWithText("المظهر الأساسي").assertExists()
        composeRule.onNodeWithText("المظهر المستقبلي الجديد").assertExists()
    }
}
