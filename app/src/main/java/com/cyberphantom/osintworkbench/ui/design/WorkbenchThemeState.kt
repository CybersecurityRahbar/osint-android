package com.cyberphantom.osintworkbench.ui.design

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cyberphantom.osintworkbench.data.WorkbenchThemePreferencesStore

object WorkbenchThemeState {
    var theme: WorkbenchVisualTheme by mutableStateOf(WorkbenchVisualTheme.STEEL)
        private set
    var motionEnabled: Boolean by mutableStateOf(true)
        private set
    var soundEnabled: Boolean by mutableStateOf(false)
        private set
    var completionAlertsEnabled: Boolean by mutableStateOf(true)
        private set
    var hapticsEnabled: Boolean by mutableStateOf(true)
        private set

    fun initialize(context: Context) {
        val store = WorkbenchThemePreferencesStore(context)
        theme = store.selectedTheme()
        motionEnabled = store.motionEnabled()
        soundEnabled = store.soundEnabled()
        completionAlertsEnabled = store.completionAlertsEnabled()
        hapticsEnabled = store.hapticsEnabled()
        IntelColors.apply(theme)
    }

    fun selectTheme(context: Context, selected: WorkbenchVisualTheme) {
        WorkbenchThemePreferencesStore(context).setSelectedTheme(selected)
        theme = selected
        IntelColors.apply(selected)
    }

    fun setMotion(context: Context, enabled: Boolean) {
        WorkbenchThemePreferencesStore(context).setMotionEnabled(enabled)
        motionEnabled = enabled
    }

    fun setSound(context: Context, enabled: Boolean) {
        WorkbenchThemePreferencesStore(context).setSoundEnabled(enabled)
        soundEnabled = enabled
    }

    fun setCompletionAlerts(context: Context, enabled: Boolean) {
        WorkbenchThemePreferencesStore(context).setCompletionAlertsEnabled(enabled)
        completionAlertsEnabled = enabled
    }

    fun setHaptics(context: Context, enabled: Boolean) {
        WorkbenchThemePreferencesStore(context).setHapticsEnabled(enabled)
        hapticsEnabled = enabled
    }

    fun tap(view: View?) {
        if (hapticsEnabled) {
            view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        if (soundEnabled) playTone(ToneGenerator.TONE_PROP_BEEP)
    }

    fun searchFinished(context: Context, view: View?, success: Boolean) {
        if (!completionAlertsEnabled) return
        if (hapticsEnabled) {
            view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        if (soundEnabled) {
            playTone(if (success) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK)
        }
    }

    private fun playTone(tone: Int) {
        val generator = runCatching {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 62)
        }.getOrNull() ?: return
        runCatching { generator.startTone(tone, 130) }
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching { generator.release() }
        }, 240L)
    }
}

@Composable
fun NeonAtmosphereBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "neon-atmosphere")
    val pulse by transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(tween(4_600, easing = LinearEasing), RepeatMode.Reverse),
        label = "neon-glow-pulse"
    )
    val scan by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8_000, easing = LinearEasing), RepeatMode.Restart),
        label = "neon-scanline"
    )

    Canvas(modifier = modifier) {
        drawRect(IntelColors.Base)
        val radius = size.width * 0.82f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(IntelColors.Green.copy(alpha = 0.17f * pulse), Color.Transparent),
                center = Offset(size.width * 0.08f, size.height * 0.20f),
                radius = radius
            ),
            radius = radius,
            center = Offset(size.width * 0.08f, size.height * 0.20f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(IntelColors.Red.copy(alpha = 0.15f * pulse), Color.Transparent),
                center = Offset(size.width * 0.96f, size.height * 0.68f),
                radius = radius * 0.78f
            ),
            radius = radius * 0.78f,
            center = Offset(size.width * 0.96f, size.height * 0.68f)
        )
        val gridGap = 36.dp.toPx()
        var gridY = 0f
        while (gridY < size.height) {
            drawLine(
                color = Color.White.copy(alpha = 0.025f),
                start = Offset(0f, gridY),
                end = Offset(size.width, gridY),
                strokeWidth = 0.5.dp.toPx()
            )
            gridY += gridGap
        }
        val scanY = scan * size.height
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    IntelColors.Blue.copy(alpha = 0.035f),
                    IntelColors.Red.copy(alpha = 0.11f),
                    Color.Transparent
                )
            ),
            topLeft = Offset(0f, scanY - 15.dp.toPx()),
            size = Size(size.width, 30.dp.toPx())
        )
        drawLine(
            color = Color.White.copy(alpha = 0.15f * pulse),
            start = Offset(0f, scanY),
            end = Offset(size.width, scanY),
            strokeWidth = 1.dp.toPx()
        )
    }
}
