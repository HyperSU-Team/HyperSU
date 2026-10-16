package com.sukisu.ultra.ui.screen.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.ui.res.stringResource
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.folk.FolkSettingsGroupScope
import com.sukisu.ultra.ui.component.folk.FolkSliderPreference
import com.sukisu.ultra.ui.component.folk.FolkSwitchPreference

/**
 * The dim / opacity controls shared by every card wallpaper (Focus hero card and the four
 * Dashboard tiles), ported from FolkPatch `ui/component/DualBackgroundSettings.kt`.
 *
 * Offered as an extension on [FolkSettingsGroupScope] so it emits `item`s directly into the
 * surrounding section instead of nesting a group. Dual dim and dual opacity are independent:
 * one may split by day/night while the other stays a single value.
 *
 * Every change calls [onSaved] on completion so the preference file is never left one step
 * behind what the slider showed.
 */
internal fun FolkSettingsGroupScope.CardWallpaperControls(
    keyPrefix: String,
    dualDimEnabled: Boolean,
    onDualDimChange: (Boolean) -> Unit,
    dim: Float,
    onDimChange: (Float) -> Unit,
    dayDim: Float,
    onDayDimChange: (Float) -> Unit,
    nightDim: Float,
    onNightDimChange: (Float) -> Unit,
    dualOpacityEnabled: Boolean,
    onDualOpacityChange: (Boolean) -> Unit,
    opacity: Float,
    onOpacityChange: (Float) -> Unit,
    dayOpacity: Float,
    onDayOpacityChange: (Float) -> Unit,
    nightOpacity: Float,
    onNightOpacityChange: (Float) -> Unit,
    onSaved: () -> Unit,
) {
    item(key = "${keyPrefix}_dual_dim") {
        FolkSwitchPreference(
            title = stringResource(R.string.card_dual_dim_title),
            summary = stringResource(R.string.card_dual_dim_desc),
            icon = Icons.Outlined.Contrast,
            checked = dualDimEnabled,
            onCheckedChange = {
                onDualDimChange(it)
                onSaved()
            },
        )
    }

    if (dualDimEnabled) {
        item(key = "${keyPrefix}_day_dim") {
            FolkSliderPreference(
                title = stringResource(R.string.card_day_dim),
                value = dayDim,
                onValueChange = onDayDimChange,
                onValueChangeFinished = onSaved,
            )
        }

        item(key = "${keyPrefix}_night_dim") {
            FolkSliderPreference(
                title = stringResource(R.string.card_night_dim),
                value = nightDim,
                onValueChange = onNightDimChange,
                onValueChangeFinished = onSaved,
            )
        }
    } else {
        item(key = "${keyPrefix}_dim") {
            FolkSliderPreference(
                title = stringResource(R.string.card_dim),
                value = dim,
                onValueChange = onDimChange,
                onValueChangeFinished = onSaved,
            )
        }
    }

    item(key = "${keyPrefix}_dual_opacity") {
        FolkSwitchPreference(
            title = stringResource(R.string.card_dual_opacity_title),
            summary = stringResource(R.string.card_dual_opacity_desc),
            icon = Icons.Outlined.Contrast,
            checked = dualOpacityEnabled,
            onCheckedChange = {
                onDualOpacityChange(it)
                onSaved()
            },
        )
    }

    if (dualOpacityEnabled) {
        item(key = "${keyPrefix}_day_opacity") {
            FolkSliderPreference(
                title = stringResource(R.string.card_day_opacity),
                value = dayOpacity,
                onValueChange = onDayOpacityChange,
                onValueChangeFinished = onSaved,
            )
        }

        item(key = "${keyPrefix}_night_opacity") {
            FolkSliderPreference(
                title = stringResource(R.string.card_night_opacity),
                value = nightOpacity,
                onValueChange = onNightOpacityChange,
                onValueChangeFinished = onSaved,
            )
        }
    } else {
        item(key = "${keyPrefix}_opacity") {
            FolkSliderPreference(
                title = stringResource(R.string.card_opacity),
                value = opacity,
                onValueChange = onOpacityChange,
                onValueChangeFinished = onSaved,
            )
        }
    }
}
