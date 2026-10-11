package com.sukisu.ultra.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 背景配置管理类 - 从 FolkPatch 移植
 */
object BackgroundConfig {
    // State
    var customBackgroundUri: String? by mutableStateOf(null)
        private set
    var isCustomBackgroundEnabled: Boolean by mutableStateOf(false)
        private set
    var customBackgroundOpacity: Float by mutableStateOf(0.5f)
        private set
    var customBackgroundBlur: Float by mutableStateOf(0.2f)
        private set
    var customBackgroundDim: Float by mutableStateOf(0.0f)
        private set
    var isDualBackgroundDimEnabled: Boolean by mutableStateOf(true)
        private set
    var customBackgroundDayDim: Float by mutableStateOf(0.0f)
        private set
    var customBackgroundNightDim: Float by mutableStateOf(0.5f)
        private set

    // Video Background
    var videoBackgroundUri: String? by mutableStateOf(null)
        private set
    var isVideoBackgroundEnabled: Boolean by mutableStateOf(false)
        private set
    var videoVolume: Float by mutableStateOf(0f)
        private set

    // Focus 布局的主卡片壁纸（该布局只有一张主卡片，所以是单数）
    var focusCardBgUri: String? by mutableStateOf(null)
        private set
    var isFocusCardBackgroundEnabled: Boolean by mutableStateOf(false)
        private set
    var focusCardBgDim: Float by mutableStateOf(0.3f)
        private set
    var isFocusCardDualDimEnabled: Boolean by mutableStateOf(false)
        private set
    var focusCardBgDayDim: Float by mutableStateOf(0.3f)
        private set
    var focusCardBgNightDim: Float by mutableStateOf(0.3f)
        private set
    var focusCardBgOpacity: Float by mutableStateOf(1f)
        private set
    var isFocusCardDualOpacityEnabled: Boolean by mutableStateOf(false)
        private set
    var focusCardBgDayOpacity: Float by mutableStateOf(1f)
        private set
    var focusCardBgNightOpacity: Float by mutableStateOf(1f)
        private set

    // Grid 布局主卡片的独立壁纸。
    //
    // 与 focus card 分开，是因为两者在 FolkPatch 里就是两套设置：Grid 的主卡片可以单独换图，
    // 而 focus card 的图服务于 Focus 布局。合并成一份会让其中一个布局被另一个的图覆盖。
    var gridWorkingCardBgUri: String? by mutableStateOf(null)
        private set
    var isGridWorkingCardBackgroundEnabled: Boolean by mutableStateOf(false)
        private set
    var isGridWorkingCardDualOpacityEnabled: Boolean by mutableStateOf(false)
        private set
    var gridWorkingCardBgOpacity: Float by mutableStateOf(1f)
        private set
    var gridWorkingCardBgDayOpacity: Float by mutableStateOf(1f)
        private set
    var gridWorkingCardBgNightOpacity: Float by mutableStateOf(1f)
        private set
    var gridWorkingCardBgDim: Float by mutableStateOf(0f)
        private set
    var isGridWorkingCardCheckHidden: Boolean by mutableStateOf(false)
        private set
    var isGridWorkingCardTextHidden: Boolean by mutableStateOf(false)
        private set
    var isGridWorkingCardModeHidden: Boolean by mutableStateOf(false)
        private set
    var isListWorkingCardModeHidden: Boolean by mutableStateOf(false)
        private set

    // 高级标题样式：用一张图替换首页顶栏的文字标题。
    //
    // 图放在顶栏里而不是页面里，是因为标题的位置由顶栏决定；放在页面里就得自己算状态栏高度和
    // 滚动行为，两处会不一致。
    var isAdvancedTitleStyleEnabled: Boolean by mutableStateOf(false)
        private set
    var titleImageUri: String? by mutableStateOf(null)
        private set
    var titleImageDayOpacity: Float by mutableStateOf(1f)
        private set
    var titleImageNightOpacity: Float by mutableStateOf(1f)
        private set
    var titleImageDim: Float by mutableStateOf(0f)
        private set
    var titleImageOffsetX: Float by mutableStateOf(0f)
        private set

    // Dashboard 布局的四个磁贴壁纸：每个磁贴一张图，共用一套明暗/不透明度。
    var isDashboardCardBackgroundEnabled: Boolean by mutableStateOf(false)
        private set
    var dashboardCardBgDim: Float by mutableStateOf(0.3f)
        private set
    var isDashboardCardDualDimEnabled: Boolean by mutableStateOf(true)
        private set
    var dashboardCardBgDayDim: Float by mutableStateOf(0.15f)
        private set
    var dashboardCardBgNightDim: Float by mutableStateOf(0.5f)
        private set
    var dashboardCardBgOpacity: Float by mutableStateOf(1f)
        private set
    var isDashboardCardDualOpacityEnabled: Boolean by mutableStateOf(true)
        private set
    var dashboardCardBgDayOpacity: Float by mutableStateOf(1f)
        private set
    var dashboardCardBgNightOpacity: Float by mutableStateOf(1f)
        private set

    var dashboardTileWorkingBgUri: String? by mutableStateOf(null)
        private set
    var dashboardTileSelinuxBgUri: String? by mutableStateOf(null)
        private set
    var dashboardTileZygiskBgUri: String? by mutableStateOf(null)
        private set
    var dashboardTileSeccompBgUri: String? by mutableStateOf(null)
        private set

    // ---- 模块卡片横幅 ----
    /** 总开关：模块卡片顶部是否显示横幅。 */
    var isBannerEnabled: Boolean by mutableStateOf(true)
        private set

    /** 是否允许横幅落在模块自己的 `banner` 文件/属性上（关闭则只用 API/缓存）。 */
    var isFolkBannerEnabled: Boolean by mutableStateOf(true)
        private set

    /** 用自定义不透明度覆盖壁纸模式下的自动推算值。 */
    var isBannerCustomOpacityEnabled: Boolean by mutableStateOf(true)
        private set

    var bannerCustomOpacity: Float by mutableStateOf(1.0f)
        private set

    /** API 模式：按模块 ID 做种子，从随机图接口或本地目录取横幅。 */
    var isBannerApiModeEnabled: Boolean by mutableStateOf(false)
        private set

    /** 随机图 API 的 URL，或一个以 `/` 开头的本地目录路径。 */
    var bannerApiSource: String by mutableStateOf("")
        private set

    // Multi-Background Mode
    var isMultiBackgroundEnabled: Boolean by mutableStateOf(false)
        private set
    var homeBackgroundUri: String? by mutableStateOf(null)
        private set
    var kernelBackgroundUri: String? by mutableStateOf(null)
        private set
    var superuserBackgroundUri: String? by mutableStateOf(null)
        private set
    var moduleBackgroundUri: String? by mutableStateOf(null)
        private set
    var settingsBackgroundUri: String? by mutableStateOf(null)
        private set

    // 壁纸平均感知亮度（key 为文件路径），用于壁纸模式下按壁纸明暗自动适配内容配色。
    // 只保留内存副本：每次启动与主题导入后由 [BackgroundManager.refreshMissingWallpaperLuminances]
    // 重算，避免再加一套编码/解码与配置文件一起导出。
    private val wallpaperLuminanceMap = mutableStateMapOf<String, Float>()

    /** 返回指定壁纸 URI 的原始平均感知亮度（0..1），未知时返回 null。 */
    fun wallpaperLuminanceFor(uri: String?): Float? =
        luminanceKey(uri)?.let { wallpaperLuminanceMap[it] }

    internal fun setWallpaperLuminance(uri: String?, luminance: Float) {
        luminanceKey(uri)?.let { wallpaperLuminanceMap[it] = luminance }
    }

