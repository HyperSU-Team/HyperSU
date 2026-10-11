package com.sukisu.ultra.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.sukisu.ultra.data.repository.SettingsRepository
import com.sukisu.ultra.data.repository.SettingsRepositoryImpl
import com.sukisu.ultra.ui.theme.tokens.FolkShape
import com.sukisu.ultra.ui.webui.MonetColorsProvider

enum class ColorMode(val value: Int) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2),
    MONET_SYSTEM(3),
    MONET_LIGHT(4),
    MONET_DARK(5),
    DARK_AMOLED(6);

    companion object {
        fun fromValue(value: Int) = entries.find { it.value == value } ?: SYSTEM
    }

    val isSystem: Boolean get() = value == 0 || value == 3
    val isDark: Boolean get() = value == 2 || value == 5 || value == 6
    val isAmoled: Boolean get() = value == 6
    val isMonet: Boolean get() = value >= 3

    fun toNonMonetMode(): Int = when (this) {
        MONET_SYSTEM -> 0
        MONET_LIGHT -> 1
        MONET_DARK, DARK_AMOLED -> 2
        else -> value
    }

    fun toMonetMode(): Int = when (this) {
        SYSTEM -> 3
        LIGHT -> 4
        DARK -> 5
        else -> value
    }
}

data class AppSettings(
    val colorMode: ColorMode,
    val keyColor: Int,
    val paletteStyle: PaletteStyle,
    val colorSpec: ColorSpec.SpecVersion,
    val colorContrast: ColorContrast,
)

val PaletteStyle.supportsSpec2025: Boolean
    get() = this == PaletteStyle.TonalSpot ||
            this == PaletteStyle.Neutral ||
            this == PaletteStyle.Vibrant ||
            this == PaletteStyle.Expressive

fun ColorSpec.SpecVersion.effectiveFor(style: PaletteStyle): ColorSpec.SpecVersion =
    if (this == ColorSpec.SpecVersion.SPEC_2025 && !style.supportsSpec2025) {
        ColorSpec.SpecVersion.SPEC_2021
    } else {
        this
    }

object ThemeController {
    fun getAppSettings(repo: SettingsRepository = SettingsRepositoryImpl()): AppSettings {
        val colorMode = ColorMode.fromValue(repo.themeMode)
        val keyColor = repo.keyColor
        val paletteStyle = try {
            PaletteStyle.valueOf(repo.colorStyle)
        } catch (_: Exception) {
            PaletteStyle.TonalSpot
        }
        val colorSpec = try {
            ColorSpec.SpecVersion.valueOf(repo.colorSpec)
        } catch (_: Exception) {
            ColorSpec.SpecVersion.SPEC_2025
        }
        val colorContrast = ColorContrast.fromName(repo.colorContrast)

        return AppSettings(colorMode, keyColor, paletteStyle, colorSpec, colorContrast)
    }
}

/**
 * AMOLED variant of the scheme: only surface roles are darkened, accents are preserved.
 * Ported verbatim from FolkPatch `ui/theme/Theme.kt#toAmoled`.
 */
fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF050505),
    surfaceDim = Color(0xFF0D0D0D),
    surfaceContainer = Color(0xFF0A0A0A),
    surfaceVariant = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF121212),
    surfaceContainerHighest = Color(0xFF1A1A1A),
    surfaceBright = Color(0xFF1F1F1F),
)

// Custom-background adaptation moved to adaptColorScheme(): it also picks the neutral polarity
// from the wallpaper's own brightness and guards the text contrast.

// Default dark ripple alpha (~10% pressed) is nearly invisible on near-black
// surfaceContainer backgrounds, so boost it for clear press feedback at night.
// Ported verbatim from FolkPatch `ui/theme/Theme.kt`.
private val DarkRippleAlpha = RippleAlpha(
    draggedAlpha = 0.32f,
    focusedAlpha = 0.24f,
    hoveredAlpha = 0.16f,
    pressedAlpha = 0.24f,
)

