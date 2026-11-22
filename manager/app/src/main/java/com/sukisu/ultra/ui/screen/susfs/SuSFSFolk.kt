package com.sukisu.ultra.ui.screen.susfs

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.folk.FolkFactsGroup
import com.sukisu.ultra.ui.component.folk.FolkScaffold
import com.sukisu.ultra.ui.component.folk.FolkSettingsGroup
import com.sukisu.ultra.ui.component.folk.FolkSeverity
import com.sukisu.ultra.ui.component.folk.FolkStatusBadge
import com.sukisu.ultra.ui.component.folk.FolkTitleStyle
import com.sukisu.ultra.ui.navigation3.LocalNavigator
import com.sukisu.ultra.ui.screen.susfs.component.AddAppPathDialog
import com.sukisu.ultra.ui.screen.susfs.component.AddKstatStaticallyDialog
import com.sukisu.ultra.ui.screen.susfs.component.AddPathDialog
import com.sukisu.ultra.ui.screen.susfs.component.ConfirmDialog
import com.sukisu.ultra.ui.screen.susfs.component.FolkSusfsTabRow
import com.sukisu.ultra.ui.screen.susfs.component.SlotInfoDialog
import com.sukisu.ultra.ui.screen.susfs.content.BasicSettingsContent
import com.sukisu.ultra.ui.screen.susfs.content.EnabledFeaturesContent
import com.sukisu.ultra.ui.screen.susfs.content.KstatConfigContent
import com.sukisu.ultra.ui.screen.susfs.content.SusLoopPathsContent
import com.sukisu.ultra.ui.screen.susfs.content.SusMapsContent
import com.sukisu.ultra.ui.screen.susfs.content.SusPathsContent
import com.sukisu.ultra.ui.screen.susfs.util.SuSFSManager
import com.sukisu.ultra.ui.screen.susfs.viewmodel.SuSFSViewModel
import com.sukisu.ultra.ui.theme.tokens.FolkType
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The SuSFS configuration screen in the FolkPatch design.
 *
 * SuSFS has no FolkPatch original, so the page is assembled from the shared
 * Folk primitives in the settings-list style: a status header, then the tab
 * selector and one grouped block per concern. The six tabs, every dialog and
 * every viewmodel call of the previous Material and Miuix variants are
 * preserved.
 */
