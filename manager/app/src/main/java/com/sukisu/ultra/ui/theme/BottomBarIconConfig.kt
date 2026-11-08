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

    /**
     * Destination names of this app's bar.
     *
     * The app has four tabs while FolkPatch has five: its module tab is split into KernelPatch
     * (`KModule`) and AndroidPatch (`AModule`). A theme therefore names its module icon with one
     * of those two, which is why [appDestinationOf] maps both onto `Module` - without that mapping
     * the module tab kept its built-in glyph no matter what the theme carried.
     */
    val DESTINATIONS = listOf("Home", "SuperUser", "Module", "Settings")

    private val THEME_ALIASES = mapOf(
        "Module" to listOf("KModule", "AModule"),
    )

    /** The names a theme may use for [destination]'s icon. */
    fun acceptingNames(destination: String): List<String> =
        listOf(destination) + THEME_ALIASES[destination].orEmpty()

    /** The destination a theme's icon name belongs to, or null when it is not a nav icon. */
    fun appDestinationOf(themeName: String): String? = when {
        themeName in DESTINATIONS -> themeName
        else -> THEME_ALIASES.entries.firstOrNull { themeName in it.value }?.key
    }

    /** Every name a theme may use for a bottom-bar icon. */
    fun allThemeNames(): List<String> = DESTINATIONS.flatMap { acceptingNames(it) }

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
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // A theme may have stored the icon under a FolkPatch name (`KModule`); the fine-grained
        // lookup below is what makes a restored theme icon actually show up.
        acceptingNames(destination).forEach { name ->
            val stored = prefs.getString(prefKey(name), null)
            if (!stored.isNullOrBlank() && File(stored).isFile) return stored
            val onDisk = File(context.filesDir, fileName(name))
            if (onDisk.isFile) return onDisk.absolutePath
        }
        return null
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
        acceptingNames(destination).forEach { name ->
            File(context.filesDir, fileName(name)).takeIf { it.exists() }?.delete()
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .also { edit -> acceptingNames(destination).forEach { edit.remove(prefKey(it)) } }
            .apply()
        notifyChanged()
    }

    fun clearAll(context: Context) {
        DESTINATIONS.flatMap { acceptingNames(it) }.forEach { name ->
            File(context.filesDir, fileName(name)).takeIf { it.exists() }?.delete()
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, false)
            .also { edit ->
                DESTINATIONS.flatMap { acceptingNames(it) }.forEach { edit.remove(prefKey(it)) }
            }
            .apply()
        notifyChanged()
    }
}
