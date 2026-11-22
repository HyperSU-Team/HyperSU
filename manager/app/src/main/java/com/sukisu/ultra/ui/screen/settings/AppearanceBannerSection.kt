package com.sukisu.ultra.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
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
import com.sukisu.ultra.ui.component.folk.FolkAlertDialog
import com.sukisu.ultra.ui.component.folk.FolkButtonDefaults
import com.sukisu.ultra.ui.component.folk.FolkSettingsSectionGroup
import com.sukisu.ultra.ui.component.folk.FolkSliderPreference
import com.sukisu.ultra.ui.component.folk.FolkSwitchPreference
import com.sukisu.ultra.ui.component.folk.FolkValuePreference
import com.sukisu.ultra.ui.theme.BackgroundConfig
import com.sukisu.ultra.ui.theme.tokens.FolkType
import com.sukisu.ultra.ui.util.ModuleBanner
import kotlinx.coroutines.launch

/**
 * 模块卡片横幅的设置项，移植自 FolkPatch
 * `ui/screen/settings/appearance/AppearanceBannerSection.kt`。
 *
 * 与原版的差别：去掉了「ApiMarketplace」那一行（它需要独立的 Route 与页面壳，属于商店类
 * 功能），其余开关、API 来源配置、透明度覆盖全部保留。
 */
@Composable
fun AppearanceBannerSection(
    snackBarHost: SnackbarHostState,
    loadingDialog: LoadingDialogHandle,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val showMessage: (Int) -> Unit = { resId ->
        val message = context.getString(resId)
        scope.launch { snackBarHost.showSnackbar(message) }
    }

    var showSourceDialog by remember { mutableStateOf(false) }
    val save = { BackgroundConfig.save(context) }

    FolkSettingsSectionGroup(title = stringResource(R.string.banner_section_title)) {
        item(key = "banner_enabled") {
            FolkSwitchPreference(
                title = stringResource(R.string.banner_enabled),
                summary = stringResource(R.string.banner_enabled_summary),
                icon = Icons.Outlined.Campaign,
                checked = BackgroundConfig.isBannerEnabled,
                onCheckedChange = { enabled ->
                    BackgroundConfig.setBannerEnabledState(enabled)
                    save()
                },
            )
        }

        if (BackgroundConfig.isBannerEnabled) {
            item(key = "banner_folk_enabled") {
                FolkSwitchPreference(
                    title = stringResource(R.string.banner_folk_enabled),
                    summary = stringResource(R.string.banner_folk_enabled_summary),
                    icon = Icons.Outlined.Image,
                    checked = BackgroundConfig.isFolkBannerEnabled,
                    onCheckedChange = { enabled ->
                        BackgroundConfig.setFolkBannerEnabledState(enabled)
                        ModuleBanner.invalidateAll()
                        save()
                    },
                )
            }

            if (BackgroundConfig.isFolkBannerEnabled) {
                item(key = "banner_api_mode") {
                    FolkSwitchPreference(
                        title = stringResource(R.string.banner_api_mode),
                        summary = stringResource(R.string.banner_api_mode_summary),
                        icon = Icons.Outlined.Api,
                        checked = BackgroundConfig.isBannerApiModeEnabled,
                        onCheckedChange = { enabled ->
                            BackgroundConfig.setBannerApiModeEnabledState(enabled)
                            save()
                        },
                    )
                }

                if (BackgroundConfig.isBannerApiModeEnabled) {
                    item(key = "banner_api_source") {
                        val source = BackgroundConfig.getEffectiveBannerApiSource()
                        val summary = when {
                            source.isBlank() -> stringResource(R.string.banner_api_source_not_configured)
                            source.startsWith("/") ->
                                stringResource(R.string.banner_api_source_configured_dir)
                            else -> stringResource(R.string.banner_api_source_configured_url)
                        }

                        FolkValuePreference(
                            title = stringResource(R.string.banner_api_source),
                            summary = summary,
                            icon = Icons.Outlined.Api,
                            onClick = { showSourceDialog = true },
                        )
                    }

                    if (BackgroundConfig.bannerApiSource.isNotBlank()) {
                        item(key = "banner_cache_clear") {
                            FolkValuePreference(
                                title = stringResource(R.string.banner_cache_clear),
                                icon = Icons.Outlined.Delete,
                                onClick = {
                                    scope.launch {
                                        loadingDialog.show()
                                        ModuleBanner.clearApiCache(context)
                                        loadingDialog.hide()
                                        showMessage(R.string.banner_cache_cleared)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            item(key = "banner_custom_opacity") {
                FolkSwitchPreference(
                    title = stringResource(R.string.banner_custom_opacity),
                    summary = stringResource(R.string.banner_custom_opacity_summary),
                    icon = Icons.Outlined.Image,
                    checked = BackgroundConfig.isBannerCustomOpacityEnabled,
                    onCheckedChange = { enabled ->
                        BackgroundConfig.setBannerCustomOpacityEnabledState(enabled)
                        save()
                    },
                )
            }

            if (BackgroundConfig.isBannerCustomOpacityEnabled) {
                item(key = "banner_opacity") {
                    FolkSliderPreference(
                        title = stringResource(R.string.banner_opacity),
                        value = BackgroundConfig.bannerCustomOpacity,
                        onValueChange = { BackgroundConfig.setBannerCustomOpacityValue(it) },
                        onValueChangeFinished = save,
                    )
                }
            }
        }
    }

    if (showSourceDialog) {
        BannerSourceDialog(
            initial = BackgroundConfig.bannerApiSource,
            onDismiss = { showSourceDialog = false },
            onConfirm = { newSource ->
                showSourceDialog = false
                BackgroundConfig.setBannerApiSourceValue(newSource.trim())
                save()
                // 换源之后旧源的缓存全部作废，否则会继续显示上一个源的图。
                scope.launch {
                    loadingDialog.show()
                    ModuleBanner.clearApiCache(context)
                    loadingDialog.hide()
                }
            },
        )
    }
}

/**
 * 填 API 来源的对话框。**以 `/` 开头按本地目录处理**，其余按随机图接口地址处理。
 */
@Composable
private fun BannerSourceDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val hint = stringResource(R.string.banner_api_source_hint)
    val invalid = stringResource(R.string.banner_api_source_invalid)

    // Keyed on the initial value: the dialog is reused, and without the key it showed the source
    // it was first opened with rather than the one now stored.
    var source by remember(initial) { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }

    FolkAlertDialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.banner_api_source_title),
                style = FolkType.Title,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.banner_api_source_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            OutlinedTextField(
                value = source,
                onValueChange = {
                    source = it
                    error = null
                },
                label = { Text(hint) },
                isError = error != null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (error != null) {
                Text(
                    text = invalid,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
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
                    onClick = {
                        if (source.isBlank()) {
                            error = invalid
                        } else {
                            onConfirm(source)
                        }
                    },
                    colors = FolkButtonDefaults.textColors(),
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    }
}
