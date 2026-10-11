package com.sukisu.ultra.ui.screen.settings.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.FolderDelete
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.KsuIsValid
import com.sukisu.ultra.ui.component.folk.FolkAlertDialog
import com.sukisu.ultra.ui.component.folk.FolkButtonDefaults
import com.sukisu.ultra.ui.component.folk.FolkNavigationPreference
import com.sukisu.ultra.ui.component.folk.FolkScaffold
import com.sukisu.ultra.ui.component.folk.FolkSettingsSectionGroup
import com.sukisu.ultra.ui.component.folk.FolkSwitchPreference
import com.sukisu.ultra.ui.component.folk.FolkTitleStyle
import com.sukisu.ultra.ui.theme.tokens.FolkType

/**
 * The tools screen in the FolkPatch design: the SELinux toggle, the umount path
 * manager, the CPU-spoofing entry point and the allowlist backup/restore pair.
 *
 * All of these are kernel features, so the whole body stays inside
 * [KsuIsValid]. The SELinux row reports the live status and disables itself
 * while a change is in flight; the spoof dialog keeps every field, the preset
 * list and the per-core selection it had.
 */
@Composable
fun ToolsFolk(
    state: ToolsUiState,
    actions: ToolsActions,
) {
    FolkScaffold(
        title = stringResource(R.string.tools),
        titleStyle = FolkTitleStyle.Flexible,
        onBack = actions.onBack,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            contentPadding = PaddingValues(
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item {
                KsuIsValid {
                    FolkSettingsSectionGroup(title = stringResource(R.string.tools_selinux_toggle)) {
                        item {
                            FolkSwitchPreference(
                                title = stringResource(R.string.tools_selinux_toggle),
                                // From the state, which ToolsScreen fills in on a background
                                // thread. This used to call getSELinuxStatusRaw() here, and that
                                // builds a shell and runs `getenforce` on every recomposition.
                                summary = stringResource(
                                    R.string.tools_selinux_summary,
                                    stringResource(
                                        if (state.selinuxEnforcing) {
                                            R.string.tools_selinux_enforcing
                                        } else {
                                            R.string.tools_selinux_permissive
                                        },
                                    ),
                                ),
                                icon = Icons.Rounded.Security,
                                enabled = !state.selinuxLoading,
                                checked = state.selinuxEnforcing,
                                onCheckedChange = actions.onSelinuxToggle,
                            )
                        }
                    }

                    FolkSettingsSectionGroup(title = stringResource(R.string.umount_path_manager)) {
                        item {
                            FolkNavigationPreference(
                                title = stringResource(R.string.umount_path_manager),
                                icon = Icons.Rounded.FolderDelete,
                                onClick = actions.onNavigateToUmountManager,
                            )
                        }
                    }

                    FolkSettingsSectionGroup(title = stringResource(R.string.tools_spoof_cpu_title)) {
                        item {
                            FolkNavigationPreference(
                                title = stringResource(R.string.tools_spoof_cpu_title),
                                summary = stringResource(R.string.tools_spoof_cpu_summary),
                                icon = Icons.Rounded.Memory,
                                onClick = actions.onOpenSpoofCpuDialog,
                            )
                        }
                    }

                    FolkSettingsSectionGroup(title = stringResource(R.string.allowlist_backup_title)) {
                        item {
                            FolkNavigationPreference(
                                title = stringResource(R.string.allowlist_backup_title),
                                summary = stringResource(R.string.allowlist_backup_summary_picker),
                                icon = Icons.Rounded.Backup,
                                onClick = actions.onBackupAllowlist,
                            )
                        }
                        item {
                            FolkNavigationPreference(
                                title = stringResource(R.string.allowlist_restore_title),
                                summary = stringResource(R.string.allowlist_restore_summary_picker),
                                icon = Icons.Rounded.Restore,
                                onClick = actions.onRestoreAllowlist,
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.spoofCpuDialogVisible) {
        SpoofCpuDialog(
            currentCpuInfo = state.currentCpuInfo,
            onDismiss = actions.onDismissSpoofCpuDialog,
            onApply = actions.onApplySpoofCpu,
        )
    }
}

@Composable
private fun SpoofCpuDialog(
    currentCpuInfo: CpuInfo?,
    onDismiss: () -> Unit,
    onApply: (SpoofCpuParams) -> Unit,
) {
    var selectedPreset by remember { mutableStateOf("custom") }
    var midrValue by remember { mutableStateOf(currentCpuInfo?.midrHex ?: "0x0") }
    var bogomipsValue by remember { mutableStateOf(currentCpuInfo?.bogomips?.toString() ?: "0") }
    var hwcapValue by remember { mutableStateOf(currentCpuInfo?.hwcap ?: "0x0") }
    var hwcap2Value by remember { mutableStateOf(currentCpuInfo?.hwcap2 ?: "0x0") }
    val coreCount = currentCpuInfo?.coreCount ?: 8
    var selectedCores by remember { mutableStateOf((0 until coreCount).toList()) }
    var presetExpanded by remember { mutableStateOf(false) }

    val presetList = listOf(
        "custom" to stringResource(R.string.spoof_cpu_midr_custom),
        "0x413fd050" to "Cortex-A55",
        "0x413fd0b1" to "Cortex-A76",
        "0x413fd0d2" to "Cortex-A78",
        "0x413fd0d4" to "Cortex-A710",
        "0x413fd0d5" to "Cortex-A715",
        "0x413fd0c1" to "Cortex-X1",
        "0x413fd0e3" to "Cortex-X3",
        "0x413fd0c0" to "Neoverse N1",
        "0x511f804d" to "Kryo 4xx Gold",
        "0x511f805c" to "Kryo 5xx Gold",
        "0x513f8050" to "Kryo 6xx Gold+",
        "0x553f1000" to "Exynos M5",
    )

    FolkAlertDialog(
        onDismissRequest = onDismiss,
        width = 360.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Text(
                text = stringResource(R.string.spoof_cpu_dialog_title),
                style = FolkType.Title,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.spoof_cpu_warning),
                    style = FolkType.Caption,
                    color = MaterialTheme.colorScheme.error,
                )

                ExposedDropdownMenuBox(
                    expanded = presetExpanded,
                    onExpandedChange = { presetExpanded = it },
                ) {
                    OutlinedTextField(
                        value = presetList.find { it.first == selectedPreset }?.second ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.spoof_cpu_midr_preset_label)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = presetExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor(
                                androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable
                            )
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = presetExpanded,
                        onDismissRequest = { presetExpanded = false },
                    ) {
                        presetList.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedPreset = value
                                    if (value != "custom") {
                                        midrValue = value
                                    }
                                    presetExpanded = false
                                },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = midrValue,
                    onValueChange = {
                        midrValue = it
                        selectedPreset = "custom"
                    },
                    label = { Text(stringResource(R.string.spoof_cpu_field_midr)) },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = bogomipsValue,
                    onValueChange = { bogomipsValue = it },
                    label = { Text(stringResource(R.string.spoof_cpu_field_bogomips)) },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = hwcapValue,
                    onValueChange = { hwcapValue = it },
                    label = { Text(stringResource(R.string.spoof_cpu_field_hwcap)) },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = hwcap2Value,
                    onValueChange = { hwcap2Value = it },
                    label = { Text(stringResource(R.string.spoof_cpu_field_hwcap2)) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = stringResource(R.string.spoof_cpu_field_cores),
                    style = FolkType.Title,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = selectedCores.size == coreCount,
                        onCheckedChange = { checked ->
                            selectedCores = if (checked) {
                                (0 until coreCount).toList()
                            } else {
                                emptyList()
                            }
                        },
                    )
                    Text(stringResource(R.string.spoof_cpu_cores_all))
                }

                repeat(coreCount) { index ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = selectedCores.contains(index),
                            onCheckedChange = { checked ->
                                selectedCores = if (checked) {
                                    selectedCores + index
                                } else {
                                    selectedCores - index
                                }
                            },
                        )
                        Text(stringResource(R.string.spoof_cpu_cores_format, index))
                    }
                }
            }

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
                    Text(stringResource(R.string.spoof_cpu_cancel))
                }
                androidx.compose.material3.Button(
                    onClick = {
                        onApply(
                            SpoofCpuParams(
                                cpuIndices = selectedCores,
                                midrHex = midrValue,
                                bogomips = bogomipsValue.toIntOrNull() ?: 0,
                                hwcapHex = hwcapValue,
                                hwcap2Hex = hwcap2Value,
                            )
                        )
                    },
                    enabled = selectedCores.isNotEmpty(),
                    colors = FolkButtonDefaults.filledColors(),
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(stringResource(R.string.spoof_cpu_apply))
                }
            }
        }
    }
}
