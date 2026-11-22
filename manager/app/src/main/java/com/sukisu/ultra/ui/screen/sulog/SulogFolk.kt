package com.sukisu.ultra.ui.screen.sulog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material3.CheckableDropdownMenuItem
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.ScrollToTopOnChange
import com.sukisu.ultra.ui.component.SearchAppBar
import com.sukisu.ultra.ui.component.folk.FolkAlertDialog
import com.sukisu.ultra.ui.component.folk.FolkButtonDefaults
import com.sukisu.ultra.ui.component.folk.FolkChoicePreference
import com.sukisu.ultra.ui.component.folk.FolkPreference
import com.sukisu.ultra.ui.component.folk.FolkScaffold
import com.sukisu.ultra.ui.component.folk.FolkSettingsGroup
import com.sukisu.ultra.ui.component.folk.FolkSeverity
import com.sukisu.ultra.ui.component.folk.FolkStateView
import com.sukisu.ultra.ui.component.folk.FolkStatusBadge
import com.sukisu.ultra.ui.component.folk.FolkTitleStyle
import com.sukisu.ultra.ui.component.statustag.StatusTag
import com.sukisu.ultra.ui.theme.tokens.FolkShape
import com.sukisu.ultra.ui.theme.tokens.FolkType
import com.sukisu.ultra.ui.util.SulogEntry
import com.sukisu.ultra.ui.util.SulogEventFilter

/**
 * The su audit log, in the FolkPatch design.
 *
 * The rows carry the same information as before - a title derived from the
 * event type, a description, a timestamp and the summary tags - but they are
 * Folk rows and the status/danger notice is a Folk banner. Tapping a row opens
 * the same monospaced field dump, now in a [FolkAlertDialog].
 */
