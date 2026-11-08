package com.sukisu.ultra.ui.screen.themes

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.dialog.ConfirmResult
import com.sukisu.ultra.ui.component.dialog.rememberConfirmDialog
import com.sukisu.ultra.ui.navigation3.LocalNavigator
import com.sukisu.ultra.ui.theme.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The theme catalogue screen's state. Per-theme progress is keyed by the release tag. */
data class ThemeStoreState(
    val themes: List<StoreTheme> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    val busyTag: String? = null,
    val progress: Map<String, Int> = emptyMap(),
)

/**
 * Theme store: the releases of the theme repository, browsed and installed from here.
 *
 * Installing is a download plus this app's normal theme import, so a theme from the store and a
 * `.fpt` handed to the importer by hand end up in exactly the same place.
 */
@Composable
fun ThemeStoreScreen() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val confirmDialog = rememberConfirmDialog()
    var state by remember { mutableStateOf(ThemeStoreState()) }

    suspend fun reload() {
        state = state.copy(loading = true, failed = false)
        val result = withContext(Dispatchers.IO) {
            runCatching { ThemeStoreRepository.fetchThemes() }
        }
        state = result.fold(
            onSuccess = { state.copy(themes = it, loading = false) },
            onFailure = { state.copy(loading = false, failed = true) },
        )
    }

    LaunchedEffect(Unit) { reload() }

    fun toast(message: Int) {
        Toast.makeText(context, context.getString(message), Toast.LENGTH_SHORT).show()
    }

    fun install(theme: StoreTheme) {
        if (state.busyTag != null) return
        scope.launch {
            state = state.copy(busyTag = theme.tag, progress = state.progress + (theme.tag to 0))
            val file = ThemeStoreRepository.download(context, theme) { percent ->
                state = state.copy(progress = state.progress + (theme.tag to percent))
            }
            if (file == null) {
                state = state.copy(busyTag = null)
                toast(R.string.theme_store_download_failed)
                return@launch
            }

            val uri = ThemeStoreRepository.localUri(file)
            val metadata = withContext(Dispatchers.IO) {
                ThemeManager.readThemeMetadata(context, uri)
            }
            if (metadata == null) {
                file.delete()
                state = state.copy(busyTag = null)
                toast(R.string.theme_import_invalid)
                return@launch
            }

            val confirmed = confirmDialog.awaitConfirm(
                title = context.getString(R.string.theme_store_install_confirm_title),
                content = context.getString(
                    R.string.theme_store_install_confirm_message,
                    metadata.name,
                    metadata.author.ifBlank { "—" },
                    metadata.version.ifBlank { "—" },
                ),
                confirm = context.getString(R.string.theme_store_install),
            )
            if (confirmed != ConfirmResult.Confirmed) {
                file.delete()
                state = state.copy(busyTag = null)
                return@launch
            }

            val imported = ThemeManager.importTheme(context, uri)
            file.delete()
            state = state.copy(busyTag = null)
            toast(
                if (imported) R.string.settings_theme_imported
                else R.string.settings_theme_import_failed,
            )
        }
    }

    ThemeStoreFolk(
        state = state,
        onBack = { navigator.pop() },
        onRefresh = { scope.launch { reload() } },
        onInstall = ::install,
    )
}
