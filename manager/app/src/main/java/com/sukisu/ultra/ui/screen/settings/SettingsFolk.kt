package com.sukisu.ultra.ui.screen.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Fence
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.KsuIsValid
import com.sukisu.ultra.ui.component.folk.FolkChoicePreference
import com.sukisu.ultra.ui.component.folk.FolkNavigationPreference
import com.sukisu.ultra.ui.component.folk.FolkScaffold
import com.sukisu.ultra.ui.component.folk.FolkSendLogSheet
import com.sukisu.ultra.ui.component.folk.FolkSettingsSectionGroup
import com.sukisu.ultra.ui.component.folk.FolkSwitchPreference
import com.sukisu.ultra.ui.component.folk.FolkTitleStyle
import com.sukisu.ultra.ui.component.folk.FolkValuePreference
import com.sukisu.ultra.ui.component.uninstalldialog.UninstallDialog
import com.sukisu.ultra.ui.util.LocaleHelper

/**
 * The settings hub in the FolkPatch design.
 *
 * Every row the old screen offered is here with the same gate: the KSU-only
 * rows stay inside [KsuIsValid], the feature switches keep their
 * supported/managed/unsupported summary and enablement, KPM and SuSFS appear
 * only when the kernel provides them, and soft reboot keeps the jailbreak-mode
 * lock. The UI-mode row is gone with the dual design, and the send-log sheet is
 * the single Folk implementation.
 */
