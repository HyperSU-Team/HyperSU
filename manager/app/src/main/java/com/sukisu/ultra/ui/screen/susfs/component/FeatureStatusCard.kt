package com.sukisu.ultra.ui.screen.susfs.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.folk.FolkAlertDialog
import com.sukisu.ultra.ui.component.folk.FolkButtonDefaults
import com.sukisu.ultra.ui.component.folk.FolkPreference
import com.sukisu.ultra.ui.component.folk.FolkSeverity
import com.sukisu.ultra.ui.component.folk.FolkStatusBadge
import com.sukisu.ultra.ui.component.folk.FolkSwitchPreference
import com.sukisu.ultra.ui.screen.susfs.util.EnabledFeature
import com.sukisu.ultra.ui.screen.susfs.util.SuSFSManager
import com.sukisu.ultra.ui.theme.tokens.FolkType
import kotlinx.coroutines.launch

/**
 * One row of the enabled-features list, in the FolkPatch design: the feature
 * name, an optional "configurable" summary, an enabled/disabled status badge
 * and - when the feature is configurable - the log toggle dialog.
 */
@Composable
fun FeatureStatusCard(
    feature: EnabledFeature,
    onRefresh: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showLogConfigDialog by remember { mutableStateOf(false) }
    var logEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        logEnabled = SuSFSManager.getEnableLogState()
    }

    if (showLogConfigDialog) {
        FolkAlertDialog(
            onDismissRequest = {
                coroutineScope.launch { logEnabled = SuSFSManager.getEnableLogState() }
                showLogConfigDialog = false
            },
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.susfs_log_config_title),
                    style = FolkType.Title,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.susfs_log_config_description),
                    style = FolkType.Summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FolkSwitchPreference(
                    title = stringResource(R.string.susfs_enable_log_label),
                    checked = logEnabled,
                    onCheckedChange = { checked -> logEnabled = checked },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch { logEnabled = SuSFSManager.getEnableLogState() }
                            showLogConfigDialog = false
                        },
                        colors = FolkButtonDefaults.textColors(),
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                if (SuSFSManager.setEnableLog(context, logEnabled)) {
                                    onRefresh?.invoke()
                                }
                                showLogConfigDialog = false
                            }
                        },
                        modifier = Modifier.heightIn(min = 40.dp),
                        colors = FolkButtonDefaults.textColors(),
                    ) {
                        Text(stringResource(R.string.susfs_apply))
                    }
                }
            }
        }
    }

    FolkPreference(
        title = feature.name,
        modifier = modifier,
        summary = if (feature.canConfigure) {
            stringResource(R.string.susfs_feature_configurable)
        } else {
            null
        },
        enabled = true,
        onClick = if (feature.canConfigure) {
            {
                coroutineScope.launch { logEnabled = SuSFSManager.getEnableLogState() }
                showLogConfigDialog = true
            }
        } else {
            null
        },
        trailing = {
            FolkStatusBadge(
                severity = if (feature.isEnabled) FolkSeverity.Positive else FolkSeverity.Neutral,
                label = feature.statusText,
            )
        },
    )
}
