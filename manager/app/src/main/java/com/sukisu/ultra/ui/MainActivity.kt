package com.sukisu.ultra.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults.flingBehavior
import androidx.compose.foundation.pager.PagerDefaults.pageNestedScrollConnection
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import com.sukisu.ultra.ui.theme.glass.GlassConfig
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.sukisu.ultra.Natives
import com.sukisu.ultra.ui.navigation.FolkBottomBar
import com.sukisu.ultra.ui.navigation.FolkNavigationRail
import com.sukisu.ultra.ui.navigation.LocalBottomBarVisible
import com.sukisu.ultra.ui.navigation.LocalIsFloatingNavMode
import com.sukisu.ultra.ui.navigation.LocalIsRootPage
import com.sukisu.ultra.ui.navigation.MainPagerState
import com.sukisu.ultra.ui.navigation.NavigationBadgeState
import com.sukisu.ultra.ui.navigation.rememberMainPagerState
import com.sukisu.ultra.ui.kernelFlash.KernelFlashScreen
import com.sukisu.ultra.ui.navigation3.HandleZipFileIntent
import com.sukisu.ultra.ui.navigation3.IntentDispatcher
import com.sukisu.ultra.ui.navigation3.LocalNavigator
import com.sukisu.ultra.ui.navigation3.Navigator
import com.sukisu.ultra.ui.navigation3.Route
import com.sukisu.ultra.ui.navigation3.rememberNavigator
import com.sukisu.ultra.ui.screen.about.AboutScreen
import com.sukisu.ultra.ui.screen.appprofile.AppProfileScreen
import com.sukisu.ultra.ui.screen.colorpalette.ColorPaletteScreen
import com.sukisu.ultra.ui.screen.executemoduleaction.ExecuteModuleActionScreen
import com.sukisu.ultra.ui.screen.flash.FlashScreen
import com.sukisu.ultra.ui.screen.home.HomePager
import com.sukisu.ultra.ui.screen.install.InstallScreen
import com.sukisu.ultra.ui.screen.module.ModulePager
import com.sukisu.ultra.ui.screen.modulerepo.ModuleRepoDetailScreen
import com.sukisu.ultra.ui.screen.modulerepo.ModuleRepoScreen
import com.sukisu.ultra.ui.screen.settings.LanguagePickerScreen
import com.sukisu.ultra.ui.screen.settings.SettingPager
import com.sukisu.ultra.ui.screen.settings.tools.ToolsScreen
import com.sukisu.ultra.ui.screen.sulog.SulogScreen
import com.sukisu.ultra.ui.screen.superuser.SuperUserPager
import com.sukisu.ultra.ui.screen.susfs.SuSFSScreen
import com.sukisu.ultra.ui.screen.template.AppProfileTemplateScreen
import com.sukisu.ultra.ui.screen.templateeditor.TemplateEditorScreen
import com.sukisu.ultra.ui.screen.umountmanager.UmountManagerScreen
import com.sukisu.ultra.ui.theme.LocalColorMode
import com.sukisu.ultra.ui.theme.BackgroundLayer
import com.sukisu.ultra.ui.theme.LocalEnableFloatingBottomBar
import com.sukisu.ultra.ui.theme.LocalEnableNavigationBadge
import com.sukisu.ultra.ui.theme.LocalModuleDescriptionMaxLines
import com.sukisu.ultra.ui.theme.HyperSUTheme
import com.sukisu.ultra.ui.component.folk.FolkLanguageSwitch
import com.sukisu.ultra.ui.util.LanguageSwitchState
import com.sukisu.ultra.ui.util.getSuperuserCount
import com.sukisu.ultra.ui.util.install
import com.sukisu.ultra.ui.util.rememberContentReady
import com.sukisu.ultra.ui.util.shouldShowSplitPane
import com.sukisu.ultra.ui.viewmodel.MainActivityViewModel
import com.sukisu.ultra.ui.viewmodel.MainPagerConfig
import com.sukisu.ultra.ui.viewmodel.SettingsViewModel
import com.sukisu.ultra.ui.viewmodel.ModuleViewModel
import com.sukisu.ultra.ui.viewmodel.SuperUserViewModel
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection
import top.yukonga.miuix.kmp.utils.PagerInterceptionMode
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import top.yukonga.miuix.kmp.utils.pagerGestureOverride

