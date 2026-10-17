package com.sukisu.ultra.data.repository

interface SettingsRepository {
    var appLanguage: String
    var checkModuleUpdate: Boolean
    var checkUpdate: Boolean
    var onboardingDone: Boolean
    var alternativeIcon : Boolean
    var themeMode: Int
    var keyColor: Int
    var colorStyle: String
    var colorSpec: String
    /** Colour contrast level, by name: STANDARD, MEDIUM or HIGH. */
    var colorContrast: String
    var enablePredictiveBack: Boolean
    var enableSwipeDismiss: Boolean
    var pagerInterceptionMode: Int
    var enableFloatingBottomBar: Boolean
    var enableNavigationBadge: Boolean
    var navigationRailExpanded: Boolean
    /** Landing layout of the Home tab: "circle", "default", "focus" or "dashboard_ui". */
    var homeLayoutStyle: String
    /** How the Grid layout's status cards are arranged: "list" (stacked) or "grid" (big + two). */
    var statsTopLayout: String
    var pageScale: Float
    var moduleDescriptionMaxLines: Int
    var enableWebDebugging: Boolean
    var moduleSortEnabledFirst: Boolean
    var moduleSortActionFirst: Boolean
    var moduleRepoSortOrder: Int
    var superuserShowSystemApps: Boolean
    var superuserShowOnlyPrimaryUserApps: Boolean
    var superuserSortOption: Int
    var suLogFilters: Set<String>?
    var showFullStatus: Boolean
    var autoJailbreak: Boolean
    var useSoftReboot: Boolean
    val intentToken: String

    suspend fun getSuCompatStatus(): String
    suspend fun getSuCompatPersistValue(): Long?
    fun isSuEnabled(): Boolean
    fun setSuEnabled(enabled: Boolean): Boolean
    fun setSuCompatModePref(mode: Int)
    fun getSuCompatModePref(): Int

    suspend fun getKernelUmountStatus(): String
    fun isKernelUmountEnabled(): Boolean
    fun setKernelUmountEnabled(enabled: Boolean): Boolean

    suspend fun getSelinuxHideStatus(): String
    fun isSelinuxHideEnabled(): Boolean
    fun setSelinuxHideEnabled(enabled: Boolean): Int

    suspend fun getSulogStatus(): String
    suspend fun getSulogPersistValue(): Long?
    fun setSulogEnabled(enabled: Boolean): Boolean

    suspend fun getAdbRootStatus(): String
    suspend fun getAdbRootPersistValue(): Long?
    fun setAdbRootEnabled(enabled: Boolean): Boolean

    fun isDefaultUmountModules(): Boolean
    fun setDefaultUmountModules(enabled: Boolean): Boolean

    fun isLkmMode(): Boolean

    fun execKsudFeatureSave()
}
