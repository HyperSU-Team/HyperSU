package com.sukisu.ultra.ui.screen.install

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.getKernelVersion
import com.sukisu.ultra.ui.component.dialog.rememberConfirmDialog
import com.sukisu.ultra.ui.component.folk.FolkButtonDefaults
import com.sukisu.ultra.ui.component.folk.FolkCheckboxPreference
import com.sukisu.ultra.ui.component.folk.FolkChoicePreference
import com.sukisu.ultra.ui.component.folk.FolkNavigationPreference
import com.sukisu.ultra.ui.component.folk.FolkScaffold
import com.sukisu.ultra.ui.component.folk.FolkSelectableRow
import com.sukisu.ultra.ui.component.folk.FolkSettingsGroup
import com.sukisu.ultra.ui.component.folk.FolkTitleStyle
import com.sukisu.ultra.ui.util.LkmSelection
import com.sukisu.ultra.ui.util.isAbDevice

/**
 * The install screen in the FolkPatch design.
 *
 * The install-method list, the partition picker, the optional LKM upload, the
 * force-backup checkbox, the collapsible advanced group (shell/ADB/spoof) and
 * the AnyKernel3 slot row is preserved with their original enablement
 * rules and callbacks. The inactive-slot option still asks for confirmation
 * first, because it flashes the other slot.
 */