/**
 * The single theme of the manager after the FolkPatch design port.
 *
 * FolkPatch structure (continuous-corner shapes, wrap-aware typography, standard motion
 * scheme, boosted dark ripple, AMOLED surfaces) driven by HyperSU's own appearance
 * settings (theme mode / key color / palette style / color spec).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HyperSUTheme(
    appSettings: AppSettings = ThemeController.getAppSettings(),
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = appSettings.colorMode.isDark ||
            (appSettings.colorMode.isSystem && systemDark)
    val amoled = appSettings.colorMode.isAmoled && darkTheme
    val dynamicColor = appSettings.keyColor == 0 &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val seedColor = when {
        dynamicColor -> Color.Unspecified
        appSettings.keyColor == 0 -> Color(0xFF4285F4) // system color below Android 12
        else -> Color(appSettings.keyColor)
    }

    val baseColorScheme = rememberKernelSUColorScheme(
        seedColor = seedColor,
        isDark = darkTheme,
        isAmoled = false,
        paletteStyle = appSettings.paletteStyle,
        colorSpec = appSettings.colorSpec,
        contrast = appSettings.colorContrast,
    )

    val amoledColorScheme = remember(baseColorScheme, amoled) {
        if (amoled) baseColorScheme.toAmoled() else baseColorScheme
    }

    // Same seed and palette style, opposite lightness. Wallpaper-adaptive content colours need a
    // complete neutral set of the other polarity; the generator is @Composable, so it cannot be
    // called from inside a remember that branches on runtime state - build both up front.
    val oppositeColorScheme = rememberKernelSUColorScheme(
        seedColor = seedColor,
        isDark = !darkTheme,
        isAmoled = false,
        paletteStyle = appSettings.paletteStyle,
        colorSpec = appSettings.colorSpec,
        contrast = appSettings.colorContrast,
    )

    val useCustomBackground = BackgroundConfig.isCustomBackgroundEnabled
    // One stable image drives the palette so the content colours do not flicker on page changes
    // while multi-background mode is on.
    val activeBackgroundUri = themeWallpaperUri()
    val wallpaperDim = BackgroundConfig.getEffectiveBackgroundDim(darkTheme)
    val wallpaperLuminance = BackgroundConfig.wallpaperLuminanceFor(activeBackgroundUri)

    // Wallpaper mode: the page background becomes transparent so [BackgroundLayer] reads through
    // it, the surfaces take the user's opacity, and the neutrals follow the wallpaper's own
    // brightness. FolkPalette sees the alpha on `background` and switches the grouped-screen
    // chrome over by itself.
    val wallpaperTheme = remember(
        amoledColorScheme,
        oppositeColorScheme,
        darkTheme,
        useCustomBackground,
        activeBackgroundUri,
        BackgroundConfig.customBackgroundOpacity,
        wallpaperDim,
        wallpaperLuminance,
    ) {
        if (useCustomBackground) {
            adaptColorScheme(amoledColorScheme, darkTheme, oppositeColorScheme, activeBackgroundUri)
        } else {
            WallpaperThemeResult(amoledColorScheme, null)
        }
    }

    val colorScheme = wallpaperTheme.colorScheme.animateAsState()

    // Wallpapers that never went through the save path (theme import, restored config) arrive
    // without a luminance record; fill those gaps once per wallpaper.
    val context = LocalContext.current
    LaunchedEffect(useCustomBackground, activeBackgroundUri) {
        if (useCustomBackground) {
            BackgroundManager.refreshMissingWallpaperLuminances(context)
        }
    }

    // The typeface is state kept in FontConfig, so changing it recomposes typography without a
    // restart. The cache inside FontConfig keeps Typeface.createFromFile off the hot path.
    val fontFamily = remember(FontConfig.fontMode, FontConfig.customFontFilename) {
        FontConfig.getFontFamily(context)
    }
    val typography = remember(fontFamily) { getTypography(fontFamily) }

    MaterialTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.standard(),
        typography = typography,
        shapes = FolkShape.materialShapes,
        content = {
            val rippleConfiguration = if (darkTheme) {
                RippleConfiguration(rippleAlpha = DarkRippleAlpha)
            } else {
                LocalRippleConfiguration.current
            }
            CompositionLocalProvider(
                LocalRippleConfiguration provides rippleConfiguration,
                // The contrast guard may push the dim above what the user asked for; the
                // preference itself is never rewritten.
                LocalWallpaperDim provides wallpaperTheme.renderDim,
            ) {
                MonetColorsProvider.UpdateCss(colorScheme)
                content()
            }
        },
    )
}

@Composable
@ReadOnlyComposable
fun isInDarkTheme(): Boolean {
    return when (LocalColorMode.current) {
        1, 4 -> false  // Force light mode
        2, 5, 6 -> true   // Force dark mode
        else -> isSystemInDarkTheme()  // Follow system (0 or default)
    }
}

val LocalColorMode = staticCompositionLocalOf { 0 }

val LocalEnableFloatingBottomBar = staticCompositionLocalOf { false }

val LocalEnableNavigationBadge = staticCompositionLocalOf { true }

val LocalModuleDescriptionMaxLines = staticCompositionLocalOf { 4 }
