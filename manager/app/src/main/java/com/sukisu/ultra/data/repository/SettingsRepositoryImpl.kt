package com.sukisu.ultra.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.edit
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.topjohnwu.superuser.ShellUtils
import com.sukisu.ultra.Natives
import com.sukisu.ultra.ksuApp
import com.sukisu.ultra.magica.BootCompletedReceiver
import com.sukisu.ultra.ui.screen.modulerepo.RepoSort
import com.sukisu.ultra.ui.theme.ColorContrast
import com.sukisu.ultra.ui.theme.booleanPref
import com.sukisu.ultra.ui.theme.floatPref
import com.sukisu.ultra.ui.theme.intPref
import com.sukisu.ultra.ui.theme.stringPref
import com.sukisu.ultra.ui.util.execKsud
import com.sukisu.ultra.ui.util.getFeaturePersistValue
import com.sukisu.ultra.ui.util.getFeatureStatus
import com.sukisu.ultra.ui.util.LocaleHelper
import java.security.SecureRandom

/** The two arrangements the Grid layout's status cards can take. */
const val STATS_TOP_LIST = "list"
const val STATS_TOP_GRID = "grid"

private const val SETTINGS_PREFS = "settings"
private const val KEY_USE_SOFT_REBOOT = "soft_reboot"

/**
 * The Home layouts the user can pick from. "circle" is the flagship FolkPatch
 * composition; the others are the lighter variants. There is deliberately no
 * stats/hardware-monitor layout.
 */
const val HOME_LAYOUT_CIRCLE = "circle"
const val HOME_LAYOUT_LIST = "default"
const val HOME_LAYOUT_FOCUS = "focus"
const val HOME_LAYOUT_DASHBOARD = "dashboard_ui"
/** FolkPatch 的 GridUI：一张大状态卡 + 侧边小卡。 */
const val HOME_LAYOUT_GRID = "grid"

/** The layouts offered in the picker, in display order. */
val HOME_LAYOUT_OPTIONS = listOf(
    HOME_LAYOUT_CIRCLE,
    HOME_LAYOUT_LIST,
    HOME_LAYOUT_FOCUS,
    HOME_LAYOUT_DASHBOARD,
    HOME_LAYOUT_GRID,
)

/** Value used when the stored layout is missing or unrecognised. */
const val HOME_LAYOUT_FALLBACK = HOME_LAYOUT_CIRCLE

/** SharedPreferences key backing [SettingsRepository.homeLayoutStyle]. */
const val KEY_HOME_LAYOUT = "home_layout_style"

/** The Grid layout's status-card arrangement. */
const val KEY_STATS_TOP_LAYOUT = "stats_top_layout"

/** Prefer soft reboot: always in jailbreak mode, or when the setting is enabled. */
fun isSoftRebootPreferred(): Boolean =
    Natives.isLateLoadMode || ksuApp.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .booleanPref(KEY_USE_SOFT_REBOOT, false)

class SettingsRepositoryImpl : SettingsRepository {

    private companion object {
        private const val INTENT_TOKEN_KEY = "intent_token"
        private val secureRandom = SecureRandom()
    }

    private val prefs by lazy {
        ksuApp.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
    }

    
    override var appLanguage: String
        get() = LocaleHelper.getCurrentLanguage(ksuApp)
        set(value) = LocaleHelper.setLanguage(ksuApp, value)

    override var checkModuleUpdate: Boolean
        get() = prefs.booleanPref("module_check_update", true)
        set(value) = prefs.edit { putBoolean("module_check_update", value) }

    override var checkUpdate: Boolean
        get() = prefs.booleanPref("manager_check_update", true)
        set(value) = prefs.edit { putBoolean("manager_check_update", value) }

    override var alternativeIcon : Boolean
        get() = prefs.booleanPref("use_alt_icon", false)
        set(value) = prefs.edit { putBoolean("use_alt_icon", value)}

    override var themeMode: Int
        get() = prefs.intPref("color_mode", 0)
        set(value) = prefs.edit { putInt("color_mode", value) }

    
    override var keyColor: Int
        get() = prefs.intPref("key_color", 0)
        set(value) = prefs.edit { putInt("key_color", value) }

