package com.sukisu.ultra.ui.screen.susfs.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.folk.FolkPreference
import com.sukisu.ultra.ui.component.folk.FolkSettingsGroup
import com.sukisu.ultra.ui.screen.susfs.component.BottomActionButtons
import com.sukisu.ultra.ui.screen.susfs.component.FolkAppIcon
import com.sukisu.ultra.ui.screen.susfs.component.FolkCountedSection
import com.sukisu.ultra.ui.screen.susfs.component.FolkCountedSectionHeader
import com.sukisu.ultra.ui.screen.susfs.component.FolkRowIconButton
import com.sukisu.ultra.ui.screen.susfs.component.FolkSusfsEmptyState
import com.sukisu.ultra.ui.screen.susfs.component.ResetButton
import com.sukisu.ultra.ui.screen.susfs.component.rememberSusfsApp
import com.sukisu.ultra.ui.screen.susfs.util.AppInfoCache
import com.sukisu.ultra.ui.theme.tokens.FolkType
import com.sukisu.ultra.ui.viewmodel.SuperUserViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The SUS Paths tab, in the FolkPatch design: the app-scoped `Android/data`
 * paths grouped per package with their icon and label, then the remaining
 * paths, then the add/reset actions.
 *
 * The grouping rules are unchanged, including the `/sys/fs/cgroup…/uid_N`
 * mapping through the superuser app list that the Miuix variant added.
 */
@Composable
fun SusPathsContent(
    susPaths: Set<String>,
    isLoading: Boolean,
    onAddPath: () -> Unit,
    onAddAppPath: () -> Unit,
    onRemovePath: (String) -> Unit,
    onEditPath: ((String) -> Unit)? = null,
    forceRefreshApps: Boolean = false,
    onReset: (() -> Unit)? = null
) {
    // The uid→package mapping follows the app list as it reloads; the old source observed a
    // plain synchronized getter through snapshotFlow, which can only ever emit once.
    val superUserApps by SuperUserViewModel.appsFlow.collectAsStateWithLifecycle()

    LaunchedEffect(superUserApps) {
        if (superUserApps.isNotEmpty()) {
            runCatching { AppInfoCache.clearCache() }
        }
    }

    LaunchedEffect(forceRefreshApps) {
        if (forceRefreshApps) {
            runCatching { AppInfoCache.clearCache() }
        }
    }

    val (appPathGroups, otherPaths) = remember(susPaths, superUserApps) {
        val appPathRegex = Regex(".*/Android/data/([^/]+)/?.*")
        val uidPathRegex = Regex("/sys/fs/cgroup(?:/[^/]+)*/uid_([0-9]+)")
        val appPathMap = mutableMapOf<String, MutableList<String>>()
        val uidToPackageMap = mutableMapOf<String, String>()
        val others = mutableListOf<String>()

        runCatching {
            superUserApps.forEach { app: SuperUserViewModel.AppInfo ->
                runCatching {
                    val uid = app.packageInfo.applicationInfo?.uid
                    if (uid != null) {
                        uidToPackageMap[uid.toString()] = app.packageName
                    }
                }
            }
        }

        susPaths.forEach { path ->
            val appDataMatch = appPathRegex.find(path)
            val uidMatch = uidPathRegex.find(path)

            when {
                appDataMatch != null -> {
                    val packageName = appDataMatch.groupValues[1]
                    appPathMap.getOrPut(packageName) { mutableListOf() }.add(path)
                }
                uidMatch != null -> {
                    val packageName = uidToPackageMap[uidMatch.groupValues[1]]
                    if (packageName != null) {
                        appPathMap.getOrPut(packageName) { mutableListOf() }.add(path)
                    } else {
                        others.add(path)
                    }
                }
                else -> others.add(path)
            }
        }

        val sortedAppGroups = appPathMap.toList()
            .sortedBy { it.first }
            .map { (packageName, paths) -> packageName to paths.sorted() }

        Pair(sortedAppGroups, others.sorted())
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (appPathGroups.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 22.dp),
            ) {
                FolkCountedSectionHeader(
                    title = stringResource(R.string.app_paths_section),
                    count = appPathGroups.size,
                )
                appPathGroups.forEach { (packageName, paths) ->
                    AppPathGroup(
                        packageName = packageName,
                        paths = paths,
                        onDeleteGroup = { paths.forEach { path -> onRemovePath(path) } },
                        onEditGroup = onEditPath?.let { edit -> { edit(paths.first()) } },
                        isLoading = isLoading,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        if (otherPaths.isNotEmpty()) {
            FolkCountedSection(
                title = stringResource(R.string.other_paths_section),
                count = otherPaths.size,
            ) {
                otherPaths.forEach { path ->
                    item(key = "path_$path") {
                        FolkPreference(
                            title = path,
                            icon = Icons.Default.Folder,
                            trailing = {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    onEditPath?.let { edit ->
                                        FolkRowIconButton(
                                            icon = Icons.Default.Edit,
                                            contentDescription = stringResource(R.string.edit),
                                            tint = MaterialTheme.colorScheme.primary,
                                            enabled = !isLoading,
                                            onClick = { edit(path) },
                                        )
                                    }
                                    FolkRowIconButton(
                                        icon = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.delete),
                                        tint = MaterialTheme.colorScheme.error,
                                        enabled = !isLoading,
                                        onClick = { onRemovePath(path) },
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }

        if (susPaths.isEmpty()) {
            FolkSusfsEmptyState(message = stringResource(R.string.susfs_no_paths_configured))
        }
    }

    BottomActionButtons(
        primaryButtonText = stringResource(R.string.add_custom_path),
        onPrimaryClick = onAddPath,
        secondaryButtonText = stringResource(R.string.susfs_apply),
        onSecondaryClick = onAddAppPath,
        isLoading = isLoading,
    )

    if (onReset != null && susPaths.isNotEmpty()) {
        ResetButton(
            title = stringResource(R.string.susfs_reset_paths_title),
            onClick = onReset,
        )
    }
}

/** One package and the `Android/data` paths configured under it. */
@Composable
private fun AppPathGroup(
    packageName: String,
    paths: List<String>,
    onDeleteGroup: () -> Unit,
    onEditGroup: (() -> Unit)?,
    isLoading: Boolean,
) {
    val cached = rememberSusfsApp(packageName)
    val label = cached.appName.ifBlank { packageName }

    FolkSettingsGroup {
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FolkAppIcon(packageName = packageName, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = label,
                            style = FolkType.Title,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (label != packageName) {
                            Text(
                                text = packageName,
                                style = FolkType.Caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        onEditGroup?.let { edit ->
                            FolkRowIconButton(
                                icon = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.edit),
                                tint = MaterialTheme.colorScheme.primary,
                                enabled = !isLoading,
                                onClick = edit,
                            )
                        }
                        FolkRowIconButton(
                            icon = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.error,
                            enabled = !isLoading,
                            onClick = onDeleteGroup,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                paths.forEach { path ->
                    Text(
                        text = path,
                        style = FolkType.Caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                    )
                }
            }
        }
    }
}
