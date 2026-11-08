package com.sukisu.ultra.ui.screen.dynamic

import androidx.compose.ui.graphics.ImageBitmap

/** Signature slot the kernel reserves for the manager picked on this screen. */
const val DYNAMIC_MANAGER_SIGNATURE_INDEX = 255

/** Slots below this one are the signatures the kernel was built with. */
private const val FIRST_BUILTIN_INDEX = 255

data class DynamicManagerApp(
    val label: String,
    val packageName: String,
    val uid: Int,
    val apkPath: String,
    val icon: ImageBitmap?,
    val signatureIndex: Int?,
) {
    val isDynamic: Boolean
        get() = signatureIndex == DYNAMIC_MANAGER_SIGNATURE_INDEX

    val isBuiltIn: Boolean
        get() = signatureIndex != null && signatureIndex < FIRST_BUILTIN_INDEX

    val isAuthorized: Boolean
        get() = signatureIndex != null
}

data class DynamicManagerState(
    val size: Int = 0,
    val hash: String = "",
    val apps: List<DynamicManagerApp> = emptyList(),
    val loading: Boolean = true,
    val submitting: Boolean = false,
) {
    val isSet: Boolean
        get() = size > 0 && hash.length == 64

    val hashSummary: String
        get() = if (hash.length > 24) hash.substring(0, 24) + "…" else hash
}

data class DynamicManagerActions(
    val onBack: () -> Unit,
    val onSelectApp: (DynamicManagerApp) -> Unit,
    val onClear: () -> Unit,
)
