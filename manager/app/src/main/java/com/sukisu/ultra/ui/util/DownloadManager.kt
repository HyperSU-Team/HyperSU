package com.sukisu.ultra.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object DownloadManager {

    enum class Status { PENDING, DOWNLOADING, COMPLETED, FAILED }

    data class DownloadState(
        val id: Int,
        val fileName: String,
        val url: String,
        val progress: Int = 0,
        val status: Status = Status.PENDING,
        val resultUri: Uri? = null,
        val error: String? = null,
    )

    private val idCounter = AtomicInteger(0)
    private val _downloads = MutableStateFlow<Map<Int, DownloadState>>(emptyMap())
    val downloads: StateFlow<Map<Int, DownloadState>> = _downloads.asStateFlow()

    private val completionCallbacks = ConcurrentHashMap<Int, MutableList<(Uri) -> Unit>>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun registerCallback(id: Int, callback: (Uri) -> Unit) {
        completionCallbacks.compute(id) { _, list ->
            (list ?: mutableListOf()).apply { add(callback) }
        }
    }

    @Synchronized
    fun enqueue(
        context: Context,
        url: String,
        fileName: String,
        onCompleted: ((Uri) -> Unit)? = null,
    ): Int {
        val existing = _downloads.value.values.find {
            it.url == url && (it.status == Status.PENDING || it.status == Status.DOWNLOADING)
        }
        if (existing != null) {
            // The same file is already on its way. The caller still needs its own completion
            // callback, otherwise a second request for one url (two screens, or a retry while the
            // first attempt is running) waits for a uri that is handed to the first caller only.
            if (onCompleted != null) registerCallback(existing.id, onCompleted)
            return existing.id
        }

        val id = idCounter.incrementAndGet()
        val state = DownloadState(id = id, fileName = fileName, url = url)
        _downloads.update { it + (id to state) }

        if (onCompleted != null) {
            registerCallback(id, onCompleted)
        }

        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_DOWNLOAD
            putExtra(DownloadService.EXTRA_DOWNLOAD_ID, id)
            putExtra(DownloadService.EXTRA_URL, url)
            putExtra(DownloadService.EXTRA_FILE_NAME, fileName)
        }
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            markFailed(id, e.message ?: "Unable to start download service")
            throw e
        }

        return id
    }

    internal fun updateProgress(id: Int, progress: Int) {
        _downloads.update { map ->
            val state = map[id] ?: return@update map
            map + (id to state.copy(progress = progress, status = Status.DOWNLOADING))
        }
    }

    @Synchronized
    internal fun markCompleted(id: Int, uri: Uri) {
        _downloads.update { map ->
            val state = map[id] ?: return@update map
            map + (id to state.copy(status = Status.COMPLETED, progress = 100, resultUri = uri))
        }

        val callbacks = completionCallbacks.remove(id)
        if (!callbacks.isNullOrEmpty()) {
            mainHandler.post { callbacks.forEach { callback -> callback(uri) } }
        }
    }

    @Synchronized
    internal fun markFailed(id: Int, error: String) {
        _downloads.update { map ->
            val state = map[id] ?: return@update map
            map + (id to state.copy(status = Status.FAILED, error = error))
        }
        completionCallbacks.remove(id)
    }

    /**
     * Forgets a download whose service never reported back.
     *
     * The entry is dropped instead of marked failed so a later request for the same url is not
     * rejected as "already downloading" by the reuse check.
     */
    internal fun abandon(id: Int) {
        _downloads.update { it - id }
        completionCallbacks.remove(id)
    }
}
