package com.sukisu.ultra.ui.screen.template

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.data.model.TemplateInfo
import com.sukisu.ultra.ui.component.folk.FolkButtonDefaults
import com.sukisu.ultra.ui.component.folk.FolkLoadingIndicator
import com.sukisu.ultra.ui.component.folk.FolkPreference
import com.sukisu.ultra.ui.component.folk.FolkScaffold
import com.sukisu.ultra.ui.component.folk.FolkSettingsGroup
import com.sukisu.ultra.ui.component.folk.FolkStateTone
import com.sukisu.ultra.ui.component.folk.FolkStateView
import com.sukisu.ultra.ui.component.statustag.StatusTag

/**
 * The App Profile template library, in the FolkPatch design.
 *
 * Each template is a Folk row carrying its identity, description and the UID /
 * GID / context tags it always showed; the local/remote origin is a tag too.
 * Import and export still go through the clipboard, and the create action is
 * the extended FAB. Everything is read from [TemplateUiState] and reported
 * through [TemplateActions], so the view model and the editor route are
 * untouched.
 */
@Composable
fun AppProfileTemplateScreenFolk(
    state: TemplateUiState,
    actions: TemplateActions,
    snackBarHost: SnackbarHostState,
) {
    val haptic = LocalHapticFeedback.current
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()

    val threshold = with(LocalDensity.current) { 100.dp.toPx() }
    val fabExpanded by remember {
        var lastIndex = 0
        var lastOffset = 0
        var scrollDelta = 0f
        var expanded = true
        derivedStateOf {
            val currentIndex = listState.firstVisibleItemIndex
            val currentOffset = listState.firstVisibleItemScrollOffset
            val delta = if (currentIndex == lastIndex) {
                (currentOffset - lastOffset).toFloat()
            } else if (currentIndex > lastIndex) {
                100f
            } else {
                -100f
            }
            scrollDelta = (scrollDelta + delta).coerceIn(-threshold, threshold)
            lastIndex = currentIndex
            lastOffset = currentOffset
            if (currentIndex == 0) {
                expanded = true
                scrollDelta = 0f
            } else if (expanded && scrollDelta >= threshold) {
                expanded = false
                scrollDelta = 0f
            } else if (!expanded && scrollDelta <= -threshold) {
                expanded = true
                scrollDelta = 0f
            }
            expanded
        }
    }

    FolkScaffold(
        title = stringResource(R.string.settings_profile_template),
        onBack = actions.onBack,
        snackbarHostState = snackBarHost,
        actions = {
            var showDropdown by remember { mutableStateOf(false) }
            IconButton(onClick = { showDropdown = true }) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.app_profile_import_export),
                )
            }
            DropdownMenuPopup(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false },
            ) {
                val menuItems = listOf(
                    R.string.app_profile_import_from_clipboard to actions.onImport,
                    R.string.app_profile_export_to_clipboard to actions.onExport,
                )
                DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                    menuItems.forEachIndexed { index, (resId, action) ->
                        SelectableDropdownMenuItem(
                            selected = false,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                action()
                                showDropdown = false
                            },
                            text = { Text(stringResource(resId)) },
                            shapes = MenuDefaults.itemShape(index = index, count = menuItems.size),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            SmallExtendedFloatingActionButton(
                expanded = fabExpanded,
                onClick = actions.onCreateTemplate,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.app_profile_template_create)) },
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            isRefreshing = state.isRefreshing,
            onRefresh = {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                actions.onRefresh(true)
            },
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isRefreshing = state.isRefreshing,
                    state = pullToRefreshState,
                )
            },
        ) {
            val templateList = state.templateList

            when {
                state.error != null -> FolkStateView(
                    title = stringResource(R.string.operation_failed),
                    hint = state.error.message,
                    tone = FolkStateTone.Critical,
                    modifier = Modifier.fillMaxSize(),
                    action = {
                        androidx.compose.material3.TextButton(
                            onClick = { actions.onRefresh(false) },
                            colors = FolkButtonDefaults.textColors(),
                        ) {
                            Text(stringResource(R.string.network_retry))
                        }
                    },
                )

                templateList.isEmpty() && state.isRefreshing -> FolkLoadingIndicator(
                    modifier = Modifier.fillMaxSize(),
                )

                templateList.isEmpty() && state.offline -> FolkStateView(
                    title = stringResource(R.string.network_offline),
                    modifier = Modifier.fillMaxSize(),
                    action = {
                        androidx.compose.material3.Button(
                            onClick = { actions.onRefresh(false) },
                            colors = FolkButtonDefaults.filledColors(),
                        ) {
                            Text(stringResource(R.string.network_retry))
                        }
                    },
                )

                templateList.isEmpty() -> FolkStateView(
                    title = stringResource(R.string.app_profile_template_import_empty),
                    hint = stringResource(R.string.app_profile_template_create),
                    modifier = Modifier.fillMaxSize(),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 16.dp + innerPadding.calculateBottomPadding() + 72.dp,
                    ),
                ) {
                    itemsIndexed(
                        items = templateList,
                        key = { _, template -> template.id },
                    ) { _, template ->
                        TemplateItem(
                            template = template,
                            onClick = { actions.onOpenTemplate(template) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateItem(
    template: TemplateInfo,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        FolkSettingsGroup {
            item {
                FolkPreference(
                    title = template.name,
                    summary = buildString {
                        append(template.id)
                        if (template.author.isNotEmpty()) append("@${template.author}")
                        if (template.description.isNotEmpty()) {
                            append("\n")
                            append(template.description)
                        }
                    },
                    onClick = onClick,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusTag(
                label = "UID: ${template.uid}",
                contentColor = MaterialTheme.colorScheme.onPrimary,
                backgroundColor = MaterialTheme.colorScheme.primary,
            )
            StatusTag(
                label = "GID: ${template.gid}",
                contentColor = MaterialTheme.colorScheme.onPrimary,
                backgroundColor = MaterialTheme.colorScheme.primary,
            )
            if (template.context.isNotEmpty()) {
                StatusTag(
                    label = template.context,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                )
            }
            StatusTag(
                label = if (template.local) "local" else "remote",
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
            )
        }
    }
}
