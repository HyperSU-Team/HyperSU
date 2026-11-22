package com.sukisu.ultra.ui.screen.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.dialog.LoadingDialogHandle
import com.sukisu.ultra.ui.component.dialog.rememberConfirmDialog
import com.sukisu.ultra.ui.component.folk.FolkSettingsSectionGroup
import com.sukisu.ultra.ui.component.folk.FolkSwitchPreference
import com.sukisu.ultra.ui.component.folk.FolkValuePreference
import com.sukisu.ultra.ui.screen.themeSettings.crop.CropBackgroundContract
import com.sukisu.ultra.ui.theme.BackgroundConfig
import com.sukisu.ultra.ui.theme.BackgroundManager
import kotlinx.coroutines.launch

/**
 * The Grid layout's main-card wallpaper, ported from FolkPatch.
 *
 * Grid gives its status card a wallpaper of its own, separate from the Focus layout's, and can
 * hide the three things the card draws on top of it: the check mark, the text block and the mode
 * label. Those are the point of the feature - a picture with the card's own content over it is
 * rarely what someone wants.
 */
@Composable
fun AppearanceGridCardSection(
    snackBarHost: SnackbarHostState,
    loadingDialog: LoadingDialogHandle,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val showMessage: (Int) -> Unit = { resId ->
        val message = context.getString(resId)
        scope.launch { snackBarHost.showSnackbar(message) }
    }

    // Cropped before saving, so the user frames the card image rather than the app
    // centre-cropping it.
    val cropLauncher = rememberLauncherForActivityResult(
        CropBackgroundContract(),
    ) { cropped: Uri? ->
        if (cropped == null) {
            CropBackgroundContract.clearCache(context)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            loadingDialog.show()
            val success = BackgroundManager.saveAndApplyGridWorkingCardBackground(context, cropped)
            loadingDialog.hide()
            showMessage(
                if (success) R.string.settings_custom_background_saved
                else R.string.settings_custom_background_error,
            )
            CropBackgroundContract.clearCache(context)
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching { cropLauncher.launch(CropBackgroundContract.Input(source = uri)) }
                .onFailure { showMessage(R.string.file_picker_unavailable) }
        }
    }

    val clearDialog = rememberConfirmDialog(
        onConfirm = {
            scope.launch {
                loadingDialog.show()
                BackgroundManager.clearGridWorkingCardBackground(context)
                loadingDialog.hide()
                showMessage(R.string.settings_background_image_cleared)
            }
        },
    )

    val hasWallpaper = !BackgroundConfig.gridWorkingCardBgUri.isNullOrEmpty()
    val save = { BackgroundConfig.save(context) }

    FolkSettingsSectionGroup(title = stringResource(R.string.grid_card_section_title)) {
        item(key = "grid_card_background_enabled") {
            FolkSwitchPreference(
                title = stringResource(R.string.grid_card_background),
                summary = stringResource(R.string.grid_card_background_summary),
                icon = Icons.Outlined.Wallpaper,
                checked = BackgroundConfig.isGridWorkingCardBackgroundEnabled,
                onCheckedChange = { enabled ->
                    BackgroundConfig.setGridWorkingCardBackgroundEnabledState(enabled)
                    save()
                },
            )
        }

        if (BackgroundConfig.isGridWorkingCardBackgroundEnabled) {
            CardWallpaperControls(
                keyPrefix = "grid_card",
                dualDimEnabled = false,
                onDualDimChange = {},
                dim = BackgroundConfig.gridWorkingCardBgDim,
                onDimChange = { BackgroundConfig.setGridWorkingCardBgDimValue(it) },
                dayDim = BackgroundConfig.gridWorkingCardBgDim,
                onDayDimChange = { BackgroundConfig.setGridWorkingCardBgDimValue(it) },
                nightDim = BackgroundConfig.gridWorkingCardBgDim,
                onNightDimChange = { BackgroundConfig.setGridWorkingCardBgDimValue(it) },
                dualOpacityEnabled = BackgroundConfig.isGridWorkingCardDualOpacityEnabled,
                onDualOpacityChange = { BackgroundConfig.setGridWorkingCardDualOpacityEnabledState(it) },
                opacity = BackgroundConfig.gridWorkingCardBgOpacity,
                onOpacityChange = { BackgroundConfig.setGridWorkingCardBgOpacityValue(it) },
                dayOpacity = BackgroundConfig.gridWorkingCardBgDayOpacity,
                onDayOpacityChange = { BackgroundConfig.setGridWorkingCardBgDayOpacityValue(it) },
                nightOpacity = BackgroundConfig.gridWorkingCardBgNightOpacity,
                onNightOpacityChange = { BackgroundConfig.setGridWorkingCardBgNightOpacityValue(it) },
                onSaved = save,
            )

            item(key = "grid_card_select") {
                FolkValuePreference(
                    title = stringResource(R.string.settings_select_background_image),
                    summary = if (hasWallpaper) {
                        stringResource(R.string.settings_background_selected)
                    } else {
                        null
                    },
                    icon = Icons.Outlined.Image,
                    onClick = {
                        runCatching { pickLauncher.launch("image/*") }
                            .onFailure { showMessage(R.string.file_picker_unavailable) }
                    },
                )
            }

            item(key = "grid_card_hide_check") {
                FolkSwitchPreference(
                    title = stringResource(R.string.grid_card_hide_check),
                    summary = stringResource(R.string.grid_card_hide_check_summary),
                    icon = Icons.Outlined.Wallpaper,
                    checked = BackgroundConfig.isGridWorkingCardCheckHidden,
                    onCheckedChange = {
                        BackgroundConfig.setGridWorkingCardCheckHiddenState(it)
                        save()
                    },
                )
            }

            item(key = "grid_card_hide_text") {
                FolkSwitchPreference(
                    title = stringResource(R.string.grid_card_hide_text),
                    summary = stringResource(R.string.grid_card_hide_text_summary),
                    icon = Icons.Outlined.Wallpaper,
                    checked = BackgroundConfig.isGridWorkingCardTextHidden,
                    onCheckedChange = {
                        BackgroundConfig.setGridWorkingCardTextHiddenState(it)
                        save()
                    },
                )
            }

            item(key = "grid_card_hide_mode") {
                FolkSwitchPreference(
                    title = stringResource(R.string.grid_card_hide_mode),
                    summary = stringResource(R.string.grid_card_hide_mode_summary),
                    icon = Icons.Outlined.Wallpaper,
                    checked = BackgroundConfig.isGridWorkingCardModeHidden,
                    onCheckedChange = {
                        BackgroundConfig.setGridWorkingCardModeHiddenState(it)
                        save()
                    },
                )
            }

            if (hasWallpaper) {
                item(key = "grid_card_clear") {
                    FolkValuePreference(
                        title = stringResource(R.string.settings_clear_background),
                        icon = Icons.Outlined.Delete,
                        onClick = {
                            clearDialog.showConfirm(
                                title = context.getString(R.string.settings_clear_background),
                                content = context.getString(R.string.settings_clear_background_confirm),
                            )
                        },
                    )
                }
            }
        }
    }
}
