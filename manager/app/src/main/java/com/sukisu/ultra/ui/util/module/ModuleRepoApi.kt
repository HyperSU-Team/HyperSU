package com.sukisu.ultra.ui.util.module

import com.sukisu.ultra.ksuApp
import com.sukisu.ultra.ui.util.isNetworkAvailable
import okhttp3.Request
import org.json.JSONArray
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

private const val MODULES_API = "https://modules.kernelsu.org/api/v1/modules"
private const val MODULES_SITE = "https://modules.kernelsu.org"

private fun fetchText(url: String): String? = runCatching {
    ksuApp.okhttpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
        if (resp.isSuccessful) resp.body.string() else null
    }
}.getOrNull()

fun fetchReleaseDescriptionHtml(moduleId: String, latestTag: String): String? {
    if (!isNetworkAvailable(ksuApp)) return null
    val url = MODULES_API + "/" + moduleId + ".json"
    return runCatching {
        ksuApp.okhttpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val obj = JSONObject(resp.body.string())
            val releases = obj.optJSONArray("releases") ?: return@use null
            var fallback: String? = null
            for (i in 0 until releases.length()) {
                val release = releases.optJSONObject(i) ?: continue
                val html = release.optString("changelogHtml", "")
                if (html.isBlank()) continue
                val tag = release.optString("tag", release.optString("version", ""))
                if (tag == latestTag) return@use html
                if (fallback == null) fallback = html
            }
            fallback
        }
    }.getOrNull()
}

fun fetchModuleDetail(moduleId: String): ModuleDetail? {
    if (!isNetworkAvailable(ksuApp)) return null
    val url = MODULES_API + "/" + moduleId + ".json"
    return runCatching {
        ksuApp.okhttpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val obj = JSONObject(resp.body.string())

            // The readme field is a path on the same site, and what it serves is already HTML.
            val readmePath = obj.optString("readme", "")
            val readmeHtml = if (readmePath.isEmpty()) {
                ""
            } else {
                fetchText(MODULES_SITE + "/" + readmePath).orEmpty()
            }

            val sourceObj = obj.optJSONObject("source")
            val stable = obj.optJSONObject("stable")

            var latestTag = stable?.optString("version", "").orEmpty()
            var latestTime = stable?.optString("publishedAt", "").orEmpty()
            var latestAssetName: String? = null
            var latestAssetUrl: String? = null

            val releasesArray = obj.optJSONArray("releases")
            val releases = if (releasesArray != null) {
                (0 until releasesArray.length()).mapNotNull { idx ->
                    val release = releasesArray.optJSONObject(idx) ?: return@mapNotNull null
                    val tag = release.optString("tag", release.optString("version", ""))
                    val zip = release.optJSONObject("zip")
                    val assets = if (zip != null) {
                        val name = zip.optString("name", "")
                        val download = stripTicks(zip.optString("url", ""))
                        if (name.isEmpty() || download.isEmpty()) {
                            emptyList()
                        } else {
                            listOf(ReleaseAssetInfo(name, download, zip.optLong("size", 0L), 0))
                        }
                    } else {
                        emptyList()
                    }
                    // The first non-prerelease release is the one the stable channel points at.
                    if (!release.optBoolean("prerelease", false) && latestAssetUrl == null) {
                        latestTag = tag
                        latestTime = release.optString("publishedAt", latestTime)
                        assets.firstOrNull()?.let { asset ->
                            latestAssetName = asset.name
                            latestAssetUrl = asset.downloadUrl
                        }
                    }
                    ReleaseInfo(
                        name = release.optString("name", tag),
                        tagName = tag,
                        publishedAt = release.optString("publishedAt", ""),
                        descriptionHTML = release.optString("changelogHtml", ""),
                        assets = assets,
                    )
                }
            } else {
                emptyList()
            }

            return@use ModuleDetail(
                readme = "",
                readmeHtml = readmeHtml,
                latestTag = latestTag,
                latestTime = latestTime,
                latestAssetName = latestAssetName,
                latestAssetUrl = latestAssetUrl,
                releases = releases,
                homepageUrl = stripTicks(obj.optString("homepage", "")),
                sourceUrl = stripTicks(sourceObj?.optString("url", "") ?: ""),
                url = MODULES_SITE + "/en/modules/" + moduleId + "/",
            )
        }
    }.getOrNull()
}
