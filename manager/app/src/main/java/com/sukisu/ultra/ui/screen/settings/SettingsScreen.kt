package com.sukisu.ultra.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sukisu.ultra.ui.navigation3.Navigator
import com.sukisu.ultra.ui.navigation3.Route
import com.sukisu.ultra.ui.util.rememberSuSFSStatus
import com.sukisu.ultra.ui.viewmodel.SettingsViewModel

@Composable
fun SettingPager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true,
) {
    val context = LocalContext.current
    val viewModel = viewModel<SettingsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSusfsSupported = rememberSuSFSStatus()
    val latestIsCurrentPage by rememberUpdatedState(isCurrentPage)
    val initialResumeHandled = rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            viewModel.refresh()
        }
    }

    LifecycleResumeEffect(Unit) {
        if (initialResumeHandled.value && latestIsCurrentPage) {
            viewModel.refresh()
        }
        initialResumeHandled.value = true
        onPauseOrDispose { }
    }

    val actions = SettingsScreenActions(
        onSetCheckModuleUpdate = viewModel::setCheckModuleUpdate,
        onSetCheckUpdate = viewModel::setCheckUpdate,
        onOpenTheme = { navigator.push(Route.ColorPalette) },
        onOpenProfileTemplate = { navigator.push(Route.AppProfileTemplate) },
        onSetLanguage = { tag -> viewModel.setLanguage(context, tag) },
        onOpenLanguage = { navigator.push(Route.Language) },
        onSetSuCompatMode = viewModel::setSuCompatMode,
        onSetKernelUmountEnabled = viewModel::setKernelUmountEnabled,
        onSetSelinuxHideEnabled = viewModel::setSelinuxHideEnabled,
        onSetSulogEnabled = viewModel::setSulogEnabled,
        onSetAdbRootEnabled = viewModel::setAdbRootEnabled,
        onSetDefaultUmountModules = viewModel::setDefaultUmountModules,
        onSetEnableWebDebugging = viewModel::setEnableWebDebugging,
        onSetAutoJailbreak = viewModel::setAutoJailbreak,
        onSetUseSoftReboot = viewModel::setUseSoftReboot,
        onOpenAbout = { navigator.push(Route.About) },
        onOpenDevice = { navigator.push(Route.Device) },
        onOpenOnboarding = { navigator.push(Route.Onboarding) },
        onSetAlternativeIcon = { enabled -> viewModel.setAlternativeIcon(context, enabled) },
        onOpenTools = { navigator.push(Route.Tool) },
        onOpenSusfsConfig = { navigator.push(Route.SuSFS) },
    )

    SettingPagerFolk(
        uiState = uiState,
        actions = actions,
        bottomInnerPadding = bottomInnerPadding,
        isSusfsSupported = isSusfsSupported
    )
}
