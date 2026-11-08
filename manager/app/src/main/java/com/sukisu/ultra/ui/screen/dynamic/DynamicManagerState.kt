package com.sukisu.ultra.ui.screen.dynamic

import androidx.compose.runtime.Immutable

/** An installed manager app: everything the kernel needs is its APK, the signature lives in it. */
@Immutable
data class DynamicManagerApp(
    val label: String,
    val packageName: String,
    val apkPath: String,
)

@Immutable
data class DynamicManagerState(
    val size: Int = 0,
    val hash: String = "",
    val apps: List<DynamicManagerApp> = emptyList(),
    val loading: Boolean = true,
    val submitting: Boolean = false,
) {
    val isSet: Boolean
        get() = size > 0 && hash.length == 64

    /** First bytes of the hash are enough to tell two signatures apart on screen. */
    val hashSummary: String
        get() = if (hash.length > 16) hash.substring(0, 16) + "…" else hash
}

@Immutable
data class DynamicManagerActions(
    val onBack: () -> Unit,
    val onSelectApp: (DynamicManagerApp) -> Unit,
    val onClear: () -> Unit,
)
