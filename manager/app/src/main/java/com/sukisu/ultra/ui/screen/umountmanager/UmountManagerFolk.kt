package com.sukisu.ultra.ui.screen.umountmanager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.sukisu.ultra.ui.component.folk.FolkLoadingIndicator
import com.sukisu.ultra.ui.component.folk.FolkScaffold
import com.sukisu.ultra.ui.component.folk.FolkSettingsGroup
import com.sukisu.ultra.ui.component.folk.FolkStateView
import com.sukisu.ultra.ui.theme.tokens.FolkShape
import com.sukisu.ultra.ui.theme.tokens.FolkType

/**
 * The custom umount path manager, in the FolkPatch design.
 *
 * A restart notice, then one row per path with its decoded flags and a delete
 * action, and finally the clear/apply buttons. Adding a path is the same dialog
 * as before, showing the flags hint so the numeric value stays discoverable.
 * Every action is still the caller's, so the shell work is untouched.
 */
@Composable
fun UmountManagerFolk(
    state: UmountManagerUiState,
    actions: UmountManagerActions,
) {
    FolkScaffold(
        title = stringResource(R.string.umount_path_manager),
        onBack = actions.onBack,
        actions = {
            IconButton(onClick = actions.onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
            }
        },
        floatingActionButton = {
            SmallFloatingActionButton(onClick = actions.onAddClick) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            androidx.compose.material3.Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = FolkShape.Corner16,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.umount_path_restart_notice),
                        style = FolkType.Summary,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    FolkLoadingIndicator()
                }
            } else if (state.pathList.isEmpty()) {
                FolkStateView(
                    title = stringResource(R.string.umount_no_paths),
                    icon = Icons.Outlined.Folder,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 16.dp + innerPadding.calculateBottomPadding() + 72.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.pathList, key = { it.path }) { entry ->
                        UmountPathRow(
                            entry = entry,
                            onDelete = { actions.onDeletePath(entry) },
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = actions.onClearCustomPaths,
                                colors = FolkButtonDefaults.tonalColors(),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(text = stringResource(R.string.clear_custom_paths))
                            }

                            Button(
                                onClick = actions.onApplyConfig,
                                colors = FolkButtonDefaults.filledColors(),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(text = stringResource(R.string.apply_config))
                            }
                        }
                    }
                }
            }
        }

        if (state.showAddDialog) {
            AddUmountPathDialog(
                onDismiss = actions.onDismissAddDialog,
                onConfirm = actions.onAddPath,
            )
        }
    }
}

@Composable
private fun UmountPathRow(
    entry: UmountPathEntry,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current

    FolkSettingsGroup {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.path,
                        style = FolkType.Title,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = buildString {
                            append(stringResource(R.string.flags))
                            append(": ")
                            append(entry.flags.toUmountFlagName(context))
                        },
                        style = FolkType.Caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddUmountPathDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit,
) {
    var path by rememberSaveable { mutableStateOf("") }
    var flags by rememberSaveable { mutableStateOf("0") }

    FolkAlertDialog(onDismissRequest = onDismiss, width = 340.dp) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.add_umount_path),
                style = FolkType.Title,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            OutlinedTextField(
                value = path,
                onValueChange = { path = it },
                label = { Text(stringResource(R.string.mount_path)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = flags,
                onValueChange = { flags = it },
                label = { Text(stringResource(R.string.umount_flags)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.umount_flags_hint),
                style = FolkType.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onDismiss,
                    colors = FolkButtonDefaults.textColors(),
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
                Button(
                    onClick = {
                        val flagsInt = flags.toIntOrNull() ?: 0
                        onConfirm(path, flagsInt)
                    },
                    enabled = path.isNotBlank(),
                    colors = FolkButtonDefaults.filledColors(),
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        }
    }
}
