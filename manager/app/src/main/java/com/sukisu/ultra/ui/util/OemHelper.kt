package com.sukisu.ultra.ui.util

import android.annotation.SuppressLint

@SuppressLint("PrivateApi")
internal fun getSystemProperty(key: String): String {
    return try {
        val props = Class.forName("android.os.SystemProperties")
        props.getMethod("get", String::class.java).invoke(null, key) as? String ?: ""
    } catch (_: Throwable) {
        ""
    }
}

// Read-only build properties: resolve them once instead of reflecting on every call.
private val isMiuiCached: Boolean by lazy { getSystemProperty("ro.miui.ui.version.name").isNotEmpty() }
private val isHyperOSCached: Boolean by lazy { getSystemProperty("ro.mi.os.version.name").isNotEmpty() }
private val isColorOSCached: Boolean by lazy {
    getSystemProperty("ro.build.version.oplus.api").isNotEmpty() || getSystemProperty("ro.vendor.oplus.market.name").isNotEmpty()
}

fun isMiui(): Boolean {
    return isMiuiCached
}

fun isHyperOS(): Boolean {
    return isHyperOSCached
}

fun isColorOS(): Boolean {
    return isColorOSCached
}
