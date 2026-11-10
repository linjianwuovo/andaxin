package com.ljyh.mei


import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.zIndex
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.NavDisplayTransitionEffects
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.ljyh.mei.constants.AccentColorKey
import com.ljyh.mei.constants.AppAppearance
import com.ljyh.mei.constants.AppAppearanceKey
import com.ljyh.mei.constants.AppBarHeight
import com.ljyh.mei.constants.BetaUpdatesEnabledKey
import com.ljyh.mei.constants.CloudMusicEnabledKey
import com.ljyh.mei.constants.CloudMusicTabEnabledKey
import com.ljyh.mei.constants.DefaultAccentColorArgb
import com.ljyh.mei.constants.DeviceIdKey
import com.ljyh.mei.constants.DownloadsEnabledKey
import com.ljyh.mei.constants.DownloadsTabEnabledKey
import com.ljyh.mei.constants.DynamicThemeKey
import com.ljyh.mei.constants.FindMusicTabEnabledKey
import com.ljyh.mei.constants.KeepBottomBarExpandedKey
import com.ljyh.mei.constants.LastSelectedTabKey
import com.ljyh.mei.constants.LibraryTabEnabledKey
import com.ljyh.mei.constants.LiquidGlassEnabledKey
import com.ljyh.mei.constants.ListeningHistoryEnabledKey
import com.ljyh.mei.constants.ListeningHistoryTabEnabledKey
import com.ljyh.mei.constants.MiniPlayerHeight
import com.ljyh.mei.constants.NavigationBarBottomMargin
import com.ljyh.mei.constants.NavigationBarHeight
import com.ljyh.mei.constants.NavigationTabOrderKey
import com.ljyh.mei.constants.PlayerKeepScreenOnKey
import com.ljyh.mei.constants.PodcastsEnabledKey
import com.ljyh.mei.constants.PodcastsTabEnabledKey
import com.ljyh.mei.constants.RecognizeClipboardLinksKey
import com.ljyh.mei.constants.ReduceTransparencyKey
import com.ljyh.mei.data.model.api.GetSongDetails
import com.ljyh.mei.data.model.melox.NeteaseMusicLink
import com.ljyh.mei.data.model.melox.NeteaseMusicLinkParser
import com.ljyh.mei.data.model.toMediaItem
import com.ljyh.mei.data.model.UserData
import com.ljyh.mei.data.network.api.ApiService
import com.ljyh.mei.di.AppDatabase
import com.ljyh.mei.di.repository.ColorRepository
import com.ljyh.mei.playback.MusicService
import com.ljyh.mei.playback.PlayerConnection
import com.ljyh.mei.playback.queue.ListQueue
import com.ljyh.mei.ui.component.ClipboardLinkPrompt
import com.ljyh.mei.ui.component.IconButton
import com.ljyh.mei.ui.component.player.BottomSheetPlayer
import com.ljyh.mei.ui.component.player.FloatingLyricsPipBackdrop
import com.ljyh.mei.ui.component.player.FloatingLyricsPipScreen
import com.ljyh.mei.ui.component.player.LocalMiniPlayerAppearance
import com.ljyh.mei.ui.component.sheet.BottomSheetState
import com.ljyh.mei.ui.component.sheet.collapsedAnchor
import com.ljyh.mei.ui.component.sheet.dismissedAnchor
import com.ljyh.mei.ui.component.sheet.rememberBottomSheetState
import com.ljyh.mei.ui.component.utils.appBarScrollBehavior
import com.ljyh.mei.ui.component.utils.resetHeightOffset
import com.ljyh.mei.ui.component.VersionUpdateAlert
import com.ljyh.mei.ui.glass.AnimatedBottomNavigationRow
import com.ljyh.mei.ui.glass.AnimatedHomeSearchButton
import com.ljyh.mei.ui.glass.CompactBottomControlIconSize
import com.ljyh.mei.ui.glass.defaultGlassColors
import com.ljyh.mei.ui.glass.GlassAppearance
import com.ljyh.mei.ui.glass.GlassBottomBar
import com.ljyh.mei.ui.glass.GlassIconButton
import com.ljyh.mei.ui.glass.GlassSurfaceStyle
import com.ljyh.mei.ui.glass.GlassTabItem
import com.ljyh.mei.ui.glass.IosBottomSearchToolbar
import com.ljyh.mei.ui.glass.LocalBlurBackdrop
import com.ljyh.mei.ui.glass.LocalGlassAppearance
import com.ljyh.mei.ui.glass.LocalGlassBackdrop
import com.ljyh.mei.ui.glass.LocalGlassColors
import com.ljyh.mei.ui.glass.rememberSharedGlassBackdrop
import com.ljyh.mei.ui.glass.SfIcon
import com.ljyh.mei.ui.glass.SfSymbol
import com.ljyh.mei.ui.glass.trackBackdropPosition
import com.ljyh.mei.ui.local.LocalDatabase
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.local.LocalPageAppearance
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import com.ljyh.mei.ui.local.LocalPlayerConnection
import com.ljyh.mei.ui.local.LocalUserData
import com.ljyh.mei.ui.navigation.MeiNavEntryViewModelStoreOwner
import com.ljyh.mei.ui.navigation.MeiNavigator
import com.ljyh.mei.ui.navigation.MeiRoute
import com.ljyh.mei.ui.screen.account.NeteaseLegacyWebSessionNotice
import com.ljyh.mei.ui.screen.Index
import com.ljyh.mei.ui.screen.navigationEntry
import com.ljyh.mei.ui.screen.Screen
import com.ljyh.mei.ui.screen.search.SearchScreen
import com.ljyh.mei.ui.theme.LocalAccentSeedColor
import com.ljyh.mei.ui.theme.MusicTheme
import com.ljyh.mei.utils.cache.preloadImage
import com.ljyh.mei.utils.dataStore
import com.ljyh.mei.utils.get
import com.ljyh.mei.utils.log.CrashHandler
import com.ljyh.mei.utils.log.FileLoggingTree
import com.ljyh.mei.utils.netease.NeteaseUtils.getAndroidId
import com.ljyh.mei.utils.rememberEnumPreference
import com.ljyh.mei.utils.rememberPreference
import com.ljyh.mei.utils.VersionUpdateChecker
import com.ljyh.mei.utils.VersionUpdateResult
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * MeiloX 音乐屏的 Compose 主体。
 *
 * 原来整段住在 MainActivity.setContent 里，直接读 Activity 的 window / lifecycleScope /
 * Hilt 注入字段。改成宿主的一页之后没有自己的 Activity 了，所以这里：
 *  · 用 LocalActivity 拿宿主 Activity（window、cacheDir、getString 都走它）；
 *  · 三个 Hilt 单例改走 @EntryPoint 从 Application 组件取；
 *  · PiP 状态恒为 false（宿主没有 PiP，FloatingLyricsPip 那条路自己 return）。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface MusicScreenEntryPoint {
    fun database(): AppDatabase
    fun colorRepository(): ColorRepository
    fun apiService(): ApiService
}

