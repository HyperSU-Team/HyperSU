package com.sukisu.ultra.ui.screen.susfs.component

import android.content.Context
import android.content.pm.PackageInfo
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.sukisu.ultra.ui.component.folk.FolkSettingsDimens
import com.sukisu.ultra.ui.component.folk.FolkSettingsGroup
import com.sukisu.ultra.ui.component.folk.FolkSettingsGroupScope
import com.sukisu.ultra.ui.component.folk.FolkSeverity
import com.sukisu.ultra.ui.component.folk.folkPreferenceValueStyle
import com.sukisu.ultra.ui.component.folk.folkPressScale
import com.sukisu.ultra.ui.component.folk.folkSectionTitleColor
import com.sukisu.ultra.ui.component.folk.folkSectionTitleStyle
import com.sukisu.ultra.ui.component.folk.folkSeverityColor
import com.sukisu.ultra.ui.screen.susfs.util.AppInfoCache
import com.sukisu.ultra.ui.theme.tokens.FolkShape
import com.sukisu.ultra.ui.theme.tokens.FolkTheme
import com.sukisu.ultra.ui.theme.tokens.FolkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helpers shared by every SuSFS Folk section.
 *
 * The SuSFS screen has no FolkPatch original, so its blocks are assembled from
 * the shared Folk primitives. Anything used by more than one section (the
 * counted section header, the description block, the compact row actions and
 * the app icon lookup) lives here so the sections cannot drift apart.
 */

/**
 * The title row of a settings section that carries an item count, the Folk
 * reading of the old Material/Miuix "section header card + badge".
 *
 * Kept separate from [FolkCountedSection] so a section whose items are each
 * their own group (one card per app, for instance) can render the same header
 * without nesting one group surface inside another.
 */
@Composable
internal fun FolkCountedSectionHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = FolkSettingsDimens.ScreenPadding + FolkSettingsDimens.SectionTitleIndent,
                end = FolkSettingsDimens.ScreenPadding,
                bottom = FolkSettingsDimens.SectionTitleSpacing,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = folkSectionTitleStyle(),
            color = folkSectionTitleColor(),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = folkPreferenceValueStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A settings section whose title carries an item count: the header and one
 * group holding every item.
 */
@Composable
internal fun FolkCountedSection(
    title: String,
    count: Int,
    modifier: Modifier = Modifier,
    content: FolkSettingsGroupScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = FolkSettingsDimens.SectionSpacing),
    ) {
        FolkCountedSectionHeader(title = title, count = count)
        FolkSettingsGroup(content = content)
    }
}

/**
 * The explanatory block that used to be a description card: a title, a body and
 * an optional caution line plus an optional footnote.
 */
