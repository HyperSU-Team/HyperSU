package com.sukisu.ultra.ui.screen.module

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import android.graphics.BitmapFactory
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CheckableDropdownMenuItem
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.data.model.Module
import com.sukisu.ultra.data.model.ModuleUpdateInfo
import com.sukisu.ultra.data.repository.SettingsRepositoryImpl
import com.sukisu.ultra.data.repository.isSoftRebootPreferred
import com.sukisu.ultra.ui.component.ObserveAsEvents
import com.sukisu.ultra.ui.component.SearchAppBar
import com.sukisu.ultra.ui.component.ScrollToTopOnChange
import com.sukisu.ultra.ui.component.dialog.rememberConfirmDialog
import com.sukisu.ultra.ui.component.folk.FolkAlertDialog
import com.sukisu.ultra.ui.component.folk.FolkButtonDefaults
import com.sukisu.ultra.ui.component.folk.FolkScaffold
import com.sukisu.ultra.ui.component.folk.FolkStateView
import com.sukisu.ultra.ui.component.folk.FolkTitleStyle
import com.sukisu.ultra.ui.component.statustag.StatusTag
import com.sukisu.ultra.ui.theme.BackgroundConfig
import com.sukisu.ultra.ui.theme.LocalModuleDescriptionMaxLines
import com.sukisu.ultra.ui.theme.LocalSimpleListBottomBar
import com.sukisu.ultra.ui.theme.bannerFadeColor
import com.sukisu.ultra.ui.theme.tokens.FolkShape
import com.sukisu.ultra.ui.theme.tokens.FolkType
import com.sukisu.ultra.ui.util.ModuleBanner
import com.sukisu.ultra.ui.util.reboot
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/**
 * Whether the module repository entry point is offered.
 *
 * On. It was switched off while the index behind it answered 404, which left the screen showing
 * nothing but a fetch error; the route, view model and screens were kept in place for exactly this
 * turn back on. A repository that cannot be reached now reports that itself, on its own screen,
 * which is better than the entry silently not existing.
 */
private val SHOW_MODULE_REPO = true

/**
 * How long the module card's action chips take to fade in and out.
 *
 * The default is a third of a second, which is slow enough to read as a stutter when a card is
 * updated and its chips change: the update chip disappears, the uninstall chip slides over, and
 * for that moment the row looks like it is still settling. This is short enough to read as one
 * change rather than as an animation.
 */
private const val ModuleChipAnimationMillis = 120

/**
 * How long the update chip takes to appear or disappear.
 *
 * Short, because this chip toggles on every update check, but not so short that it is invisible:
 * below roughly a tenth of a second the eye reads it as a jump rather than as a fade, which is
 * what 60ms looked like.
 */
private const val UpdateChipAnimationMillis = 120

/**
 * The Module tab in the FolkPatch design.
 *
 * Each module is one surface: name, version and author, an expanding
 * description, META/ZYGISK tags, then a row of actions - run the action script,
 * open the WebUI, update, uninstall/undo. Long-pressing an action button opens
 * the shortcut sheet, exactly as before. Search, sorting and the install FAB
 * keep their original behaviour; only the presentation changed.
 */
