package com.sukisu.ultra.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.folk.FolkScaffold
import com.sukisu.ultra.ui.component.folk.FolkTitleStyle
import com.sukisu.ultra.ui.component.splicedLazyColumnGroup
import com.sukisu.ultra.ui.util.LocaleHelper

/**
 * The language chooser, in the FolkPatch design.
 *
 * A full page rather than a dropdown, ported from FolkPatch's `LanguagePickerScreen`: the
 * languages are one continuous card group, the current one is tinted and carries a check, and
 * the first entry is "follow system" with a translate glyph instead of a language code.
 *
 * Choosing here goes through [LocaleHelper.setLanguage], the same call the settings row used,
 * so the switch is masked by the FolkLanguageSwitch cover exactly as before.
 */
@Composable
fun LanguagePickerScreen(
    currentTag: String,
    onBack: () -> Unit,
    onSelected: (String) -> Unit,
) {
    // The switch is applied asynchronously and the activity is recreated behind the switch
    // cover, so the row the user just tapped is marked straight away rather than waiting for
    // the new configuration to arrive.
    var lastSelectedIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(currentTag) { lastSelectedIndex = -1 }

    val systemLabel = stringResource(R.string.settings_language_system)
    val tags = remember { listOf(LocaleHelper.SYSTEM) + LocaleHelper.SUPPORTED_TAGS }
    val names = remember(systemLabel) {
        tags.map { if (it.isEmpty()) systemLabel else LocaleHelper.displayName(it) }
    }

    FolkScaffold(
        title = stringResource(R.string.settings_language),
        titleStyle = FolkTitleStyle.Inline,
        onBack = onBack,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item { Spacer(Modifier.height(12.dp)) }

            splicedLazyColumnGroup(
                items = names,
                key = { index, _ -> tags[index] },
            ) { index, item ->
                val isSelected = if (lastSelectedIndex != -1) {
                    lastSelectedIndex == index
                } else {
                    tags[index].equals(currentTag, ignoreCase = true)
                }

                LanguageRow(
                    label = item,
                    code = if (index == 0) null else languageCode(tags[index]),
                    selected = isSelected,
                    onClick = {
                        lastSelectedIndex = index
                        onSelected(tags[index])
                    },
                )
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/**
 * One language row.
 *
 * The leading glyph distinguishes the two kinds of entry: "follow system" has no language of
 * its own, so it shows a translate icon; every other row shows its code in a circle, filled
 * when chosen.
 */
@Composable
private fun LanguageRow(
    label: String,
    code: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (code == null) {
            Icon(
                imageVector = Icons.Filled.Translate,
                contentDescription = null,
                tint = if (selected) accent else muted,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = if (selected) accent else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        muted
                    },
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )

        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = accent,
            )
        }
    }
}

/** The short code shown in the leading circle: `zh-CN` becomes `ZH`, `pt-BR` becomes `PT`. */
private fun languageCode(tag: String): String =
    tag.substringBefore('-').uppercase()