    private fun luminanceKey(uri: String?): String? {
        if (uri.isNullOrEmpty()) return null
        // 归一到文件路径：保存时的查询参数之类不影响同一张图的亮度。
        return runCatching { Uri.parse(uri).path ?: uri }.getOrDefault(uri)
    }

    /** Preference file holding every wallpaper setting; shared with [ThemeManager]. */
    const val PREFS_NAME = "background_settings"
    private const val KEY_CUSTOM_BACKGROUND_URI = "custom_background_uri"
    private const val KEY_CUSTOM_BACKGROUND_ENABLED = "custom_background_enabled"
    private const val KEY_CUSTOM_BACKGROUND_OPACITY = "custom_background_opacity"
    private const val KEY_CUSTOM_BACKGROUND_BLUR = "custom_background_blur"
    private const val KEY_CUSTOM_BACKGROUND_DIM = "custom_background_dim"
    private const val KEY_CUSTOM_BACKGROUND_DUAL_DIM_ENABLED = "custom_background_dual_dim_enabled"
    private const val KEY_CUSTOM_BACKGROUND_DAY_DIM = "custom_background_day_dim"
    private const val KEY_CUSTOM_BACKGROUND_NIGHT_DIM = "custom_background_night_dim"

    private const val KEY_VIDEO_BACKGROUND_URI = "video_background_uri"
    private const val KEY_VIDEO_BACKGROUND_ENABLED = "video_background_enabled"
    private const val KEY_VIDEO_VOLUME = "video_volume"

    // Focus 主卡片
    private const val KEY_FOCUS_CARD_BG_URI = "focus_card_bg_uri"
    private const val KEY_FOCUS_CARD_BACKGROUND_ENABLED = "focus_card_background_enabled"
    private const val KEY_FOCUS_CARD_BG_DIM = "focus_card_bg_dim"
    private const val KEY_FOCUS_CARD_DUAL_DIM_ENABLED = "focus_card_dual_dim_enabled"
    private const val KEY_FOCUS_CARD_DAY_DIM = "focus_card_day_dim"
    private const val KEY_FOCUS_CARD_NIGHT_DIM = "focus_card_night_dim"
    private const val KEY_FOCUS_CARD_OPACITY = "focus_card_opacity"
    private const val KEY_FOCUS_CARD_DUAL_OPACITY_ENABLED = "focus_card_dual_opacity_enabled"
    private const val KEY_FOCUS_CARD_DAY_OPACITY = "focus_card_day_opacity"
    private const val KEY_FOCUS_CARD_NIGHT_OPACITY = "focus_card_night_opacity"

    // Grid 布局主卡片。键名与 FolkPatch 一致，主题包才能双向搬运。
    private const val KEY_GRID_WORKING_CARD_BG_URI = "grid_working_card_background_uri"
    private const val KEY_GRID_WORKING_CARD_ENABLED = "grid_working_card_background_enabled"
    private const val KEY_GRID_WORKING_CARD_DUAL_OPACITY_ENABLED = "grid_working_card_dual_opacity_enabled"
    private const val KEY_GRID_WORKING_CARD_OPACITY = "grid_working_card_background_opacity"
    private const val KEY_GRID_WORKING_CARD_DAY_OPACITY = "grid_working_card_background_day_opacity"
    private const val KEY_GRID_WORKING_CARD_NIGHT_OPACITY = "grid_working_card_background_night_opacity"
    private const val KEY_GRID_WORKING_CARD_DIM = "grid_working_card_background_dim"
    private const val KEY_GRID_WORKING_CARD_CHECK_HIDDEN = "grid_working_card_check_hidden"
    private const val KEY_GRID_WORKING_CARD_TEXT_HIDDEN = "grid_working_card_text_hidden"
    private const val KEY_GRID_WORKING_CARD_MODE_HIDDEN = "grid_working_card_mode_hidden"
    private const val KEY_LIST_WORKING_CARD_MODE_HIDDEN = "list_working_card_mode_hidden"

    // 高级标题样式。键名沿用 FolkPatch，主题包才能双向搬运。
    private const val KEY_ADVANCED_TITLE_STYLE_ENABLED = "advanced_title_style_enabled"
    private const val KEY_TITLE_IMAGE_URI = "title_image_uri"
    private const val KEY_TITLE_IMAGE_DAY_OPACITY = "title_image_day_opacity"
    private const val KEY_TITLE_IMAGE_NIGHT_OPACITY = "title_image_night_opacity"
    private const val KEY_TITLE_IMAGE_DIM = "title_image_dim"
    private const val KEY_TITLE_IMAGE_OFFSET_X = "title_image_offset_x"

    // Dashboard 磁贴
    private const val KEY_DASHBOARD_CARD_BACKGROUND_ENABLED = "dashboard_card_background_enabled"
    private const val KEY_DASHBOARD_CARD_BG_DIM = "dashboard_card_bg_dim"
    private const val KEY_DASHBOARD_CARD_DUAL_DIM_ENABLED = "dashboard_card_dual_dim_enabled"
    private const val KEY_DASHBOARD_CARD_DAY_DIM = "dashboard_card_day_dim"
    private const val KEY_DASHBOARD_CARD_NIGHT_DIM = "dashboard_card_night_dim"
    private const val KEY_DASHBOARD_CARD_OPACITY = "dashboard_card_opacity"
    private const val KEY_DASHBOARD_CARD_DUAL_OPACITY_ENABLED = "dashboard_card_dual_opacity_enabled"
    private const val KEY_DASHBOARD_CARD_DAY_OPACITY = "dashboard_card_day_opacity"
    private const val KEY_DASHBOARD_CARD_NIGHT_OPACITY = "dashboard_card_night_opacity"

    /** Dashboard 布局四个磁贴的稳定标识，同时是它们的偏好键后缀。 */
    const val DASHBOARD_TILE_WORKING = "working"
    const val DASHBOARD_TILE_SELINUX = "selinux"
    const val DASHBOARD_TILE_ZYGISK = "zygisk"
    const val DASHBOARD_TILE_SECCOMP = "seccomp"
    val DASHBOARD_TILES = listOf(DASHBOARD_TILE_WORKING, DASHBOARD_TILE_SELINUX, DASHBOARD_TILE_ZYGISK, DASHBOARD_TILE_SECCOMP)

    private fun dashboardTileKey(tile: String) = "dashboard_tile_bg_uri_$tile"

    // ---- 模块卡片横幅 ----
    private const val KEY_BANNER_ENABLED = "banner_enabled"
    private const val KEY_FOLK_BANNER_ENABLED = "folk_banner_enabled"
    private const val KEY_BANNER_CUSTOM_OPACITY_ENABLED = "banner_custom_opacity_enabled"
    private const val KEY_BANNER_CUSTOM_OPACITY = "banner_custom_opacity"
    private const val KEY_BANNER_API_MODE_ENABLED = "banner_api_mode_enabled"
    private const val KEY_BANNER_API_SOURCE = "banner_api_source"
    
    private const val KEY_MULTI_BACKGROUND_ENABLED = "multi_background_enabled"
    private const val KEY_HOME_BACKGROUND_URI = "home_background_uri"
    private const val KEY_KERNEL_BACKGROUND_URI = "kernel_background_uri"
    private const val KEY_SUPERUSER_BACKGROUND_URI = "superuser_background_uri"
    private const val KEY_MODULE_BACKGROUND_URI = "module_background_uri"
    private const val KEY_SETTINGS_BACKGROUND_URI = "settings_background_uri"

    private const val TAG = "BackgroundConfig"
    
    /**
     * 更新自定义背景URI
     */
    fun updateCustomBackgroundUri(uri: String?) {
        customBackgroundUri = uri
        isCustomBackgroundEnabled = uri != null
    }
    
