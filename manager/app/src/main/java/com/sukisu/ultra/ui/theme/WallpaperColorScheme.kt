package com.sukisu.ultra.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

// 壁纸有效亮度低于该阈值时，内容采用深色主题的中性色（浅色文字）。
private const val WALLPAPER_DARK_THRESHOLD = 0.5f

/**
 * 自定义壁纸生效时，把中性配色改写成「跟着壁纸明暗走」的一套。
 *
 * 与 FolkPatch 的 `adaptColorScheme` 等价，但生成方式换成了 HyperSU 的 materialkolor 配色：
 * 调用方预先算好同一种子、同一 palette style 的**反极性**方案 [opposite]，这里按壁纸亮度决定
 * 用哪一套当「中性色」，避免在 remember 里按状态分支调用 @Composable 的配色生成器。
 *
 * 规则：
 *  - 页面背景全透明，[BackgroundLayer] 从这里透出来；`FolkPalette.onCustomBackground` 由
 *    background 的 alpha 自动识别，无需另行分支。
 *  - surface 系列取中性方案的颜色、再叠用户设置的 [BackgroundConfig.customBackgroundOpacity]，
 *    卡片因此浮在壁纸上。
 *  - 强调色容器（primary/secondary/tertiary/error）保持不透明：它们与各自的 on 色配对，一旦
 *    降 alpha 就会失去对比度。
 *  - 浅色模式压在暗壁纸上时，用 [guardedDim] 抬高实际遮罩，兜底文字对比度（用户偏好不改写，
 *    只在 [WallpaperThemeResult.renderDim] 里给出绘制值）。
 */
internal fun adaptColorScheme(
    base: ColorScheme,
    darkTheme: Boolean,
    opposite: ColorScheme,
    activeBackgroundUri: String?,
): WallpaperThemeResult {
    val wallpaperDim = BackgroundConfig.getEffectiveBackgroundDim(darkTheme)
    val effectiveLuminance = BackgroundConfig.wallpaperLuminanceFor(activeBackgroundUri)
        ?.let { it * (1f - wallpaperDim) }

    // 夜间始终用深色中性方案：不因壁纸偏亮把整套 UI 翻成浅色（避免「发白」），
    // 可读性交给下面的对比度保护抬高遮罩。浅色模式才按壁纸明暗决定，以照顾暗壁纸。
    val useLightContent = if (darkTheme) {
        true
    } else {
        effectiveLuminance?.let { it < WALLPAPER_DARK_THRESHOLD } ?: false
    }

    val neutral = if (useLightContent == darkTheme) base else opposite

    val opacity = BackgroundConfig.customBackgroundOpacity
    // Only `surface` and `surfaceContainer` take the user's opacity.
    //
    // Every other role stays opaque, which is what FolkPatch does and what makes the wallpaper
    // mode readable: `surfaceContainer` is the grouped card, so it has to let the picture
    // through, but the roles *inside* a card - `surfaceContainerHigh` for an icon tile or a
    // facts block, `surfaceContainerLow`, `surfaceVariant` - must not, or their text ends up
    // sitting directly on the photograph. `surfaceDim` and `surfaceBright` are the same case.
    //
    // The page background is transparent so the wallpaper layer shows through it.
    val adapted = base.copy(
        background = Color.Transparent,
        surface = neutral.surface.copy(alpha = opacity),
        surfaceDim = neutral.surfaceDim,
        surfaceBright = neutral.surfaceBright,
        surfaceContainerLowest = neutral.surfaceContainerLowest,
        surfaceContainerLow = neutral.surfaceContainerLow,
        surfaceContainer = neutral.surfaceContainer.copy(alpha = opacity),
        surfaceContainerHigh = neutral.surfaceContainerHigh,
        surfaceContainerHighest = neutral.surfaceContainerHighest,
        surfaceVariant = neutral.surfaceVariant,
        onBackground = neutral.onBackground,
        onSurface = neutral.onSurface,
        onSurfaceVariant = neutral.onSurfaceVariant,
        outline = neutral.outline,
        outlineVariant = neutral.outlineVariant,
        inverseSurface = neutral.inverseSurface,
        inverseOnSurface = neutral.inverseOnSurface,
    )

    // 浅色中性内容压在暗壁纸上时才需要抬遮罩；夜间遮罩完全交给用户的夜间暗度滑块。
    val renderDim = if (useLightContent && !darkTheme) {
        guardedDim(wallpaperDim, adapted) ?: wallpaperDim
    } else {
        wallpaperDim
    }

    return WallpaperThemeResult(adapted, renderDim)
}