@Composable
internal fun ModulePagerFolk(
    uiState: ModuleUiState,
    confirmDialogState: ModuleConfirmDialogState?,
    moduleEvent: kotlinx.coroutines.flow.Flow<ModuleEffect>,
    actions: ModuleActions,
    bottomInnerPadding: Dp,
) {
    val snackBarHost = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val resource = LocalResources.current

    // Null when the repository entry is hidden, so SearchAppBar draws no leading icon. Held in a
    // typed local rather than inlined: the ternary would otherwise have to infer a composable
    // lambda type against null.
    val repoEntryAction: (@Composable () -> Unit)? = if (SHOW_MODULE_REPO) {
        {
            IconButton(onClick = actions.onOpenRepo) {
                Icon(
                    imageVector = Icons.Outlined.Cloud,
                    contentDescription = stringResource(R.string.module_repos),
                )
            }
        }
    } else {
        null
    }

    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val refreshTick = remember { mutableIntStateOf(0) }

    // The zip the user picked, held back until they confirm. Reading module.prop is async, so
    // the dialog is only shown once the metadata has arrived; a zip without one still reaches
    // the dialog, with null info. The coroutine scope used to read it is the one declared
    // further down for the module events.
    var pendingInstall by remember { mutableStateOf<List<Uri>?>(null) }
    var pendingInfo by remember { mutableStateOf<ModuleZipInfo?>(null) }
    var pendingInfoLoading by remember { mutableStateOf(false) }

    // Read once: the setting only changes on the settings page, and the module screen is rebuilt
    // when it comes back.
    val installConfirmRequired = remember { SettingsRepositoryImpl().moduleInstallConfirm }

    val threshold = with(LocalDensity.current) { 100.dp.toPx() }
    val fabExpanded by remember {
        var lastIndex = 0
        var lastOffset = 0
        var scrollDelta = 0f
        var expanded = true
        derivedStateOf {
            val currentIndex = listState.firstVisibleItemIndex
            val currentOffset = listState.firstVisibleItemScrollOffset
            val delta = if (currentIndex == lastIndex) {
                (currentOffset - lastOffset).toFloat()
            } else if (currentIndex > lastIndex) {
                100f
            } else {
                -100f
            }
            scrollDelta = (scrollDelta + delta).coerceIn(-threshold, threshold)
            lastIndex = currentIndex
            lastOffset = currentOffset
            if (currentIndex == 0) {
                expanded = true
                scrollDelta = 0f
            } else if (expanded && scrollDelta >= threshold) {
                expanded = false
                scrollDelta = 0f
            } else if (!expanded && scrollDelta <= -threshold) {
                expanded = true
                scrollDelta = 0f
            }
            expanded
        }
    }

    val shortcutState = rememberModuleShortcutState(context)
    val showShortcutDialog = remember { mutableStateOf(false) }

    val confirmDialog = rememberConfirmDialog(
        onConfirm = {
            when (val request = confirmDialogState?.request) {
                is ModuleConfirmRequest.Uninstall -> actions.onUninstallModule(request.module)
                is ModuleConfirmRequest.Update -> actions.onConfirmUpdate(request)
                null -> Unit
            }
        },
        onDismiss = actions.onDismissConfirmRequest,
    )

    fun openShortcutDialogForType(type: ShortcutType) {
        shortcutState.selectType(type)
        showShortcutDialog.value = true
    }

    val pickShortcutIconLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        shortcutState.updateIconUri(uri?.toString())
    }

    fun onModuleAddShortcut(module: Module, type: ShortcutType) {
        shortcutState.bindModule(module)
        openShortcutDialogForType(type)
    }

    LaunchedEffect(confirmDialogState) {
        confirmDialogState?.let {
            confirmDialog.showConfirm(
                title = it.title,
                content = it.content,
                markdown = it.markdown,
                html = it.html,
                confirm = it.confirm,
                dismiss = it.dismiss,
            )
        }
    }

    val scope = rememberCoroutineScope()
    val snackbarJob = remember { mutableStateOf<Job?>(null) }
    ObserveAsEvents(moduleEvent) { event ->
        when (event) {
            is ModuleEffect.Toast -> {
                Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }

            is ModuleEffect.SnackBar -> {
                snackbarJob.value?.cancel()
                snackBarHost.currentSnackbarData?.dismiss()
                val softReboot = isSoftRebootPreferred()
                snackbarJob.value = scope.launch {
                    val result = snackBarHost.showSnackbar(
                        message = event.message,
                        actionLabel = resource.getString(
                            if (softReboot) R.string.reboot_soft else R.string.reboot
                        ),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        reboot(if (softReboot) "soft_reboot" else "")
                    }
                }
            }
        }
    }

    FolkScaffold(
        titleStyle = FolkTitleStyle.None,
        snackbarHostState = snackBarHost,
        topBar = {
            SearchAppBar(
                title = { Text(stringResource(R.string.module)) },
                searchText = uiState.searchStatus.searchText,
                onSearchTextChange = actions.onSearchTextChange,
                onClearClick = actions.onClearSearch,
                leadingActions = repoEntryAction,
                dropdownContent = {
                    ModuleSortMenu(uiState, actions, haptic)
                },
            )
        },
        floatingActionButton = {
            if (uiState.installButtonVisible) {
                val moduleInstall = stringResource(R.string.module_install)
                val selectZipLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult(),
                ) { activityResult ->
                    if (activityResult.resultCode != android.app.Activity.RESULT_OK) {
                        return@rememberLauncherForActivityResult
                    }
                    val data = activityResult.data ?: return@rememberLauncherForActivityResult
                    val clipData = data.clipData
                    val uris = mutableListOf<Uri>()
                    if (clipData != null) {
                        for (i in 0 until clipData.itemCount) {
                            clipData.getItemAt(i)?.uri?.let { uris.add(it) }
                        }
                    } else {
                        data.data?.let { uris.add(it) }
                    }
                    if (uris.isEmpty()) return@rememberLauncherForActivityResult

                    // Hold the selection and read its metadata; the flash starts only from the
                    // dialog's confirm. The first archive describes the selection - a multi-pick
                    // is still one flash, and its other entries are reported by count.
                    pendingInstall = uris
                    pendingInfo = null
                    pendingInfoLoading = true
                    scope.launch {
                        val info = ModuleZipReader.read(context, uris.first())
                        // The user may have dismissed or picked again while this was reading.
                        if (pendingInstall === uris) {
                            pendingInfo = info
                            pendingInfoLoading = false
                        }
                    }
                    // With the confirmation turned off the metadata is not needed, so the flash
                    // starts now instead of waiting for a dialog that will not be shown.
                    if (!installConfirmRequired) {
                        pendingInstall = null
                        pendingInfoLoading = false
                        actions.onOpenFlash(uris)
                    }
                }

                SmallExtendedFloatingActionButton(
                    modifier = Modifier.padding(bottom = bottomInnerPadding),
                    expanded = fabExpanded,
                    onClick = {
                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        }
                        selectZipLauncher.launch(intent)
                    },
                    icon = { Icon(Icons.Filled.Add, moduleInstall) },
                    text = { Text(text = moduleInstall) },
                )
            }
        },
    
        // The shell reserves the bottom bar and passes its height in as bottomInnerPadding,
        // so reserving it again here would add both allowances and push the last row under it.
        addBottomClearance = false,
    ) { innerPadding ->
        val inSearchMode = uiState.searchStatus.searchText.isNotEmpty()

        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            isRefreshing = uiState.isRefreshing,
            onRefresh = {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                actions.onRefresh()
                refreshTick.intValue++
            },
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isRefreshing = uiState.isRefreshing,
                    state = pullToRefreshState,
                )
            },
        ) {
            if (uiState.magiskInstalled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.module_magisk_conflict),
                        textAlign = TextAlign.Center,
                    )
                }
                return@PullToRefreshBox
            }

            if (inSearchMode) {
                val latestSearchResults = rememberUpdatedState(uiState.searchResults)
                ScrollToTopOnChange(searchListState, uiState.searchStatus.searchText) {
                    latestSearchResults.value
                }
                ModuleList(
                    bottomInnerPadding = bottomInnerPadding,
                    modifier = Modifier.fillMaxSize(),
                    listState = searchListState,
                    displayModules = uiState.searchResults,
                    updateInfoMap = uiState.updateInfo,
                    actions = actions,
                    onModuleAddShortcut = { module, type -> onModuleAddShortcut(module, type) },
                    preparingUpdateModuleIds = uiState.preparingUpdateModuleIds,
                )
            } else {
                val latestModuleList = rememberUpdatedState(uiState.moduleList)
                val latestRefreshing = rememberUpdatedState(uiState.isRefreshing)
                ScrollToTopOnChange(
                    listState,
                    uiState.sortEnabledFirst,
                    uiState.sortActionFirst,
                    refreshTick.intValue,
                    isBusy = { latestRefreshing.value },
                ) { latestModuleList.value }
                ModuleList(
                    bottomInnerPadding = bottomInnerPadding,
                    modifier = Modifier.fillMaxSize(),
                    listState = listState,
                    displayModules = uiState.moduleList,
                    updateInfoMap = uiState.updateInfo,
                    actions = actions,
                    onModuleAddShortcut = { module, type -> onModuleAddShortcut(module, type) },
                    preparingUpdateModuleIds = uiState.preparingUpdateModuleIds,
                )
            }
        }
    }

    ModuleShortcutDialog(
        show = showShortcutDialog.value,
        shortcutState = shortcutState,
        onDismiss = { showShortcutDialog.value = false },
        onPickShortcutIcon = { pickShortcutIconLauncher.launch("image/*") },
        onDeleteShortcut = {
            shortcutState.deleteShortcut(context)
            showShortcutDialog.value = false
        },
        onConfirmShortcut = {
            shortcutState.createShortcut(context)
            showShortcutDialog.value = false
        },
    )

    // Shown only once the metadata has been read, so the dialog never appears and then
    // reshuffles its rows as the values arrive.
    val install = pendingInstall
    if (install != null && !pendingInfoLoading) {
        ModuleInstallConfirmDialog(
            info = pendingInfo,
            fileName = install.first().lastPathSegment?.substringAfterLast('/').orEmpty(),
            fileCount = install.size,
            onDismiss = {
                pendingInstall = null
                pendingInfo = null
            },
            onConfirm = {
                pendingInstall = null
                pendingInfo = null
                actions.onOpenFlash(install)
            },
        )
    }
}

