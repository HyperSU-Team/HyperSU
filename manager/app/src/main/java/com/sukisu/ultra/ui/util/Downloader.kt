package com.sukisu.ultra.ui.util

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.sukisu.ultra.ksuApp

/** Generous: a slow release asset on mobile data still finishes, a dead service does not. */
private const val DOWNLOAD_TIMEOUT_MS = 15L * 60L * 1000L

/**
 * @author weishu
 * @date 2023/6/22.
 */
suspend fun download(
    url: String,
    fileName: String,
    onDownloaded: (Uri) -> Unit = {},
    onDownloading: () -> Unit = {},
    onProgress: (Int) -> Unit = {}
) {
    onDownloading()

    val downloadId = DownloadManager.enqueue(
        context = ksuApp,
        url = url,
        fileName = fileName,
    )

    val terminalState = withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) {
        DownloadManager.downloads
            .onEach { map -> map[downloadId]?.let { onProgress(it.progress) } }
            .first { map ->
                val status = map[downloadId]?.status
                status == DownloadManager.Status.COMPLETED ||
                    status == DownloadManager.Status.FAILED
            }
    } ?: run {
        // The service died (killed while backgrounded, or a crash): without a deadline the caller
        // waited for a status that would never arrive, and the screen stayed on "downloading".
        DownloadManager.abandon(downloadId)
        throw java.io.IOException("download timed out")
    }

    // A failed download used to return normally: the caller's runCatching never ran, so the user
    // saw a "download failed" notification and nothing in the app. Report it as an exception so
    // the caller can retry a mirror or explain what happened.
    val completed = terminalState.getValue(downloadId)
    if (completed.status == DownloadManager.Status.FAILED) {
        throw java.io.IOException(completed.error ?: "download failed")
    }
    val uri = completed.resultUri ?: throw java.io.IOException("Missing download URI")
    withContext(Dispatchers.Main) { onDownloaded(uri) }
}

internal suspend fun isDownloadAvailable(uri: Uri): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        ksuApp.contentResolver.openFileDescriptor(uri, "r").use { it != null }
    }.getOrDefault(false)
}
