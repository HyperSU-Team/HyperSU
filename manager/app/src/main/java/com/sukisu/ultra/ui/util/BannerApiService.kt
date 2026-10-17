package com.sukisu.ultra.ui.util

import android.content.Context
import android.util.Log
import com.sukisu.ultra.ksuApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * 横幅的 API 模式服务，移植自 FolkPatch `ui/screen/misc/BannerApiService.kt`。
 *
 * 图片源可以是一个随机图 API 的 URL，也可以是以 `/` 开头的本地目录。两种都用模块 ID 做
 * 种子，保证**同一模块每次拿到同一张图**（否则每次刷新列表都会换图）。
 *
 * 结果写进 `filesDir/api_banners/<sourceHash>_<moduleId>`，文件名带上源的 hash，切换 API
 * 源不会读到上一个源的缓存；缓存 24 小时过期。
 */
object BannerApiService {
    private const val TAG = "BannerApiService"
    private const val API_BANNER_DIR_NAME = "api_banners"
    private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    /** 取模块横幅；任何一步失败都返回 null，调用方据此回落到模块自带的横幅。 */
    suspend fun getModuleBanner(
        context: Context,
        moduleId: String,
        source: String,
    ): ByteArray? {
        if (source.isBlank()) {
            Log.w(TAG, "API source is empty")
            return null
        }

        val trimmedSource = source.trim()
        val sourceHash = getSourceHash(trimmedSource)

        return try {
            val cachedBanner = getCachedBanner(context, moduleId, sourceHash)
            if (cachedBanner != null) return cachedBanner

            val bannerData = if (isLocalDirectory(trimmedSource)) {
                getFromLocalDirectory(context, moduleId, trimmedSource)
            } else {
                getFromApi(moduleId, trimmedSource)
            }

            bannerData?.let { cacheBanner(context, moduleId, sourceHash, it) }
            bannerData
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get banner for module $moduleId: ${e.message}", e)
            null
        }
    }

    /** 每个 API 源一个缓存命名空间。 */
    private fun getSourceHash(source: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(source.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    private fun isLocalDirectory(source: String): Boolean =
        source.startsWith("/") || source.startsWith("file://")

    private suspend fun getFromLocalDirectory(
        context: Context,
        moduleId: String,
        directoryPath: String,
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val dir = File(directoryPath.removePrefix("file://"))
            if (!dir.exists() || !dir.isDirectory) {
                Log.w(TAG, "Directory does not exist or is not a directory: $dir")
                return@withContext null
            }

            val imageFiles = dir.listFiles()
                ?.filter { it.isFile && it.extension.lowercase(Locale.ROOT) in IMAGE_EXTENSIONS }
                ?.sortedBy { it.name }
                ?: emptyList()

            if (imageFiles.isEmpty()) {
                Log.w(TAG, "No image files found in directory: $dir")
                return@withContext null
            }

            // 模块 ID 的哈希决定索引，同一模块固定拿同一张。
            imageFiles[getIndexFromModuleId(moduleId, imageFiles.size)].readBytes()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load image from local directory: ${e.message}", e)
            null
        }
    }

    private suspend fun getFromApi(moduleId: String, apiUrl: String): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val urlWithSeed = buildUrlWithSeed(apiUrl, moduleId)
                val response = ksuApp.okhttpClient
                    .newCall(Request.Builder().url(urlWithSeed).build())
                    .execute()

                if (!response.isSuccessful) {
                    Log.w(TAG, "API request failed with code: ${response.code}")
                    return@withContext null
                }
                response.body?.bytes()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch banner from API: ${e.message}", e)
                null
            }
        }

    /** 已带查询参数就用 `&` 追加，否则用 `?`。 */
    private fun buildUrlWithSeed(baseUrl: String, moduleId: String): String {
        val seed = getSeedFromModuleId(moduleId)
        return if (baseUrl.contains("?")) "$baseUrl&seed=$seed" else "$baseUrl?seed=$seed"
    }

    private fun getIndexFromModuleId(moduleId: String, listSize: Int): Int =
        kotlin.math.abs(moduleId.hashCode()) % listSize

    private fun getSeedFromModuleId(moduleId: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(moduleId.toByteArray())
        return digest.take(4).joinToString("") { "%02x".format(it) }
    }

    private fun getApiBannerDir(context: Context): File {
        val dir = File(context.filesDir, API_BANNER_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getCachedBannerFile(context: Context, moduleId: String, sourceHash: String): File =
        File(getApiBannerDir(context), "${sourceHash}_${sanitizeModuleId(moduleId)}")

    private suspend fun getCachedBanner(
        context: Context,
        moduleId: String,
        sourceHash: String,
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val file = getCachedBannerFile(context, moduleId, sourceHash)
            if (!file.exists()) return@withContext null
            if (System.currentTimeMillis() - file.lastModified() > CACHE_TTL_MS) {
                file.delete()
                return@withContext null
            }
            file.readBytes()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read cached banner: ${e.message}", e)
            null
        }
    }

    private suspend fun cacheBanner(
        context: Context,
        moduleId: String,
        sourceHash: String,
        data: ByteArray,
    ) = withContext(Dispatchers.IO) {
        runCatching {
            getCachedBannerFile(context, moduleId, sourceHash).writeBytes(data)
        }.onFailure {
            Log.e(TAG, "Failed to cache banner: ${it.message}", it)
        }
    }

    suspend fun clearAllCache(context: Context) = withContext(Dispatchers.IO) {
        runCatching {
            getApiBannerDir(context).listFiles()?.forEach { it.delete() }
            Log.d(TAG, "Cleared all API banner cache")
        }.onFailure {
            Log.e(TAG, "Failed to clear cache: ${it.message}", it)
        }
    }

    suspend fun clearModuleCache(context: Context, moduleId: String) = withContext(Dispatchers.IO) {
        runCatching {
            val sanitizedId = sanitizeModuleId(moduleId)
            getApiBannerDir(context).listFiles()
                ?.filter { it.name.endsWith("_$sanitizedId") }
                ?.forEach { it.delete() }
        }.onFailure {
            Log.e(TAG, "Failed to clear module cache: ${it.message}", it)
        }
    }

    private fun sanitizeModuleId(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9._-]"), "_")
}
