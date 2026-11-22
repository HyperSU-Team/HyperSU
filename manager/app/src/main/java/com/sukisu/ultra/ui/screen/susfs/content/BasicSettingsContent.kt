package com.sukisu.ultra.ui.screen.susfs.content

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.folk.FolkButtonDefaults
import com.sukisu.ultra.ui.component.folk.FolkFactsGroup
import com.sukisu.ultra.ui.component.folk.FolkSelectableRow
import com.sukisu.ultra.ui.component.folk.FolkSettingsGroup
import com.sukisu.ultra.ui.component.folk.FolkSettingsSectionGroup
import com.sukisu.ultra.ui.component.folk.FolkSwitchPreference
import com.sukisu.ultra.ui.screen.susfs.component.BackupRestoreComponent
import com.sukisu.ultra.ui.screen.susfs.component.FolkDescriptionGroup
import com.sukisu.ultra.ui.screen.susfs.component.ResetButton
import com.sukisu.ultra.ui.theme.tokens.FolkType
import com.sukisu.ultra.ui.util.isAbDevice

/**
 * The Basic Settings tab, in the FolkPatch design: the description, the
 * execution location, the uname/build time fields with their current values,
 * the cmdline/bootconfig replacement, the feature toggles, the slot inspector
 * and the backup/restore block.
 *
 * Every viewmodel call, validation and state transition of the previous
 * Material and Miuix variants is preserved; only the presentation is Folk.
 */
