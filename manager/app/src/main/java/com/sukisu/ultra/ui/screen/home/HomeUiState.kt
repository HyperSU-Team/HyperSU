package com.sukisu.ultra.ui.screen.home

import androidx.compose.runtime.Immutable
import com.sukisu.ultra.KernelVersion

@Immutable
data class HomeUiState(
    // Every field has a default so the home tab has something to draw before its state has been
    // collected - the real values need a root shell, so they arrive a moment later. `loaded`
    // tells the two apart.
    val kernelVersion: KernelVersion = KernelVersion(0, 0, 0),
    val ksuVersion: Int? = null,
    val managerUAPIVersion: Int = 0,
    val kernelUAPIVersion: Int? = null,
    val lkmMode: Boolean? = null,
    val isLkmBundled: Boolean = false,
    val isManager: Boolean = false,
    val isManagerPrBuild: Boolean = false,
    val isKernelPrBuild: Boolean = false,
    val requiresNewKernel: Boolean = false,
    val requiresNewManager: Boolean = false,
    val isRootAvailable: Boolean = false,
    val isSafeMode: Boolean = false,
    val isLateLoadMode: Boolean = false,
    val currentManagerVersionCode: Long = 0L,
    val systemInfo: SystemInfo = SystemInfo(
        kernelVersion = "",
        managerVersion = "",
        deviceModel = "",
        kernelFullVersion = null,
        fingerprint = "",
        selinuxStatus = "",
        seccompStatus = -1,
    ),
    val showFullStatus: Boolean = true,
    /**
     * This build's signing certificate digest, or null when it could not be read.
     *
     * Shown when the kernel does not recognise the manager: the kernel matches by certificate, so
     * the digest is the one fact that explains a rejection, and without it the user can only be
     * told to reinstall and hope.
     */
    val managerSignature: String? = null,
    /** Whether each of the home facts rows is shown. All default to on. */
    val showHomeDeviceModel: Boolean = true,
    val showHomeFingerprint: Boolean = true,
    val showHomeSelinux: Boolean = true,
    val showHomeSeccomp: Boolean = true,
    /** False until the first buildState() has come back. */
    val loaded: Boolean = false,
) {
    val isSELinuxPermissive: Boolean
        get() = systemInfo.selinuxStatus == "Permissive"

    val showGkiWarning: Boolean
        get() = ksuVersion != null && lkmMode == false

    val showLkmUpdate: Boolean
        get() = isManager &&
                lkmMode == true &&
                isLkmBundled &&
                ksuVersion?.toLong() != currentManagerVersionCode &&
                !requiresNewKernel &&
                !requiresNewManager

    val showCustomLkmBadge: Boolean
        get() = lkmMode == true && !isLkmBundled

    val showRootWarning: Boolean
        get() = ksuVersion != null && !isRootAvailable

    val showManagerPrBuildWarning: Boolean
        get() = isManager && isManagerPrBuild

    val showKernelPrBuildWarning: Boolean
        get() = isManager && !isManagerPrBuild && isKernelPrBuild
}

@Immutable
data class HomeActions(
    val onInstallClick: () -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onJailbreakClick: () -> Unit = {},
)
