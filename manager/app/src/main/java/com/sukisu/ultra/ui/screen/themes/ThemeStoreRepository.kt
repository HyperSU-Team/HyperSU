package com.sukisu.ultra.ui.screen.themes

import android.content.Context
import android.net.Uri
import android.util.Log
import com.sukisu.ultra.ksuApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** One theme offered by the store: a release whose single asset is the `.fpt` archive. */
data class StoreTheme(
    val tag: String,
    val name: String,
    val previewUrl: String?,
    val downloadUrl: String,
    val sizeBytes: Long,
    val downloadCount: Int,
)

/**
 * The theme catalogue, read from the releases of the theme repository.
 *
 * A release is a theme: its `name` is the title, its one `.fpt` asset is the archive, and its body
 * carries the preview screenshot as a markdown image. That is the whole format - there is no index
 * file to keep in sync, and publishing a theme is publishing a release.
 *
 * The parsed list is cached on disk for a while: the GitHub call is the slow part of opening the
 * store, and the catalogue changes at most a few times a week. Opening the screen shows the last
 * list immediately and refreshes it in the background.
 */
object ThemeStoreRepository {

    private const val TAG = "ThemeStore"
    private const val RELEASES_URL = "https://api.github.com/repos/1542296180/FolkPatch-/releases"
    // One page holds a full catalogue's worth of releases, and the second page covers the older
    // half of the repository. The store used to read a single page of 30 and quietly hid every
    // theme beyond it.
    private const val PER_PAGE = 100
    private const val PAGES = 2
    private const val CACHE_TTL_MS = 10 * 60 * 1000L

    // The shared client's 10s connect timeout is short for a mobile connection and a 100-release
    // response; the catalogue request gets a little more room before the screen declares failure.
    private val client by lazy {
        ksuApp.okhttpClient.newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .build()
    }

