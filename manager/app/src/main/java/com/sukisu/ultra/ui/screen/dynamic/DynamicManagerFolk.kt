package com.sukisu.ultra.ui.screen.dynamic

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
                                    "size 0x${state.size.toString(16)}, hash ${state.hashSummary}"
                                } else {
                                    stringResource(R.string.dynamic_manager_not_set)
                                },
                                icon = Icons.Rounded.Security,
                                onClick = {},
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
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                            }
                        } else {
                            state.apps.forEach { app ->
                                item(key = app.packageName) {
                                    FolkNavigationPreference(
                                        title = app.label,
                                        summary = app.packageName,
                                        icon = Icons.Rounded.Security,
                                        enabled = !state.submitting,
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
