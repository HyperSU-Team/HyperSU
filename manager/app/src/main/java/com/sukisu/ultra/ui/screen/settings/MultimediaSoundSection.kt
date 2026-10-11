package com.sukisu.ultra.ui.screen.settings

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.VolumeUp
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
import com.sukisu.ultra.ui.component.folk.FolkSliderPreference
import com.sukisu.ultra.ui.component.folk.FolkSwitchPreference
import com.sukisu.ultra.ui.component.folk.FolkValuePreference
import com.sukisu.ultra.ui.theme.SoundEffectConfig
import com.sukisu.ultra.ui.theme.VibrationConfig
import kotlinx.coroutines.launch

/**
 * The click / startup sound rows, ported from FolkPatch
 * `ui/screen/settings/MultimediaSoundSection.kt`.
 *
 * Every change is persisted straight away so [com.sukisu.ultra.ui.util.SoundEffectManager] picks
 * it up on the next tap - the player reads the config rather than being told.
 */
@Composable
fun MultimediaSoundSection(
    snackBarHost: SnackbarHostState,
    loadingDialog: LoadingDialogHandle,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val showMessage: (Int) -> Unit = { resId ->
        val message = context.getString(resId)
        scope.launch { snackBarHost.showSnackbar(message) }
    }

    val soundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                loadingDialog.show()
                val ok = SoundEffectConfig.saveSoundEffectFile(context, uri)
                loadingDialog.hide()
                showMessage(if (ok) R.string.sound_saved else R.string.sound_save_failed)
            }
        }
    }

    val startupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                loadingDialog.show()
                val ok = SoundEffectConfig.saveStartupSoundFile(context, uri)
                loadingDialog.hide()
                showMessage(if (ok) R.string.sound_saved else R.string.sound_save_failed)
            }
        }
    }

    val clearDialog = rememberConfirmDialog(
        onConfirm = {
            SoundEffectConfig.clearSoundEffect(context)
            showMessage(R.string.sound_cleared)
        },
    )

    val clearStartupDialog = rememberConfirmDialog(
        onConfirm = {
            SoundEffectConfig.clearStartupSound(context)
            showMessage(R.string.sound_cleared)
        },
    )

    fun pick(launcher: androidx.activity.result.ActivityResultLauncher<String>) {
        try {
            // "*/*": file managers label .mp3/.ogg inconsistently, and a wrong label shows an
            // empty picker. A file that will not play fails in MediaPlayer and is ignored.
            launcher.launch("*/*")
        } catch (e: ActivityNotFoundException) {
            showMessage(R.string.file_picker_unavailable)
        }
    }

    val scopeOptions = listOf(
        stringResource(R.string.sound_scope_global),
        stringResource(R.string.sound_scope_bottom_bar),
    )
    val sourceOptions = listOf(
        stringResource(R.string.sound_source_local),
        stringResource(R.string.sound_source_preset),
    )

    FolkSettingsSectionGroup(title = stringResource(R.string.sound_section_title)) {
        item(key = "sound_enabled") {
            FolkSwitchPreference(
                title = stringResource(R.string.sound_enabled),
                summary = stringResource(R.string.sound_enabled_summary),
                icon = Icons.Outlined.VolumeUp,
                checked = SoundEffectConfig.isSoundEffectEnabled,
                onCheckedChange = { enabled ->
                    SoundEffectConfig.setEnabledState(enabled)
                    SoundEffectConfig.save(context)
                },
            )
        }

        if (SoundEffectConfig.isSoundEffectEnabled) {
            item(key = "sound_scope") {
                FolkChoicePreference(
                    title = stringResource(R.string.sound_scope_title),
                    icon = Icons.Outlined.Tune,
                    options = scopeOptions,
                    selectedIndex = if (SoundEffectConfig.scope == SoundEffectConfig.SCOPE_GLOBAL) 0 else 1,
                    onSelect = { index ->
                        SoundEffectConfig.setScopeValue(
                            if (index == 0) SoundEffectConfig.SCOPE_GLOBAL
                            else SoundEffectConfig.SCOPE_BOTTOM_BAR,
                        )
                        SoundEffectConfig.save(context)
                    },
                )
            }

            item(key = "sound_source") {
                val presetSelected = SoundEffectConfig.sourceType == SoundEffectConfig.SOURCE_TYPE_PRESET
                FolkChoicePreference(
                    title = stringResource(R.string.sound_source_title),
                    icon = Icons.Outlined.Audiotrack,
                    options = sourceOptions,
                    selectedIndex = if (presetSelected) 1 else 0,
                    onSelect = { index ->
                        SoundEffectConfig.setSourceTypeValue(
                            if (index == 1) SoundEffectConfig.SOURCE_TYPE_PRESET
                            else SoundEffectConfig.SOURCE_TYPE_LOCAL,
                        )
                        SoundEffectConfig.save(context)
                    },
                )
            }

            if (SoundEffectConfig.sourceType == SoundEffectConfig.SOURCE_TYPE_PRESET) {
                item(key = "sound_preset") {
                    FolkChoicePreference(
                        title = stringResource(R.string.sound_preset_title),
                        options = SoundEffectConfig.PRESETS,
                        selectedIndex = SoundEffectConfig.PRESETS
                            .indexOf(SoundEffectConfig.presetName)
                            .coerceAtLeast(0),
                        onSelect = { index ->
                            SoundEffectConfig.setPresetNameValue(SoundEffectConfig.PRESETS[index])
                            SoundEffectConfig.save(context)
                        },
                    )
                }
            } else {
                item(key = "sound_select") {
                    FolkValuePreference(
                        title = stringResource(R.string.sound_select),
                        summary = if (!SoundEffectConfig.soundEffectFilename.isNullOrEmpty()) {
                            stringResource(R.string.sound_selected)
                        } else {
                            null
                        },
                        icon = Icons.Outlined.PlayArrow,
                        onClick = { pick(soundLauncher) },
                    )
                }

                if (!SoundEffectConfig.soundEffectFilename.isNullOrEmpty()) {
                    item(key = "sound_clear") {
                        FolkValuePreference(
                            title = stringResource(R.string.sound_clear),
                            icon = Icons.Outlined.Delete,
                            onClick = {
                                clearDialog.showConfirm(
                                    title = context.getString(R.string.sound_clear),
                                    content = context.getString(R.string.sound_clear_confirm),
                                )
                            },
                        )
                    }
                }
            }
        }

        item(key = "sound_startup_enabled") {
            FolkSwitchPreference(
                title = stringResource(R.string.sound_startup_enabled),
                summary = stringResource(R.string.sound_startup_enabled_summary),
                icon = Icons.Outlined.PlayArrow,
                checked = SoundEffectConfig.isStartupSoundEnabled,
                onCheckedChange = { enabled ->
                    SoundEffectConfig.setStartupEnabledState(enabled)
                    SoundEffectConfig.save(context)
                },
            )
        }

        if (SoundEffectConfig.isStartupSoundEnabled) {
            item(key = "sound_startup_source") {
                val presetSelected =
                    SoundEffectConfig.startupSourceType == SoundEffectConfig.SOURCE_TYPE_PRESET
                FolkChoicePreference(
                    title = stringResource(R.string.sound_source_title),
                    icon = Icons.Outlined.Audiotrack,
                    options = sourceOptions,
                    selectedIndex = if (presetSelected) 1 else 0,
                    onSelect = { index ->
                        SoundEffectConfig.setStartupSourceTypeValue(
                            if (index == 1) SoundEffectConfig.SOURCE_TYPE_PRESET
                            else SoundEffectConfig.SOURCE_TYPE_LOCAL,
                        )
                        SoundEffectConfig.save(context)
                    },
                )
            }

            if (SoundEffectConfig.startupSourceType == SoundEffectConfig.SOURCE_TYPE_PRESET) {
                item(key = "sound_startup_preset") {
                    FolkChoicePreference(
                        title = stringResource(R.string.sound_preset_title),
                        options = SoundEffectConfig.STARTUP_PRESETS,
                        selectedIndex = SoundEffectConfig.STARTUP_PRESETS
                            .indexOf(SoundEffectConfig.startupPresetName)
                            .coerceAtLeast(0),
                        onSelect = { index ->
                            SoundEffectConfig.setStartupPresetNameValue(
                                SoundEffectConfig.STARTUP_PRESETS[index],
                            )
                            SoundEffectConfig.save(context)
                        },
                    )
                }
            } else {
                item(key = "sound_startup_select") {
                    FolkValuePreference(
                        title = stringResource(R.string.sound_select),
                        summary = if (!SoundEffectConfig.startupSoundFilename.isNullOrEmpty()) {
                            stringResource(R.string.sound_selected)
                        } else {
                            null
                        },
                        icon = Icons.Outlined.PlayArrow,
                        onClick = { pick(startupLauncher) },
                    )
                }

                if (!SoundEffectConfig.startupSoundFilename.isNullOrEmpty()) {
                    item(key = "sound_startup_clear") {
                        FolkValuePreference(
                            title = stringResource(R.string.sound_clear),
                            icon = Icons.Outlined.Delete,
                            onClick = {
                                clearStartupDialog.showConfirm(
                                    title = context.getString(R.string.sound_clear),
                                    content = context.getString(R.string.sound_clear_confirm),
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    val vibrationScopeOptions = listOf(
        stringResource(R.string.vibration_scope_global),
        stringResource(R.string.vibration_scope_bottom_bar),
    )

    FolkSettingsSectionGroup(title = stringResource(R.string.vibration_section_title)) {
        item(key = "vibration_enabled") {
            FolkSwitchPreference(
                title = stringResource(R.string.vibration_enabled),
                summary = stringResource(R.string.vibration_enabled_summary),
                icon = Icons.Outlined.Vibration,
                checked = VibrationConfig.isVibrationEnabled,
                onCheckedChange = { enabled ->
                    VibrationConfig.setEnabledState(enabled)
                    VibrationConfig.save(context)
                },
            )
        }

        if (VibrationConfig.isVibrationEnabled) {
            item(key = "vibration_scope") {
                FolkChoicePreference(
                    title = stringResource(R.string.vibration_scope_title),
                    icon = Icons.Outlined.Tune,
                    options = vibrationScopeOptions,
                    selectedIndex = if (VibrationConfig.scope == VibrationConfig.SCOPE_GLOBAL) 0 else 1,
                    onSelect = { index ->
                        VibrationConfig.setScopeValue(
                            if (index == 0) VibrationConfig.SCOPE_GLOBAL
                            else VibrationConfig.SCOPE_BOTTOM_BAR,
                        )
                        VibrationConfig.save(context)
                    },
                )
            }

            item(key = "vibration_intensity") {
                FolkSliderPreference(
                    title = stringResource(R.string.vibration_intensity),
                    value = VibrationConfig.vibrationIntensity,
                    // Written on release, not on every frame of the drag: the value is only read
                    // when a tap happens, so persisting mid-drag is a write per pixel for nothing.
                    onValueChange = { VibrationConfig.setIntensityValue(it) },
                    onValueChangeFinished = { VibrationConfig.save(context) },
                )
            }
        }
    }
}
