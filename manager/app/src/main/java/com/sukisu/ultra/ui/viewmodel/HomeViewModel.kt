package com.sukisu.ultra.ui.viewmodel

import android.os.Build
import android.system.Os
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

/**
 * The facts that need nothing from the kernel: no root, no driver, no shell.
 *
 * Read on their own, before anything that does need those, so a failure in the root-dependent
 * half cannot take them down with it.
 */
private data class HomeBasics(
    val kernelRelease: String,
    val fingerprint: String,
    val deviceModel: String,
    val managerVersion: ManagerVersion,
    val selinuxStatus: String,
    val seccompStatus: Int,
)

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
            // A cold start's first read can come back without the facts that need no root at all:
            // the driver is still coming up, the package manager has not settled, or the process
            // was started before the user unlocked. A single attempt then left the rows showing
            // dashes until something else recreated the activity - which is why re-selecting the
            // language, and nothing else, appeared to fix it. So it is retried, and only while the
            // state is still missing something it should always have.
            var attempt = 0
            var state = readState()
            while (!state.hasAlwaysAvailableFacts() && attempt < MaxLoadAttempts - 1) {
                attempt++
                delay(RetryDelayMillis * attempt)
                state = readState()
            }

            // A later read that fails must not replace a good one with a blank screen, so the
            // previously shown state is kept whenever the new one has less to say.
            _uiState.update { previous ->
                if (previous.loaded && !state.hasAlwaysAvailableFacts() && previous.hasAlwaysAvailableFacts()) {
                    previous
                } else {
                    state
                }
            }
        }
    }

    private suspend fun readState(): HomeUiState = withContext(Dispatchers.IO) {
        runCatching { buildState() }.getOrElse { failure ->
            Log.w(TAG, "could not read the home state", failure)
            // Even a failed read reports the facts that need nothing, rather than blanking them.
            HomeUiState(loaded = true, systemInfo = basicsAsSystemInfo(readBasics(), kernelUAPIVersion = 0))
        }
    }

    /**
     * Whether the state carries the facts that no permission, driver or root shell is needed for.
     *
     * These come from `Os.uname`, `Build` and the package manager, so they are available on every
     * device - with or without a kernel, and before or after a root shell can be opened. An empty
     * one therefore means the read failed rather than that the device has no value, and is worth
     * retrying.
     */
    private fun HomeUiState.hasAlwaysAvailableFacts(): Boolean =
        systemInfo.kernelVersion.isNotBlank() &&
                systemInfo.managerVersion.isNotBlank() &&
                systemInfo.fingerprint.isNotBlank()

    private fun readBasics(): HomeBasics = HomeBasics(
        kernelRelease = runCatching { Os.uname().release }.getOrNull().orEmpty(),
        fingerprint = runCatching { Build.FINGERPRINT }.getOrNull().orEmpty(),
        deviceModel = runCatching { resolveDeviceName() }.getOrNull().orEmpty(),
        managerVersion = runCatching { getManagerVersion(ksuApp) }
            .getOrElse { ManagerVersion(versionName = BuildConfig.VERSION_NAME, versionCode = 0L) },
        selinuxStatus = runCatching { getSELinuxStatusRaw() }.getOrNull().orEmpty(),
        // PR_GET_SECCOMP. Reports -1 rather than throwing when seccomp is not compiled in.
        seccompStatus = runCatching { Os.prctl(21 /* PR_GET_SECCOMP */, 0, 0, 0, 0) }.getOrDefault(-1),
    )

    private fun basicsAsSystemInfo(
        basics: HomeBasics,
        kernelUAPIVersion: Int,
    ): SystemInfo = SystemInfo(
        kernelVersion = basics.kernelRelease,
        managerVersion = "${basics.managerVersion.versionName} " +
                "(${basics.managerVersion.versionCode}-$kernelUAPIVersion)",
        deviceModel = basics.deviceModel,
        kernelFullVersion = null,
        fingerprint = basics.fingerprint,
        selinuxStatus = basics.selinuxStatus,
        seccompStatus = basics.seccompStatus,
    )

    /**
     * Gathers the home screen's state.
     *
     * The order matters. Everything that needs no root is read and assembled first, and the
     * settings are read one guarded flag at a time, so a failure anywhere in the root-dependent
     * half - or in a preference read - cannot take the kernel version, this app's own version, the
     * fingerprint or the device name down with it. Those four used to come up blank while the rest
     * of the card was fine, because they were built in the same call as everything else and a
     * throw discarded the whole thing.
     */
    private fun buildState(): HomeUiState {
        // ---- Facts that need nothing: no root, no driver, no shell. ----
        val basics = readBasics()

        // ---- Facts that need the driver. Each one guarded on its own. ----
        val kernelVersion = runCatching { getKernelVersion() }
            .getOrElse { KernelVersion(-1, -1, -1) }
        val isManager = runCatching { Natives.isManager }.getOrDefault(false)
        val ksuVersion = if (isManager) runCatching { Natives.version }.getOrNull() else null
        val kernelUAPIVersion = if (isManager) runCatching { Natives.kernelUAPIVersion }.getOrNull() else null
        val managerUAPIVersion = runCatching { Natives.managerUAPIVersion }.getOrDefault(0)
        val lkmMode = ksuVersion?.let {
            runCatching { if (kernelVersion.isGKI()) Natives.isLkmMode else null }.getOrNull()
        }
        val isRootAvailable = runCatching { rootAvailable() }.getOrDefault(false)
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

        // ---- Settings. Read through the repository, one guard each. ----
        // "show_full_status", not FolkPatch's "show_fingerprint": that one means "hide the
        // fingerprint" and defaults to false, so reading it here inverted the setting for anyone
        // who had ever touched it.
        val showFullStatus = runCatching { settingsRepo.showFullStatus }.getOrDefault(true)
        val showHomeDeviceModel = runCatching { settingsRepo.showHomeDeviceModel }.getOrDefault(true)
        val showHomeFingerprint = runCatching { settingsRepo.showHomeFingerprint }.getOrDefault(true)
        val showHomeSelinux = runCatching { settingsRepo.showHomeSelinux }.getOrDefault(true)
        val showHomeSeccomp = runCatching { settingsRepo.showHomeSeccomp }.getOrDefault(true)

        val systemInfo = basicsAsSystemInfo(basics, managerUAPIVersion)
            .copy(kernelFullVersion = kernelFullVersion, zygiskImplementation = zygiskImplementation)

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
            showFullStatus = showFullStatus,
            showHomeDeviceModel = showHomeDeviceModel,
            showHomeFingerprint = showHomeFingerprint,
            showHomeSelinux = showHomeSelinux,
            showHomeSeccomp = showHomeSeccomp,
            managerSignature = runCatching { managerSignatureSha256(ksuApp) }.getOrNull(),
            currentManagerVersionCode = basics.managerVersion.versionCode,
            systemInfo = systemInfo,
        )
    }

    private companion object {
        const val TAG = "HomeViewModel"

        /** How many times a cold start's incomplete read is retried before it is shown as it is. */
        const val MaxLoadAttempts = 3

        /** Between attempts, multiplied by the attempt number so the wait grows. */
        const val RetryDelayMillis = 400L
    }
}
