package com.sukisu.ultra.ui.screen.settings

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.dialog.LoadingDialogHandle
import com.sukisu.ultra.ui.component.dialog.rememberConfirmDialog
import com.sukisu.ultra.ui.component.folk.FolkSettingsGroupScope
import com.sukisu.ultra.ui.component.folk.FolkSettingsSectionGroup
import com.sukisu.ultra.ui.component.folk.FolkSwitchPreference
import com.sukisu.ultra.ui.component.folk.FolkValuePreference
import com.sukisu.ultra.ui.screen.themeSettings.crop.CropBackgroundContract
import com.sukisu.ultra.ui.theme.BackgroundConfig
import com.sukisu.ultra.ui.theme.BackgroundManager
import kotlinx.coroutines.launch

/**
 * The Dashboard layout's tile wallpapers, ported from FolkPatch
 * `ui/screen/settings/appearance/AppearanceDashboardCardSection.kt`.
 *
 * FolkPatch's Dashboard is one hero card with one image; HyperSU's Dashboard is four status
 * tiles, so the four Focus cards of the original map onto these four tiles. The image is
 * per-tile, while dim and opacity are shared by all four - splitting those by tile would make
 * the row list three times longer for no visible gain.
 */
@Composable
fun AppearanceDashboardCardSection(
    snackBarHost: SnackbarHostState,
    loadingDialog: LoadingDialogHandle,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Which tile the pending pick / clear belongs to.
    var pendingTile by remember { mutableStateOf<String?>(null) }

    val showMessage: (Int) -> Unit = { resId ->
        val message = context.getString(resId)
        scope.launch { snackBarHost.showSnackbar(message) }
    }

    // Cropped before saving, so the user frames each tile image rather than the app
    // centre-cropping it. pendingTile stays set across the crop, since the crop result is
    // what completes the pick.
    val cropLauncher = rememberLauncherForActivityResult(
        CropBackgroundContract(),
    ) { cropped: Uri? ->
        val tile = pendingTile
        pendingTile = null
        if (cropped == null || tile == null) {
            CropBackgroundContract.clearCache(context)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            loadingDialog.show()
            val success = BackgroundManager.saveAndApplyDashboardTileBackground(context, tile, cropped)
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
        val tile = pendingTile
        if (uri == null || tile == null) {
            pendingTile = null
            return@rememberLauncherForActivityResult
        }
        runCatching { cropLauncher.launch(CropBackgroundContract.Input(source = uri)) }
            .onFailure {
                pendingTile = null
                showMessage(R.string.file_picker_unavailable)
            }
    }

    val clearDialog = rememberConfirmDialog(
        onConfirm = {
            val tile = pendingTile
            pendingTile = null
            if (tile != null) {
                scope.launch {
                    loadingDialog.show()
                    BackgroundManager.clearDashboardTileBackground(context, tile)
                    loadingDialog.hide()
                    showMessage(R.string.settings_background_image_cleared)
                }
            }
        },
    )

    val save = { BackgroundConfig.save(context) }

    fun pick(tile: String) {
        pendingTile = tile
        try {
            pickLauncher.launch("image/*")
        } catch (e: ActivityNotFoundException) {
            pendingTile = null
            showMessage(R.string.file_picker_unavailable)
        }
    }

    FolkSettingsSectionGroup(title = stringResource(R.string.dashboard_card_section_title)) {
        item(key = "dashboard_card_background_enabled") {
            FolkSwitchPreference(
                title = stringResource(R.string.dashboard_card_background),
                summary = stringResource(R.string.dashboard_card_background_summary),
                icon = Icons.Outlined.Wallpaper,
                checked = BackgroundConfig.isDashboardCardBackgroundEnabled,
                onCheckedChange = { enabled ->
                    BackgroundConfig.setDashboardCardBackgroundEnabledState(enabled)
                    save()
                },
            )
        }

        if (BackgroundConfig.isDashboardCardBackgroundEnabled) {
            CardWallpaperControls(
                keyPrefix = "dashboard_card",
                dualDimEnabled = BackgroundConfig.isDashboardCardDualDimEnabled,
                onDualDimChange = { BackgroundConfig.setDashboardCardDualDimEnabledState(it) },
                dim = BackgroundConfig.dashboardCardBgDim,
                onDimChange = { BackgroundConfig.setDashboardCardBgDimValue(it) },
                dayDim = BackgroundConfig.dashboardCardBgDayDim,
                onDayDimChange = { BackgroundConfig.setDashboardCardBgDayDimValue(it) },
                nightDim = BackgroundConfig.dashboardCardBgNightDim,
                onNightDimChange = { BackgroundConfig.setDashboardCardBgNightDimValue(it) },
                dualOpacityEnabled = BackgroundConfig.isDashboardCardDualOpacityEnabled,
                onDualOpacityChange = { BackgroundConfig.setDashboardCardDualOpacityEnabledState(it) },
                opacity = BackgroundConfig.dashboardCardBgOpacity,
                onOpacityChange = { BackgroundConfig.setDashboardCardBgOpacityValue(it) },
                dayOpacity = BackgroundConfig.dashboardCardBgDayOpacity,
                onDayOpacityChange = { BackgroundConfig.setDashboardCardBgDayOpacityValue(it) },
                nightOpacity = BackgroundConfig.dashboardCardBgNightOpacity,
                onNightOpacityChange = { BackgroundConfig.setDashboardCardBgNightOpacityValue(it) },
                onSaved = save,
            )

            TileRow(
                key = "dashboard_tile_working",
                title = R.string.dashboard_tile_working,
                uri = BackgroundConfig.getDashboardTileBgUri(BackgroundConfig.DASHBOARD_TILE_WORKING),
                onPick = { pick(BackgroundConfig.DASHBOARD_TILE_WORKING) },
                onClear = {
                    pendingTile = BackgroundConfig.DASHBOARD_TILE_WORKING
                    clearDialog.showConfirm(
                        title = context.getString(R.string.settings_clear_background),
                        content = context.getString(R.string.settings_clear_background_confirm),
                    )
                },
            )
            TileRow(
                key = "dashboard_tile_selinux",
                title = R.string.dashboard_tile_selinux,
                uri = BackgroundConfig.getDashboardTileBgUri(BackgroundConfig.DASHBOARD_TILE_SELINUX),
                onPick = { pick(BackgroundConfig.DASHBOARD_TILE_SELINUX) },
                onClear = {
                    pendingTile = BackgroundConfig.DASHBOARD_TILE_SELINUX
                    clearDialog.showConfirm(
                        title = context.getString(R.string.settings_clear_background),
                        content = context.getString(R.string.settings_clear_background_confirm),
                    )
                },
            )
            TileRow(
                key = "dashboard_tile_zygisk",
                title = R.string.dashboard_tile_zygisk,
                uri = BackgroundConfig.getDashboardTileBgUri(BackgroundConfig.DASHBOARD_TILE_ZYGISK),
                onPick = { pick(BackgroundConfig.DASHBOARD_TILE_ZYGISK) },
                onClear = {
                    pendingTile = BackgroundConfig.DASHBOARD_TILE_ZYGISK
                    clearDialog.showConfirm(
                        title = context.getString(R.string.settings_clear_background),
                        content = context.getString(R.string.settings_clear_background_confirm),
                    )
                },
            )
            TileRow(
                key = "dashboard_tile_seccomp",
                title = R.string.dashboard_tile_seccomp,
                uri = BackgroundConfig.getDashboardTileBgUri(BackgroundConfig.DASHBOARD_TILE_SECCOMP),
                onPick = { pick(BackgroundConfig.DASHBOARD_TILE_SECCOMP) },
                onClear = {
                    pendingTile = BackgroundConfig.DASHBOARD_TILE_SECCOMP
                    clearDialog.showConfirm(
                        title = context.getString(R.string.settings_clear_background),
                        content = context.getString(R.string.settings_clear_background_confirm),
                    )
                },
            )
        }
    }
}

/** One tile's row: pick a wallpaper, and remove the current one without leaving the row. */
private fun FolkSettingsGroupScope.TileRow(
    key: String,
    @StringRes title: Int,
    uri: String?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    val selected = !uri.isNullOrEmpty()

    item(key = key) {
        FolkValuePreference(
            title = stringResource(title),
            summary = if (selected) stringResource(R.string.settings_background_selected) else null,
            icon = Icons.Outlined.Image,
            onClick = onPick,
        )
    }

    if (selected) {
        item(key = "${key}_clear") {
            FolkValuePreference(
                title = stringResource(R.string.settings_clear_background),
                icon = Icons.Outlined.Delete,
                onClick = onClear,
            )
        }
    }
}
