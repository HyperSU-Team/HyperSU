package com.sukisu.ultra.ui.component.folk

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.theme.SoundEffectConfig
import com.sukisu.ultra.ui.theme.VibrationConfig
import com.sukisu.ultra.ui.theme.tokens.FolkShape
import com.sukisu.ultra.ui.theme.tokens.FolkType
import com.sukisu.ultra.ui.util.SoundEffectManager
import com.sukisu.ultra.ui.util.VibrationManager

/**
 * The base settings row used across FolkPatch.
 *
 * Layout is intentionally hand-built instead of wrapping [androidx.compose.material3.ListItem]
 * so the icon size, text hierarchy, vertical rhythm and trailing alignment are
 * identical on every screen - and so the same row can host a switch, a value, a
 * chevron or any custom trailing content.
 */
@Composable
fun FolkPreference(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    iconTint: Color = folkIconColor(enabled),
    summary: String? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val clickModifier = if (onClick != null) {
        Modifier
            .folkPressScale(interactionSource, enabled)
            .combinedClickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    // A row tap is only audible in "Whole app" mode; the tab bar is the
                    // "Bottom bar only" scope and hooks itself. Vibration is split the same way.
                    SoundEffectManager.playScoped(context, SoundEffectConfig.SCOPE_GLOBAL)
                    if (VibrationConfig.scope == VibrationConfig.SCOPE_GLOBAL) {
                        VibrationManager.vibrate(context)
                    }
                    onClick()
                },
                onLongClick = onLongClick,
            )
    } else {
        Modifier
    }

    FolkPreferenceRow(
        title = title,
        modifier = modifier.then(clickModifier),
        icon = icon,
        enabled = enabled,
        iconTint = iconTint,
        summary = summary,
        selected = selected,
        trailing = trailing,
    )
}

@Composable
internal fun FolkPreferenceRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    iconTint: Color = folkIconColor(enabled),
    summary: String? = null,
    selected: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val titleColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val summaryColor = if (enabled) {
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        else MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }

    // Rows grow with their content: a summary adds breathing room, a plain
    // single-line row stays compact. That variation is what stops the list from
    // looking like a grid of identical Material list items.
    val verticalPadding = if (summary != null) {
        FolkSettingsDimens.ItemVerticalPaddingWithSummary
    } else {
        FolkSettingsDimens.ItemVerticalPadding
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            // Every row keeps at least a 56dp touch target, but rows still
            // grow with their content instead of being force-filled.
            .defaultMinSize(minHeight = 56.dp)
            .padding(
                start = FolkSettingsDimens.ItemHorizontalPadding,
                end = FolkSettingsDimens.ItemEndPadding,
                top = verticalPadding,
                bottom = verticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(FolkSettingsDimens.IconSize),
            )
            Spacer(Modifier.width(FolkSettingsDimens.IconSpacing))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = folkPreferenceTitleStyle(),
                color = titleColor,
            )
            if (summary != null) {
                Spacer(Modifier.height(FolkSettingsDimens.TitleSummarySpacing))
                Text(
                    text = summary,
                    style = folkPreferenceSummaryStyle(),
                    color = summaryColor,
                )
            }
        }

        if (trailing != null) {
            Spacer(Modifier.width(FolkSettingsDimens.TrailingSpacing))
            Box(contentAlignment = Alignment.Center) { trailing() }
        }
    }
}

/** A preference that toggles a boolean value with a Material switch. */
@Composable
fun FolkSwitchPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    summary: String? = null,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    FolkPreferenceRow(
        title = title,
        modifier = modifier
            .folkPressScale(interactionSource, enabled)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    // Same scope as a row tap: without this a switch would be the only control
                    // in "Whole app" mode that stays silent.
                    SoundEffectManager.playScoped(context, SoundEffectConfig.SCOPE_GLOBAL)
                    if (VibrationConfig.scope == VibrationConfig.SCOPE_GLOBAL) {
                        VibrationManager.vibrate(context)
                    }
                    onCheckedChange(it)
                },
            ),
        icon = icon,
        iconTint = folkIconColor(enabled),
        summary = summary,
        enabled = enabled,
        trailing = {
            com.sukisu.ultra.ui.component.ExpressiveSwitch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
            )
        },
    )
}

/** A preference that opens another screen. */
@Composable
fun FolkNavigationPreference(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    summary: String? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    FolkPreference(
        title = title,
        modifier = modifier,
        icon = icon,
        summary = summary,
        enabled = enabled,
        onClick = onClick,
        trailing = trailing ?: { FolkChevron(enabled) },
    )
}

/** A preference whose trailing side shows the current value. */
@Composable
fun FolkValuePreference(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    summary: String? = null,
    value: String? = null,
    showChevron: Boolean = true,
    enabled: Boolean = true,
) {
    FolkPreference(
        title = title,
        modifier = modifier,
        icon = icon,
        summary = summary,
        enabled = enabled,
        onClick = onClick,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value != null) {
                    Text(
                        text = value,
                        style = folkPreferenceValueStyle(),
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                    )
                    if (showChevron) Spacer(Modifier.width(6.dp))
                }
                if (showChevron) FolkChevron(enabled)
            }
        },
    )
}