@Composable
private fun ModuleSortMenu(
    uiState: ModuleUiState,
    actions: ModuleActions,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
) {
    var showDropdown by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { showDropdown = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.settings),
            )
        }
        DropdownMenuPopup(
            expanded = showDropdown,
            onDismissRequest = { showDropdown = false },
        ) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                CheckableDropdownMenuItem(
                    text = { Text(stringResource(R.string.module_sort_action_first)) },
                    checked = uiState.sortActionFirst,
                    checkedLeadingIcon = {
                        Icon(
                            Icons.Filled.Check,
                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                            contentDescription = null,
                        )
                    },
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        actions.onToggleSortActionFirst()
                    },
                    shapes = MenuDefaults.itemShape(index = 0, count = 2),
                )
                CheckableDropdownMenuItem(
                    text = { Text(stringResource(R.string.module_sort_enabled_first)) },
                    checked = uiState.sortEnabledFirst,
                    checkedLeadingIcon = {
                        Icon(
                            Icons.Filled.Check,
                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                            contentDescription = null,
                        )
                    },
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        actions.onToggleSortEnabledFirst()
                    },
                    shapes = MenuDefaults.itemShape(index = 1, count = 2),
                )
            }
        }
    }
}

@Composable
private fun ModuleList(
    bottomInnerPadding: Dp,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    displayModules: List<Module>,
    updateInfoMap: Map<String, ModuleUpdateInfo>,
    actions: ModuleActions,
    onModuleAddShortcut: (Module, ShortcutType) -> Unit,
    /** 正在准备更新的模块 ID，对应的更新按钮显示转圈。 */
    preparingUpdateModuleIds: Set<String> = emptySet(),
) {
    if (displayModules.isEmpty()) {
        FolkStateView(
            title = stringResource(R.string.module_empty),
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(13.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 16.dp + bottomInnerPadding + 56.dp + 16.dp,
        ),
    ) {
        items(displayModules, key = { it.id }, contentType = { "module" }) { module ->
            val moduleUpdateInfo = updateInfoMap[module.id] ?: ModuleUpdateInfo.Empty
            val preparing = module.id in preparingUpdateModuleIds

            // The callbacks are remembered on the module they act on.
            //
            // A lambda written inline is a new object every time this item composes, and Compose
            // compares parameters by equality to decide whether to skip a child. With five fresh
            // lambdas per card, ModuleItem could never skip - so scrolling past a module whose
            // data had not changed still recomposed the whole card, text and banner layer included.
            // The captured values are the module and `actions`, both immutable, so keying on the
            // id is enough.
            val onUninstallClicked = remember(module.id, actions) {
                {
                    if (module.remove) {
                        actions.onUndoUninstallModule(module)
                    } else {
                        actions.onRequestUninstallConfirmation(module)
                    }
                }
            }
            val onCheckChanged = remember(module.id, actions) {
                // The switch's new value is not used: the view model reads the module's current
                // state and flips it, so passing the value through would only be a second source
                // of truth for the same thing.
                { _: Boolean -> actions.onToggleModule(module) }
            }
            // No loading dialog. The chip itself spins while the update is being prepared, so a
            // dialog on top of it only said the same thing twice - and a dialog that appears and
            // disappears for a fraction of a second reads as a flash, not as progress.
            val onUpdate = remember(module.id, actions, moduleUpdateInfo) {
                { actions.onRequestUpdateConfirmation(module, moduleUpdateInfo) }
            }
            val onAddShortcut = remember(module.id, actions) {
                { type: ShortcutType -> onModuleAddShortcut(module, type) }
            }
            val onOpenWebUi = remember(module.id, actions) {
                { if (module.hasWebUi) actions.onOpenWebUi(module) }
            }
            val onExecuteAction = remember(module.id, actions) {
                { actions.onExecuteModuleAction(module) }
            }

            ModuleItem(
                module = module,
                updateUrl = moduleUpdateInfo.downloadUrl,
                onUninstallClicked = onUninstallClicked,
                onCheckChanged = onCheckChanged,
                onUpdate = onUpdate,
                isPreparingUpdate = preparing,
                onAddShortcut = onAddShortcut,
                onOpenWebUi = onOpenWebUi,
                onExecuteAction = onExecuteAction,
            )
        }
    }
}

@Composable
private fun ModuleItem(
    module: Module,
    updateUrl: String,
    onUninstallClicked: () -> Unit,
    onCheckChanged: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    onAddShortcut: (ShortcutType) -> Unit,
    onOpenWebUi: () -> Unit,
    onExecuteAction: () -> Unit,
    /** True while this module's update is being prepared; the update chip shows a spinner. */
    isPreparingUpdate: Boolean = false,
) {
    val hasDescription = module.description.isNotBlank()
    val maxLinesLimit = LocalModuleDescriptionMaxLines.current
    var expanded by rememberSaveable(module.id) { mutableStateOf(false) }
    val canOpenWebUi = module.hasWebUi && !module.remove && module.enabled
    val cardInteractionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val textDecoration = if (module.remove) TextDecoration.LineThrough else null
    val actionButtonsEnabled = !module.remove && module.enabled
    val hasUpdate = updateUrl.isNotEmpty()
    // In icon-only mode every action drops its label, so the row fits on a narrow screen without
    // the per-chip fallback deciding differently for each one.
    val iconOnlyActions = LocalSimpleListBottomBar.current

    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            // No explicit clip: Surface already clips its content to `shape` below, so clipping
            // here as well made the card take an extra offscreen layer for every frame it was
            // drawn - one of the costs that showed up as scroll stutter on a long module list.
            .then(
                if (canOpenWebUi || hasDescription) {
                    Modifier.clickable(interactionSource = cardInteractionSource, indication = null) {
                        if (canOpenWebUi) onOpenWebUi() else expanded = !expanded
                    }
                } else {
                    Modifier
                }
            ),
        shape = FolkShape.Corner20,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box {
            ModuleBannerLayer(module)
            Column(modifier = Modifier.padding(16.dp, 14.dp, 16.dp, 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = module.name,
                        fontWeight = FontWeight.SemiBold,
                        style = FolkType.Title,
                        textDecoration = textDecoration,
                    )
                    Text(
                        text = "${stringResource(R.string.module_version)}: ${module.version}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = FolkType.Caption,
                        textDecoration = textDecoration,
                    )
                    Text(
                        text = "${stringResource(R.string.module_author)}: ${module.author}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = FolkType.Caption,
                        textDecoration = textDecoration,
                    )
                }

                Switch(
                    enabled = !module.update,
                    checked = module.enabled,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        onCheckChanged(it)
                    },
                )
            }

            if (hasDescription) {
                Spacer(Modifier.height(8.dp))
                ExpandableDescriptionText(
                    text = module.description,
                    expanded = expanded,
                    maxLinesLimit = maxLinesLimit,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = FolkType.Summary,
                    textDecoration = textDecoration,
                )
            }

            if (module.metamodule || module.zygisk) {
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (module.metamodule) {
                        StatusTag(
                            label = "META",
                            modifier = Modifier.padding(bottom = 4.dp),
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            backgroundColor = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (module.zygisk) {
                        StatusTag(
                            label = "ZYGISK",
                            modifier = Modifier.padding(bottom = 4.dp),
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                        )
                    }
                }
            }

            // A preinit directory the kernel could not stage is invisible otherwise: the module
            // looks installed and enabled, and its boot-time actions simply never run.
            module.preinitWarning()?.let { message ->
                StatusTag(
                    label = stringResource(message),
                    modifier = Modifier.padding(bottom = 4.dp),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    backgroundColor = MaterialTheme.colorScheme.errorContainer,
                )
            }

            HorizontalDivider(thickness = Dp.Hairline)

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(
                    visible = actionButtonsEnabled,
                    enter = fadeIn(animationSpec = tween(ModuleChipAnimationMillis)),
                    exit = fadeOut(animationSpec = tween(ModuleChipAnimationMillis)),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (module.hasActionScript) {
                            ModuleActionChip(
                                onClick = onExecuteAction,
                                onLongClick = { onAddShortcut(ShortcutType.Action) },
                                icon = Icons.Outlined.PlayArrow,
                                label = stringResource(R.string.action).takeIf {
                                    !iconOnlyActions && !module.hasWebUi && !hasUpdate
                                },
                            )
                        }
                        if (module.hasWebUi) {
                            ModuleActionChip(
                                onClick = onOpenWebUi,
                                onLongClick = { onAddShortcut(ShortcutType.WebUI) },
                                icon = Icons.Outlined.Code,
                                label = stringResource(R.string.open).takeIf {
                                    !iconOnlyActions && !module.hasActionScript && !hasUpdate
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f, true))

                AnimatedVisibility(
                    visible = hasUpdate,
                    // The update chip has its own, shorter timing. It appears and disappears on
                    // every refresh as the update check answers, so it is the one chip the user
                    // sees animate repeatedly - and at the shared speed it read as the list
                    // stuttering rather than as a chip arriving.
                    enter = fadeIn(animationSpec = tween(UpdateChipAnimationMillis)),
                    exit = fadeOut(animationSpec = tween(UpdateChipAnimationMillis)),
                ) {
                    Row {
                        ModuleActionChip(
                            onClick = onUpdate,
                            onLongClick = null,
                            icon = Icons.Outlined.Download,
                            label = stringResource(R.string.module_update).takeIf {
                                !iconOnlyActions && (!module.hasActionScript || !module.hasWebUi)
                            },
                            enabled = !module.remove,
                            loading = isPreparingUpdate,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                }

                ModuleActionChip(
                    onClick = onUninstallClicked,
                    onLongClick = null,
                    icon = if (module.remove) Icons.Outlined.Refresh else Icons.Outlined.Delete,
                    iconModifier = if (module.remove) Modifier.rotate(180f) else Modifier,
                    label = stringResource(
                        if (module.remove) R.string.undo else R.string.uninstall
                    ).takeIf { !iconOnlyActions && (!module.hasActionScript && !module.hasWebUi || !hasUpdate) },
                )
            }
        }
        }
    }
}

/**
 * 模块卡片的横幅背景层，移植自 FolkPatch `APMModuleItem` 的横幅渲染。
 *
 * 画在卡片内容之下、卡片底色之上，用 `matchParentSize` 跟随卡片实际尺寸（卡片是
 * wrap-content，`fillMaxSize` 会把它压成 0）。刻意**不动 Surface 颜色和任何文字颜色**：
 * 横幅按 [BackgroundConfig.getEffectiveBannerOpacity] 只有 0.18 左右的不透明度，是装饰层，
 * 加底部渐隐后完全压得住文字。
 */
@Composable
private fun BoxScope.ModuleBannerLayer(module: Module) {
    if (!BackgroundConfig.isBannerEnabled) return

    val context = LocalContext.current
    val opacity = BackgroundConfig.getEffectiveBannerOpacity(
        isWallpaperMode = BackgroundConfig.isCustomBackgroundEnabled,
        wallpaperOpacity = BackgroundConfig.customBackgroundOpacity,
    )
    // 任何一个影响解析顺序的配置变化都要重取，否则切了 API 源还会继续显示旧源的图。
    //
    // 拼成一个字符串而不是留一个 list：`produceState` 按相等性判断 key，而每次重组新建的
    // list 与上一个永远不相等，等于每帧都重启一次加载 —— 那正是「滑到哪里加载到哪里」。
    val configKey = buildString {
        append(BackgroundConfig.isBannerEnabled).append('|')
        append(BackgroundConfig.isFolkBannerEnabled).append('|')
        append(BackgroundConfig.isBannerApiModeEnabled).append('|')
        append(BackgroundConfig.bannerApiSource)
    }

    // The banner is resolved once per module, not once per composition.
    //
    // It used to pass `reload = true`, which forced a fresh resolve every time the card entered
    // composition - so scrolling back over a card re-fetched its picture instead of using the one
    // already in memory, and the list appeared to load a banner per card as it scrolled past. The
    // cache is keyed by module id, so a plain load is already the right answer; what invalidates
    // it is the source settings changing, and `configKey` is what re-keys this state for that.
    val bytes by produceState(
        initialValue = ModuleBanner.loadSync(module.id),
        key1 = module.id,
        key2 = configKey,
    ) {
        value = ModuleBanner.load(context, module.id)
    }

    // Decoded off the main thread.
    //
    // Both halves of this used to run during composition: loadSync read a file on the main thread
    // for the initial value, and the decode below ran inside `remember`, also on the main thread.
    // One card is cheap, but the list composes several at once and the result was the stutter that
    // appeared as soon as banners were switched on. The state starts as null, so a card draws its
    // banner a frame or two later instead of blocking the scroll.
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = bytes) {
        value = bytes?.let { data ->
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeByteArray(data, 0, data.size) }
                    .getOrNull()
                    ?.asImageBitmap()
            }
        }
    }
    val image = bitmap ?: return

    // The brush and its colour list are remembered on the colour they are built from. Both were
    // allocated on every composition, which is a fresh Brush and a fresh List per card per frame -
    // during a scroll that is continuous garbage for the collector to chase.
    val fadeColor = bannerFadeColor()
    val fadeBrush = remember(fadeColor) {
        Brush.verticalGradient(colors = listOf(Color.Transparent, fadeColor))
    }

    Image(
        painter = remember(image) { BitmapPainter(image) },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .matchParentSize()
            .alpha(opacity),
    )
    // 底部渐隐到卡片底色，保证压在图上的文字仍读得清。
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(fadeBrush),
    )
}