@SuppressLint("SdCardPath", "AutoboxingStateCreation")
@Composable
fun SuSFSFolk() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current

    val viewModel: SuSFSViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    // Serialises read-modify-write removals so a group delete does not lose updates.
    val removeMutex = remember { Mutex() }

    var isNavigating by remember { mutableStateOf(false) }
    val allTabs = SuSFSTab.getAllTabs()

    LaunchedEffect(Unit) {
        viewModel.loadInitial(context)
    }

    LaunchedEffect(uiState.selectedTab) {
        if (uiState.selectedTab == SuSFSTab.ENABLED_FEATURES) {
            viewModel.loadEnabledFeatures(context)
        }
    }

    LaunchedEffect(uiState.canEnableAutoStart, uiState.autoStartEnabled) {
        if (!uiState.canEnableAutoStart && uiState.autoStartEnabled) {
            // viewModel.configureAutoStart() bails out when canEnableAutoStart is false,
            // so disable through the manager directly and resync the state.
            SuSFSManager.configureAutoStart(context, false)
            viewModel.reloadConfig()
        }
    }

    SlotInfoDialog(
        showDialog = uiState.showSlotInfoDialog,
        onDismiss = { viewModel.showSlotInfoDialog(false) },
        slotInfoList = uiState.slotInfoList,
        currentActiveSlot = uiState.currentActiveSlot,
        isLoadingSlotInfo = uiState.isLoadingSlotInfo,
        onRefresh = { viewModel.loadSlotInfo(context) },
        onUseUname = { uname ->
            viewModel.updateUname(uname)
            viewModel.showSlotInfoDialog(false)
        },
        onUseBuildTime = { buildTime ->
            viewModel.updateBuildTime(buildTime)
            viewModel.showSlotInfoDialog(false)
        }
    )

    AddPathDialog(
        showDialog = uiState.showAddPathDialog,
        onDismiss = { viewModel.closeAddPathDialog() },
        onConfirm = { path ->
            val oldPath = uiState.editingPath
            coroutineScope.launch {
                val success = if (oldPath != null) {
                    SuSFSManager.editSusPath(context, oldPath, path)
                } else {
                    SuSFSManager.addSusPath(context, path)
                }
                if (success) viewModel.reloadConfig()
                viewModel.closeAddPathDialog()
            }
        },
        isLoading = uiState.isLoading,
        titleRes = if (uiState.editingPath != null) R.string.susfs_edit_sus_path else R.string.susfs_add_sus_path,
        labelRes = R.string.susfs_path_label,
        initialValue = uiState.editingPath ?: ""
    )

    AddPathDialog(
        showDialog = uiState.showAddLoopPathDialog,
        onDismiss = { viewModel.closeAddLoopPathDialog() },
        onConfirm = { path ->
            val oldPath = uiState.editingLoopPath
            coroutineScope.launch {
                val success = if (oldPath != null) {
                    SuSFSManager.editSusLoopPath(context, oldPath, path)
                } else {
                    SuSFSManager.addSusLoopPath(context, path)
                }
                if (success) viewModel.reloadConfig()
                viewModel.closeAddLoopPathDialog()
            }
        },
        isLoading = uiState.isLoading,
        titleRes = if (uiState.editingLoopPath != null) R.string.susfs_edit_sus_loop_path else R.string.susfs_add_sus_loop_path,
        labelRes = R.string.susfs_loop_path_label,
        initialValue = uiState.editingLoopPath ?: ""
    )

    AddPathDialog(
        showDialog = uiState.showAddSusMapDialog,
        onDismiss = { viewModel.closeAddSusMapDialog() },
        onConfirm = { path ->
            val oldPath = uiState.editingSusMap
            coroutineScope.launch {
                val success = if (oldPath != null) {
                    SuSFSManager.editSusMap(context, oldPath, path)
                } else {
                    SuSFSManager.addSusMap(context, path)
                }
                if (success) viewModel.reloadConfig()
                viewModel.closeAddSusMapDialog()
            }
        },
        isLoading = uiState.isLoading,
        titleRes = if (uiState.editingSusMap != null) R.string.susfs_edit_sus_map else R.string.susfs_add_sus_map,
        labelRes = R.string.susfs_sus_map_label,
        initialValue = uiState.editingSusMap ?: ""
    )

    AddAppPathDialog(
        showDialog = uiState.showAddAppPathDialog,
        onDismiss = { viewModel.closeAddAppPathDialog() },
        onConfirm = { packageNames ->
            coroutineScope.launch {
                var successCount = 0
                packageNames.forEach { packageName ->
                    if (SuSFSManager.addAppPaths(context, packageName)) successCount++
                }
                if (successCount > 0) viewModel.reloadConfig()
                viewModel.closeAddAppPathDialog()
            }
        },
        isLoading = uiState.isLoading,
        apps = uiState.installedApps,
        onLoadApps = { viewModel.loadInstalledApps() },
        existingSusPaths = uiState.susPaths
    )

    AddKstatStaticallyDialog(
        showDialog = uiState.showAddKstatStaticallyDialog,
        onDismiss = { viewModel.closeAddKstatStaticallyDialog() },
        onConfirm = { path, ino, dev, nlink, size, atime, atimeNsec, mtime, mtimeNsec, ctime, ctimeNsec, blocks, blksize ->
            val oldConfig = uiState.editingKstatConfig
            coroutineScope.launch {
                val success = if (oldConfig != null) {
                    SuSFSManager.editKstatConfig(
                        context, oldConfig, path, ino, dev, nlink, size, atime, atimeNsec,
                        mtime, mtimeNsec, ctime, ctimeNsec, blocks, blksize
                    )
                } else {
                    SuSFSManager.addKstatStatically(
                        context, path, ino, dev, nlink, size, atime, atimeNsec,
                        mtime, mtimeNsec, ctime, ctimeNsec, blocks, blksize
                    )
                }
                if (success) viewModel.reloadConfig()
                viewModel.closeAddKstatStaticallyDialog()
            }
        },
        isLoading = uiState.isLoading,
        initialConfig = uiState.editingKstatConfig ?: ""
    )

    AddPathDialog(
        showDialog = uiState.showAddKstatDialog,
        onDismiss = { viewModel.closeAddKstatDialog() },
        onConfirm = { path ->
            val oldPath = uiState.editingKstatPath
            coroutineScope.launch {
                val success = if (oldPath != null) {
                    SuSFSManager.editAddKstat(context, oldPath, path)
                } else {
                    SuSFSManager.addKstat(context, path)
                }
                if (success) viewModel.reloadConfig()
                viewModel.closeAddKstatDialog()
            }
        },
        isLoading = uiState.isLoading,
        titleRes = if (uiState.editingKstatPath != null) R.string.edit_kstat_path_title else R.string.add_kstat_path_title,
        labelRes = R.string.file_or_directory_path_label,
        initialValue = uiState.editingKstatPath ?: ""
    )

    ConfirmDialog(
        showDialog = uiState.showConfirmReset,
        onDismiss = { viewModel.toggleConfirmReset(false) },
        onConfirm = { viewModel.resetAll(context) },
        titleRes = R.string.susfs_reset_confirm_title,
        messageRes = R.string.susfs_reset_confirm_message,
        isLoading = uiState.isLoading
    )

    ConfirmDialog(
        showDialog = uiState.showResetPathsDialog,
        onDismiss = { viewModel.toggleResetPathsDialog(false) },
        onConfirm = {
            coroutineScope.launch {
                SuSFSManager.saveSusPaths(emptySet())
                if (SuSFSManager.isAutoStartEnabled()) SuSFSManager.configureAutoStart(context, true)
                viewModel.reloadConfig()
                viewModel.toggleResetPathsDialog(false)
            }
        },
        titleRes = R.string.susfs_reset_paths_title,
        messageRes = R.string.susfs_reset_paths_message,
        isLoading = uiState.isLoading
    )

    ConfirmDialog(
        showDialog = uiState.showResetLoopPathsDialog,
        onDismiss = { viewModel.toggleResetLoopPathsDialog(false) },
        onConfirm = {
            coroutineScope.launch {
                SuSFSManager.saveSusLoopPaths(emptySet())
                if (SuSFSManager.isAutoStartEnabled()) SuSFSManager.configureAutoStart(context, true)
                viewModel.reloadConfig()
                viewModel.toggleResetLoopPathsDialog(false)
            }
        },
        titleRes = R.string.susfs_reset_loop_paths_title,
        messageRes = R.string.susfs_reset_loop_paths_message,
        isLoading = uiState.isLoading
    )

    ConfirmDialog(
        showDialog = uiState.showResetSusMapsDialog,
        onDismiss = { viewModel.toggleResetSusMapsDialog(false) },
        onConfirm = {
            coroutineScope.launch {
                SuSFSManager.saveSusMaps(emptySet())
                if (SuSFSManager.isAutoStartEnabled()) SuSFSManager.configureAutoStart(context, true)
                viewModel.reloadConfig()
                viewModel.toggleResetSusMapsDialog(false)
            }
        },
        titleRes = R.string.susfs_reset_sus_maps_title,
        messageRes = R.string.susfs_reset_sus_maps_message,
        isLoading = uiState.isLoading
    )

    ConfirmDialog(
        showDialog = uiState.showResetKstatDialog,
        onDismiss = { viewModel.toggleResetKstatDialog(false) },
        onConfirm = {
            coroutineScope.launch {
                SuSFSManager.saveKstatConfigs(emptySet())
                SuSFSManager.saveAddKstatPaths(emptySet())
                if (SuSFSManager.isAutoStartEnabled()) SuSFSManager.configureAutoStart(context, true)
                viewModel.reloadConfig()
                viewModel.toggleResetKstatDialog(false)
            }
        },
        titleRes = R.string.reset_kstat_config_title,
        messageRes = R.string.reset_kstat_config_message,
        isLoading = uiState.isLoading
    )

    FolkScaffold(
        title = stringResource(R.string.susfs_config_title),
        titleStyle = FolkTitleStyle.Inline,
        onBack = {
            if (!isNavigating) {
                isNavigating = true
                navigator.pop()
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding(),
                ),
        ) {
            Spacer(Modifier.height(8.dp))

            SusfsStatusHeader(
                autoStartEnabled = uiState.autoStartEnabled,
                canEnableAutoStart = uiState.canEnableAutoStart,
                unameValue = uiState.unameValue,
                buildTimeValue = uiState.buildTimeValue,
                enableHideBl = uiState.enableHideBl,
                enableCleanupResidue = uiState.enableCleanupResidue,
                enableAvcLogSpoofing = uiState.enableAvcLogSpoofing,
                hideSusMountsForAllProcs = uiState.hideSusMountsForAllProcs,
                susPaths = uiState.susPaths,
                susLoopPaths = uiState.susLoopPaths,
                susMaps = uiState.susMaps,
                kstatConfigs = uiState.kstatConfigs,
                addKstatPaths = uiState.addKstatPaths,
            )

            FolkSusfsTabRow(
                labels = allTabs.map { stringResource(it.displayNameRes) },
                selectedIndex = allTabs.indexOf(uiState.selectedTab),
                onSelect = { index -> viewModel.setSelectedTab(allTabs[index]) },
                modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
            )

            when (uiState.selectedTab) {
                SuSFSTab.BASIC_SETTINGS -> {
                    BasicSettingsContent(
                        unameValue = uiState.unameValue,
                        onUnameValueChange = { viewModel.updateUname(it) },
                        buildTimeValue = uiState.buildTimeValue,
                        onBuildTimeValueChange = { viewModel.updateBuildTime(it) },
                        executeInPostFsData = uiState.executeInPostFsData,
                        onExecuteInPostFsDataChange = { viewModel.setExecuteInPostFsData(it) },
                        autoStartEnabled = uiState.autoStartEnabled,
                        canEnableAutoStart = uiState.canEnableAutoStart,
                        isLoading = uiState.isLoading,
                        onAutoStartToggle = { viewModel.configureAutoStart(context, it) },
                        onShowSlotInfo = {
                            viewModel.showSlotInfoDialog(true)
                            viewModel.loadSlotInfo(context)
                        },
                        enableHideBl = uiState.enableHideBl,
                        onEnableHideBlChange = { viewModel.setEnableHideBl(context, it) },
                        enableCleanupResidue = uiState.enableCleanupResidue,
                        onEnableCleanupResidueChange = { viewModel.setEnableCleanupResidue(context, it) },
                        enableAvcLogSpoofing = uiState.enableAvcLogSpoofing,
                        onEnableAvcLogSpoofingChange = { viewModel.setEnableAvcLogSpoofing(context, it) },
                        hideSusMountsForAllProcs = uiState.hideSusMountsForAllProcs,
                        onHideSusMountsForAllProcsChange = { viewModel.setHideSusMountsForAllProcs(context, it) },
                        cmdlineOrBootconfigPath = uiState.cmdlineOrBootconfigPath,
                        onCmdlineOrBootconfigApply = { uri -> viewModel.applyCmdlineOrBootconfig(context, uri) },
                        onReset = { viewModel.toggleConfirmReset(true) },
                        onApply = { viewModel.applyBasicSettings(context) },
                        onConfigReload = { viewModel.reloadConfig() }
                    )
                }
                SuSFSTab.SUS_PATHS -> {
                    SusPathsContent(
                        susPaths = uiState.susPaths,
                        isLoading = uiState.isLoading,
                        onAddPath = { viewModel.openAddPathDialog() },
                        onAddAppPath = {
                            viewModel.openAddAppPathDialog()
                            viewModel.loadInstalledApps()
                        },
                        onRemovePath = { path ->
                            coroutineScope.launch {
                                removeMutex.withLock {
                                    if (SuSFSManager.removeSusPath(path)) viewModel.reloadConfig()
                                }
                            }
                        },
                        onEditPath = { viewModel.openAddPathDialog(it) },
                        onReset = { viewModel.toggleResetPathsDialog(true) }
                    )
                }
                SuSFSTab.SUS_LOOP_PATHS -> {
                    SusLoopPathsContent(
                        susLoopPaths = uiState.susLoopPaths,
                        isLoading = uiState.isLoading,
                        onAddLoopPath = { viewModel.openAddLoopPathDialog() },
                        onRemoveLoopPath = { path ->
                            coroutineScope.launch {
                                removeMutex.withLock {
                                    if (SuSFSManager.removeSusLoopPath(path)) viewModel.reloadConfig()
                                }
                            }
                        },
                        onEditLoopPath = { viewModel.openAddLoopPathDialog(it) },
                        onReset = { viewModel.toggleResetLoopPathsDialog(true) }
                    )
                }
                SuSFSTab.SUS_MAPS -> {
                    SusMapsContent(
                        susMaps = uiState.susMaps,
                        isLoading = uiState.isLoading,
                        onAddSusMap = { viewModel.openAddSusMapDialog() },
                        onRemoveSusMap = { map ->
                            coroutineScope.launch {
                                removeMutex.withLock {
                                    if (SuSFSManager.removeSusMap(map)) viewModel.reloadConfig()
                                }
                            }
                        },
                        onEditSusMap = { viewModel.openAddSusMapDialog(it) },
                        onReset = { viewModel.toggleResetSusMapsDialog(true) }
                    )
                }
                SuSFSTab.KSTAT_CONFIG -> {
                    KstatConfigContent(
                        kstatConfigs = uiState.kstatConfigs,
                        addKstatPaths = uiState.addKstatPaths,
                        isLoading = uiState.isLoading,
                        onAddKstatStatically = { viewModel.openAddKstatStaticallyDialog() },
                        onAddKstat = { viewModel.openAddKstatDialog() },
                        onRemoveKstatConfig = { config ->
                            coroutineScope.launch {
                                removeMutex.withLock {
                                    if (SuSFSManager.removeKstatConfig(config)) viewModel.reloadConfig()
                                }
                            }
                        },
                        onEditKstatConfig = { viewModel.openAddKstatStaticallyDialog(it) },
                        onRemoveAddKstat = { path ->
                            coroutineScope.launch {
                                removeMutex.withLock {
                                    if (SuSFSManager.removeAddKstat(path)) viewModel.reloadConfig()
                                }
                            }
                        },
                        onEditAddKstat = { viewModel.openAddKstatDialog(it) },
                        onUpdateKstat = { path -> coroutineScope.launch { SuSFSManager.updateKstat(context, path) } },
                        onUpdateKstatFullClone = { path -> coroutineScope.launch { SuSFSManager.updateKstatFullClone(context, path) } }
                    )
                }
                SuSFSTab.ENABLED_FEATURES -> {
                    EnabledFeaturesContent(
                        enabledFeatures = uiState.enabledFeatures,
                        onRefresh = { viewModel.loadEnabledFeatures(context) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * The status block at the top of the page: whether auto start is on, then the
 * current configuration summarised as facts.
 */
@Composable
private fun SusfsStatusHeader(
    autoStartEnabled: Boolean,
    canEnableAutoStart: Boolean,
    unameValue: String,
    buildTimeValue: String,
    enableHideBl: Boolean,
    enableCleanupResidue: Boolean,
    enableAvcLogSpoofing: Boolean,
    hideSusMountsForAllProcs: Boolean,
    susPaths: Set<String>,
    susLoopPaths: Set<String>,
    susMaps: Set<String>,
    kstatConfigs: Set<String>,
    addKstatPaths: Set<String>,
) {
    val configuredCount = listOf(
        enableHideBl,
        enableCleanupResidue,
        enableAvcLogSpoofing,
        hideSusMountsForAllProcs,
    ).count { it }

    FolkSettingsGroup {
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                FolkStatusBadge(
                    severity = if (autoStartEnabled) FolkSeverity.Positive else FolkSeverity.Neutral,
                    label = stringResource(R.string.susfs_autostart_title),
                    meta = if (autoStartEnabled) {
                        stringResource(R.string.susfs_feature_enabled)
                    } else {
                        stringResource(R.string.susfs_feature_disabled)
                    },
                )

                Spacer(Modifier.height(10.dp))

                FolkFactsGroup {
                    fact(stringResource(R.string.susfs_uname_label), unameValue)
                    fact(stringResource(R.string.susfs_build_time_label), buildTimeValue)
                    fact(
                        label = stringResource(R.string.susfs_tab_path_settings),
                        value = (susPaths.size + susLoopPaths.size + susMaps.size).toString(),
                    )
                    fact(
                        label = stringResource(R.string.susfs_tab_kstat_config),
                        value = (kstatConfigs.size + addKstatPaths.size).toString(),
                    )
                    fact(
                        label = stringResource(R.string.susfs_section_features),
                        value = configuredCount.toString(),
                        valueTone = if (configuredCount > 0) {
                            FolkSeverity.Positive
                        } else {
                            FolkSeverity.Neutral
                        },
                    )
                }

                if (!canEnableAutoStart) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.susfs_autostart_requirement),
                        style = FolkType.Caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
