package com.sukisu.ultra.ui.theme

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Custom images for the bottom navigation icons, ported from FolkPatch's BottomBarIconConfig.
 *
 * A custom icon replaces one tab's glyph. The images live in `filesDir` under
 * `nav_icon_<destination>.png`, so they travel in a theme archive the same way the wallpapers do,
 * and the preference keys match FolkPatch's (`nav_icon_custom_enabled`, `nav_icon_<dest>`).
 *
 * [revision] is bumped on every change so the bar recomposes: the icons are read through it
 * rather than through Compose state, because they are files whose contents change without the
 * path doing so - replacing an icon would otherwise not redraw.
 */
object BottomBarIconConfig {

    private const val PREFS = BackgroundConfig.PREFS_NAME
    private const val KEY_ENABLED = "nav_icon_custom_enabled"

    /** Destination names, matching FolkPatch so a theme's entries line up. */
    val DESTINATIONS = listOf("Home", "KModule", "SuperUser", "AModule", "Settings")

    private fun prefKey(destination: String) = "nav_icon_$destination"

    fun fileName(destination: String) = "nav_icon_$destination.png"

    /** Bumped whenever an icon or the switch changes, so the bar redraws. */
    var revision by mutableIntStateOf(0)
        private set

    fun notifyChanged() {
        revision++
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        notifyChanged()
    }

    /** The custom icon for [destination], or null when it has none. */
    fun getCustomIconUri(context: Context, destination: String): String? {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(prefKey(destination), null)
        if (stored.isNullOrBlank()) return null
        // The stored value is a file path; a theme can restore an icon whose file was never
        // written on this device, so the file is checked rather than trusted.
        return stored.takeIf { File(it).isFile }
    }

    /**
     * Copies [uri] into internal storage as this destination's icon.
     *
     * The image is taken as-is: the picker in the settings screen already crops it to a square,
     * and re-encoding here would either lose that or duplicate the work.
     */
    suspend fun saveCustomIcon(context: Context, destination: String, uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = File(context.filesDir, fileName(destination))
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                } ?: return@runCatching false

                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(prefKey(destination), target.absolutePath).apply()
                notifyChanged()
                true
            }.getOrElse { false }
        }

    fun clearCustomIcon(context: Context, destination: String) {
        File(context.filesDir, fileName(destination)).takeIf { it.exists() }?.delete()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(prefKey(destination)).apply()
        notifyChanged()
    }

    fun clearAll(context: Context) {
        DESTINATIONS.forEach { destination ->
            File(context.filesDir, fileName(destination)).takeIf { it.exists() }?.delete()
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, false)
            .also { edit -> DESTINATIONS.forEach { edit.remove(prefKey(it)) } }
            .apply()
        notifyChanged()
    }
}
