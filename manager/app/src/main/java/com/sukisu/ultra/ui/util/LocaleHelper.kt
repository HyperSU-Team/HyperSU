package com.sukisu.ultra.ui.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import androidx.core.content.edit
import java.util.Locale

object LocaleHelper {
    private const val PREFS = "settings"
    private const val KEY_LANGUAGE = "app_language"

    // follow system language.
    const val SYSTEM = ""

    /**
     * The languages the in-app list offers.
     *
     * These have to be the tags the platform recognises, which is not always the tag a
     * resource folder is named after. `values-in` and `values-iw` are the historical folder
     * names for Indonesian and Hebrew; the language tags are `id` and `he`, and asking for a
     * tag the generated locale config does not list is what made a switch fail. `tl` is the
     * other name for `fil`, so only one of the two is offered.
     */
    val SUPPORTED_TAGS: List<String> = listOf(
        "en", "ar", "az", "bg", "bn", "bn-BD", "bs", "da", "de", "es", "et",
        "fa", "fil", "fr", "gl", "hi", "hr", "hu", "id", "it", "he", "ja",
        "km", "kn", "ko", "lt", "lv", "mr", "ms", "my", "nl", "pl", "pt",
        "pt-BR", "ro", "ru", "sl", "sr", "te", "th", "tr", "uk", "vi",
        "zh-CN", "zh-HK", "zh-TW"
    )

    fun getPersistedLanguage(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, SYSTEM) ?: SYSTEM

    private fun persistLanguage(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putString(KEY_LANGUAGE, tag) }
    }

    private fun getSystemLocale(): Locale =
        Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()

    private fun getAppLocaleManager(context: Context): android.app.LocaleManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(android.app.LocaleManager::class.java)
        } else {
            null
        }

    private fun syncPersistedLanguageWithSystem(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return getPersistedLanguage(context)
        }

        val locales = getAppLocaleManager(context)?.applicationLocales ?: LocaleList.getEmptyLocaleList()
        val tag = if (locales.isEmpty) SYSTEM else locales[0]?.toLanguageTag() ?: SYSTEM
        persistLanguage(context, tag)
        return tag
    }

    fun getCurrentLanguage(context: Context): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            syncPersistedLanguageWithSystem(context)
        } else {
            getPersistedLanguage(context)
        }

    fun setLanguage(context: Context, tag: String) {
        persistLanguage(context, tag)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ treats applicationLocales as the source of truth.
            //
            // The assignment is guarded: the platform rejects a tag the app's locale config
            // does not list, and that rejection arrives as an exception. The config is
            // generated from the resource folders, whose names do not always match the modern
            // language tag - `values-in` and `values-iw` are the historical spellings of `id`
            // and `he` - so a tag offered by our own list can be refused. Letting that escape
            // took the app down on a language pick; the persisted value is already written, so
            // the switch still applies on the next launch.
            runCatching {
                getAppLocaleManager(context)?.applicationLocales = if (tag.isEmpty()) {
                    LocaleList.getEmptyLocaleList()
                } else {
                    LocaleList.forLanguageTags(tag)
                }
            }
            return
        }

        val locale = if (tag.isEmpty()) getSystemLocale() else Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
    }

    fun displayName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayName(locale)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

    /**
     * Opens the system's per-app language page, where the language is chosen.
     *
     * Handing the choice to the system is what keeps the switch out of our own
     * window: it is applied while our task sits in the background, so our activity
     * is never recreated in front of the user and no starting window plays over
     * our screens.
     *
     * Returns false on versions that have no such page, or on a build that does
     * not ship it, so the caller can fall back to the in-app list.
     */
    fun launchSystemLanguageSettings(context: Context): Boolean {
        if (!canLaunchSystemLanguageSettings(context)) return false

        return try {
            context.startActivity(systemLanguageSettingsIntent(context))
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Whether this device ships the system's per-app language page. */
    fun canLaunchSystemLanguageSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return try {
            systemLanguageSettingsIntent(context).resolveActivity(context.packageManager) != null
        } catch (_: Exception) {
            false
        }
    }

    private fun systemLanguageSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))

    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            syncPersistedLanguageWithSystem(base)
            // LocaleList.get returns null on an empty list, and Locale.setDefault rejects null.
            base.resources.configuration.locales[0]?.let { Locale.setDefault(it) }
            return base
        }

        val tag = getPersistedLanguage(base)
        if (tag.isEmpty()) {
            Locale.setDefault(getSystemLocale())
            return base
        }

        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)

        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}

fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