    /**
     * 启用/禁用自定义背景
     */
    fun setCustomBackgroundEnabledState(enabled: Boolean) {
        isCustomBackgroundEnabled = enabled
    }

    /**
     * 设置自定义背景不透明度
     */
    fun setCustomBackgroundOpacityValue(opacity: Float) {
        customBackgroundOpacity = opacity
    }

    /**
     * 设置自定义背景模糊度
     */
    fun setCustomBackgroundBlurValue(blur: Float) {
        customBackgroundBlur = blur
    }

    /**
     * 设置自定义背景暗度
     */
    fun setCustomBackgroundDimValue(dim: Float) {
        customBackgroundDim = dim
    }

    fun setDualBackgroundDimEnabledState(enabled: Boolean) {
        isDualBackgroundDimEnabled = enabled
    }

    fun setCustomBackgroundDayDimValue(dim: Float) {
        customBackgroundDayDim = dim
    }

    fun setCustomBackgroundNightDimValue(dim: Float) {
        customBackgroundNightDim = dim
    }

    fun updateVideoBackgroundUri(uri: String?) {
        videoBackgroundUri = uri
    }

    fun setVideoBackgroundEnabledState(enabled: Boolean) {
        isVideoBackgroundEnabled = enabled
    }

    fun setVideoVolumeValue(volume: Float) {
        videoVolume = volume
    }

    // ---- Focus 主卡片 ----

    fun updateFocusCardBgUri(uri: String?) {
        focusCardBgUri = uri
    }

    fun setFocusCardBackgroundEnabledState(enabled: Boolean) {
        isFocusCardBackgroundEnabled = enabled
    }

    fun setFocusCardBgDimValue(value: Float) {
        focusCardBgDim = value
    }

    fun setFocusCardDualDimEnabledState(enabled: Boolean) {
        isFocusCardDualDimEnabled = enabled
    }

    fun setFocusCardBgDayDimValue(value: Float) {
        focusCardBgDayDim = value
    }

    fun setFocusCardBgNightDimValue(value: Float) {
        focusCardBgNightDim = value
    }

    fun setFocusCardBgOpacityValue(value: Float) {
        focusCardBgOpacity = value
    }

    fun setFocusCardDualOpacityEnabledState(enabled: Boolean) {
        isFocusCardDualOpacityEnabled = enabled
    }

    fun setFocusCardBgDayOpacityValue(value: Float) {
        focusCardBgDayOpacity = value
    }

    fun setFocusCardBgNightOpacityValue(value: Float) {
        focusCardBgNightOpacity = value
    }

    /** 双切开启时按当前主题取值，否则用统一值。 */
    fun getEffectiveFocusCardBgDim(isDarkTheme: Boolean): Float =
        if (isFocusCardDualDimEnabled) {
            if (isDarkTheme) focusCardBgNightDim else focusCardBgDayDim
        } else {
            focusCardBgDim
        }

    fun getEffectiveFocusCardBgOpacity(isDarkTheme: Boolean): Float =
        if (isFocusCardDualOpacityEnabled) {
            if (isDarkTheme) focusCardBgNightOpacity else focusCardBgDayOpacity
        } else {
            focusCardBgOpacity
        }

    // ---- Grid 布局主卡片 ----

    fun updateGridWorkingCardBgUri(uri: String?) {
        gridWorkingCardBgUri = uri
    }

    fun setGridWorkingCardBackgroundEnabledState(enabled: Boolean) {
        isGridWorkingCardBackgroundEnabled = enabled
    }

    fun setGridWorkingCardDualOpacityEnabledState(enabled: Boolean) {
        isGridWorkingCardDualOpacityEnabled = enabled
    }

    fun setGridWorkingCardBgOpacityValue(value: Float) {
        gridWorkingCardBgOpacity = value
    }

    fun setGridWorkingCardBgDayOpacityValue(value: Float) {
        gridWorkingCardBgDayOpacity = value
    }

    fun setGridWorkingCardBgNightOpacityValue(value: Float) {
        gridWorkingCardBgNightOpacity = value
    }

    fun setGridWorkingCardBgDimValue(value: Float) {
        gridWorkingCardBgDim = value
    }

    fun setGridWorkingCardCheckHiddenState(hidden: Boolean) {
        isGridWorkingCardCheckHidden = hidden
    }

    fun setGridWorkingCardTextHiddenState(hidden: Boolean) {
        isGridWorkingCardTextHidden = hidden
    }

    fun setGridWorkingCardModeHiddenState(hidden: Boolean) {
        isGridWorkingCardModeHidden = hidden
    }

    fun setListWorkingCardModeHiddenState(hidden: Boolean) {
        isListWorkingCardModeHidden = hidden
    }

    /** 双切开启时按当前主题取值，否则用统一值。 */
    fun getEffectiveGridWorkingCardBgOpacity(isDarkTheme: Boolean): Float =
        if (isGridWorkingCardDualOpacityEnabled) {
            if (isDarkTheme) gridWorkingCardBgNightOpacity else gridWorkingCardBgDayOpacity
        } else {
            gridWorkingCardBgOpacity
        }

    // ---- 高级标题样式 ----

    fun updateTitleImageUri(uri: String?) {
        titleImageUri = uri
    }

    fun setAdvancedTitleStyleEnabledState(enabled: Boolean) {
        isAdvancedTitleStyleEnabled = enabled
    }

    fun setTitleImageDayOpacityValue(value: Float) {
        titleImageDayOpacity = value
    }

    fun setTitleImageNightOpacityValue(value: Float) {
        titleImageNightOpacity = value
    }

    fun setTitleImageDimValue(value: Float) {
        titleImageDim = value
    }

    fun setTitleImageOffsetXValue(value: Float) {
        titleImageOffsetX = value
    }

    /** 按当前明暗主题取标题图的不透明度。 */
    fun getEffectiveTitleImageOpacity(isDarkTheme: Boolean): Float =
        if (isDarkTheme) titleImageNightOpacity else titleImageDayOpacity

    /** 标题图当前是否可用：开关打开且图确实存在。 */
    fun isTitleImageActive(): Boolean =
        isAdvancedTitleStyleEnabled && !titleImageUri.isNullOrEmpty()

    // ---- Dashboard 磁贴 ----

    fun getDashboardTileBgUri(tile: String): String? = when (tile) {
        DASHBOARD_TILE_WORKING -> dashboardTileWorkingBgUri
        DASHBOARD_TILE_SELINUX -> dashboardTileSelinuxBgUri
        DASHBOARD_TILE_ZYGISK -> dashboardTileZygiskBgUri
        DASHBOARD_TILE_SECCOMP -> dashboardTileSeccompBgUri
        else -> null
    }

    fun updateDashboardTileBgUri(tile: String, uri: String?) {
        when (tile) {
            DASHBOARD_TILE_WORKING -> dashboardTileWorkingBgUri = uri
            DASHBOARD_TILE_SELINUX -> dashboardTileSelinuxBgUri = uri
            DASHBOARD_TILE_ZYGISK -> dashboardTileZygiskBgUri = uri
            DASHBOARD_TILE_SECCOMP -> dashboardTileSeccompBgUri = uri
        }
    }

    fun setDashboardCardBackgroundEnabledState(enabled: Boolean) {
        isDashboardCardBackgroundEnabled = enabled
    }

    fun setDashboardCardBgDimValue(value: Float) {
        dashboardCardBgDim = value
    }

    fun setDashboardCardDualDimEnabledState(enabled: Boolean) {
        isDashboardCardDualDimEnabled = enabled
    }

    fun setDashboardCardBgDayDimValue(value: Float) {
        dashboardCardBgDayDim = value
    }

    fun setDashboardCardBgNightDimValue(value: Float) {
        dashboardCardBgNightDim = value
    }

