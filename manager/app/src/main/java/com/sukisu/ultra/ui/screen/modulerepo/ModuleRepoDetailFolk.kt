package com.sukisu.ultra.ui.screen.modulerepo

import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ChromeReaderMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.dialog.ConfirmDialogHandle
import com.sukisu.ultra.ui.component.dialog.rememberConfirmDialog
import com.sukisu.ultra.ui.component.folk.FolkButtonDefaults
import com.sukisu.ultra.ui.component.folk.FolkLoadingIndicator
import com.sukisu.ultra.ui.component.folk.FolkScaffold
import com.sukisu.ultra.ui.component.folk.FolkSettingsGroup
import com.sukisu.ultra.ui.component.folk.FolkStateView
import com.sukisu.ultra.ui.component.folk.FolkTitleStyle
import com.sukisu.ultra.ui.component.markdown.GithubMarkdown
import com.sukisu.ultra.ui.theme.tokens.FolkShape
import com.sukisu.ultra.ui.theme.tokens.FolkType
import com.sukisu.ultra.ui.util.download
import com.sukisu.ultra.ui.util.isDownloadAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A module's page in the repository, in the FolkPatch design.
 *
 * The three tabs (README, releases, info) are kept as a pager with a Folk tab
 * row, and each tab keeps its own content: the README is the rendered Markdown,
 * the releases list each release with its description and downloadable assets,
 * and info lists the authors and the source link. The download-then-install
 * flow, including the confirmation before downloading, is unchanged.
 */
@Composable
fun ModuleRepoDetailScreenFolk(
    state: ModuleRepoDetailUiState,
    actions: ModuleRepoDetailActions,
) {
    val module = state.module
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val confirmTitle = stringResource(R.string.module_install)
    var pendingDownload by remember { mutableStateOf<(() -> Unit)?>(null) }
    val confirmDialog = rememberConfirmDialog(onConfirm = { pendingDownload?.invoke() })

    val tabs = listOf(
        stringResource(R.string.tab_readme),
        stringResource(R.string.tab_releases),
        stringResource(R.string.tab_info),
    )
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })

    FolkScaffold(
        title = module.moduleName,
        titleStyle = FolkTitleStyle.Flexible,
        onBack = actions.onBack,
        actions = {
            if (state.webUrl.isNotEmpty()) {
                IconButton(onClick = actions.onOpenWebUrl) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ChromeReaderMode,
                        contentDescription = null,
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            FolkTabRow(
                tabs = tabs,
                selectedIndex = pagerState.currentPage,
                onTabClick = { index ->
                    scope.launch { pagerState.animateScrollToPage(index) }
                },
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                overscrollEffect = null,
            ) { page ->
                val pagePadding = PaddingValues(
                    start = innerPadding.calculateStartPadding(
                        androidx.compose.ui.platform.LocalLayoutDirection.current
                    ),
                    end = innerPadding.calculateEndPadding(
                        androidx.compose.ui.platform.LocalLayoutDirection.current
                    ),
                    bottom = innerPadding.calculateBottomPadding(),
                )
                when (page) {
                    0 -> RepoReadmePage(
                        readmeHtml = state.readmeHtml,
                        readmeLoaded = state.readmeLoaded,
                        innerPadding = pagePadding,
                    )

                    1 -> RepoReleasesPage(
                        detailReleases = state.detailReleases,
                        innerPadding = pagePadding,
                        confirmTitle = confirmTitle,
                        confirmDialog = confirmDialog,
                        scope = scope,
                        onInstallModule = actions.onInstallModule,
                        context = context,
                        setPendingDownload = { pendingDownload = it },
                    )

                    else -> RepoInfoPage(
                        module = module,
                        innerPadding = pagePadding,
                        onOpenUrl = actions.onOpenUrl,
                        sourceUrl = state.sourceUrl,
                    )
                }
            }
        }
    }
}

