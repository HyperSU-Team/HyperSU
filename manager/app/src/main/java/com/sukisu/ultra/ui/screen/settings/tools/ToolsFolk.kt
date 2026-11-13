package com.sukisu.ultra.ui.screen.settings.tools

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.FolderDelete
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.KsuIsValid
import com.sukisu.ultra.ui.component.folk.FolkNavigationPreference
import com.sukisu.ultra.ui.component.folk.FolkScaffold
import com.sukisu.ultra.ui.component.folk.FolkSettingsSectionGroup
import com.sukisu.ultra.ui.component.folk.FolkSwitchPreference
import com.sukisu.ultra.ui.component.folk.FolkTitleStyle

/**
 * The tools screen in the FolkPatch design: the SELinux toggle, the umount path
 * manager and the allowlist backup/restore pair.
 *
 * All of these are kernel features, so the whole body stays inside
 * [KsuIsValid]. The SELinux row reports the live status and disables itself
 * while a change is in flight.
 *
 * There used to be a fourth group, a CPU identity spoof (MIDR / BogoMIPS / HWCAP per core). It is
 * gone because nothing can serve it: `ksud kernel spoof-cpu` does not exist in this project's
 * userspace, `KSU_IOCTL_SET_SPOOF_CPU` is not in the uapi crate, and no kernel here implements
 * the /proc/cpuinfo rewrite it promised. The entry only ever produced "the apply failed", so the
 * row was removed rather than left as a dead end.
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
}
