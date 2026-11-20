package com.sukisu.ultra.ui.screen.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.dialog.LoadingDialogHandle
import com.sukisu.ultra.ui.component.dialog.rememberConfirmDialog
import com.sukisu.ultra.ui.component.folk.FolkChoicePreference
import com.sukisu.ultra.ui.component.folk.FolkSettingsSectionGroup
import com.sukisu.ultra.ui.component.folk.FolkValuePreference
import com.sukisu.ultra.ui.theme.FontConfig
import com.sukisu.ultra.ui.theme.FontMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The font rows of the appearance screen, ported from FolkPatch
 * `ui/screen/settings/appearance/AppearanceFontSection.kt`.
 *
 * The choice itself lives in [FontConfig], which is snapshot state, so switching mode redraws
 * the whole app at once - there is no "restart to apply" step.
 */
@Composable
fun AppearanceFontSection(
    snackBarHost: SnackbarHostState,
    loadingDialog: LoadingDialogHandle,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val showMessage: (Int) -> Unit = { resId ->
        val message = context.getString(resId)
        scope.launch { snackBarHost.showSnackbar(message) }
    }

    // "*/*" rather than "font/*": file managers label .ttf/.otf inconsistently, and a wrong label
    // simply shows an empty picker. A file that is not a font fails in FontConfig and keeps the
    // current face instead of breaking the UI.
    val fontLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                loadingDialog.show()
                // A TTF can be several megabytes; the copy is file IO, not main-thread work.
                val ok = withContext(Dispatchers.IO) { FontConfig.saveFontFile(context, uri) }
                loadingDialog.hide()
                showMessage(if (ok) R.string.font_saved else R.string.font_save_failed)
            }
        }
    }

    val clearDialog = rememberConfirmDialog(
        onConfirm = {
            scope.launch {
                withContext(Dispatchers.IO) { FontConfig.clearFont(context) }
                showMessage(R.string.font_cleared)
            }
        },
    )

    fun pickFont() {
        try {
            fontLauncher.launch("*/*")
        } catch (e: ActivityNotFoundException) {
            showMessage(R.string.file_picker_unavailable)
        }
    }

    val modeNames = listOf(
        stringResource(R.string.font_mode_app),
        stringResource(R.string.font_mode_system),
        stringResource(R.string.font_mode_custom),
    )
    val hasCustomFile = !FontConfig.customFontFilename.isNullOrEmpty()

    FolkSettingsSectionGroup(title = stringResource(R.string.font_section_title)) {
        item(key = "font_mode") {
            FolkChoicePreference(
                title = stringResource(R.string.font_mode_title),
                summary = modeNames[FontConfig.fontMode.ordinal],
                icon = Icons.Outlined.FontDownload,
                options = modeNames,
                selectedIndex = FontConfig.fontMode.ordinal,
                onSelect = { index ->
                    val mode = FontMode.entries[index]
                    if (mode == FontMode.CUSTOM && !hasCustomFile) {
                        // Nothing imported yet: go straight to the picker instead of switching
                        // to a mode that would silently render the default font.
                        pickFont()
                    } else {
                        FontConfig.setFontMode(context, mode)
                    }
                },
            )
        }

        // Offered whenever there is no imported file, so CUSTOM is reachable before it is set.
        if (!hasCustomFile) {
            item(key = "font_pick") {
                FolkValuePreference(
                    title = stringResource(R.string.font_pick),
                    summary = stringResource(R.string.font_pick_summary),
                    icon = Icons.Outlined.TextFields,
                    onClick = { pickFont() },
                )
            }
        }

        if (hasCustomFile) {
            item(key = "font_replace") {
                FolkValuePreference(
                    title = stringResource(R.string.font_pick),
                    summary = stringResource(R.string.font_pick_summary),
                    icon = Icons.Outlined.TextFields,
                    onClick = { pickFont() },
                )
            }

            item(key = "font_clear") {
                FolkValuePreference(
                    title = stringResource(R.string.font_clear),
                    icon = Icons.Outlined.Delete,
                    onClick = {
                        clearDialog.showConfirm(
                            title = context.getString(R.string.font_clear),
                            content = context.getString(R.string.font_clear_confirm),
                        )
                    },
                )
            }
        }
    }
}
