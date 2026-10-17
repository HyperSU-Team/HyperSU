package com.sukisu.ultra.ui.util.module

import com.sukisu.ultra.data.registry.RegistryUrls
import com.sukisu.ultra.ksuApp
import com.sukisu.ultra.ui.util.isNetworkAvailable
import okhttp3.Request
import org.json.JSONObject

data class ModuleDetail(
    val readme: String,
    val readmeHtml: String,
    val latestTag: String,
    val latestTime: String,
    val latestAssetName: String?,
    val latestAssetUrl: String?,
    val releases: List<ReleaseInfo>,
    val homepageUrl: String,
    val sourceUrl: String,
    val url: String
)

data class ReleaseInfo(
    val name: String,
    val tagName: String,
    val publishedAt: String,
    val descriptionHTML: String,
    val assets: List<ReleaseAssetInfo>
)

data class ReleaseAssetInfo(
    val name: String,
    val downloadUrl: String,
    val size: Long,
    val downloadCount: Int
)

fun sanitizeVersionString(version: String): String {
    return version.replace(Regex("[^a-zA-Z0-9.\\-_]"), "_")
}

fun stripTicks(s: String): String {
    val t = s.trim()
    return if (t.startsWith("`") && t.endsWith("`") && t.length >= 2) t.substring(1, t.length - 1) else t
}

/**
 * The changelog of one release, as HTML.
 *
 * The release entry calls this field `changelogHtml` now; it used to be `descriptionHTML`. Both
 * spellings are read because the fallback below is what shows the update dialog's notes, and
 * getting nothing there silently drops the one thing that tells the user what changed.
 */
fun fetchReleaseDescriptionHtml(moduleId: String, latestTag: String): String? {
    if (!isNetworkAvailable(ksuApp)) return null
    val url = RegistryUrls.resolve("api/v1/modules/$moduleId.json")
    return runCatching {
        ksuApp.okhttpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val obj = JSONObject(resp.body?.string().orEmpty())
            val releasesArray = obj.optJSONArray("releases") ?: return@use null

            var fallbackHtml: String? = null
            for (i in 0 until releasesArray.length()) {
                val r = releasesArray.optJSONObject(i) ?: continue
                val descHtml = r.optString("changelogHtml", r.optString("descriptionHTML", ""))
                if (fallbackHtml == null && descHtml.isNotBlank()) {
                    fallbackHtml = descHtml
                }
                val tag = r.optString("tag", r.optString("name", r.optString("version", "")))
                val name = r.optString("name", tag)
                if ((tag == latestTag || name == latestTag) && descHtml.isNotBlank()) {
                    return@use descHtml
                }
            }
            fallbackHtml
        }
    }.getOrNull()
}

/**
 * The module's detail document.
 *
 * The registry's versioned API splits what used to be one document: the list carries the summary
 * fields, and this carries the readme, the release history and the zip to download. The zip moved
 * from a flat `downloadUrl` into a `zip` object on each release, and the readme is a path to
 * fetch rather than text inline - so [ModuleDetail.readme] is filled by [fetchModuleReadme] when
 * the screen needs it.
 */
fun fetchModuleDetail(moduleId: String): ModuleDetail? {
    if (!isNetworkAvailable(ksuApp)) return null
    val url = RegistryUrls.resolve("api/v1/modules/$moduleId.json")
    return runCatching {
        ksuApp.okhttpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val body = resp.body?.string().orEmpty()
            val obj = JSONObject(body)

            val sourceObj = obj.optJSONObject("source")
            val homepageUrl = obj.optString("homepage", "")
            val sourceUrl = sourceObj?.optString("url", "").orEmpty()

            val releasesArray = obj.optJSONArray("releases")
            val releases = if (releasesArray != null) {
                (0 until releasesArray.length()).mapNotNull { rIdx ->
                    val r = releasesArray.optJSONObject(rIdx) ?: return@mapNotNull null
                    val tag = r.optString("tag", r.optString("name", ""))
                    val zip = r.optJSONObject("zip")
                    val assets = if (zip != null) {
                        val zipUrl = zip.optString("url", "")
                        val zipName = zip.optString("name", "")
                        if (zipUrl.isEmpty()) emptyList()
                        else listOf(
                            ReleaseAssetInfo(
                                name = zipName.ifEmpty { zipUrl.substringAfterLast('/') },
                                downloadUrl = zipUrl,
                                size = zip.optLong("size", 0L),
                                downloadCount = 0,
                            )
                        )
                    } else {
                        emptyList()
                    }
                    ReleaseInfo(
                        name = r.optString("name", tag),
                        tagName = tag,
                        publishedAt = r.optString("publishedAt", ""),
                        descriptionHTML = r.optString("changelogHtml", ""),
                        assets = assets,
                    )
                }
            } else emptyList()

            // The newest release is the first one the document lists; the API orders them newest
            // first and marks prereleases, so a stable entry is preferred when there is one.
            val newest = releases.firstOrNull { !it.tagName.isEmpty() }
            val newestAsset = newest?.assets?.firstOrNull()

            return@use ModuleDetail(
                readme = "",
                readmeHtml = "",
                latestTag = newest?.tagName.orEmpty(),
                latestTime = newest?.publishedAt.orEmpty(),
                latestAssetName = newestAsset?.name,
                latestAssetUrl = newestAsset?.downloadUrl,
                releases = releases,
                homepageUrl = homepageUrl,
                sourceUrl = sourceUrl,
                url = url,
            )
        }
    }.getOrNull()
}

/**
 * The module's readme, fetched from the path the detail document names.
 *
 * The document carries `readme` as a registry path rather than the text, so it is a second request
 * and is made only when the detail screen needs it. Returns null when there is no readme.
 */
fun fetchModuleReadme(moduleId: String): String? {
    if (!isNetworkAvailable(ksuApp)) return null
    val detailUrl = RegistryUrls.resolve("api/v1/modules/$moduleId.json")
    return runCatching {
        val readmePath = ksuApp.okhttpClient.newCall(Request.Builder().url(detailUrl).build())
            .execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                JSONObject(resp.body?.string().orEmpty()).optString("readme", "")
            }
        if (readmePath.isBlank()) return@runCatching null

        ksuApp.okhttpClient.newCall(Request.Builder().url(RegistryUrls.resolve(readmePath)).build())
            .execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()
            }
    }.getOrNull()
}
