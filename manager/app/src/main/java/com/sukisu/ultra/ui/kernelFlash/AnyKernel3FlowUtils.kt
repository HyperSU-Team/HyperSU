package com.sukisu.ultra.ui.kernelFlash

import androidx.compose.runtime.Stable
import com.sukisu.ultra.ui.screen.install.InstallMethod

@Stable
data class AnyKernel3State(
    val showSlotSelectionDialog: Boolean,
    /**
     * True while the user is being asked to confirm the archive they picked.
     *
     * Picking a file used to open a patch dialog straight away, which asked a question the user
     * had not raised. This confirmation takes that place: it reports what is about to be
     * flashed and waits for an answer.
     */
    val showConfirmDialog: Boolean,
    /** Either archive row; the callbacks ignore anything that is not one. */
    val onHorizonKernelSelected: (InstallMethod) -> Unit,
    val onSlotSelected: (String) -> Unit,
    val onDismissSlotDialog: () -> Unit,
    val onConfirmFlash: () -> Unit,
    val onDismissConfirmDialog: () -> Unit,
    val onReopenSlotDialog: (InstallMethod) -> Unit
)
