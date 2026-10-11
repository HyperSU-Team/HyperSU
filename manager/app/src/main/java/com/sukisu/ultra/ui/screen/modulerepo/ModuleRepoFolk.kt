package com.sukisu.ultra.ui.screen.modulerepo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.data.model.RepoModule
import com.sukisu.ultra.ui.component.ScrollToTopOnChange
import com.sukisu.ultra.ui.component.SearchAppBar
import com.sukisu.ultra.ui.component.folk.FolkButtonDefaults
import com.sukisu.ultra.ui.component.folk.FolkLoadingIndicator
import com.sukisu.ultra.ui.component.folk.FolkScaffold
import com.sukisu.ultra.ui.component.folk.FolkStateTone
import com.sukisu.ultra.ui.component.folk.FolkStateView
import com.sukisu.ultra.ui.component.folk.FolkTitleStyle
import com.sukisu.ultra.ui.component.statustag.StatusTag
import com.sukisu.ultra.ui.theme.tokens.FolkShape
import com.sukisu.ultra.ui.theme.tokens.FolkType

/**
 * The online module repository, in the FolkPatch design.
 *
 * The module cards keep every field they showed - id, author, summary up to
 * four lines, the META/ZYGISK tags, the star count and the last release time -
 * on the Folk surface, and the sort menu keeps all four orderings. Search and
 * pull-to-refresh behave as before.
 */
@Composable
fun ModuleRepoScreenFolk(
    state: ModuleRepoUiState,
    actions: ModuleRepoActions,
) {
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val refreshTick = remember { mutableIntStateOf(0) }
    val pullToRefreshState = rememberPullToRefreshState()

    val inSearchMode = state.searchStatus.searchText.isNotEmpty()

    FolkScaffold(
        titleStyle = FolkTitleStyle.None,
        topBar = {
            SearchAppBar(
                title = { Text(text = stringResource(R.string.module_repos)) },
                searchText = state.searchStatus.searchText,
                onSearchTextChange = actions.onSearchTextChange,
                onClearClick = actions.onClearSearch,
                onBackClick = actions.onBack,
                dropdownContent = {
                    ModuleRepoSortMenu(state, actions, haptic)
                },
            )
        },
    ) { innerPadding ->
        // A fetch that has not come back yet, rather than "the list is empty". The two are not the
        // same: an index that legitimately serves no modules produced an empty list, and the old
        // test left the spinner turning for ever with no way out.
        val isLoading = state.modules.isEmpty() && state.error == null && !state.loadedOnce

        when {
            state.error != null -> FolkStateView(
                // The message comes from the failure itself, so it is not a resource. The
                // localized offline page below is the one used when the network is the cause.
                title = stringResource(R.string.operation_failed),
                hint = if (state.offline) {
                    stringResource(R.string.network_offline)
                } else {
                    state.error.message
                },
                tone = FolkStateTone.Critical,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
                action = {
                    androidx.compose.material3.TextButton(
                        onClick = actions.onRefresh,
                        colors = FolkButtonDefaults.textColors(),
                    ) {
                        Text(stringResource(R.string.network_retry))
                    }
                },
            )

            isLoading -> FolkLoadingIndicator(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
            )

            state.modules.isEmpty() -> FolkStateView(
                title = stringResource(R.string.module_repo_empty),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
                action = {
                    Button(
                        onClick = actions.onRefresh,
                        colors = FolkButtonDefaults.filledColors(),
                    ) {
                        Text(stringResource(R.string.network_retry))
                    }
                },
            )

            else -> {
                val latestModules = rememberUpdatedState(state.modules)
                val latestRefreshing = rememberUpdatedState(state.isRefreshing)
                ScrollToTopOnChange(
                    if (inSearchMode) searchListState else listState,
                    state.sortOrder,
                    refreshTick.intValue,
                    isBusy = { latestRefreshing.value },
                ) { latestModules.value }

                PullToRefreshBox(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = innerPadding.calculateTopPadding()),
                    isRefreshing = state.isRefreshing,
                    onRefresh = {
                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        actions.onRefresh()
                        refreshTick.intValue++
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
                    RepoModuleList(
                        modules = if (inSearchMode) state.searchResults else state.modules,
                        listState = if (inSearchMode) searchListState else listState,
                        modifier = Modifier.fillMaxSize(),
                        bottomPadding = innerPadding.calculateBottomPadding(),
                        onModuleClick = actions.onOpenRepoDetail,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleRepoSortMenu(
    state: ModuleRepoUiState,
    actions: ModuleRepoActions,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
) {
    var showSortMenu by remember { mutableStateOf(false) }

    IconButton(onClick = { showSortMenu = true }) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Sort,
            contentDescription = stringResource(R.string.menu_sort),
        )
    }
    DropdownMenuPopup(
        expanded = showSortMenu,
        onDismissRequest = { showSortMenu = false },
    ) {
        val sortOptions = listOf(
            RepoSort.UPDATED to R.string.module_repos_sort_updated,
            RepoSort.CREATED to R.string.module_repos_sort_created,
            RepoSort.NAME to R.string.module_repos_sort_name,
            RepoSort.STARS to R.string.module_repos_sort_stars,
        )
        DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
            sortOptions.forEachIndexed { index, (order, resId) ->
                SelectableDropdownMenuItem(
                    text = { Text(stringResource(resId)) },
                    selected = state.sortOrder == order,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        actions.onSetSortOrder(order)
                        showSortMenu = false
                    },
                    shapes = MenuDefaults.itemShape(index = index, count = sortOptions.size),
                    selectedLeadingIcon = {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun RepoModuleList(
    modules: List<RepoModule>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    onModuleClick: (RepoModule) -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(13.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 16.dp + bottomPadding,
        ),
    ) {
        items(modules, key = { it.moduleId }, contentType = { "module" }) { module ->
            RepoModuleCard(module = module, onClick = { onModuleClick(module) })
        }
    }
}

@Composable
private fun RepoModuleCard(
    module: RepoModule,
    onClick: () -> Unit,
) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = FolkShape.Corner20,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(16.dp, 14.dp, 16.dp, 10.dp)) {
            if (module.moduleName.isNotEmpty()) {
                Text(
                    text = module.moduleName,
                    style = FolkType.Title,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (module.moduleId.isNotEmpty()) {
                Text(
                    text = "ID: ${module.moduleId}",
                    style = FolkType.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${stringResource(R.string.module_author)}: ${module.authors}",
                style = FolkType.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (module.summary.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = module.summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = FolkType.Summary,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 4,
                )
            }

            if (module.metamodule || module.zygisk) {
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (module.metamodule) {
                        StatusTag(
                            label = "META",
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            backgroundColor = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (module.zygisk) {
                        StatusTag(
                            label = "ZYGISK",
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                        )
                    }
                }
            }

            HorizontalDivider(thickness = Dp.Hairline)
            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (module.stargazerCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = module.stargazerCount.toString(),
                            style = FolkType.Caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (module.latestReleaseTime.isNotEmpty()) {
                    Text(
                        text = module.latestReleaseTime,
                        style = FolkType.Caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
