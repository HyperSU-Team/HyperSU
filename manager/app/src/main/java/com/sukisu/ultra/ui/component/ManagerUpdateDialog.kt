package com.sukisu.ultra.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.folk.FolkAlertDialog
import com.sukisu.ultra.ui.component.folk.FolkButtonDefaults
import com.sukisu.ultra.ui.theme.tokens.FolkShape
import com.sukisu.ultra.ui.theme.tokens.FolkType
import com.sukisu.ultra.ui.util.ManagerUpdate

/**
 * Offers a newer manager build.
 *
 * The layout is a header block, an optional notes block, then the actions stacked full-width
 * rather than a row of three text buttons. In a row the Chinese label for "download and install"
 * wrapped onto three lines and the whole bar read as cramped; a full-width primary action with
 * the two secondary ones beside it below gives the label the room it needs and makes the
 * primary choice obvious at a glance.
 *
 * The notes are only drawn when there are some. A release with no body written yet is common,
 * and the block for it used to render as an empty box or, before the parser was fixed, the
 * literal word "null".
 */
@Composable
fun ManagerUpdateDialog(
    update: ManagerUpdate,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onOpenPage: () -> Unit,
) {
    val hasApk = update.apkUrl != null
    val notes = update.notes.trim()

    FolkAlertDialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            UpdateHeader(versionName = update.versionName)

            if (notes.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = notes,
                        style = FolkType.Summary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            // Release notes can be long; the dialog stays readable rather than
                            // growing past the screen.
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // The primary action owns its row. A release without an APK can only be opened in
                // a browser, so the page action takes that place instead of disappearing.
                if (hasApk) {
                    Button(
                        onClick = onInstall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = FolkShape.CornerFull,
                        colors = FolkButtonDefaults.filledColors(),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_manager_update_download),
                            style = FolkType.Title,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                } else {
                    Button(
                        onClick = onOpenPage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = FolkShape.CornerFull,
                        colors = FolkButtonDefaults.filledColors(),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_manager_update_open_page),
                            style = FolkType.Title,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = FolkButtonDefaults.textColors(),
                    ) {
                        Text(
                            text = stringResource(android.R.string.cancel),
                            maxLines = 1,
                        )
                    }
                    // With no APK the page action already took the primary row, so only the
                    // cancel button remains here.
                    if (hasApk) {
                        TextButton(
                            onClick = onOpenPage,
                            modifier = Modifier.weight(1f),
                            colors = FolkButtonDefaults.textColors(),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_manager_update_open_page),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * The version banner at the top of the dialog.
 *
 * A tinted block with the app's update glyph rather than a bare line of text: the version is the
 * one thing the user needs to read, and it was previously the same weight and colour as the body.
 */
@Composable
private fun UpdateHeader(versionName: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ),
            )
            .padding(horizontal = 20.dp, vertical = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(FolkShape.CornerFull)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(Modifier.width(14.dp))

            Column {
                Text(
                    text = stringResource(R.string.settings_manager_update),
                    style = FolkType.Caption,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.settings_manager_update_available,
                        versionName,
                    ),
                    style = FolkType.Title,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
