package com.sukisu.ultra.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.security.MessageDigest

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

/**
 * The SHA-256 of a downloaded file, as lowercase hex, or null when it cannot be read.
 *
 * Used before installing an update: the APK may have come from an acceleration mirror rather than
 * GitHub, and the release publishes the digest of the real asset.
 */
fun sha256Of(context: Context, uri: Uri): String? = runCatching {
    val digest = MessageDigest.getInstance("SHA-256")
    val input = context.contentResolver.openInputStream(uri) ?: return@runCatching null
    input.use { stream ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    digest.digest().joinToString("") { "%02x".format(it) }
}.onFailure { Log.w(TAG, "could not hash the downloaded apk", it) }.getOrNull()
