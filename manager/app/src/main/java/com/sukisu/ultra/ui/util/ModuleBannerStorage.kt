package com.sukisu.ultra.ui.util

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * 模块 Banner 图片的本地存储，移植自 FolkPatch `util/ModuleBannerStorage.kt`。
 *
 * 存放「用户自己给某个模块指定的横幅」，与 API 模式取到的图分开：前者是明确的个人选择，
 * 换 API 源、清缓存都不应该把它一起清掉。
 */
class ModuleBannerStorage(context: Context, private val dirName: String) {

    private companion object {
        /** A banner is a card decoration; the same cap as the API and module-directory sources. */
        const val MAX_BYTES = 12L * 1024 * 1024
    }

    private val dir: File = File(context.filesDir, dirName).apply {
        if (!exists()) mkdirs()
    }

    private fun sanitizeKey(raw: String): String = raw.replace(Regex("[^a-zA-Z0-9._-]"), "_")

    private fun getBannerFile(key: String): File = File(dir, sanitizeKey(key))

    fun read(key: String): ByteArray? = runCatching {
        val file = getBannerFile(key)
        if (file.exists()) file.inputStream().use { readBounded(it) } else null
    }.getOrNull()

    fun write(context: Context, key: String, uri: Uri): ByteArray? {
        val data = context.contentResolver.openInputStream(uri)?.use { readBounded(it) }
            ?: return null
        getBannerFile(key).outputStream().use { it.write(data) }
        return data
    }

    fun clear(key: String): Boolean {
        val file = getBannerFile(key)
        return !file.exists() || file.delete()
    }

    private fun readBounded(input: java.io.InputStream): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) throw java.io.IOException("stalled banner read")
            if (out.size().toLong() + read > MAX_BYTES) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray().takeIf { it.isNotEmpty() }
    }
}
