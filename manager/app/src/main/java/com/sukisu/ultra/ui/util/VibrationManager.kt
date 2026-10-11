package com.sukisu.ultra.ui.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.sukisu.ultra.ui.theme.VibrationConfig

/**
 * Fires the short haptic tap the vibration setting controls.
 *
 * The scope is not checked here: the caller decides whether this is a global tap or one belonging
 * to the bottom bar, the same division FolkPatch uses. This only asks whether vibration is on at
 * all.
 */
object VibrationManager {

    private const val TAG = "VibrationManager"

    /** A tap is short by definition; the setting controls strength, not length. */
    private const val DURATION_MS = 30L

    fun vibrate(context: Context) {
        if (!VibrationConfig.isVibrationEnabled) return

        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (!vibrator.hasVibrator()) return

            // 1..255, and the floor is 1 rather than 0: a zero amplitude is not a weaker tap, it is
            // no tap at all, and a user who turns the slider to the bottom still asked for one.
            val amplitude = (VibrationConfig.vibrationIntensity * 255).toInt().coerceIn(1, 255)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    vibrator.vibrate(VibrationEffect.createOneShot(DURATION_MS, amplitude))
                } catch (_: Exception) {
                    // Some devices reject amplitude control; a plain tap is better than none.
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(DURATION_MS)
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(DURATION_MS)
            }
        } catch (e: Exception) {
            // Includes SecurityException on devices that refuse vibration outright.
            Log.w(TAG, "could not vibrate", e)
        }
    }
}
