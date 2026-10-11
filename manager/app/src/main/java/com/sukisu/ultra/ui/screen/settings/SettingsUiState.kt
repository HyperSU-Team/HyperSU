package com.sukisu.ultra.ui.screen.settings

import androidx.compose.runtime.Immutable
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec

@Immutable
data class SettingsUiState(
    val appLanguage: String = "",
    val checkModuleUpdate: Boolean = true,
    val alternativeIcon : Boolean = false,
    val themeMode: Int = 0,
    val keyColor: Int = 0,
    val colorStyle: String = PaletteStyle.TonalSpot.name,
    val colorSpec: String = ColorSpec.SpecVersion.SPEC_2025.name,
    val enablePredictiveBack: Boolean = false,
    val enableSwipeDismiss: Boolean = true,
    // Native, not CrossAxisInterceptor: the interceptor consumes horizontal drags in the
    // Initial pass and starves every settings slider of its drag.
    val pagerInterceptionMode: Int = 0,
    val enableFloatingBottomBar: Boolean = false,
    val enableNavigationBadge: Boolean = true,
    val pageScale: Float = 1.0f,
    val moduleDescriptionMaxLines: Int = 4,
    val simpleListBottomBar: Boolean = false,
    val enableWebDebugging: Boolean = false,
    val showFullStatus: Boolean = true,
    val showHomeDeviceModel: Boolean = true,
    val showHomeFingerprint: Boolean = true,
    val showHomeSelinux: Boolean = true,
    val showHomeSeccomp: Boolean = true,

    // Su Compat
    val suCompatStatus: String = "",
    val suCompatMode: Int = 0, // 0: enable default, 1: disable until reboot, 2: disable always
    val isSuEnabled: Boolean = false,

    // Kernel Umount
    val kernelUmountStatus: String = "",
    val isKernelUmountEnabled: Boolean = false,

    // SELinux Hide
    val selinuxHideStatus: String = "",
    val isSelinuxHideEnabled: Boolean = false,

    // SU Log
    val sulogStatus: String = "",
    val isSulogEnabled: Boolean = false,

    // Umount Modules
    val isDefaultUmountModules: Boolean = false,

    // ADB Root
    val adbRootStatus: String = "",
    val isAdbRootEnabled: Boolean = false,

    val isLkmMode: Boolean = false,
    val isLateLoadMode: Boolean = false,

    // Auto Jailbreak
    val autoJailbreak: Boolean = false,

    // Soft Reboot
    val useSoftReboot: Boolean = false
)

@Immutable
data class SettingsScreenActions(
    val onSetCheckModuleUpdate: (Boolean) -> Unit,
    val onOpenTheme: () -> Unit,
    val onOpenProfileTemplate: () -> Unit,
    val onSetLanguage: (String) -> Unit,
    /** Opens the language picker page; the choice is made there. */
    val onOpenLanguage: () -> Unit,
    val onSetSuCompatMode: (Int) -> Unit,
    val onSetKernelUmountEnabled: (Boolean) -> Unit,
    val onSetSelinuxHideEnabled: (Boolean) -> Unit,
    val onSetSulogEnabled: (Boolean) -> Unit,
    val onSetAdbRootEnabled: (Boolean) -> Unit,
    val onSetDefaultUmountModules: (Boolean) -> Unit,
    val onSetEnableWebDebugging: (Boolean) -> Unit,
    val onSetModuleDescriptionMaxLines: (Int) -> Unit,
    val onSetSimpleListBottomBar: (Boolean) -> Unit,
    val onSetShowFullStatus: (Boolean) -> Unit,
    val onSetShowHomeDeviceModel: (Boolean) -> Unit,
    val onSetShowHomeFingerprint: (Boolean) -> Unit,
    val onSetShowHomeSelinux: (Boolean) -> Unit,
    val onSetShowHomeSeccomp: (Boolean) -> Unit,
    val onSetAutoJailbreak: (Boolean) -> Unit,
    val onSetUseSoftReboot: (Boolean) -> Unit,
    val onOpenAbout: () -> Unit,
    val onSetAlternativeIcon: (Boolean) -> Unit,
    val onOpenTools: () -> Unit,
    val onOpenSusfsConfig: () -> Unit,
)