    // Release bodies carry the screenshot in either form: most are markdown images, but several
    // authors paste the HTML `<img>` GitHub's editor produces. Only the markdown form was read, so
    // those releases showed a card with no cover at all.
    private val MARKDOWN_IMAGE = Regex("""!\[[^\]]*]\((https?://[^)\s]+)\)""")
    private val HTML_IMAGE = Regex("""<img[^>]*?\ssrc\s*=\s*["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)

    /** An image published as a release asset rather than embedded in the body. */
    private val PREVIEW_ASSET_EXTENSIONS = listOf(".png", ".jpg", ".jpeg", ".webp")

    /** Width requested from the resizing proxy: the card is 260dp tall on a 1080p phone. */
    private const val PREVIEW_WIDTH = 640

    /**
     * Public mirrors of a GitHub release asset.
     *
     * A theme is a 20MB archive and the release host is slow or unreachable on some networks; the
     * manager's own update already retries through these, and a theme download benefits the same
     * way.
     */
    private val MIRROR_PREFIXES = listOf(
        "https://ghfast.top/",
        "https://gh-proxy.com/",
        "https://ghproxy.net/",
    )

    /**
     * The urls a cover is tried from, best first.
     *
     * A release screenshot is a full phone capture - 1.3MB at 1440x3200 - and GitHub serves it at
     * a few hundred kilobytes a minute on a throttled connection, so a store page full of covers
     * took tens of seconds to fill in. The first entries go through a resizing proxy that returns
     * the same picture at the width the card actually draws, about 40KB, which is what makes the
     * list readable immediately; the direct url stays as the last resort so nothing breaks when
     * the proxy is unreachable.
     *
     * `wsrv.nl` wants the source without a scheme.
     */
    fun previewCandidates(url: String): List<String> {
        if (url.isBlank()) return emptyList()
        val bare = url.removePrefix("https://").removePrefix("http://")
        val resized = "https://wsrv.nl/?url=$bare&w=$PREVIEW_WIDTH&output=webp&we"
        return listOf(resized, url)
    }

    private fun cacheFile(context: Context) = File(context.cacheDir, "theme_store/catalog.json")

    suspend fun fetchThemes(context: Context, force: Boolean = false): List<StoreTheme> =
        withContext(Dispatchers.IO) {
            val cache = cacheFile(context)
            if (!force && cache.isFile && System.currentTimeMillis() - cache.lastModified() < CACHE_TTL_MS) {
                readCache(cache)?.let { return@withContext it }
            }

            // The pages are independent requests, so they run together instead of one after the
            // other; a page that fails only costs its own themes.
            val fresh = runCatching {
                coroutineScope {
                    (1..PAGES).map { page ->
                        async { runCatching { fetchPage(page) }.getOrDefault(emptyList()) }
                    }.awaitAll().flatten()
                }
            }
                .onFailure { Log.w(TAG, "could not load the theme catalogue", it) }
                .getOrNull()
                ?.distinctBy { it.tag }
                ?.takeIf { it.isNotEmpty() }

            if (fresh != null) {
                writeCache(cache, fresh)
                return@withContext fresh
            }

            // Any cached list beats an error screen, even a stale one; only when there is nothing
            // cached at all does the screen show its failure state.
            readCache(cache)?.let { return@withContext it }
            error("theme catalogue unavailable")
        }

    private fun readCache(file: File): List<StoreTheme>? = runCatching {
        val array = JSONArray(file.readText())
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            StoreTheme(
                tag = item.optString("tag"),
                name = item.optString("name"),
                previewUrl = item.optString("previewUrl").takeIf { it.isNotBlank() },
                downloadUrl = item.optString("downloadUrl"),
                sizeBytes = item.optLong("sizeBytes"),
                downloadCount = item.optInt("downloadCount"),
            )
        }
    }.getOrNull()

    private fun writeCache(file: File, themes: List<StoreTheme>) {
        runCatching {
            file.parentFile?.mkdirs()
            val array = JSONArray()
            themes.forEach { theme ->
                array.put(
                    JSONObject().apply {
                        put("tag", theme.tag)
                        put("name", theme.name)
                        put("previewUrl", theme.previewUrl.orEmpty())
                        put("downloadUrl", theme.downloadUrl)
                        put("sizeBytes", theme.sizeBytes)
                        put("downloadCount", theme.downloadCount)
                    },
                )
            }
            file.writeText(array.toString())
        }.onFailure { Log.w(TAG, "could not cache the theme catalogue", it) }
    }

    private fun fetchPage(page: Int): List<StoreTheme> {
        val request = Request.Builder()
            .url("$RELEASES_URL?per_page=$PER_PAGE&page=$page")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "HyperSU-Manager")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("GitHub returned ${response.code}")
            val array = JSONArray(response.body?.string().orEmpty())
            return (0 until array.length()).mapNotNull { index ->
                val release = array.optJSONObject(index) ?: return@mapNotNull null
                val asset = release.optJSONArray("assets")?.let { assets ->
                    (0 until assets.length())
                        .mapNotNull { assets.optJSONObject(it) }
                        .firstOrNull { it.optString("name").endsWith(".fpt", ignoreCase = true) }
                } ?: return@mapNotNull null

                StoreTheme(
                    tag = release.optString("tag_name").ifBlank { "release-$index" },
                    // A few authors name the release after the file, suffix and all; the store
                    // shows the theme's name, not its file name.
                    name = (release.optString("name").ifBlank { release.optString("tag_name") })
                        .trim()
                        .removeSuffix(".fpt")
                        .removeSuffix(".FPT"),
                    previewUrl = previewUrlOf(release),
                    downloadUrl = asset.optString("browser_download_url"),
                    sizeBytes = asset.optLong("size"),
                    downloadCount = asset.optInt("download_count"),
                )
            }
        }
    }

    /**
     * The cover image of a release, whichever way the author attached it.
     *
     * Body first - markdown image, then HTML `<img>` - because that is the picture the author saw
     * when publishing. When the body carries none, an image published as a release asset is used
     * instead of leaving the card blank.
     */
    private fun previewUrlOf(release: JSONObject): String? {
        val body = release.optString("body")
        MARKDOWN_IMAGE.find(body)?.groupValues?.get(1)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        HTML_IMAGE.find(body)?.groupValues?.get(1)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val assets = release.optJSONArray("assets") ?: return null
        return (0 until assets.length())
            .mapNotNull { assets.optJSONObject(it) }
            .firstOrNull { asset ->
                val name = asset.optString("name")
                PREVIEW_ASSET_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) }
            }
            ?.optString("browser_download_url")
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Downloads [theme] into the cache and returns the local archive.
     *
     * The file is a transfer target only; [ThemeLibrary] keeps the copy that survives the import.
     */
    suspend fun download(
        context: Context,
        theme: StoreTheme,
        onProgress: (Int) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "theme_store").apply { mkdirs() }
        val target = File(directory, "${theme.tag.safeFileName()}.fpt")
        val partial = File(directory, "${theme.tag.safeFileName()}.part")

        // Direct first, then the mirrors: whichever answers first wins, and progress restarts from
        // zero on a new attempt so the bar does not jump backwards mid-way.
        val candidates = listOf(theme.downloadUrl) +
            MIRROR_PREFIXES.map { prefix -> prefix + theme.downloadUrl }

        var lastError: Throwable? = null
        for (url in candidates) {
            val fetched = runCatching { fetch(url, partial, onProgress) }
                .onFailure { lastError = it }
                .isSuccess
            if (!fetched) {
                partial.delete()
                continue
            }

            // `renameTo` reports failure on some storage configurations even though the file is
            // there, and the failure surfaced as an IllegalStateException that crashed the app.
            // Copying is one extra pass over a file that is about to be read again anyway, and the
            // only thing it can fail on is real (no space, read-only storage), so it is used here.
            val stored = runCatching {
                target.delete()
                partial.copyTo(target, overwrite = true)
            }.onFailure { lastError = it }.isSuccess
            partial.delete()
            if (stored) return@withContext target
            break
        }

        Log.w(TAG, "theme download failed: ${theme.downloadUrl}", lastError)
        partial.delete()
        null
    }

    private fun fetch(url: String, partial: File, onProgress: (Int) -> Unit) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("empty response")
            val total = body.contentLength()
            var written = 0L
            onProgress(0)
            partial.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0L) {
                            onProgress(((written * 100L) / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
        }
    }

    fun localUri(file: File): Uri = Uri.fromFile(file)

    private fun String.safeFileName(): String =
        replace(Regex("""[^A-Za-z0-9._-]"""), "_").take(80)
}