@Composable
internal fun FolkDescriptionGroup(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    warning: String? = null,
    additionalInfo: String? = null,
) {
    FolkSettingsGroup(modifier = modifier) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(
                    text = title,
                    style = FolkType.Title,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = description,
                    style = FolkType.Summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (warning != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = warning,
                        style = FolkType.Summary,
                        fontWeight = FontWeight.Medium,
                        color = folkSeverityColor(FolkSeverity.Caution),
                    )
                }
                if (additionalInfo != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = additionalInfo,
                        style = FolkType.Caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

/** A compact trailing action so a row can host three or four of them. */
@Composable
internal fun FolkRowIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(36.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** The placeholder shown when a list is empty. */
@Composable
internal fun FolkSusfsEmptyState(message: String, modifier: Modifier = Modifier) {
    FolkSettingsGroup(modifier = modifier) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Inbox,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = message,
                    style = FolkType.Summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The tab selector. The SuSFS screen has six tabs; they are rendered as a
 * scrollable capsule row in the Folk colours rather than as Material chips so
 * the screen keeps one visual language.
 */
@Composable
internal fun FolkSusfsTabRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = FolkSettingsDimens.ScreenPadding, vertical = 4.dp),
    ) {
        items(labels.size) { index ->
            val selected = index == selectedIndex
            val interactionSource = remember { MutableInteractionSource() }
            Surface(
                modifier = Modifier
                    .folkPressScale(interactionSource, true)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                    ) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(index)
                    },
                shape = FolkShape.CornerFull,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    FolkTheme.palette.groupedSurface
                },
            ) {
                Text(
                    text = labels[index],
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    style = FolkType.Summary,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/**
 * Resolves an installed app to a label, its [PackageInfo] and its icon, reusing
 * the existing on-disk cache. Runs off the main thread; failures degrade to the
 * package name with no icon instead of throwing.
 */
private fun resolveSusfsApp(context: Context, packageName: String): AppInfoCache.CachedAppInfo {
    val cached = AppInfoCache.getAppInfo(packageName)
    if (cached != null && (cached.drawable != null || cached.packageInfo != null)) {
        return cached
    }

    val fromSuperUser = runCatching { AppInfoCache.getAppInfoFromSuperUser(packageName) }.getOrNull()
    if (fromSuperUser != null) {
        val drawable = runCatching {
            fromSuperUser.packageInfo?.applicationInfo?.let { context.packageManager.getApplicationIcon(it) }
        }.getOrNull()
        val info = AppInfoCache.CachedAppInfo(
            appName = fromSuperUser.appName.ifBlank { packageName },
            packageInfo = fromSuperUser.packageInfo,
            drawable = drawable,
        )
        AppInfoCache.putAppInfo(packageName, info)
        return info
    }

    return runCatching {
        val pm = context.packageManager
        val packageInfo = pm.getPackageInfo(packageName, 0)
        val label = packageInfo.applicationInfo
            ?.let { runCatching { pm.getApplicationLabel(it).toString() }.getOrNull() }
            ?: packageName
        val drawable = runCatching {
            packageInfo.applicationInfo?.let { pm.getApplicationIcon(it) }
        }.getOrNull()
        AppInfoCache.CachedAppInfo(appName = label, packageInfo = packageInfo, drawable = drawable)
            .also { AppInfoCache.putAppInfo(packageName, it) }
    }.getOrElse {
        Log.d("SuSFSApp", "failed to resolve $packageName", it)
        AppInfoCache.CachedAppInfo(appName = packageName, packageInfo = null, drawable = null)
    }
}

/** The app label and [PackageInfo] for [packageName], loaded lazily. */
@Composable
internal fun rememberSusfsApp(packageName: String): AppInfoCache.CachedAppInfo {
    val context = LocalContext.current
    var info by remember(packageName) { mutableStateOf(AppInfoCache.getAppInfo(packageName)) }

    LaunchedEffect(packageName) {
        if (info == null) {
            info = withContext(Dispatchers.IO) { resolveSusfsApp(context, packageName) }
        }
    }

    return info ?: AppInfoCache.CachedAppInfo(appName = packageName, packageInfo = null, drawable = null)
}

/** The app icon as a bitmap inside a Folk inset tile, with a placeholder. */
@Composable
internal fun FolkAppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cached = rememberSusfsApp(packageName)
    var bitmap by remember(packageName, cached.drawable, cached.packageInfo) {
        mutableStateOf<ImageBitmap?>(null)
    }

    LaunchedEffect(packageName, cached.drawable, cached.packageInfo) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val drawable = cached.drawable
                    ?: cached.packageInfo?.applicationInfo?.loadIcon(context.packageManager)
                drawable?.toBitmap()?.asImageBitmap()
            }.getOrNull()
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (bitmap == null) FolkTheme.palette.groupedInset else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Default.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** An app row with its icon, label, package name and a selectable checkbox. */
@Composable
internal fun FolkAppSelectableRow(
    packageName: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val cached = rememberSusfsApp(packageName)
    val label = cached.appName.ifBlank { packageName }
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .folkPressScale(interactionSource, true)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(!checked)
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FolkAppIcon(packageName = packageName, modifier = Modifier.size(36.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = FolkType.Title,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (label != packageName) {
                Text(
                    text = packageName,
                    style = FolkType.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        androidx.compose.material3.Checkbox(
            checked = checked,
            onCheckedChange = null,
        )
    }
}
