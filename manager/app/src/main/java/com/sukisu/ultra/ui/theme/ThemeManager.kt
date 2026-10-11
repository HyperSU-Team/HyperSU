package com.sukisu.ultra.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.sukisu.ultra.data.repository.HOME_LAYOUT_FALLBACK
import com.sukisu.ultra.data.repository.HOME_LAYOUT_OPTIONS
import com.sukisu.ultra.data.repository.STATS_TOP_GRID
import com.sukisu.ultra.data.repository.STATS_TOP_LIST
import com.sukisu.ultra.ui.util.LocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Theme import / export / reset for the manager.
 *
 * A theme file (`.fpt`) is FolkPatch's container: a ZIP wrapped in AES/CBC, holding `theme.json`
 * plus the wallpaper images, font, music and sound effects the appearance depends on. See
 * [ThemeArchive] for the container itself.
 *
 * The JSON carries two vocabularies. The flat keys (`nightModeEnabled`, `isBackgroundEnabled`,
 * `musicFilename`, ...) are FolkPatch's, so a theme exported here imports there and vice versa;
 * the `hs*` sections are this app's own preferences verbatim, so importing a theme exported here
 * restores exactly what was exported rather than round-tripping through the lossier mapping.
 */
object ThemeManager {

    private const val TAG = "ThemeManager"

    private const val SETTINGS_PREFS = "settings"
    private const val THEME_EXTENSION = "fpt"

    /** Appearance keys exported with a theme, in the order they are shown in settings. */
    val appearanceKeys = listOf(
        "color_mode",
        "key_color",
        "color_style",
        "color_spec",
        "home_layout_style",
    )

    /**
     * Wallpaper preferences that are not URIs. The URIs deliberately stay out: they travel as
     * image entries and are repointed at the extracted copy on import, so listing them here
     * would resurrect a path from the machine the theme was exported on.
     *
     * Derived from the preference file rather than hand-listed, so a wallpaper added later is
     * carried by themes without touching this file.
     */
    private fun readNonUriKeys(prefs: SharedPreferences): JSONObject =
        JSONObject().also { json ->
            prefs.all.forEach { (key, value) ->
                if (key.endsWith("_uri")) return@forEach
                when (value) {
                    null -> Unit
                    is Float -> json.put(key, value.toDouble())
                    else -> json.put(key, value)
                }
            }
        }

    /** 字体/音乐/音效的偏好里没有 URI，键原样带走即可。 */
    private fun readAllKeys(prefs: SharedPreferences): JSONObject =
        JSONObject().also { json ->
            prefs.all.forEach { (key, value) ->
                when (value) {
                    null -> Unit
                    is Float -> json.put(key, value.toDouble())
                    else -> json.put(key, value)
                }
            }
        }

    private fun prefsString(context: Context, file: String, key: String): String? =
        context.getSharedPreferences(file, Context.MODE_PRIVATE)
            .stringPref(key, null)
            ?.takeIf { it.isNotBlank() }

    /**
     * Maps a FolkPatch theme's flat appearance keys onto HyperSU's preferences.
     *
     * The two apps describe the same settings with different vocabularies: FolkPatch stores a
     * boolean night mode plus a "follow system" flag, HyperSU a single `color_mode` enum. The
     * mapping below is the inverse of what [buildConfigJson] writes, so a theme exported by
     * either app lands in the same state here.
     *
     * Returns the language tag the theme asked for, when it named one this build ships, or null.
     * The caller applies it - see the note at the return.
     */
    private fun applyFolkPatchAppearance(context: Context, json: JSONObject): String? {
        val settings = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)

        // Defaults match FolkPatch's, which matter because a theme that omits a key is taken to
        // mean FolkPatch's default rather than this app's. `useSystemDynamicColor` is the one
        // that shows: FolkPatch defaults it to true, so a theme that does not mention it means
        // "follow the system palette", and reading false instead switched those themes to a
        // fixed key colour.
        val followSys = json.optBoolean("nightModeFollowSys", true)
        val nightEnabled = json.optBoolean("nightModeEnabled", true)
        val monet = json.optBoolean("useSystemDynamicColor", true)

        // FolkPatch has no AMOLED flag, so AMOLED is not reachable from its themes; it comes
        // back through HyperSU's own section on a round trip.
        val mode = when {
            monet && followSys -> ColorMode.MONET_SYSTEM
            monet && nightEnabled -> ColorMode.MONET_DARK
            monet -> ColorMode.MONET_LIGHT
            followSys -> ColorMode.SYSTEM
            nightEnabled -> ColorMode.DARK
            else -> ColorMode.LIGHT
        }

        settings.edit {
            putInt("color_mode", mode.value)
            // FolkPatch's customColor is a theme key name; HyperSU stores a packed ARGB int.
            // Only overwrite when it parses as a number, so a catalog name does not clear the
            // user's key colour.
            json.optString("customColor", "").toIntOrNull()?.let { putInt("key_color", it) }
            json.optString("colorStyle", "").takeIf { it.isNotBlank() }?.let {
                putString("color_style", it)
            }
            // FolkPatch spells the standard MD3_2021 / MD3_2025; HyperSU uses the enum name.
            when (json.optString("colorStandard", "")) {
                "MD3_2021" -> putString("color_spec", ColorSpec.SpecVersion.SPEC_2021.name)
                "MD3_2025" -> putString("color_spec", ColorSpec.SpecVersion.SPEC_2025.name)
            }
            // FolkPatch's layout vocabulary is its own (grid_working, stats, ...). Only accept a
            // name HyperSU actually knows, otherwise the home screen would be handed a layout it
            // cannot render and fall back unpredictably.
            json.optString("homeLayoutStyle", "").takeIf { it in HOME_LAYOUT_OPTIONS }?.let {
                putString("home_layout_style", it)
            }
            // The Grid layout's card arrangement. Only the two names this app renders.
            json.optString("statsTopLayout", "")
                .takeIf { it == STATS_TOP_LIST || it == STATS_TOP_GRID }
                ?.let { putString("stats_top_layout", it) }
            // Colour contrast, by name. FolkPatch writes the enum name.
            json.optString("colorContrast", "")
                .takeIf { it.isNotBlank() && ColorContrast.entries.any { entry -> entry.name == it } }
                ?.let { putString("color_contrast", it) }
        }