@androidx.annotation.OptIn(UnstableApi::class)
@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    modifier: Modifier = Modifier,
    // 宿主首页音乐胶囊点进来时直接展开播放器（封面页），不落在音乐首页。
    openInPlayer: Boolean = false,
    // 只渲染播放器封面，不渲染音乐主页（NavDisplay、底栏、搜索）。宿主把它当成自己的一页，
    // 返回时直接退出这一页回宿主，而不是把封面收成 mini player 露出下面的音乐主页。
    coverOnly: Boolean = false,
    // coverOnly 下返回键 / 下拉收起要交给宿主 popBackStack。
    onCoverBack: (() -> Unit)? = null,
) {
    val activity = LocalActivity.current as? ComponentActivity
        ?: error("MusicScreen 需要一个 ComponentActivity 宿主")
    val entryPoint = remember(activity) {
        EntryPointAccessors.fromApplication(activity.applicationContext, MusicScreenEntryPoint::class.java)
    }
    val database = entryPoint.database()
    val colorRepository = entryPoint.colorRepository()
    val apiService = entryPoint.apiService()
    var userData by remember { mutableStateOf(UserData.VISITOR) }
    val pictureInPictureMode = false
    val context = activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val backStack = rememberNavBackStack(MeiRoute(Screen.Home.route))
    val navController = remember(backStack) {
        MeiNavigator(context = context, backStack = backStack)
    }
    val pageAppearanceOverrides = remember {
        mutableStateMapOf<String, Pair<Any, Boolean>>()
    }
    val registerPageAppearance: (String, Any, Boolean?) -> Unit = remember {
        { route, owner, isDark ->
            if (isDark != null) {
                pageAppearanceOverrides[route] = owner to isDark
            } else if (pageAppearanceOverrides[route]?.first === owner) {
                pageAppearanceOverrides.remove(route)
            }
        }
    }
    // Search chrome belongs to the search destination, including back navigation.
    val active = navController.currentRoute == Screen.Search.route
    var focusSearchOnEntry by remember { mutableStateOf(false) }
    val dynamicTheme by rememberPreference(DynamicThemeKey, defaultValue = true)
    val accentColorArgb by rememberPreference(AccentColorKey, DefaultAccentColorArgb)
    val appAppearance by rememberEnumPreference(AppAppearanceKey, AppAppearance.System)
    val (lastSelectedTab, setLastSelectedTab) = rememberPreference(LastSelectedTabKey, Index.Home.name)
    val recognizeClipboardLinks by rememberPreference(RecognizeClipboardLinksKey, false)
    val navigationPreferences by context.dataStore.data
        .collectAsState<Preferences, Preferences?>(initial = null)
    val keepBottomBarExpanded = navigationPreferences?.get(KeepBottomBarExpandedKey) ?: false
    val liquidGlassEnabled = navigationPreferences?.get(LiquidGlassEnabledKey) ?: true
    val reduceTransparency = navigationPreferences?.get(ReduceTransparencyKey) ?: false
    val podcastsEnabled = navigationPreferences?.get(PodcastsEnabledKey) ?: true
    val downloadsEnabled = navigationPreferences?.get(DownloadsEnabledKey) ?: true
    val cloudMusicEnabled = navigationPreferences?.get(CloudMusicEnabledKey) ?: true
    val listeningHistoryEnabled = navigationPreferences?.get(ListeningHistoryEnabledKey) ?: true
    val findMusicTabEnabled = navigationPreferences?.get(FindMusicTabEnabledKey) ?: true
    val libraryTabEnabled = navigationPreferences?.get(LibraryTabEnabledKey) ?: true
    val podcastsTabEnabled = navigationPreferences?.get(PodcastsTabEnabledKey) ?: false
    val downloadsTabEnabled = navigationPreferences?.get(DownloadsTabEnabledKey) ?: false
    val cloudMusicTabEnabled = navigationPreferences?.get(CloudMusicTabEnabledKey) ?: false
    val listeningHistoryTabEnabled = navigationPreferences?.get(ListeningHistoryTabEnabledKey) ?: false
    val keepScreenOnInPlayer by rememberPreference(PlayerKeepScreenOnKey, false)
    val navigationTabOrder = navigationPreferences?.get(NavigationTabOrderKey)
        ?: Index.DefaultOrder.joinToString(",", transform = Index::name)
    var playerConnection by remember { mutableStateOf<PlayerConnection?>(null) }
    var clipboardLink by remember { mutableStateOf<NeteaseMusicLink?>(null) }
    var clipboardInspected by rememberSaveable { mutableStateOf(false) }
    var startupUpdateResult by remember { mutableStateOf<VersionUpdateResult?>(null) }

    LaunchedEffect(Unit) {
        try {
            val betaUpdatesEnabled = context.dataStore.data.first()[BetaUpdatesEnabledKey] ?: false
            val result = VersionUpdateChecker.check(
                BuildConfig.VERSION_NAME,
                betaUpdatesEnabled = betaUpdatesEnabled,
            )
            val currentBetaUpdatesEnabled = context.dataStore.data.first()[BetaUpdatesEnabledKey] ?: false
            if (result is VersionUpdateResult.UpdateAvailable &&
                result.comparisonKnown &&
                currentBetaUpdatesEnabled == betaUpdatesEnabled
            ) {
                startupUpdateResult = result
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return@LaunchedEffect
        }
    }

    var isMeasured by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val intent = Intent(context, MusicService::class.java)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                Timber.tag("MainActivity").d("Service Connected") // 添加日志
                if (service is MusicService.MusicBinder) {
                    // 更新 State，触发 Recomposition
                    playerConnection = PlayerConnection(
                        context,
                        service,
                        database,
                        lifecycleOwner.lifecycleScope
                    )
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Timber.tag("MainActivity").d("Service Disconnected")
                playerConnection?.dispose() // 假设你有 dispose 方法清理资源
                playerConnection = null
            }
        }

        // 启动并绑定服务
        context.startService(intent)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)

        onDispose {
            // Compose 销毁时解绑
            playerConnection?.dispose()
            context.unbindService(connection)
            playerConnection = null
        }
    }
    var targetThemeColor by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(playerConnection, dynamicTheme) {
        Timber.tag("MainActivity").d("playerConnection: $playerConnection")
        if (!dynamicTheme) {
            targetThemeColor = null
            return@LaunchedEffect
        }
        val playerConnection = playerConnection ?: return@LaunchedEffect
        val player = playerConnection.service.player
        playerConnection.service.currentMediaMetadata.collectLatest { song ->
            if (song == null) {
                targetThemeColor = null
                return@collectLatest
            }
            val context = activity
            Timber.tag("MainActivity").d("获取当前歌曲颜色: $song")
            val color = colorRepository.getColorOrExtract(context, song.coverUrl)
            targetThemeColor = color.takeUnless { it == Color.Black }
            Timber.tag("MainActivity").d("获取歌曲颜色: $targetThemeColor")

            val nextIndex = player.nextMediaItemIndex
            if (nextIndex != C.INDEX_UNSET) {
                val nextUrl = player.getMediaItemAt(nextIndex).mediaMetadata.artworkUri?.toString()
                if (!nextUrl.isNullOrEmpty()) {
                    Timber.tag("MainActivity").d("获取下一首歌曲颜色: $nextUrl")
                    launch(Dispatchers.IO) {
                        colorRepository.getColorOrExtract(context, nextUrl)
                        preloadImage(context, nextUrl)
                    }
                }
            }
        }

    }
    var navigationBarVisible by remember { mutableStateOf(true) }
    val navigationBarExpanded = keepBottomBarExpanded || navigationBarVisible
    val keepBottomBarExpandedState = rememberUpdatedState(keepBottomBarExpanded)
    LaunchedEffect(keepBottomBarExpanded) {
        if (keepBottomBarExpanded) navigationBarVisible = true
    }
    val scrollPlayerSheet = remember { mutableStateOf<BottomSheetState?>(null) }
    val searchActiveState = rememberUpdatedState(active)
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {

            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (keepBottomBarExpandedState.value) return Offset.Zero
                // Read the actual sheet state for every event. Page scrolling remains
                // unconsumed, but cannot move the mini player's return target mid-flight.
                val sheet = scrollPlayerSheet.value
                if (sheet != null && !sheet.isCollapsed && !sheet.isDismissed) {
                    return Offset.Zero
                }
                val route = navController.currentRoute
                val handlesNavigationBar = !searchActiveState.value &&
                    (route == null ||
                        route in homeNavigationRoutes ||
                        route == Screen.Search.route)
                if (!handlesNavigationBar) return Offset.Zero

                if (available.y < -10f) {
                    // 向下滑
                    navigationBarVisible = false
                }

                if (available.y > 10f) {
                    // 向上滑
                    navigationBarVisible = true
                }

                return Offset.Zero
            }
        }
    }



    val systemDark = isSystemInDarkTheme()
    val effectiveDark = when (appAppearance) {
        AppAppearance.System -> systemDark
        AppAppearance.Light -> false
        AppAppearance.Dark -> true
    }
    val defaultAccent = if (effectiveDark) Color(0xFFFF4245) else Color(DefaultAccentColorArgb.toInt())
    val configuredAccent = if (accentColorArgb == DefaultAccentColorArgb) {
        defaultAccent
    } else {
        Color(accentColorArgb.toInt())
    }
    val effectiveAccent = if (dynamicTheme) targetThemeColor ?: defaultAccent else configuredAccent
    val accentSeedColor = if (dynamicTheme) {
        targetThemeColor
    } else {
        Color(accentColorArgb.toInt()).takeUnless { accentColorArgb == DefaultAccentColorArgb }
    }
    MusicTheme(
        seedColor = effectiveAccent,
        isDark = effectiveDark,
    ) {
        val glassColors = defaultGlassColors(
            isDark = effectiveDark,
            accent = MaterialTheme.colorScheme.primary,
        )
        // The regular page backdrop is a static color, so avoid recording a full-screen
        // layer just to replay the same pixels for every glass consumer.
        val staticGlassBackdrop = rememberCanvasBackdrop {
            drawRect(glassColors.groupedBackground)
        }
        // Picture-in-picture still needs a recorded cover image backdrop.
        val pipBackdrop = rememberLayerBackdrop()
        val glassBackdrop = if (pictureInPictureMode && playerConnection != null) {
            pipBackdrop
        } else {
            staticGlassBackdrop
        }
        // Keep the base page backdrop and the bottom controls' sample
        // layer separate. Page glass samples [glassBackdrop], while the
        // bottom layer records the page after it has rendered. Controls
        // then sample [bottomBackdrop] without sampling themselves.
        val bottomBackdrop = rememberLayerBackdrop()
        // Popups are hosted in a separate Android window. Wrap the recorded page layer
        // before combining it so its sample coordinates use the popup's real screen
        // position rather than the popup-local overshoot origin.
        val bottomControlsBackdrop = rememberSharedGlassBackdrop(glassBackdrop, bottomBackdrop)
        CompositionLocalProvider(
            LocalGlassBackdrop provides glassBackdrop,
            LocalGlassColors provides glassColors,
            LocalGlassAppearance provides GlassAppearance(liquidGlassEnabled, reduceTransparency),
            LocalBlurBackdrop provides bottomControlsBackdrop,
            LocalAccentSeedColor provides accentSeedColor,
            LocalPageAppearance provides registerPageAppearance,
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    // 封面模式下这一页要透出宿主首页：胶囊从首页原位涨成封面，
                    // 涨到盖满之前底下的首页得还在，否则会先闪一屏纯色。
                    .then(if (coverOnly) Modifier else Modifier.background(MaterialTheme.colorScheme.background))
                    .nestedScroll(nestedScrollConnection)
                    .onSizeChanged {
                        isMeasured = true
                    }
            ) {
            val focusManager = LocalFocusManager.current
            val density = LocalDensity.current
            val windowsInsets = WindowInsets.systemBars
            val currentRoute = navController.currentRoute
            val handlesKeyboardInsets = active || currentRoute == Screen.Search.route ||
                currentRoute?.startsWith("${Screen.PrivateConversation.route}/") == true ||
                currentRoute?.startsWith("${Screen.Comment.route}/") == true
            DisposableEffect(handlesKeyboardInsets) {
                val previousMode = activity.window.attributes.softInputMode
                if (handlesKeyboardInsets) {
                    activity.window.setSoftInputMode(
                        (previousMode and android.view.WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
                            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
                    )
                }
                onDispose {
                    if (handlesKeyboardInsets) activity.window.setSoftInputMode(previousMode)
                }
            }
            val imeInsets = WindowInsets.ime
            val searchBottomInset = maxOf(with(density) { imeInsets.getBottom(this).toDp() },
                WindowInsets.systemBars.asPaddingValues().calculateBottomPadding())

            val bottomInset by remember {
                derivedStateOf {
                    with(density) {
                        windowsInsets.getBottom(density).toDp()
                    }
                }
            }

            val navigationItems = remember(
                podcastsEnabled,
                downloadsEnabled,
                cloudMusicEnabled,
                listeningHistoryEnabled,
                findMusicTabEnabled,
                libraryTabEnabled,
                podcastsTabEnabled,
                downloadsTabEnabled,
                cloudMusicTabEnabled,
                listeningHistoryTabEnabled,
                navigationTabOrder,
            ) {
                val available = buildList {
                    add(Index.Home)
                    if (findMusicTabEnabled) add(Index.FindMusic)
                    if (podcastsEnabled && podcastsTabEnabled) add(Index.Podcasts)
                    if (libraryTabEnabled) add(Index.Library)
                    if (downloadsEnabled && downloadsTabEnabled) add(Index.Downloads)
                    if (cloudMusicEnabled && cloudMusicTabEnabled) add(Index.Cloud)
                    if (listeningHistoryEnabled && listeningHistoryTabEnabled) add(Index.History)
                    add(Index.Settings)
                }
                val ordered = navigationTabOrder.split(',')
                    .mapNotNull { name -> Index.entries.firstOrNull { it.name == name } }
                    .filter { it in available }
                    .distinct()
                    .toMutableList()
                available.forEach { item ->
                    if (item !in ordered) {
                        val settingsIndex = ordered.indexOf(Index.Settings)
                        if (settingsIndex >= 0 && item != Index.Settings) ordered.add(settingsIndex, item)
                        else ordered.add(item)
                    }
                }
                ordered.remove(Index.Home)
                ordered.add(0, Index.Home)
                ordered.remove(Index.Settings)
                ordered.add(Index.Settings)
                ordered
            }


            val shouldAllowNavigationBar = remember(currentRoute, active) {
                (currentRoute == null ||
                        navigationItems.fastAny { it.route == currentRoute } ||
                        currentRoute == Screen.Search.route) &&
                        !active
            }
            // Route visibility must not reset the expanded/compact shape. This keeps a
            // MiniNav compact while it fades out. The expanded preference overrides
            // scrolling without changing which routes can display the navigation bar.
            val shouldCompactNavigationBar = !navigationBarExpanded
            // 封面模式下这一页没有底栏，胶囊也不该按「收球」进度下移：
            // 保持 progress = 0，收起态就落在宿主首页那条胶囊的同一高度上，
            // 涨起来的第一帧与宿主那一帧对齐，看不出换页。
            val shouldCompactMiniPlayer =
                shouldAllowNavigationBar && !navigationBarExpanded && !coverOnly



            val searchBarFocusRequester = remember { FocusRequester() }
            // Keep the anchor stable while the navigation morphs. The mini player
            // moves inside the collapsed host with the same continuous spring.
            // 封面模式下收起态要停在「宿主首页那条胶囊」的同一高度：宿主首页的胶囊
            // 就压在底栏（NavigationBarHeight）之上，这里照同一个预留量算，涨起来的第一帧
            // 才和宿主那一帧重合，看不出换页。
            val collapsedBottomReservation = if (shouldAllowNavigationBar || active || coverOnly) {
                NavigationBarHeight
            } else {
                NavigationBarBottomMargin
            }
            val collapsedBound = bottomInset +
                collapsedBottomReservation +
                MiniPlayerHeight

            val compactNavigationProgress = animateFloatAsState(
                targetValue = if (shouldCompactNavigationBar) 1f else 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "CompactBottomNavigation",
            )
            val compactMiniPlayerProgress = animateFloatAsState(
                targetValue = if (shouldCompactMiniPlayer) 1f else 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "CompactMiniPlayer",
            )
            val playerBottomSheetState = rememberBottomSheetState(
                dismissedBound = 0.dp,
                collapsedBound = collapsedBound,
                expandedBound = maxHeight,
                // 封面模式也保留「胶囊涨成封面」这段动效：先停在收起态（就是宿主首页
                // 那条胶囊的位置），再由 expandSoft() 涨起来。这一页底下没有音乐主页，
                // 所以涨的过程中不会露出音乐界面。
                initialAnchor = if (coverOnly) collapsedAnchor else dismissedAnchor,
            )
            val playerBackgroundReady = remember { mutableStateOf(false) }
            val onPlayerBackgroundReadyChanged = remember {
                { ready: Boolean -> playerBackgroundReady.value = ready }
            }
            // 封面模式退出：先把封面收回成胶囊（collapseSoft），等这段动效跑完再让宿主
            // 把这一页移除。直接移除会把 collapse 动画掐掉，退出就成了硬切。
            val coverBackScope = rememberCoroutineScope()
            val currentCoverBack = rememberUpdatedState(onCoverBack)
            var coverClosing by remember { mutableStateOf(false) }
            val requestCoverBack: () -> Unit = remember(playerBottomSheetState, coverOnly) {
                {
                    val external = currentCoverBack.value
                    if (!coverOnly || playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed) {
                        external?.invoke()
                    } else if (!coverClosing) {
                        coverClosing = true
                        playerBottomSheetState.collapseSoft()
                        coverBackScope.launch {
                            // isCollapsed 用 == 比较，动画途中恒为 false，只能轮询等它落位。
                            var waitedMs = 0L
                            while (
                                !playerBottomSheetState.isCollapsed &&
                                !playerBottomSheetState.isDismissed &&
                                waitedMs < 1_000L
                            ) {
                                delay(16L)
                                waitedMs += 16L
                            }
                            external?.invoke()
                        }
                    }
                }
            }
            LaunchedEffect(coverOnly, playerBottomSheetState) {
                if (!coverOnly) return@LaunchedEffect
                // 把封面下拉到底会 dismiss（MeiloX 原行为：停播并清空队列），这一页
                // 没有别的内容可看，直接退回宿主。
                while (true) {
                    delay(16L)
                    if (playerBottomSheetState.isDismissed) {
                        currentCoverBack.value?.invoke()
                        break
                    }
                }
            }
            DisposableEffect(playerBottomSheetState) {
                scrollPlayerSheet.value = playerBottomSheetState
                onDispose {
                    if (scrollPlayerSheet.value === playerBottomSheetState) {
                        scrollPlayerSheet.value = null
                    }
                }
            }
            val windowInsetsController = remember {
                WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            }
            val isPlayerPage = remember(playerBottomSheetState) {
                derivedStateOf {
                    playerBottomSheetState.isExpanded ||
                        playerBottomSheetState.progress >= 0.99f
                }
            }.value
            val playerDismissed by remember(playerBottomSheetState) {
                derivedStateOf { playerBottomSheetState.isDismissed }
            }
            val pageDarkAppearance = currentRoute?.let { pageAppearanceOverrides[it]?.second }
            val statusBarDark = isPlayerPage || (pageDarkAppearance ?: effectiveDark)
            SideEffect {
                val transparent = android.graphics.Color.TRANSPARENT
                activity.enableEdgeToEdge(
                    statusBarStyle = if (isPlayerPage) {
                        // The expanded player renders artwork behind the status bar;
                        // keep its foreground white regardless of the app theme.
                        SystemBarStyle.dark(transparent)
                    } else {
                        SystemBarStyle.auto(transparent, transparent) { statusBarDark }
                    },
                    navigationBarStyle = SystemBarStyle.auto(transparent, transparent) {
                        effectiveDark
                    },
                )
                // Keep this explicit because the API 35+ edge-to-edge implementation can
                // retain the previous appearance while the bottom sheet settles.
                windowInsetsController.isAppearanceLightStatusBars =
                    !statusBarDark
                if (isPlayerPage && keepScreenOnInPlayer) {
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                // Keep the navigation bar transparent instead of applying the platform
                // contrast scrim.
                activity.window.navigationBarColor = transparent
                activity.window.isNavigationBarContrastEnforced = false
                activity.window.navigationBarDividerColor = transparent
            }
            val (query, onQueryChange) = rememberSaveable(stateSaver = TextFieldValue.Saver) {
                mutableStateOf(TextFieldValue())
            }
            val clearSearchInput = {
                focusManager.clearFocus()
                onQueryChange(TextFieldValue())
                focusSearchOnEntry = false
            }
            LaunchedEffect(active) {
                if (!active) {
                    clearSearchInput()
                }
            }

            val onSearchCancel = {
                clearSearchInput()
                if (navController.currentRoute == Screen.Search.route) {
                    navController.popBackStack()
                }
            }

            val onSearch: (String) -> Unit = {
                if (it.isNotEmpty()) {
                    clearSearchInput()
                    Screen.SearchResult.navigate(navController){
                        addPath(Uri.encode(it))
                        addPath("1") // 默认所搜单曲
                    }
                }
            }

            LaunchedEffect(Unit) {
                lifecycleOwner.lifecycleScope.launch {
                    getAndroidId(activity)
                    if (activity.dataStore.get(DeviceIdKey, "").isEmpty()) {
                        activity.dataStore.edit { settings ->
                            settings[DeviceIdKey] = com.ljyh.mei.utils.getDeviceId()
                        }
                    }
                }
            }
            LaunchedEffect(isMeasured, playerConnection, openInPlayer, coverOnly) {
                if (!isMeasured) return@LaunchedEffect
                val player = playerConnection?.player ?: return@LaunchedEffect
                if (openInPlayer || coverOnly) {
                    // 从宿主首页胶囊点进来：直接展开播放器（封面页）。
                    // 不能只判一次 currentMediaItem —— binder 刚连上时它常常还是 null（队列恢复晚一步），
                    // 那样这次就白等了，且 openInPlayer 不再变化，会永久停在音乐首页。
                    // 这里轮询等它出现（currentMediaItem 不是 Compose 状态，snapshotFlow 收不到变化）；
                    // 最多等 8 秒，等不到就停在音乐首页，不拦着用户自己点。
                    var waitedMs = 0L
                    while (player.currentMediaItem == null && waitedMs < 8_000L) {
                        delay(100L)
                        waitedMs += 100L
                    }
                    // 等不到歌就别硬展：空播放器页不如停在音乐首页。
                    // 封面模式下「音乐首页」并不存在，直接退回宿主。
                    if (player.currentMediaItem != null) {
                        playerBottomSheetState.expandSoft()
                    } else if (coverOnly) {
                        onCoverBack?.invoke()
                    }
                } else if (player.currentMediaItem != null) {
                    // 普通入口照旧收成 mini player。
                    playerBottomSheetState.collapseSoft()
                }
            }

            LaunchedEffect(playerConnection, openInPlayer, coverOnly) {
                val player = playerConnection?.player ?: return@LaunchedEffect
                if (player.currentMediaItem == null) {
                    // 从胶囊进来的这一路正在等队列恢复，别把它刚展开的播放器又收回抽屉。
                    if (!openInPlayer && !coverOnly && !playerBottomSheetState.isDismissed) {
                        playerBottomSheetState.dismiss()
                    }
                } else {
                    if (playerBottomSheetState.isDismissed) {
                        playerBottomSheetState.collapseSoft()
                    }
                }
            }


            DisposableEffect(playerConnection, playerBottomSheetState) {
                val player =
                    playerConnection?.player ?: return@DisposableEffect onDispose { }
                val listener = object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && mediaItem != null && playerBottomSheetState.isDismissed) {
                            playerBottomSheetState.collapseSoft()
                        }
                    }
                }
                player.addListener(listener)
                onDispose {
                    player.removeListener(listener)
                }
            }
            val selectionToolbar = remember { com.ljyh.mei.ui.local.SelectionToolbarState() }
            CompositionLocalProvider(
                com.ljyh.mei.ui.local.LocalSelectionToolbar provides selectionToolbar,
                LocalDatabase provides database,
                LocalNavController provides navController,
                LocalPlayerConnection provides playerConnection,
                LocalUserData provides userData,
            ) {
                if (pictureInPictureMode && playerConnection != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(pipBackdrop)
                            .trackBackdropPosition(pipBackdrop),
                    ) {
                        FloatingLyricsPipBackdrop(playerConnection = playerConnection!!)
                    }
                }

                if (pictureInPictureMode && playerConnection != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer()
                            .layerBackdrop(bottomBackdrop)
                            .trackBackdropPosition(bottomBackdrop),
                    ) {
                        CompositionLocalProvider(
                            // Child glass controls sample the base page,
                            // never the bottom sample layer they create.
                            LocalGlassBackdrop provides glassBackdrop,
                        ) {
                            FloatingLyricsPipScreen(playerConnection = playerConnection!!)
                        }
                    }
                } else if (coverOnly) {
                    // 封面模式：宿主把播放器封面当成自己的一页，这里不渲染音乐主页
                    // （NavDisplay / 底栏 / 搜索）。背景保持透明 —— 胶囊从宿主首页原位
                    // 涨成封面的过程中，底下露出的还是首页，不是一屏纯色。
                    AnimatedMiniPlayerLayer(
                        compactProgress = compactMiniPlayerProgress,
                        state = playerBottomSheetState,
                        backdrop = bottomControlsBackdrop,
                        pageDarkAppearance = pageDarkAppearance,
                        onBackgroundReadyChanged = onPlayerBackgroundReadyChanged,
                        onBack = requestCoverBack,
                    )
                } else {
                    // Page content is the backdrop source for the
                    // floating mini player and bottom controls. Its own
                    // glass controls sample the static backdrop instead
                    // of this source, avoiding a feedback loop.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // Keep sibling player/nav draw invalidations from recording
                            // the unchanged page again. Child updates remain live.
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                layout(placeable.width, placeable.height) {
                                    // SurfaceView clears a hole after Compose draws. An
                                    // opaque player therefore does not automatically
                                    // cull the home page's text/images/glass underneath.
                                    if (!playerBottomSheetState.isExpanded || !playerBackgroundReady.value) {
                                        placeable.place(0, 0)
                                    }
                                }
                            }
                            .graphicsLayer()
                            .layerBackdrop(bottomBackdrop)
                            .trackBackdropPosition(bottomBackdrop),
                    ) {
                        CompositionLocalProvider(
                            LocalGlassBackdrop provides glassBackdrop,
                        ) {
                            val entryViewModelOwners = remember {
                                mutableMapOf<String, MeiNavEntryViewModelStoreOwner>()
                            }
                            val viewModelEntryDecorator = remember(context) {
                                NavEntryDecorator<NavKey>(
                                    onPop = { contentKey ->
                                        entryViewModelOwners.remove(contentKey.toString())?.clear()
                                    },
                                    decorate = { entry ->
                                        val route = entry.contentKey.toString()
                                        val owner = entryViewModelOwners.getOrPut(route) {
                                            MeiNavEntryViewModelStoreOwner(activity)
                                        }
                                        CompositionLocalProvider(
                                            LocalViewModelStoreOwner provides owner,
                                        ) {
                                            entry.Content()
                                        }
                                    },
                                )
                            }
                            NavDisplay(
                                backStack = backStack,
                                modifier = Modifier.fillMaxSize(),
                                onBack = navController::popBackStack,
                                entryDecorators = listOf(
                                    rememberSaveableStateHolderNavEntryDecorator(),
                                    viewModelEntryDecorator,
                                ),
                                transitionSpec = {
                                    if (usesHomeAlphaTransition()) {
                                        homeAlphaTransition()
                                    } else {
                                        defaultTransitionSpec<NavKey>().invoke(this)
                                    }
                                },
                                popTransitionSpec = {
                                    if (usesHomeAlphaTransition()) {
                                        homeAlphaTransition()
                                    } else {
                                        defaultPopTransitionSpec<NavKey>().invoke(this)
                                    }
                                },
                                predictivePopTransitionSpec = { swipeEdge ->
                                    if (usesHomeAlphaTransition()) {
                                        homeAlphaTransition()
                                    } else {
                                        defaultPredictivePopTransitionSpec<NavKey>()
                                            .invoke(this, swipeEdge)
                                    }
                                },
                                transitionEffects = remember(
                                    navController.usesMiuixTransitionEffects,
                                ) {
                                    if (navController.usesMiuixTransitionEffects) {
                                        NavDisplayTransitionEffects(
                                            enableCornerClip = true,
                                            dimAmount = 0.5f,
                                            blockInputDuringTransition = false,
                                        )
                                    } else {
                                        NavDisplayTransitionEffects(
                                            enableCornerClip = false,
                                            dimAmount = 0f,
                                            blockInputDuringTransition = false,
                                        )
                                    }
                                },
                                entryProvider = { route ->
                                    val meiRoute = route as MeiRoute
                                    NavEntry(
                                        key = route,
                                        contentKey = meiRoute.route,
                                    ) {
                                        val entryTopAppBarScrollBehavior = appBarScrollBehavior(
                                            canScroll = {
                                                !meiRoute.route.startsWith("search_result/") &&
                                                    (playerBottomSheetState.isCollapsed ||
                                                        playerBottomSheetState.isDismissed)
                                            },
                                        )
                                        LaunchedEffect(currentRoute) {
                                            if (meiRoute.route == currentRoute) {
                                                entryTopAppBarScrollBehavior.state
                                                    .resetHeightOffset()
                                            }
                                        }
                                        LaunchedEffect(active) {
                                            if (active && meiRoute.route == currentRoute) {
                                                entryTopAppBarScrollBehavior.state
                                                    .resetHeightOffset()
                                            }
                                        }
                                        val entryPlayerAwareWindowInsets = remember(
                                            meiRoute.route,
                                            bottomInset,
                                            navigationItems,
                                            navigationBarExpanded,
                                            playerDismissed,
                                            windowsInsets,
                                        ) {
                                            playerAwareWindowInsetsForRoute(
                                                route = meiRoute.route,
                                                navigationItems = navigationItems,
                                                navigationBarVisible = navigationBarExpanded,
                                                playerDismissed = playerDismissed,
                                                windowsInsets = windowsInsets,
                                                bottomInset = bottomInset,
                                            )
                                        }
                                        CompositionLocalProvider(
                                            LocalPlayerAwareWindowInsets provides
                                                if (meiRoute.route == Screen.Search.route) entryPlayerAwareWindowInsets.union(imeInsets)
                                                else entryPlayerAwareWindowInsets,
                                        ) {
                                            navigationEntry(
                                                route = meiRoute.route,
                                                scrollBehavior = entryTopAppBarScrollBehavior,
                                                isNavigationTab = navigationItems.any {
                                                    it.route == meiRoute.route
                                                },
                                            )
                                        }
                                    }
                                },
                            )
                            AnimatedVisibility(
                                visible = active,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(
                                            if (query.text.isEmpty()) Color.Transparent
                                            else MaterialTheme.colorScheme.background,
                                        ),
                                ) {
                                    if (query.text.isNotEmpty()) {
                                        SearchScreen(
                                            query = query.text,
                                            onQueryChange = onQueryChange,
                                            onSearch = { query, type ->
                                                Screen.SearchResult.navigate(navController){
                                                    addPath(Uri.encode(query))
                                                    addPath(type.toString())
                                                }
                                            },
                                            onDismiss = clearSearchInput,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(top = windowsInsets.asPaddingValues().calculateTopPadding())
                                                .padding(bottom = searchBottomInset + NavigationBarHeight),
                                        )
                                    }
                                }
                            }
                        }
                    }
                if (selectionToolbar.content.value == null) AnimatedMiniPlayerLayer(
                    compactProgress = compactMiniPlayerProgress,
                    state = playerBottomSheetState,
                    backdrop = bottomControlsBackdrop,
                    pageDarkAppearance = pageDarkAppearance,
                    onBackgroundReadyChanged = onPlayerBackgroundReadyChanged,
                )
                AnimatedVisibility(
                    visible = active,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    CompositionLocalProvider(
                        LocalGlassBackdrop provides bottomControlsBackdrop,
                    ) {
                        IosBottomSearchToolbar(
                            query = query,
                            onQueryChange = onQueryChange,
                            onSearch = onSearch,
                            onCancel = onSearchCancel,
                            cancelLabel = stringResource(R.string.cancel),
                            placeholder = stringResource(R.string.search_bar_search),
                            focusRequester = searchBarFocusRequester,
                            autoFocus = focusSearchOnEntry,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(bottom = searchBottomInset + NavigationBarBottomMargin),
                        )
                    }
                }

                val selectedIndex = navigationItems.firstOrNull { it.route == currentRoute }
                    ?: navigationItems.firstOrNull { it.name == lastSelectedTab }
                    ?: Index.Home
                val navigationTabs = navigationItems.map { item ->
                    GlassTabItem(
                        key = item,
                        label = stringResource(item.labelRes),
                        symbol = item.symbol,
                    )
                }
                AnimatedVisibility(
                    visible = navigationPreferences != null && shouldAllowNavigationBar &&
                        selectionToolbar.content.value == null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(2f),
                ) {
                    AnimatedBottomNavigationRow(
                        compactProgress = compactNavigationProgress,
                        items = navigationTabs,
                        selectedKey = selectedIndex,
                        onExpand = { navigationBarVisible = true },
                        onSelected = { screen ->
                            setLastSelectedTab(screen.name)
                            if (currentRoute == screen.route) {
                                navController.currentBackStackEntry?.savedStateHandle?.set(
                                    "scrollToTop",
                                    true,
                                )
                            } else {
                                navController.navigateTopLevel(screen.route)
                            }
                        },
                        backdrop = bottomControlsBackdrop,
                        playerProgress = { playerBottomSheetState.progress },
                        bottomInset = bottomInset,
                    ) {
                        AnimatedHomeSearchButton(
                            compactProgress = compactNavigationProgress,
                            backdrop = bottomControlsBackdrop,
                            onClick = {
                                focusSearchOnEntry = true
                                if (currentRoute != Screen.Search.route) {
                                    navController.navigate(Screen.Search.route)
                                }
                            },
                        )
                    }
                }
                selectionToolbar.content.value?.let { toolbar ->
                    Box(Modifier.align(Alignment.BottomCenter).zIndex(3f)
                        .fillMaxWidth().padding(horizontal = 16.dp)
                        .padding(bottom = bottomInset + NavigationBarBottomMargin)) {
                        CompositionLocalProvider(LocalGlassBackdrop provides bottomControlsBackdrop) {
                            toolbar()
                        }
                    }
                }
                LaunchedEffect(recognizeClipboardLinks) {
                    if (recognizeClipboardLinks && !clipboardInspected) {
                        clipboardInspected = true
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = clipboard.primaryClip
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)
                            ?.coerceToText(activity)
                            ?.toString()
                        clipboardLink = text?.let(NeteaseMusicLinkParser::parse)
                    }
                }
                clipboardLink?.let { detectedLink ->
                    ClipboardLinkPrompt(
                        link = detectedLink,
                        onDismiss = { clipboardLink = null },
                        onOpen = {
                            clipboardLink = null
                            when (detectedLink) {
                                is NeteaseMusicLink.ListenTogether -> {
                                    navController.currentBackStackEntry?.savedStateHandle?.set(
                                        "listen_invitation",
                                        detectedLink.invitationText,
                                    )
                                    Screen.ListenTogether.navigate(navController)
                                }
                                is NeteaseMusicLink.Song -> {
                                    lifecycleOwner.lifecycleScope.launch {
                                        runCatching {
                                            apiService.getSongDetail(GetSongDetails(detectedLink.id.toString()))
                                                .songs
                                                .firstOrNull()
                                                ?: error(context.getString(R.string.clipboard_song_not_found))
                                        }.onSuccess { song ->
                                            playerConnection?.playQueue(
                                                ListQueue(
                                                    id = "clipboard-${song.id}",
                                                    title = song.name,
                                                    items = listOf(song.id.toString() to song.toMediaItem()),
                                                ),
                                            )
                                        }.onFailure { error ->
                                            Toast.makeText(
                                                activity,
                                                context.getString(R.string.clipboard_open_failed, error.message.orEmpty()),
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                    }
                                }
                            }
                        },
                    )
                }
                if (!pictureInPictureMode) NeteaseLegacyWebSessionNotice()
                VersionUpdateAlert(
                    result = startupUpdateResult,
                    onDismiss = { startupUpdateResult = null },
                )
                }
            }
        }
    }

}
}

