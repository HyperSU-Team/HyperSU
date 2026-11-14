package com.sukisu.ultra.ui.util

import android.net.Uri
import android.util.Log

/**
 * The urls a GitHub asset is tried from, best first.
 *
 * `github.com/.../releases/download/...` answers with a redirect to
 * `release-assets.githubusercontent.com`, and on a filtered network that host resolves to a
 * loopback address: the connect fails with `Failed to connect to
 * release-assets.githubusercontent.com/127.0.0.1:443`. Trying the public mirrors first turns that
 * into a working download instead of a failure, and the release host stays as the last attempt.
 */
object GithubMirrors {

    private const val TAG = "GithubMirrors"

    private val MIRROR_PREFIXES = listOf(
        "https://ghfast.top/",
        "https://gh-proxy.com/",
        "https://ghproxy.net/",
    )

    fun candidates(url: String): List<String> {
        if (!url.startsWith("http", ignoreCase = true) || !url.contains("github.com/")) {
            return listOf(url)
        }
        return listOf(url) + MIRROR_PREFIXES.map { prefix -> prefix + url }
    }

    /**
     * Downloads [url], falling back to the mirrors when the release host fails.
     *
     * Returns the downloaded uri, or null when every attempt failed - the caller shows its own
     * message instead of letting the exception kill the process, which is what a filtered network
     * used to do.
     */
    suspend fun downloadThroughMirrors(
        url: String,
        fileName: String,
        onDownloading: () -> Unit = {},
        onProgress: (Int) -> Unit = {},
    ): Uri? {
        var lastError: Throwable? = null
        for (candidate in candidates(url)) {
            var result: Uri? = null
            val outcome = runCatching {
                download(
                    url = candidate,
                    fileName = fileName,
                    onDownloaded = { uri -> result = uri },
                    onDownloading = onDownloading,
                    onProgress = onProgress,
                )
            }
            if (outcome.isSuccess) return result
            lastError = outcome.exceptionOrNull()
            Log.w(TAG, "attempt failed: $candidate", lastError)
        }
        Log.w(TAG, "download failed: $url", lastError)
        return null
    }
}
