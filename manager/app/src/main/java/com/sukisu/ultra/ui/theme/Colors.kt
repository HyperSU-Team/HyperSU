package com.sukisu.ultra.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Stands in for the system's dynamic colour on releases before Android 12, where the platform
 * palette does not exist. It matches the last entry of [keyColorOptions], so a device without
 * dynamic colour still starts on a colour the picker already offers.
 */
val FallbackSeedColor = Color(0xFFFF9CA8)

val keyColorOptions = listOf(
    Color(0xFFF44336).toArgb(),
    Color(0xFFE91E63).toArgb(),
    Color(0xFF9C27B0).toArgb(),
    Color(0xFF673AB7).toArgb(),
    Color(0xFF3F51B5).toArgb(),
    Color(0xFF2196F3).toArgb(),
    Color(0xFF00BCD4).toArgb(),
    Color(0xFF009688).toArgb(),
    Color(0xFF4FAF50).toArgb(),
    Color(0xFFFFEB3B).toArgb(),
    Color(0xFFFFC107).toArgb(),
    Color(0xFFFF9800).toArgb(),
    Color(0xFF795548).toArgb(),
    Color(0xFF607D8F).toArgb(),
    Color(0xFFFF9CA8).toArgb(),
)

/**
 * FolkPatch's named key colours, mapped onto the colour this app stores.
 *
 * The two apps keep the same setting in different forms - FolkPatch a name like `indigo`, this app
 * a packed ARGB int - so a theme built there carries a value this app cannot parse. Without this
 * table the seed was left untouched on import, and because the seed decides the whole generated
 * palette, an imported theme came out in the user's previous colours rather than its own.
 *
 * The names are FolkPatch's `ThemeColorOption` keys. Where it offers more colours than this app's
 * picker does, the nearest of this app's entries is used, so a theme still arrives with the
 * colour it asked for; the reverse direction reports this app's colour as the closest name.
 */
private val folkPatchColorNames: Map<String, Int> = linkedMapOf(
    "indigo" to 0xFF3F51B5.toInt(),
    "blue" to 0xFF2196F3.toInt(),
    "light_blue" to 0xFF00BCD4.toInt(),
    "cyan" to 0xFF00BCD4.toInt(),
    "teal" to 0xFF009688.toInt(),
    "green" to 0xFF4FAF50.toInt(),
    "light_green" to 0xFF4FAF50.toInt(),
    "lime" to 0xFF4FAF50.toInt(),
    "yellow" to 0xFFFFEB3B.toInt(),
    "amber" to 0xFFFFC107.toInt(),
    "orange" to 0xFFFF9800.toInt(),
    "deep_orange" to 0xFFF44336.toInt(),
    "red" to 0xFFF44336.toInt(),
    "pink" to 0xFFE91E63.toInt(),
    "purple" to 0xFF9C27B0.toInt(),
    "deep_purple" to 0xFF673AB7.toInt(),
    "brown" to 0xFF795548.toInt(),
    "blue_grey" to 0xFF607D8F.toInt(),
    "sakura" to 0xFFFF9CA8.toInt(),
    "ink_wash" to 0xFF607D8F.toInt(),
)

/** The colour FolkPatch means by [name], or null when it names one this app does not know. */
fun folkPatchColorFor(name: String?): Int? =
    folkPatchColorNames[name?.trim()?.lowercase()]

/** The FolkPatch name closest to [argb], for the reverse direction of an export. */
fun folkPatchNameFor(argb: Int): String? {
    val target = Color(argb)
    return folkPatchColorNames.entries
        .minByOrNull { (_, value) ->
            val candidate = Color(value)
            val dr = target.red - candidate.red
            val dg = target.green - candidate.green
            val db = target.blue - candidate.blue
            dr * dr + dg * dg + db * db
        }
        ?.key
}
