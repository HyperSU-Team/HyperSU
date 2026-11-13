package com.sukisu.ultra.ui.screen.themes

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.folk.FolkScaffold
import com.sukisu.ultra.ui.component.folk.FolkTitleStyle
import com.sukisu.ultra.ui.theme.tokens.FolkShape
import java.util.Locale

@Composable
fun ThemeStoreFolk(
    state: ThemeStoreState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onTabChange: (ThemeStoreTab) -> Unit,
    onQueryChange: (String) -> Unit,
    onInstall: (StoreTheme) -> Unit,
    onApplyLocal: (LocalTheme) -> Unit,
    onDeleteLocal: (LocalTheme) -> Unit,
) {
    FolkScaffold(
        title = stringResource(R.string.theme_store),
        titleStyle = FolkTitleStyle.Flexible,
        onBack = onBack,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            TabRow(selectedTabIndex = state.tab.ordinal) {
                Tab(
                    selected = state.tab == ThemeStoreTab.STORE,
                    onClick = { onTabChange(ThemeStoreTab.STORE) },
                    text = { Text(stringResource(R.string.theme_store_tab_store)) },
                )
                Tab(
                    selected = state.tab == ThemeStoreTab.DOWNLOADED,
                    onClick = { onTabChange(ThemeStoreTab.DOWNLOADED) },
                    text = { Text(stringResource(R.string.theme_store_tab_downloaded)) },
                )
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                },
                placeholder = { Text(stringResource(R.string.theme_store_search)) },
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.tab == ThemeStoreTab.STORE) {
                    storeItems(state, onRefresh, onInstall, onApplyLocal)
                } else {
                    downloadedItems(state, onApplyLocal, onDeleteLocal)
                }
            }
        }
    }
}

/**
 * The library copy of a store theme, when there is one.
 *
 * The release tag is the reliable key; themes installed before the tag was recorded are matched
 * by name, ignoring the `.fpt` suffix some releases put in their title.
 */
private fun downloadedCopy(theme: StoreTheme, downloads: List<LocalTheme>): LocalTheme? =
    downloads.firstOrNull { it.sourceTag == theme.tag }
        ?: downloads.firstOrNull { local ->
            val lhs = local.name.trim().removeSuffix(".fpt")
            val rhs = theme.name.trim().removeSuffix(".fpt")
            lhs.equals(rhs, ignoreCase = true)
        }

private fun androidx.compose.foundation.lazy.LazyListScope.storeItems(
    state: ThemeStoreState,
    onRefresh: () -> Unit,
    onInstall: (StoreTheme) -> Unit,
    onApply: (LocalTheme) -> Unit,
) {
    val query = state.query.trim()
    val visible = if (query.isEmpty()) {
        state.themes
    } else {
        state.themes.filter {
            it.name.contains(query, ignoreCase = true) || it.tag.contains(query, ignoreCase = true)
        }
    }

    when {
        state.loading -> item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        }

        state.failed -> item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.theme_store_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onRefresh) {
                    Text(stringResource(R.string.theme_store_retry))
                }
            }
        }

        visible.isEmpty() -> item {
            Text(
                text = stringResource(R.string.theme_store_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
            )
        }

        else -> items(visible, key = { it.tag }) { theme ->
            StoreThemeCard(
                theme = theme,
                state = state,
                downloaded = downloadedCopy(theme, state.downloads),
                onInstall = onInstall,
                onApply = onApply,
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.downloadedItems(
    state: ThemeStoreState,
    onApply: (LocalTheme) -> Unit,
    onDelete: (LocalTheme) -> Unit,
) {
    val query = state.query.trim()
    val visible = if (query.isEmpty()) {
        state.downloads
    } else {
        state.downloads.filter { it.name.contains(query, ignoreCase = true) }
    }

    if (visible.isEmpty()) {
        item {
            Text(
                text = stringResource(R.string.theme_store_no_downloads),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
            )
        }
    } else {
        items(visible, key = { it.id }) { local ->
            LocalThemeCard(
                theme = local,
                busy = state.busyKey == local.id,
                onApply = onApply,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun StoreThemeCard(
    theme: StoreTheme,
    state: ThemeStoreState,
    downloaded: LocalTheme?,
    onInstall: (StoreTheme) -> Unit,
    onApply: (LocalTheme) -> Unit,
) {
    // Two different operations can be in flight on this card: a download keyed by the release
    // tag, and applying an archive that is already on the device, keyed by the library id.
    val busy = state.busyKey == theme.tag || (downloaded != null && state.busyKey == downloaded.id)
    val progress = state.progress[theme.tag] ?: 0

    Card(
        shape = FolkShape.Corner20,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column {
            // The previews are phone screenshots. Cropping them to a fixed height showed only the
            // middle of each theme, so the whole picture is fitted instead, letterboxed on a
            // neutral surface.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                val preview = theme.previewUrl
                if (!preview.isNullOrBlank()) {
                    // A preview that failed to load used to stay blank forever; a retry button
                    // re-requests it instead of leaving the card without a picture. The attempt
                    // is part of the cache key so the retry does not read the failed entry back.
                    var attempt by remember(theme.tag) { mutableIntStateOf(0) }
                    var failed by remember(theme.tag, attempt) { mutableStateOf(false) }
                    val context = LocalContext.current
                    if (failed) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.theme_store_preview_failed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { attempt++ }) {
                                Text(stringResource(R.string.theme_store_retry))
                            }
                        }
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(preview)
                                .memoryCacheKey("$preview#$attempt")
                                .diskCacheKey("$preview#$attempt")
                                .crossfade(true)
                                .build(),
                            contentDescription = theme.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                            onError = { failed = true },
                        )
                    }
                } else {
                    Text(
                        text = theme.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = theme.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = theme.tag,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = formatSize(theme.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // A theme that is already on the device says so, and its button applies the
                    // local copy instead of downloading the same 20MB again.
                    if (downloaded != null) {
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.theme_store_tab_downloaded),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.theme_store_downloads, theme.downloadCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(12.dp))
                when {
                    // Only a download has a percentage to report; applying an existing copy is
                    // quick and just leaves the button disabled until it is done.
                    busy && state.busyKey == theme.tag -> {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "$progress%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    downloaded != null -> Button(
                        onClick = { onApply(downloaded) },
                        enabled = state.busyKey == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.theme_store_apply))
                    }

                    else -> Button(
                        onClick = { onInstall(theme) },
                        enabled = state.busyKey == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.theme_store_install))
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalThemeCard(
    theme: LocalTheme,
    busy: Boolean,
    onApply: (LocalTheme) -> Unit,
    onDelete: (LocalTheme) -> Unit,
) {
    Card(
        shape = FolkShape.Corner20,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = theme.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    if (theme.author.isNotBlank()) append(theme.author)
                    if (theme.version.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(theme.version)
                    }
                    if (isNotEmpty()) append(" · ")
                    append(formatSize(theme.sizeBytes))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onApply(theme) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.theme_store_apply))
                }
                TextButton(
                    onClick = { onDelete(theme) },
                    enabled = !busy,
                ) {
                    Text(stringResource(R.string.theme_store_delete))
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "—"
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    else -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
}
