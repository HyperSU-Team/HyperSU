package com.sukisu.ultra.ui.screen.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contrast
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
import com.sukisu.ultra.ui.component.folk.FolkSettingsSectionGroup
import com.sukisu.ultra.ui.component.folk.FolkValuePreference
import com.sukisu.ultra.ui.theme.ColorContrast
import kotlinx.coroutines.launch

/**
 * The palette's contrast level.
 *
 * The Grid layout's card arrangement used to be a row here as well. It is not a choice any more:
 * the cards are always drawn side by side, which is the arrangement FolkPatch uses and the one the
 * Grid layout was composed around, so a row that could only ever move away from it was a way to
 * get a worse screen rather than a preference.
 */
@Composable
fun AppearanceLayoutOptionsSection(
    snackBarHost: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { SettingsRepositoryImpl() }

    var colorContrast by remember { mutableStateOf(repo.colorContrast) }

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
