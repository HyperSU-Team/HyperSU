package com.sukisu.ultra.ui.screen.settings

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.dialog.LoadingDialogHandle
import com.sukisu.ultra.ui.component.dialog.rememberConfirmDialog
import com.sukisu.ultra.ui.component.folk.FolkAlertDialog
import com.sukisu.ultra.ui.component.folk.FolkButtonDefaults
import com.sukisu.ultra.ui.component.folk.FolkSettingsSectionGroup
import com.sukisu.ultra.ui.navigation3.LocalNavigator
import com.sukisu.ultra.ui.navigation3.Route
import com.sukisu.ultra.ui.screen.themes.ThemeLibrary
import com.sukisu.ultra.ui.screen.themes.ThemeStoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.sukisu.ultra.ui.component.folk.FolkValuePreference
import com.sukisu.ultra.ui.theme.ThemeManager
import com.sukisu.ultra.ui.theme.tokens.FolkType
import kotlinx.coroutines.launch

/**
 * Theme save / import / reset rows, ported from FolkPatch
 * `ui/screen/settings/appearance/AppearanceThemeSection.kt` and
 * `AppearanceThemeIoDialogs.kt`.
 *
 * Export and import both go through the system document picker, so no storage permission is
 * needed; the archive itself is written and read by [ThemeManager].
 */
@Composable
fun AppearanceThemeSection(
    snackBarHost: SnackbarHostState,
    loadingDialog: LoadingDialogHandle,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navigator = LocalNavigator.current

    val showMessage: (Int) -> Unit = { resId ->
        val message = context.getString(resId)
        scope.launch { snackBarHost.showSnackbar(message) }
    }

    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<ThemeManager.ThemeMetadata?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImport by remember { mutableStateOf<ThemeManager.ThemeMetadata?>(null) }

    // Declared outside the dialogs so the launcher keeps one position in composition for the
    // whole lifetime of the screen.
    //
    // The MIME type is deliberately generic. A .fpt is a ZIP, but declaring "application/zip"
    // made some file managers rewrite the name to .zip on save, so the exported file no longer
    // looked like a theme and would not come back through the importer.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val metadata = pendingExport
        pendingExport = null
        if (uri != null && metadata != null) {
            scope.launch {
                loadingDialog.show()
                val success = ThemeManager.exportTheme(context, uri, metadata)
                loadingDialog.hide()
                showMessage(
                    if (success) R.string.settings_theme_saved
                    else R.string.settings_theme_save_failed,
                )
            }
        }
    }

    // "*/*" and not a ZIP filter: the picker is the system document UI, where a .fpt has no
    // registered MIME type, so any narrower filter hides the very files this screen exports.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                loadingDialog.show()
                try {
                    val metadata = ThemeManager.readThemeMetadata(context, uri)
                    if (metadata == null) {
                        // Not a theme archive: nothing to confirm, report it as invalid.
                        showMessage(R.string.theme_import_invalid)
                    } else {
                        pendingImportUri = uri
                        pendingImport = metadata
                        showImportDialog = true
                    }
                } finally {
                    loadingDialog.hide()
                }
            }
        }
    }

    val resetDialog = rememberConfirmDialog(
        onConfirm = {
            scope.launch {
                loadingDialog.show()
                val success = ThemeManager.resetTheme(context)
                loadingDialog.hide()
                showMessage(
                    if (success) R.string.settings_theme_reset
                    else R.string.settings_theme_reset_failed,
                )
            }
        },
    )

    FolkSettingsSectionGroup(title = stringResource(R.string.settings_appearance_theme)) {
        item(key = "appearance_theme_store") {
            FolkValuePreference(
                title = stringResource(R.string.theme_store),
                summary = stringResource(R.string.theme_store_summary),
                icon = Icons.Outlined.Storefront,
                onClick = { navigator.push(Route.ThemeStore) },
            )
        }

        item(key = "appearance_save_theme") {
            FolkValuePreference(
                title = stringResource(R.string.settings_save_theme),
                summary = stringResource(R.string.settings_save_theme_summary),
                icon = Icons.Outlined.FileDownload,
                onClick = { showExportDialog = true },
            )
        }

        item(key = "appearance_import_theme") {
            FolkValuePreference(
                title = stringResource(R.string.settings_import_theme),
                summary = stringResource(R.string.settings_import_theme_summary),
                icon = Icons.Outlined.FileUpload,
                onClick = {
                    runCatching { importLauncher.launch("*/*") }
                        .onFailure { showMessage(R.string.file_picker_unavailable) }
                },
            )
        }

        item(key = "appearance_reset_theme") {
            FolkValuePreference(
                title = stringResource(R.string.settings_reset_theme),
                summary = stringResource(R.string.settings_reset_theme_summary),
                icon = Icons.Outlined.RestartAlt,
                onClick = {
                    resetDialog.showConfirm(
                        title = context.getString(R.string.settings_reset_theme),
                        content = context.getString(R.string.settings_reset_theme_confirm),
                    )
                },
            )
        }
    }

    if (showExportDialog) {
        ThemeExportDialog(
            onDismiss = { showExportDialog = false },
            onConfirm = { metadata ->
                showExportDialog = false
                pendingExport = metadata
                val safeName = metadata.name.replace("[\\/:*?\"<>|]".toRegex(), "_")
                exportLauncher.launch("$safeName.${ThemeManager.themeFileExtension}")
            },
        )
    }

    val importMetadata = pendingImport
    if (showImportDialog && importMetadata != null) {
        ThemeImportDialog(
            metadata = importMetadata,
            onDismiss = {
                showImportDialog = false
                pendingImport = null
                pendingImportUri = null
            },
            onConfirm = {
                val uri = pendingImportUri
                showImportDialog = false
                pendingImport = null
                pendingImportUri = null
                if (uri != null) {
                    scope.launch {
                loadingDialog.show()
                try {
                    // Copy into the library first: the picker's uri is the only copy, so
                    // keeping it is what makes the theme applyable again from the store's
                    // downloaded tab. Importing the archived copy also means both views work
                    // from the same bytes.
                    val local = copyToThemeLibrary(context, uri)
                    val success = if (local != null) {
                        ThemeManager.importTheme(
                            context,
                            ThemeStoreRepository.localUri(ThemeLibrary.file(context, local)),
                        )
                    } else {
                        ThemeManager.importTheme(context, uri)
                    }
                    showMessage(
                        if (success) R.string.settings_theme_imported
                        else R.string.settings_theme_import_failed,
                    )
                } finally {
                    loadingDialog.hide()
                }
            }
                }
            },
        )
    }
}