@Composable
fun SulogScreenFolk(
    state: SulogScreenState,
    actions: SulogActions,
) {
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val fileSelector = buildSulogFileSelector(state.files, state.selectedFilePath)

    var selectedEntry by remember { mutableStateOf<SulogEntry?>(null) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var localSearchText by remember { mutableStateOf(state.searchText) }

    LaunchedEffect(state.searchText) {
        localSearchText = state.searchText
    }

    if (selectedEntry != null) {
        SulogDetailDialog(
            entry = selectedEntry!!,
            onDismiss = { selectedEntry = null },
        )
    }

    FolkScaffold(
        titleStyle = FolkTitleStyle.None,
        topBar = {
            SearchAppBar(
                title = { Text(stringResource(R.string.settings_sulog)) },
                searchText = localSearchText,
                onSearchTextChange = {
                    localSearchText = it
                    actions.onSearchTextChange(it)
                },
                onClearClick = {
                    localSearchText = ""
                    actions.onSearchTextChange("")
                },
                onBackClick = actions.onBack,
                leadingActions = {
                    IconButton(onClick = actions.onCleanFile) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = stringResource(R.string.sulog_clean_title),
                        )
                    }
                },
                dropdownContent = {
                    Box {
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(
                                imageVector = Icons.Filled.FilterList,
                                contentDescription = stringResource(R.string.sulog_filter_title),
                            )
                        }
                        DropdownMenuPopup(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false },
                        ) {
                            val filters = SulogEventFilter.entries
                            DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                                filters.forEachIndexed { index, filter ->
                                    CheckableDropdownMenuItem(
                                        text = { Text(sulogFilterLabel(filter)) },
                                        checked = filter in state.selectedFilters,
                                        checkedLeadingIcon = {
                                            Icon(
                                                Icons.Filled.Check,
                                                modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                                contentDescription = null,
                                            )
                                        },
                                        onCheckedChange = {
                                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                            actions.onToggleFilter(filter)
                                        },
                                        shapes = MenuDefaults.itemShape(index = index, count = filters.size),
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        val inSearchMode = localSearchText.isNotEmpty()

        val latestVisibleEntries = rememberUpdatedState(state.visibleEntries)
        ScrollToTopOnChange(
            if (inSearchMode) searchListState else listState,
            state.searchText,
            state.selectedFilters,
            state.selectedFilePath,
        ) { latestVisibleEntries.value }

        LazyColumn(
            state = if (inSearchMode) searchListState else listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 16.dp + innerPadding.calculateBottomPadding(),
            ),
        ) {
            if (!inSearchMode) {
                item {
                    SulogStatusSection(state, actions)
                }

                if (fileSelector.items.isNotEmpty()) {
                    item {
                        FolkSettingsGroup {
                            item {
                                FolkChoicePreference(
                                    title = stringResource(R.string.sulog_log_files),
                                    options = fileSelector.items,
                                    selectedIndex = fileSelector.selectedIndex,
                                    onSelect = { index ->
                                        state.files.getOrNull(index)?.let { file ->
                                            actions.onSelectFile(file.path)
                                        }
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }

            sulogEntriesSection(
                entries = state.visibleEntries,
                errorMessage = state.errorMessage,
                onEntryClick = { selectedEntry = it },
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.sulogEntriesSection(
    entries: List<SulogEntry>,
    errorMessage: String?,
    onEntryClick: (SulogEntry) -> Unit,
) {
    when {
        errorMessage != null -> item {
            FolkStateView(
                title = stringResource(R.string.sulog_failed_to_load),
                hint = errorMessage,
                tone = com.sukisu.ultra.ui.component.folk.FolkStateTone.Critical,
                modifier = Modifier.fillParentMaxSize(),
            )
        }

        entries.isEmpty() -> item {
            FolkStateView(
                title = stringResource(R.string.sulog_log_empty),
                icon = Icons.Outlined.Article,
                modifier = Modifier.fillParentMaxSize(),
            )
        }

        else -> itemsIndexed(entries, key = { index, entry -> "$index-${entry.key}" }) { _, entry ->
            SulogEntryRow(entry = entry, onClick = { onEntryClick(entry) })
        }
    }
}

@Composable
private fun SulogEntryRow(entry: SulogEntry, onClick: () -> Unit) {
    val tags = sulogEntrySummaryTags(entry)

    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        FolkSettingsGroup {
            item {
                FolkPreference(
                    title = sulogEntryTitle(entry),
                    summary = sulogEntryDescription(entry),
                    onClick = onClick,
                    trailing = {
                        sulogEntryStatus(entry)?.let {
                            Text(
                                text = it,
                                style = FolkType.Caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
        }

        val timestamp = entry.timestampText
        if (timestamp != null || tags.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (timestamp != null) {
                    Text(
                        text = timestamp,
                        style = FolkType.Caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                tags.take(3).forEach { tag ->
                    StatusTag(
                        label = tag,
                        backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun SulogStatusSection(
    state: SulogScreenState,
    actions: SulogActions,
) {
    when (state.sulogStatus) {
        "unsupported" -> SulogNotice(stringResource(R.string.sulog_unsupported_title))

        "managed" -> SulogNotice(stringResource(R.string.feature_status_managed_summary))

        "supported" if !state.isSulogEnabled -> SulogNotice(
            message = stringResource(R.string.sulog_disabled_title),
            actionLabel = stringResource(R.string.sulog_enable_action),
            onAction = actions.onEnableSulog,
        )

        else -> Unit
    }
}

@Composable
private fun SulogNotice(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        shape = FolkShape.Corner16,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FolkStatusBadge(
                    severity = FolkSeverity.Critical,
                    label = "",
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    text = message,
                    style = FolkType.Title,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            if (actionLabel != null && onAction != null) {
                androidx.compose.material3.TextButton(
                    onClick = onAction,
                    colors = FolkButtonDefaults.textColors(),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun SulogDetailDialog(
    entry: SulogEntry,
    onDismiss: () -> Unit,
) {
    FolkAlertDialog(
        onDismissRequest = onDismiss,
        width = 340.dp,
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = sulogEntryTitle(entry),
                style = FolkType.Title,
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                SelectionContainer {
                    Text(
                        text = sulogEntryDetailText(entry),
                        fontFamily = FontFamily.Monospace,
                        style = FolkType.Caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                androidx.compose.material3.TextButton(
                    onClick = onDismiss,
                    colors = FolkButtonDefaults.textColors(),
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        }
    }
}
