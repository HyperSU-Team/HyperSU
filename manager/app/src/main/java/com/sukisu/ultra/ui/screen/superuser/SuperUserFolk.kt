package com.sukisu.ultra.ui.screen.superuser

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CheckableDropdownMenuItem
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.data.model.AppInfo
import com.sukisu.ultra.ui.component.AppIconImage
import com.sukisu.ultra.ui.component.ScrollToTopOnChange
import com.sukisu.ultra.ui.component.SearchAppBar
import com.sukisu.ultra.ui.component.SearchStatus
import com.sukisu.ultra.ui.component.folk.FolkButtonDefaults
import com.sukisu.ultra.ui.component.folk.FolkLoadingIndicator
import com.sukisu.ultra.ui.component.folk.FolkPreference
import com.sukisu.ultra.ui.component.folk.FolkScaffold
import com.sukisu.ultra.ui.component.folk.FolkSettingsGroup
import com.sukisu.ultra.ui.component.folk.FolkSettingsSection
import com.sukisu.ultra.ui.component.folk.FolkSeverity
import com.sukisu.ultra.ui.component.folk.FolkStateTone
import com.sukisu.ultra.ui.component.folk.FolkStateView
import com.sukisu.ultra.ui.component.folk.FolkStatusBadge
import com.sukisu.ultra.ui.component.folk.FolkTitleStyle
import com.sukisu.ultra.ui.util.ownerNameForUid
import com.sukisu.ultra.ui.viewmodel.AppSortType

/**
 * The SuperUser tab in the FolkPatch design.
 *
 * One Folk group per UID: the launcher icon, the owner (or the single app's
 * label), the package name and the status words the tab has always shown for
 * root / umount / custom-profile / secondary-user. A group covering more than
 * one app expands in place on long press; tapping any row opens that UID's
 * profile.
 *
 * Everything is read from [SuperUserUiState] and driven through
 * [SuperUserActions], so search, sorting, filtering, refresh and the profile
 * route keep the behaviour the Material and Miuix variants had. Those variants
 * had no batch/multi-select bar, so neither does this one.
 */
