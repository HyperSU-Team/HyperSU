package com.sukisu.ultra.ui.screen.themes

import android.content.Context
import android.util.Log
import android.widget.Toast
import coil.imageLoader
import coil.request.ImageRequest
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
import java.io.File

enum class ThemeStoreTab { STORE, DOWNLOADED }

/**
 * How many covers to fetch ahead of the user.
 *
 * The cards are tall, so only a couple are on screen at a time, and each cover takes seconds on a
 * slow connection - that is what made the list look empty while scrolling. Warming the first few
 * while the user reads the top of the list means the cards they reach are already decoded.
 */
private const val PREVIEW_PREFETCH = 8

/**
 * Fetches the first covers into the image cache in the background.
 *
 * It requests the same url the card itself will ask for (the resized one), so the card finds it in
 * the disk cache instead of starting its own transfer.
 */
private fun prefetchPreviews(context: Context, themes: List<StoreTheme>) {
    val loader = context.imageLoader
    themes.asSequence()
        .mapNotNull { it.previewUrl }
        .distinct()
        .take(PREVIEW_PREFETCH)
        .forEach { preview ->
            val url = ThemeStoreRepository.previewCandidates(preview).firstOrNull() ?: return@forEach
            loader.enqueue(ImageRequest.Builder(context).data(url).build())
        }
}

/** The theme store's state. Per-theme progress is keyed by the release tag. */
data class ThemeStoreState(
    val themes: List<StoreTheme> = emptyList(),
    val downloads: List<LocalTheme> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    val tab: ThemeStoreTab = ThemeStoreTab.STORE,
    val query: String = "",
    val busyKey: String? = null,
    val progress: Map<String, Int> = emptyMap(),
)

/**
 * Theme store: the releases of the theme repository, plus the themes already downloaded.
 *
 * Installing is a download, a copy into the on-device library, and this app's normal theme
 * import, so a theme from the store, one from the "downloaded" tab and a `.fpt` handed to the
 * importer by hand all end up in exactly the same place.
 */
@Composable
fun ThemeStoreScreen() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val confirmDialog = rememberConfirmDialog()
    var state by remember { mutableStateOf(ThemeStoreState()) }

    suspend fun reloadLibrary() {
        val downloads = withContext(Dispatchers.IO) { ThemeLibrary.list(context) }
        state = state.copy(downloads = downloads)
    }

    suspend fun reload(force: Boolean = false) {
        state = state.copy(loading = true, failed = false)
        val themes = withContext(Dispatchers.IO) {
            runCatching { ThemeStoreRepository.fetchThemes(context, force) }
        }
        state = themes.fold(
            onSuccess = { state.copy(themes = it, loading = false) },
            onFailure = { state.copy(loading = false, failed = true) },
        )
        themes.getOrNull()?.let { prefetchPreviews(context, it) }
        reloadLibrary()
    }

    LaunchedEffect(Unit) {
        // The library lives on the device, so it is read first and on its own: the downloaded tab
        // used to stay empty until the GitHub request came back, which on a slow connection looked
        // like nothing had ever been downloaded.
        reloadLibrary()
        reload()
    }

    fun toast(message: Int) {
        Toast.makeText(context, context.getString(message), Toast.LENGTH_SHORT).show()
    }

    suspend fun confirmAndImport(local: LocalTheme): Boolean {
        val confirmed = confirmDialog.awaitConfirm(
            title = context.getString(R.string.theme_store_install_confirm_title),
            content = context.getString(
                R.string.theme_store_install_confirm_message,
                local.name,
                local.author.ifBlank { "—" },
                local.version.ifBlank { "—" },
            ),
            // Always "apply", never "download and apply": by the time this dialog is shown the
            // archive is already in the library, whether it was just downloaded or was there
            // all along. The old label made an installed theme look like it still had to be
            // downloaded.
            confirm = context.getString(R.string.theme_store_apply),
        )
        if (confirmed != ConfirmResult.Confirmed) return false

        val uri = ThemeStoreRepository.localUri(ThemeLibrary.file(context, local))
        val imported = ThemeManager.importTheme(context, uri)
        toast(
            if (imported) R.string.settings_theme_imported
            else R.string.settings_theme_import_failed,
        )
        return imported
    }

    fun install(theme: StoreTheme) {
        if (state.busyKey != null) return
        scope.launch {
            state = state.copy(busyKey = theme.tag, progress = state.progress + (theme.tag to 0))
            val archive = runCatching {
                ThemeStoreRepository.download(context, theme) { percent ->
                    state = state.copy(progress = state.progress + (theme.tag to percent))
                }
            }.onFailure { Log.w("ThemeStore", "download failed", it) }.getOrNull()
            if (archive == null) {
                state = state.copy(busyKey = null)
                toast(R.string.theme_store_download_failed)
                return@launch
            }

            val local = withContext(Dispatchers.IO) {
                ThemeLibrary.add(context, archive, theme.name, sourceTag = theme.tag)
            }
            archive.delete()
            if (local == null) {
                state = state.copy(busyKey = null)
                toast(R.string.theme_import_invalid)
                return@launch
            }

            state = state.copy(busyKey = null)
            confirmAndImport(local)
            reloadLibrary()
        }
    }

    fun applyLocal(local: LocalTheme) {
        if (state.busyKey != null) return
        scope.launch {
            state = state.copy(busyKey = local.id)
            confirmAndImport(local)
            state = state.copy(busyKey = null)
        }
    }

    fun deleteLocal(local: LocalTheme) {
        scope.launch {
            withContext(Dispatchers.IO) { ThemeLibrary.delete(context, local) }
            reloadLibrary()
        }
    }

    ThemeStoreFolk(
        state = state,
        onBack = { navigator.pop() },
        onRefresh = { scope.launch { reload(force = true) } },
        onTabChange = { tab ->
            state = state.copy(tab = tab)
            // An import can land while the screen is open (the picker returns to it), so the
            // downloaded tab re-reads the library each time it is opened instead of trusting the
            // list that was read on first composition.
            if (tab == ThemeStoreTab.DOWNLOADED) scope.launch { reloadLibrary() }
        },
        onQueryChange = { query -> state = state.copy(query = query) },
        onInstall = ::install,
        onApplyLocal = ::applyLocal,
        onDeleteLocal = ::deleteLocal,
    )
}
