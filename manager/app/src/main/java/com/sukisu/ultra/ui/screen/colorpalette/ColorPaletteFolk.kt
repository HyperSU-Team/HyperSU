package com.sukisu.ultra.ui.screen.colorpalette

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.filled.Brightness1
import androidx.compose.material.icons.filled.Brightness3
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.navigation.useNavigationRail
import com.sukisu.ultra.ui.component.folk.FolkButtonDefaults
import com.sukisu.ultra.ui.component.dialog.rememberLoadingDialog
import com.sukisu.ultra.ui.screen.settings.AppearanceBackgroundSection
import com.sukisu.ultra.ui.screen.settings.AppearanceBannerSection
import com.sukisu.ultra.ui.screen.settings.AppearanceDashboardCardSection
import com.sukisu.ultra.ui.screen.settings.AppearanceFocusCardSection
import com.sukisu.ultra.ui.screen.settings.AppearanceFontSection
import com.sukisu.ultra.ui.screen.settings.AppearanceGridCardSection
import com.sukisu.ultra.ui.screen.settings.AppearanceLayoutOptionsSection
import com.sukisu.ultra.ui.screen.settings.AppearanceNavIconSection
import com.sukisu.ultra.ui.screen.settings.AppearanceThemeSection
import com.sukisu.ultra.ui.screen.settings.AppearanceTitleImageSection
import com.sukisu.ultra.ui.screen.settings.MultimediaMusicSection
import com.sukisu.ultra.ui.screen.settings.MultimediaSoundSection
import com.sukisu.ultra.ui.component.folk.FolkSettingsDimens
import com.sukisu.ultra.ui.component.folk.FolkChoicePreference
import com.sukisu.ultra.ui.component.folk.FolkPreference
import com.sukisu.ultra.ui.component.folk.FolkSettingsScaffold
import com.sukisu.ultra.ui.component.folk.FolkSettingsSectionGroup
import com.sukisu.ultra.ui.component.folk.FolkSliderPreference
import com.sukisu.ultra.ui.component.folk.FolkSwitchPreference
import com.sukisu.ultra.ui.theme.ColorMode
import com.sukisu.ultra.ui.theme.glass.GlassConfig
import com.sukisu.ultra.ui.theme.keyColorOptions
import com.sukisu.ultra.ui.theme.rememberKernelSUColorScheme

/**
 * The theme picker in the FolkPatch design.
 *
 * The live preview, the seed-colour swatches and every setting below them keep
 * their behaviour; the settings are Folk rows now, and the four theme modes are
 * a single row of icon choices rather than a button group. The preview still
 * reflects the mode currently being edited, not the applied theme, so a user
 * can see a dark choice before committing to it.
 */