    fun setDashboardCardBgOpacityValue(value: Float) {
        dashboardCardBgOpacity = value
    }

    fun setDashboardCardDualOpacityEnabledState(enabled: Boolean) {
        isDashboardCardDualOpacityEnabled = enabled
    }

    fun setDashboardCardBgDayOpacityValue(value: Float) {
        dashboardCardBgDayOpacity = value
    }

    fun setDashboardCardBgNightOpacityValue(value: Float) {
        dashboardCardBgNightOpacity = value
    }

    fun getEffectiveDashboardCardBgDim(isDarkTheme: Boolean): Float =
        if (isDashboardCardDualDimEnabled) {
            if (isDarkTheme) dashboardCardBgNightDim else dashboardCardBgDayDim
        } else {
            dashboardCardBgDim
        }

    fun getEffectiveDashboardCardBgOpacity(isDarkTheme: Boolean): Float =
        if (isDashboardCardDualOpacityEnabled) {
            if (isDarkTheme) dashboardCardBgNightOpacity else dashboardCardBgDayOpacity
        } else {
            dashboardCardBgOpacity
        }

    // ---- 模块卡片横幅 ----

    fun setBannerEnabledState(enabled: Boolean) {
        isBannerEnabled = enabled
    }

    fun setFolkBannerEnabledState(enabled: Boolean) {
        isFolkBannerEnabled = enabled
    }

    fun setBannerCustomOpacityEnabledState(enabled: Boolean) {
        isBannerCustomOpacityEnabled = enabled
    }

    fun setBannerCustomOpacityValue(opacity: Float) {
        bannerCustomOpacity = opacity
    }

    fun setBannerApiModeEnabledState(enabled: Boolean) {
        isBannerApiModeEnabled = enabled
    }

    fun setBannerApiSourceValue(source: String) {
        bannerApiSource = source
    }

    fun getEffectiveBannerApiSource(): String = bannerApiSource

    /**
     * 横幅实际显示的不透明度。
     *
     * 没有自定义值时按场景推算：壁纸模式下页面本身被压暗过，横幅要更明显一点；普通模式
     * 下只做一层很淡的装饰，不能盖住卡片上的文字。
     */
    fun getEffectiveBannerOpacity(isWallpaperMode: Boolean, wallpaperOpacity: Float): Float =
        if (isBannerCustomOpacityEnabled) {
            bannerCustomOpacity
        } else if (isWallpaperMode) {
            (0.35f + (wallpaperOpacity.coerceAtLeast(0.35f) - 0.2f) * 0.5f).coerceIn(0.25f, 0.6f)
        } else {
            0.18f
        }

    fun getEffectiveBackgroundDim(isDarkTheme: Boolean): Float {
        return if (isDualBackgroundDimEnabled) {
            if (isDarkTheme) customBackgroundNightDim else customBackgroundDayDim
        } else {
            customBackgroundDim
        }
    }

    // Multi-Background Setters
    fun setMultiBackgroundEnabledState(enabled: Boolean) {
        isMultiBackgroundEnabled = enabled
    }

    fun updateHomeBackgroundUri(uri: String?) {
        homeBackgroundUri = uri
    }

    fun updateKernelBackgroundUri(uri: String?) {
        kernelBackgroundUri = uri
    }

    fun updateSuperuserBackgroundUri(uri: String?) {
        superuserBackgroundUri = uri
    }

    fun updateModuleBackgroundUri(uri: String?) {
        moduleBackgroundUri = uri
    }

    fun updateSettingsBackgroundUri(uri: String?) {
        settingsBackgroundUri = uri
    }
    
