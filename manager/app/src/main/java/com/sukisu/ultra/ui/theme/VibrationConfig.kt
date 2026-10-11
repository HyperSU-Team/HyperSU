package com.sukisu.ultra.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The haptic feedback setting, in the same shape as the sound-effect one.
 *
 * Ported from FolkPatch's `VibrationConfig`, including its storage: the same preference file and
 * key names, so a theme exported there and imported here carries the setting, and the two apps
 * agree on what "global" and "bottom_bar" mean.
 */
object VibrationConfig {

    private const val PREFS_NAME = "vibration_settings"
    private const val KEY_ENABLED = "vibration_enabled"
    private const val KEY_INTENSITY = "vibration_intensity"
    private const val KEY_SCOPE = "vibration_scope"

    const val SCOPE_GLOBAL = "global"
    const val SCOPE_BOTTOM_BAR = "bottom_bar"

    var isVibrationEnabled: Boolean by mutableStateOf(false)
        private set

    var vibrationIntensity: Float by mutableStateOf(0.5f)
        private set

    var scope: String by mutableStateOf(SCOPE_GLOBAL)
        private set

    fun setEnabledState(enabled: Boolean) {
        isVibrationEnabled = enabled
    }

    /** Clamped here rather than at the call site, so an out-of-range value cannot be stored. */
    fun setIntensityValue(value: Float) {
        vibrationIntensity = value.coerceIn(0f, 1f)
    }

    fun setScopeValue(value: String) {
        scope = value
    }

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isVibrationEnabled = prefs.getBoolean(KEY_ENABLED, false)
        vibrationIntensity = prefs.getFloat(KEY_INTENSITY, 0.5f).coerceIn(0f, 1f)
        scope = prefs.getString(KEY_SCOPE, SCOPE_GLOBAL) ?: SCOPE_GLOBAL
    }

    fun save(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, isVibrationEnabled)
            .putFloat(KEY_INTENSITY, vibrationIntensity)
            .putString(KEY_SCOPE, scope)
            .apply()
    }
}
