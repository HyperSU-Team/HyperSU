package com.sukisu.ultra.ui.screen.susfs.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.folk.FolkAlertDialog
import com.sukisu.ultra.ui.component.folk.FolkButtonDefaults
import com.sukisu.ultra.ui.screen.susfs.util.AppInfo
import com.sukisu.ultra.ui.theme.tokens.FolkType

/**
 * The multi-select app picker for `Android/data` SUS paths, in the FolkPatch
 * design: a search field over a scrollable list of app rows with checkboxes,
 * and an Add/Cancel pair.
 */
@Composable
fun AddAppPathDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    isLoading: Boolean,
    apps: List<AppInfo> = emptyList(),
    onLoadApps: () -> Unit,
    existingSusPaths: Set<String> = emptySet()
) {
    var searchText by remember { mutableStateOf("") }
    var selectedApps by remember { mutableStateOf(setOf<AppInfo>()) }

    val addedPackageNames = remember(existingSusPaths) {
        existingSusPaths.mapNotNull { path ->
            val regex = Regex(".*/Android/data/([^/]+)/?.*")
            regex.find(path)?.groupValues?.get(1)
        }.toSet()
    }

    val availableApps = remember(apps, addedPackageNames) {
        apps.filter { app -> !addedPackageNames.contains(app.packageName) }
    }

    val filteredApps = remember(availableApps, searchText) {
        if (searchText.isBlank()) {
            availableApps
        } else {
            availableApps.filter { app ->
                app.appName.contains(searchText, ignoreCase = true) ||
                    app.packageName.contains(searchText, ignoreCase = true)
            }
        }
    }

    LaunchedEffect(showDialog) {
        if (showDialog && apps.isEmpty()) {
            onLoadApps()
        }
        if (showDialog) {
            selectedApps = setOf()
        }
    }

    if (!showDialog) return

    val resetAndDismiss = {
        onDismiss()
        selectedApps = setOf()
        searchText = ""
    }

    FolkAlertDialog(
        onDismissRequest = resetAndDismiss,
        width = 340.dp,
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.susfs_add_app_path),
                style = FolkType.Title,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text(stringResource(R.string.search_apps)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                if (selectedApps.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.selected_apps_count, selectedApps.size),
                        style = FolkType.Summary,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }

                if (filteredApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (availableApps.isEmpty()) {
                                stringResource(R.string.all_apps_already_added)
                            } else {
                                stringResource(R.string.no_apps_found)
                            },
                            style = FolkType.Summary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            FolkAppSelectableRow(
                                packageName = app.packageName,
                                checked = selectedApps.contains(app),
                                onCheckedChange = { checked ->
                                    selectedApps = if (checked) {
                                        selectedApps + app
                                    } else {
                                        selectedApps - app
                                    }
                                },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = resetAndDismiss,
                    colors = FolkButtonDefaults.textColors(),
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        if (selectedApps.isNotEmpty()) {
                            onConfirm(selectedApps.map { it.packageName })
                        }
                        selectedApps = setOf()
                        searchText = ""
                    },
                    enabled = selectedApps.isNotEmpty() && !isLoading,
                    colors = FolkButtonDefaults.textColors(),
                ) {
                    Text(stringResource(R.string.add))
                }
            }
        }
    }
}