@Composable
internal fun SuperUserPagerFolk(
    uiState: SuperUserUiState,
    actions: SuperUserActions,
    bottomInnerPadding: Dp,
) {
    val listState = rememberLazyListState()
    val refreshTick = remember { mutableIntStateOf(0) }
    val pullToRefreshState = rememberPullToRefreshState()
    val haptic = LocalHapticFeedback.current

    // The search field is owned by SearchAppBar; this mirror lets a text change
    // from anywhere else (page switch, clear) reach the field.
    var localSearchText by remember { mutableStateOf(uiState.searchStatus.searchText) }
    LaunchedEffect(uiState.searchStatus.searchText) {
        localSearchText = uiState.searchStatus.searchText
    }

    var expandedUids by remember { mutableStateOf(emptySet<Int>()) }
    val searching = localSearchText.isNotEmpty()

    val latestGroupedApps = rememberUpdatedState(uiState.groupedApps)
    val latestRefreshing = rememberUpdatedState(uiState.isRefreshing)

    FolkScaffold(
        titleStyle = FolkTitleStyle.None,
        topBar = {
            SearchAppBar(
                title = { Text(stringResource(R.string.superuser)) },
                searchText = localSearchText,
                onSearchTextChange = {
                    localSearchText = it
                    actions.onSearchTextChange(it)
                },
                onClearClick = {
                    localSearchText = ""
                    actions.onClearSearch()
                },
                leadingActions = {
                    IconButton(onClick = actions.onOpenSulog) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Article,
                            contentDescription = stringResource(R.string.settings_sulog),
                        )
                    }
                },
                dropdownContent = { SuperUserMenus(uiState, actions, haptic) },
            )
        },
    
        // The shell reserves the bottom bar and passes its height in as bottomInnerPadding,
        // so reserving it again here would add both allowances and push the last row under it.
        addBottomClearance = false,
    ) { innerPadding ->
        PullToRefreshBox(
            modifier = Modifier.fillMaxSize(),
            isRefreshing = uiState.isRefreshing,
            onRefresh = {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                actions.onRefresh()
                refreshTick.intValue++
            },
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isRefreshing = uiState.isRefreshing,
                    state = pullToRefreshState,
                )
            },
        ) {
            // FolkScaffold already reserves the floating-bar clearance, so the
            // list only needs the page's own bottom inset on top of it.
            val contentPadding = PaddingValues(
                top = 8.dp,
                bottom = innerPadding.calculateBottomPadding() + bottomInnerPadding,
            )

            ScrollToTopOnChange(
                listState,
                uiState.sortConfig,
                uiState.showSystemApps,
                uiState.showOnlyPrimaryUserApps,
                refreshTick.intValue,
                isBusy = { latestRefreshing.value },
            ) { latestGroupedApps.value }

            when {
                searching -> SearchResults(
                    uiState = uiState,
                    actions = actions,
                    listState = listState,
                    contentPadding = contentPadding,
                )

                uiState.error != null -> CenteredState {
                    FolkStateView(
                        title = stringResource(R.string.operation_failed),
                        icon = Icons.Rounded.ErrorOutline,
                        hint = uiState.error.message,
                        tone = FolkStateTone.Critical,
                        action = {
                            Button(
                                onClick = actions.onRefresh,
                                colors = FolkButtonDefaults.filledColors(),
                            ) {
                                Text(stringResource(R.string.network_retry))
                            }
                        },
                    )
                }

                uiState.groupedApps.isEmpty() && !uiState.hasLoaded ->
                    CenteredState { FolkLoadingIndicator() }

                uiState.groupedApps.isEmpty() -> CenteredState {
                    FolkStateView(
                        title = stringResource(R.string.no_apps_found),
                        icon = Icons.Filled.Search,
                    )
                }

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                ) {
                    if (uiState.recentlyInstalledResults.isNotEmpty()) {
                        item(key = "recently_installed") {
                            FolkSettingsSection(title = stringResource(R.string.recently_installed)) {
                                Column {
                                    uiState.recentlyInstalledResults.forEach { group ->
                                        GroupCard(
                                            group = group,
                                            expanded = false,
                                            onToggleExpand = null,
                                            onClickPrimary = { actions.onOpenProfile(group) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    items(uiState.groupedApps, key = { it.uid }) { group ->
                        val expanded = expandedUids.contains(group.uid)
                        GroupCard(
                            group = group,
                            expanded = expanded,
                            onToggleExpand = if (group.apps.size > 1) {
                                {
                                    expandedUids = if (expanded) {
                                        expandedUids - group.uid
                                    } else {
                                        expandedUids + group.uid
                                    }
                                }
                            } else {
                                null
                            },
                            onClickPrimary = { actions.onOpenProfile(group) },
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Menus
// ---------------------------------------------------------------------------

@Composable
private fun SuperUserMenus(
    uiState: SuperUserUiState,
    actions: SuperUserActions,
    haptic: HapticFeedback,
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }

    Box {
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
            val sortEntries = listOf(
                AppSortType.NAME to R.string.sort_by_name,
                AppSortType.PACKAGE_NAME to R.string.sort_by_package_name,
                AppSortType.INSTALL_TIME to R.string.sort_by_install_time,
                AppSortType.UPDATE_TIME to R.string.sort_by_update_time,
            )
            val sortConfig = uiState.sortConfig

            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 2)) {
                sortEntries.onEachIndexed { index, (type, resId) ->
                    SelectableDropdownMenuItem(
                        text = { Text(stringResource(resId)) },
                        selected = sortConfig.sortType == type,
                        selectedLeadingIcon = {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                            )
                        },
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            actions.onUpdateSortConfig(sortConfig.withType(type))
                            showSortMenu = false
                        },
                        shapes = MenuDefaults.itemShape(index = index, count = sortEntries.size),
                    )
                }
            }

            Spacer(Modifier.height(MenuDefaults.GroupSpacing))

            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 1, count = 2)) {
                CheckableDropdownMenuItem(
                    text = { Text(stringResource(R.string.sort_reverse)) },
                    checked = sortConfig.reversed,
                    checkedLeadingIcon = {
                        Icon(
                            Icons.Filled.Check,
                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                            contentDescription = null,
                        )
                    },
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        actions.onUpdateSortConfig(sortConfig.toggleReversed())
                        showSortMenu = false
                    },
                    shapes = MenuDefaults.itemShape(index = 0, count = 1),
                )
            }
        }
    }

    Box {
        IconButton(onClick = { showFilterMenu = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.settings),
            )
        }
        DropdownMenuPopup(
            expanded = showFilterMenu,
            onDismissRequest = { showFilterMenu = false },
        ) {
            val multiUser = uiState.userIds.size > 1
            val filterCount = if (multiUser) 2 else 1
            DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                CheckableDropdownMenuItem(
                    text = { Text(stringResource(R.string.show_system_apps)) },
                    checked = uiState.showSystemApps,
                    checkedLeadingIcon = {
                        Icon(
                            Icons.Filled.Check,
                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                            contentDescription = null,
                        )
                    },
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        actions.onToggleShowSystemApps()
                        showFilterMenu = false
                    },
                    shapes = MenuDefaults.itemShape(index = 0, count = filterCount),
                )
                if (multiUser) {
                    CheckableDropdownMenuItem(
                        text = { Text(stringResource(R.string.show_only_primary_user_apps)) },
                        checked = uiState.showOnlyPrimaryUserApps,
                        checkedLeadingIcon = {
                            Icon(
                                Icons.Filled.Check,
                                modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                contentDescription = null,
                            )
                        },
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            actions.onToggleShowOnlyPrimaryUserApps()
                            showFilterMenu = false
                        },
                        shapes = MenuDefaults.itemShape(index = 1, count = filterCount),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Search results
// ---------------------------------------------------------------------------

@Composable
private fun SearchResults(
    uiState: SuperUserUiState,
    actions: SuperUserActions,
    listState: androidx.compose.foundation.lazy.LazyListState,
    contentPadding: PaddingValues,
) {
    if (uiState.searchStatus.resultStatus != SearchStatus.ResultStatus.SHOW &&
        uiState.searchResults.isEmpty()
    ) {
        CenteredState {
            when (uiState.searchStatus.resultStatus) {
                SearchStatus.ResultStatus.EMPTY -> FolkStateView(
                    title = stringResource(R.string.no_apps_found),
                    icon = Icons.Filled.Search,
                )

                else -> FolkLoadingIndicator()
            }
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        items(uiState.searchResults, key = { it.uid }) { group ->
            GroupCard(
                group = group,
                expanded = true,
                onToggleExpand = null,
                onClickPrimary = { actions.onOpenProfile(group) },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Rows
// ---------------------------------------------------------------------------

@Composable
private fun CenteredState(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        content()
    }
}

/**
 * One UID group as a Folk settings group: the header row always, plus a row per
 * member app when the group covers more than one and is expanded. The group's
 * own item visibility supplies the expand/collapse animation.
 */
@Composable
private fun GroupCard(
    group: GroupedApps,
    expanded: Boolean,
    onToggleExpand: (() -> Unit)?,
    onClickPrimary: () -> Unit,
) {
    val summaryText = if (group.apps.size > 1) {
        stringResource(R.string.group_contains_apps, group.apps.size)
    } else {
        group.primary.displayIdentifier
    }
    val multiApp = group.apps.size > 1

    FolkSettingsGroup(modifier = Modifier.padding(bottom = 12.dp)) {
        item(key = "header-${group.uid}") {
            AppRow(
                app = group.primary,
                title = if (multiApp) ownerNameForUid(group.uid) else group.primary.label,
                summary = summaryText,
                selected = expanded,
                onClick = onClickPrimary,
                onLongClick = onToggleExpand,
                trailing = { GroupStatusBadges(group) },
            )
        }
        group.apps.forEach { app ->
            item(key = "app-${group.uid}-${app.packageName}", visible = multiApp && expanded) {
                AppRow(
                    app = app,
                    title = app.label,
                    summary = app.displayIdentifier,
                    selected = group.matchedIdentifiers.contains(app.displayIdentifier),
                    indent = true,
                    onClick = onClickPrimary,
                )
            }
        }
    }
}

/**
 * A Folk settings row carrying the real launcher icon.
 *
 * [FolkPreference]'s leading slot only takes a vector, so the app icon is
 * placed beside it; the preference still owns the title, summary, tap and
 * long-press behaviour, which is what this tab needs.
 */
@Composable
private fun AppRow(
    app: AppInfo,
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
    indent: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconImage(
            packageInfo = app.packageInfo,
            label = app.label,
            modifier = Modifier
                .padding(start = if (indent) 52.dp else 16.dp)
                .size(if (indent) 28.dp else 40.dp),
        )
        FolkPreference(
            title = title,
            summary = summary,
            modifier = Modifier.weight(1f),
            selected = selected,
            onClick = onClick,
            onLongClick = onLongClick,
            trailing = trailing,
        )
    }
}

/**
 * The status words this tab has always shown, coloured through the Folk
 * severity tokens. The UID is carried as the metadata of the first badge, so
 * every row still states which UID it belongs to.
 */
@Composable
private fun GroupStatusBadges(group: GroupedApps) {
    val userId = group.uid / 100000

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (group.anyAllowSu) {
            FolkStatusBadge(
                severity = FolkSeverity.Positive,
                label = "ROOT",
                meta = group.uid.toString(),
            )
        }
        if (group.shouldUmount) {
            FolkStatusBadge(severity = FolkSeverity.Caution, label = "UMOUNT")
        }
        if (group.anyCustom) {
            FolkStatusBadge(severity = FolkSeverity.Info, label = "CUSTOM")
        }
        if (userId != 0) {
            FolkStatusBadge(severity = FolkSeverity.Neutral, label = "USER $userId")
        }
    }
}
