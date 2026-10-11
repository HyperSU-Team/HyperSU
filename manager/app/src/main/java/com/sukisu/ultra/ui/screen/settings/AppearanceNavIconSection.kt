package com.sukisu.ultra.ui.screen.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Navigation
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
import com.sukisu.ultra.ui.theme.BottomBarIconConfig
import kotlinx.coroutines.launch

/**
 * Custom images for the bottom navigation icons, ported from FolkPatch.
 *
 * One row per tab plus a master switch. The images are square-cropped by the picker before they
 * are stored, because a nav icon is drawn in a 22dp box and a non-square image would be fitted
 * into it with letterboxing.
 */
@Composable
fun AppearanceNavIconSection(
    snackBarHost: SnackbarHostState,
    loadingDialog: LoadingDialogHandle,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Which tab the pending pick belongs to.
    var pendingDestination by remember { mutableStateOf<String?>(null) }

    val showMessage: (Int) -> Unit = { resId ->
        val message = context.getString(resId)
        scope.launch { snackBarHost.showSnackbar(message) }
    }

    // Cropped square before saving: a nav icon is drawn in a 22dp box, so a non-square picture
    // would be fitted into it with letterboxing and read as smaller than the glyphs beside it.
    val cropLauncher = rememberLauncherForActivityResult(
        CropBackgroundContract(),
    ) { cropped: Uri? ->
        val destination = pendingDestination
        pendingDestination = null
        if (cropped == null || destination == null) {
            CropBackgroundContract.clearCache(context)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            loadingDialog.show()
            val saved = BottomBarIconConfig.saveCustomIcon(context, destination, cropped)
            loadingDialog.hide()
            showMessage(if (saved) R.string.nav_icon_set else R.string.nav_icon_set_failed)
            CropBackgroundContract.clearCache(context)
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                cropLauncher.launch(
                    CropBackgroundContract.Input(
                        source = uri,
                        aspectRatioX = 1f,
                        aspectRatioY = 1f,
                    ),
                )
            }.onFailure {
                pendingDestination = null
                showMessage(R.string.file_picker_unavailable)
            }
        }
    }

    val clearDialog = rememberConfirmDialog(
        onConfirm = {
            val destination = pendingDestination
            pendingDestination = null
            if (destination != null) {
                BottomBarIconConfig.clearCustomIcon(context, destination)
                showMessage(R.string.nav_icon_cleared)
            }
        },
    )

    // Read so this screen recomposes when an icon or the switch changes; `enabled` is derived
    // from it rather than read once, since the switch can be flipped from here.
    val revision = BottomBarIconConfig.revision
    val enabled = remember(revision) { BottomBarIconConfig.isEnabled(context) }

    FolkSettingsSectionGroup(title = stringResource(R.string.nav_icon_section_title)) {
        item(key = "nav_icon_enabled") {
            FolkSwitchPreference(
                title = stringResource(R.string.nav_icon_enabled),
                summary = stringResource(R.string.nav_icon_enabled_summary),
                icon = Icons.Outlined.Navigation,
                checked = enabled,
                onCheckedChange = { BottomBarIconConfig.setEnabled(context, it) },
            )
        }

        if (enabled) {
            // A plain read, not `remember`: this body only registers row descriptors, and the
            // section group runs it outside any composable scope.
            NavIconRows.forEach { (destination, labelRes) ->
                val hasIcon = BottomBarIconConfig.getCustomIconUri(context, destination) != null
                NavIconRow(
                    destination = destination,
                    labelRes = labelRes,
                    hasIcon = hasIcon,
                    onPick = {
                        pendingDestination = destination
                        runCatching { pickLauncher.launch("image/*") }
                            .onFailure {
                                pendingDestination = null
                                showMessage(R.string.file_picker_unavailable)
                            }
                    },
                    onClear = {
                        pendingDestination = destination
                        clearDialog.showConfirm(
                            title = context.getString(R.string.nav_icon_clear),
                            content = context.getString(R.string.nav_icon_clear_confirm),
                        )
                    },
                )
            }
        }
    }
}

/**
 * One destination's rows: pick an icon, and remove the current one.
 *
 * Not `@Composable`, and it must not be: [FolkSettingsSectionGroup] runs its content lambda
 * immediately to collect the row descriptors, so this body is plain code that only registers
 * `item`s. The `@Composable` work - `stringResource`, the preference reads - happens inside each
 * `item` lambda, which is composable. Marking this function composable made every call in it a
 * composable invocation from a non-composable scope.
 */
private fun FolkSettingsGroupScope.NavIconRow(
    destination: String,
    @StringRes labelRes: Int,
    hasIcon: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    item(key = "nav_icon_$destination") {
        FolkValuePreference(
            title = stringResource(labelRes),
            summary = if (hasIcon) {
                stringResource(R.string.nav_icon_set)
            } else {
                stringResource(R.string.nav_icon_not_set)
            },
            icon = Icons.Outlined.Image,
            onClick = onPick,
        )
    }

    if (hasIcon) {
        item(key = "nav_icon_${destination}_clear") {
            FolkValuePreference(
                title = stringResource(R.string.nav_icon_clear),
                icon = Icons.Outlined.Delete,
                onClick = onClear,
            )
        }
    }
}

/**
 * The tabs a custom icon can be set for, paired with their labels.
 *
 * The names are FolkPatch's, and match the preference keys and archive entry names, so a theme
 * exported there restores its icons here.
 */
private val NavIconRows: List<Pair<String, Int>> = listOf(
    "Home" to R.string.nav_icon_home,
    "SuperUser" to R.string.nav_icon_superuser,
    "AModule" to R.string.nav_icon_module,
    "Settings" to R.string.nav_icon_settings,
)