class MainActivity : ComponentActivity() {
    private val intentChannel = Channel<Intent>(capacity = Channel.BUFFERED)
    private var contentReady = false
    private var splashStartedAt = 0L
    private val splashAnimationDurationMs = 500L


    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.sukisu.ultra.ui.util.LocaleHelper.wrap(newBase))
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashStartedAt = SystemClock.uptimeMillis()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition {
            !contentReady || SystemClock.uptimeMillis() - splashStartedAt < splashAnimationDurationMs
        }

        val isManager = Natives.isManager
        if (isManager && Natives.kernelUAPIVersion == Natives.managerUAPIVersion) install()

        if (savedInstanceState == null) intent?.let { intentChannel.trySend(it) }

        setContent {
            val viewModel = viewModel<MainActivityViewModel>()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val selectedMainPage by viewModel.selectedMainPage.collectAsStateWithLifecycle()
            val appSettings = uiState.appSettings
            val darkMode = appSettings.colorMode.isDark ||
                    (appSettings.colorMode.isSystem && isSystemInDarkTheme())

            DisposableEffect(darkMode) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { darkMode },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { darkMode },
                )
                window.isNavigationBarContrastEnforced = false
                onDispose { }
            }

            val navigator = rememberNavigator(Route.Main)
            val systemDensity = LocalDensity.current
            val density = remember(systemDensity, uiState.pageScale) {
                Density(systemDensity.density * uiState.pageScale, systemDensity.fontScale)
            }

            CompositionLocalProvider(
                LocalNavigator provides navigator,
                LocalDensity provides density,
                LocalColorMode provides appSettings.colorMode.value,
                LocalEnableFloatingBottomBar provides uiState.enableFloatingBottomBar,
                LocalEnableNavigationBadge provides uiState.enableNavigationBadge,
                LocalModuleDescriptionMaxLines provides uiState.moduleDescriptionMaxLines,
                LocalBottomBarVisible provides remember { mutableStateOf(true) },
                LocalIsFloatingNavMode provides uiState.enableFloatingBottomBar,
            ) {
                HyperSUTheme(appSettings = appSettings) {
                    // Only the in-app switch path below Android 13 (or on a build with
                    // no system language page) lands here: that switch recreates the
                    // activity, and this page is what the recreation draws instead of
                    // the app content it would throw away. The system path never gets
                    // here - it switches while our task is in the background.
                    val switchingTo = LanguageSwitchState.targetTag
                    if (switchingTo != null) {
                        FolkLanguageSwitch(tag = switchingTo)
                        return@HyperSUTheme
                    }
                    IntentDispatcher(intentChannel = intentChannel)
                    HandleZipFileIntent()
                    val swipeDismiss = if (uiState.enableSwipeDismiss) {
                        if (LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl) {
                            NavSwipeDirection.RightToLeft
                        } else {
                            NavSwipeDirection.LeftToRight
                        }
                    } else {
                        NavSwipeDirection.None
                    }
                    val mainScreenEntry = @Composable {
                        MainScreen(
                            initialPage = selectedMainPage,
                            pagerInterceptionMode = uiState.pagerInterceptionMode,
                            onPageChanged = viewModel::setSelectedMainPage,
                        )
                    }

                    // Wallpaper behind everything; the content box sits above it so the
                    // transparent page background lets the image read through.
                    Box(modifier = Modifier.fillMaxSize()) {
                        BackgroundLayer(
                            currentRoute = navigator.current(),
                            mainPage = selectedMainPage,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(1f),
                        ) {
                            NavDisplay(
                                backStack = navigator.backStack,
                                effects = NavDisplayEffects(cornerClipRadius = rememberNavSystemCornerRadius()),
                                onBack = {
                                    when (val top = navigator.current()) {
                                        is Route.TemplateEditor -> {
                                            if (!top.readOnly) {
                                                navigator.setResult("template_edit", true)
                                            } else {
                                                navigator.pop()
                                            }
                                        }

                                        else -> navigator.pop()
                                    }
                                }) {
                                entry<Route.Main>(swipeDismiss = swipeDismiss) { mainScreenEntry() }
                                entry<Route.About>(swipeDismiss = swipeDismiss) { AboutScreen() }
                                entry<Route.Sulog>(swipeDismiss = swipeDismiss) { SulogScreen() }
                                entry<Route.ColorPalette>(swipeDismiss = swipeDismiss) { ColorPaletteScreen() }
                                entry<Route.AppProfileTemplate>(swipeDismiss = swipeDismiss) { AppProfileTemplateScreen() }
                                entry<Route.TemplateEditor>(swipeDismiss = swipeDismiss) { key -> TemplateEditorScreen(key.template, key.readOnly) }
                                entry<Route.AppProfile>(swipeDismiss = swipeDismiss) { key -> AppProfileScreen(key.uid) }
                                entry<Route.ModuleRepo>(swipeDismiss = swipeDismiss) { ModuleRepoScreen() }
                                entry<Route.ModuleRepoDetail>(swipeDismiss = swipeDismiss) { key -> ModuleRepoDetailScreen(key.module) }
                                entry<Route.Install>(swipeDismiss = swipeDismiss) { key -> InstallScreen(preselectedKernelUri = key.preselectedKernelUri) }
                                entry<Route.Flash>(swipeDismiss = swipeDismiss) { key -> FlashScreen(key.flashIt) }
                                entry<Route.ExecuteModuleAction>(swipeDismiss = swipeDismiss) { key ->
                                    ExecuteModuleActionScreen(
                                        key.moduleId,
                                        key.fromShortcut
                                    )
                                }
                                entry<Route.Home>(swipeDismiss = swipeDismiss) { mainScreenEntry() }
                                entry<Route.SuperUser>(swipeDismiss = swipeDismiss) { mainScreenEntry() }
                                entry<Route.Module>(swipeDismiss = swipeDismiss) { mainScreenEntry() }
                                entry<Route.Settings>(swipeDismiss = swipeDismiss) { mainScreenEntry() }
                                entry<Route.KernelFlash>(swipeDismiss = swipeDismiss) { key ->
                                    KernelFlashScreen(
                                        key.kernelUri,
                                        key.selectedSlot,
                                        key.kpmPatchEnabled,
                                        key.kpmUndoPatch
                                    )
                                }
                                entry<Route.SuSFS>(swipeDismiss = swipeDismiss) { SuSFSScreen() }
                                entry<Route.Tool>(swipeDismiss = swipeDismiss) { ToolsScreen() }
                                entry<Route.UmountManager>(swipeDismiss = swipeDismiss) { UmountManagerScreen() }
                                entry<Route.Language>(swipeDismiss = swipeDismiss) {
                                    // The language lives in SettingsViewModel, which owns both the
                                    // stored value and the switch that recreates the activity.
                                    val settingsViewModel = viewModel<SettingsViewModel>()
                                    val settingsState by settingsViewModel.uiState
                                        .collectAsStateWithLifecycle()
                                    LanguagePickerScreen(
                                        currentTag = settingsState.appLanguage,
                                        onBack = { navigator.pop() },
                                        onSelected = { tag ->
                                            settingsViewModel.setLanguage(this@MainActivity, tag)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    // Fires once per activity instance, when the first frame is composed: the
                    // startup sound is played after the splash condition can be released rather
                    // than during onCreate, and never again on a configuration recreation.
                    SideEffect {
                        if (!contentReady) {
                            contentReady = true
                            com.sukisu.ultra.ui.util.SoundEffectManager.playStartup(this@MainActivity)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentChannel.trySend(intent)
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainScreen(
    initialPage: Int = 0,
    pagerInterceptionMode: Int = PagerInterceptionMode.CrossAxisInterceptor.ordinal,
    onPageChanged: (Int) -> Unit = {},
) {
    val navController = LocalNavigator.current
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { MainPagerConfig.PAGE_COUNT })
    val mainPagerState = rememberMainPagerState(
        pagerState = pagerState,
        animatePageChanges = true,
    )
    val isFullFeatured = Natives.isFullFeatured()
    val pagerMode = PagerInterceptionMode.entries.getOrElse(pagerInterceptionMode) {
        PagerInterceptionMode.Native
    }
    val interceptPagerGestures = pagerMode == PagerInterceptionMode.CrossAxisInterceptor
    var userScrollEnabled by remember(isFullFeatured) { mutableStateOf(isFullFeatured) }

    val enableNavigationBadge = LocalEnableNavigationBadge.current
    val badgeEnabled = enableNavigationBadge && isFullFeatured
    val moduleViewModel = viewModel<ModuleViewModel>()
    val moduleUiState by moduleViewModel.uiState.collectAsStateWithLifecycle()

    val superUserViewModel = viewModel<SuperUserViewModel>()
    val grantedUidCount by remember(superUserViewModel) {
        superUserViewModel.uiState
            .map { state -> state.groupedApps.count { it.anyAllowSu } }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(0)

    var startupPreloadStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isFullFeatured) {
        if (!isFullFeatured || startupPreloadStarted) {
            return@LaunchedEffect
        }

        moduleViewModel.initializePreferences()
        val moduleState = moduleViewModel.uiState.value
        if (!moduleState.hasLoaded) {
            if (!moduleState.isRefreshing) moduleViewModel.fetchModuleList()
            moduleViewModel.uiState.first { it.hasLoaded }
        }
        moduleViewModel.syncModuleUpdateInfo(moduleViewModel.uiState.value.modules)

        val superUserState = superUserViewModel.uiState.value
        if (!superUserState.hasLoaded) {
            superUserViewModel.initializePreferences()
            if (superUserState.isRefreshing) {
                superUserViewModel.uiState.first { it.hasLoaded }
            } else {
                superUserViewModel.loadAppList().join()
            }
        }

        startupPreloadStarted = true
    }

    // Loading the app list just for a badge is too expensive; read the kernel allowlist instead.
    var superuserCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(badgeEnabled, grantedUidCount) {
        superuserCount = if (badgeEnabled) withContext(Dispatchers.IO) { getSuperuserCount() } else 0
    }

    val navigationBadge = if (badgeEnabled) {
        NavigationBadgeState(
            superuserCount = superuserCount,
            // A module queued for removal (`remove`) is still `enabled` on disk until the next
            // reboot, but it is not part of what the user counts as enabled - counting it made
            // the badge sit one (or more) higher than the module list.
            moduleEnabledCount = moduleUiState.modules.count { it.enabled && !it.remove },
            moduleUpdatableCount = moduleUiState.updateInfo.count { it.value.downloadUrl.isNotBlank() },
        )
    } else {
        NavigationBadgeState()
    }

    // Large screens with a docked bar switch to the navigation rail; the floating
    // capsule bar always stays at the bottom (FolkPatch nav shell behaviour).
    val useNavigationRail = shouldShowSplitPane() && !enableFloatingBottomBar

    val settledPage = mainPagerState.pagerState.settledPage
    LaunchedEffect(settledPage) {
        onPageChanged(settledPage)
    }

    val currentPage = mainPagerState.pagerState.currentPage
    LaunchedEffect(currentPage) {
        mainPagerState.syncPage()
    }

    MainScreenBackHandler(mainPagerState, navController)

    // ReSukiSU's bar refracts the page behind it: the handle is created here, the pager
    // records itself into it below, and the bar samples it. The opaque base keeps the
    // captured layer from coming back empty over a wallpaper.
    val navBarSurface = MaterialTheme.colorScheme.surface
    val navBarBackdrop = rememberLayerBackdrop {
        drawRect(navBarSurface)
        drawContent()
    }

    val contentReady = rememberContentReady()
    val pagerContent: @Composable (Dp) -> Unit = { bottomInnerPadding ->
        HorizontalPager(
            modifier = Modifier
                .fillMaxWidth()
                .pagerGestureOverride(
                    pagerState = mainPagerState.pagerState,
                    mode = pagerMode,
                    enabled = userScrollEnabled,
                )
                .then(
                    if (enableFloatingBottomBar && GlassConfig.blurEnabled) {
                        Modifier.layerBackdrop(navBarBackdrop)
                    } else {
                        Modifier
                    },
                ),
            state = mainPagerState.pagerState,
            beyondViewportPageCount = if (contentReady) 3 else 0,
            overscrollEffect = null,
            userScrollEnabled = userScrollEnabled && !interceptPagerGestures,
            pageNestedScrollConnection = if (interceptPagerGestures) {
                PagerGestureNestedScrollConnection
            } else {
                pageNestedScrollConnection(
                    state = mainPagerState.pagerState,
                    orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                )
            },
            flingBehavior = flingBehavior(
                state = mainPagerState.pagerState,
                snapAnimationSpec = PagerNavigationSpringSpec,
            ),
        ) { page ->
            val isCurrentPage = page == settledPage
            when (page) {
                0 -> if (contentReady || isCurrentPage) HomePager(navController, bottomInnerPadding, isCurrentPage)
                1 -> if (contentReady || isCurrentPage) SuperUserPager(navController, bottomInnerPadding, isCurrentPage)
                2 -> if (contentReady || isCurrentPage) ModulePager(bottomInnerPadding, isCurrentPage)
                3 -> if (contentReady || isCurrentPage) SettingPager(navController, bottomInnerPadding, isCurrentPage)
            }
        }
    }

    // Every page inside the tab pager is a root page: it is drawn directly over the wallpaper,
    // with no entry beneath it, so it must not paint an opaque container or the image disappears.
    CompositionLocalProvider(LocalIsRootPage provides true) {
        if (useNavigationRail) {
            val startInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
                .only(WindowInsetsSides.Start)
            val navBarBottomPadding = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

            Scaffold(containerColor = MaterialTheme.colorScheme.background) {
                Row {
                    FolkNavigationRail(
                        selectedIndex = mainPagerState.selectedPage,
                        onSelectedIndexChange = mainPagerState::animateToPage,
                        badge = navigationBadge,
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .consumeWindowInsets(startInsets)
                    ) {
                        pagerContent(navBarBottomPadding)
                    }
                }
            }
        } else {
            Scaffold(
                bottomBar = {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        FolkBottomBar(
                            selectedIndex = mainPagerState.selectedPage,
                            onSelectedIndexChange = mainPagerState::animateToPage,
                            badge = navigationBadge,
                            isFloating = enableFloatingBottomBar,
                            backdrop = navBarBackdrop,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.background,
            ) { innerPadding ->
                pagerContent(innerPadding.calculateBottomPadding())
            }
        }
    }
}

@Composable
private fun MainScreenBackHandler(
    mainState: MainPagerState,
    navController: Navigator,
) {
    val isPagerBackHandlerEnabled by remember {
        derivedStateOf {
            navController.current() is Route.Main && navController.backStackSize() == 1 && mainState.selectedPage != 0
        }
    }

    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)

    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = isPagerBackHandlerEnabled,
        onBackCompleted = {
            mainState.animateToPage(0)
        }
    )
}
