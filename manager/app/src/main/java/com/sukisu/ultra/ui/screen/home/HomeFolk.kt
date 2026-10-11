package com.sukisu.ultra.ui.screen.home

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.Natives
import com.sukisu.ultra.R
import com.sukisu.ultra.data.repository.HOME_LAYOUT_DASHBOARD
import com.sukisu.ultra.data.repository.HOME_LAYOUT_FOCUS
import com.sukisu.ultra.data.repository.HOME_LAYOUT_GRID
import com.sukisu.ultra.data.repository.HOME_LAYOUT_LIST
import com.sukisu.ultra.data.repository.HOME_LAYOUT_OPTIONS
import com.sukisu.ultra.data.repository.KEY_HOME_LAYOUT
import com.sukisu.ultra.data.repository.SettingsRepositoryImpl
import com.sukisu.ultra.ksuApp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberAsyncImagePainter
import com.sukisu.ultra.ui.component.dialog.rememberConfirmDialog
import com.sukisu.ultra.ui.component.folk.FolkFactsGroup
import com.sukisu.ultra.ui.component.folk.FolkNavigationPreference
import com.sukisu.ultra.ui.component.folk.FolkPreference
import com.sukisu.ultra.ui.component.folk.FolkScaffold
import com.sukisu.ultra.ui.component.folk.FolkSettingsGroup
import com.sukisu.ultra.ui.component.folk.FolkSeverity
import com.sukisu.ultra.ui.component.folk.FolkStatusDot
import com.sukisu.ultra.ui.component.folk.FolkTitleStyle
import com.sukisu.ultra.ui.component.folk.folkSeverityColor
import com.sukisu.ultra.ui.component.rebootlistpopup.RebootListPopup
import com.sukisu.ultra.ui.component.statustag.StatusTag
import com.sukisu.ultra.ui.theme.BackgroundConfig
import com.sukisu.ultra.ui.theme.BackgroundManager
import com.sukisu.ultra.ui.util.module.LatestVersionInfo
import com.sukisu.ultra.ui.theme.isInDarkTheme
import com.sukisu.ultra.ui.theme.tokens.FolkShape
import com.sukisu.ultra.ui.theme.tokens.FolkType

/**
 * Colours a card uses once it carries its own wallpaper: the container goes transparent so the
 * image reads through it, and the content flips to white, whose contrast the dim overlay then
 * guarantees. Accent pills keep their own pairing (e.g. `tertiaryContainer` with
 * `onTertiaryContainer`) and are left alone.
 *
 * [content]/[muted] are the card's own colours with no wallpaper, so the default appearance is
 * bit-for-bit what it was before.
 */
private data class CardPalette(
    val container: Color,
    val content: Color,
    val muted: Color,
)

private fun cardPalette(
    uri: String?,
    container: Color,
    content: Color,
    muted: Color = content,
): CardPalette =
    if (uri.isNullOrEmpty()) CardPalette(container, content, muted)
    else CardPalette(Color.Transparent, Color.White, Color.White.copy(alpha = 0.8f))

/**
 * The wallpaper layer, sized to whatever the card measures to.
 *
 * `matchParentSize` and not `fillMaxSize`: the card is wrap-content, and filling the maximum
 * would collapse it to nothing. The image must be the first child so it sits under the content.
 */
@Composable
private fun BoxScope.CardWallpaperLayer(uri: String?, dim: Float, opacity: Float) {
    if (uri.isNullOrEmpty()) return
    Image(
        painter = rememberAsyncImagePainter(model = uri),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .matchParentSize()
            .alpha(opacity),
    )
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(Color.Black.copy(alpha = dim)),
    )
}

/**
 * The Home tab in the FolkPatch design.
 *
 * The screen data is [HomeUiState], which the caller keeps producing exactly as
 * before; this file only decides how it is presented. Everything that says
 * something about the running kernel - the working/not-installed hero, the
 * warning stack, the update prompt and the fact list - reads from those fields,
 * so the layouts below are interchangeable views over one state rather than
 * four copies of the logic.
 *
 * Five layouts are offered (the user's choice is persisted through
 * `SettingsRepository.homeLayoutStyle`):
 *  - [HOME_LAYOUT_CIRCLE]: the flagship composition - a hero status card, core
 *    shortcuts and the full fact list.
 *  - [HOME_LAYOUT_LIST]: a denser list-first variant of the same information.
 *  - [HOME_LAYOUT_FOCUS]: the hero and warnings only, with the facts folded into
 *    a compact block.
 *  - [HOME_LAYOUT_DASHBOARD]: a grid of small status tiles above the facts.
 *  - [HOME_LAYOUT_GRID]: the hero next to a pair of small cards, above the facts.
 *
 * There is deliberately no stats/hardware-monitor layout.
 */
