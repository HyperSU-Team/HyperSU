package com.sukisu.ultra.ui.screen.dynamic

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.KsuIsValid
import com.sukisu.ultra.ui.component.folk.FolkNavigationPreference
import com.sukisu.ultra.ui.component.folk.FolkScaffold
import com.sukisu.ultra.ui.component.folk.FolkSettingsSectionGroup
import com.sukisu.ultra.ui.component.folk.FolkTitleStyle

@Composable
fun DynamicManagerFolk(
    state: DynamicManagerState,
    actions: DynamicManagerActions,
) {
    FolkScaffold(
        title = stringResource(R.string.dynamic_manager),
        titleStyle = FolkTitleStyle.Flexible,
        onBack = actions.onBack,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            contentPadding = PaddingValues(
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item {
                KsuIsValid {
                    FolkSettingsSectionGroup(title = stringResource(R.string.dynamic_manager_current)) {
                        item {
                            FolkNavigationPreference(
                                title = stringResource(R.string.dynamic_manager_current),
                                summary = if (state.isSet) {
                                    "0x${state.size.toString(16)}  ${state.hashSummary}"
                                } else {
                                    stringResource(R.string.dynamic_manager_not_set)
                                },
                                icon = Icons.Rounded.Security,
                                onClick = {},
                                trailing = {},
                            )
                        }
                        item {
                            FolkNavigationPreference(
                                title = stringResource(R.string.dynamic_manager_clear),
                                icon = Icons.Rounded.DeleteForever,
                                enabled = state.isSet && !state.submitting,
                                onClick = actions.onClear,
                            )
                        }
                    }

                    FolkSettingsSectionGroup(title = stringResource(R.string.dynamic_manager_apps)) {
                        if (state.apps.isEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.dynamic_manager_no_apps),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                )
                            }
                        } else {
                            state.apps.forEach { app ->
                                item(key = app.packageName) {
                                    ManagerAppRow(
                                        app = app,
                                        submitting = state.submitting,
                                        onClick = { actions.onSelectApp(app) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One manager app: its own icon and label, the package it comes from, and what the kernel thinks
 * of it. Only unauthorized rows react to a tap - the others are already known signatures.
 */
@Composable
private fun ManagerAppRow(
    app: DynamicManagerApp,
    submitting: Boolean,
    onClick: () -> Unit,
) {
    val statusMessage = when {
        app.isDynamic -> R.string.dynamic_manager_status_dynamic
        app.isBuiltIn -> R.string.dynamic_manager_status_builtin
        else -> R.string.dynamic_manager_status_none
    }
    val statusColor = when {
        app.isDynamic -> MaterialTheme.colorScheme.tertiary
        app.isAuthorized -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val canGrant = !app.isAuthorized && !submitting

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = canGrant, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(statusMessage),
                style = MaterialTheme.typography.labelLarge,
                color = statusColor,
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 72.dp),
            thickness = Dp.Hairline,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun AppIcon(app: DynamicManagerApp) {
    val icon = app.icon
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
    } else {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = app.label.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
