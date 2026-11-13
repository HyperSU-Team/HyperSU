package com.sukisu.ultra.ui.navigation3

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A latch that keeps the navigation host from closing a screen whose work has to finish.
 *
 * Flashing a kernel archive or a boot image writes to the boot partition from a coroutine that
 * belongs to the screen doing it. A swipe in from the edge - or the system back gesture - popped
 * that screen and cancelled the coroutine halfway through the write, which the user could do by
 * accident while the progress page was up.
 *
 * The screens that run such a job raise this for as long as the job runs; the host then disables
 * every entry's edge-swipe dismissal and ignores back. The screens' own back buttons still call
 * the navigator directly, so a finished job is still dismissible in the normal way.
 */
object NavLock {

    var isBusy by mutableStateOf(false)
        private set

    fun acquire() {
        isBusy = true
    }

    fun release() {
        isBusy = false
    }
}
