package com.sukisu.ultra.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 横幅图片底部渐变的 fading 颜色，移植自 FolkPatch `ui/theme/BannerFadeColor.kt`。
 *
 * 动态取色时跟 `surface` 走（渐变才能融进页面），否则用固定的深/浅底色。
 */
@Composable
fun bannerFadeColor(): Color {
    val isDark = isSystemInDarkTheme()
    val colorScheme = MaterialTheme.colorScheme
    val isDynamic = colorScheme.primary != colorScheme.secondary
    return when {
        isDynamic -> colorScheme.surface
        isDark -> Color(0xFF222222)
        else -> Color.White
    }
}
