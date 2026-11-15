package com.sukisu.ultra.ui.screen.module

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * The metadata a module zip declares in its `module.prop`.
 *
 * Only the four fields the confirmation dialog shows are read. A missing or unparsable
 * `module.prop` yields a [ModuleZipInfo] with blank fields rather than an error, so the
 * dialog can still describe the file it is about to flash.
 */
data class ModuleZipInfo(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    /** True when the archive really carried a `module.prop`. */
    val hasModuleProp: Boolean,
) {
    /** Falls back to the file name when the archive does not name itself. */
    fun displayName(fallback: String): String = name.ifBlank { fallback }
}

/**
 * Reads `module.prop` out of a module archive.
 *
 * The entry may sit at the root or under a single wrapping directory, so a name that merely
 * ends in `module.prop` is accepted too. The archive is streamed, so a large zip is not held
 * in memory; reading stops at the first match.
 */
object ModuleZipReader {

    private const val TAG = "ModuleZipReader"
    private const val PROP_ENTRY = "module.prop"
    private const val MAX_PROP_BYTES = 64 * 1024

    suspend fun read(context: Context, uri: Uri): ModuleZipInfo? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val name = entry.name
                        val isProp = name == PROP_ENTRY || name.endsWith("/$PROP_ENTRY")
                        if (isProp) {
                            return@withContext parse(readProp(zip))
                        }
                        zip.closeEntry()
                    }
                }
            }
            null
        }.getOrElse {
            Log.w(TAG, "Failed to read module.prop from $uri: ${it.message}")
            null
        }
    }

    /**
     * Reads the entry, capped so a malformed archive cannot stream unbounded data.
     *
     * A `module.prop` is a few hundred bytes; the cap only exists to bound the damage from a
     * zip that declares a huge entry.
     */
    internal fun readProp(input: InputStream): String {
        val buffer = ByteArray(4096)
        val out = ByteArrayOutputStream()
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) throw IOException("Stalled module.prop read")
            if (read > MAX_PROP_BYTES - out.size()) throw IOException("module.prop exceeds size limit")
            out.write(buffer, 0, read)
        }
        return out.toByteArray().toString(Charsets.UTF_8)
    }

    /**
     * Parses `key=value` lines.
     *
     * A byte-order mark is stripped: prop files edited on Windows often carry one, and it
     * would otherwise become part of the first key and hide `id`.
     */
    private fun parse(text: String): ModuleZipInfo {
        val values = mutableMapOf<String, String>()
        text.removePrefix("\uFEFF").lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val eq = line.indexOf('=')
            if (eq <= 0) return@forEach
            val key = line.substring(0, eq).trim()
            val value = line.substring(eq + 1).trim()
            values[key] = value
        }

        return ModuleZipInfo(
            id = values["id"].orEmpty(),
            name = values["name"].orEmpty(),
            version = values["version"].orEmpty(),
            author = values["author"].orEmpty(),
            description = values["description"].orEmpty(),
            hasModuleProp = values.isNotEmpty(),
        )
    }
}
