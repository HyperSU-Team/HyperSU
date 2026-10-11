package com.sukisu.ultra.ui.screen.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.dialog.LoadingDialogHandle
import com.sukisu.ultra.ui.component.dialog.rememberConfirmDialog
import com.sukisu.ultra.ui.component.folk.FolkSettingsSectionGroup
import com.sukisu.ultra.ui.component.folk.FolkSliderPreference
import com.sukisu.ultra.ui.component.folk.FolkSwitchPreference
import com.sukisu.ultra.ui.component.folk.FolkValuePreference
import com.sukisu.ultra.ui.screen.themeSettings.crop.CropBackgroundContract
import com.sukisu.ultra.ui.theme.BackgroundConfig
import com.sukisu.ultra.ui.theme.BackgroundManager
import kotlinx.coroutines.launch

/**
 * The advanced title style, ported from FolkPatch: the home top bar's title becomes an image.
 *
 * The image is fitted to the bar's height and can be offset horizontally, which is what makes a
 * wordmark or a logo sit where the user wants it rather than centred by the bar. Day and night
 * opacities are separate because a light image on a dark bar and the same image on a light bar
 * rarely want the same strength.
 */
@Composable
fun AppearanceTitleImageSection(
    snackBarHost: SnackbarHostState,
    loadingDialog: LoadingDialogHandle,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val showMessage: (Int) -> Unit = { resId ->
        val message = context.getString(resId)
        scope.launch { snackBarHost.showSnackbar(message) }
    }

    // Cropped before saving, so the user frames the wordmark rather than the app centre-cropping
    // it. A title image is wide and short, so framing matters more here than for a wallpaper.
    val cropLauncher = rememberLauncherForActivityResult(
        CropBackgroundContract(),
    ) { cropped: Uri? ->
        if (cropped == null) {
            CropBackgroundContract.clearCache(context)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            loadingDialog.show()
            val success = BackgroundManager.saveAndApplyTitleImage(context, cropped)
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
                BackgroundManager.clearTitleImage(context)
                loadingDialog.hide()
                showMessage(R.string.settings_background_image_cleared)
            }
        },
    )

    val hasImage = !BackgroundConfig.titleImageUri.isNullOrEmpty()
    val save = { BackgroundConfig.save(context) }

    FolkSettingsSectionGroup(title = stringResource(R.string.title_image_section_title)) {
        item(key = "title_image_enabled") {
            FolkSwitchPreference(
                title = stringResource(R.string.title_image_enabled),
                summary = stringResource(R.string.title_image_enabled_summary),
                icon = Icons.Outlined.Title,
                checked = BackgroundConfig.isAdvancedTitleStyleEnabled,
                onCheckedChange = { enabled ->
                    BackgroundConfig.setAdvancedTitleStyleEnabledState(enabled)
                    save()
                },
            )
        }

        if (BackgroundConfig.isAdvancedTitleStyleEnabled) {
            item(key = "title_image_select") {
                FolkValuePreference(
                    title = stringResource(R.string.title_image_select),
                    summary = if (hasImage) {
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

            item(key = "title_image_day_opacity") {
                FolkSliderPreference(
                    title = stringResource(R.string.title_image_day_opacity),
                    value = BackgroundConfig.titleImageDayOpacity,
                    onValueChange = {
                        BackgroundConfig.setTitleImageDayOpacityValue(it)
                        save()
                    },
                )
            }

            item(key = "title_image_night_opacity") {
                FolkSliderPreference(
                    title = stringResource(R.string.title_image_night_opacity),
                    value = BackgroundConfig.titleImageNightOpacity,
                    onValueChange = {
                        BackgroundConfig.setTitleImageNightOpacityValue(it)
                        save()
                    },
                )
            }

            item(key = "title_image_dim") {
                FolkSliderPreference(
                    title = stringResource(R.string.title_image_dim),
                    value = BackgroundConfig.titleImageDim,
                    onValueChange = {
                        BackgroundConfig.setTitleImageDimValue(it)
                        save()
                    },
                )
            }

            item(key = "title_image_offset") {
                FolkSliderPreference(
                    title = stringResource(R.string.title_image_offset_x),
                    value = BackgroundConfig.titleImageOffsetX,
                    onValueChange = {
                        BackgroundConfig.setTitleImageOffsetXValue(it)
                        save()
                    },
                )
            }

            if (hasImage) {
                item(key = "title_image_clear") {
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
