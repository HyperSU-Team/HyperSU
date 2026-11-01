package com.sukisu.ultra.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

private const val TAG = "ApkInstaller"

const val APK_MIME_TYPE = "application/vnd.android.package-archive"

/**
 * Hand a downloaded APK to the system package installer.
 *
 * The file comes back as a MediaStore uri, so the installer needs the read
 * grant; without it the activity start throws and nothing appears to happen.
 */
fun installApk(context: Context, uri: Uri): Boolean {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, APK_MIME_TYPE)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching {
        context.startActivity(intent)
        true
    }.getOrElse {
        Log.w(TAG, "Unable to start the package installer for $uri", it)
        false
    }
}