        // The language is *not* applied here. On Android 13 and up LocaleHelper.setLanguage
        // assigns applicationLocales, which makes the platform recreate the activity; doing that
        // from inside this IO block, while the caller is holding a loading dialog, is what left
        // the import looking hung. The tag is returned instead, and the caller applies it on the
        // main thread once the import has finished.
        return json.optString("appLanguage", "")
            .takeIf { it in LocaleHelper.SUPPORTED_TAGS }
    }

    /**
     * Maps FolkPatch's flat background keys onto HyperSU's wallpaper preferences.
     *
     * A key the theme does not mention is left alone rather than written with a default. That
     * matters for the enable flags: BackgroundConfig.load() turns a wallpaper's switch on when
     * an image is present for it, and writing an explicit `false` here overrode that - a theme
     * carrying a focus-card picture arrived with the picture saved and the card switched off,
     * which reads as "the wallpaper did not apply".
     */
    private fun applyFolkPatchBackground(context: Context, json: JSONObject) {
        val background = context.getSharedPreferences(BackgroundConfig.PREFS_NAME, Context.MODE_PRIVATE)

        background.edit {
            // Main background. `isBackgroundEnabled` has no fallback of its own, so its absence
            // means "off", matching how the exporter writes it.
            putBoolean("custom_background_enabled", json.optBoolean("isBackgroundEnabled", false))
            // The day and night dims fall back to the base dim, as FolkPatch's do: a theme that
            // sets only the base value means it for both.
            val backgroundDim = json.optDouble("backgroundDim", 0.2)
            if (json.has("backgroundOpacity")) {
                putFloat("custom_background_opacity", json.optDouble("backgroundOpacity", 0.5).toFloat())
            }
            if (json.has("backgroundBlur")) {
                putFloat("custom_background_blur", json.optDouble("backgroundBlur", 0.0).toFloat())
            }
            if (json.has("backgroundDim")) {
                putFloat("custom_background_dim", backgroundDim.toFloat())
            }
            if (json.has("isDualBackgroundDimEnabled")) {
                putBoolean("custom_background_dual_dim_enabled", json.optBoolean("isDualBackgroundDimEnabled", false))
            }
            if (json.has("backgroundDayDim")) {
                putFloat("custom_background_day_dim", json.optDouble("backgroundDayDim", backgroundDim).toFloat())
            }
            if (json.has("backgroundNightDim")) {
                putFloat("custom_background_night_dim", json.optDouble("backgroundNightDim", backgroundDim).toFloat())
            }

            // The rest of the wallpaper settings, in the same flat vocabulary. Without these a
            // theme carried its images across but none of the switches and sliders that decide
            // whether they are shown or how strongly.
            if (json.has("isMultiBackgroundEnabled")) {
                putBoolean("multi_background_enabled", json.optBoolean("isMultiBackgroundEnabled", false))
            }
            if (json.has("isVideoBackgroundEnabled")) {
                putBoolean("video_background_enabled", json.optBoolean("isVideoBackgroundEnabled", false))
            }
            if (json.has("videoVolume")) {
                putFloat("video_volume", json.optDouble("videoVolume", 0.0).toFloat())
            }

            // Focus card: the hero card's own wallpaper and its dim/opacity controls.
            //
            // The defaults are FolkPatch's, not this app's: a theme that omits `focusCardBgDim`
            // means 0.3 there, and reading 0.0 here made every such theme arrive with an
            // undimmed picture. The day and night values fall back to the base value rather than
            // to a literal, which is what FolkPatch does - a theme that sets only the base value
            // means it for both.
            val focusDim = json.optDouble("focusCardBgDim", 0.3).toFloat()
            val focusOpacity = json.optDouble("focusCardBgOpacity", 1.0).toFloat()
            // FolkPatch falls back to "did the theme carry any focus card picture" rather than to
            // false, so a theme that ships a picture without mentioning the switch still shows
            // it. Reading a literal false here is what made those themes arrive with the card
            // off and the picture nowhere.
            val hasAnyFocusCardBg = FOCUS_SUBCARD_ENTRIES.any { json.optBoolean(hasMarkerKey(it), false) }
            if (json.has("isFocusCardBackgroundEnabled") || hasAnyFocusCardBg) {
                putBoolean(
                    "focus_card_background_enabled",
                    json.optBoolean("isFocusCardBackgroundEnabled", hasAnyFocusCardBg),
                )
            }
            if (json.has("focusCardBgDim")) {
                putFloat("focus_card_bg_dim", focusDim)
            }
            if (json.has("isFocusCardDualDimEnabled")) {
                putBoolean("focus_card_dual_dim_enabled", json.optBoolean("isFocusCardDualDimEnabled", false))
            }
            if (json.has("focusCardBgDayDim")) {
                putFloat("focus_card_day_dim", json.optDouble("focusCardBgDayDim", focusDim.toDouble()).toFloat())
            }
            if (json.has("focusCardBgNightDim")) {
                putFloat("focus_card_night_dim", json.optDouble("focusCardBgNightDim", focusDim.toDouble()).toFloat())
            }
            if (json.has("focusCardBgOpacity")) {
                putFloat("focus_card_opacity", focusOpacity)
            }
            if (json.has("isFocusCardDualOpacityEnabled")) {
                putBoolean("focus_card_dual_opacity_enabled", json.optBoolean("isFocusCardDualOpacityEnabled", false))
            }
            if (json.has("focusCardBgDayOpacity")) {
                putFloat("focus_card_day_opacity", json.optDouble("focusCardBgDayOpacity", focusOpacity.toDouble()).toFloat())
            }
            if (json.has("focusCardBgNightOpacity")) {
                putFloat("focus_card_night_opacity", json.optDouble("focusCardBgNightOpacity", focusOpacity.toDouble()).toFloat())
            }

            // Dashboard card: shared dim/opacity across the tiles. Same fallback rule.
            val dashDim = json.optDouble("dashboardCardBgDim", 0.3).toFloat()
            val dashOpacity = json.optDouble("dashboardCardBgOpacity", 1.0).toFloat()
            if (json.has("isDashboardCardBackgroundEnabled")) {
                putBoolean("dashboard_card_background_enabled", json.optBoolean("isDashboardCardBackgroundEnabled", false))
            }
            if (json.has("dashboardCardBgDim")) {
                putFloat("dashboard_card_bg_dim", dashDim)
            }
            if (json.has("isDashboardCardDualDimEnabled")) {
                putBoolean("dashboard_card_dual_dim_enabled", json.optBoolean("isDashboardCardDualDimEnabled", false))
            }
            if (json.has("dashboardCardBgDayDim")) {
                putFloat("dashboard_card_day_dim", json.optDouble("dashboardCardBgDayDim", dashDim.toDouble()).toFloat())
            }
            if (json.has("dashboardCardBgNightDim")) {
                putFloat("dashboard_card_night_dim", json.optDouble("dashboardCardBgNightDim", dashDim.toDouble()).toFloat())
            }
            if (json.has("dashboardCardBgOpacity")) {
                putFloat("dashboard_card_opacity", dashOpacity)
            }
            if (json.has("isDashboardCardDualOpacityEnabled")) {
                putBoolean("dashboard_card_dual_opacity_enabled", json.optBoolean("isDashboardCardDualOpacityEnabled", false))
            }
            if (json.has("dashboardCardBgDayOpacity")) {
                putFloat("dashboard_card_day_opacity", json.optDouble("dashboardCardBgDayOpacity", dashOpacity.toDouble()).toFloat())
            }
            if (json.has("dashboardCardBgNightOpacity")) {
                putFloat("dashboard_card_night_opacity", json.optDouble("dashboardCardBgNightOpacity", dashOpacity.toDouble()).toFloat())
            }

            // Grid 布局的主卡片壁纸，以及它那几个隐藏开关。FolkPatch 的 Grid 主卡片可以单独
            // 换图，并且能藏掉状态勾、文字和模式标签；这些原本在导入时整块被丢掉。
            //
            // `gridWorkingCardBackgroundDim` 在 FolkPatch 的默认值是 0.3，不是 0：主题不写这个
            // 字段时它要的是有遮罩的卡片，读成 0 会让图过亮。
            val gridDim = json.optDouble("gridWorkingCardBackgroundDim", 0.3).toFloat()
            val gridOpacity = json.optDouble("gridWorkingCardBackgroundOpacity", 1.0).toFloat()
            if (json.has("isGridWorkingCardBackgroundEnabled")) {
                putBoolean(
                    "grid_working_card_background_enabled",
                    json.optBoolean("isGridWorkingCardBackgroundEnabled", false),
                )
            }
            if (json.has("gridWorkingCardBackgroundOpacity")) {
                putFloat("grid_working_card_background_opacity", gridOpacity)
            }
            if (json.has("isGridDualOpacityEnabled")) {
                putBoolean("grid_working_card_dual_opacity_enabled", json.optBoolean("isGridDualOpacityEnabled", false))
            }
            if (json.has("gridWorkingCardBackgroundDayOpacity")) {
                putFloat(
                    "grid_working_card_background_day_opacity",
                    json.optDouble("gridWorkingCardBackgroundDayOpacity", gridOpacity.toDouble()).toFloat(),
                )
            }
            if (json.has("gridWorkingCardBackgroundNightOpacity")) {
                putFloat(
                    "grid_working_card_background_night_opacity",
                    json.optDouble("gridWorkingCardBackgroundNightOpacity", gridOpacity.toDouble()).toFloat(),
                )
            }
            if (json.has("gridWorkingCardBackgroundDim")) {
                putFloat("grid_working_card_background_dim", gridDim)
            }
            if (json.has("isGridWorkingCardCheckHidden")) {
                putBoolean("grid_working_card_check_hidden", json.optBoolean("isGridWorkingCardCheckHidden", false))
            }
            if (json.has("isGridWorkingCardTextHidden")) {
                putBoolean("grid_working_card_text_hidden", json.optBoolean("isGridWorkingCardTextHidden", false))
            }
            if (json.has("isGridWorkingCardModeHidden")) {
                putBoolean("grid_working_card_mode_hidden", json.optBoolean("isGridWorkingCardModeHidden", false))
            }
            if (json.has("isListWorkingCardModeHidden")) {
                putBoolean("list_working_card_mode_hidden", json.optBoolean("isListWorkingCardModeHidden", false))
            }

            // 高级标题样式：用图片替换首页顶栏的标题。
            if (json.has("isAdvancedTitleStyleEnabled")) {
                putBoolean(
                    "advanced_title_style_enabled",
                    json.optBoolean("isAdvancedTitleStyleEnabled", false),
                )
            }
            if (json.has("titleImageDayOpacity")) {
                putFloat("title_image_day_opacity", json.optDouble("titleImageDayOpacity", 1.0).toFloat())
            }
            if (json.has("titleImageNightOpacity")) {
                putFloat("title_image_night_opacity", json.optDouble("titleImageNightOpacity", 1.0).toFloat())
            }
            if (json.has("titleImageDim")) {
                putFloat("title_image_dim", json.optDouble("titleImageDim", 0.0).toFloat())
            }
            if (json.has("titleImageOffsetX")) {
                putFloat("title_image_offset_x", json.optDouble("titleImageOffsetX", 0.0).toFloat())
            }

            // 导航图标。开关与每个图标的存在标记都来自主题；图标文件本身由 unzipImages 落盘。
            if (json.has("navIconCustomEnabled")) {
                putBoolean("nav_icon_custom_enabled", json.optBoolean("navIconCustomEnabled", false))
            }
        }
    }

    /** FolkPatch's flat font keys. */
    private fun applyFolkPatchFont(context: Context, json: JSONObject) {
        val modeName = json.optString("fontMode", "")
        val mode = FontMode.fromSerializedName(modeName)
            ?: if (json.optBoolean("isFontEnabled", false)) FontMode.CUSTOM else FontMode.SYSTEM_DEFAULT

        when (mode) {
            FontMode.CUSTOM -> {
                // FolkPatch always packs the face as `font.ttf`; adopt it when present.
                val imported = File(context.filesDir, "font.ttf")
                if (imported.isFile) {
                    FontConfig.applyCustomFont(context, imported)
                } else {
                    FontConfig.setFontMode(context, FontMode.APP_DEFAULT)
                }
            }
            else -> FontConfig.setFontMode(context, mode)
        }
    }

    /** FolkPatch's flat music keys. */
    private fun applyFolkPatchMusic(context: Context, json: JSONObject) {
        val filename = json.optString("musicFilename", "").takeIf { it.isNotBlank() && it != "null" }
        val enabled = json.optBoolean("isMusicEnabled", false)
        val hasFile = filename != null && File(context.filesDir, "music/$filename").isFile

        MusicConfig.setMusicEnabledState(enabled && hasFile)
        MusicConfig.setVolumeValue(json.optDouble("musicVolume", 1.0).toFloat())
        MusicConfig.setAutoPlayEnabledState(json.optBoolean("isAutoPlayEnabled", false))
        MusicConfig.setLoopingEnabledState(json.optBoolean("isLoopingEnabled", false))
        MusicConfig.setMusicFilenameValue(if (hasFile) filename else null)
        MusicConfig.save(context)
    }

    /** FolkPatch's flat sound-effect keys. */
    private fun applyFolkPatchSound(context: Context, json: JSONObject) {
        val filename = json.optString("soundEffectFilename", "").takeIf { it.isNotBlank() && it != "null" }
        val hasFile = filename != null && File(context.filesDir, "sound_effects/$filename").isFile

        SoundEffectConfig.setEnabledState(json.optBoolean("isSoundEffectEnabled", false))
        SoundEffectConfig.setScopeValue(
            json.optString("soundEffectScope", SoundEffectConfig.SCOPE_GLOBAL),
        )
        SoundEffectConfig.setSourceTypeValue(
            if (hasFile) SoundEffectConfig.SOURCE_TYPE_LOCAL else SoundEffectConfig.SOURCE_TYPE_PRESET,
        )
        SoundEffectConfig.setFilenameValue(if (hasFile) filename else null)
        SoundEffectConfig.save(context)
    }

    /** 应用归档里的字体段落。
     *
     * 顺序很重要：先决定模式，再处理文件。自定义字体只在**归档确实带了文件**时才采用，
     * 否则退回内置字体，而不是留一个指向不存在文件的 CUSTOM 模式（那会让整个界面掉回默认字体
     * 却仍然显示「已启用自定义字体」）。
     */
    private fun applyFontSection(context: Context, section: JSONObject) {
        val modeName = section.optString("font_mode", "")
        val customPath = section.optString("custom_font_path", "").takeIf { it.isNotBlank() }
        val legacyEnabled = section.optBoolean("custom_font_enabled", false)

        val mode = FontMode.fromName(modeName)
            ?: if (legacyEnabled && customPath != null) FontMode.CUSTOM else null

        when (mode) {
            FontMode.SYSTEM_DEFAULT -> FontConfig.setFontMode(context, FontMode.SYSTEM_DEFAULT)
            FontMode.APP_DEFAULT -> FontConfig.setFontMode(context, FontMode.APP_DEFAULT)
            FontMode.CUSTOM -> {
                val file = customPath?.let { File(context.filesDir, it) }
                if (file != null && file.isFile) {
                    FontConfig.applyCustomFont(context, file)
                } else {
                    // 归档声明了自定义字体却没带文件：退回内置字体，别留坏状态。
                    FontConfig.setFontMode(context, FontMode.APP_DEFAULT)
                }
            }
            // 归档没有字体信息：保持用户当前字体不变。
            null -> Unit
        }
    }

    /** 应用归档里的音乐段落；带文件才启用，其余字段照写。 */
    private fun applyMusicSection(context: Context, section: JSONObject) {
        val filename = section.optString("music_filename", "").takeIf { it.isNotBlank() }
        val enabled = section.optBoolean("music_enabled", false)
        val hasFile = filename != null && File(context.filesDir, "music/$filename").isFile

        MusicConfig.setMusicEnabledState(enabled && hasFile)
        MusicConfig.setVolumeValue(section.optDouble("volume", 1.0).toFloat())
        MusicConfig.setAutoPlayEnabledState(section.optBoolean("auto_play", false))
        MusicConfig.setLoopingEnabledState(section.optBoolean("looping_enabled", false))
        MusicConfig.setMusicFilenameValue(if (hasFile) filename else null)
        MusicConfig.save(context)
    }

    /** 应用归档里的音效段落；预设不需要文件，自定义文件缺失时退回预设。 */
    private fun applySoundSection(context: Context, section: JSONObject) {
        val enabled = section.optBoolean("sound_effect_enabled", false)
        val filename = section.optString("sound_effect_filename", "").takeIf { it.isNotBlank() }
        val sourceType = section.optString(
            "sound_effect_source_type",
            SoundEffectConfig.SOURCE_TYPE_PRESET,
        )
        val hasFile = filename != null && File(context.filesDir, "sound_effects/$filename").isFile

        SoundEffectConfig.setEnabledState(enabled)
        SoundEffectConfig.setScopeValue(
            section.optString("sound_effect_scope", SoundEffectConfig.SCOPE_GLOBAL),
        )
        SoundEffectConfig.setSourceTypeValue(
            if (sourceType == SoundEffectConfig.SOURCE_TYPE_LOCAL && !hasFile) {
                SoundEffectConfig.SOURCE_TYPE_PRESET
            } else {
                sourceType
            },
        )
        SoundEffectConfig.setPresetNameValue(
            section.optString("sound_effect_preset_name", SoundEffectConfig.PRESETS[0]),
        )
        SoundEffectConfig.setFilenameValue(if (hasFile) filename else null)

        val startupFilename = section.optString("startup_sound_filename", "").takeIf { it.isNotBlank() }
        val startupHasFile = startupFilename != null &&
            File(context.filesDir, "sound_effects/$startupFilename").isFile
        SoundEffectConfig.setStartupEnabledState(section.optBoolean("startup_sound_enabled", false))
        SoundEffectConfig.setStartupSourceTypeValue(
            section.optString("startup_sound_source_type", SoundEffectConfig.SOURCE_TYPE_PRESET),
        )
        SoundEffectConfig.setStartupPresetNameValue(
            section.optString("startup_sound_preset_name", SoundEffectConfig.STARTUP_PRESETS[0]),
        )
        SoundEffectConfig.setStartupFilenameValue(if (startupHasFile) startupFilename else null)

        SoundEffectConfig.save(context)
    }

    /**
     * 主题要一并带走的二进制文件，返回「归档内条目名 → 本地文件」。
     *
     * 条目名带一级目录（`music/…`、`sound_effects/…`），解压时才落回原位置；字体直接在
     * `filesDir` 根，所以条目名就是文件名。`.distinctBy` 防止点击音效和开机音效指向同一个文件
     * 时往 zip 里写两次同名条目。
     */
    private fun mediaFiles(context: Context): List<Pair<String, File>> = buildList {
        // The face is packed twice under different names: `font.ttf` is what FolkPatch looks for
        // on import, and the `custom_font_*.ttf` name is what HyperSU's own importer resolves
        // through `custom_font_path`. The file is a few hundred KB, so the duplicate is cheap
        // next to being unreadable by one of the two managers.
        prefsString(context, FONT_PREFS, "custom_font_path")?.let { name ->
            val file = File(context.filesDir, name)
            add(name to file)
            add(FOLK_FONT_ENTRY to file)
        }
        prefsString(context, MUSIC_PREFS, "music_filename")?.let {
            add("music/$it" to File(context.filesDir, "music/$it"))
        }
        prefsString(context, SOUND_PREFS, "sound_effect_filename")?.let {
            add("sound_effects/$it" to File(context.filesDir, "sound_effects/$it"))
        }
        prefsString(context, SOUND_PREFS, "startup_sound_filename")?.let {
            add("sound_effects/$it" to File(context.filesDir, "sound_effects/$it"))
        }
        // Custom navigation icons. They are packed at the root under the same names FolkPatch
        // uses, which is also the name of the file on disk, so no mapping is needed.
        BottomBarIconConfig.DESTINATIONS.forEach { destination ->
            val name = BottomBarIconConfig.fileName(destination)
            add(name to File(context.filesDir, name))
        }
    }.distinctBy { it.first }

    private fun putFile(zip: ZipOutputStream, entryName: String, file: File) {
        if (!file.isFile) return
        runCatching {
            zip.putNextEntry(ZipEntry(entryName))
            FileInputStream(file).use { it.copyTo(zip) }
            zip.closeEntry()
        }.onFailure { Log.w(TAG, "failed to pack $entryName", it) }
    }

    /** Wallpaper file base name -> preference key holding its URI. */
    private val imageKeyByName = mapOf(
        "background" to "custom_background_uri",
        "background_home" to "home_background_uri",
        "background_kernel" to "kernel_background_uri",
        "background_superuser" to "superuser_background_uri",
        "background_module" to "module_background_uri",
        "background_settings" to "settings_background_uri",
        "background_video" to "video_background_uri",
        "focus_card_bg" to "focus_card_bg_uri",
        "grid_working_card_background" to "grid_working_card_background_uri",
        "title_image" to "title_image_uri",
        "dashboard_tile_bg_working" to "dashboard_tile_bg_uri_working",
        "dashboard_tile_bg_selinux" to "dashboard_tile_bg_uri_selinux",
        "dashboard_tile_bg_zygisk" to "dashboard_tile_bg_uri_zygisk",
        "dashboard_tile_bg_seccomp" to "dashboard_tile_bg_uri_seccomp",
    )

    /**
     * FolkPatch entry names that differ from HyperSU's, mapped onto HyperSU's base name.
     *
     * The two apps agree on most slots, but not all, and a name that does not match is silently
     * dropped - which reads as "the theme imported but the wallpaper did nothing". The module
     * background is the one that bit: FolkPatch calls it `background_system_module`, HyperSU
     * `background_module`, and because `background` is a prefix of both, the longest-prefix
     * lookup below would otherwise fall through to nothing.
     *
     * FolkPatch also stores one dashboard image (`dashboard_card_bg`) where HyperSU stores one
     * per tile; that maps onto the working tile, which is the card FolkPatch's single image
     * belongs to.
     */
    private val folkPatchEntryAliases = mapOf(
        "background_system_module" to "background_module",
        "dashboard_card_bg" to "dashboard_tile_bg_working",
    )

    private val imageExtensions =
        listOf(".jpg", ".png", ".gif", ".webp", ".mp4", ".webm", ".mkv", ".mov", ".avi", ".3gp")

    /**
     * 字体 / 音乐 / 音效各自独立的偏好文件。
     *
     * 它们不在 `background_settings` 里，所以壁纸那套 `readNonUriKeys` 覆盖不到；主题要真正
     * 「完整」就得连这三份一起带走，否则从商店下载的主题只能改配色和壁纸。
     */
    private const val FONT_PREFS = "font_settings"
    private const val MUSIC_PREFS = "music_settings"
    private const val SOUND_PREFS = "sound_effect_settings"

    /**
     * 归档里允许出现的一级子目录：音乐和音效各占一个目录，字体直接落在 `filesDir` 根。
     * 解压只放行这些目录，其余任何含 `/` 的条目一律丢弃（配合 `..` 检查防路径穿越）。
     */
    private val ARCHIVE_DIRS = setOf("music", "sound_effects")

    /** 归档里字体的合法后缀（字体没有目录前缀，靠后缀识别）。 */
    private val FONT_EXTENSIONS = listOf(".ttf", ".otf", ".ttc")

    /** FolkPatch's fixed name for the packed custom face. */
    private const val FOLK_FONT_ENTRY = "font.ttf"

    data class ThemeMetadata(
        val name: String,
        val type: String, // "phone" or "tablet"
        val version: String,
        val author: String,
        val description: String,
    )

    /** Export the current appearance and wallpapers to [uri] as a theme archive. */
    suspend fun exportTheme(context: Context, uri: Uri, metadata: ThemeMetadata): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val json = buildConfigJson(context, metadata)

                val written = context.contentResolver.openOutputStream(uri)?.use { output ->
                    // Same encrypted container FolkPatch writes, so the file is readable by
                    // either manager.
                    ThemeArchive.write(output) { zip ->
                        zip.putNextEntry(ZipEntry(ThemeArchive.CONFIG_ENTRY))
                        zip.write(json.toString(2).toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                        imageKeyByName.keys.forEach { base -> putImage(zip, context, base) }
                        mediaFiles(context).forEach { (entryName, file) -> putFile(zip, entryName, file) }
                    }
                } != null

                if (written) Log.d(TAG, "theme exported to $uri")
                written
            } catch (e: Exception) {
                Log.e(TAG, "failed to export theme", e)
                false
            }
        }
    }

    /**
     * Builds the theme JSON.
     *
     * Two sets of keys go in on purpose. The `meta_*` and flat appearance keys are FolkPatch's,
     * so a theme exported here imports there; the `hs*` sections are HyperSU's own preferences
     * verbatim, so importing back restores exactly what was exported instead of round-tripping
     * through FolkPatch's lossier vocabulary.
     */
    private fun buildConfigJson(context: Context, metadata: ThemeMetadata): JSONObject {
        val settings = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        val background = context.getSharedPreferences(BackgroundConfig.PREFS_NAME, Context.MODE_PRIVATE)
        val font = context.getSharedPreferences(FONT_PREFS, Context.MODE_PRIVATE)
        val music = context.getSharedPreferences(MUSIC_PREFS, Context.MODE_PRIVATE)
        val sound = context.getSharedPreferences(SOUND_PREFS, Context.MODE_PRIVATE)

        val colorMode = ColorMode.fromValue(settings.intPref("color_mode", 0))
        val colorSpec = settings.stringPref("color_spec", ColorSpec.SpecVersion.SPEC_2025.name)
            ?: ColorSpec.SpecVersion.SPEC_2025.name

        return JSONObject().apply {
            // ---- FolkPatch metadata keys ----
            put("meta_name", metadata.name)
            put("meta_type", metadata.type)
            put("meta_version", metadata.version)
            put("meta_author", metadata.author)
            put("meta_description", metadata.description)

            // ---- FolkPatch appearance keys ----
            put("isBackgroundEnabled", background.booleanPref("custom_background_enabled", false))
            put("backgroundOpacity", background.floatPref("custom_background_opacity", 0.5f).toDouble())
            put("backgroundBlur", background.floatPref("custom_background_blur", 0.2f).toDouble())
            put("backgroundDim", background.floatPref("custom_background_dim", 0.0f).toDouble())
            put("isDualBackgroundDimEnabled", background.booleanPref("custom_background_dual_dim_enabled", true))
            put("backgroundDayDim", background.floatPref("custom_background_day_dim", 0.0f).toDouble())
            put("backgroundNightDim", background.floatPref("custom_background_night_dim", 0.5f).toDouble())

            put("nightModeEnabled", colorMode.isDark)
            put("nightModeFollowSys", colorMode.isSystem)
            put("useSystemDynamicColor", colorMode.isMonet)
            // FolkPatch has no AMOLED flag; a theme cannot express it, so it is not written.
            put("customColor", settings.intPref("key_color", 0).toString())
            // org.json's put(String, Any) throws on null, so every optional string needs a
            // fallback rather than a nullable value.
            put("colorStyle", settings.stringPref("color_style", PaletteStyle.TonalSpot.name) ?: PaletteStyle.TonalSpot.name)
            put("colorStandard", if (colorSpec == ColorSpec.SpecVersion.SPEC_2021.name) "MD3_2021" else "MD3_2025")
            put("colorGenerationMode", if (colorMode.isMonet) "custom" else "classic")
            put("homeLayoutStyle", settings.stringPref("home_layout_style", HOME_LAYOUT_FALLBACK) ?: HOME_LAYOUT_FALLBACK)
            put("statsTopLayout", settings.stringPref("stats_top_layout", STATS_TOP_LIST) ?: STATS_TOP_LIST)
            put("colorContrast", settings.stringPref("color_contrast", ColorContrast.STANDARD.name) ?: ColorContrast.STANDARD.name)
            // The language, so a theme carries it both ways. Read through LocaleHelper for the
            // same reason it is applied through it: the preference is only a mirror of the
            // platform's applicationLocales on Android 13 and up.
            LocaleHelper.getCurrentLanguage(context)
                .takeIf { it.isNotBlank() }
                ?.let { put("appLanguage", it) }

            // ---- FolkPatch wallpaper keys ----
            // The switches and sliders that go with the images. Writing these matters more than
            // it looks: FolkPatch resets a slot to its default when its key is absent, so a theme
            // that shipped a focus-card picture without these would arrive with the picture
            // hidden behind a disabled switch.
            put("isMultiBackgroundEnabled", background.booleanPref("multi_background_enabled", false))
            put("isVideoBackgroundEnabled", background.booleanPref("video_background_enabled", false))
            put("videoVolume", background.floatPref("video_volume", 1.0f).toDouble())

            // The enable flags follow the same rule BackgroundConfig.load() applies: a wallpaper
            // that is present counts as enabled even when the switch was never touched, so the
            // fallback here is "is there an image", not false. Exporting a literal false would
            // hand the next importer a theme whose picture is there but switched off.
            put(
                "isFocusCardBackgroundEnabled",
                background.booleanPref(
                    "focus_card_background_enabled",
                    !background.stringPref("focus_card_bg_uri", null).isNullOrBlank(),
                ),
            )
            put("focusCardBgDim", background.floatPref("focus_card_bg_dim", 0.0f).toDouble())
            put("isFocusCardDualDimEnabled", background.booleanPref("focus_card_dual_dim_enabled", false))
            put("focusCardBgDayDim", background.floatPref("focus_card_day_dim", 0.0f).toDouble())
            put("focusCardBgNightDim", background.floatPref("focus_card_night_dim", 0.0f).toDouble())
            put("isFocusCardDualOpacityEnabled", background.booleanPref("focus_card_dual_opacity_enabled", false))
            put("focusCardBgOpacity", background.floatPref("focus_card_opacity", 1.0f).toDouble())
            put("focusCardBgDayOpacity", background.floatPref("focus_card_day_opacity", 1.0f).toDouble())
            put("focusCardBgNightOpacity", background.floatPref("focus_card_night_opacity", 1.0f).toDouble())
            // The `has*` markers tell the other manager which pictures the archive actually
            // carries. FolkPatch derives "is the focus card switched on" from them when the
            // switch key is absent, so leaving them out makes an exported theme arrive there with
            // the card off however the switch was set here.
            //
            // This app has one hero card where FolkPatch has four, so its single picture is
            // reported as the kernel card - the status card, which is what the hero card is.
            val hasFocusCardBg = !background.stringPref("focus_card_bg_uri", null).isNullOrBlank()
            put("hasFocusCardKernelBg", hasFocusCardBg)
            put("hasFocusCardAppBg", false)
            put("hasFocusCardDeviceBg", false)
            put("hasFocusCardStorageBg", false)

            put(
                "isDashboardCardBackgroundEnabled",
                background.booleanPref(
                    "dashboard_card_background_enabled",
                    listOf("working", "selinux", "zygisk", "seccomp").any {
                        !background.stringPref("dashboard_tile_bg_uri_$it", null).isNullOrBlank()
                    },
                ),
            )
            put("dashboardCardBgDim", background.floatPref("dashboard_card_bg_dim", 0.3f).toDouble())
            put("isDashboardCardDualDimEnabled", background.booleanPref("dashboard_card_dual_dim_enabled", false))
            put("dashboardCardBgDayDim", background.floatPref("dashboard_card_day_dim", 0.3f).toDouble())
            put("dashboardCardBgNightDim", background.floatPref("dashboard_card_night_dim", 0.3f).toDouble())
            put("isDashboardCardDualOpacityEnabled", background.booleanPref("dashboard_card_dual_opacity_enabled", false))
            put("dashboardCardBgOpacity", background.floatPref("dashboard_card_opacity", 1.0f).toDouble())
            put("dashboardCardBgDayOpacity", background.floatPref("dashboard_card_day_opacity", 1.0f).toDouble())
            put("dashboardCardBgNightOpacity", background.floatPref("dashboard_card_night_opacity", 1.0f).toDouble())
            // Same reason as the focus card markers above: FolkPatch reads this to decide whether
            // the dashboard card has a picture at all.
            put(
                "hasDashboardCardBg",
                listOf("working", "selinux", "zygisk", "seccomp").any {
                    !background.stringPref("dashboard_tile_bg_uri_$it", null).isNullOrBlank()
                },
            )

            // Grid 布局的主卡片，用 FolkPatch 自己的键名，这样导出的主题在那边也能用。
            put(
                "isGridWorkingCardBackgroundEnabled",
                background.booleanPref(
                    "grid_working_card_background_enabled",
                    !background.stringPref("grid_working_card_background_uri", null).isNullOrBlank(),
                ),
            )
            put("gridWorkingCardBackgroundOpacity", background.floatPref("grid_working_card_background_opacity", 1.0f).toDouble())
            put("isGridDualOpacityEnabled", background.booleanPref("grid_working_card_dual_opacity_enabled", false))
            put("gridWorkingCardBackgroundDayOpacity", background.floatPref("grid_working_card_background_day_opacity", 1.0f).toDouble())
            put("gridWorkingCardBackgroundNightOpacity", background.floatPref("grid_working_card_background_night_opacity", 1.0f).toDouble())
            put("gridWorkingCardBackgroundDim", background.floatPref("grid_working_card_background_dim", 0.0f).toDouble())
            put("isGridWorkingCardCheckHidden", background.booleanPref("grid_working_card_check_hidden", false))
            put("isGridWorkingCardTextHidden", background.booleanPref("grid_working_card_text_hidden", false))
            put("isGridWorkingCardModeHidden", background.booleanPref("grid_working_card_mode_hidden", false))
            put("isListWorkingCardModeHidden", background.booleanPref("list_working_card_mode_hidden", false))

            // 高级标题样式，用 FolkPatch 的键名。
            put(
                "isAdvancedTitleStyleEnabled",
                background.booleanPref(
                    "advanced_title_style_enabled",
                    !background.stringPref("title_image_uri", null).isNullOrBlank(),
                ),
            )
            put("titleImageDayOpacity", background.floatPref("title_image_day_opacity", 1.0f).toDouble())
            put("titleImageNightOpacity", background.floatPref("title_image_night_opacity", 1.0f).toDouble())
            put("titleImageDim", background.floatPref("title_image_dim", 0.0f).toDouble())
            put("titleImageOffsetX", background.floatPref("title_image_offset_x", 0.0f).toDouble())

            // 导航图标：开关与每个图标的存在标记。图标文件本身由 mediaFiles 打包。
            put("navIconCustomEnabled", BottomBarIconConfig.isEnabled(context))
            put("navIcons", JSONObject().apply {
                BottomBarIconConfig.DESTINATIONS.forEach { destination ->
                    val name = BottomBarIconConfig.fileName(destination)
                    if (File(context.filesDir, name).isFile) put(destination, name)
                }
            })

            put("isFontEnabled", font.booleanPref("custom_font_enabled", false))
            put("fontMode", FontMode.fromName(font.stringPref("font_mode", null))?.serializedName ?: "system")

            put("isMusicEnabled", music.booleanPref("music_enabled", false))
            put("musicVolume", music.floatPref("volume", 1.0f).toDouble())
            put("isAutoPlayEnabled", music.booleanPref("auto_play", false))
            put("isLoopingEnabled", music.booleanPref("looping_enabled", false))
            put("musicFilename", music.stringPref("music_filename", "") ?: "")

            put("isSoundEffectEnabled", sound.booleanPref("sound_effect_enabled", false))
            put("soundEffectFilename", sound.stringPref("sound_effect_filename", "") ?: "")
            put("soundEffectScope", sound.stringPref("sound_effect_scope", SoundEffectConfig.SCOPE_GLOBAL) ?: SoundEffectConfig.SCOPE_GLOBAL)

            // ---- HyperSU-native sections (exact restore) ----
            put("hsSettings", readKeys(settings, appearanceKeys))
            put("hsBackground", readNonUriKeys(background))
            put("hsFont", readAllKeys(font))
            put("hsMusic", readAllKeys(music))
            put("hsSound", readAllKeys(sound))
        }
    }

    /**
     * Read only the metadata of a theme archive, so the user can confirm before it overwrites
     * anything. Returns null when the file is not a readable theme.
     *
     * Reads both the FolkPatch key names and HyperSU's own, and both containers, so a theme
     * from either source is recognised.
     */
    suspend fun readThemeMetadata(context: Context, uri: Uri): ThemeMetadata? {
        return withContext(Dispatchers.IO) {
            try {
                val json = openConfig(context, uri) ?: return@withContext null
                // FolkPatch nests nothing and prefixes with meta_; the first HyperSU release
                // used a metadata object. Accept either.
                val nested = json.optJSONObject("metadata")
                ThemeMetadata(
                    name = nested?.optString("name")?.takeIf { it.isNotBlank() }
                        ?: json.optString("meta_name", ""),
                    type = nested?.optString("type")?.takeIf { it.isNotBlank() }
                        ?: json.optString("meta_type", "phone"),
                    version = nested?.optString("version")?.takeIf { it.isNotBlank() }
                        ?: json.optString("meta_version", "1.0"),
                    author = nested?.optString("author")?.takeIf { it.isNotBlank() }
                        ?: json.optString("meta_author", ""),
                    description = nested?.optString("description")?.takeIf { it.isNotBlank() }
                        ?: json.optString("meta_description", ""),
                )
            } catch (e: Exception) {
                Log.e(TAG, "failed to read theme metadata", e)
                null
            }
        }
    }

    /**
     * Apply a theme archive: appearance keys, wallpaper keys and the wallpaper images.
     *
     * The language, if the theme names one, is applied on the main thread once the rest is done.
     * It cannot be applied from inside the IO block below: on Android 13 and up it assigns
     * applicationLocales, which makes the platform recreate the activity, and a recreation
     * triggered while the caller still holds its loading dialog is what made an import look
     * hung. Doing it last also means a failure earlier in the import never leaves the app
     * switched into another language.
     */
    suspend fun importTheme(context: Context, uri: Uri): Boolean {
        // A null result means the import failed; a successful one carries the language the theme
        // asked for, or null when it named none. Wrapping the tag keeps the two apart, which a
        // bare nullable string could not.
        val result = withContext(Dispatchers.IO) {
            val tempDir = File(context.cacheDir, "theme_import")
            try {
                val json = openConfig(context, uri) ?: return@withContext null

                // Images first: the preference URIs written below must already resolve.
                val localUris = unzipImages(context, uri, tempDir)

                // HyperSU-native sections win when present: they are this app's own keys, so a
                // round trip restores exactly what was exported. FolkPatch themes have only the
                // flat keys, which are mapped below.
                val hasNativeSections = json.has("hsSettings")

                json.optJSONObject("hsSettings")?.let { section ->
                    writeKeys(context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE), section)
                }
                val language = if (!hasNativeSections) {
                    applyFolkPatchAppearance(context, json)
                } else {
                    null
                }

                val backgroundPrefs = context.getSharedPreferences(
                    BackgroundConfig.PREFS_NAME,
                    Context.MODE_PRIVATE,
                )
                json.optJSONObject("hsBackground")?.let { section ->
                    writeKeys(backgroundPrefs, section)
                }
                if (!hasNativeSections) {
                    applyFolkPatchBackground(context, json)
                }

                // Each slot is decided on its own, the way FolkPatch decides it: a slot whose
                // switch is on and whose file is in the archive is repointed, a slot whose switch
                // is on but whose file is missing is cleared, and a slot whose switch is off is
                // left exactly as it was.
                //
                // Clearing everything first, as this used to, is the opposite of that last case:
                // importing a theme that carries only a main wallpaper would wipe the user's
                // per-page wallpapers, which is not what "import this theme" means.
                applyWallpaperSlots(context, backgroundPrefs, json, localUris)

                // 字体 / 音乐 / 音效：**归档里没有的段落一律不动**。
                //
                // 这一点和 FolkPatch 相反：它无条件 clear 再按归档内容重设，所以导入一个不含
                // 音乐的主题会把你现有的音乐删掉。主题商店里大部分主题只有配色和壁纸，那种
                // 行为等于「导入主题顺手清空个人设置」，这里改成缺省即保留。
                val fontSection = json.optJSONObject("hsFont")
                if (fontSection != null) {
                    applyFontSection(context, fontSection)
                } else if (json.has("fontMode") || json.has("isFontEnabled")) {
                    applyFolkPatchFont(context, json)
                }

                val musicSection = json.optJSONObject("hsMusic")
                if (musicSection != null) {
                    applyMusicSection(context, musicSection)
                } else if (json.has("isMusicEnabled")) {
                    applyFolkPatchMusic(context, json)
                }

                val soundSection = json.optJSONObject("hsSound")
                if (soundSection != null) {
                    applySoundSection(context, soundSection)
                } else if (json.has("isSoundEffectEnabled")) {
                    applyFolkPatchSound(context, json)
                }

                BackgroundConfig.load(context)
                Log.d(TAG, "theme imported from $uri")
                ImportResult(language)
            } catch (e: Exception) {
                Log.e(TAG, "failed to import theme", e)
                null
            } finally {
                tempDir.deleteRecursively()
            }
        } ?: return false

        // Applied on the main thread, after everything else has succeeded: on Android 13 and up
        // this assigns applicationLocales, which recreates the activity.
        result.language?.let { tag ->
            withContext(Dispatchers.Main) {
                runCatching { LocaleHelper.setLanguage(context, tag) }
                    .onFailure { Log.w(TAG, "failed to apply theme language $tag", it) }
            }
        }
        return true
    }

    /** A finished import: the language it asked for, or null when it named none. */
    private data class ImportResult(val language: String?)

    /**
     * Restore the built-in appearance and drop every wallpaper. Other settings are untouched.
     */
    suspend fun resetTheme(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val settings = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
                val background = context.getSharedPreferences(
                    BackgroundConfig.PREFS_NAME,
                    Context.MODE_PRIVATE,
                )

                settings.edit {
                    putInt("color_mode", 0)
                    putInt("key_color", 0)
                    putString("color_style", PaletteStyle.TonalSpot.name)
                    putString("color_spec", ColorSpec.SpecVersion.SPEC_2025.name)
                    remove("home_layout_style")
                }
                background.edit { clear() }
                // 字体/音乐/音效的 clear* 会同时删掉文件、清偏好并把内存态复位，
                // 比手工清三份偏好再调 load 更不容易漏。
                FontConfig.clearFont(context)
                MusicConfig.clearMusic(context)
                SoundEffectConfig.clearSoundEffect(context)
                SoundEffectConfig.clearStartupSound(context)

                imageKeyByName.keys.forEach { base ->
                    imageExtensions.forEach { ext ->
                        File(context.filesDir, "$base$ext").delete()
                    }
                }

                BackgroundConfig.reset()
                BackgroundConfig.load(context)
                Log.d(TAG, "theme reset")
                true
            } catch (e: Exception) {
                Log.e(TAG, "failed to reset theme", e)
                false
            }
        }
    }

    /** The file extension a theme is saved under. */
    val themeFileExtension: String get() = THEME_EXTENSION

    private fun readKeys(prefs: SharedPreferences, keys: List<String>): JSONObject =
        JSONObject().also { json ->
            keys.forEach { key ->
                when (val value = prefs.all[key]) {
                    null -> Unit
                    is Float -> json.put(key, value.toDouble())
                    else -> json.put(key, value)
                }
            }
        }

    /**
     * Writes a theme section back into preferences.
     *
     * Numbers are stored by the type the *key* uses, never by the type JSON happened to parse
     * into. org.json has a single number type and re-serialises a whole-valued Double as a bare
     * integer, so `0.5` stays a Double but `1.0` comes back as an Integer - and storing that with
     * putInt made every later `getFloat` throw ClassCastException. Since these preferences are
     * loaded from Application.onCreate, that turned one import into an app that could not start.
     */
    private fun writeKeys(prefs: SharedPreferences, json: JSONObject) {
        val editor = prefs.edit()
        json.keys().forEach { key ->
            when (val value = json.opt(key)) {
                null, JSONObject.NULL -> editor.remove(key)
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
                is Number -> if (key in INT_KEYS) {
                    editor.putInt(key, value.toInt())
                } else {
                    editor.putFloat(key, value.toFloat())
                }
                else -> editor.putString(key, value.toString())
            }
        }
        editor.apply()
    }

    /**
     * The appearance keys that really are integers.
     *
     * Everything else that is numeric is a Float. Keeping this list explicit is the point: it is
     * what stops a theme import from silently changing a key's storage type.
     */
    private val INT_KEYS = setOf("color_mode", "key_color")

    /**
     * Reads the theme JSON from either container.
     *
     * Encrypted archives are decrypted first; a plain ZIP is read directly. `theme.json` is
     * FolkPatch's name and `theme_config.json` is what the first HyperSU release wrote, so both
     * are accepted.
     */
    private fun openConfig(context: Context, uri: Uri): JSONObject? {
        val format = ThemeArchive.detect(context, uri)
        if (format == ThemeArchive.Format.Unknown) return null

        val stream = ThemeArchive.openZipStream(context, uri, format) ?: return null
        stream.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (name == ThemeArchive.CONFIG_ENTRY ||
                        name == ThemeArchive.LEGACY_CONFIG_ENTRY ||
                        name.endsWith("/${ThemeArchive.CONFIG_ENTRY}") ||
                        name.endsWith("/${ThemeArchive.LEGACY_CONFIG_ENTRY}")
                    ) {
                        return JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                    }
                }
            }
        }
        return null
    }

    private fun putImage(zip: ZipOutputStream, context: Context, baseName: String) {
        imageExtensions.forEach { ext ->
            val file = File(context.filesDir, "$baseName$ext")
            if (!file.exists()) return@forEach
            runCatching {
                zip.putNextEntry(ZipEntry(file.name))
                FileInputStream(file).use { it.copyTo(zip) }
                zip.closeEntry()
            }.onFailure { Log.w(TAG, "failed to pack ${file.name}", it) }
        }
    }

    /**
     * Extract the archive into filesDir and return the wallpaper URIs that were restored.
     *
     * This follows FolkPatch's model rather than a key-name match, because the two disagree in a
     * way that matters. FolkPatch walks a fixed list of slot base names and, for each, tries the
     * known extensions in turn; a slot it does not find is either left alone or cleared depending
     * on the switch that governs it. Matching entry names against preference keys instead, as
     * this used to, cannot express those decisions and silently dropped every slot whose name it
     * did not happen to recognise.
     *
     * Both containers are handled by [ThemeArchive]. The custom face is packed as `font.ttf` by
     * FolkPatch, and `video_background.*` is its name for what this app calls
     * `background_video.*`; those two names are normalised so the rest of the import can work in
     * this app's vocabulary.
     */
    private fun unzipImages(context: Context, uri: Uri, tempDir: File): Map<String, String> {
        tempDir.deleteRecursively()
        tempDir.mkdirs()

        val localUris = mutableMapOf<String, String>()
        // For a slot fed by several entries - only the focus card, which folds four sub-cards
        // into one - the rank of the sub-card that currently owns it.
        val claimedFocusRank = mutableMapOf<String, Int>()
        val entries = ThemeArchive.extractTo(context, uri, tempDir)

        entries.forEach { name ->
            val normalised = normaliseEntryName(name)
            val source = File(tempDir, name)
            if (!source.isFile) return@forEach

            val topLevel = normalised.substringBefore('/', "")
            // A wallpaper is a root-level entry whose base name is one of the slots and whose
            // extension is an image one. The base is the name minus its extension, so this is an
            // exact match on the slot rather than a prefix one.
            val dot = normalised.lastIndexOf('.')
            val base = if (dot > 0) normalised.substring(0, dot) else normalised
            val isWallpaper = !normalised.contains('/') &&
                base in WALLPAPER_SLOTS &&
                imageExtensions.any { normalised.endsWith(it) }
            // 导航图标是根目录下的 nav_icon_*.png，既不是壁纸也不是字体，所以单独认一次；
            // 漏掉它会让主题里的图标被静默丢弃。
            val isNavIcon = !isWallpaper && BottomBarIconConfig.DESTINATIONS.any {
                normalised == BottomBarIconConfig.fileName(it)
            }
            val isMedia = !isWallpaper && !isNavIcon && when {
                normalised.contains("/") -> topLevel in ARCHIVE_DIRS
                else -> FONT_EXTENSIONS.any { normalised.endsWith(it) }
            }
            if (!isWallpaper && !isMedia && !isNavIcon) return@forEach

            // Four Focus sub-cards all normalise onto this app's single hero card. The zip's
            // entry order is whatever the archive happens to use, so which of them wins cannot be
            // left to iteration order: the rank of the sub-card is tracked and only a better one
            // replaces the current picture. The kernel card - the status card, which is what the
            // hero card is here - ranks first.
            //
            // The check is before the copy, not after: two sub-cards can share an extension, and
            // writing then deleting would take the winning file with it.
            val rank = focusSubcardRank(name)
            if (isWallpaper && base in localUris) {
                val currentRank = claimedFocusRank[base]
                if (currentRank != null && (rank == null || currentRank <= rank)) return@forEach
                // A better sub-card is taking the slot. The one it displaces may have a different
                // extension, and that file has to go: the URI is about to point elsewhere, so the
                // old picture would sit on disk unreferenced and be picked up by a later export.
                KNOWN_WALLPAPER_EXTENSIONS.forEach { ext ->
                    File(context.filesDir, "$base$ext").takeIf { it.exists() }?.delete()
                }
            }

            val target = File(context.filesDir, normalised)
            // `music/…` 与 `sound_effects/…` 在全新安装上可能还不存在。
            target.parentFile?.mkdirs()
            runCatching { source.copyTo(target, overwrite = true) }

            if (isWallpaper) {
                localUris[base] = Uri.fromFile(target).toString()
                if (rank != null) claimedFocusRank[base] = rank
            }
        }
        // 图标文件落盘后要让底栏重画，否则界面仍显示内置图标。
        if (BottomBarIconConfig.DESTINATIONS.any {
                File(context.filesDir, BottomBarIconConfig.fileName(it)).isFile
            }
        ) {
            BottomBarIconConfig.notifyChanged()
        }
        return localUris
    }

    /**
     * Every wallpaper slot this app can hold, by its file base name.
     *
     * Used to recognise an archive entry as a wallpaper and to decide what to do with each slot,
     * so it has to list the slots rather than the preference keys: the entry name and the file
     * name are the same thing.
     */
    private val WALLPAPER_SLOTS = setOf(
        "background",
        "background_home",
        "background_kernel",
        "background_superuser",
        "background_module",
        "background_settings",
        "background_video",
        "focus_card_bg",
        "grid_working_card_background",
        "title_image",
        "dashboard_tile_bg_working",
        "dashboard_tile_bg_selinux",
        "dashboard_tile_bg_zygisk",
        "dashboard_tile_bg_seccomp",
    )

    /**
     * Points each wallpaper slot at what the theme shipped, following FolkPatch's decisions.
     *
     * FolkPatch does not treat the slots alike, and the differences are deliberate - its comments
     * say so. This reproduces them slot for slot:
     *
     * - **Main background**: applied only when `isBackgroundEnabled`; when it is off the existing
     *   wallpaper is left alone rather than cleared. FolkPatch's own comment reads "user might
     *   want to keep files", and importing a theme is not an instruction to delete anything.
     * - **Multi-background pages** (`background_home` and the four others): applied only when
     *   `isMultiBackgroundEnabled`, and then *every* page slot is decided - one whose file is
     *   absent is cleared. With the switch on, the theme is taken to describe the whole set.
     * - **Video, grid card, dashboard card, title image, focus card**: applied when their switch
     *   is on; the two whose switch also means "the picture itself" - the title image and the
     *   focus card - are cleared when it is off, because those switches exist only to show a
     *   picture and an off switch with a picture still set is a state the theme asked not to be
     *   in.
     *
     * @param localUris slot base name to the extracted file's URI, for the entries present.
     */
    private fun applyWallpaperSlots(
        context: Context,
        prefs: android.content.SharedPreferences,
        json: JSONObject,
        localUris: Map<String, String>,
    ) {
        // The URI a slot should end up with: the extracted file, or null when the theme asked for
        // the slot to be cleared. `unchanged` means leave whatever is stored.
        val unchanged = "\u0000"

        fun decide(base: String, enabled: Boolean, clearWhenOff: Boolean): String {
            val uri = localUris[base]
            return when {
                enabled && uri != null -> uri
                enabled -> if (clearWhenOff) "" else unchanged
                clearWhenOff -> ""
                else -> unchanged
            }
        }

        val multiEnabled = json.optBoolean("isMultiBackgroundEnabled", false)
        val pageSlots = listOf(
            "background_home",
            "background_kernel",
            "background_superuser",
            "background_module",
            "background_settings",
        )

        val decisions = buildMap {
            put("background", decide("background", json.optBoolean("isBackgroundEnabled", false), false))
            pageSlots.forEach { put(it, decide(it, multiEnabled, true)) }
            put("background_video", decide("background_video", json.optBoolean("isVideoBackgroundEnabled", false), false))
            put(
                "grid_working_card_background",
                decide(
                    "grid_working_card_background",
                    json.optBoolean("isGridWorkingCardBackgroundEnabled", false),
                    false,
                ),
            )
            put(
                "title_image",
                decide("title_image", json.optBoolean("isAdvancedTitleStyleEnabled", false), true),
            )
            put(
                "focus_card_bg",
                decide("focus_card_bg", json.optBoolean("isFocusCardBackgroundEnabled", false), true),
            )
            put(
                "dashboard_tile_bg_working",
                decide("dashboard_tile_bg_working", json.optBoolean("hasDashboardCardBg", false), true),
            )
            put(
                "dashboard_tile_bg_selinux",
                decide("dashboard_tile_bg_selinux", json.optBoolean("hasDashboardCardBg", false), true),
            )
            put(
                "dashboard_tile_bg_zygisk",
                decide("dashboard_tile_bg_zygisk", json.optBoolean("hasDashboardCardBg", false), true),
            )
            put(
                "dashboard_tile_bg_seccomp",
                decide("dashboard_tile_bg_seccomp", json.optBoolean("hasDashboardCardBg", false), true),
            )
        }

        prefs.edit {
            decisions.forEach { (base, decision) ->
                if (decision == unchanged) return@forEach
                val key = imageKeyByName[base] ?: return@forEach
                putString(key, decision.ifEmpty { null })
                // A slot being cleared has to take its file with it, or a later import that
                // reuses the name would find a stale picture still on disk.
                if (decision.isEmpty()) {
                    KNOWN_WALLPAPER_EXTENSIONS.forEach { ext ->
                        File(context.filesDir, "$base$ext").takeIf { it.exists() }?.delete()
                    }
                }
            }
        }
    }

    /** The extensions a wallpaper file may carry, matching FolkPatch's list. */
    private val KNOWN_WALLPAPER_EXTENSIONS = listOf(".jpg", ".png", ".gif", ".webp")

    /**
     * Rewrites the entry names that differ between the two managers onto HyperSU's own.
     *
     * `video_background.*` and the focus sub-card names are the differences; the rest -
     * `background*`, `background_*`, `focus_card_bg*` and `dashboard_tile_bg_*` - are written
     * under the same names by both apps.
     */
    private fun normaliseEntryName(name: String): String {
        // FolkPatch: video_background.mp4 -> HyperSU: background_video.mp4
        if (name.startsWith("video_background")) {
            return name.replaceFirst("video_background", "background_video")
        }
        // FolkPatch's Focus layout has four separate cards, each with its own picture
        // (focus_card_kernel_bg, _app_bg, _device_bg, _storage_bg). This app's Focus layout has a
        // single hero card, so there is nowhere for four pictures to go. They are folded onto the
        // one slot this app has, in FolkPatch's own card order, so a theme that carries any of
        // them still shows a picture instead of dropping all four.
        //
        // The order matters: the kernel card is the status card, which is what the hero card is
        // here, so it is tried first.
        FOCUS_SUBCARD_ENTRIES.firstOrNull { name.startsWith(it) }?.let { sub ->
            val ext = name.removePrefix(sub)
            if (ext.startsWith(".")) return "focus_card_bg$ext"
        }
        // The extension is carried over unchanged; only the base name is rewritten.
        val dot = name.lastIndexOf('.')
        val base = if (dot >= 0) name.substring(0, dot) else name
        val ext = if (dot >= 0) name.substring(dot) else ""
        val mapped = folkPatchEntryAliases[base] ?: return name
        return mapped + ext
    }

    /**
     * FolkPatch's four Focus card pictures, in the order they map onto this app's single hero
     * card. The kernel card is the status card, so it wins when a theme carries more than one.
     */
    private val FOCUS_SUBCARD_ENTRIES = listOf(
        "focus_card_kernel_bg",
        "focus_card_app_bg",
        "focus_card_device_bg",
        "focus_card_storage_bg",
    )

    /**
     * Which Focus sub-card an archive entry is, or null when it is not one.
     *
     * The index is the rank: lower is better, and [FOCUS_SUBCARD_ENTRIES] is ordered so the
     * kernel card comes first.
     */
    private fun focusSubcardRank(entryName: String): Int? {
        val index = FOCUS_SUBCARD_ENTRIES.indexOfFirst { entryName.startsWith(it) }
        return index.takeIf { it >= 0 }
    }

    /**
     * The JSON key FolkPatch writes to say whether a focus sub-card picture is present.
     *
     * Its markers are camelCase versions of the entry names: `focus_card_kernel_bg` becomes
     * `hasFocusCardKernelBg`. Kept as an explicit table rather than derived, so a rename on either
     * side is a compile-time edit here rather than a silent mismatch at import.
     */
    private fun hasMarkerKey(entryName: String): String = when (entryName) {
        "focus_card_kernel_bg" -> "hasFocusCardKernelBg"
        "focus_card_app_bg" -> "hasFocusCardAppBg"
        "focus_card_device_bg" -> "hasFocusCardDeviceBg"
        "focus_card_storage_bg" -> "hasFocusCardStorageBg"
        else -> "has${entryName}"
    }
}
