package com.sukisu.ultra.ui.util

import android.util.Log
import com.sukisu.ultra.BuildConfig
import com.sukisu.ultra.ksuApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * What a release offers, once it is known to be newer than this build.
 *
 * @param versionName the tag, with any leading `v` removed.
 * @param apkUrl the asset to install, or null when the release carries no APK.
 * @param pageUrl the release page, used when there is nothing to download.
 */
data class ManagerUpdate(
    val versionName: String,
    val apkUrl: String?,
    val pageUrl: String,
    val notes: String,
)

/**
 * Asks GitHub whether a newer manager has been released.
 *
 * The releases API is used rather than the repository's tags because a release is the thing that
 * carries an APK, and being able to install it is the point.
 *
 * Two tagging schemes are understood, because this build's version code comes from the commit
 * count rather than from its tag, so the two cannot be compared the same way:
 *
 * - a numeric tag (`v37304`) is compared as a version code, which is exact;
 * - a semantic one (`v4.2.0`) is compared against this build's version name, which is the tag it
 *   was built from, so that comparison is only meaningful for a build made from a tag.
 *
 * A tag fitting neither is skipped rather than guessed at: offering the wrong download is worse
 * than offering none.
 */
object ManagerUpdateChecker {

    private const val TAG = "ManagerUpdateChecker"

    /** The project this build comes from. */
    private const val RELEASES_URL =
        "https://api.github.com/repos/KirinNova/HyperSU/releases/latest"

    private val NUMERIC_TAG = Regex("""^v?(\d+)$""")
    private val SEMANTIC_TAG = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""")

    /**
     * @return the update when the newest release is newer than this build, null when it is not or
     *         the tags cannot be compared, and a failed [Result] when GitHub could not be asked.
     */
    suspend fun check(): Result<ManagerUpdate?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .build()

            val body = ksuApp.okhttpClient.newCall(request).execute().use { response ->
                // 404 is the expected answer for a project with no releases yet, not an error.
                if (response.code == 404) return@withContext Result.success(null)
                if (!response.isSuccessful) error("GitHub returned ${response.code}")
                response.body?.string().orEmpty()
            }

            val json = JSONObject(body)
            val tag = json.optString("tag_name").trim()
            if (!isNewer(tag)) return@withContext Result.success(null)

            Result.success(
                ManagerUpdate(
                    versionName = tag.removePrefix("v"),
                    apkUrl = apkAssetOf(json),
                    pageUrl = json.optString("html_url"),
                    notes = json.optString("body"),
                ),
            )
        }.onFailure { Log.w(TAG, "update check failed", it) }
    }

    /** Whether [tag] names a version later than this build. */
    private fun isNewer(tag: String): Boolean {
        NUMERIC_TAG.matchEntire(tag)?.groupValues?.get(1)?.toLongOrNull()?.let { code ->
            return code > BuildConfig.VERSION_CODE
        }
        val remote = SEMANTIC_TAG.matchEntire(tag)?.groupValues ?: return false
        val local = SEMANTIC_TAG.matchEntire(BuildConfig.VERSION_NAME)?.groupValues ?: return false
        for (i in 1..3) {
            val difference = remote[i].toInt() - local[i].toInt()
            if (difference != 0) return difference > 0
        }
        return false
    }

    /** The first `.apk` asset, if the release carries one. */
    private fun apkAssetOf(release: JSONObject): String? {
        val assets = release.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            if (name.endsWith(".apk", ignoreCase = true)) {
                return asset.optString("browser_download_url").takeIf { it.isNotEmpty() }
            }
        }
        return null
    }
}