/**
 * A preference that shows the selected value and opens a dialog to change it.
 *
 * A tap opens the list rather than stepping to the next entry: the option lists
 * here are long (the supported locales, the log files) and cycling through them
 * one tap at a time makes a target several taps away - and makes the row's
 * value jump around while the user looks for it.
 *
 * The list opens as a dialog rather than an anchored dropdown so it is not tied
 * to the row's position: these lists are often taller than the space left below
 * the row, and a dropdown would then open upward or run off the screen. A
 * dialog also gives the choice an explicit confirm, so a mis-tap is not applied
 * before the user has seen which entry it landed on.
 */
@Composable
fun FolkChoicePreference(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    summary: String? = null,
    enabled: Boolean = true,
) {
    var showDialog by remember { mutableStateOf(false) }

    FolkPreference(
        title = title,
        modifier = modifier,
        icon = icon,
        summary = summary,
        enabled = enabled,
        onClick = { showDialog = true },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                options.getOrNull(selectedIndex)?.let { value ->
                    Text(
                        text = value,
                        style = folkPreferenceValueStyle(),
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                    )
                    Spacer(Modifier.width(6.dp))
                }
                FolkChevron(enabled)
            }
        },
    )

    if (showDialog) {
        FolkChoiceDialog(
            title = title,
            options = options,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            onDismissRequest = { showDialog = false },
        )
    }
}

/**
 * The choice popup, ported from ReSukiSU's `SettingsChooseDialogFrame`.
 *
 * What makes it read as a chooser instead of a settings row that happens to sit
 * inside a dialog:
 *
 * - the panel is wide (the screen minus a 32dp margin) and the title is centred,
 *   so the dialog has its own axis instead of inheriting the left-aligned rhythm
 *   of the list it opened from;
 * - the options are radio rows inside a single group surface, the radio on the
 *   leading side and the chosen row filled - the current value is found by
 *   running an eye down the left edge rather than comparing trailing glyphs;
 * - cancel and confirm are both plain text buttons, so neither reads as the one
 *   "real" action of the screen.
 *
 * The colours and corners are ours ([FolkShape.Dialog], the group tone); the
 * layout is the reference's.
 */
@Composable
private fun FolkChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    // Pending until confirm: cancelling (or tapping outside) drops it and leaves
    // the stored value alone, the same way the KMI chooser behaves.
    var pendingIndex by remember(selectedIndex) {
        mutableIntStateOf(selectedIndex.coerceIn(0, maxOf(0, options.lastIndex)))
    }

    FolkChooserDialog(
        title = title,
        onDismissRequest = onDismissRequest,
        onConfirm = {
            onSelect(pendingIndex)
            onDismissRequest()
        },
    ) {
        itemsIndexed(options) { index, option ->
            FolkChoiceOptionRow(
                title = option,
                selected = pendingIndex == index,
                onClick = { pendingIndex = index },
            )
        }
    }
}

/**
 * The frame every list chooser shares: a wide panel, a centred title, the rows
 * in one group surface, and cancel/confirm as plain text buttons.
 *
 * Callers supply only their rows, so a chooser that needs its own row shape -
 * the KMI picker marks the device's current value - still cannot drift away
 * from the frame the others use.
 */
@Composable
internal fun FolkChooserDialog(
    title: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    rows: LazyListScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .sizeIn(minWidth = 280.dp, maxWidth = 560.dp)
                .padding(horizontal = 32.dp),
            shape = FolkShape.Dialog,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = title,
                    style = FolkType.Title,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .heightIn(max = 400.dp),
                    shape = FolkShape.Corner16,
                    color = folkGroupColor(),
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(OptionRowInset),
                        content = rows,
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        colors = FolkButtonDefaults.textColors(),
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }

                    TextButton(
                        onClick = onConfirm,
                        enabled = confirmEnabled,
                        colors = FolkButtonDefaults.textColors(),
                    ) {
                        Text(stringResource(R.string.confirm))
                    }
                }
            }
        }
    }
}

/**
 * The gap between the option rows and the group they sit in.
 *
 * The chosen row is inset by this much on all four sides, and its corner is the
 * group's own corner minus this gap, which is what makes the two arcs read as
 * concentric. A row inset by a different amount horizontally than vertically -
 * or a row carrying the group's radius itself - cannot line up with the group
 * corner however tall the row is.
 */
private val OptionRowInset = 4.dp

/** One option of [FolkChooserDialog]: a radio on the leading side, filled when chosen. */
@Composable
internal fun FolkChoiceOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    summary: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FolkShape.Corner12)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            )
            .folkPressScale(interactionSource, true)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            )
            .defaultMinSize(minHeight = 48.dp)
            .padding(start = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                text = title,
                style = FolkType.Title,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = FolkType.Summary,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** A preference that toggles a boolean value with a checkbox. */
@Composable
fun FolkCheckboxPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    summary: String? = null,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    FolkPreferenceRow(
        title = title,
        modifier = modifier
            .folkPressScale(interactionSource, enabled)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCheckedChange(it)
                },
            ),
        icon = icon,
        iconTint = folkIconColor(enabled),
        summary = summary,
        enabled = enabled,
        trailing = {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
            )
        },
    )
}

@Composable
fun FolkChevron(enabled: Boolean = true) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
            alpha = if (enabled) 0.7f else 0.3f,
        ),
        modifier = Modifier.size(FolkSettingsDimens.ChevronSize),
    )
}
