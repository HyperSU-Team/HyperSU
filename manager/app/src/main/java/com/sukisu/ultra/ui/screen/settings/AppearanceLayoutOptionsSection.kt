package com.sukisu.ultra.ui.screen.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sukisu.ultra.R
import com.sukisu.ultra.data.repository.SettingsRepositoryImpl
import com.sukisu.ultra.data.repository.STATS_TOP_GRID
import com.sukisu.ultra.data.repository.STATS_TOP_LIST
import com.sukisu.ultra.ui.component.folk.FolkSettingsSectionGroup
import com.sukisu.ultra.ui.component.folk.FolkValuePreference
import com.sukisu.ultra.ui.theme.ColorContrast
import kotlinx.coroutines.launch

/**
 * The Grid layout's card arrangement and the palette's contrast level.
 *
 * Both are small settings with no picture attached, so they share one section rather than
 * getting one each: a section per two-row choice would make the screen longer without making
 * anything easier to find.
 */
@Composable
fun AppearanceLayoutOptionsSection(
    snackBarHost: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { SettingsRepositoryImpl() }

    var statsTopLayout by remember { mutableStateOf(repo.statsTopLayout) }
    var colorContrast by remember { mutableStateOf(repo.colorContrast) }

    val statsLabels = listOf(
        STATS_TOP_LIST to stringResource(R.string.stats_top_layout_list),
        STATS_TOP_GRID to stringResource(R.string.stats_top_layout_grid),
    )
    val contrastLabels = ColorContrast.entries.map { entry ->
        entry to stringResource(
            when (entry) {
                ColorContrast.STANDARD -> R.string.color_contrast_standard
                ColorContrast.MEDIUM -> R.string.color_contrast_medium
                ColorContrast.HIGH -> R.string.color_contrast_high
            }
        )
    }

    FolkSettingsSectionGroup(title = stringResource(R.string.layout_options_section_title)) {
        item(key = "stats_top_layout") {
            val index = statsLabels.indexOfFirst { it.first == statsTopLayout }.coerceAtLeast(0)
            FolkValuePreference(
                title = stringResource(R.string.stats_top_layout),
                summary = stringResource(R.string.stats_top_layout_summary),
                icon = Icons.Outlined.ViewAgenda,
                value = statsLabels[index].second,
                onClick = {
                    // Two states, so the row cycles rather than opening a chooser.
                    val next = statsLabels[(index + 1) % statsLabels.size].first
                    statsTopLayout = next
                    repo.statsTopLayout = next
                    scope.launch {
                        snackBarHost.showSnackbar(context.getString(R.string.stats_top_layout_changed))
                    }
                },
            )
        }

        item(key = "color_contrast") {
            val index = contrastLabels.indexOfFirst { it.first.name == colorContrast }.coerceAtLeast(0)
            FolkValuePreference(
                title = stringResource(R.string.color_contrast),
                summary = stringResource(R.string.color_contrast_summary),
                icon = Icons.Outlined.Contrast,
                value = contrastLabels[index].second,
                onClick = {
                    val next = contrastLabels[(index + 1) % contrastLabels.size].first
                    colorContrast = next.name
                    repo.colorContrast = next.name
                    scope.launch {
                        snackBarHost.showSnackbar(context.getString(R.string.color_contrast_changed))
                    }
                },
            )
        }
    }
}