@Composable
fun SettingPagerFolk(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
    bottomInnerPadding: Dp,
    isSusfsSupported: Boolean,
) {
    val snackBarHost = remember { SnackbarHostState() }
    val showUninstallDialog = rememberSaveable { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }

    UninstallDialog(
        show = showUninstallDialog.value,
        onDismissRequest = { showUninstallDialog.value = false },
    )

    val languageSystemLabel = stringResource(R.string.settings_language_system)
    val languageTags = remember { listOf(LocaleHelper.SYSTEM) + LocaleHelper.SUPPORTED_TAGS }
    val languageNames = remember(languageSystemLabel) {
        languageTags.map { if (it.isEmpty()) languageSystemLabel else LocaleHelper.displayName(it) }
    }

    FolkScaffold(
        title = stringResource(R.string.settings),
        titleStyle = FolkTitleStyle.Flexible,
        snackbarHostState = snackBarHost,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            contentPadding = PaddingValues(
                bottom = bottomInnerPadding + innerPadding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            // Update checks. Deliberately outside KsuIsValid: comparing this build against its
            // own releases and a module against its own update.json needs the network and these
            // settings, not the driver - and gating them on the kernel recognising the manager
            // hid both switches on a device whose kernel predates the manager certificate.
            item {
                FolkSettingsSectionGroup(title = stringResource(R.string.settings_check_update)) {
                    item {
                        FolkSwitchPreference(
                            title = stringResource(R.string.settings_manager_check_update),
                            summary = stringResource(R.string.settings_check_update_summary),
                            icon = Icons.Filled.SystemUpdateAlt,
                            checked = uiState.checkUpdate,
                            onCheckedChange = actions.onSetCheckUpdate,
                        )
                    }
                    item {
                        FolkSwitchPreference(
                            title = stringResource(R.string.settings_module_check_update),
                            summary = stringResource(R.string.settings_check_update_summary),
                            icon = Icons.Filled.SystemUpdateAlt,
                            checked = uiState.checkModuleUpdate,
                            onCheckedChange = actions.onSetCheckModuleUpdate,
                        )
                    }
                }
            }

            // Appearance.
            item {
                FolkSettingsSectionGroup(title = stringResource(R.string.settings_theme)) {
                    item {
                        val context = LocalContext.current
                        val languageIndex = languageTags.indexOf(uiState.appLanguage)
                            .coerceAtLeast(0)
                        // The system's own per-app language page is preferred wherever it exists:
                        // the switch is then applied while our task sits in the background, so our
                        // activity is never recreated in front of the user and neither a switch
                        // page nor a starting window plays over the screen - which is the black
                        // frame the in-app switch shows in the dark theme.
                        //
                        // The app's own page is the fallback for the versions that have no such
                        // page, and it is masked there by the FolkLanguageSwitch cover.
                        val systemPicker = remember {
                            LocaleHelper.canLaunchSystemLanguageSettings(context)
                        }
                        FolkValuePreference(
                            title = stringResource(R.string.settings_language),
                            summary = stringResource(R.string.settings_language_summary),
                            icon = Icons.Rounded.Language,
                            value = languageNames[languageIndex],
                            onClick = {
                                if (systemPicker) {
                                    LocaleHelper.launchSystemLanguageSettings(context)
                                } else {
                                    actions.onOpenLanguage()
                                }
                            },
                        )
                    }
                    item {
                        FolkNavigationPreference(
                            title = stringResource(R.string.settings_theme),
                            summary = stringResource(R.string.settings_theme_summary),
                            icon = Icons.Filled.Palette,
                            onClick = actions.onOpenTheme,
                        )
                    }
                    item {
                        FolkSwitchPreference(
                            title = stringResource(R.string.icon_switch_title),
                            summary = stringResource(R.string.icon_switch_summary),
                            icon = Icons.Rounded.Android,
                            checked = uiState.alternativeIcon,
                            onCheckedChange = actions.onSetAlternativeIcon,
                        )
                    }
                }
            }

            // Templates and tools (KSU only).
            item {
                KsuIsValid {
                    FolkSettingsSectionGroup(title = stringResource(R.string.settings_profile_template)) {
                        item {
                            FolkNavigationPreference(
                                title = stringResource(R.string.settings_profile_template),
                                summary = stringResource(R.string.settings_profile_template_summary),
                                icon = Icons.Filled.Description,
                                onClick = actions.onOpenProfileTemplate,
                            )
                        }
                        item {
                            FolkNavigationPreference(
                                title = stringResource(R.string.settings_tools),
                                summary = stringResource(R.string.settings_tools_summary),
                                icon = Icons.Filled.Fence,
                                onClick = actions.onOpenTools,
                            )
                        }
                    }
                }
            }

            // SuSFS is a ksud feature of its own, gated only by SuSFS support.
            if (isSusfsSupported) {
                item {
                    FolkSettingsSectionGroup(title = stringResource(R.string.susfs_config_title)) {
                        item {
                            FolkNavigationPreference(
                                title = stringResource(R.string.susfs_config_title),
                                summary = stringResource(R.string.susfs_config_summary),
                                icon = Icons.Filled.Fence,
                                onClick = actions.onOpenSusfsConfig,
                            )
                        }
                    }
                }
            }

            // Kernel features (KSU only).
            item {
                KsuIsValid {
                    FolkSettingsSectionGroup(title = stringResource(R.string.settings_sucompat)) {
                        item {
                            val suSummary = when (uiState.suCompatStatus) {
                                "unsupported" -> stringResource(R.string.feature_status_unsupported_summary)
                                "managed" -> stringResource(R.string.feature_status_managed_summary)
                                else -> stringResource(R.string.settings_sucompat_summary)
                            }
                            val suModes = listOf(
                                stringResource(R.string.settings_mode_enable_by_default),
                                stringResource(R.string.settings_mode_disable_until_reboot),
                                stringResource(R.string.settings_mode_disable_always),
                            )
                            FolkChoicePreference(
                                title = stringResource(R.string.settings_sucompat),
                                summary = suSummary,
                                icon = Icons.Filled.AdminPanelSettings,
                                options = suModes,
                                selectedIndex = uiState.suCompatMode,
                                enabled = uiState.suCompatStatus == "supported",
                                onSelect = actions.onSetSuCompatMode,
                            )
                        }

                        item {
                            val umountSummary = when (uiState.kernelUmountStatus) {
                                "unsupported" -> stringResource(R.string.feature_status_unsupported_summary)
                                "managed" -> stringResource(R.string.feature_status_managed_summary)
                                else -> stringResource(R.string.settings_kernel_umount_summary)
                            }
                            FolkSwitchPreference(
                                title = stringResource(R.string.settings_kernel_umount),
                                summary = umountSummary,
                                icon = Icons.Filled.LayersClear,
                                enabled = uiState.kernelUmountStatus == "supported",
                                checked = uiState.isKernelUmountEnabled,
                                onCheckedChange = actions.onSetKernelUmountEnabled,
                            )
                        }

                        item {
                            val selinuxHideSummary = when (uiState.selinuxHideStatus) {
                                "unsupported" -> stringResource(R.string.feature_status_unsupported_summary)
                                "managed" -> stringResource(R.string.feature_status_managed_summary)
                                else -> stringResource(R.string.settings_selinux_hide_summary)
                            }
                            FolkSwitchPreference(
                                title = stringResource(R.string.settings_selinux_hide),
                                summary = selinuxHideSummary,
                                icon = Icons.Filled.Security,
                                enabled = uiState.selinuxHideStatus == "supported",
                                checked = uiState.isSelinuxHideEnabled,
                                onCheckedChange = actions.onSetSelinuxHideEnabled,
                            )
                        }

                        item {
                            val sulogSummary = when (uiState.sulogStatus) {
                                "unsupported" -> stringResource(R.string.feature_status_unsupported_summary)
                                "managed" -> stringResource(R.string.feature_status_managed_summary)
                                else -> stringResource(R.string.settings_sulog_summary)
                            }
                            FolkSwitchPreference(
                                title = stringResource(R.string.settings_sulog),
                                summary = sulogSummary,
                                icon = Icons.AutoMirrored.Filled.Article,
                                enabled = uiState.sulogStatus == "supported",
                                checked = uiState.isSulogEnabled,
                                onCheckedChange = actions.onSetSulogEnabled,
                            )
                        }

                        item {
                            val adbRootSummary = when (uiState.adbRootStatus) {
                                "unsupported" -> stringResource(R.string.feature_status_unsupported_summary)
                                "managed" -> stringResource(R.string.feature_status_managed_summary)
                                else -> stringResource(R.string.settings_adb_root_summary)
                            }
                            FolkSwitchPreference(
                                title = stringResource(R.string.settings_adb_root),
                                summary = adbRootSummary,
                                icon = Icons.Filled.Adb,
                                enabled = uiState.adbRootStatus == "supported",
                                checked = uiState.isAdbRootEnabled,
                                onCheckedChange = actions.onSetAdbRootEnabled,
                            )
                        }

                        item {
                            FolkSwitchPreference(
                                title = stringResource(R.string.settings_soft_reboot),
                                summary = stringResource(R.string.settings_soft_reboot_summary),
                                icon = Icons.Filled.RestartAlt,
                                enabled = !uiState.isLateLoadMode,
                                checked = uiState.isLateLoadMode || uiState.useSoftReboot,
                                onCheckedChange = actions.onSetUseSoftReboot,
                            )
                        }
                    }
                }
            }

            // Behaviour (KSU only).
            item {
                KsuIsValid {
                    FolkSettingsSectionGroup(title = stringResource(R.string.settings_umount_modules_default)) {
                        item {
                            FolkSwitchPreference(
                                title = stringResource(R.string.settings_umount_modules_default),
                                summary = stringResource(R.string.settings_umount_modules_default_summary),
                                icon = Icons.AutoMirrored.Filled.Rule,
                                checked = uiState.isDefaultUmountModules,
                                onCheckedChange = actions.onSetDefaultUmountModules,
                            )
                        }
                        item {
                            FolkSwitchPreference(
                                title = stringResource(R.string.enable_web_debugging),
                                summary = stringResource(R.string.enable_web_debugging_summary),
                                icon = Icons.Filled.DeveloperMode,
                                checked = uiState.enableWebDebugging,
                                onCheckedChange = actions.onSetEnableWebDebugging,
                            )
                        }
                        item {
                            FolkSwitchPreference(
                                title = stringResource(R.string.settings_auto_jailbreak),
                                summary = stringResource(R.string.settings_auto_jailbreak_summary),
                                icon = Icons.Filled.FlashOn,
                                enabled = uiState.isLateLoadMode,
                                checked = uiState.autoJailbreak,
                                onCheckedChange = actions.onSetAutoJailbreak,
                            )
                        }
                    }
                }
            }

            // Uninstall, only meaningful in LKM mode.
            if (uiState.isLkmMode) {
                item {
                    FolkSettingsSectionGroup(title = stringResource(R.string.settings_uninstall)) {
                        item {
                            FolkNavigationPreference(
                                title = stringResource(R.string.settings_uninstall),
                                icon = Icons.Filled.Delete,
                                enabled = !uiState.isLateLoadMode,
                                onClick = { showUninstallDialog.value = true },
                            )
                        }
                    }
                }
            }

            // Diagnostics and about.
            item {
                FolkSettingsSectionGroup(title = stringResource(R.string.about)) {
                    item {
                        FolkNavigationPreference(
                            title = stringResource(R.string.send_log),
                            icon = Icons.Filled.BugReport,
                            onClick = { showBottomSheet = true },
                        )
                    }
                    item {
                        FolkNavigationPreference(
                            title = stringResource(R.string.settings_device),
                            summary = stringResource(R.string.settings_device_summary),
                            icon = Icons.Rounded.Android,
                            onClick = actions.onOpenDevice,
                        )
                    }
                    item {
                        FolkNavigationPreference(
                            title = stringResource(R.string.settings_onboarding),
                            summary = stringResource(R.string.settings_onboarding_summary),
                            icon = Icons.AutoMirrored.Filled.Article,
                            onClick = actions.onOpenOnboarding,
                        )
                    }
                    item {
                        FolkNavigationPreference(
                            title = stringResource(R.string.about),
                            icon = Icons.Filled.Info,
                            onClick = actions.onOpenAbout,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }

        if (showBottomSheet) {
            FolkSendLogSheet(
                onDismiss = { showBottomSheet = false },
                snackbarHostState = snackBarHost,
            )
        }
    }
}