@Composable
internal fun InstallScreenFolk(
    uiState: InstallUiState,
    actions: InstallScreenActions,
    snackBarHost: SnackbarHostState,
) {
    val isAb by produceState(initialValue = false) { value = isAbDevice() }
    val isGki by produceState(initialValue = false) { value = getKernelVersion().isGKI() }

    // The slot, KPM and confirmation dialogs are drawn by InstallScreen, which owns the state
    // they act on. Drawing them here as well stacked two copies of each on top of one another.

    FolkScaffold(
        title = stringResource(R.string.install),
        titleStyle = FolkTitleStyle.Flexible,
        onBack = actions.onBack,
        snackbarHostState = snackBarHost,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            SelectInstallMethod(
                state = uiState,
                onSelected = actions.onSelectMethod,
                onDownloadFile = actions.onDownloadFile,
                onSelectBootImage = actions.onSelectBootImage,
            )

            // Partitions, backup and LKM.
            val isDownload = uiState.installMethod is InstallMethod.DownloadFile
            val partitionItems = if (isDownload) {
                uiState.remoteDisplayPartitions
            } else {
                uiState.displayPartitions
            }
            val partitionIndex = if (isDownload) {
                uiState.remotePartitionSelectionIndex
            } else {
                uiState.partitionSelectionIndex
            }

            FolkSettingsGroup {
                if (partitionItems.isNotEmpty()) {
                    item {
                        FolkChoicePreference(
                            title = if (isDownload) {
                                stringResource(R.string.install_select_partition)
                            } else {
                                "${stringResource(R.string.install_select_partition)} (${uiState.slotSuffix})"
                            },
                            icon = Icons.Filled.Edit,
                            options = partitionItems,
                            selectedIndex = partitionIndex.coerceIn(0, partitionItems.lastIndex),
                            enabled = uiState.canSelectPartition,
                            onSelect = actions.onSelectPartition,
                        )
                    }
                }

                if (uiState.canForceBackup && !uiState.installMethod.isKernelArchive) {
                    item {
                        FolkCheckboxPreference(
                            title = stringResource(R.string.install_force_backup),
                            summary = stringResource(R.string.install_force_backup_summary),
                            checked = uiState.forceBackup,
                            onCheckedChange = actions.onSelectForceBackup,
                        )
                    }
                }

                if (isGki && !uiState.installMethod.isKernelArchive) {
                    item {
                        FolkNavigationPreference(
                            title = stringResource(R.string.install_upload_lkm_file),
                            summary = (uiState.lkmSelection as? LkmSelection.LkmUri)?.let {
                                stringResource(
                                    R.string.selected_lkm,
                                    it.uri.lastPathSegment ?: "(file)",
                                )
                            },
                            icon = Icons.AutoMirrored.Filled.DriveFileMove,
                            onClick = actions.onUploadLkm,
                        )
                    }
                    if (uiState.lkmSelection is LkmSelection.LkmUri) {
                        item {
                            FolkNavigationPreference(
                                title = stringResource(android.R.string.cancel),
                                icon = Icons.Filled.Close,
                                onClick = actions.onClearLkm,
                            )
                        }
                    }
                }
            }

            // Advanced options.
            val rotationState by animateFloatAsState(
                targetValue = if (uiState.advancedOptionsShown) 180f else 0f,
                label = "RotationAnimation",
            )

            FolkSettingsGroup {
                item {
                    FolkNavigationPreference(
                        title = stringResource(R.string.advanced_options),
                        onClick = actions.onAdvancedOptionsClicked,
                        trailing = {
                            Icon(
                                imageVector = Icons.Filled.ExpandMore,
                                contentDescription = stringResource(R.string.expand),
                                modifier = Modifier.graphicsLayer { rotationZ = rotationState },
                            )
                        },
                    )
                }
                item(visible = uiState.advancedOptionsShown) {
                    FolkCheckboxPreference(
                        title = stringResource(R.string.allow_shell),
                        summary = stringResource(R.string.allow_shell_summary),
                        checked = uiState.allowShell,
                        onCheckedChange = actions.onSelectAllowShell,
                    )
                }
                item(visible = uiState.advancedOptionsShown) {
                    FolkCheckboxPreference(
                        title = stringResource(R.string.enable_adb),
                        summary = stringResource(R.string.enable_adb_summary),
                        checked = uiState.enableAdb,
                        onCheckedChange = actions.onSelectEnableAdb,
                    )
                }
            }

            if (uiState.advancedOptionsShown) {
                FolkSettingsGroup {
                    item {
                        OutlinedTextField(
                            value = uiState.spoofRelease,
                            onValueChange = actions.onSpoofReleaseChange,
                            label = { Text(stringResource(R.string.kernel_spoof_release)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = uiState.spoofVersion,
                            onValueChange = actions.onSpoofVersionChange,
                            label = { Text(stringResource(R.string.kernel_spoof_version)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        )
                    }
                }
            }

            // AnyKernel3 slot and KPM rows. Either archive row qualifies, so this guards on the
            // shared predicate rather than one concrete class.
            val archiveMethod = uiState.installMethod?.takeIf { it.isKernelArchive }
            if (archiveMethod != null) {
                FolkSettingsGroup {
                    if (isAb && archiveMethod.archiveSlot != null) {
                        item {
                            FolkNavigationPreference(
                                title = stringResource(
                                    R.string.selected_slot,
                                    if (archiveMethod.archiveSlot == "a") {
                                        stringResource(R.string.slot_a)
                                    } else {
                                        stringResource(R.string.slot_b)
                                    },
                                ),
                                icon = Icons.Filled.SdStorage,
                                onClick = { actions.onReopenSlotDialog(archiveMethod) },
                            )
                        }
                    }
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.installMethod != null,
                colors = FolkButtonDefaults.filledColors(),
                onClick = actions.onNext,
            ) {
                Text(stringResource(R.string.install_next))
            }

            Spacer(Modifier.height(innerPadding.calculateBottomPadding() + 24.dp))
        }
    }
}

@Composable
private fun SelectInstallMethod(
    state: InstallUiState,
    onSelected: (InstallMethod) -> Unit,
    onDownloadFile: () -> Unit,
    onSelectBootImage: (InstallMethod) -> Unit,
) {
    val confirmDialog = rememberConfirmDialog(
        onConfirm = { onSelected(InstallMethod.DirectInstallToInactiveSlot) },
        onDismiss = null,
    )
    val dialogTitle = stringResource(android.R.string.dialog_alert_title)
    val dialogContent = stringResource(R.string.install_inactive_slot_warning)

    val onClick = { option: InstallMethod ->
        when (option) {
            is InstallMethod.SelectFile -> onSelectBootImage(option)
            is InstallMethod.HorizonKernel -> onSelectBootImage(option)
            is InstallMethod.DownloadFile -> onDownloadFile()
            is InstallMethod.DirectInstall -> onSelected(option)
            is InstallMethod.DirectInstallToInactiveSlot ->
                confirmDialog.showConfirm(dialogTitle, dialogContent)
        }
    }

    key(state.installMethodOptions.size) {
        FolkSettingsGroup {
            state.installMethodOptions.forEach { option ->
                item(key = option::class.simpleName) {
                    FolkSelectableRow(
                        title = stringResource(option.label),
                        summary = option.summary,
                        selected = state.installMethod?.let { option::class == it::class } ?: false,
                        onClick = { onClick(option) },
                    )
                }
            }
        }
    }
}
