package com.sukisu.ultra.ui.util

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CancellationException

/**
 * The urls a GitHub asset is tried from, best first.
 *
 * `github.com/.../releases/download/...` answers with a redirect to
 * `release-assets.githubusercontent.com`, and on a filtered network that host resolves to a
 * loopback address: the connect fails with `Failed to connect to
 * release-assets.githubusercontent.com/127.0.0.1:443`. The public acceleration hosts below proxy
 * the same file over a reachable domain, so a theme or a manager APK can still be fetched.
 *
 * The mirror that last worked is remembered and tried first: on a network where the direct host is
 * blocked, every download would otherwise spend a connect timeout on it before falling back.
 */
object GithubMirrors {

    private const val TAG = "GithubMirrors"

    /**
     * Public mirrors, in the order they are tried before the direct url on a blocked network.
     *
     * Every entry takes the full `https://github.com/...` url appended to it. The first two were
     * checked against a real release asset (an APK range request answered 206); `ghproxy.cc` was
     * removed after its certificate expired and every request failed the TLS handshake.
     */
    private val MIRROR_PREFIXES = listOf(
        "https://ghfast.top/",
        "https://ghm.078465.xyz/",
        "https://gh-proxy.com/",
        "https://ghproxy.net/",
    )

    /** The mirror that last succeeded, or null when none has been found yet. */
    @Volatile
    private var preferredPrefix: String? = null

    /**
     * Records which acceleration host served [usedUrl], so later downloads start there.
     */
    fun recordSuccess(usedUrl: String) {
        val prefix = MIRROR_PREFIXES.firstOrNull { usedUrl.startsWith(it) }
        if (prefix != null && prefix != preferredPrefix) {
            preferredPrefix = prefix
            Log.i(TAG, "using $prefix for GitHub assets")
        }
    }

    fun candidates(url: String): List<String> {
        if (!url.startsWith("http", ignoreCase = true) || !url.contains("github.com/")) {
            return listOf(url)
        }
        val preferred = preferredPrefix
        val mirrors = MIRROR_PREFIXES.map { prefix -> prefix + url }
        // Once a mirror is known to work it goes first: the direct host is the one that fails on a
        // filtered network, and waiting for its connect timeout on every download is what makes the
        // store feel broken.
        return if (preferred == null) {
            listOf(url) + mirrors
        } else {
            mirrors.sortedBy { if (it.startsWith(preferred)) 0 else 1 } + url
        }
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
            if (outcome.isSuccess) {
                recordSuccess(candidate)
                return result
            }
            lastError = outcome.exceptionOrNull()
            if (lastError is CancellationException) throw lastError
            Log.w(TAG, "attempt failed: $candidate", lastError)
        }
        Log.w(TAG, "download failed: $url", lastError)
        return null
    }
}
