package com.sukisu.ultra.ui.kernelFlash

import android.net.Uri
import com.sukisu.ultra.ui.kernelFlash.state.HorizonKernelState

object KernelFlashStateHolder {
    var currentState: HorizonKernelState? = null
    var currentUri: Uri? = null
    var currentSlot: String? = null
    var isFlashing = false

    fun clear() {
        currentState = null
        currentUri = null
        currentSlot = null
        isFlashing = false
    }
}