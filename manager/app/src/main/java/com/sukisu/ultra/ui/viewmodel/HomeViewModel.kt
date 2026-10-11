package com.sukisu.ultra.ui.viewmodel

import android.content.Context
import android.os.Build
import android.system.Os
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sukisu.ultra.BuildConfig
import com.sukisu.ultra.KernelVersion
import com.sukisu.ultra.Natives
import com.sukisu.ultra.data.repository.SettingsRepository
import com.sukisu.ultra.data.repository.SettingsRepositoryImpl
import com.sukisu.ultra.getKernelVersion
import com.sukisu.ultra.ksuApp
import com.sukisu.ultra.ui.screen.home.HomeUiState
import com.sukisu.ultra.ui.screen.home.ManagerVersion
import com.sukisu.ultra.ui.screen.home.SystemInfo
import com.sukisu.ultra.ui.screen.home.getManagerVersion
import com.sukisu.ultra.ui.util.getSELinuxStatusRaw
import com.sukisu.ultra.ui.util.managerSignatureSha256
import com.sukisu.ultra.ui.util.resolveDeviceName
import com.sukisu.ultra.ui.util.rootAvailable

class HomeViewModel(
    private val settingsRepo: SettingsRepository = SettingsRepositoryImpl()
) : ViewModel() {

    // Empty to begin with, then filled off the main thread. buildState() opens a root shell,
    // runs `ksud module list` and starts another shell for `getenforce`, and it used to run in
    // the constructor - which is the first composition of the home tab, so the first frame paid
    // for all of it.
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** The load in flight, so the screen asking to refresh while one is already running does not
     *  start a second - each one is a root shell and a `ksud module list`. */
    private var loadJob: Job? = null

    init {
        reload()
    }

    fun refresh() = reload()

    private fun reload() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            val baseState = withContext(Dispatchers.IO) { buildState() }
            _uiState.update { baseState }
        }
    }

    /**
     * Gathers the home screen's state.
     *
     * Every field that can fail is read on its own and falls back to a safe value, because one
     * failure used to take the whole state with it: the callers below reach a root shell, a native
     * library and the package manager, and an exception from any of them meant `_uiState` was never
     * updated at all. The screen then sat on its initial empty state, which is what made the kernel
     * version, the manager version, the fingerprint and the SELinux row appear and disappear
     * between launches - they were never wrong, the update simply never arrived.
     *
     * Nothing here is allowed to abort the rest: the facts that need no root at all - the kernel
     * release, this app's own version, the fingerprint, the device name - are always available and
     * are always reported, root or not.
     */
    private fun buildState(): HomeUiState {
        val kernelVersion = runCatching { getKernelVersion() }
            .getOrElse { KernelVersion(-1, -1, -1) }

        // The native library is loaded in Natives' init, so every one of these can throw if it is
        // missing or the driver is absent. They are read through one guard each rather than a
        // single block so a device without a kernel still gets the rest of the state.
        val isManager = runCatching { Natives.isManager }.getOrDefault(false)
        val ksuVersion = if (isManager) runCatching { Natives.version }.getOrNull() else null
        val kernelUAPIVersion = if (isManager) runCatching { Natives.kernelUAPIVersion }.getOrNull() else null
        val managerUAPIVersion = runCatching { Natives.managerUAPIVersion }.getOrDefault(0)
        val lkmMode = ksuVersion?.let {
            runCatching { if (kernelVersion.isGKI()) Natives.isLkmMode else null }.getOrNull()
        }
        val isRootAvailable = runCatching { rootAvailable() }.getOrDefault(false)
        // Guarded, and it has to be: getManagerVersion dereferences the PackageManager's result and
        // the version name with `!!`, and it is handed the global application reference, which is
        // only assigned once the user is unlocked. A throw here used to take the manager version,
        // the kernel version, the device name and the fingerprint down with it - which is exactly
        // the four rows that came up blank while the rest of the card was fine.
        val managerVersion = runCatching { getManagerVersion(ksuApp) }
            .getOrElse { ManagerVersion(versionName = BuildConfig.VERSION_NAME, versionCode = 0L) }
        val kernelFullVersion = if (isManager) runCatching { Natives.getFullVersion() }.getOrNull() else null

        val zygiskImplementation = if (isManager && isRootAvailable) {
            runCatching {
                com.sukisu.ultra.ui.screen.home.getZygiskImplementation(
                    notInstalledText = ksuApp.getString(com.sukisu.ultra.R.string.home_zygisk_not_installed),
                    disabledText = ksuApp.getString(com.sukisu.ultra.R.string.home_zygisk_disabled),
                    rebootRequiredText = ksuApp.getString(com.sukisu.ultra.R.string.home_zygisk_reboot_required),
                )
            }.getOrNull()
        } else null

        return HomeUiState(
            kernelVersion = kernelVersion,
            ksuVersion = ksuVersion,
            lkmMode = lkmMode,
            isLkmBundled = lkmMode == true && runCatching { Natives.isLkmBundled }.getOrDefault(false),
            isManager = isManager,
            isManagerPrBuild = BuildConfig.IS_PR_BUILD,
            isKernelPrBuild = runCatching { Natives.isPrBuild }.getOrDefault(false),
            requiresNewKernel = isManager && managerUAPIVersion > (kernelUAPIVersion ?: 0),
            requiresNewManager = isManager && managerUAPIVersion < (kernelUAPIVersion ?: 0),
            kernelUAPIVersion = kernelUAPIVersion,
            managerUAPIVersion = managerUAPIVersion,
            isRootAvailable = isRootAvailable,
            isSafeMode = runCatching { Natives.isSafeMode }.getOrDefault(false),
            loaded = true,
            isLateLoadMode = runCatching { Natives.isLateLoadMode }.getOrDefault(false),
            // Read through the repository rather than the preference directly. The key is
            // "show_full_status", not FolkPatch's "show_fingerprint": that one means "hide the
            // fingerprint" and defaults to false, so reading it here inverted the setting for
            // anyone who had ever touched it.
            showFullStatus = settingsRepo.showFullStatus,
            showHomeDeviceModel = settingsRepo.showHomeDeviceModel,
            showHomeFingerprint = settingsRepo.showHomeFingerprint,
            showHomeSelinux = settingsRepo.showHomeSelinux,
            showHomeSeccomp = settingsRepo.showHomeSeccomp,
            managerSignature = runCatching { managerSignatureSha256(ksuApp) }.getOrNull(),
            currentManagerVersionCode = managerVersion.versionCode,
            systemInfo = SystemInfo(
                // `Os.uname()` needs no root, so this is the release string even on a device with
                // no kernel driver at all.
                kernelVersion = runCatching { Os.uname().release }.getOrDefault(""),
                managerVersion = "${managerVersion.versionName} (${managerVersion.versionCode}-$managerUAPIVersion)",
                deviceModel = runCatching { resolveDeviceName() }.getOrDefault(""),
                kernelFullVersion = kernelFullVersion,
                fingerprint = Build.FINGERPRINT,
                selinuxStatus = runCatching { getSELinuxStatusRaw() }.getOrDefault("Unknown"),
                seccompStatus = runCatching {
                    Os.prctl(21 /* PR_GET_SECCOMP */, 0, 0, 0, 0)
                }.getOrDefault(-1),
                zygiskImplementation = zygiskImplementation,
            ),
        )
    }
}