@Composable
fun ColorPaletteScreenFolk(
    state: ColorPaletteUiState,
    actions: ColorPaletteScreenActions,
) {
    val uiState = state.uiState
    val currentColorMode = state.currentColorMode
    val currentKeyColor = uiState.keyColor
    val colorStyle = state.currentPaletteStyle
    val colorSpec = state.currentColorSpec
    val haptic = LocalHapticFeedback.current

    val isDark = currentColorMode.isDark ||
        (currentColorMode.isSystem && isSystemInDarkTheme())
    val isAmoled = currentColorMode.isAmoled

    val snackbarHost = remember { SnackbarHostState() }
    val loadingDialog = rememberLoadingDialog()

    FolkSettingsScaffold(
        title = stringResource(R.string.settings_theme),
        onBack = actions.onBack,
        snackbarHostState = snackbarHost,
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            ) {
                ThemePreviewCard(
                    keyColor = currentKeyColor,
                    isDark = isDark,
                    isAmoled = isAmoled,
                    paletteStyle = colorStyle,
                    colorSpec = colorSpec,
                )

                Spacer(Modifier.height(16.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        ColorSwatch(
                            color = Color.Unspecified,
                            isSelected = currentKeyColor == 0,
                            isDark = isDark,
                            isAmoled = isAmoled,
                            paletteStyle = colorStyle,
                            colorSpec = colorSpec,
                            onClick = { actions.onSetKeyColor(0) },
                        )
                    }
                    items(keyColorOptions) { color ->
                        ColorSwatch(
                            color = Color(color),
                            isSelected = currentKeyColor == color,
                            isDark = isDark,
                            isAmoled = isAmoled,
                            paletteStyle = colorStyle,
                            colorSpec = colorSpec,
                            onClick = { actions.onSetKeyColor(color) },
                        )
                    }
                }
            }
        }

        item {
            FolkSettingsSectionGroup(title = stringResource(R.string.settings_theme)) {
                item {
                    FolkPreference(
                        title = stringResource(R.string.settings_theme),
                        summary = stringResource(currentColorMode.labelRes()),
                        trailing = {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                val modes = listOf(
                                    ColorMode.SYSTEM to Icons.Filled.Brightness4,
                                    ColorMode.LIGHT to Icons.Filled.Brightness7,
                                    ColorMode.DARK to Icons.Filled.Brightness3,
                                    ColorMode.DARK_AMOLED to Icons.Filled.Brightness1,
                                )
                                modes.forEach { (mode, icon) ->
                                    val selected = currentColorMode == mode
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (selected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                                }
                                            )
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                                actions.onSetColorMode(mode)
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = stringResource(mode.labelRes()),
                                            tint = if (selected) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }

        item {
            FolkSettingsSectionGroup(title = stringResource(R.string.settings_color_style)) {
                item {
                    // The palettes and the spec versions are long lists: stepping to
                    // the next entry on tap puts a target several taps away, so both
                    // rows open the same chooser the other option rows use.
                    val styles = PaletteStyle.entries
                    FolkChoicePreference(
                        title = stringResource(R.string.settings_color_style),
                        options = styles.map { stringResource(it.labelRes()) },
                        selectedIndex = styles.indexOf(colorStyle).coerceAtLeast(0),
                        onSelect = { index -> actions.onSetColorStyle(styles[index].name) },
                    )
                }
                item {
                    val specs = ColorSpec.SpecVersion.entries
                    FolkChoicePreference(
                        title = stringResource(R.string.settings_color_spec),
                        options = remember(specs) { specs.map { it.name } },
                        selectedIndex = specs.indexOf(colorSpec).coerceAtLeast(0),
                        onSelect = { index -> actions.onSetColorSpec(specs[index].name) },
                    )
                }
            }
        }

        item {
            FolkSettingsSectionGroup(title = stringResource(R.string.settings_navigation)) {
                item {
                    FolkSwitchPreference(
                        title = stringResource(R.string.settings_navigation_badge),
                        summary = stringResource(R.string.settings_navigation_badge_summary),
                        icon = Icons.Rounded.Pin,
                        checked = uiState.enableNavigationBadge,
                        onCheckedChange = actions.onSetEnableNavigationBadge,
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    item {
                        FolkSwitchPreference(
                            title = stringResource(R.string.settings_enable_blur),
                            summary = stringResource(R.string.settings_enable_blur_summary),
                            icon = Icons.Rounded.BlurOn,
                            checked = GlassConfig.blurEnabled,
                            onCheckedChange = { GlassConfig.setBlurEnabled(it) },
                        )
                    }
                }
                item {
                    FolkSwitchPreference(
                        title = stringResource(R.string.settings_floating_bottom_bar),
                        summary = stringResource(R.string.settings_floating_bottom_bar_summary),
                        icon = Icons.Rounded.Dock,
                        checked = uiState.enableFloatingBottomBar,
                        onCheckedChange = actions.onSetEnableFloatingBottomBar,
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    item {
                        FolkSwitchPreference(
                            title = stringResource(R.string.settings_enable_predictive_back),
                            summary = stringResource(R.string.settings_enable_predictive_back_summary),
                            icon = Icons.AutoMirrored.Rounded.MenuOpen,
                            checked = uiState.enablePredictiveBack,
                            onCheckedChange = actions.onSetEnablePredictiveBack,
                        )
                    }
                }
                item {
                    FolkSwitchPreference(
                        title = stringResource(R.string.settings_show_fullstatus),
                        summary = stringResource(R.string.settings_show_fullstatus_summary),
                        icon = Icons.Outlined.Info,
                        checked = state.showFullStatus,
                        onCheckedChange = actions.onSetShowFullStatus,
                    )
                }
                item {
                    FolkSwitchPreference(
                        title = stringResource(R.string.settings_enable_swipe_dismiss),
                        summary = stringResource(R.string.settings_enable_swipe_dismiss_summary),
                        icon = Icons.Rounded.Swipe,
                        checked = uiState.enableSwipeDismiss,
                        onCheckedChange = actions.onSetEnableSwipeDismiss,
                    )
                }
            }
        }

        item {
            FolkSettingsSectionGroup(title = stringResource(R.string.settings_page_scale)) {
                item {
                    // Scaling the page re-measures every screen, so dragging the
                    // slider must not apply as it moves. The slider edits a pending
                    // value and the apply button appears only once it differs from
                    // what is in use - nothing changes until that button is pressed.
                    var pendingScale by remember(uiState.pageScale) {
                        mutableStateOf(uiState.pageScale)
                    }
                    Column {
                        FolkSliderPreference(
                            title = stringResource(R.string.settings_page_scale),
                            summary = stringResource(R.string.settings_page_scale_summary),
                            value = pendingScale,
                            valueRange = 0.8f..1.1f,
                            valueFormat = { "${(it * 100).toInt()}%" },
                            onValueChange = { pendingScale = it },
                        )
                        AnimatedVisibility(visible = pendingScale != uiState.pageScale) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = FolkSettingsDimens.ItemHorizontalPadding,
                                        end = FolkSettingsDimens.ItemEndPadding,
                                        bottom = FolkSettingsDimens.ItemVerticalPadding,
                                    ),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                Button(
                                    onClick = { actions.onSetPageScale(pendingScale) },
                                    colors = FolkButtonDefaults.filledColors(),
                                ) {
                                    Text(stringResource(R.string.settings_page_scale_apply))
                                }
                            }
                        }
                    }
                }
                item {
                    val unitLinesLabel = stringResource(R.string.unit_lines)
                    FolkSliderPreference(
                        title = stringResource(R.string.settings_module_description_max_lines),
                        summary = stringResource(R.string.settings_module_description_max_lines_summary),
                        value = uiState.moduleDescriptionMaxLines.toFloat(),
                        valueRange = 1f..5f,
                        steps = 3,
                        valueFormat = { "${it.toInt()} $unitLinesLabel" },
                        onValueChange = {
                            actions.onSetModuleDescriptionMaxLines(it.toInt())
                        },
                    )
                }
            }
        }

        item {
            AppearanceBackgroundSection(
                snackBarHost = snackbarHost,
                loadingDialog = loadingDialog,
            )
        }

        item {
            AppearanceFocusCardSection(
                snackBarHost = snackbarHost,
                loadingDialog = loadingDialog,
            )
        }

        item {
            AppearanceGridCardSection(
                snackBarHost = snackbarHost,
                loadingDialog = loadingDialog,
            )
        }

        item {
            AppearanceTitleImageSection(
                snackBarHost = snackbarHost,
                loadingDialog = loadingDialog,
            )
        }

        item {
            AppearanceNavIconSection(
                snackBarHost = snackbarHost,
                loadingDialog = loadingDialog,
            )
        }

        item {
            AppearanceLayoutOptionsSection(snackBarHost = snackbarHost)
        }

        item {
            AppearanceDashboardCardSection(
                snackBarHost = snackbarHost,
                loadingDialog = loadingDialog,
            )
        }

        item {
            AppearanceBannerSection(
                snackBarHost = snackbarHost,
                loadingDialog = loadingDialog,
            )
        }

        item {
            AppearanceFontSection(
                snackBarHost = snackbarHost,
                loadingDialog = loadingDialog,
            )
        }

        item {
            MultimediaMusicSection(
                snackBarHost = snackbarHost,
                loadingDialog = loadingDialog,
            )
        }

        item {
            MultimediaSoundSection(
                snackBarHost = snackbarHost,
                loadingDialog = loadingDialog,
            )
        }

        item {
            AppearanceThemeSection(
                snackBarHost = snackbarHost,
                loadingDialog = loadingDialog,
            )
        }

        item {
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun ColorMode.labelRes(): Int = when (this) {
    ColorMode.SYSTEM, ColorMode.MONET_SYSTEM -> R.string.settings_theme_mode_system
    ColorMode.LIGHT, ColorMode.MONET_LIGHT -> R.string.settings_theme_mode_light
    ColorMode.DARK, ColorMode.MONET_DARK -> R.string.settings_theme_mode_dark
    ColorMode.DARK_AMOLED -> R.string.settings_theme_mode_amoled
}

/**
 * The phone-shaped preview: it rebuilds the scheme from the edited seed colour
 * and mode so the surface, surfaces and accent all show what the theme would
 * look like before it is applied.
 */
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
private fun ThemePreviewCard(
    keyColor: Int,
    isDark: Boolean,
    isAmoled: Boolean = false,
    paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    colorSpec: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2025,
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.toFloat()
    val screenHeight = configuration.screenHeightDp.toFloat()
    val screenRatio = if (screenHeight > 0f) screenWidth / screenHeight else 0.5f
    val useRail = useNavigationRail(enableFloatingBottomBar = false)

    val colorScheme = rememberKernelSUColorScheme(
        seedColor = if (keyColor == 0) Color.Unspecified else Color(keyColor),
        isDark = isDark,
        isAmoled = isAmoled,
        paletteStyle = paletteStyle,
        colorSpec = colorSpec,
    )

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .aspectRatio(screenRatio),
            color = colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, color = colorScheme.outlineVariant),
        ) {
            val content: @Composable ColumnScope.() -> Unit = {
                Box(
                    modifier = Modifier
                        .height(if (useRail) 36.dp else 48.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopStart,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 12.dp, top = if (useRail) 8.dp else 16.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurface,
                        )
                    }
                }

                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    val showInfoCard = maxHeight >= 72.dp
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(
                            color = colorScheme.secondaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                            content = {},
                        )
                        if (showInfoCard) {
                            Surface(
                                color = colorScheme.surfaceBright,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                content = {},
                            )
                        }
                    }
                }
            }

            if (useRail) {
                Row {
                    Surface(
                        color = colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(36.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(Icons.Filled.Home, null, tint = colorScheme.primary)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) { content() }
                }
            } else {
                Column {
                    content()
                    Surface(
                        color = colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .height(40.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(Icons.Filled.Home, null, tint = colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One seed-colour swatch, showing the accent it would produce. */
@Composable
private fun ColorSwatch(
    color: Color,
    isSelected: Boolean,
    isDark: Boolean,
    isAmoled: Boolean = false,
    paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    colorSpec: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2025,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val colorScheme = rememberKernelSUColorScheme(
        seedColor = color,
        isDark = isDark,
        isAmoled = isAmoled,
        paletteStyle = paletteStyle,
        colorSpec = colorSpec,
    )

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onClick()
        },
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surfaceContainer,
        modifier = Modifier.size(72.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(48.dp)) {
                drawArc(
                    color = colorScheme.primaryContainer,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true,
                )
                drawArc(
                    color = colorScheme.tertiaryContainer,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = true,
                )
            }

            val scale by animateFloatAsState(targetValue = if (isSelected) 1.1f else 1.0f)
            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
                contentAlignment = Alignment.Center,
            ) {
                AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .border(2.dp, colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(colorScheme.primary, CircleShape),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = colorScheme.onPrimary,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(16.dp),
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = !isSelected,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(colorScheme.primary, CircleShape),
                    )
                }
            }
        }
    }
}

/**
 * The name a palette is shown under, in place of the enum constant.
 *
 * "TonalSpot" and "FruitSalad" are the upstream names of the schemes, not
 * something to show in a list; the label here is the scheme's Material name so
 * the row and the chooser read as options rather than as identifiers.
 */
@StringRes
private fun PaletteStyle.labelRes(): Int = when (this) {
    PaletteStyle.TonalSpot -> R.string.palette_style_tonal_spot
    PaletteStyle.Neutral -> R.string.palette_style_neutral
    PaletteStyle.Vibrant -> R.string.palette_style_vibrant
    PaletteStyle.Expressive -> R.string.palette_style_expressive
    PaletteStyle.Rainbow -> R.string.palette_style_rainbow
    PaletteStyle.FruitSalad -> R.string.palette_style_fruit_salad
    PaletteStyle.Monochrome -> R.string.palette_style_monochrome
    PaletteStyle.Fidelity -> R.string.palette_style_fidelity
    PaletteStyle.Content -> R.string.palette_style_content
}
