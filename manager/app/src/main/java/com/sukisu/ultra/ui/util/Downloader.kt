package com.sukisu.ultra.ui.util

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import com.sukisu.ultra.ksuApp
import com.sukisu.ultra.ui.util.module.LatestVersionInfo
import okhttp3.Request

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
        onCompleted = onDownloaded,
    )

    DownloadManager.downloads
        .onEach { map -> map[downloadId]?.let { onProgress(it.progress) } }
        .first { map ->
            val status = map[downloadId]?.status
            status == DownloadManager.Status.COMPLETED ||
                status == DownloadManager.Status.FAILED
        }
}

internal suspend fun isDownloadAvailable(uri: Uri): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        ksuApp.contentResolver.openFileDescriptor(uri, "r").use { it != null }
    }.getOrDefault(false)
}

/**
 * The newest release of this fork.
 *
 * It has to be this fork's releases: comparing against upstream reported upstream's versionCode
 * as newer than ours, and tapping that banner offered a different app.
 */
fun checkNewVersion(): LatestVersionInfo {
    if (!isNetworkAvailable(ksuApp)) return LatestVersionInfo()
    val url = "https://api.github.com/repos/kafuzhi/HyperSU/releases/latest"
    val defaultValue = LatestVersionInfo()
    runCatching {
        ksuApp.okhttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                return defaultValue
            }
            val json = org.json.JSONObject(response.body.string())
            val changelog = json.optString("body")
            val assets = json.optJSONArray("assets") ?: return defaultValue
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                if (!name.endsWith(".apk")) {
                    continue
                }
                // The release asset is named HyperSU_v<version>_<versionCode>-release.apk.
                val versionCode = name.substringAfterLast('_').substringBefore('-').toLongOrNull()
                    ?: continue
                return LatestVersionInfo(
                    versionCode = versionCode,
                    downloadUrl = asset.optString("browser_download_url"),
                    changelog = changelog,
                )
            }
        }
    }
    return defaultValue
}
