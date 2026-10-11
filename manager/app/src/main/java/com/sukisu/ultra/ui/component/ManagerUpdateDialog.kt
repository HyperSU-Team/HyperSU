package com.sukisu.ultra.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.folk.FolkAlertDialog
import com.sukisu.ultra.ui.component.folk.FolkButtonDefaults
import com.sukisu.ultra.ui.util.ManagerUpdate

/**
 * Offers a newer manager build.
 *
 * The primary action is to install, when the release carries an APK; a release without one can
 * only be opened in a browser. Either way the release page stays reachable as the secondary
 * action, because the notes may be worth reading first.
 */
@Composable
fun ManagerUpdateDialog(
    update: ManagerUpdate,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onOpenPage: () -> Unit,
) {
    FolkAlertDialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(
                    R.string.settings_manager_update_available,
                    update.versionName,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (update.notes.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = update.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        // Release notes can be long; the dialog stays readable rather than
                        // growing past the screen.
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(
                    onClick = onDismiss,
                    colors = FolkButtonDefaults.textColors(),
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = onOpenPage,
                    colors = FolkButtonDefaults.textColors(),
                ) {
                    Text(stringResource(R.string.settings_manager_update_open_page))
                }
                if (update.apkUrl != null) {
                    Button(
                        onClick = onInstall,
                        colors = FolkButtonDefaults.filledColors(),
                    ) {
                        Text(stringResource(R.string.settings_manager_update_download))
                    }
                }
            }
        }
    }
}
