package com.sukisu.ultra.ui.screen.umountmanager

import android.content.Context
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import org.json.JSONArray

val SPACING_SMALL = 3.dp
val SPACING_MEDIUM = 8.dp
val SPACING_LARGE = 16.dp

data class UmountPathEntry(
    val path: String,
    val flags: Int
)

fun parseUmountPaths(output: String): List<UmountPathEntry> {
    // `ksud umount list` prints a single-line JSON array of {"path","flags"} objects; the
    // whitespace table the parser used to expect never matched, so the manager always showed
    // an empty list. The old table format stays as a fallback for older daemons.
    val fromJson = runCatching {
        val array = JSONArray(output.trim())
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val path = item.optString("path")
            if (path.isEmpty()) null else UmountPathEntry(path, item.optInt("flags"))
        }
    }.getOrNull()
    if (!fromJson.isNullOrEmpty()) return fromJson

    val lines = output.lines().filter { it.isNotBlank() }
    if (lines.size < 2) return emptyList()

    return lines.drop(2).mapNotNull { line ->
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size >= 2) {
            UmountPathEntry(
                path = parts[0],
                flags = parts[1].toIntOrNull() ?: 0
            )
        } else null
    }
}

fun Int.toUmountFlagName(context: Context): String {
    return when (this) {
        2 -> context.getString(R.string.mnt_detach)
        else -> this.toString()
    }
}