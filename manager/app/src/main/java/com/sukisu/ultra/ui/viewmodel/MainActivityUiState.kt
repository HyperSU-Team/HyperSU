package com.sukisu.ultra.ui.viewmodel

import androidx.compose.runtime.Immutable
import com.sukisu.ultra.ui.theme.AppSettings

@Immutable
data class MainActivityUiState(
    val appSettings: AppSettings,
    val pageScale: Float,
    val enableFloatingBottomBar: Boolean,
    val enableNavigationBadge: Boolean,
    val enableSwipeDismiss: Boolean,
    val pagerInterceptionMode: Int,
    val moduleDescriptionMaxLines: Int = 4,
    val simpleListBottomBar: Boolean = false,
)