    /**
     * 保存配置到SharedPreferences
     */
    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_CUSTOM_BACKGROUND_URI, customBackgroundUri)
            putBoolean(KEY_CUSTOM_BACKGROUND_ENABLED, isCustomBackgroundEnabled)
            putFloat(KEY_CUSTOM_BACKGROUND_OPACITY, customBackgroundOpacity)
            putFloat(KEY_CUSTOM_BACKGROUND_BLUR, customBackgroundBlur)
            putFloat(KEY_CUSTOM_BACKGROUND_DIM, customBackgroundDim)
            putBoolean(KEY_CUSTOM_BACKGROUND_DUAL_DIM_ENABLED, isDualBackgroundDimEnabled)
            putFloat(KEY_CUSTOM_BACKGROUND_DAY_DIM, customBackgroundDayDim)
            putFloat(KEY_CUSTOM_BACKGROUND_NIGHT_DIM, customBackgroundNightDim)

            putBoolean(KEY_MULTI_BACKGROUND_ENABLED, isMultiBackgroundEnabled)
            putString(KEY_HOME_BACKGROUND_URI, homeBackgroundUri)
            putString(KEY_KERNEL_BACKGROUND_URI, kernelBackgroundUri)
            putString(KEY_SUPERUSER_BACKGROUND_URI, superuserBackgroundUri)
            putString(KEY_MODULE_BACKGROUND_URI, moduleBackgroundUri)
            putString(KEY_SETTINGS_BACKGROUND_URI, settingsBackgroundUri)

            putString(KEY_VIDEO_BACKGROUND_URI, videoBackgroundUri)
            putBoolean(KEY_VIDEO_BACKGROUND_ENABLED, isVideoBackgroundEnabled)
            putFloat(KEY_VIDEO_VOLUME, videoVolume)

            putString(KEY_FOCUS_CARD_BG_URI, focusCardBgUri)
            putBoolean(KEY_FOCUS_CARD_BACKGROUND_ENABLED, isFocusCardBackgroundEnabled)
            putFloat(KEY_FOCUS_CARD_BG_DIM, focusCardBgDim)
            putBoolean(KEY_FOCUS_CARD_DUAL_DIM_ENABLED, isFocusCardDualDimEnabled)
            putFloat(KEY_FOCUS_CARD_DAY_DIM, focusCardBgDayDim)
            putFloat(KEY_FOCUS_CARD_NIGHT_DIM, focusCardBgNightDim)
            putFloat(KEY_FOCUS_CARD_OPACITY, focusCardBgOpacity)
            putBoolean(KEY_FOCUS_CARD_DUAL_OPACITY_ENABLED, isFocusCardDualOpacityEnabled)
            putFloat(KEY_FOCUS_CARD_DAY_OPACITY, focusCardBgDayOpacity)
            putFloat(KEY_FOCUS_CARD_NIGHT_OPACITY, focusCardBgNightOpacity)

            putString(KEY_GRID_WORKING_CARD_BG_URI, gridWorkingCardBgUri)
            putBoolean(KEY_GRID_WORKING_CARD_ENABLED, isGridWorkingCardBackgroundEnabled)
            putBoolean(KEY_GRID_WORKING_CARD_DUAL_OPACITY_ENABLED, isGridWorkingCardDualOpacityEnabled)
            putFloat(KEY_GRID_WORKING_CARD_OPACITY, gridWorkingCardBgOpacity)
            putFloat(KEY_GRID_WORKING_CARD_DAY_OPACITY, gridWorkingCardBgDayOpacity)
            putFloat(KEY_GRID_WORKING_CARD_NIGHT_OPACITY, gridWorkingCardBgNightOpacity)
            putFloat(KEY_GRID_WORKING_CARD_DIM, gridWorkingCardBgDim)
            putBoolean(KEY_GRID_WORKING_CARD_CHECK_HIDDEN, isGridWorkingCardCheckHidden)
            putBoolean(KEY_GRID_WORKING_CARD_TEXT_HIDDEN, isGridWorkingCardTextHidden)
            putBoolean(KEY_GRID_WORKING_CARD_MODE_HIDDEN, isGridWorkingCardModeHidden)
            putBoolean(KEY_LIST_WORKING_CARD_MODE_HIDDEN, isListWorkingCardModeHidden)

            putBoolean(KEY_ADVANCED_TITLE_STYLE_ENABLED, isAdvancedTitleStyleEnabled)
            putString(KEY_TITLE_IMAGE_URI, titleImageUri)
            putFloat(KEY_TITLE_IMAGE_DAY_OPACITY, titleImageDayOpacity)
            putFloat(KEY_TITLE_IMAGE_NIGHT_OPACITY, titleImageNightOpacity)
            putFloat(KEY_TITLE_IMAGE_DIM, titleImageDim)
            putFloat(KEY_TITLE_IMAGE_OFFSET_X, titleImageOffsetX)

            putBoolean(KEY_DASHBOARD_CARD_BACKGROUND_ENABLED, isDashboardCardBackgroundEnabled)
            putFloat(KEY_DASHBOARD_CARD_BG_DIM, dashboardCardBgDim)
            putBoolean(KEY_DASHBOARD_CARD_DUAL_DIM_ENABLED, isDashboardCardDualDimEnabled)
            putFloat(KEY_DASHBOARD_CARD_DAY_DIM, dashboardCardBgDayDim)
            putFloat(KEY_DASHBOARD_CARD_NIGHT_DIM, dashboardCardBgNightDim)
            putFloat(KEY_DASHBOARD_CARD_OPACITY, dashboardCardBgOpacity)
            putBoolean(KEY_DASHBOARD_CARD_DUAL_OPACITY_ENABLED, isDashboardCardDualOpacityEnabled)
            putFloat(KEY_DASHBOARD_CARD_DAY_OPACITY, dashboardCardBgDayOpacity)
            putFloat(KEY_DASHBOARD_CARD_NIGHT_OPACITY, dashboardCardBgNightOpacity)
            putString(dashboardTileKey(DASHBOARD_TILE_WORKING), dashboardTileWorkingBgUri)
            putString(dashboardTileKey(DASHBOARD_TILE_SELINUX), dashboardTileSelinuxBgUri)
            putString(dashboardTileKey(DASHBOARD_TILE_ZYGISK), dashboardTileZygiskBgUri)
            putString(dashboardTileKey(DASHBOARD_TILE_SECCOMP), dashboardTileSeccompBgUri)

            putBoolean(KEY_BANNER_ENABLED, isBannerEnabled)
            putBoolean(KEY_FOLK_BANNER_ENABLED, isFolkBannerEnabled)
            putBoolean(KEY_BANNER_CUSTOM_OPACITY_ENABLED, isBannerCustomOpacityEnabled)
            putFloat(KEY_BANNER_CUSTOM_OPACITY, bannerCustomOpacity)
            putBoolean(KEY_BANNER_API_MODE_ENABLED, isBannerApiModeEnabled)
            putString(KEY_BANNER_API_SOURCE, bannerApiSource)

            apply()
        }
    }
    
    /**
     * 从SharedPreferences加载配置
     */
    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uri = prefs.stringPref(KEY_CUSTOM_BACKGROUND_URI, null)
        val enabled = prefs.booleanPref(KEY_CUSTOM_BACKGROUND_ENABLED, false)
        val opacity = prefs.floatPref(KEY_CUSTOM_BACKGROUND_OPACITY, 0.5f)
        val blur = prefs.floatPref(KEY_CUSTOM_BACKGROUND_BLUR, 0.2f)
        val dim = prefs.floatPref(KEY_CUSTOM_BACKGROUND_DIM, 0.0f)
        val dualDimEnabled = prefs.booleanPref(KEY_CUSTOM_BACKGROUND_DUAL_DIM_ENABLED, true)
        val dayDim = prefs.floatPref(KEY_CUSTOM_BACKGROUND_DAY_DIM, 0.0f)
        val nightDim = prefs.floatPref(KEY_CUSTOM_BACKGROUND_NIGHT_DIM, 0.5f)

        val multiEnabled = prefs.booleanPref(KEY_MULTI_BACKGROUND_ENABLED, false)
        val homeUri = prefs.stringPref(KEY_HOME_BACKGROUND_URI, null)
        val kernelUri = prefs.stringPref(KEY_KERNEL_BACKGROUND_URI, null)
        val superuserUri = prefs.stringPref(KEY_SUPERUSER_BACKGROUND_URI, null)
        val moduleUri = prefs.stringPref(KEY_MODULE_BACKGROUND_URI, null)
        val settingsUri = prefs.stringPref(KEY_SETTINGS_BACKGROUND_URI, null)

        val videoUri = prefs.stringPref(KEY_VIDEO_BACKGROUND_URI, null)
        val videoEnabled = prefs.booleanPref(KEY_VIDEO_BACKGROUND_ENABLED, false)
        val videoVolume = prefs.floatPref(KEY_VIDEO_VOLUME, 0f)

        val focusCardBg = prefs.stringPref(KEY_FOCUS_CARD_BG_URI, null)
        // 主题导入这类路径可能只带图片不带开关，默认值跟着图片走，避免图已经存在却关着。
        val focusCardEnabled = prefs.booleanPref(KEY_FOCUS_CARD_BACKGROUND_ENABLED, focusCardBg != null)
        val focusCardDim = prefs.floatPref(KEY_FOCUS_CARD_BG_DIM, 0.3f)
        val focusCardDualDim = prefs.booleanPref(KEY_FOCUS_CARD_DUAL_DIM_ENABLED, false)
        val focusCardDayDim = prefs.floatPref(KEY_FOCUS_CARD_DAY_DIM, focusCardDim)
        val focusCardNightDim = prefs.floatPref(KEY_FOCUS_CARD_NIGHT_DIM, focusCardDim)
        val focusCardOpacity = prefs.floatPref(KEY_FOCUS_CARD_OPACITY, 1f)
        val focusCardDualOpacity = prefs.booleanPref(KEY_FOCUS_CARD_DUAL_OPACITY_ENABLED, false)
        val focusCardDayOpacity = prefs.floatPref(KEY_FOCUS_CARD_DAY_OPACITY, focusCardOpacity)
        val focusCardNightOpacity = prefs.floatPref(KEY_FOCUS_CARD_NIGHT_OPACITY, focusCardOpacity)

        // Grid 主卡片。开关的兜底与 focus card 一致：有图即视为启用，避免图已存在却关着。
        val gridWorkingCardBg = prefs.stringPref(KEY_GRID_WORKING_CARD_BG_URI, null)
        val gridWorkingCardEnabled = prefs.booleanPref(KEY_GRID_WORKING_CARD_ENABLED, gridWorkingCardBg != null)
        val gridWorkingCardDualOpacity = prefs.booleanPref(KEY_GRID_WORKING_CARD_DUAL_OPACITY_ENABLED, false)
        val gridWorkingCardOpacity = prefs.floatPref(KEY_GRID_WORKING_CARD_OPACITY, 1f)
        val gridWorkingCardDayOpacity = prefs.floatPref(KEY_GRID_WORKING_CARD_DAY_OPACITY, gridWorkingCardOpacity)
        val gridWorkingCardNightOpacity = prefs.floatPref(KEY_GRID_WORKING_CARD_NIGHT_OPACITY, gridWorkingCardOpacity)
        val gridWorkingCardDim = prefs.floatPref(KEY_GRID_WORKING_CARD_DIM, 0f)
        val gridWorkingCardCheckHidden = prefs.booleanPref(KEY_GRID_WORKING_CARD_CHECK_HIDDEN, false)
        val gridWorkingCardTextHidden = prefs.booleanPref(KEY_GRID_WORKING_CARD_TEXT_HIDDEN, false)
        val gridWorkingCardModeHidden = prefs.booleanPref(KEY_GRID_WORKING_CARD_MODE_HIDDEN, false)
        val listWorkingCardModeHidden = prefs.booleanPref(KEY_LIST_WORKING_CARD_MODE_HIDDEN, false)

        // 高级标题样式。开关兜底与卡片一致：有图即视为启用。
        val titleImageUriValue = prefs.stringPref(KEY_TITLE_IMAGE_URI, null)
        val advancedTitleStyleEnabled = prefs.booleanPref(
            KEY_ADVANCED_TITLE_STYLE_ENABLED,
            titleImageUriValue != null,
        )
        val titleImageDayOpacityValue = prefs.floatPref(KEY_TITLE_IMAGE_DAY_OPACITY, 1f)
        val titleImageNightOpacityValue = prefs.floatPref(KEY_TITLE_IMAGE_NIGHT_OPACITY, 1f)
        val titleImageDimValue = prefs.floatPref(KEY_TITLE_IMAGE_DIM, 0f)
        val titleImageOffsetXValue = prefs.floatPref(KEY_TITLE_IMAGE_OFFSET_X, 0f)

        val dashWorking = prefs.stringPref(dashboardTileKey(DASHBOARD_TILE_WORKING), null)
        val dashSelinux = prefs.stringPref(dashboardTileKey(DASHBOARD_TILE_SELINUX), null)
        val dashZygisk = prefs.stringPref(dashboardTileKey(DASHBOARD_TILE_ZYGISK), null)
        val dashSeccomp = prefs.stringPref(dashboardTileKey(DASHBOARD_TILE_SECCOMP), null)
        val hasDashboardWallpaper = dashWorking != null || dashSelinux != null ||
            dashZygisk != null || dashSeccomp != null
        val dashboardEnabled = prefs.booleanPref(KEY_DASHBOARD_CARD_BACKGROUND_ENABLED, hasDashboardWallpaper)
        val dashboardDim = prefs.floatPref(KEY_DASHBOARD_CARD_BG_DIM, 0.3f)
        val dashboardDualDim = prefs.booleanPref(KEY_DASHBOARD_CARD_DUAL_DIM_ENABLED, true)
        val dashboardDayDim = prefs.floatPref(KEY_DASHBOARD_CARD_DAY_DIM, 0.15f)
        val dashboardNightDim = prefs.floatPref(KEY_DASHBOARD_CARD_NIGHT_DIM, 0.5f)
        val dashboardOpacity = prefs.floatPref(KEY_DASHBOARD_CARD_OPACITY, 1f)
        val dashboardDualOpacity = prefs.booleanPref(KEY_DASHBOARD_CARD_DUAL_OPACITY_ENABLED, true)
        val dashboardDayOpacity = prefs.floatPref(KEY_DASHBOARD_CARD_DAY_OPACITY, 1f)
        val dashboardNightOpacity = prefs.floatPref(KEY_DASHBOARD_CARD_NIGHT_OPACITY, 1f)

        val bannerEnabled = prefs.booleanPref(KEY_BANNER_ENABLED, true)
        val folkBannerEnabled = prefs.booleanPref(KEY_FOLK_BANNER_ENABLED, true)
        val bannerCustomOpacityEnabled = prefs.booleanPref(KEY_BANNER_CUSTOM_OPACITY_ENABLED, true)
        val bannerCustomOpacity = prefs.floatPref(KEY_BANNER_CUSTOM_OPACITY, 1.0f)
        val bannerApiModeEnabled = prefs.booleanPref(KEY_BANNER_API_MODE_ENABLED, false)
        val bannerApiSource = prefs.stringPref(KEY_BANNER_API_SOURCE, "") ?: ""

        Log.d(TAG, "加载背景配置: URI=$uri, enabled=$enabled, opacity=$opacity, dim=$dim")
        
        customBackgroundUri = uri
        isCustomBackgroundEnabled = enabled
        customBackgroundOpacity = opacity
        customBackgroundBlur = blur
        customBackgroundDim = dim
        isDualBackgroundDimEnabled = dualDimEnabled
        customBackgroundDayDim = dayDim
        customBackgroundNightDim = nightDim

        isMultiBackgroundEnabled = multiEnabled
        homeBackgroundUri = homeUri
        kernelBackgroundUri = kernelUri
        superuserBackgroundUri = superuserUri
        moduleBackgroundUri = moduleUri
        settingsBackgroundUri = settingsUri

        videoBackgroundUri = videoUri
        isVideoBackgroundEnabled = videoEnabled
        this.videoVolume = videoVolume

        focusCardBgUri = focusCardBg
        isFocusCardBackgroundEnabled = focusCardEnabled
        focusCardBgDim = focusCardDim
        isFocusCardDualDimEnabled = focusCardDualDim
        focusCardBgDayDim = focusCardDayDim
        focusCardBgNightDim = focusCardNightDim
        focusCardBgOpacity = focusCardOpacity
        isFocusCardDualOpacityEnabled = focusCardDualOpacity
        focusCardBgDayOpacity = focusCardDayOpacity
        focusCardBgNightOpacity = focusCardNightOpacity

        gridWorkingCardBgUri = gridWorkingCardBg
        isGridWorkingCardBackgroundEnabled = gridWorkingCardEnabled
        isGridWorkingCardDualOpacityEnabled = gridWorkingCardDualOpacity
        gridWorkingCardBgOpacity = gridWorkingCardOpacity
        gridWorkingCardBgDayOpacity = gridWorkingCardDayOpacity
        gridWorkingCardBgNightOpacity = gridWorkingCardNightOpacity
        gridWorkingCardBgDim = gridWorkingCardDim
        isGridWorkingCardCheckHidden = gridWorkingCardCheckHidden
        isGridWorkingCardTextHidden = gridWorkingCardTextHidden
        isGridWorkingCardModeHidden = gridWorkingCardModeHidden
        isListWorkingCardModeHidden = listWorkingCardModeHidden

        isAdvancedTitleStyleEnabled = advancedTitleStyleEnabled
        titleImageUri = titleImageUriValue
        titleImageDayOpacity = titleImageDayOpacityValue
        titleImageNightOpacity = titleImageNightOpacityValue
        titleImageDim = titleImageDimValue
        titleImageOffsetX = titleImageOffsetXValue

        dashboardTileWorkingBgUri = dashWorking
        dashboardTileSelinuxBgUri = dashSelinux
        dashboardTileZygiskBgUri = dashZygisk
        dashboardTileSeccompBgUri = dashSeccomp
        isDashboardCardBackgroundEnabled = dashboardEnabled
        dashboardCardBgDim = dashboardDim
        isDashboardCardDualDimEnabled = dashboardDualDim
        dashboardCardBgDayDim = dashboardDayDim
        dashboardCardBgNightDim = dashboardNightDim
        dashboardCardBgOpacity = dashboardOpacity
        isDashboardCardDualOpacityEnabled = dashboardDualOpacity
        dashboardCardBgDayOpacity = dashboardDayOpacity
        dashboardCardBgNightOpacity = dashboardNightOpacity

        isBannerEnabled = bannerEnabled
        isFolkBannerEnabled = folkBannerEnabled
        isBannerCustomOpacityEnabled = bannerCustomOpacityEnabled
        this.bannerCustomOpacity = bannerCustomOpacity
        isBannerApiModeEnabled = bannerApiModeEnabled
        this.bannerApiSource = bannerApiSource
    }
    
    /**
     * 重置配置
     */
    fun reset() {
        customBackgroundUri = null
        isCustomBackgroundEnabled = false
        customBackgroundOpacity = 0.5f
        customBackgroundBlur = 0.2f
        customBackgroundDim = 0.0f
        isDualBackgroundDimEnabled = true
        customBackgroundDayDim = 0.0f
        customBackgroundNightDim = 0.5f

        isMultiBackgroundEnabled = false
        homeBackgroundUri = null
        kernelBackgroundUri = null
        superuserBackgroundUri = null
        moduleBackgroundUri = null
        settingsBackgroundUri = null

        videoBackgroundUri = null
        isVideoBackgroundEnabled = false
        videoVolume = 0f

        focusCardBgUri = null
        isFocusCardBackgroundEnabled = false
        focusCardBgDim = 0.3f
        isFocusCardDualDimEnabled = false
        focusCardBgDayDim = 0.3f
        focusCardBgNightDim = 0.3f
        focusCardBgOpacity = 1f
        isFocusCardDualOpacityEnabled = false
        focusCardBgDayOpacity = 1f
        focusCardBgNightOpacity = 1f

        dashboardTileWorkingBgUri = null
        dashboardTileSelinuxBgUri = null
        dashboardTileZygiskBgUri = null
        dashboardTileSeccompBgUri = null
        isDashboardCardBackgroundEnabled = false
        dashboardCardBgDim = 0.3f
        isDashboardCardDualDimEnabled = true
        dashboardCardBgDayDim = 0.15f
        dashboardCardBgNightDim = 0.5f
        dashboardCardBgOpacity = 1f
        isDashboardCardDualOpacityEnabled = true
        dashboardCardBgDayOpacity = 1f
        dashboardCardBgNightOpacity = 1f

        isBannerEnabled = true
        isFolkBannerEnabled = true
        isBannerCustomOpacityEnabled = true
        bannerCustomOpacity = 1.0f
        isBannerApiModeEnabled = false
        bannerApiSource = ""
    }
}

