package com.sukisu.ultra.ui.screen.settings

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.VolumeUp
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
import com.sukisu.ultra.ui.theme.MusicConfig
import com.sukisu.ultra.ui.util.MusicManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The background-music rows, ported from FolkPatch `ui/screen/settings/MultimediaMusicSection.kt`.
 *
 * Every change is persisted through [MusicConfig] and pushed to the running player through
 * [MusicManager], so the effect is audible immediately rather than after a restart.
 */
@Composable
fun MultimediaMusicSection(
    snackBarHost: SnackbarHostState,
    loadingDialog: LoadingDialogHandle,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val showMessage: (Int) -> Unit = { resId ->
        val message = context.getString(resId)
        scope.launch { snackBarHost.showSnackbar(message) }
    }

    val musicLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                loadingDialog.show()
                // Copying a multi-megabyte track is file IO; the main dispatcher only owns the
                // dialog and the toast around it.
                val ok = withContext(Dispatchers.IO) { MusicConfig.saveMusicFile(context, uri) }
                loadingDialog.hide()
                if (ok) {
                    // saveMusicFile turns the feature on, so the file just picked is meant to
                    // be heard now rather than after the next foreground transition.
                    MusicManager.play()
                    showMessage(R.string.music_saved)
                } else {
                    showMessage(R.string.music_save_failed)
                }
            }
        }
    }

    val clearDialog = rememberConfirmDialog(
        onConfirm = {
            scope.launch {
                withContext(Dispatchers.IO) { MusicConfig.clearMusic(context) }
                MusicManager.reload()
                showMessage(R.string.music_cleared)
            }
        },
    )

    fun pickMusic() {
        try {
            musicLauncher.launch("audio/*")
        } catch (e: ActivityNotFoundException) {
            showMessage(R.string.file_picker_unavailable)
        }
    }

    val hasFile = !MusicConfig.musicFilename.isNullOrEmpty()

    FolkSettingsSectionGroup(title = stringResource(R.string.music_section_title)) {
        item(key = "music_enabled") {
            FolkSwitchPreference(
                title = stringResource(R.string.music_enabled),
                summary = stringResource(R.string.music_enabled_summary),
                icon = Icons.Outlined.MusicNote,
                checked = MusicConfig.isMusicEnabled,
                onCheckedChange = { enabled ->
                    MusicConfig.setMusicEnabledState(enabled)
                    MusicConfig.save(context)
                    // Turning the switch on is an explicit request to hear it; "auto-play"
                    // governs resuming on foreground, not whether the switch takes effect.
                    if (enabled) MusicManager.play() else MusicManager.reload()
                },
            )
        }

        if (MusicConfig.isMusicEnabled) {
            item(key = "music_select") {
                FolkValuePreference(
                    title = stringResource(R.string.music_select),
                    summary = if (hasFile) stringResource(R.string.music_selected) else null,
                    icon = Icons.Outlined.LibraryMusic,
                    onClick = { pickMusic() },
                )
            }

            if (hasFile) {
                item(key = "music_clear") {
                    FolkValuePreference(
                        title = stringResource(R.string.music_clear),
                        icon = Icons.Outlined.Delete,
                        onClick = {
                            clearDialog.showConfirm(
                                title = context.getString(R.string.music_clear),
                                content = context.getString(R.string.music_clear_confirm),
                            )
                        },
                    )
                }

                item(key = "music_autoplay") {
                    FolkSwitchPreference(
                        title = stringResource(R.string.music_autoplay),
                        checked = MusicConfig.isAutoPlayEnabled,
                        onCheckedChange = { enabled ->
                            MusicConfig.setAutoPlayEnabledState(enabled)
                            MusicConfig.save(context)
                            MusicManager.reload()
                        },
                    )
                }

                item(key = "music_looping") {
                    FolkSwitchPreference(
                        title = stringResource(R.string.music_looping),
                        icon = Icons.Outlined.Repeat,
                        checked = MusicConfig.isLoopingEnabled,
                        onCheckedChange = { enabled ->
                            MusicConfig.setLoopingEnabledState(enabled)
                            MusicConfig.save(context)
                            MusicManager.updateLooping(enabled)
                        },
                    )
                }

                item(key = "music_volume") {
                    FolkSliderPreference(
                        title = stringResource(R.string.music_volume),
                        icon = Icons.Outlined.VolumeUp,
                        value = MusicConfig.volume,
                        onValueChange = { MusicConfig.setVolumeValue(it) },
                        onValueChangeFinished = {
                            MusicConfig.save(context)
                            MusicManager.updateVolume(MusicConfig.volume)
                        },
                    )
                }
            }
        }
    }
}
