package com.sukisu.ultra.ui.screen.themes

import android.content.Context
import android.net.Uri
import android.util.Log
import com.sukisu.ultra.ksuApp
import kotlinx.coroutines.Dispatchers
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
    private const val PER_PAGE = 30
    private const val CACHE_TTL_MS = 10 * 60 * 1000L

    // The shared client's 10s connect timeout is short for a mobile connection and a 100-release
    // response; the catalogue request gets a little more room before the screen declares failure.
    private val client by lazy {
        ksuApp.okhttpClient.newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .build()
    }

    private val MARKDOWN_IMAGE = Regex("""!\[[^\]]*]\((https?://[^)]+)\)""")

    private fun cacheFile(context: Context) = File(context.cacheDir, "theme_store/catalog.json")

    suspend fun fetchThemes(context: Context, force: Boolean = false): List<StoreTheme> =
        withContext(Dispatchers.IO) {
            val cache = cacheFile(context)
            if (!force && cache.isFile && System.currentTimeMillis() - cache.lastModified() < CACHE_TTL_MS) {
                readCache(cache)?.let { return@withContext it }
            }

            val fresh = runCatching { fetchPage(1) }
                .onFailure { Log.w(TAG, "could not load the theme catalogue", it) }
                .getOrNull()

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
                    name = release.optString("name").ifBlank { release.optString("tag_name") },
                    previewUrl = MARKDOWN_IMAGE.find(release.optString("body"))
                        ?.groupValues?.get(1),
                    downloadUrl = asset.optString("browser_download_url"),
                    sizeBytes = asset.optLong("size"),
                    downloadCount = asset.optInt("download_count"),
                )
            }
        }
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

        runCatching {
            val request = Request.Builder().url(theme.downloadUrl).build()
            ksuApp.okhttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("empty response")
                val total = body.contentLength()
                var written = 0L
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
            if (target.exists()) target.delete()
            if (!partial.renameTo(target)) error("could not store the archive")
            target
        }.onFailure {
            Log.w(TAG, "theme download failed: ${theme.downloadUrl}", it)
            partial.delete()
        }.getOrNull()
    }

    fun localUri(file: File): Uri = Uri.fromFile(file)

    private fun String.safeFileName(): String =
        replace(Regex("""[^A-Za-z0-9._-]"""), "_").take(80)
}