@Composable
fun BasicSettingsContent(
    unameValue: String,
    onUnameValueChange: (String) -> Unit,
    buildTimeValue: String,
    onBuildTimeValueChange: (String) -> Unit,
    executeInPostFsData: Boolean,
    onExecuteInPostFsDataChange: (Boolean) -> Unit,
    autoStartEnabled: Boolean,
    canEnableAutoStart: Boolean,
    isLoading: Boolean,
    onAutoStartToggle: (Boolean) -> Unit,
    onShowSlotInfo: () -> Unit,
    enableHideBl: Boolean,
    onEnableHideBlChange: (Boolean) -> Unit,
    enableCleanupResidue: Boolean,
    onEnableCleanupResidueChange: (Boolean) -> Unit,
    enableAvcLogSpoofing: Boolean,
    onEnableAvcLogSpoofingChange: (Boolean) -> Unit,
    hideSusMountsForAllProcs: Boolean,
    onHideSusMountsForAllProcsChange: (Boolean) -> Unit,
    cmdlineOrBootconfigPath: String = "",
    onCmdlineOrBootconfigApply: (String) -> Unit = {},
    onReset: (() -> Unit)? = null,
    onApply: (() -> Unit)? = null,
    onConfigReload: () -> Unit
) {
    val isAbDevice = produceState(initialValue = false) { value = isAbDevice() }.value

    // SAF file picker for `/proc/cmdline` (non-GKI) or `/proc/bootconfig` (GKI)
    // replacement. The MIME array lets the user choose any plain text file.
    val cmdlineFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { onCmdlineOrBootconfigApply(it.toString()) } }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FolkDescriptionGroup(
            title = stringResource(R.string.susfs_config_description),
            description = stringResource(R.string.susfs_config_description_text),
        )

        // 执行位置
        FolkSettingsSectionGroup(title = stringResource(R.string.susfs_execution_location_label)) {
            item {
                FolkSelectableRow(
                    title = stringResource(R.string.susfs_execution_location_service),
                    selected = !executeInPostFsData,
                    enabled = !isLoading,
                    onClick = { onExecuteInPostFsDataChange(false) },
                )
            }
            item {
                FolkSelectableRow(
                    title = stringResource(R.string.susfs_execution_location_post_fs_data),
                    selected = executeInPostFsData,
                    enabled = !isLoading,
                    onClick = { onExecuteInPostFsDataChange(true) },
                )
            }
        }

        // Uname / 构建时间输入
        FolkSettingsGroup {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = unameValue,
                        onValueChange = onUnameValueChange,
                        label = { Text(stringResource(R.string.susfs_uname_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = buildTimeValue,
                        onValueChange = onBuildTimeValueChange,
                        label = { Text(stringResource(R.string.susfs_build_time_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                        singleLine = true,
                    )
                    if (onApply != null) {
                        Button(
                            onClick = { onApply() },
                            enabled = !isLoading &&
                                (unameValue.isNotBlank() || buildTimeValue.isNotBlank()),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            colors = FolkButtonDefaults.filledColors(),
                        ) {
                            Text(stringResource(R.string.susfs_apply))
                        }
                    }
                }
            }
        }

        // 当前值
        FolkSettingsSectionGroup(title = stringResource(R.string.susfs_section_current_values)) {
            item {
                FolkFactsGroup(
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    fact(
                        label = stringResource(R.string.susfs_uname_label),
                        value = unameValue,
                    )
                    fact(
                        label = stringResource(R.string.susfs_build_time_label),
                        value = buildTimeValue,
                    )
                    fact(
                        label = stringResource(R.string.susfs_execution_location_label),
                        value = if (executeInPostFsData) "Post-FS-Data" else "Service",
                    )
                }
            }
        }

        // Cmdline / Bootconfig
        FolkSettingsSectionGroup(title = stringResource(R.string.susfs_cmdline_or_bootconfig_title)) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.susfs_cmdline_or_bootconfig_description),
                            style = FolkType.Summary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = stringResource(
                            R.string.susfs_cmdline_or_bootconfig_current,
                            cmdlineOrBootconfigPath.ifBlank {
                                stringResource(R.string.susfs_cmdline_or_bootconfig_none)
                            }
                        ),
                        style = FolkType.Numeral,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = {
                            cmdlineFileLauncher.launch(
                                arrayOf("text/plain", "application/octet-stream", "*/*")
                            )
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        colors = FolkButtonDefaults.tonalColors(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.susfs_cmdline_or_bootconfig_pick_file))
                    }
                }
            }
        }

        // 开关
        FolkSettingsSectionGroup(title = stringResource(R.string.susfs_section_features)) {
            item {
                FolkSwitchPreference(
                    title = stringResource(R.string.susfs_autostart_title),
                    summary = if (canEnableAutoStart) {
                        stringResource(R.string.susfs_autostart_description)
                    } else {
                        stringResource(R.string.susfs_autostart_requirement)
                    },
                    icon = Icons.Default.AutoMode,
                    checked = autoStartEnabled,
                    onCheckedChange = onAutoStartToggle,
                    enabled = !isLoading && canEnableAutoStart,
                )
            }
            item {
                FolkSwitchPreference(
                    title = stringResource(R.string.hide_bl_script),
                    summary = stringResource(R.string.hide_bl_script_description),
                    icon = Icons.Default.Security,
                    checked = enableHideBl,
                    onCheckedChange = onEnableHideBlChange,
                    enabled = !isLoading,
                )
            }
            item {
                FolkSwitchPreference(
                    title = stringResource(R.string.cleanup_residue),
                    summary = stringResource(R.string.cleanup_residue_description),
                    icon = Icons.Default.CleaningServices,
                    checked = enableCleanupResidue,
                    onCheckedChange = onEnableCleanupResidueChange,
                    enabled = !isLoading,
                )
            }
            item {
                FolkSwitchPreference(
                    title = stringResource(R.string.avc_log_spoofing),
                    summary = stringResource(R.string.avc_log_spoofing_description),
                    icon = Icons.Default.VisibilityOff,
                    checked = enableAvcLogSpoofing,
                    onCheckedChange = onEnableAvcLogSpoofingChange,
                    enabled = !isLoading,
                )
            }
            item {
                FolkSwitchPreference(
                    title = stringResource(R.string.susfs_hide_mounts_for_all_procs_label),
                    summary = if (hideSusMountsForAllProcs) {
                        stringResource(R.string.susfs_hide_mounts_for_all_procs_enabled_description)
                    } else {
                        stringResource(R.string.susfs_hide_mounts_for_all_procs_disabled_description)
                    },
                    icon = if (hideSusMountsForAllProcs) {
                        Icons.Default.VisibilityOff
                    } else {
                        Icons.Default.Visibility
                    },
                    checked = hideSusMountsForAllProcs,
                    onCheckedChange = onHideSusMountsForAllProcsChange,
                    enabled = !isLoading,
                )
            }
        }

        // 槽位信息（仅 A/B 设备）
        if (isAbDevice) {
            FolkSettingsSectionGroup(title = stringResource(R.string.susfs_slot_info_title)) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.susfs_slot_info_description),
                                style = FolkType.Summary,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = onShowSlotInfo,
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            colors = FolkButtonDefaults.tonalColors(),
                        ) {
                            Text(stringResource(R.string.susfs_slot_info_title))
                        }
                    }
                }
            }
        }

        BackupRestoreComponent(
            isLoading = isLoading,
            onLoadingChange = { },
            onConfigReload = onConfigReload,
        )

        if (onReset != null) {
            ResetButton(
                title = stringResource(R.string.susfs_reset_confirm_title),
                onClick = onReset,
            )
        }
    }
}
