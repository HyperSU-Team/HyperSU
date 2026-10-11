package com.sukisu.ultra.ui.screen.install

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.sukisu.ultra.R
import com.sukisu.ultra.getKernelVersion
import com.sukisu.ultra.ui.component.choosekmidialog.ChooseKmiDialog
import com.sukisu.ultra.ui.kernelFlash.FlashConfirmDialog
import com.sukisu.ultra.ui.kernelFlash.component.SlotSelectionDialog
import com.sukisu.ultra.ui.component.dialog.rememberLoadingDialog
import com.sukisu.ultra.ui.kernelFlash.rememberAnyKernel3State
import com.sukisu.ultra.ui.navigation3.LocalNavigator
import com.sukisu.ultra.ui.navigation3.Route
import com.sukisu.ultra.ui.screen.flash.FlashIt
import com.sukisu.ultra.ui.screen.susfs.util.SuSFSManager
import com.sukisu.ultra.ui.util.LkmSelection
import com.sukisu.ultra.ui.util.getAvailablePartitions
import com.sukisu.ultra.ui.util.getCurrentKmi
import com.sukisu.ultra.ui.util.getDefaultPartition
import com.sukisu.ultra.ui.util.getSlotSuffix
import com.sukisu.ultra.ui.util.isAbDevice
import android.net.Uri
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalResources
import com.sukisu.ultra.ui.component.dialog.DownloadDialog
import com.sukisu.ultra.ui.util.*

