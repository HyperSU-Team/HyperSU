package com.sukisu.ultra.ui.util

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The manager's own update check.
 *
 * It used to be a row the user had to tap. It is automatic now and on by default, and it runs every
 * time the app comes to the foreground. The timestamp is only a debounce: without it every activity
 * recreation (a rotation, a theme change) would fire another request.
 */
object ManagerUpdateSettings {

    private const val PREFS_NAME = "settings"
    private const val KEY_ENABLED = "manager_auto_update"
    private const val KEY_LAST_CHECK = "manager_auto_update_last_check"

    /** Short debounce, so "opened the app" means a check but a rotation does not. */
    private const val CHECK_INTERVAL_MS = 60L * 1000L

    /** Observed by the settings row, so the switch follows the stored value. */
    var isEnabled by mutableStateOf(true)
        private set

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isEnabled = prefs.getBoolean(KEY_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        isEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun lastCheck(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_CHECK, 0L)

    fun markChecked(context: Context, now: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_CHECK, now).apply()
    }

    fun isCheckDue(context: Context): Boolean =
        System.currentTimeMillis() - lastCheck(context) > CHECK_INTERVAL_MS
}
