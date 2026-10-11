package com.sukisu.ultra.ui.screen.settings

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
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
 * The wallpaper rows of the appearance screen, ported from FolkPatch
 * `ui/screen/settings/appearance/AppearanceBackgroundSection.kt`.
 *
 * Everything the section edits lives in [BackgroundConfig], which is a snapshot-state holder,
 * so each row reads live values and the theme (and therefore the transparent page background)
 * follows immediately. Saving an image is delegated to [BackgroundManager].
 */
@Composable
fun AppearanceBackgroundSection(
    snackBarHost: SnackbarHostState,
    loadingDialog: LoadingDialogHandle,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Which wallpaper the pending pick will replace: null once the pick finished.
    var pendingTarget by remember { mutableStateOf<String?>(null) }

    val showMessage: (Int) -> Unit = { resId ->
        val message = context.getString(resId)
        scope.launch { snackBarHost.showSnackbar(message) }
    }

    // The wallpaper rows that take a still image, which is all of them except video: the
    // cropper edits a single frame, which says nothing about how a clip should be framed.
    // "default" is the main background; the others are the per-page ones.
    val croppableTargets = remember {
        setOf("default", "home", "superuser", "module", "settings")
    }

    /** Saves the chosen image for [target], once it is final. */
    fun applyBackground(target: String, uri: Uri) {
        scope.launch {
            loadingDialog.show()
            val success = when (target) {
                "video" -> BackgroundManager.saveAndApplyVideoBackground(context, uri)
                "home" -> BackgroundManager.saveAndApplyHomeBackground(context, uri)
                "superuser" -> BackgroundManager.saveAndApplySuperuserBackground(context, uri)
                "module" -> BackgroundManager.saveAndApplyModuleBackground(context, uri)
                "settings" -> BackgroundManager.saveAndApplySettingsBackground(context, uri)
                else -> BackgroundManager.saveAndApplyCustomBackground(context, uri)
            }
            // Picking a wallpaper is the user opting in, same as for the still images.
            if (success && target == "video") {
                BackgroundConfig.setVideoBackgroundEnabledState(true)
                BackgroundConfig.save(context)
            }
            loadingDialog.hide()
            showMessage(
                if (success) R.string.settings_custom_background_saved
                else R.string.settings_custom_background_error,
            )
            // The crop is a scratch copy and the image has been taken into internal storage by
            // now, so it is safe to drop. Doing it here rather than at the launcher callback
            // avoids having to tell the cropped file apart from the others: a FileProvider uri
            // reports a path relative to its root, which does not compare to an absolute one.
            CropBackgroundContract.clearCache(context)
        }
    }

    // Cropping happens between the picker and the save, so the user frames the image rather
    // than the app centre-cropping it. A cancelled crop leaves the current wallpaper alone.
    val cropLauncher = rememberLauncherForActivityResult(
        CropBackgroundContract(),
    ) { cropped: Uri? ->
        val target = pendingTarget
        pendingTarget = null
        if (cropped == null || target == null) {
            // Cancelled: nothing was saved, so the scratch copy can go.
            CropBackgroundContract.clearCache(context)
            return@rememberLauncherForActivityResult
        }
        applyBackground(target, cropped)
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        val target = pendingTarget
        if (uri == null || target == null) {
            pendingTarget = null
            return@rememberLauncherForActivityResult
        }

        if (target in croppableTargets) {
            // pendingTarget stays set: the crop result is what completes the pick.
            try {
                cropLauncher.launch(CropBackgroundContract.Input(source = uri))
            } catch (e: ActivityNotFoundException) {
                pendingTarget = null
                showMessage(R.string.file_picker_unavailable)
            }
        } else {
            pendingTarget = null
            applyBackground(target, uri)
        }
    }

    fun pick(target: String) {
        pendingTarget = target
        val mime = if (target == "video") "video/*" else "image/*"
        try {
            pickImageLauncher.launch(mime)
        } catch (e: ActivityNotFoundException) {
            pendingTarget = null
            showMessage(R.string.file_picker_unavailable)
        }
    }

    val clearDialog = rememberConfirmDialog(
        onConfirm = {
            scope.launch {
                loadingDialog.show()
                BackgroundManager.clearCustomBackground(context)
                loadingDialog.hide()
                showMessage(R.string.settings_background_image_cleared)
            }
        },
    )

    val clearVideoDialog = rememberConfirmDialog(
        onConfirm = {
            scope.launch {
                loadingDialog.show()
                BackgroundManager.clearVideoBackground(context)
                BackgroundConfig.setVideoBackgroundEnabledState(false)
                BackgroundConfig.save(context)
                loadingDialog.hide()
                showMessage(R.string.video_background_cleared)
            }
        },
    )

    // Resolved once: stringResource must be called from the composable body.
    val selectedLabel = stringResource(R.string.settings_background_selected)

    FolkSettingsSectionGroup(title = stringResource(R.string.settings_appearance_background)) {
        item(key = "appearance_custom_background") {
            FolkSwitchPreference(
                title = stringResource(R.string.settings_custom_background),
                summary = stringResource(R.string.settings_custom_background_summary),
                icon = Icons.Outlined.Wallpaper,
                checked = BackgroundConfig.isCustomBackgroundEnabled,
                onCheckedChange = { enabled ->
                    BackgroundConfig.setCustomBackgroundEnabledState(enabled)
                    BackgroundConfig.save(context)
                },
            )
        }

        if (BackgroundConfig.isCustomBackgroundEnabled) {
            item(key = "appearance_background_dual_dim") {
                FolkSwitchPreference(
                    title = stringResource(R.string.settings_custom_background_dual_dim),
                    summary = stringResource(R.string.settings_custom_background_dual_dim_desc),
                    icon = Icons.Outlined.Contrast,
                    checked = BackgroundConfig.isDualBackgroundDimEnabled,
                    onCheckedChange = { enabled ->
                        BackgroundConfig.setDualBackgroundDimEnabledState(enabled)
                        BackgroundConfig.save(context)
                    },
                )
            }

            item(key = "appearance_background_opacity") {
                FolkSliderPreference(
                    title = stringResource(R.string.settings_custom_background_opacity),
                    value = BackgroundConfig.customBackgroundOpacity,
                    onValueChange = { BackgroundConfig.setCustomBackgroundOpacityValue(it) },
                    onValueChangeFinished = { BackgroundConfig.save(context) },
                )
            }

            item(key = "appearance_background_blur") {
                FolkSliderPreference(
                    title = stringResource(R.string.settings_custom_background_blur),
                    value = BackgroundConfig.customBackgroundBlur,
                    valueRange = 0f..50f,
                    valueFormat = { "${it.toInt()}" },
                    onValueChange = { BackgroundConfig.setCustomBackgroundBlurValue(it) },
                    onValueChangeFinished = { BackgroundConfig.save(context) },
                )
            }

            if (BackgroundConfig.isDualBackgroundDimEnabled) {
                item(key = "appearance_background_day_dim") {
                    FolkSliderPreference(
                        title = stringResource(R.string.settings_custom_background_day_dim),
                        value = BackgroundConfig.customBackgroundDayDim,
                        onValueChange = { BackgroundConfig.setCustomBackgroundDayDimValue(it) },
                        onValueChangeFinished = { BackgroundConfig.save(context) },
                    )
                }

                item(key = "appearance_background_night_dim") {
                    FolkSliderPreference(
                        title = stringResource(R.string.settings_custom_background_night_dim),
                        value = BackgroundConfig.customBackgroundNightDim,
                        onValueChange = { BackgroundConfig.setCustomBackgroundNightDimValue(it) },
                        onValueChangeFinished = { BackgroundConfig.save(context) },
                    )
                }
            } else {
                item(key = "appearance_background_dim") {
                    FolkSliderPreference(
                        title = stringResource(R.string.settings_custom_background_dim),
                        value = BackgroundConfig.customBackgroundDim,
                        onValueChange = { BackgroundConfig.setCustomBackgroundDimValue(it) },
                        onValueChangeFinished = { BackgroundConfig.save(context) },
                    )
                }
            }

            item(key = "appearance_video_background") {
                FolkSwitchPreference(
                    title = stringResource(R.string.video_background),
                    summary = stringResource(R.string.video_background_summary),
                    icon = Icons.Outlined.Movie,
                    checked = BackgroundConfig.isVideoBackgroundEnabled,
                    onCheckedChange = { enabled ->
                        BackgroundConfig.setVideoBackgroundEnabledState(enabled)
                        BackgroundConfig.save(context)
                    },
                )
            }

            if (BackgroundConfig.isVideoBackgroundEnabled) {
                item(key = "appearance_select_video") {
                    FolkValuePreference(
                        title = stringResource(R.string.video_background_select),
                        summary = if (!BackgroundConfig.videoBackgroundUri.isNullOrEmpty()) {
                            selectedLabel
                        } else {
                            null
                        },
                        icon = Icons.Outlined.VideoFile,
                        onClick = { pick("video") },
                    )
                }

                if (!BackgroundConfig.videoBackgroundUri.isNullOrEmpty()) {
                    item(key = "appearance_clear_video") {
                        FolkValuePreference(
                            title = stringResource(R.string.video_background_clear),
                            icon = Icons.Outlined.Delete,
                            onClick = {
                                clearVideoDialog.showConfirm(
                                    title = context.getString(R.string.video_background_clear),
                                    content = context.getString(R.string.video_background_clear_confirm),
                                )
                            },
                        )
                    }
                }

                item(key = "appearance_video_volume") {
                    FolkSliderPreference(
                        title = stringResource(R.string.video_background_volume),
                        value = BackgroundConfig.videoVolume,
                        onValueChange = { BackgroundConfig.setVideoVolumeValue(it) },
                        onValueChangeFinished = { BackgroundConfig.save(context) },
                    )
                }
            }

            item(key = "appearance_multi_background") {
                FolkSwitchPreference(
                    title = stringResource(R.string.settings_multi_background_mode),
                    summary = stringResource(R.string.settings_multi_background_mode_summary),
                    icon = Icons.Outlined.GridView,
                    checked = BackgroundConfig.isMultiBackgroundEnabled,
                    onCheckedChange = { enabled ->
                        BackgroundConfig.setMultiBackgroundEnabledState(enabled)
                        BackgroundConfig.save(context)
                    },
                )
            }

            if (BackgroundConfig.isMultiBackgroundEnabled) {
                item(key = "appearance_page_wallpapers") {
                    PageWallpaperRow(
                        title = R.string.settings_select_home_background,
                        uri = BackgroundConfig.homeBackgroundUri,
                        onSelect = { pick("home") },
                    )
                    PageWallpaperRow(
                        title = R.string.settings_select_superuser_background,
                        uri = BackgroundConfig.superuserBackgroundUri,
                        onSelect = { pick("superuser") },
                    )
                    PageWallpaperRow(
                        title = R.string.settings_select_module_background,
                        uri = BackgroundConfig.moduleBackgroundUri,
                        onSelect = { pick("module") },
                    )
                    PageWallpaperRow(
                        title = R.string.settings_select_settings_background,
                        uri = BackgroundConfig.settingsBackgroundUri,
                        onSelect = { pick("settings") },
                    )
                }
            } else {
                item(key = "appearance_select_background") {
                    FolkValuePreference(
                        title = stringResource(R.string.settings_select_background_image),
                        summary = if (!BackgroundConfig.customBackgroundUri.isNullOrEmpty()) {
                            selectedLabel
                        } else {
                            null
                        },
                        icon = Icons.Outlined.Image,
                        onClick = { pick("default") },
                    )
                }

                if (!BackgroundConfig.customBackgroundUri.isNullOrEmpty()) {
                    item(key = "appearance_clear_background") {
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
}

/** One row of the multi-background picker, showing whether that page already has an image. */
@Composable
private fun PageWallpaperRow(
    @StringRes title: Int,
    uri: String?,
    onSelect: () -> Unit,
) {
    FolkValuePreference(
        title = stringResource(title),
        summary = if (!uri.isNullOrEmpty()) {
            stringResource(R.string.settings_background_selected)
        } else {
            null
        },
        icon = Icons.Outlined.Image,
        onClick = onSelect,
    )
}