/** A simple underline-free tab row in the Folk colours. */
@Composable
private fun FolkTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onTabClick: (Int) -> Unit,
) {
    androidx.compose.material3.PrimaryTabRow(
        selectedTabIndex = selectedIndex,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        tabs.forEachIndexed { index, title ->
            androidx.compose.material3.Tab(
                selected = index == selectedIndex,
                onClick = { onTabClick(index) },
                text = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

@Composable
private fun RepoReadmePage(
    readmeHtml: String?,
    readmeLoaded: Boolean,
    innerPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = innerPadding,
    ) {
        item {
            if (readmeLoaded && readmeHtml != null) {
                var loaded by remember(readmeHtml) { mutableStateOf(false) }
                val alpha by animateFloatAsState(
                    targetValue = if (loaded) 1f else 0f,
                    animationSpec = tween(durationMillis = 300),
                    label = "ReadmeAlpha",
                )
                Box {
                    Box(modifier = Modifier.graphicsLayer { this.alpha = alpha }) {
                        GithubMarkdown(
                            content = readmeHtml,
                            onLoadingChange = { loaded = !it },
                            containerColor = MaterialTheme.colorScheme.surface,
                        )
                    }
                    AnimatedVisibility(
                        visible = !loaded,
                        enter = EnterTransition.None,
                        exit = fadeOut(animationSpec = tween(durationMillis = 150)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillParentMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            FolkLoadingIndicator()
                        }
                    }
                }
            } else if (readmeLoaded) {
                // The fetch is done and there is no README to show. Spinning here is what made a
                // module without one - or a request that failed - look like it was still loading.
                Box(
                    modifier = Modifier
                        .fillParentMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.module_repo_no_readme),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillParentMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    FolkLoadingIndicator()
                }
            }
        }
    }
}

@Composable
private fun RepoReleasesPage(
    detailReleases: List<ReleaseArg>,
    innerPadding: PaddingValues,
    confirmTitle: String,
    confirmDialog: ConfirmDialogHandle,
    scope: CoroutineScope,
    onInstallModule: (Uri) -> Unit,
    context: Context,
    setPendingDownload: ((() -> Unit)) -> Unit,
) {
    if (detailReleases.isEmpty()) {
        FolkStateView(
            title = stringResource(R.string.release_no_releases),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = innerPadding.calculateStartPadding(
                androidx.compose.ui.platform.LocalLayoutDirection.current
            ) + 16.dp,
            end = innerPadding.calculateEndPadding(
                androidx.compose.ui.platform.LocalLayoutDirection.current
            ) + 16.dp,
            bottom = innerPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        items(
            items = detailReleases,
            key = { it.tagName },
            contentType = { "release" },
        ) { rel ->
            val title = remember(rel.name, rel.tagName) { rel.name.ifBlank { rel.tagName } }

            Column {
                FolkSettingsGroup {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = FolkType.Title,
                                    fontWeight = FontWeight.Medium,
                                )
                                if (rel.tagName.isNotBlank() && rel.tagName != title) {
                                    Text(
                                        text = rel.tagName,
                                        style = FolkType.Caption,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (rel.publishedAt.isNotBlank()) {
                                Text(
                                    text = rel.publishedAt,
                                    style = FolkType.Caption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                if (rel.descriptionHTML.isNotEmpty()) {
                    var descLoaded by remember(rel.descriptionHTML) { mutableStateOf(false) }
                    val descAlpha by animateFloatAsState(
                        targetValue = if (descLoaded) 1f else 0f,
                        animationSpec = tween(durationMillis = 300),
                        label = "ReleaseDescAlpha",
                    )
                    val placeholderAlpha by animateFloatAsState(
                        targetValue = if (descLoaded) 0f else 1f,
                        animationSpec = tween(durationMillis = 150),
                        label = "ReleaseDescPlaceholderAlpha",
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                            .animateContentSize(animationSpec = tween(durationMillis = 300)),
                    ) {
                        Box(modifier = Modifier.graphicsLayer { this.alpha = descAlpha }) {
                            GithubMarkdown(
                                content = rel.descriptionHTML,
                                onLoadingChange = { descLoaded = !it },
                            )
                        }
                        if (placeholderAlpha > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp)
                                    .graphicsLayer { this.alpha = placeholderAlpha },
                                contentAlignment = Alignment.Center,
                            ) {
                                FolkLoadingIndicator()
                            }
                        }
                    }
                }

                rel.assets.forEach { asset ->
                    ReleaseAssetRow(
                        asset = asset,
                        confirmTitle = confirmTitle,
                        confirmDialog = confirmDialog,
                        scope = scope,
                        context = context,
                        onInstallModule = onInstallModule,
                        setPendingDownload = setPendingDownload,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReleaseAssetRow(
    asset: ReleaseAssetArg,
    confirmTitle: String,
    confirmDialog: ConfirmDialogHandle,
    scope: CoroutineScope,
    context: Context,
    onInstallModule: (Uri) -> Unit,
    setPendingDownload: ((() -> Unit)) -> Unit,
) {
    val fileName = asset.name
    val sizeText = remember(asset.size) {
        val s = asset.size
        when {
            s >= 1024L * 1024L * 1024L -> String.format("%.1f GB", s / (1024f * 1024f * 1024f))
            s >= 1024L * 1024L -> String.format("%.1f MB", s / (1024f * 1024f))
            s >= 1024L -> String.format("%.0f KB", s / 1024f)
            else -> "$s B"
        }
    }
    // "·" rather than the mojibake this used to carry, and the count goes through a resource so
    // it is localized rather than the hard-coded English "downloads" it had.
    val downloadsLabel = stringResource(R.string.module_repo_downloads, asset.downloadCount)
    val sizeAndDownloads = remember(sizeText, downloadsLabel) {
        "$sizeText · $downloadsLabel"
    }

    var isDownloading by remember(fileName, asset.downloadUrl) { mutableStateOf(false) }
    var progress by remember(fileName, asset.downloadUrl) { mutableIntStateOf(0) }
    var downloadedUri by remember(fileName, asset.downloadUrl) { mutableStateOf<Uri?>(null) }
    val isDownloaded = downloadedUri != null

    val onClickDownload = remember(fileName, asset.downloadUrl) {
        {
            val startText = context.getString(R.string.module_start_downloading, fileName)
            setPendingDownload {
                isDownloading = true
                scope.launch(Dispatchers.IO) {
                    download(
                        asset.downloadUrl,
                        fileName,
                        onDownloaded = { uri ->
                            isDownloading = false
                            downloadedUri = uri
                        },
                        onDownloading = { isDownloading = true },
                        onProgress = { p -> scope.launch(Dispatchers.Main) { progress = p } },
                    )
                    isDownloading = false
                }
            }
            confirmDialog.showConfirm(title = confirmTitle, content = startText)
        }
    }

    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(FolkShape.Corner16),
        shape = FolkShape.Corner16,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = fileName, style = FolkType.Title)
                Text(
                    text = sizeAndDownloads,
                    style = FolkType.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isDownloaded) {
                Button(
                    onClick = {
                        val uri = downloadedUri ?: return@Button
                        scope.launch {
                            if (isDownloadAvailable(uri)) {
                                onInstallModule(uri)
                            } else {
                                downloadedUri = null
                            }
                        }
                    },
                    colors = FolkButtonDefaults.tonalColors(),
                    modifier = Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 32.dp),
                ) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        imageVector = Icons.Outlined.InstallMobile,
                        contentDescription = stringResource(R.string.install),
                    )
                    Text(
                        modifier = Modifier.padding(start = 7.dp),
                        text = stringResource(R.string.install),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            } else {
                Button(
                    onClick = onClickDownload,
                    enabled = !isDownloading,
                    colors = FolkButtonDefaults.tonalColors(),
                    modifier = Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 32.dp),
                ) {
                    if (isDownloading) {
                        androidx.compose.material3.CircularProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = Icons.Outlined.Download,
                            contentDescription = stringResource(R.string.download),
                        )
                        Text(
                            modifier = Modifier.padding(start = 7.dp),
                            text = stringResource(R.string.download),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoInfoPage(
    module: RepoModuleArg,
    innerPadding: PaddingValues,
    onOpenUrl: (String) -> Unit,
    sourceUrl: String,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            bottom = innerPadding.calculateBottomPadding(),
        ),
    ) {
        if (module.authorsList.isNotEmpty()) {
            item {
                FolkSettingsGroup(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    module.authorsList.forEach { author ->
                        item(key = author.name) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = author.name,
                                    style = FolkType.Title,
                                    modifier = Modifier.weight(1f),
                                )
                                if (author.link.isNotEmpty()) {
                                    IconButton(onClick = { onOpenUrl(author.link) }) {
                                        Icon(
                                            modifier = Modifier.size(20.dp),
                                            imageVector = Icons.Outlined.Link,
                                            contentDescription = null,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (sourceUrl.isNotEmpty()) {
            item {
                FolkSettingsGroup(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.module_repos_source_code),
                                    style = FolkType.Title,
                                )
                                Text(
                                    text = sourceUrl,
                                    style = FolkType.Caption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { onOpenUrl(sourceUrl) }) {
                                Icon(
                                    modifier = Modifier.size(20.dp),
                                    imageVector = Icons.Outlined.Link,
                                    contentDescription = null,
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
