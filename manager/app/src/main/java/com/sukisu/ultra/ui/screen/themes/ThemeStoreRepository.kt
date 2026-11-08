package com.sukisu.ultra.ui.screen.themes

import android.content.Context
import android.net.Uri
import android.util.Log
import com.sukisu.ultra.ksuApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.io.File

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
 */
object ThemeStoreRepository {

    private const val TAG = "ThemeStore"
    private const val RELEASES_URL = "https://api.github.com/repos/1542296180/FolkPatch-/releases"
    private const val PER_PAGE = 100
    private const val MAX_PAGES = 5

    private val MARKDOWN_IMAGE = Regex("""!\[[^\]]*]\((https?://[^)]+)\)""")

    suspend fun fetchThemes(): List<StoreTheme> = withContext(Dispatchers.IO) {
        val themes = mutableListOf<StoreTheme>()
        for (page in 1..MAX_PAGES) {
            val batch = fetchPage(page)
            themes += batch
            if (batch.size < PER_PAGE) break
        }
        themes
    }

    private fun fetchPage(page: Int): List<StoreTheme> {
        val request = Request.Builder()
            .url("$RELEASES_URL?per_page=$PER_PAGE&page=$page")
            .header("Accept", "application/vnd.github+json")
            .build()

        ksuApp.okhttpClient.newCall(request).execute().use { response ->
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
     * The store keeps one file per tag: a retry overwrites the half-finished attempt instead of
     * filling the cache with them, and the archive is removed again once it has been imported.
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
