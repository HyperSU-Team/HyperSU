package com.sukisu.ultra.ui.util

import android.content.Context
import android.content.res.Configuration

/**
 * A user-chosen display density, in dpi.
 *
 * Applied by overriding the density on a wrapped context, which has to happen in
 * `attachBaseContext` - the resources are read before `onCreate`, so setting it there would be too
 * late and the first screen would still be laid out at the system density.
 *
 * The value lives in its own preference file, under FolkPatch's name and key, so a theme carries
 * it and an install that set it there keeps it.
 *
 * There is deliberately no default `Context` parameter here. This is read from
 * `attachBaseContext`, which runs before `Application.onCreate` has assigned the global
 * application reference, so reaching for that would crash on startup.
 */
object DpiUtils {

    private const val PREFS_NAME = "settings"
    private const val KEY_DPI = "app_dpi"

    /** Follow the system density. */
    const val DEFAULT_DPI = -1

    const val MIN_DPI = 160
    const val MAX_DPI = 600

    fun currentDpi(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_DPI, DEFAULT_DPI)

    fun save(context: Context, dpi: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_DPI, dpi.coerceIn(MIN_DPI, MAX_DPI))
            .apply()
    }

    /** A name for a density, for the settings row's summary. */
    fun friendlyName(dpi: Int): String = when {
        dpi <= 240 -> "Small"
        dpi <= 360 -> "Medium"
        dpi <= 480 -> "Large"
        else -> "Extra large"
    }

    /**
     * Returns [base] with its density overridden, or [base] itself when following the system.
     *
     * A context is always created rather than only when a density is set: a theme import can set
     * one while the app is running, and the activity is recreated after that, so the branch has to
     * be able to go both ways.
     */
    fun wrap(base: Context): Context {
        val dpi = currentDpi(base)
        if (dpi == DEFAULT_DPI) return base
        return runCatching {
            val config = Configuration(base.resources.configuration)
            config.densityDpi = dpi
            base.createConfigurationContext(config)
        }.getOrElse { base }
    }
}
