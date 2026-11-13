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
    /**
     * The store release this copy came from, when it came from the store at all.
     *
     * The store uses this to know which of its cards are already on the device, so a theme that
     * was downloaded once offers "apply" instead of "download and apply". Matching that by name
     * would break on the releases whose title carries a `.fpt` suffix, so the tag is recorded
     * when the archive is installed.
     */
    val sourceTag: String? = null,
)

/**
 * Themes kept on the device after an import.
 *
 * The store used to delete the archive once it had been imported, so a theme that had just been
 * downloaded and applied was gone and there was no way to apply it again without downloading it.
 * The archive now stays in `filesDir/themes`, with a small JSON index of what each file is, and
 * the store lists them under a second tab.
 *
 * The listing also scans the directory rather than trusting the index alone: an import that
 * copied its archive but failed to record it - or a file put there by an older version - would
 * otherwise be invisible in the library.
 */
object ThemeLibrary {

    private const val TAG = "ThemeLibrary"
    private const val DIR = "themes"
    private const val INDEX = "index.json"

    private fun directory(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    private fun indexFile(context: Context) = File(directory(context), INDEX)

    fun file(context: Context, theme: LocalTheme): File = File(directory(context), theme.fileName)

    suspend fun list(context: Context): List<LocalTheme> = withContext(Dispatchers.IO) {
        scan(context)
    }

    /**
     * Copies [archive] into the library and records its metadata.
     *
     * A theme with the same name and version - or the same store release - replaces the previous
     * copy instead of piling up: downloading an updated theme should not leave two entries that
     * look identical.
     */
    suspend fun add(
        context: Context,
        archive: File,
        fallbackName: String,
        sourceTag: String? = null,
    ): LocalTheme? = withContext(Dispatchers.IO) {
        runCatching {
            val metadata = ThemeManager.readThemeMetadata(context, Uri.fromFile(archive))
            val id = "${System.currentTimeMillis()}"
            val fileName = "theme_$id.fpt"
            val target = File(directory(context), fileName)
            // The callers hand over a fresh temporary file, and cache and files share a partition,
            // so a rename avoids copying a 20MB archive a second time; the copy is the fallback.
            //
            // A successful `renameTo` is not proof the file arrived: the theme download hit
            // exactly that on the user's device and produced an index entry pointing at nothing,
            // so the listing dropped it and the downloaded tab looked empty. The target is
            // checked, and the copy runs whenever it is missing.
            val moved = archive.renameTo(target)
            if (!moved || !target.isFile) {
                target.delete()
                archive.copyTo(target, overwrite = true)
            }
            if (!target.isFile) error("theme archive did not reach ${target.name}")

            val theme = LocalTheme(
                id = id,
                fileName = fileName,
                name = metadata?.name?.takeIf { it.isNotBlank() } ?: fallbackName,
                author = metadata?.author.orEmpty(),
                version = metadata?.version.orEmpty(),
                sizeBytes = target.length(),
                addedAt = System.currentTimeMillis(),
                sourceTag = sourceTag,
            )

            // Replace an identical theme, judged from the index rather than from the directory:
            // the file just copied would otherwise be seen as its own duplicate.
            val indexed = readIndex(context)
            val duplicates = indexed.filter {
                (sourceTag != null && it.sourceTag == sourceTag) ||
                    (it.name == theme.name && it.version == theme.version)
            }
            duplicates.forEach { file(context, it).takeIf { f -> f.exists() }?.delete() }

            writeIndex(context, indexed - duplicates.toSet() + theme)
            theme
        }.getOrElse {
            Log.w(TAG, "could not add a theme to the library", it)
            null
        }
    }

    suspend fun delete(context: Context, theme: LocalTheme) = withContext(Dispatchers.IO) {
        runCatching {
            file(context, theme).takeIf { it.exists() }?.delete()
            writeIndex(context, readIndex(context) - theme)
        }.onFailure { Log.w(TAG, "could not delete ${theme.fileName}", it) }
        Unit
    }

    /** Index entries whose archive still exists, plus archives the index does not mention. */
    private suspend fun scan(context: Context): List<LocalTheme> {
        val indexed = readIndex(context).filter { file(context, it).isFile }
        val known = indexed.map { it.fileName }.toSet()
        val orphans = directory(context)
            .listFiles { file -> file.isFile && file.name.endsWith(".fpt", ignoreCase = true) }
            ?.filterNot { it.name in known }
            ?.mapNotNull { archive -> describe(context, archive) }
            .orEmpty()

        val merged = (indexed + orphans).sortedByDescending { it.addedAt }
        // Recording the discovered files makes the next listing cheaper and keeps the names.
        if (orphans.isNotEmpty()) writeIndex(context, merged)
        Log.d(TAG, "library: ${merged.size} theme(s), ${orphans.size} recovered from disk")
        return merged
    }

    private suspend fun describe(context: Context, archive: File): LocalTheme? = runCatching {
        val metadata = ThemeManager.readThemeMetadata(context, Uri.fromFile(archive))
        LocalTheme(
            id = archive.nameWithoutExtension,
            fileName = archive.name,
            name = metadata?.name?.takeIf { it.isNotBlank() } ?: archive.nameWithoutExtension,
            author = metadata?.author.orEmpty(),
            version = metadata?.version.orEmpty(),
            sizeBytes = archive.length(),
            addedAt = archive.lastModified(),
        )
    }.onFailure { Log.w(TAG, "could not read ${archive.name}", it) }.getOrNull()

    private fun readIndex(context: Context): List<LocalTheme> = runCatching {
        val raw = indexFile(context).takeIf { it.isFile }?.readText().orEmpty()
        if (raw.isBlank()) return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            LocalTheme(
                id = item.optString("id"),
                fileName = item.optString("fileName"),
                name = item.optString("name"),
                author = item.optString("author"),
                version = item.optString("version"),
                sizeBytes = item.optLong("sizeBytes"),
                addedAt = item.optLong("addedAt"),
                sourceTag = item.optString("sourceTag").takeIf { it.isNotBlank() },
            )
        }
    }.getOrElse {
        Log.w(TAG, "could not read the theme index", it)
        emptyList()
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
                    put("sourceTag", theme.sourceTag.orEmpty())
                },
            )
        }
        runCatching { indexFile(context).writeText(array.toString()) }
            .onFailure { Log.w(TAG, "could not write the theme index", it) }
    }
}
