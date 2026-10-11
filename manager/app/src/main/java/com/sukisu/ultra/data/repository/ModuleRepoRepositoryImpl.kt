package com.sukisu.ultra.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.sukisu.ultra.data.model.Author
import com.sukisu.ultra.data.model.RepoModule
import com.sukisu.ultra.ksuApp
import com.sukisu.ultra.ui.util.isNetworkAvailable
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class ModuleRepoRepositoryImpl : ModuleRepoRepository {

    companion object {
        /**
         * The module index.
         *
         * The site was rebuilt and the old modules.json path now answers 404. The index lives on
         * the versioned API, as an object carrying a modules array rather than a bare array.
         */
        private const val MODULES_URL = "https://modules.kernelsu.org/api/v1/modules.json"

    }

    override suspend fun fetchModules(): Result<List<RepoModule>> = withContext(Dispatchers.IO) {
        runCatching {
            if (!isNetworkAvailable(ksuApp)) {
                throw Exception("Network unavailable")
            }

            val request = Request.Builder().url(MODULES_URL).build()
            ksuApp.okhttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("Fetch failed: " + response.code)
                }

                val body = response.body.string()
                val json = JSONObject(body).optJSONArray("modules") ?: JSONArray()
                (0 until json.length()).mapNotNull { idx ->
                    val item = json.optJSONObject(idx) ?: return@mapNotNull null
                    parseRepoModule(item)
                }
            }
        }
    }

    private fun parseRepoModule(item: JSONObject): RepoModule? {
        val moduleId = item.optString("id", "")
        if (moduleId.isEmpty()) return null

        val authorsArray = item.optJSONArray("authors")
        val authorList = if (authorsArray != null) {
            (0 until authorsArray.length()).mapNotNull { idx ->
                val authorObj = authorsArray.optJSONObject(idx) ?: return@mapNotNull null
                val name = authorObj.optString("name", "").trim()
                val link = authorObj.optString("url", "").trim()
                if (name.isEmpty()) null else Author(name = name, link = link)
            }
        } else {
            emptyList()
        }

        val tagsArray = item.optJSONArray("tags")
        val tags = if (tagsArray != null) {
            (0 until tagsArray.length()).map { tagsArray.optString(it, "") }
        } else {
            emptyList()
        }

        // The index carries the published version and its timestamp; the download URL only
        // exists in the per-module detail document, so the list itself stays without an asset.
        val stable = item.optJSONObject("stable")

        return RepoModule(
            moduleId = moduleId,
            moduleName = item.optString("name", ""),
            authors = authorList.joinToString(", ") { it.name },
            authorList = authorList,
            summary = item.optString("summary", ""),
            metamodule = item.optBoolean("metamodule", false),
            zygisk = tags.any { it.equals("zygisk", ignoreCase = true) },
            stargazerCount = item.optInt("stars", 0),
            updatedAt = item.optString("updatedAt", ""),
            createdAt = "",
            latestRelease = stable?.optString("version", "").orEmpty(),
            latestReleaseTime = stable?.optString("publishedAt", "").orEmpty(),
            latestVersionCode = stable?.optLong("versionCode", 0L) ?: 0L,
            latestAsset = null,
        )
    }
}
