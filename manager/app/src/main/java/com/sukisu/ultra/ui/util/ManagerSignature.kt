package com.sukisu.ultra.ui.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.security.MessageDigest

private const val TAG = "ManagerSignature"

/**
 * The SHA-256 of this build's signing certificate.
 *
 * Used to tell the user *why* the kernel does not recognise this manager. The kernel matches the
 * manager by certificate, not by package name - it reads the APK path, verifies the v2 signature
 * against a compiled-in size and digest, and only then crowns the app - so a manager signed with a
 * different key than the kernel module was built for is rejected no matter what else matches.
 * Reporting that difference is the difference between "reinstall and try again" and a fix.
 *
 * Returns null when the signature cannot be read, which is not itself a failure worth reporting.
 */
fun managerSignatureSha256(context: Context): String? = runCatching {
    val pm = context.packageManager
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
    }

    val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.signingInfo?.apkContentsSigners
    } else {
        @Suppress("DEPRECATION")
        info.signatures
    }

    val first = signatures?.firstOrNull() ?: return@runCatching null
    MessageDigest.getInstance("SHA-256")
        .digest(first.toByteArray())
        .joinToString("") { "%02x".format(it) }
}.onFailure { Log.w(TAG, "could not read the signing certificate", it) }.getOrNull()
