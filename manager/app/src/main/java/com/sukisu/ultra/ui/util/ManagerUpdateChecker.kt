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
 * @param apkUrls the asset and its mirrors, in the order they should be tried. Empty when the
 *                release carries no APK.
 * @param pageUrl the release page, used when there is nothing to download.
 */
data class ManagerUpdate(
    val versionName: String,
    /**
     * Every URL that serves the same APK, in the order they should be tried: the release asset
     * first, then mirrors of it. The asset host is a different host from the API one and is not
     * reachable everywhere the API is - a `timeout` there is common enough that a single URL is
     * not a working update path.
     */
    val apkUrls: List<String>,
    val pageUrl: String,
    val notes: String,
)

/**
 * Asks GitHub whether a newer manager has been released.
 *
 * The releases API is used rather than the repository's tags because a release is the thing that
 * carries an APK, and being able to install it is the point.
 *
 * Deciding what "newer" means is the whole difficulty here, because this build's version name is
 * not a release version: `manager/build.gradle.kts` takes it from `git describe --tags --always`,
 * which yields a tag only when the build sits exactly on one and a bare commit hash otherwise. So
 * the version name cannot be compared against a release tag - a build from `main` reports
 * `f7955fb53`, which matches no semantic version at all, and every release then looked "not
 * newer", which is why the check never found anything.
 *
 * The comparison therefore uses the version code, which is built from the commit count and is
 * always numeric, and falls back to the semantic tag only when the local name really is one.
 */
object ManagerUpdateChecker {

    private const val TAG = "ManagerUpdateChecker"

    /** The project this build comes from. */
    private const val RELEASES_URL =
        "https://api.github.com/repos/HyperSU-Team/HyperSU/releases/latest"

    private val NUMERIC_TAG = Regex("""^v?(\d+)$""")
    private val SEMANTIC_TAG = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""")

    /**
     * The version code embedded in an APK asset name, e.g. `HyperSU_v4.2.0_37360-release.apk`.
     *
     * The release tag alone cannot decide this: `v4.2.0` is a marketing version, and the same tag
     * is reused for every build cut from that line, so two APKs can carry it while only one of
     * them is newer. The build number in the asset name is the value the version code is derived
     * from, which makes it directly comparable.
     */
    private val ASSET_BUILD_NUMBER = Regex("""_(\d{4,})""")

    /**
     * @return the update when the newest release is newer than this build, null when it is not or
     *         the versions cannot be compared, and a failed [Result] when GitHub could not be asked.
     */
    suspend fun check(): Result<ManagerUpdate?> = withContext(Dispatchers.IO) {
        // The block yields the update or null, never a Result: wrapping one here nests it and
        // gives Result<Result<ManagerUpdate>>, which is not what the signature promises.
        runCatching {
            val request = Request.Builder()
                .url(RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .build()

            ksuApp.okhttpClient.newCall(request).execute().use { response ->
                // 404 is the expected answer for a project with no releases yet, not an error.
                if (response.code == 404) return@runCatching null
                if (!response.isSuccessful) error("GitHub returned ${response.code}")

                val json = JSONObject(response.body?.string().orEmpty())
                val tag = json.optString("tag_name").trim()
                if (tag.isEmpty()) return@runCatching null

                val assets = assetsOf(json)
                if (!isNewer(tag, assets)) return@runCatching null

                ManagerUpdate(
                    versionName = tag.removePrefix("v"),
                    apkUrls = downloadableUrls(apkAssetOf(assets)),
                    pageUrl = json.optString("html_url"),
                    // `optString` turns a JSON null into the four-character string "null", which
                    // is what a release with no body written yet actually contains - and the
                    // dialog then displayed "null" as the release notes. `opt` returns the null
                    // itself, so a missing body reads as empty.
                    notes = (json.opt("body") as? String).orEmpty().trim(),
                )
            }
        }.onFailure { Log.w(TAG, "update check failed", it) }
    }

    /** The release's asset objects, or an empty list when it carries none. */
    private fun assetsOf(release: JSONObject): List<JSONObject> {
        val array = release.optJSONArray("assets") ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    /**
     * Whether [tag] (with [assets]) names a version later than this build.
     *
     * The build number in an APK asset name is preferred because it is the same number the local
     * version code is derived from, so the two compare directly. Only when no asset carries one is
     * the tag itself used, and then only if this build's own name is a comparable version.
     */
    private fun isNewer(tag: String, assets: List<JSONObject>): Boolean {
        val remoteCode = assets.firstNotNullOfOrNull { asset ->
            ASSET_BUILD_NUMBER.find(asset.optString("name"))?.groupValues?.get(1)?.toLongOrNull()
        }
        if (remoteCode != null) {
            return remoteCode > BuildConfig.VERSION_CODE
        }

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

    /**
     * The APK asset this build should install.
     *
     * A release carries both the normal APK and a `-spoofed` variant, and the spoofed one sorts
     * first ("release-spoofed" < "release."), so taking the first `.apk` handed the updater the
     * spoofed build. Prefer the plain `-release.apk` name and never fall back to a spoofed asset.
     */
    private fun apkAssetOf(assets: List<JSONObject>): String? {
        val apks = assets
            .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
            .filterNot { it.optString("name").contains("spoofed", ignoreCase = true) }
        val preferred = apks.firstOrNull { it.optString("name").endsWith("-release.apk", ignoreCase = true) }
            ?: apks.firstOrNull()
            ?: return null
        return preferred.optString("browser_download_url").takeIf { it.isNotEmpty() }
    }

    /**
     * Public mirrors of a GitHub release asset.
     *
     * `api.github.com` answering while the release-asset host times out is the normal failure on
     * networks that only partly reach GitHub, so a mirror is tried when the direct URL fails.
     */
    private val MIRROR_PREFIXES = listOf(
        "https://ghfast.top/",
        "https://gh-proxy.com/",
        "https://ghproxy.net/",
    )

    private fun downloadableUrls(assetUrl: String?): List<String> {
        if (assetUrl.isNullOrEmpty()) return emptyList()
        return listOf(assetUrl) + MIRROR_PREFIXES.map { prefix -> prefix + assetUrl }
    }
}
