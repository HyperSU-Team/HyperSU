package com.sukisu.ultra.ui.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException

/**
 * Writes a small text file where the user can find it.
 *
 * A direct write to `/sdcard/Download` fails on Android 10 and later: scoped storage gives the
 * app no write access to the public collection without a MediaStore insert, and the failure
 * surfaced as an uncaught exception inside the save coroutine. Below Android 10 the public
 * directory needs the storage permission, so the app-specific external directory is used there
 * instead - it needs no permission and still appears under a browsable path.
 *
 * @return the path shown to the user, e.g. `Download/HyperSU_log.txt`.
 */
fun saveTextToDownloads(context: Context, fileName: String, content: String): String {
    val safeName = sanitizeDownloadName(fileName)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return saveViaMediaStore(context, safeName, content)
    }
    val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        ?: context.filesDir
    directory.mkdirs()
    val file = File(directory, safeName)
    file.writeText(content)
    return file.absolutePath
}

private fun sanitizeDownloadName(raw: String): String {
    val base = raw.substringAfterLast('/').substringAfterLast('\\')
    return base.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "_").trim().ifBlank { "log.txt" }
}

private fun saveViaMediaStore(context: Context, fileName: String, content: String): String {
    val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
        put(MediaStore.Downloads.MIME_TYPE, "text/plain")
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val uri = context.contentResolver.insert(collection, values)
        ?: throw IOException("Failed to create $fileName")
    try {
        val output = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("Failed to open $fileName")
        output.use { it.write(content.toByteArray(Charsets.UTF_8)) }
        val published = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
        if (context.contentResolver.update(uri, published, null, null) != 1) {
            throw IOException("Failed to publish $fileName")
        }
        return "${Environment.DIRECTORY_DOWNLOADS}/$fileName"
    } catch (t: Throwable) {
        runCatching { context.contentResolver.delete(uri, null, null) }
        throw t
    }
}
