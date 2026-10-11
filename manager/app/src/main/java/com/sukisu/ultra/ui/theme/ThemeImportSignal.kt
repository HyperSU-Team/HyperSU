package com.sukisu.ultra.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Bumped after a theme is imported, reset or applied.
 *
 * A theme import writes preferences directly rather than going through the ViewModels that own
 * them, so nothing would otherwise tell the screens to re-read. Those writes do not all fire
 * `OnSharedPreferenceChangeListener` either - a key whose value did not actually change, and every
 * key written inside a single `edit { }` commit, arrive as one notification at best - so a signal
 * raised by the import itself is what the screens watch.
 */
object ThemeImportSignal {
    var revision by mutableIntStateOf(0)
        private set

    fun notifyImported() {
        revision++
    }
}
