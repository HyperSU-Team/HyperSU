package com.sukisu.ultra.ui.component.folk

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A titled block of settings.
 *
 * The title sits *outside* the group surface (never inside the rounded card),
 * which is what makes a section read as one shelf rather than a card with its
 * own caption. A section is followed by [FolkSettingsDimens.SectionSpacing].
 */
@Composable
fun FolkSettingsSection(
    title: String? = null,
    description: String? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        bottom = FolkSettingsDimens.SectionSpacing,
    ),
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = folkSectionTitleStyle(),
                color = folkSectionTitleColor(),
                modifier = Modifier.padding(
                    start = FolkSettingsDimens.ScreenPadding + FolkSettingsDimens.SectionTitleIndent,
                    end = FolkSettingsDimens.ScreenPadding,
                    bottom = if (description == null) {
                        FolkSettingsDimens.SectionTitleSpacing
                    } else {
                        2.dp
                    },
                ),
            )
        }
        if (description != null) {
            Text(
                text = description,
                style = folkPreferenceSummaryStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = FolkSettingsDimens.ScreenPadding + FolkSettingsDimens.SectionTitleIndent,
                    end = FolkSettingsDimens.ScreenPadding,
                    bottom = FolkSettingsDimens.SectionTitleSpacing,
                ),
            )
        }
        content()
    }
}
