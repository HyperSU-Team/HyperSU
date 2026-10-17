package com.sukisu.ultra.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.sukisu.ultra.data.model.Author
import com.sukisu.ultra.data.model.RepoModule
import com.sukisu.ultra.data.registry.RegistryUrls
import com.sukisu.ultra.ksuApp
import com.sukisu.ultra.ui.util.isNetworkAvailable
import okhttp3.Request
import org.json.JSONObject

/**
 * The module list, read from the registry's versioned API.
 *
 * The registry used to serve a flat array of modules at the site root. That document is gone -
 * it answers 404 now - and the list has moved behind an index that names the current list
 * document, with each entry carrying a `detail` path instead of the release fields inline. Both
 * the path and the field names changed, so the parsing below is written against the new document
 * rather than adapted from the old one.
 */
class ModuleRepoRepositoryImpl : ModuleRepoRepository {

    override suspend fun fetchModules(): Result<List<RepoModule>> = withContext(Dispatchers.IO) {
        runCatching {
            if (!isNetworkAvailable(ksuApp)) {
                throw Exception("Network unavailable")
            }

            val modulesPath = fetchModulesPath()
            val body = fetchText(RegistryUrls.resolve(modulesPath))
            val json = JSONObject(body)
            val modules = json.optJSONArray("modules")
                ?: throw Exception("Registry document has no modules")

            (0 until modules.length()).mapNotNull { idx ->
                val item = modules.optJSONObject(idx) ?: return@mapNotNull null
                parseRepoModule(item)
            }
        }
    }

    /**
     * The path of the list document, as named by the index.
     *
     * The index is a tiny document whose only job is to say where the list is, which is what lets
     * the registry change the list's location without breaking older clients. When it cannot be
     * read the known path is used instead, because a stale-but-present list is better than an
     * error screen.
     */
    private fun fetchModulesPath(): String = runCatching {
        val json = JSONObject(fetchText(RegistryUrls.resolve(RegistryUrls.INDEX_PATH)))
        json.optString("modules").takeIf { it.isNotBlank() } ?: RegistryUrls.FALLBACK_MODULES_PATH
    }.getOrDefault(RegistryUrls.FALLBACK_MODULES_PATH)

    private fun fetchText(url: String): String {
        val request = Request.Builder().url(url).build()
        ksuApp.okhttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Fetch failed: ${response.code} for $url")
            }
            return response.body?.string().orEmpty()
        }
    }

    private fun parseRepoModule(item: JSONObject): RepoModule? {
        val moduleId = item.optString("id", "").ifEmpty { item.optString("moduleId", "") }
        if (moduleId.isEmpty()) return null

        val moduleName = item.optString("name", "").ifEmpty { item.optString("moduleName", "") }

        val authorList = item.optJSONArray("authors")?.let { authors ->
            (0 until authors.length()).mapNotNull { idx ->
                val author = authors.optJSONObject(idx) ?: return@mapNotNull null
                val name = author.optString("name", "").trim()
                if (name.isEmpty()) null else Author(name = name, link = author.optString("url", "").trim())
            }
        }.orEmpty()
        val authors = authorList.joinToString(", ") { it.name }

        // The release fields moved into a `stable` object, and the download URL is no longer
        // inline: the list carries the version, and the zip is described by the detail document.
        val stable = item.optJSONObject("stable")
        val latestRelease = stable?.optString("version", "").orEmpty()
        val latestReleaseTime = stable?.optString("publishedAt", "").orEmpty()
        val latestVersionCode = stable?.optLong("versionCode", 0L) ?: 0L

        val tags = item.optJSONArray("tags")?.let { array ->
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        }.orEmpty()

        return RepoModule(
            moduleId = moduleId,
            moduleName = moduleName.ifEmpty { moduleId },
            authors = authors,
            authorList = authorList,
            summary = item.optString("summary", ""),
            metamodule = item.optBoolean("metamodule", false),
            // The old document had an explicit `zygisk` flag; the new one exposes it as a tag.
            zygisk = item.optBoolean("zygisk", false) || tags.any { it.equals("zygisk", true) },
            stargazerCount = item.optInt("stars", item.optInt("stargazerCount", 0)),
            updatedAt = item.optString("updatedAt", ""),
            createdAt = item.optString("createdAt", ""),
            latestRelease = latestRelease,
            latestReleaseTime = latestReleaseTime,
            latestVersionCode = latestVersionCode,
            // Filled in from the detail document when the user opens the module: the list document
            // no longer carries a download URL.
            latestAsset = null,
        )
    }
}