/**
 * The small pill-shaped action used on a module card. A long press is optional;
 * when it is null the press simply runs [onClick].
 */
@Composable
private fun ModuleActionChip(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconModifier: Modifier = Modifier,
    /**
     * Shows a spinner in place of the icon and label.
     *
     * Used while the update is being prepared: the row previously kept its download icon and text
     * while a dialog opened over it, so there was nothing to say the tap had been received. A bare
     * spinner is the whole of what the button should be during that time - no icon, no label, no
     * transition between them.
     */
    loading: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    androidx.compose.material3.Surface(
        modifier = modifier
            .defaultMinSize(minWidth = 52.dp, minHeight = 32.dp)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled && !loading,
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            onLongClick()
                        },
                        onClick = onClick,
                    )
                } else {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled && !loading,
                        onClick = onClick,
                    )
                }
            ),
        shape = FolkShape.CornerFull,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            } else {
                Icon(
                    modifier = Modifier
                        .size(20.dp)
                        .then(iconModifier),
                    imageVector = icon,
                    contentDescription = null,
                )
                if (label != null) {
                    Text(
                        modifier = Modifier.padding(start = 7.dp),
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleShortcutDialog(
    show: Boolean,
    shortcutState: ModuleShortcutState,
    onDismiss: () -> Unit,
    onPickShortcutIcon: () -> Unit,
    onDeleteShortcut: () -> Unit,
    onConfirmShortcut: () -> Unit,
) {
    if (!show) return

    val context = LocalContext.current
    val resources = LocalResources.current

    fun copyShortcutUrl() {
        val url = shortcutState.buildShortcutUrl() ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("HyperSU deep link", url))
        Toast.makeText(
            context,
            resources.getString(R.string.module_shortcut_scheme_copied),
            Toast.LENGTH_SHORT,
        ).show()
    }

    FolkAlertDialog(onDismissRequest = onDismiss, width = 340.dp) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.module_shortcut_title),
                style = MaterialTheme.typography.titleLarge,
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(vertical = 13.dp)
                    .size(100.dp)
                    .clip(RoundedCornerShape(25.dp)),
            ) {
                val preview = shortcutState.previewIcon
                if (preview != null) {
                    Image(
                        bitmap = preview,
                        modifier = Modifier.size(100.dp),
                        contentDescription = null,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(Color.White),
                    )
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onPickShortcutIcon,
                    colors = FolkButtonDefaults.tintedColors(),
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        text = stringResource(R.string.module_shortcut_icon_pick),
                    )
                }
                AnimatedVisibility(
                    visible = shortcutState.iconUri != shortcutState.defaultShortcutIconUri,
                    enter = expandHorizontally() + slideInHorizontally(initialOffsetX = { it }),
                    exit = shrinkHorizontally() + slideOutHorizontally(targetOffsetX = { it }),
                ) {
                    IconButton(
                        onClick = shortcutState::resetIconToDefault,
                        modifier = Modifier.padding(start = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            OutlinedTextField(
                value = shortcutState.name,
                onValueChange = shortcutState::updateName,
                label = { Text(stringResource(R.string.module_shortcut_name_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 3.dp),
            )

            if (shortcutState.hasExistingShortcut) {
                TextButton(
                    onClick = onDeleteShortcut,
                    modifier = Modifier.fillMaxWidth(),
                    colors = FolkButtonDefaults.textColors(),
                ) {
                    Text(stringResource(R.string.module_shortcut_delete))
                }
            }

            TextButton(
                onClick = ::copyShortcutUrl,
                modifier = Modifier.fillMaxWidth(),
                colors = FolkButtonDefaults.textColors(),
            ) {
                Text(stringResource(R.string.module_shortcut_copy_scheme))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(13.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
                Button(
                    onClick = onConfirmShortcut,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (shortcutState.hasExistingShortcut) {
                            stringResource(R.string.module_update)
                        } else {
                            stringResource(android.R.string.ok)
                        }
                    )
                }
            }
        }
    }
}
