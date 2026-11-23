package com.cyberphantom.osintworkbench.data

import android.content.Context
import com.cyberphantom.osintworkbench.ui.design.WorkbenchVisualTheme

class WorkbenchThemePreferencesStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "osint_workbench_visual_settings_v1",
        Context.MODE_PRIVATE
    )

    fun selectedTheme(): WorkbenchVisualTheme =
        runCatching {
            WorkbenchVisualTheme.valueOf(
                preferences.getString(KEY_THEME, WorkbenchVisualTheme.STEEL.name)
                    ?: WorkbenchVisualTheme.STEEL.name
            )
        }.getOrDefault(WorkbenchVisualTheme.STEEL)

    fun setSelectedTheme(theme: WorkbenchVisualTheme) {
        preferences.edit().putString(KEY_THEME, theme.name).apply()
    }

    fun motionEnabled(): Boolean = preferences.getBoolean(KEY_MOTION, true)
    fun setMotionEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_MOTION, enabled).apply()
    }

    fun soundEnabled(): Boolean = preferences.getBoolean(KEY_SOUND, false)
    fun setSoundEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SOUND, enabled).apply()
    }

    fun completionAlertsEnabled(): Boolean = preferences.getBoolean(KEY_ALERTS, true)
    fun setCompletionAlertsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ALERTS, enabled).apply()
    }

    fun hapticsEnabled(): Boolean = preferences.getBoolean(KEY_HAPTICS, true)
    fun setHapticsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_HAPTICS, enabled).apply()
    }

    companion object {
        private const val KEY_THEME = "selected_theme"
        private const val KEY_MOTION = "motion_enabled"
        private const val KEY_SOUND = "sound_enabled"
        private const val KEY_ALERTS = "completion_alerts_enabled"
        private const val KEY_HAPTICS = "haptics_enabled"
    }
}