/**
 * Copies a picked theme archive into the on-device library.
 *
 * The picker hands over a content uri that may stop being readable once the activity is gone, so
 * the bytes are copied into a temporary file first and the library takes it from there.
 */
private suspend fun copyToThemeLibrary(
    context: android.content.Context,
    uri: Uri,
): com.sukisu.ultra.ui.screen.themes.LocalTheme? = withContext(Dispatchers.IO) {
    val temp = File(context.cacheDir, "theme_library_import.fpt")
    val added = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        } ?: return@runCatching null
        if (!temp.isFile) return@runCatching null
        ThemeLibrary.add(context, temp, temp.nameWithoutExtension)
    }.onFailure {
        Log.w("AppearanceThemeSection", "could not keep a copy of the theme", it)
    }.getOrNull()
    temp.delete()
    added
}

/**
 * The metadata form shown before an export. Kept separate so the screen stays a list of rows;
 * the fields live in this composable and only the result leaves it.
 */
@Composable
private fun ThemeExportDialog(
    onDismiss: () -> Unit,
    onConfirm: (ThemeManager.ThemeMetadata) -> Unit,
) {
    val defaultName = stringResource(R.string.theme_default_name)
    val defaultAuthor = stringResource(R.string.theme_default_author)

    // Keyed on the localized defaults: they come from stringResource, so after a language change
    // the fields would otherwise keep the wording of the language the dialog was opened in.
    var name by remember(defaultName) { mutableStateOf(defaultName) }
    var author by remember(defaultAuthor) { mutableStateOf(defaultAuthor) }
    var description by remember { mutableStateOf("") }

    FolkAlertDialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.theme_export_title),
                style = FolkType.Title,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.theme_export_details),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.theme_field_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.padding(top = 8.dp))
            OutlinedTextField(
                value = author,
                onValueChange = { author = it },
                label = { Text(stringResource(R.string.theme_field_author)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.padding(top = 8.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.theme_field_description)) },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onDismiss,
                    colors = FolkButtonDefaults.textColors(),
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    enabled = name.isNotBlank() && author.isNotBlank(),
                    onClick = {
                        onConfirm(
                            ThemeManager.ThemeMetadata(
                                name = name.trim(),
                                type = "phone",
                                version = "1.0",
                                author = author.trim(),
                                description = description.trim(),
                            ),
                        )
                    },
                    colors = FolkButtonDefaults.textColors(),
                ) {
                    Text(stringResource(R.string.theme_export_action))
                }
            }
        }
    }
}

/** The confirmation shown once a theme file has been read successfully. */
@Composable
private fun ThemeImportDialog(
    metadata: ThemeManager.ThemeMetadata,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    FolkAlertDialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.theme_import_title),
                style = FolkType.Title,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.theme_import_confirm),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            Text(
                text = metadata.name,
                style = FolkType.Title,
                fontWeight = FontWeight.Medium,
            )
            if (metadata.author.isNotEmpty()) {
                Text(
                    text = metadata.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (metadata.description.isNotEmpty()) {
                Text(
                    text = metadata.description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onDismiss,
                    colors = FolkButtonDefaults.textColors(),
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = onConfirm,
                    colors = FolkButtonDefaults.textColors(),
                ) {
                    Text(stringResource(R.string.theme_import_action))
                }
            }
        }
    }
}
