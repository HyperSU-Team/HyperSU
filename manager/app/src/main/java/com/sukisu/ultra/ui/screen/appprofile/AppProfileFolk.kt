package com.sukisu.ultra.ui.screen.appprofile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.Natives
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.AppIconImage
import com.sukisu.ultra.ui.component.folk.FolkPreference
import com.sukisu.ultra.ui.component.folk.FolkScaffold
import com.sukisu.ultra.ui.component.folk.FolkSettingsGroup
import com.sukisu.ultra.ui.component.folk.FolkSelectableRow
import com.sukisu.ultra.ui.component.folk.FolkSwitchPreference
import com.sukisu.ultra.ui.component.folk.FolkTitleStyle
import com.sukisu.ultra.ui.component.profile.AppProfileConfig
import com.sukisu.ultra.ui.component.profile.RootProfileConfig
import com.sukisu.ultra.ui.component.profile.TemplateConfig
import com.sukisu.ultra.ui.component.statustag.StatusTag
import com.sukisu.ultra.ui.theme.tokens.FolkType
import com.sukisu.ultra.ui.util.ownerNameForUid
import com.sukisu.ultra.ui.viewmodel.SuperUserViewModel

/**
 * The per-app (or per-UID) profile editor, in the FolkPatch design.
 *
 * The header states the app, its version and package (or, for a shared UID, the
 * app count and shared id) and carries the UID tags. Below it the root grant is
 * a switch, and the profile mode - default, template or custom - is a radio
 * group; the matching editor follows. A UID group lists the apps it affects.
 *
 * The grant/mode state machine and the profile writing are exactly the ones the
 * Material screen used, including the rule that picking Template does not itself
 * change the profile.
 */
@Composable
fun AppProfileScreenFolk(
    state: AppProfileUiState,
    actions: AppProfileActions,
    snackBarHost: SnackbarHostState,
) {
    val showActions = !state.appGroup.primary.isWebViewZygote

    FolkScaffold(
        title = stringResource(R.string.profile),
        titleStyle = FolkTitleStyle.Flexible,
        onBack = actions.onBack,
        snackbarHostState = snackBarHost,
        actions = {
            if (!state.isUidGroup && showActions) {
                AppProfileMenu(
                    packageName = state.packageName,
                    userId = state.uid / 100000,
                    onLaunchApp = actions.onLaunchApp,
                    onForceStopApp = actions.onForceStopApp,
                    onRestartApp = actions.onRestartApp,
                )
            }
        },
    ) { innerPadding ->
        AppProfileInner(
            modifier = Modifier
                .fillMaxHeight()
                .padding(top = innerPadding.calculateTopPadding())
                .imePadding()
                .verticalScroll(rememberScrollState()),
            packageName = if (state.isUidGroup) {
                ""
            } else {
                state.appGroup.primary.displayIdentifier
            },
            appLabel = if (state.isUidGroup) {
                ownerNameForUid(state.appGroup.primary.uid)
            } else {
                state.appGroup.primary.label
            },
            appIcon = {
                AppIconImage(
                    packageInfo = state.appGroup.primary.packageInfo,
                    label = state.appGroup.primary.label,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(48.dp),
                )
            },
            appUid = state.uid,
            sharedUserId = if (state.isUidGroup) state.sharedUserId else "",
            appVersionName = if (state.isUidGroup) {
                ""
            } else {
                state.appGroup.primary.packageInfo.versionName ?: ""
            },
            appVersionCode = if (state.isUidGroup) {
                0L
            } else {
                state.appGroup.primary.packageInfo.longVersionCode
            },
            profile = state.profile,
            isUidGroup = state.isUidGroup,
            isSpecialApp = state.appGroup.primary.special,
            affectedApps = state.appGroup.apps,
            onViewTemplate = actions.onViewTemplate,
            onManageTemplate = actions.onManageTemplate,
            onProfileChange = actions.onProfileChange,
        )
    }
}

@Composable
private fun AppProfileMenu(
    packageName: String,
    userId: Int,
    onLaunchApp: (String, Int) -> Unit,
    onForceStopApp: (String, Int) -> Unit,
    onRestartApp: (String, Int) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var showDropdown by remember { mutableStateOf(false) }

    IconButton(onClick = { showDropdown = true }) {
        Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = stringResource(R.string.settings),
        )
    }
    DropdownMenuPopup(
        expanded = showDropdown,
        onDismissRequest = { showDropdown = false },
    ) {
        val menuItems = listOf(
            R.string.launch_app to onLaunchApp,
            R.string.force_stop_app to onForceStopApp,
            R.string.restart_app to onRestartApp,
        )
        DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
            menuItems.forEachIndexed { index, (resId, action) ->
                SelectableDropdownMenuItem(
                    selected = false,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        showDropdown = false
                        action(packageName, userId)
                    },
                    text = { Text(stringResource(resId)) },
                    shapes = MenuDefaults.itemShape(index = index, count = menuItems.size),
                )
            }
        }
    }
}

