package com.sukisu.ultra.ui.screen.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.folk.FolkNavigationPreference
import com.sukisu.ultra.ui.component.folk.FolkSettingsScaffold
import com.sukisu.ultra.ui.component.folk.FolkSettingsSectionGroup

/**
 * The About page in the FolkPatch design: the app mark and version above a
 * grouped list of the project's links.
 *
 * The links themselves still come from [AboutUiState], which the caller builds
 * by parsing the same localised HTML it always did, so a translated build keeps
 * its own links.
 */
@Composable
fun AboutScreenFolk(
    state: AboutUiState,
    actions: AboutScreenActions,
) {
    FolkSettingsScaffold(
        title = state.title,
        onBack = actions.onBack,
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 40.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The adaptive launcher icon, which brings its own background. This used to draw
                // the transparent-cutout foreground on a hard-coded white plate, which left a
                // white glyph on a white square in a light theme.
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(22.dp)),
                )
                Text(
                    modifier = Modifier.padding(top = 14.dp),
                    text = state.appName,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = state.versionName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            FolkSettingsSectionGroup(title = state.title) {
                state.links.forEach { link ->
                    item(key = link.url) {
                        FolkNavigationPreference(
                            title = link.fullText,
                            onClick = { actions.onOpenLink(link.url) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