@Composable
fun InstallScreen(
    preselectedKernelUri: Uri? = null
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    var probeJob by remember { mutableStateOf<Job?>(null) }
    val loadingDialog = rememberLoadingDialog()

    var installMethod by rememberSaveable { mutableStateOf<InstallMethod?>(null) }
    var downloadDialogShown by rememberSaveable { mutableStateOf(false) }
    var remotePartitions by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var remotePartitionSelectionIndex by rememberSaveable { mutableIntStateOf(0) }
    var lkmSelection by rememberSaveable { mutableStateOf<LkmSelection>(LkmSelection.KmiNone) }
    var partitionSelectionIndex by rememberSaveable { mutableIntStateOf(0) }
    var hasCustomSelected by rememberSaveable { mutableStateOf(false) }
    val showChooseKmiDialog = rememberSaveable { mutableStateOf(false) }
    var advancedOptionsShown by rememberSaveable { mutableStateOf(false) }
    var allowShell by rememberSaveable { mutableStateOf(false) }
    var enableAdb by rememberSaveable { mutableStateOf(false) }
    var forceBackup by rememberSaveable { mutableStateOf(false) }

    // Read the configuration from the boot image ksu_config
    val bootConfig by produceState(initialValue = BootConfig()) { value = getBootConfig() }
    var spoofRelease by rememberSaveable { mutableStateOf(SuSFSManager.getKernelSpoofRelease()) }
    var spoofVersion by rememberSaveable { mutableStateOf(SuSFSManager.getKernelSpoofVersion()) }

    LaunchedEffect(bootConfig) {
        spoofRelease = bootConfig.spoofRelease.ifEmpty { spoofRelease }
        spoofVersion = bootConfig.spoofVersion.ifEmpty { spoofVersion }
    }

    val currentKmi by produceState(initialValue = "") { value = getCurrentKmi() }
    val partitions by produceState(initialValue = emptyList()) { value = getAvailablePartitions() }
    val defaultPartition by produceState(initialValue = "") { value = getDefaultPartition() }
    val rootAvailable by produceState(initialValue = false) { value = rootAvailable() }
    val isAbDevice by produceState(initialValue = false) { value = isAbDevice() }
    val isGkiDevice by produceState(initialValue = false) { value = getKernelVersion().isGKI() }

    val selectFileTip = stringResource(id = R.string.select_file_tip, defaultPartition)
    val selectFileTipNoGki = stringResource(id = R.string.select_file_tip_nogki)

    val downloadFileMsg = stringResource(id = R.string.download_dialog_msg)

    val horizonKernelSummary = stringResource(R.string.horizon_kernel_summary)
    val installMethodOptions = remember(rootAvailable, isAbDevice, isGkiDevice, selectFileTip, selectFileTipNoGki, downloadFileMsg, horizonKernelSummary) {
        buildList {
            add(InstallMethod.SelectFile(summary = if (isGkiDevice) selectFileTip else selectFileTipNoGki))
            add(InstallMethod.DownloadFile(summary = downloadFileMsg))
            if (rootAvailable && isGkiDevice) {
                add(InstallMethod.DirectInstall)
                if (isAbDevice) add(InstallMethod.DirectInstallToInactiveSlot)
            }
            // The AnyKernel3 row needs root only: it flashes a kernel archive the user holds,
            // which does not require the device to be GKI.
            //
            // There is one such row, not two. A second one was added under the name
            // AnyKernel3 while this one is already labelled "AnyKernel3 Kernel" and runs the
            // same flow, so the list showed the same entry twice.
            if (rootAvailable) {
                add(InstallMethod.HorizonKernel(summary = horizonKernelSummary))
            }
        }
    }

    val installMethodState = remember { mutableStateOf<InstallMethod?>(null) }

    // AnyKernel3 flow state (slot selection, confirmation and KPM patching).
    val anyKernel3State = rememberAnyKernel3State(
        installMethodState = installMethodState,
        preselectedKernelUri = preselectedKernelUri?.toString(),
        horizonKernelSummary = horizonKernelSummary,
        isAbDevice = isAbDevice
    )

    // Keep installMethod and anyKernel3State in step.
    LaunchedEffect(installMethod) {
        installMethodState.value = installMethod
    }

    // ...and back again. The slot step writes the archive with its slot into
    // installMethodState, and cancelling the confirmation clears it, but onInstall and the
    // confirmation read the saveable installMethod. Without this the chosen slot was dropped
    // on the way to the flash, and a cancelled pick left its row marked as chosen.
    //
    // The two effects settle rather than ping-ponging: each writes only on a real difference,
    // and InstallMethod compares structurally, so the echo is always equal.
    LaunchedEffect(installMethodState.value) {
        val fromState = installMethodState.value
        if (fromState != installMethod) {
            installMethod = fromState
        }
    }

    val showSlotSelectionDialog = anyKernel3State.showSlotSelectionDialog && isAbDevice

    // Slot selection dialog.
    if (showSlotSelectionDialog) {
        SlotSelectionDialog(
            show = true,
            onDismiss = { anyKernel3State.onDismissSlotDialog() },
            onSlotSelected = { slot ->
                anyKernel3State.onSlotSelected(slot)
            }
        )
    }

    val isOta = installMethod is InstallMethod.DirectInstallToInactiveSlot
    val slotSuffix by produceState(initialValue = "", isOta) { value = getSlotSuffix(isOta) }
    val defaultIndex = remember(partitions, defaultPartition) {
        partitions.indexOf(defaultPartition).coerceAtLeast(0)
    }

    LaunchedEffect(partitions, defaultIndex, hasCustomSelected) {
        if (partitions.isEmpty()) return@LaunchedEffect
        if (!hasCustomSelected) {
            partitionSelectionIndex = defaultIndex.coerceIn(0, partitions.lastIndex)
        } else if (partitionSelectionIndex > partitions.lastIndex) {
            partitionSelectionIndex = partitions.lastIndex
        }
    }

    val displayPartitions = remember(partitions, defaultPartition) {
        partitions.map { name -> if (defaultPartition == name) "$name (default)" else name }
    }
    val remoteDisplayPartitions = remember(remotePartitions, defaultPartition) {
        remotePartitions.map { name -> if (defaultPartition == name) "$name (default)" else name }
    }

    fun showMessage(message: String) {
        scope.launch {
            snackbarHost.showSnackbar(message)
        }
    }

    val onInstall = {
        installMethod?.let { method ->
            when (method) {
                // The archive row goes through the kernel-flash route.
                is InstallMethod.HorizonKernel -> {
                    method.archiveUri?.let { uri ->
                        navigator.push(
                            Route.KernelFlash(
                                kernelUri = uri,
                                selectedSlot = method.archiveSlot,
                                kpmPatchEnabled = false,
                                kpmUndoPatch = false
                            )
                        )
                    }
                }
                else -> {
                    val isOta = method is InstallMethod.DirectInstallToInactiveSlot
                    navigator.push(
                        Route.Flash(
                            when (method) {
                                is InstallMethod.DownloadFile -> FlashIt.DownloadBoot(
                                    url = method.url ?: return@let,
                                    partition = method.partition ?: return@let,
                                    lkm = lkmSelection,
                                    allowShell = allowShell,
                                    enableAdb = enableAdb,
                                    backup = forceBackup,
                                    spoofRelease = spoofRelease.trim(),
                                    spoofVersion = spoofVersion.trim(),
                                )
                                else -> FlashIt.FlashBoot(
                                    boot = if (method is InstallMethod.SelectFile) method.uri else null,
                                    lkm = lkmSelection,
                                    ota = isOta,
                                    partition = partitions.getOrNull(partitionSelectionIndex),
                                    allowShell = allowShell,
                                    enableAdb = enableAdb,
                                    backup = method is InstallMethod.SelectFile && forceBackup,
                                    spoofRelease = spoofRelease.trim(),
                                    spoofVersion = spoofVersion.trim(),
                                )
                            }
                        )
                    )
                }
            }
        }
    }

    // Confirmation for the archive the user picked. It replaces the KPM dialog that used to
    // appear at this point, which asked about patching before anything had been confirmed.
    // Declared after onInstall because confirming is what starts the flash.
    if (anyKernel3State.showConfirmDialog) {
        val archive = installMethod
        if (archive != null && archive.isKernelArchive) {
            FlashConfirmDialog(
                archive = archive,
                onDismiss = { anyKernel3State.onDismissConfirmDialog() },
                onConfirm = {
                    anyKernel3State.onConfirmFlash()
                    onInstall()
                },
            )
        }
    }

    ChooseKmiDialog(
        show = showChooseKmiDialog.value,
        onDismissRequest = { showChooseKmiDialog.value = false },
        onSelected = { kmi ->
            kmi?.let {
                lkmSelection = LkmSelection.KmiString(it)
                onInstall()
            }
        }
    )

    DownloadDialog(
        show = downloadDialogShown,
        onConfirm = { url ->
            downloadDialogShown = false
            probeJob?.cancel()
            probeJob = scope.launch {
                try {
                    loadingDialog.showLoading()
                    val result = probeRemoteBootPartitions(url)
                    if (result.partitions.isEmpty()) {
                        showMessage(resources.getString(R.string.download_no_boot_partition))
                    } else {
                        val defaultIdx = result.partitions.indexOf(defaultPartition).coerceAtLeast(0)
                        remotePartitions = result.partitions
                        remotePartitionSelectionIndex = defaultIdx
                        installMethod = InstallMethod.DownloadFile(
                            url = url,
                            partition = result.partitions[defaultIdx],
                            summary = downloadFileMsg,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    showMessage(
                        resources.getString(R.string.download_probe_failed, e.message ?: "")
                    )
                } finally {
                    loadingDialog.hide()
                }
            }
        },
        onDismiss = { downloadDialogShown = false }
    )

    val selectLkmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                if (isKoFile(context, uri)) {
                    lkmSelection = LkmSelection.LkmUri(uri)
                } else {
                    lkmSelection = LkmSelection.KmiNone
                    showMessage(resources.getString(R.string.install_only_support_ko_file))
                }
            }
        }
    }
    val selectImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                val option: InstallMethod? = when (installMethod) {
                    is InstallMethod.SelectFile -> InstallMethod.SelectFile(uri, summary = selectFileTip)
                    is InstallMethod.HorizonKernel -> InstallMethod.HorizonKernel(uri, summary = horizonKernelSummary)
                    else -> null
                }
                option?.let { opt ->
                    installMethod = opt
                    // The archive row enters the slot-selection and confirmation flow.
                    if (opt.isKernelArchive) {
                        anyKernel3State.onHorizonKernelSelected(opt)
                    }
                }
            }
        }
    }

    val state = InstallUiState(
        installMethod = installMethod,
        lkmSelection = lkmSelection,
        partitionSelectionIndex = partitionSelectionIndex,
        displayPartitions = displayPartitions,
        remoteDisplayPartitions = remoteDisplayPartitions,
        remotePartitionSelectionIndex = remotePartitionSelectionIndex,
        currentKmi = currentKmi,
        slotSuffix = slotSuffix,
        installMethodOptions = installMethodOptions,
        canSelectPartition = installMethod is InstallMethod.DirectInstall ||
            installMethod is InstallMethod.DirectInstallToInactiveSlot ||
            installMethod is InstallMethod.DownloadFile,
        advancedOptionsShown = advancedOptionsShown,
        allowShell = allowShell,
        enableAdb = enableAdb,
        forceBackup = forceBackup,
        canForceBackup = installMethod is InstallMethod.SelectFile,
        spoofRelease = spoofRelease,
        spoofVersion = spoofVersion,
        anyKernel3State = anyKernel3State,
        showSlotSelectionDialog = showSlotSelectionDialog,
    )
    val actions = InstallScreenActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onSelectMethod = { method ->
            when {
                // The archive row carries no uri until the user picks one, so selecting it
                // opens the picker; the result then enters the slot and confirmation steps.
                method.isKernelArchive -> {
                    installMethod = method
                    selectImageLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "application/*"
                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/octet-stream"))
                    })
                }

                else -> installMethod = method
            }
        },
        onDownloadFile = { downloadDialogShown = true },
        onSelectBootImage = { method ->
            // 在打开文件选择器之前，先设置 installMethod
            installMethod = method
            selectImageLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "application/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "application/zip"))
            })
        },
        onUploadLkm = {
            selectLkmLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "application/octet-stream" })
        },
        onClearLkm = { lkmSelection = LkmSelection.KmiNone },
        onSelectPartition = { index ->
            hasCustomSelected = true
            val method = installMethod
            if (method is InstallMethod.DownloadFile) {
                remotePartitionSelectionIndex = index
                installMethod = method.copy(partition = remotePartitions.getOrNull(index))
            } else {
                partitionSelectionIndex = index
            }
        },
        onNext = {
            val isLkmSelected = lkmSelection != LkmSelection.KmiNone
            val isKmiUnknown = currentKmi.isBlank()
            val isKmiUnresolved = when (installMethod) {
                // The download flow extracts the KMI itself; no manual
                // selection needed.
                is InstallMethod.DownloadFile -> false
                is InstallMethod.SelectFile -> true
                else -> isKmiUnknown
            }
            if (isGkiDevice && !isLkmSelected && isKmiUnresolved && !installMethod.isKernelArchive) {
                showChooseKmiDialog.value = true
            } else {
                onInstall()
            }
        },
        onAdvancedOptionsClicked = {
            advancedOptionsShown = !advancedOptionsShown
        },
        onSelectAllowShell = {
            allowShell = it
        },
        onSelectEnableAdb = {
            enableAdb = it
        },
        onSelectForceBackup = {
            forceBackup = it
        },
        onSpoofReleaseChange = {
            spoofRelease = it
            scope.launch {
                SuSFSManager.saveUnameValue(it.trim().ifBlank { "default" })
            }
        },
        onSpoofVersionChange = {
            spoofVersion = it
            scope.launch {
                SuSFSManager.saveBuildTimeValue(it.trim().ifBlank { "default" })
            }
        },
        onHorizonKernelSelected = { method ->
            anyKernel3State.onHorizonKernelSelected(method)
        },
        onReopenSlotDialog = { method ->
            anyKernel3State.onReopenSlotDialog(method)
        }
    )

    InstallScreenFolk(state, actions, snackbarHost)
}