@Composable
private fun AppProfileInner(
    modifier: Modifier = Modifier,
    packageName: String,
    appLabel: String,
    appIcon: @Composable (() -> Unit),
    appUid: Int,
    sharedUserId: String = "",
    appVersionName: String,
    appVersionCode: Long,
    profile: Natives.Profile,
    isUidGroup: Boolean = false,
    isSpecialApp: Boolean = false,
    affectedApps: List<SuperUserViewModel.AppInfo> = emptyList(),
    onViewTemplate: (id: String) -> Unit = {},
    onManageTemplate: () -> Unit = {},
    onProfileChange: (Natives.Profile) -> Unit,
) {
    val isRootGranted = !isSpecialApp && profile.allowSu
    val userId = appUid / 100000
    val appId = appUid % 100000

    val initialRootMode = when {
        profile.rootUseDefault -> Mode.Default
        profile.rootTemplate != null -> Mode.Template
        else -> Mode.Custom
    }
    var rootMode by rememberSaveable(profile) { mutableStateOf(initialRootMode) }
    val nonRootMode = if (profile.nonRootUseDefault) Mode.Default else Mode.Custom
    val mode = if (isRootGranted) rootMode else nonRootMode

    Column(modifier = modifier) {
        FolkSettingsGroup(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    appIcon()
                    Spacer(Modifier.size(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = appLabel,
                            style = FolkType.Title,
                            fontWeight = FontWeight.Medium,
                        )
                        when {
                            isSpecialApp -> Text(
                                text = packageName,
                                style = FolkType.Caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            !isUidGroup -> {
                                Text(
                                    text = "$appVersionName ($appVersionCode)",
                                    style = FolkType.Caption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = packageName,
                                    style = FolkType.Caption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            else -> {
                                if (sharedUserId.isNotEmpty()) {
                                    Text(
                                        text = sharedUserId,
                                        style = FolkType.Caption,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.group_contains_apps, affectedApps.size),
                                    style = FolkType.Caption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (userId != 0) {
                            StatusTag(
                                label = "USER $userId",
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                            )
                            StatusTag(
                                label = "UID $appId",
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                            )
                        } else {
                            StatusTag(
                                label = "UID $appUid",
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                            )
                        }
                    }
                }
            }

            if (!isSpecialApp) {
                item {
                    FolkSwitchPreference(
                        title = stringResource(R.string.superuser),
                        icon = Icons.Filled.Security,
                        checked = isRootGranted,
                        onCheckedChange = { onProfileChange(profile.copy(allowSu = it)) },
                    )
                }
            }

            item {
                FolkPreference(
                    title = stringResource(R.string.profile),
                    summary = mode.text,
                    icon = Icons.Filled.AccountCircle,
                )
            }
        }

        Crossfade(targetState = isRootGranted, label = "AppProfileMode") { current ->
            Column(modifier = Modifier.padding(bottom = 6.dp + 48.dp)) {
                if (current) {
                    ProfileModePicker(
                        mode = mode,
                        hasTemplate = profile.rootTemplate != null,
                        onModeChange = {
                            // Template mode must not itself change the profile.
                            if (it == Mode.Default || it == Mode.Custom) {
                                onProfileChange(
                                    profile.copy(
                                        rootUseDefault = it == Mode.Default,
                                        rootTemplate = null,
                                    )
                                )
                            }
                            rootMode = it
                        },
                    )

                    AnimatedVisibility(
                        visible = mode == Mode.Template,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        TemplateConfig(
                            profile = profile,
                            onViewTemplate = onViewTemplate,
                            onManageTemplate = onManageTemplate,
                            onProfileChange = onProfileChange,
                        )
                    }

                    AnimatedVisibility(
                        visible = mode == Mode.Custom,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        RootProfileConfig(
                            fixedName = true,
                            enabled = mode == Mode.Custom,
                            profile = profile,
                            onProfileChange = onProfileChange,
                        )
                    }
                } else {
                    ProfileModePicker(
                        mode = mode,
                        hasTemplate = false,
                        onModeChange = {
                            onProfileChange(profile.copy(nonRootUseDefault = it == Mode.Default))
                        },
                    )

                    AppProfileConfig(
                        fixedName = true,
                        profile = profile,
                        enabled = mode == Mode.Custom,
                        onProfileChange = onProfileChange,
                    )
                }

                if (isUidGroup) {
                    FolkSettingsGroup(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        affectedApps.forEach { app ->
                            item(key = app.packageName) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AppIconImage(
                                        packageInfo = app.packageInfo,
                                        label = app.label,
                                        modifier = Modifier.size(36.dp),
                                    )
                                    Spacer(Modifier.size(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = app.label, style = FolkType.Title)
                                        Text(
                                            text = app.displayIdentifier,
                                            style = FolkType.Caption,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The default/template/custom choice, as a radio group in the Folk list style. */
@Composable
private fun ProfileModePicker(
    mode: Mode,
    hasTemplate: Boolean,
    onModeChange: (Mode) -> Unit,
) {
    val options = listOf(
        Mode.Default to stringResource(R.string.profile_default),
        Mode.Template to stringResource(R.string.profile_template),
        Mode.Custom to stringResource(R.string.profile_custom),
    )
    val haptic = LocalHapticFeedback.current

    FolkSettingsGroup(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        options.forEach { (option, label) ->
            item(key = option.name) {
                FolkSelectableRow(
                    title = label,
                    selected = mode == option,
                    enabled = if (option == Mode.Template) hasTemplate else true,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onModeChange(option)
                    },
                )
            }
        }
    }
}