private val homeNavigationRoutes = Screen.MainScreens.map { it.route }.toSet()

private fun Scene<NavKey>.currentRoute(): String? =
    entries.lastOrNull()?.contentKey as? String

@Composable
private fun BoxScope.AnimatedMiniPlayerLayer(
    compactProgress: State<Float>,
    state: BottomSheetState,
    backdrop: Backdrop,
    pageDarkAppearance: Boolean?,
    onBackgroundReadyChanged: (Boolean) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val miniPlayerVerticalOffset = remember(compactProgress) {
        { (NavigationBarHeight - 16.dp) * compactProgress.value }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f),
    ) {
        CompositionLocalProvider(
            // MiniPlayer is rendered by BottomSheetPlayer's collapsed content. Give it the page
            // sample layer while keeping page/player glass out of that source.
            LocalGlassBackdrop provides backdrop,
            LocalMiniPlayerAppearance provides pageDarkAppearance,
        ) {
            BottomSheetPlayer(
                state = state,
                modifier = Modifier.fillMaxSize(),
                compactMiniPlayerProgress = compactProgress,
                miniPlayerVerticalOffset = miniPlayerVerticalOffset,
                onBackgroundReadyChanged = onBackgroundReadyChanged,
                onBack = onBack,
            )
        }
    }
}


private fun playerAwareWindowInsetsForRoute(
    route: String,
    navigationItems: List<Index>,
    navigationBarVisible: Boolean,
    playerDismissed: Boolean,
    windowsInsets: WindowInsets,
    bottomInset: Dp,
): WindowInsets {
    val allowsNavigationBar =
        navigationItems.fastAny { it.route == route } || route == Screen.Search.route
    var bottom = bottomInset
    if (allowsNavigationBar && navigationBarVisible) bottom += NavigationBarHeight
    if (!playerDismissed) {
        bottom += MiniPlayerHeight
        if (!allowsNavigationBar) bottom += NavigationBarBottomMargin
    }
    return windowsInsets
        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
        .add(WindowInsets(top = 0.dp, bottom = bottom))
}

private fun AnimatedContentTransitionScope<Scene<NavKey>>.usesHomeAlphaTransition(): Boolean {
    return initialState.currentRoute() in homeNavigationRoutes &&
        targetState.currentRoute() in homeNavigationRoutes
}

private fun AnimatedContentTransitionScope<Scene<NavKey>>.homeAlphaTransition(): ContentTransform =
    fadeIn() togetherWith fadeOut()