@Composable
internal fun HomePagerFolk(
    state: HomeUiState,
    actions: HomeActions,
    bottomInnerPadding: Dp,
) {
    val settingsRepo = remember { SettingsRepositoryImpl() }
    // Observed rather than remembered once: a theme import rewrites home_layout_style while
    // this screen is alive, and a plain remember would keep showing the old layout until the
    // app restarted.
    val prefs = remember { ksuApp.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var layout by remember { mutableStateOf(settingsRepo.homeLayoutStyle) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == KEY_HOME_LAYOUT) {
                layout = settingsRepo.homeLayoutStyle
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    FolkScaffold(
        title = stringResource(R.string.app_name),
        titleStyle = FolkTitleStyle.Inline,
        topBar = {
            HomeTopBar(
                state = state,
                actions = actions,
                layout = layout,
                onLayoutChange = { chosen ->
                    layout = chosen
                    settingsRepo.homeLayoutStyle = chosen
                },
            )
        },
    ) { innerPadding ->
        val contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 8.dp,
            bottom = innerPadding.calculateBottomPadding() +
                bottomInnerPadding +
                if (!Natives.isFullFeatured()) {
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                } else {
                    0.dp
                },
        )

        when (layout) {
            HOME_LAYOUT_LIST -> HomeLayoutList(state, actions, contentPadding)
            HOME_LAYOUT_FOCUS -> HomeLayoutFocus(state, actions, contentPadding)
            HOME_LAYOUT_DASHBOARD -> HomeLayoutDashboard(state, actions, contentPadding)
            HOME_LAYOUT_GRID -> HomeLayoutGrid(state, actions, contentPadding)
            else -> HomeLayoutCircle(state, actions, contentPadding)
        }
    }
}

// ---------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------

@Composable
private fun HomeTopBar(
    state: HomeUiState,
    actions: HomeActions,
    layout: String,
    onLayoutChange: (String) -> Unit,
) {
    var layoutMenu by remember { mutableStateOf(false) }

    androidx.compose.material3.TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        actions = {
            IconButton(onClick = actions.onInstallClick) {
                Icon(
                    imageVector = Icons.Filled.AutoFixHigh,
                    contentDescription = stringResource(R.string.home_click_to_install),
                )
            }

            if (state.ksuVersion != null) {
                RebootListPopup()
            }

            Box {
                IconButton(onClick = { layoutMenu = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.home_layout),
                    )
                }
                DropdownMenu(
                    expanded = layoutMenu,
                    onDismissRequest = { layoutMenu = false },
                ) {
                    HOME_LAYOUT_OPTIONS.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(homeLayoutLabel(option)) },
                            onClick = {
                                layoutMenu = false
                                onLayoutChange(option)
                            },
                            trailingIcon = {
                                if (option == layout) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                                }
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun homeLayoutLabel(option: String): String = stringResource(
    when (option) {
        HOME_LAYOUT_LIST -> R.string.home_layout_default
        HOME_LAYOUT_FOCUS -> R.string.home_layout_focus
        HOME_LAYOUT_DASHBOARD -> R.string.home_layout_dashboard
        HOME_LAYOUT_GRID -> R.string.home_layout_grid
        else -> R.string.home_layout_circle
    }
)

// ---------------------------------------------------------------------------
// Shared sections
// ---------------------------------------------------------------------------

/** The warning stack. Identical in every layout so the messages cannot drift. */
@Composable
private fun HomeWarnings(state: HomeUiState, actions: HomeActions) {
    if (state.showManagerPrBuildWarning && state.showFullStatus) {
        FolkStatusBannerMedium(stringResource(R.string.home_pr_build_warning), FolkSeverity.Caution)
    } else if (state.showKernelPrBuildWarning && state.showFullStatus) {
        FolkStatusBannerMedium(stringResource(R.string.home_pr_kernel_warning), FolkSeverity.Caution)
    }

    if (state.requiresNewKernel && state.showFullStatus) {
        FolkStatusBannerMedium(
            message = stringResource(
                if (state.lkmMode == true) R.string.require_kernel_version
                else R.string.require_kernel_version_gki
            ),
            severity = FolkSeverity.Critical,
            onClick = if (state.lkmMode == true) actions.onInstallClick else null,
        )
    }

    if (state.requiresNewManager) {
        FolkStatusBannerMedium(
            message = stringResource(R.string.require_manager_version),
            severity = FolkSeverity.Critical,
        )
    }

    if (state.showLkmUpdate && state.showFullStatus) {
        FolkStatusBannerMedium(
            message = stringResource(R.string.home_lkm_update_available),
            severity = FolkSeverity.Caution,
            onClick = actions.onInstallClick,
        )
    }

    if (state.showRootWarning) {
        FolkStatusBannerMedium(
            message = stringResource(R.string.grant_root_failed),
            severity = FolkSeverity.Critical,
        )
    }

    if (state.hasUpdate) {
        HomeUpdateBanner(state.latestVersionInfo, actions)
    }
}

/**
 * This fork has a newer release than the running manager.
 *
 * The changelog comes from the release body: tapping opens it in a dialog so the version can be
 * read before downloading, and a release without a body goes straight to the download.
 */
@Composable
private fun HomeUpdateBanner(latest: LatestVersionInfo, actions: HomeActions) {
    val title = stringResource(R.string.module_changelog)
    val updateText = stringResource(R.string.module_update)
    val dialog = rememberConfirmDialog(onConfirm = { actions.onOpenUrl(latest.downloadUrl) })

    FolkStatusBannerMedium(
        message = stringResource(R.string.new_version_available, latest.versionCode),
        severity = FolkSeverity.Info,
        onClick = {
            if (latest.changelog.isEmpty()) {
                actions.onOpenUrl(latest.downloadUrl)
            } else {
                dialog.showConfirm(
                    title = title,
                    content = latest.changelog,
                    markdown = true,
                    confirm = updateText,
                )
            }
        },
    )
}

/** A single-line message row in the Folk status colours. */
@Composable
private fun FolkStatusBannerMedium(
    message: String,
    severity: FolkSeverity,
    onClick: (() -> Unit)? = null,
) {
    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        shape = FolkShape.Corner16,
        color = folkSeverityColor(severity).copy(alpha = 0.14f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FolkStatusDot(severity)
            Spacer(Modifier.size(12.dp))
            Text(
                text = message,
                style = FolkType.Summary,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Hero status card
// ---------------------------------------------------------------------------

@Composable
private fun HomeHeroCard(
    state: HomeUiState,
    actions: HomeActions,
    wallpaperUri: String? = null,
) {
    when {
        state.ksuVersion != null -> HomeWorkingCard(state, actions, wallpaperUri)
        // Root works, so a driver is loaded; the kernel just does not recognise this manager.
        // Saying "no driver detected" here contradicted the superuser and module pages.
        state.isRootAvailable -> HomeUnsupportedCard(
            actions = actions,
            wallpaperUri = wallpaperUri,
            titleRes = R.string.home_manager_unrecognized,
            reasonRes = R.string.home_manager_unrecognized_reason,
        )
        state.kernelVersion.isGKI() -> HomeNotInstalledCard(state, actions, wallpaperUri)
        else -> HomeUnsupportedCard(actions, wallpaperUri)
    }
}

@Composable
private fun HomeWorkingCard(
    state: HomeUiState,
    actions: HomeActions,
    wallpaperUri: String? = null,
) {
    val markers = buildString {
        if (state.isSafeMode) append(" [${stringResource(R.string.safe_mode)}]")
        if (state.isLateLoadMode) append(" [${stringResource(R.string.jailbreak_mode)}]")
    }
    val mode = when (state.lkmMode) {
        null -> null
        true -> "LKM"
        else -> "Built-in"
    }
    val dark = isInDarkTheme()
    val palette = cardPalette(
        uri = wallpaperUri,
        container = MaterialTheme.colorScheme.secondaryContainer,
        content = MaterialTheme.colorScheme.onSecondaryContainer,
    )

    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!state.isLateLoadMode) {
                    Modifier.clickable(onClick = actions.onInstallClick)
                } else {
                    Modifier
                }
            ),
        shape = FolkShape.Corner28,
        color = palette.container,
    ) {
        Box {
            CardWallpaperLayer(
                uri = wallpaperUri,
                dim = BackgroundConfig.getEffectiveFocusCardBgDim(dark),
                opacity = BackgroundConfig.getEffectiveFocusCardBgOpacity(dark),
            )
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = palette.content,
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = "${stringResource(R.string.home_working)}$markers",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.content,
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(
                            R.string.home_working_version,
                            "${state.ksuVersion}-${state.kernelUAPIVersion}",
                        ),
                        style = FolkType.Summary,
                        color = palette.content,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (state.showCustomLkmBadge) {
                        Spacer(Modifier.size(8.dp))
                        StatusTag(
                            label = stringResource(R.string.home_lkm_custom),
                            backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }

                if (mode != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = mode,
                        style = FolkType.Caption,
                        color = palette.content.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeNotInstalledCard(
    state: HomeUiState,
    actions: HomeActions,
    wallpaperUri: String? = null,
) {
    val dark = isInDarkTheme()
    val palette = cardPalette(
        uri = wallpaperUri,
        container = MaterialTheme.colorScheme.surfaceContainerHigh,
        content = MaterialTheme.colorScheme.onSurface,
        muted = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (!state.isLateLoadMode) actions.onInstallClick()
            },
        shape = FolkShape.Corner28,
        color = palette.container,
    ) {
        Box {
            CardWallpaperLayer(
                uri = wallpaperUri,
                dim = BackgroundConfig.getEffectiveFocusCardBgDim(dark),
                opacity = BackgroundConfig.getEffectiveFocusCardBgOpacity(dark),
            )
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = if (wallpaperUri.isNullOrEmpty()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        palette.content
                    },
                )
                Spacer(Modifier.size(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_not_installed),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.content,
                    )
                    Text(
                        text = stringResource(R.string.home_click_to_install),
                        style = FolkType.Summary,
                        color = palette.muted,
                    )
                }
                if (state.isSELinuxPermissive) {
                    androidx.compose.material3.TextButton(onClick = actions.onJailbreakClick) {
                        Text(
                            text = stringResource(R.string.home_jailbreak),
                            color = palette.content,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeUnsupportedCard(
    actions: HomeActions,
    wallpaperUri: String? = null,
    titleRes: Int = R.string.home_unsupported,
    reasonRes: Int = R.string.home_unsupported_reason,
) {
    val dark = isInDarkTheme()
    val palette = cardPalette(
        uri = wallpaperUri,
        container = MaterialTheme.colorScheme.surfaceContainerHigh,
        content = MaterialTheme.colorScheme.onSurface,
        muted = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = actions.onInstallClick),
        shape = FolkShape.Corner28,
        color = palette.container,
    ) {
        Box {
            CardWallpaperLayer(
                uri = wallpaperUri,
                dim = BackgroundConfig.getEffectiveFocusCardBgDim(dark),
                opacity = BackgroundConfig.getEffectiveFocusCardBgOpacity(dark),
            )
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = if (wallpaperUri.isNullOrEmpty()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        palette.content
                    },
                )
                Spacer(Modifier.size(16.dp))
                Column {
                    Text(
                        text = stringResource(titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.content,
                    )
                    Text(
                        text = stringResource(reasonRes),
                        style = FolkType.Summary,
                        color = palette.muted,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Facts
// ---------------------------------------------------------------------------

@Composable
private fun HomeFacts(state: HomeUiState) {
    val systemInfo = state.systemInfo

    val selinuxDisplay = when (systemInfo.selinuxStatus) {
        "Enforcing" -> stringResource(R.string.selinux_status_enforcing)
        "Permissive" -> stringResource(R.string.selinux_status_permissive)
        "Disabled" -> stringResource(R.string.selinux_status_disabled)
        else -> stringResource(R.string.selinux_status_unknown)
    }
    val seccompDisplay = when (systemInfo.seccompStatus) {
        -1 -> stringResource(R.string.seccomp_status_not_supported)
        0 -> stringResource(R.string.seccomp_status_disabled)
        1 -> stringResource(R.string.seccomp_status_strict)
        2 -> stringResource(R.string.seccomp_status_filter)
        else -> stringResource(R.string.seccomp_status_unknown)
    }

    val manualHookText = stringResource(R.string.manual_hook)
    val inlineHookText = stringResource(R.string.inline_hook)
    val tracepointHookText = stringResource(R.string.tracepoint_hook)
    val unknownHookText = stringResource(R.string.selinux_status_unknown)
    val susfsInfo = rememberSusfsInfo(manualHookText, inlineHookText)
    val isSusfsSupported = susfsInfo.status == SusfsStatus.Supported
    val hookTypeLabel = rememberHookTypeLabel(
        manualHookText,
        inlineHookText,
        tracepointHookText,
        unknownHookText,
    )

    FolkFactsGroup {
        fact(stringResource(R.string.home_manager_version), systemInfo.managerVersion)
        fact(stringResource(R.string.home_kernel), systemInfo.kernelVersion)

        if (state.showFullStatus) {
            if (!systemInfo.kernelFullVersion.isNullOrBlank()) {
                fact(stringResource(R.string.home_kernel_full_version), systemInfo.kernelFullVersion)
            }
            if (isSusfsSupported) {
                fact(stringResource(R.string.home_susfs_version), susfsInfo.detail)
            } else if (!hookTypeLabel.isNullOrBlank()) {
                fact(stringResource(R.string.hook_type), hookTypeLabel)
            }
            if (!systemInfo.zygiskImplementation.isNullOrBlank()) {
                fact(stringResource(R.string.home_zygisk_implementation), systemInfo.zygiskImplementation)
            }
        }

        fact(stringResource(R.string.home_device_model), systemInfo.deviceModel)
        fact(stringResource(R.string.home_fingerprint), systemInfo.fingerprint)
        fact(stringResource(R.string.home_selinux_status), selinuxDisplay)
        fact(stringResource(R.string.home_seccomp_status), seccompDisplay)
    }
}

/** The two quick links, as Folk rows. */
@Composable
private fun HomeSupportLinks(actions: HomeActions) {
    val learnMoreUrl = stringResource(R.string.home_learn_kernelsu_url)

    FolkSettingsGroup {
        item {
            FolkNavigationPreference(
                title = stringResource(R.string.home_support_title),
                summary = stringResource(R.string.home_support_content),
                icon = Icons.Outlined.Security,
                onClick = { actions.onOpenUrl("https://patreon.com/weishu") },
            )
        }
        item {
            FolkNavigationPreference(
                title = stringResource(R.string.home_learn_kernelsu),
                summary = stringResource(R.string.home_click_to_learn_kernelsu),
                icon = Icons.Outlined.Info,
                onClick = { actions.onOpenUrl(learnMoreUrl) },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Layouts
// ---------------------------------------------------------------------------

/** The flagship layout: hero, warnings, quick links, full facts. */
/**
 * 主卡片壁纸，凡是要画主状态卡的地方都用它（圆形 / 焦点 / 网格布局）。
 *
 * 配置键沿用移植过来的 `focus_card_*` 命名，但作用对象是**主卡片本身**，不是某个布局 ——
 * 同一张卡在不同布局里出现时不该换一张壁纸。
 */
private fun heroWallpaperUri(): String? =
    if (BackgroundConfig.isFocusCardBackgroundEnabled) BackgroundConfig.focusCardBgUri else null

/**
 * Grid: the hero card, then a pair of small cards side by side, then warnings and facts.
 *
 * FolkPatch's GridUI is built around KernelPatch / AndroidPatch patch states that this
 * manager has no model for (`APApplication.State`, `installApatch()`), so it cannot be ported
 * literally. What is kept is the *composition* - one large status card next to smaller ones -
 * rebuilt over the same [HomeUiState] every other layout reads.
 */
@Composable
private fun HomeLayoutGrid(
    state: HomeUiState,
    actions: HomeActions,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HomeHeroCard(state = state, actions = actions, wallpaperUri = heroWallpaperUri())

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HomeTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Info,
                label = stringResource(R.string.home_kernel),
                value = state.systemInfo.kernelVersion,
                severity = if (state.isManager) FolkSeverity.Positive else FolkSeverity.Critical,
            )
            HomeTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Security,
                label = stringResource(R.string.home_selinux_status),
                value = state.systemInfo.selinuxStatus,
                severity = if (state.isSELinuxPermissive) FolkSeverity.Caution else FolkSeverity.Positive,
            )
        }

        HomeWarnings(state, actions)
        HomeFacts(state)
        Spacer(Modifier.height(8.dp))
    }
}

/** Circle: the flagship composition. */
@Composable
private fun HomeLayoutCircle(
    state: HomeUiState,
    actions: HomeActions,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HomeHeroCard(state = state, actions = actions, wallpaperUri = heroWallpaperUri())
        HomeWarnings(state, actions)
        HomeSupportLinks(actions)
        HomeFacts(state)
        Spacer(Modifier.height(8.dp))
    }
}

/** List-first: the same content as grouped rows, without the hero surface. */
@Composable
private fun HomeLayoutList(
    state: HomeUiState,
    actions: HomeActions,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FolkSettingsGroup {
            item {
                FolkPreference(
                    title = stringResource(R.string.home_working),
                    summary = state.ksuVersion?.let {
                        stringResource(
                            R.string.home_working_version,
                            "$it-${state.kernelUAPIVersion}",
                        )
                    } ?: stringResource(R.string.home_not_installed),
                    icon = if (state.ksuVersion != null) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                    onClick = if (!state.isLateLoadMode) actions.onInstallClick else null,
                )
            }
            item {
                FolkPreference(
                    title = stringResource(R.string.home_click_to_install),
                    summary = stringResource(R.string.home_jailbreak),
                    icon = Icons.Outlined.SystemUpdate,
                    enabled = state.isSELinuxPermissive,
                    onClick = actions.onJailbreakClick,
                )
            }
        }

        HomeWarnings(state, actions)
        HomeFacts(state)
        HomeSupportLinks(actions)
        Spacer(Modifier.height(8.dp))
    }
}

/** Focus: the status and anything wrong, then only the essential facts. */
@Composable
private fun HomeLayoutFocus(
    state: HomeUiState,
    actions: HomeActions,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HomeHeroCard(
            state = state,
            actions = actions,
            wallpaperUri = heroWallpaperUri(),
        )
        HomeWarnings(state, actions)

        FolkFactsGroup {
            fact(stringResource(R.string.home_manager_version), state.systemInfo.managerVersion)
            fact(stringResource(R.string.home_kernel), state.systemInfo.kernelVersion)
            fact(stringResource(R.string.home_device_model), state.systemInfo.deviceModel)
        }

        Spacer(Modifier.height(8.dp))
    }
}

/** Dashboard: compact status tiles above the facts. */
@Composable
private fun HomeLayoutDashboard(
    state: HomeUiState,
    actions: HomeActions,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HomeTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.CheckCircle,
                label = stringResource(R.string.home_working),
                value = state.ksuVersion?.toString() ?: "-",
                severity = if (state.ksuVersion != null) FolkSeverity.Positive else FolkSeverity.Critical,
                cardId = BackgroundConfig.DASHBOARD_TILE_WORKING,
            )
            HomeTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Security,
                label = stringResource(R.string.home_selinux_status),
                value = state.systemInfo.selinuxStatus,
                severity = if (state.isSELinuxPermissive) FolkSeverity.Caution else FolkSeverity.Positive,
                cardId = BackgroundConfig.DASHBOARD_TILE_SELINUX,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HomeTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Layers,
                label = stringResource(R.string.home_zygisk_implementation),
                value = state.systemInfo.zygiskImplementation
                    ?: stringResource(R.string.home_zygisk_not_installed),
                severity = FolkSeverity.Neutral,
                cardId = BackgroundConfig.DASHBOARD_TILE_ZYGISK,
            )
            HomeTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Extension,
                label = stringResource(R.string.home_seccomp_status),
                value = state.systemInfo.seccompStatus.toString(),
                severity = FolkSeverity.Neutral,
                cardId = BackgroundConfig.DASHBOARD_TILE_SECCOMP,
            )
        }

        HomeWarnings(state, actions)
        HomeFacts(state)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HomeTile(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    severity: FolkSeverity,
    cardId: String? = null,
) {
    // 只有 Dashboard 布局的磁贴会带 cardId，其余布局的磁贴不受该开关影响。
    val uri = if (cardId != null && BackgroundConfig.isDashboardCardBackgroundEnabled) {
        BackgroundConfig.getDashboardTileBgUri(cardId)
    } else {
        null
    }
    val dark = isInDarkTheme()
    val palette = cardPalette(
        uri = uri,
        container = MaterialTheme.colorScheme.surfaceContainerHigh,
        content = MaterialTheme.colorScheme.onSurface,
        muted = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = FolkShape.Corner20,
        color = palette.container,
    ) {
        Box {
            CardWallpaperLayer(
                uri = uri,
                dim = BackgroundConfig.getEffectiveDashboardCardBgDim(dark),
                opacity = BackgroundConfig.getEffectiveDashboardCardBgOpacity(dark),
            )
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = palette.muted,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = label,
                        style = FolkType.Caption,
                        color = palette.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                FolkStatusDot(severity)
            }
        }
    }
}