/**
 * 背景管理器
 */
object BackgroundManager {
    private const val TAG = "BackgroundManager"
    private const val HOME_BACKGROUND_FILENAME = "background_home"
    private const val KERNEL_BACKGROUND_FILENAME = "background_kernel"
    private const val SUPERUSER_BACKGROUND_FILENAME = "background_superuser"
    private const val MODULE_BACKGROUND_FILENAME = "background_module"
    private const val SETTINGS_BACKGROUND_FILENAME = "background_settings"
    private const val VIDEO_BACKGROUND_FILENAME = "background_video"
    private const val FOCUS_CARD_BG_FILENAME = "focus_card_bg"
    private const val GRID_WORKING_CARD_BG_FILENAME = "grid_working_card_background"
    private const val TITLE_IMAGE_FILENAME = "title_image"
    private const val DASHBOARD_TILE_BG_FILENAME = "dashboard_tile_bg"

    /** Every extension a wallpaper file may have been written under, so a re-pick leaves no orphans. */
    private val KNOWN_EXTENSIONS =
        listOf(".jpg", ".png", ".gif", ".webp", ".mp4", ".webm", ".mkv", ".mov", ".avi", ".3gp")

    /**
     * 获取文件扩展名
     */
    private fun getFileExtension(context: Context, uri: Uri): String {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
            ?: return ".jpg"
        return when {
            mime.contains("gif", true) -> ".gif"
            mime.contains("png", true) -> ".png"
            mime.contains("webp", true) -> ".webp"
            mime.startsWith("video", true) ->
                android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                    ?.let { ".$it" } ?: ".mp4"
            else -> ".jpg"
        }
    }

