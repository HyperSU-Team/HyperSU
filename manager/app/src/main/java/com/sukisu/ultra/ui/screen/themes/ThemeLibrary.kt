package com.sukisu.ultra.ui.screen.themes

import android.content.Context
import android.net.Uri
import android.util.Log
import com.sukisu.ultra.ui.theme.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A theme that is already on the device and can be applied without downloading again. */
data class LocalTheme(
    val id: String,
    val fileName: String,
    val name: String,
    val author: String,
    val version: String,
    val sizeBytes: Long,
    val addedAt: Long,
)

/**
 * Themes kept on the device after an import.
 *
 * The store used to delete the archive once it had been imported, so a theme that had just been
 * downloaded and applied was gone and there was no way to apply it again without downloading it.
 * The archive now stays in `filesDir/themes`, with a small JSON index of what each file is, and
 * the store lists them under a second tab.
 */
object ThemeLibrary {

    private const val TAG = "ThemeLibrary"
    private const val DIR = "themes"
    private const val INDEX = "index.json"

    private fun directory(context: Context): File =
        File(context.filesDir, DIR).apply { mkdirs() }

    private fun indexFile(context: Context) = File(directory(context), INDEX)

    fun file(context: Context, theme: LocalTheme): File =
        File(directory(context), theme.fileName)

    suspend fun list(context: Context): List<LocalTheme> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = indexFile(context).takeIf { it.isFile }?.readText().orEmpty()
            if (raw.isBlank()) return@runCatching emptyList()
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val theme = LocalTheme(
                    id = item.optString("id"),
                    fileName = item.optString("fileName"),
                    name = item.optString("name"),
                    author = item.optString("author"),
                    version = item.optString("version"),
                    sizeBytes = item.optLong("sizeBytes"),
                    addedAt = item.optLong("addedAt"),
                )
                // A file the user deleted from storage should not linger in the index.
                theme.takeIf { file(context, it).isFile }
            }
        }.getOrElse {
            Log.w(TAG, "could not read the theme index", it)
            emptyList()
        }
    }

    /**
     * Copies [archive] into the library and records its metadata.
     *
     * A theme with the same name and version replaces the previous copy instead of piling up:
     * downloading an updated theme should not leave two entries that look identical.
     */
    suspend fun add(
        context: Context,
        archive: File,
        fallbackName: String,
    ): LocalTheme? = withContext(Dispatchers.IO) {
        runCatching {
            val metadata = ThemeManager.readThemeMetadata(context, Uri.fromFile(archive))
            val id = "${System.currentTimeMillis()}"
            val fileName = "theme_$id.fpt"
            val target = File(directory(context), fileName)
            archive.copyTo(target, overwrite = true)

            val existing = list(context).filter {
                it.name == (metadata?.name ?: fallbackName) &&
                    it.version == (metadata?.version ?: "")
            }
            existing.forEach { deleteInternal(context, it) }

            val theme = LocalTheme(
                id = id,
                fileName = fileName,
                name = metadata?.name ?: fallbackName,
                author = metadata?.author.orEmpty(),
                version = metadata?.version.orEmpty(),
                sizeBytes = target.length(),
                addedAt = System.currentTimeMillis(),
            )
            writeIndex(context, list(context) + theme)
            theme
        }.getOrElse {
            Log.w(TAG, "could not add a theme to the library", it)
            null
        }
    }

    suspend fun delete(context: Context, theme: LocalTheme) = withContext(Dispatchers.IO) {
        runCatching { deleteInternal(context, theme) }
            .onFailure { Log.w(TAG, "could not delete ${theme.fileName}", it) }
        Unit
    }

    private fun deleteInternal(context: Context, theme: LocalTheme) {
        file(context, theme).takeIf { it.exists() }?.delete()
        writeIndex(context, listFresh(context) - theme)
    }

    /** The index without filtering: used while rewriting it, where deletion is intended. */
    private fun listFresh(context: Context): List<LocalTheme> {
        val raw = indexFile(context).takeIf { it.isFile }?.readText().orEmpty()
        if (raw.isBlank()) return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            LocalTheme(
                id = item.optString("id"),
                fileName = item.optString("fileName"),
                name = item.optString("name"),
                author = item.optString("author"),
                version = item.optString("version"),
                sizeBytes = item.optLong("sizeBytes"),
                addedAt = item.optLong("addedAt"),
            )
        }
    }

    private fun writeIndex(context: Context, themes: List<LocalTheme>) {
        val array = JSONArray()
        themes.forEach { theme ->
            array.put(
                JSONObject().apply {
                    put("id", theme.id)
                    put("fileName", theme.fileName)
                    put("name", theme.name)
                    put("author", theme.author)
                    put("version", theme.version)
                    put("sizeBytes", theme.sizeBytes)
                    put("addedAt", theme.addedAt)
                },
            )
        }
        runCatching { indexFile(context).writeText(array.toString()) }
            .onFailure { Log.w(TAG, "could not write the theme index", it) }
    }
}
