package com.sukisu.ultra.ui.screen.module

/**
 * Set when something outside the module screen changed what is installed on disk, so the list
 * reloads on the next resume instead of showing the state from before the install.
 *
 * The flash screen sets it; the module screen consumes it once, in its resume effect.
 */
object ModuleListRefresh {
    @Volatile
    private var pending = false

    fun request() {
        pending = true
    }

    /** True once per request. */
    fun consume(): Boolean {
        if (!pending) return false
        pending = false
        return true
    }
}