    override var colorStyle: String
        get() = prefs.stringPref("color_style", PaletteStyle.TonalSpot.name) ?: PaletteStyle.TonalSpot.name
        set(value) = prefs.edit { putString("color_style", value) }

    override var colorSpec: String
        get() = prefs.stringPref("color_spec", ColorSpec.SpecVersion.SPEC_2025.name) ?: ColorSpec.SpecVersion.SPEC_2025.name
        set(value) = prefs.edit { putString("color_spec", value) }

    override var colorContrast: String
        get() = prefs.stringPref("color_contrast", ColorContrast.STANDARD.name) ?: ColorContrast.STANDARD.name
        set(value) = prefs.edit { putString("color_contrast", value) }

    override var statsTopLayout: String
        get() = prefs.stringPref("stats_top_layout", STATS_TOP_LIST) ?: STATS_TOP_LIST
        set(value) = prefs.edit { putString("stats_top_layout", value) }

    override var enablePredictiveBack: Boolean
        get() = prefs.booleanPref("enable_predictive_back", false)
        set(value) = prefs.edit { putBoolean("enable_predictive_back", value) }

    override var enableSwipeDismiss: Boolean
        get() = prefs.booleanPref("enable_swipe_dismiss", true)
        set(value) = prefs.edit { putBoolean("enable_swipe_dismiss", value) }

    override var pagerInterceptionMode: Int
        get() = prefs.intPref("pager_interception_mode", 1)
        set(value) = prefs.edit { putInt("pager_interception_mode", value.coerceIn(0, 2)) }

    
    override var enableFloatingBottomBar: Boolean
        get() = prefs.booleanPref("enable_floating_bottom_bar", false)
        set(value) = prefs.edit { putBoolean("enable_floating_bottom_bar", value) }

    
    override var enableNavigationBadge: Boolean
        get() = prefs.booleanPref("enable_navigation_badge", true)
        set(value) = prefs.edit { putBoolean("enable_navigation_badge", value) }

    override var navigationRailExpanded: Boolean
        get() = prefs.booleanPref("nav_rail_expanded", false)
        set(value) = prefs.edit { putBoolean("nav_rail_expanded", value) }

    override var homeLayoutStyle: String
        get() = prefs.stringPref(KEY_HOME_LAYOUT, HOME_LAYOUT_FALLBACK) ?: HOME_LAYOUT_FALLBACK
        set(value) = prefs.edit { putString(KEY_HOME_LAYOUT, value) }

    override var pageScale: Float
        get() = prefs.floatPref("page_scale", 1.0f)
        set(value) = prefs.edit { putFloat("page_scale", value) }

    override var moduleDescriptionMaxLines: Int
        get() = prefs.intPref("module_description_max_lines", 4)
        set(value) = prefs.edit { putInt("module_description_max_lines", value) }

    override var enableWebDebugging: Boolean
        get() = prefs.booleanPref("enable_web_debugging", false)
        set(value) = prefs.edit { putBoolean("enable_web_debugging", value) }

    override var moduleSortEnabledFirst: Boolean
        get() = prefs.booleanPref("module_sort_enabled_first", false)
        set(value) = prefs.edit { putBoolean("module_sort_enabled_first", value) }

    override var moduleSortActionFirst: Boolean
        get() = prefs.booleanPref("module_sort_action_first", false)
        set(value) = prefs.edit { putBoolean("module_sort_action_first", value) }

    override var moduleRepoSortOrder: Int
        get() = prefs.intPref("module_repo_sort_order", RepoSort.UPDATED.ordinal)
        set(value) = prefs.edit { putInt("module_repo_sort_order", value) }

    override var superuserShowSystemApps: Boolean
        get() = prefs.booleanPref("show_system_apps", false)
        set(value) = prefs.edit { putBoolean("show_system_apps", value) }

    override var superuserShowOnlyPrimaryUserApps: Boolean
        get() = prefs.booleanPref("show_only_primary_user_apps", false)
        set(value) = prefs.edit { putBoolean("show_only_primary_user_apps", value) }

