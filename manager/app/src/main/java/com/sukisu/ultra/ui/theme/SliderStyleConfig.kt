package com.sukisu.ultra.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Whether sliders snap to steps instead of moving continuously.
 *
 * A global rather than a parameter because every slider in the app reads it and none of them own
 * it - the setting lives on the appearance page. Ported from FolkPatch's `SliderStyleConfig`,
 * including the preference key, so a theme carries it.
 */
object SliderStyleConfig {

    private const val PREFS_NAME = "settings"
    private const val KEY_DISCRETE = "discrete_slider"

    /** The step count used when discrete mode is on; 19 gives 20 positions across a 0..1 range. */
    const val DISCRETE_STEPS = 19

    var isDiscrete: Boolean by mutableStateOf(true)
        private set

    fun setDiscrete(value: Boolean) {
        isDiscrete = value
    }

    fun load(context: Context) {
        isDiscrete = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DISCRETE, true)
    }
}
