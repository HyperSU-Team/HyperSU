package com.sukisu.ultra.ui.kernelFlash

import android.net.Uri
import com.sukisu.ultra.ui.kernelFlash.state.HorizonKernelState

object KernelFlashStateHolder {
    // Written by the flash worker and read by the UI and by a recreated screen: plain fields there
    // are a data race, and the screen could read a stale state after a rotation.
    @Volatile var currentState: HorizonKernelState? = null
    @Volatile var currentUri: Uri? = null
    @Volatile var currentSlot: String? = null
    @Volatile var isFlashing = false

    fun clear() {
        currentState = null
        currentUri = null
        currentSlot = null
        isFlashing = false
    }
}