    override var superuserSortOption: Int
        get() = prefs.intPref("superuser_sort_option", 0)
        set(value) = prefs.edit { putInt("superuser_sort_option", value) }

    override var suLogFilters: Set<String>?
        get() = prefs.getStringSet("sulog_filters", null)?.toSet()
        set(filters) = prefs.edit { putStringSet("sulog_filters", filters) }

    override var showFullStatus: Boolean
        get() = prefs.booleanPref("show_fingerprint", true)
        set(value) = prefs.edit { putBoolean("show_fingerprint", value) }

    override var autoJailbreak: Boolean
        get() = prefs.booleanPref("auto_jailbreak", false)
        set(value) {
            runCatching {
                ksuApp.packageManager.setComponentEnabledSetting(
                    ComponentName(ksuApp, BootCompletedReceiver::class.java),
                    if (value) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }.onFailure {
                Log.e("Settings", "failed to change boot receiver state to $value", it)
            }
            prefs.edit {
                putBoolean("auto_jailbreak", value)
            }
        }

    override var useSoftReboot: Boolean
        get() = prefs.booleanPref(KEY_USE_SOFT_REBOOT, false)
        set(value) = prefs.edit { putBoolean(KEY_USE_SOFT_REBOOT, value) }

    override val intentToken: String
        get() {
        val existing = prefs.stringPref(INTENT_TOKEN_KEY, null)
        if (!existing.isNullOrBlank()) return existing
        val token = ByteArray(32).also(secureRandom::nextBytes)
            .joinToString(separator = "") { "%02x".format(it) }
        prefs.edit { putString(INTENT_TOKEN_KEY, token) }
        return token
    }

    override suspend fun getSuCompatStatus(): String = getFeatureStatus("su_compat")

    override suspend fun getSuCompatPersistValue(): Long? = getFeaturePersistValue("su_compat")

    override fun isSuEnabled(): Boolean = Natives.isSuEnabled()

    override fun setSuEnabled(enabled: Boolean): Boolean = Natives.setSuEnabled(enabled)

    override fun setSuCompatModePref(mode: Int) = prefs.edit { putInt("su_compat_mode", mode) }

    override fun getSuCompatModePref(): Int = prefs.intPref("su_compat_mode", 0)

    override suspend fun getKernelUmountStatus(): String = getFeatureStatus("kernel_umount")

    override fun isKernelUmountEnabled(): Boolean = Natives.isKernelUmountEnabled()

    override fun setKernelUmountEnabled(enabled: Boolean): Boolean = Natives.setKernelUmountEnabled(enabled)

    override suspend fun getSelinuxHideStatus(): String = getFeatureStatus("selinux_hide")

    override fun isSelinuxHideEnabled(): Boolean = Natives.isSelinuxHideEnabled()

    override fun setSelinuxHideEnabled(enabled: Boolean): Int = Natives.setSelinuxHideEnabled(enabled)

    override suspend fun getSulogStatus(): String = getFeatureStatus("sulog")

    override suspend fun getSulogPersistValue(): Long? = getFeaturePersistValue("sulog")

    override fun setSulogEnabled(enabled: Boolean): Boolean = execKsud("feature set sulog ${if (enabled) 1 else 0}", true)

    override suspend fun getAdbRootStatus(): String = getFeatureStatus("adb_root")

    override suspend fun getAdbRootPersistValue(): Long? = getFeaturePersistValue("adb_root")

    override fun setAdbRootEnabled(enabled: Boolean): Boolean =
        if (execKsud("feature set adb_root ${if (enabled) 1 else 0}", true)) {
            ShellUtils.fastCmd("setprop ctl.restart adbd")
            true
        } else {
            false
        }

    override fun isDefaultUmountModules(): Boolean = Natives.isDefaultUmountModules()

    override fun setDefaultUmountModules(enabled: Boolean): Boolean = Natives.setDefaultUmountModules(enabled)

    override fun isLkmMode(): Boolean = Natives.isLkmMode

    override fun execKsudFeatureSave() {
        execKsud("feature save", true)
    }
}
