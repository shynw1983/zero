package com.zero.todo.core.designsystem

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

val LocalZeroHapticsEnabled = staticCompositionLocalOf { true }

/**
 * Tactile feedback utility tailored to ZERO's Near-Future Minimalism interactions:
 * - Mechanical click on task completion / swipe threshold snap
 * - Subtle tick on drag-and-drop reorder crossings
 * - Crisp double-pulse on undo / voice capture activation
 * - Heavy snap on focus timer completion
 */
@Immutable
class ZeroHaptics(
    private val vibrator: Vibrator?,
    private val composeHaptic: HapticFeedback?,
    private val enabled: Boolean = true
) {
    /** Crisp mechanical click for task completion or swipe-to-complete snap. */
    fun taskComplete() {
        if (!enabled) return
        performPredefinedOrWaveform(
            predefinedEffect = VibrationEffect.EFFECT_CLICK,
            fallbackDurationMs = 14L,
            fallbackAmplitude = 180,
            composeFallback = HapticFeedbackType.LongPress
        )
    }

    /** Subtle mechanical tick for drag-and-drop reordering and tree snap points. */
    fun reorderTick() {
        if (!enabled) return
        performPredefinedOrWaveform(
            predefinedEffect = VibrationEffect.EFFECT_TICK,
            fallbackDurationMs = 8L,
            fallbackAmplitude = 90,
            composeFallback = HapticFeedbackType.TextHandleMove
        )
    }

    /** Short crisp confirmation for quick-input capture or undo action. */
    fun confirm() {
        if (!enabled) return
        performPredefinedOrWaveform(
            predefinedEffect = VibrationEffect.EFFECT_DOUBLE_CLICK,
            fallbackDurationMs = 12L,
            fallbackAmplitude = 140,
            composeFallback = HapticFeedbackType.LongPress
        )
    }

    /** Stronger tactile click for entering voice capture or finishing a focus session. */
    fun heavyClick() {
        if (!enabled) return
        performPredefinedOrWaveform(
            predefinedEffect = VibrationEffect.EFFECT_HEAVY_CLICK,
            fallbackDurationMs = 22L,
            fallbackAmplitude = 230,
            composeFallback = HapticFeedbackType.LongPress
        )
    }

    /** Gentle tactile pulse used when the 2-Minute Starter finishes and invites seamless Pomodoro transition. */
    fun gentlePrompt() {
        if (!enabled) return
        val vib = vibrator
        if (vib != null && vib.hasVibrator()) {
            try {
                val timings = longArrayOf(0L, 18L, 90L, 24L)
                val amplitudes = intArrayOf(0, 75, 0, 110)
                vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                return
            } catch (_: SecurityException) {
                // Fall back to Compose HapticFeedback.
            }
        }
        composeHaptic?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    /** Gentle resistance feedback when rejecting an invalid drop target (e.g., folder cycle). */
    fun reject() {
        if (!enabled) return
        val vib = vibrator
        if (vib != null && vib.hasVibrator()) {
            try {
                val timings = longArrayOf(0L, 12L, 35L, 12L)
                val amplitudes = intArrayOf(0, 120, 0, 120)
                vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                return
            } catch (_: SecurityException) {
                // Fall back to Compose HapticFeedback if VIBRATE permission is unavailable.
            }
        }
        composeHaptic?.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    private fun performPredefinedOrWaveform(
        predefinedEffect: Int,
        fallbackDurationMs: Long,
        fallbackAmplitude: Int,
        composeFallback: HapticFeedbackType
    ) {
        val vib = vibrator
        if (vib != null && vib.hasVibrator()) {
            try {
                val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    VibrationEffect.createPredefined(predefinedEffect)
                } else {
                    VibrationEffect.createOneShot(fallbackDurationMs, fallbackAmplitude)
                }
                vib.vibrate(effect)
                return
            } catch (_: SecurityException) {
                // Fall back to Compose HapticFeedback.
            }
        }
        composeHaptic?.performHapticFeedback(composeFallback)
    }

    companion object {
        fun resolveVibrator(context: Context): Vibrator? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }
    }
}

@Composable
fun rememberZeroHaptics(): ZeroHaptics {
    val context = LocalContext.current.applicationContext
    val composeHaptic = LocalHapticFeedback.current
    val enabled = LocalZeroHapticsEnabled.current
    return remember(context, composeHaptic, enabled) {
        ZeroHaptics(
            vibrator = ZeroHaptics.resolveVibrator(context),
            composeHaptic = composeHaptic,
            enabled = enabled
        )
    }
}
