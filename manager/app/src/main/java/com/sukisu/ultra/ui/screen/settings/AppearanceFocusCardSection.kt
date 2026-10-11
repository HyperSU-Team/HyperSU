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
 * The Focus layout's main-card wallpaper, ported from FolkPatch
 * `ui/screen/settings/appearance/AppearanceFocusCardSection.kt`.
 *
 * FolkPatch's Focus layout has four separate cards; HyperSU's has one hero card plus a facts
 * group, so this section owns a single image rather than four. The controls are shared with the
 * Dashboard tiles through [CardWallpaperControls].
 */
@Composable
fun AppearanceFocusCardSection(
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
            val success = BackgroundManager.saveAndApplyFocusCardBackground(context, cropped)
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
                BackgroundManager.clearFocusCardBackground(context)
                loadingDialog.hide()
                showMessage(R.string.settings_background_image_cleared)
            }
        },
    )

    val hasWallpaper = !BackgroundConfig.focusCardBgUri.isNullOrEmpty()
    val save = { BackgroundConfig.save(context) }

    FolkSettingsSectionGroup(title = stringResource(R.string.focus_card_section_title)) {
        item(key = "focus_card_background_enabled") {
            FolkSwitchPreference(
                title = stringResource(R.string.focus_card_background),
                summary = stringResource(R.string.focus_card_background_summary),
                icon = Icons.Outlined.Wallpaper,
                checked = BackgroundConfig.isFocusCardBackgroundEnabled,
                onCheckedChange = { enabled ->
                    BackgroundConfig.setFocusCardBackgroundEnabledState(enabled)
                    save()
                },
            )
        }

        if (BackgroundConfig.isFocusCardBackgroundEnabled) {
            CardWallpaperControls(
                keyPrefix = "focus_card",
                dualDimEnabled = BackgroundConfig.isFocusCardDualDimEnabled,
                onDualDimChange = { BackgroundConfig.setFocusCardDualDimEnabledState(it) },
                dim = BackgroundConfig.focusCardBgDim,
                onDimChange = { BackgroundConfig.setFocusCardBgDimValue(it) },
                dayDim = BackgroundConfig.focusCardBgDayDim,
                onDayDimChange = { BackgroundConfig.setFocusCardBgDayDimValue(it) },
                nightDim = BackgroundConfig.focusCardBgNightDim,
                onNightDimChange = { BackgroundConfig.setFocusCardBgNightDimValue(it) },
                dualOpacityEnabled = BackgroundConfig.isFocusCardDualOpacityEnabled,
                onDualOpacityChange = { BackgroundConfig.setFocusCardDualOpacityEnabledState(it) },
                opacity = BackgroundConfig.focusCardBgOpacity,
                onOpacityChange = { BackgroundConfig.setFocusCardBgOpacityValue(it) },
                dayOpacity = BackgroundConfig.focusCardBgDayOpacity,
                onDayOpacityChange = { BackgroundConfig.setFocusCardBgDayOpacityValue(it) },
                nightOpacity = BackgroundConfig.focusCardBgNightOpacity,
                onNightOpacityChange = { BackgroundConfig.setFocusCardBgNightOpacityValue(it) },
                onSaved = save,
            )

            item(key = "focus_card_select") {
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

            if (hasWallpaper) {
                item(key = "focus_card_clear") {
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