    /**
     * 获取背景文件
     */
    private fun getBackgroundFile(context: Context, extension: String = ".jpg"): File {
        return File(context.filesDir, "background$extension")
    }

    /**
     * 清理旧的背景文件
     */
    private fun clearOldFiles(context: Context, baseName: String) {
        KNOWN_EXTENSIONS.forEach { ext ->
            val file = File(context.filesDir, "$baseName$ext")
            if (file.exists()) {
                file.delete()
            }
        }
    }
    
    /**
     * 保存图片到内部存储
     */
    private suspend fun saveImageToInternalStorage(context: Context, uri: Uri, targetFile: File): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                // 校验落盘结果：空文件视为失败，避免持久化一个读不出来的路径。
                if (!targetFile.exists() || targetFile.length() == 0L) {
                    Log.e(TAG, "保存图片失败: 目标文件为空 ${targetFile.absolutePath}")
                    targetFile.delete()
                    return@withContext null
                }
                val saved = Uri.fromFile(targetFile)
                // 记录平均亮度，供壁纸模式下的内容配色与对比度保护使用。
                computeLuminanceFromFile(targetFile)?.let {
                    BackgroundConfig.setWallpaperLuminance(saved.toString(), it)
                }
                saved
            } catch (e: Exception) {
                Log.e(TAG, "保存图片失败: ${e.message}", e)
                null
            }
        }
    }
    
    /**
     * 保存并应用自定义背景
     */
    suspend fun saveAndApplyCustomBackground(context: Context, uri: Uri): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val extension = getFileExtension(context, uri)
                clearOldFiles(context, "background")
                
                val savedUri = saveImageToInternalStorage(context, uri, getBackgroundFile(context, extension))
                if (savedUri != null) {
                    Log.d(TAG, "图片保存成功，URI: $savedUri")
                    BackgroundConfig.updateCustomBackgroundUri(savedUri.toString())
                    BackgroundConfig.save(context)
                    true
                } else {
                    Log.e(TAG, "图片保存失败")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存自定义背景失败: ${e.message}", e)
            false
        }
    }
    
    /**
     * 清除自定义背景
     */
    fun clearCustomBackground(context: Context) {
        try {
            clearOldFiles(context, "background")
            BackgroundConfig.updateCustomBackgroundUri(null)
            BackgroundConfig.setCustomBackgroundEnabledState(false)
            BackgroundConfig.save(context)
        } catch (e: Exception) {
            Log.e(TAG, "清除自定义背景失败: ${e.message}", e)
        }
    }

    /**
     * Save and apply generic background
     */
    private suspend fun saveAndApplyGenericBackground(
        context: Context,
        uri: Uri,
        filenameBase: String,
        updateConfigAction: (String) -> Unit
    ): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val extension = getFileExtension(context, uri)
                clearOldFiles(context, filenameBase)
                
                val targetFile = File(context.filesDir, "$filenameBase$extension")
                val savedUri = saveImageToInternalStorage(context, uri, targetFile)
                
                if (savedUri != null) {
                    Log.d(TAG, "$filenameBase 图片保存成功，URI: $savedUri")
                    updateConfigAction(savedUri.toString())
                    BackgroundConfig.save(context)
                    true
                } else {
                    Log.e(TAG, "$filenameBase 图片保存失败")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存 $filenameBase 失败: ${e.message}", e)
            false
        }
    }

    /**
     * Clear generic background
     */
    private fun clearGenericBackground(
        context: Context,
        filenameBase: String,
        updateConfigAction: (String?) -> Unit
    ) {
        try {
            clearOldFiles(context, filenameBase)
            updateConfigAction(null)
            BackgroundConfig.save(context)
        } catch (e: Exception) {
            Log.e(TAG, "清除 $filenameBase 失败: ${e.message}", e)
        }
    }

    // Home Background
    suspend fun saveAndApplyHomeBackground(context: Context, uri: Uri) = 
        saveAndApplyGenericBackground(context, uri, HOME_BACKGROUND_FILENAME) { BackgroundConfig.updateHomeBackgroundUri(it) }
    
    fun clearHomeBackground(context: Context) = 
        clearGenericBackground(context, HOME_BACKGROUND_FILENAME) { BackgroundConfig.updateHomeBackgroundUri(it) }

    // Kernel Background
    suspend fun saveAndApplyKernelBackground(context: Context, uri: Uri) = 
        saveAndApplyGenericBackground(context, uri, KERNEL_BACKGROUND_FILENAME) { BackgroundConfig.updateKernelBackgroundUri(it) }
    
    fun clearKernelBackground(context: Context) = 
        clearGenericBackground(context, KERNEL_BACKGROUND_FILENAME) { BackgroundConfig.updateKernelBackgroundUri(it) }

    // Superuser Background
    suspend fun saveAndApplySuperuserBackground(context: Context, uri: Uri) = 
        saveAndApplyGenericBackground(context, uri, SUPERUSER_BACKGROUND_FILENAME) { BackgroundConfig.updateSuperuserBackgroundUri(it) }
    
    fun clearSuperuserBackground(context: Context) = 
        clearGenericBackground(context, SUPERUSER_BACKGROUND_FILENAME) { BackgroundConfig.updateSuperuserBackgroundUri(it) }

    // Module Background
    suspend fun saveAndApplyModuleBackground(context: Context, uri: Uri) = 
        saveAndApplyGenericBackground(context, uri, MODULE_BACKGROUND_FILENAME) { BackgroundConfig.updateModuleBackgroundUri(it) }
    
    fun clearModuleBackground(context: Context) = 
        clearGenericBackground(context, MODULE_BACKGROUND_FILENAME) { BackgroundConfig.updateModuleBackgroundUri(it) }

    // Settings Background
    suspend fun saveAndApplySettingsBackground(context: Context, uri: Uri) =
        saveAndApplyGenericBackground(context, uri, SETTINGS_BACKGROUND_FILENAME) { BackgroundConfig.updateSettingsBackgroundUri(it) }

    fun clearSettingsBackground(context: Context) =
        clearGenericBackground(context, SETTINGS_BACKGROUND_FILENAME) { BackgroundConfig.updateSettingsBackgroundUri(it) }

    // Video Background
    suspend fun saveAndApplyVideoBackground(context: Context, uri: Uri) =
        saveAndApplyGenericBackground(context, uri, VIDEO_BACKGROUND_FILENAME) {
            BackgroundConfig.updateVideoBackgroundUri(it)
        }

    fun clearVideoBackground(context: Context) =
        clearGenericBackground(context, VIDEO_BACKGROUND_FILENAME) { BackgroundConfig.updateVideoBackgroundUri(it) }

    // Focus 主卡片壁纸
    suspend fun saveAndApplyFocusCardBackground(context: Context, uri: Uri) =
        saveAndApplyGenericBackground(context, uri, FOCUS_CARD_BG_FILENAME) {
            BackgroundConfig.updateFocusCardBgUri(it)
        }

    fun clearFocusCardBackground(context: Context) =
        clearGenericBackground(context, FOCUS_CARD_BG_FILENAME) { BackgroundConfig.updateFocusCardBgUri(it) }

    // Grid 布局主卡片壁纸
    suspend fun saveAndApplyGridWorkingCardBackground(context: Context, uri: Uri) =
        saveAndApplyGenericBackground(context, uri, GRID_WORKING_CARD_BG_FILENAME) {
            BackgroundConfig.updateGridWorkingCardBgUri(it)
        }

    fun clearGridWorkingCardBackground(context: Context) =
        clearGenericBackground(context, GRID_WORKING_CARD_BG_FILENAME) {
            BackgroundConfig.updateGridWorkingCardBgUri(it)
        }

    // 高级标题样式的标题图
    suspend fun saveAndApplyTitleImage(context: Context, uri: Uri) =
        saveAndApplyGenericBackground(context, uri, TITLE_IMAGE_FILENAME) {
            BackgroundConfig.updateTitleImageUri(it)
        }

    fun clearTitleImage(context: Context) =
        clearGenericBackground(context, TITLE_IMAGE_FILENAME) {
            BackgroundConfig.updateTitleImageUri(it)
        }

    // Dashboard 磁贴壁纸（每个磁贴一个文件名后缀，互不覆盖）
    suspend fun saveAndApplyDashboardTileBackground(context: Context, tile: String, uri: Uri) =
        saveAndApplyGenericBackground(context, uri, "${DASHBOARD_TILE_BG_FILENAME}_$tile") {
            BackgroundConfig.updateDashboardTileBgUri(tile, it)
        }

    fun clearDashboardTileBackground(context: Context, tile: String) =
        clearGenericBackground(context, "${DASHBOARD_TILE_BG_FILENAME}_$tile") {
            BackgroundConfig.updateDashboardTileBgUri(tile, null)
        }

    /** 一次性清掉 Dashboard 四个磁贴的壁纸（关闭总开关或恢复默认时用）。 */
    fun clearAllDashboardTileBackgrounds(context: Context) {
        BackgroundConfig.DASHBOARD_TILES.forEach { clearDashboardTileBackground(context, it) }
    }

    private val VIDEO_EXTENSIONS = setOf(".mp4", ".webm", ".mkv", ".mov", ".avi", ".3gp")

    /**
     * 为尚无亮度记录的已启用壁纸补算亮度。
     *
     * 用于启动、以及主题导入这类不经过保存流程、不会顺手记亮度的场景；算完一次性写回配置。
     */
    suspend fun refreshMissingWallpaperLuminances(context: Context) {
        if (!BackgroundConfig.isCustomBackgroundEnabled) return
        withContext(Dispatchers.IO) {
            val uris = LinkedHashSet<String>()
            BackgroundConfig.customBackgroundUri?.let { uris.add(it) }
            // The video drives the palette too when it is on, and its brightness comes from
            // the first frame.
            if (BackgroundConfig.isVideoBackgroundEnabled) {
                BackgroundConfig.videoBackgroundUri?.let { uris.add(it) }
            }
            if (BackgroundConfig.isMultiBackgroundEnabled) {
                BackgroundConfig.homeBackgroundUri?.let { uris.add(it) }
                BackgroundConfig.superuserBackgroundUri?.let { uris.add(it) }
                BackgroundConfig.moduleBackgroundUri?.let { uris.add(it) }
                BackgroundConfig.settingsBackgroundUri?.let { uris.add(it) }
            }
            var changed = false
            uris.forEach { uri ->
                if (BackgroundConfig.wallpaperLuminanceFor(uri) != null) return@forEach
                val path = runCatching { Uri.parse(uri).path }.getOrNull() ?: return@forEach
                computeLuminanceFromFile(File(path))?.let {
                    BackgroundConfig.setWallpaperLuminance(uri, it)
                    changed = true
                }
            }
            if (changed) BackgroundConfig.save(context)
        }
    }

    /** 计算文件（图片或视频首帧）的平均感知亮度（0..1）；失败返回 null。 */
    private fun computeLuminanceFromFile(file: File): Float? {
        if (!file.exists() || file.length() == 0L) return null
        val extension = file.extension.lowercase().let { if (it.isEmpty()) "" else ".$it" }
        val bitmap = try {
            if (extension in VIDEO_EXTENSIONS) extractVideoFrame(file) else decodeSampledBitmap(file)
        } catch (e: Exception) {
            Log.w(TAG, "读取壁纸亮度失败: ${e.message}")
            null
        } ?: return null
        return try {
            averageLuminance(bitmap)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    /** 解到约 128px 采样：亮度只需要均值，全尺寸解码太贵。 */
    private fun decodeSampledBitmap(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 128 || bounds.outHeight / sampleSize > 128) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun extractVideoFrame(file: File): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 128, 128)
            } else {
                retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        } catch (e: Exception) {
            Log.w(TAG, "读取视频首帧失败: ${e.message}")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun averageLuminance(bitmap: Bitmap): Float {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        if (pixels.isEmpty()) return 0.5f
        var sum = 0.0
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            sum += (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        }
        return (sum / pixels.size).toFloat()
    }
}
