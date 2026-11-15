package com.flaretune.client.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected as semanticSelected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import coil.ImageLoader
import coil.compose.AsyncImage
import com.flaretune.client.R
import com.flaretune.client.data.SearchEntity
import com.flaretune.client.model.Album
import com.flaretune.client.model.Artist
import com.flaretune.client.model.ClientNotice
import com.flaretune.client.model.LyricLine
import com.flaretune.client.model.LyricState
import com.flaretune.client.model.LyricStatus
import com.flaretune.client.model.PlaylistSummary
import com.flaretune.client.model.Song
import com.flaretune.client.model.isCurrentAlbumTrack
import com.flaretune.client.ui.theme.FlareTokens
import com.flaretune.client.ui.theme.floatingDockSurface
import com.flaretune.client.ui.theme.isFlareDarkTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot

private enum class HomeSubpage { NONE, ALL_SONGS, ALL_ALBUMS }

private enum class HomeFeedFilter { ALL, CONTINUE, DISCOVERY, LIBRARY }

/**
 * Floating dock shell:
 * Content scrolls underneath the compact floating dock and aligned mini player.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListeningShell(
    state: ClientUiState,
    actions: ClientActions,
    imageLoader: ImageLoader?,
    openPlayer: () -> Unit,
    openQueue: () -> Unit,
    miniPlayerActive: Boolean = true,
    playerTransitionRegistry: PlayerTransitionRegistry? = null,
    playerExpandProgress: () -> Float = { 0f },
    playerSheetMounted: () -> Boolean = { false },
    onExpandDragStart: () -> Unit = {},
    onExpandDragDelta: (Float) -> Float = { 0f },
    onExpandDragEnd: (Float) -> Unit = {},
) {
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    var historyTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var statsOpen by rememberSaveable { mutableStateOf(false) }
    var sleepSheetOpen by rememberSaveable { mutableStateOf(false) }
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var homeSubpage by rememberSaveable { mutableStateOf(HomeSubpage.NONE) }
    val listeningInteractions = LocalListeningInteractions.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val safeTopInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    val topInset = max(safeTopInset, 24.dp)
    val bottomSystemInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val compactBottomClearance = max(bottomSystemInset, 14.dp)

    val openStats: () -> Unit = {
        drawerOpen = false
        settingsOpen = false
        historyOpen = false
        statsOpen = true
        actions.refreshPlayStats()
    }
    val updatedInteractions = listeningInteractions?.copy(statistics = openStats)
        ?: ListeningInteractions(
            song = {},
            createPlaylist = {},
            addToPlaylists = {},
            playlist = actions::openPlaylist,
            addSongs = {},
            artists = {},
            albums = {},
            statistics = openStats,
            clearHistory = {},
            clearQueue = {},
        )

    CompositionLocalProvider(
        LocalOpenAccountDrawer provides { drawerOpen = true },
        LocalAccountImageLoader provides imageLoader,
        LocalListeningInteractions provides updatedInteractions,
    ) {
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(
                if (isFlareDarkTheme()) Color(0xFF0A0A0A)
                else MaterialTheme.colorScheme.background,
            ),
    ) {
        val shellMaxWidth = maxWidth
        val wideLayout = shellMaxWidth >= 840.dp
        val tabletDrawer = shellMaxWidth >= 700.dp
        val drawerWidth = if (tabletDrawer) 300.dp else minOf(300.dp, shellMaxWidth * 0.82f)
        val drawerOuterStart = if (tabletDrawer) 12.dp else 0.dp
        val drawerRightGap = if (tabletDrawer) 12.dp else 0.dp
        val density = LocalDensity.current
        val pushDistancePx = with(density) { (drawerOuterStart + drawerWidth + drawerRightGap).toPx() }
        val drawerStartHiddenPx = with(density) { (drawerOuterStart + drawerWidth).toPx() }
        val drawerScope = rememberCoroutineScope()
        val drawerProgressAnim = remember { Animatable(0f) }
        var drawerDragging by remember { mutableStateOf(false) }
        LaunchedEffect(drawerOpen) {
            if (!drawerDragging) {
                drawerProgressAnim.animateTo(
                    targetValue = if (drawerOpen) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = if (drawerOpen) 250 else 210,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
        }
        val drawerProgress = drawerProgressAnim.value.coerceIn(0f, 1f)
        val drawerVisible = drawerOpen || drawerProgress > 0.001f
        val closeDrawer: () -> Unit = {
            drawerDragging = false
            drawerOpen = false
        }
        val drawerDragState = rememberDraggableState { deltaPx ->
            if (pushDistancePx > 0f) {
                drawerDragging = true
                val next = (drawerProgressAnim.value + deltaPx / pushDistancePx).coerceIn(0f, 1f)
                drawerScope.launch { drawerProgressAnim.snapTo(next) }
            }
        }
        val onDrawerDragStopped: suspend kotlinx.coroutines.CoroutineScope.(Float) -> Unit = { velocity ->
            val current = drawerProgressAnim.value
            val shouldStayOpen = if (velocity < -600f) false else if (velocity > 600f) true else current >= 0.65f
            drawerDragging = false
            drawerOpen = shouldStayOpen
            drawerScope.launch {
                drawerProgressAnim.animateTo(
                    targetValue = if (shouldStayOpen) 1f else 0f,
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                )
            }
        }
        val wideDock = wideLayout && state.playback.current != null
        val fontScale = LocalDensity.current.fontScale
        val fontOverflowScale = if (fontScale > 1.3f) ((fontScale - 1f).coerceAtMost(1f)) else 0f
        val compactNavExtraHeight = if (!wideLayout) 12.dp * fontOverflowScale else 0.dp
        val compactMiniExtraHeight = if (!wideLayout) 28.dp * fontOverflowScale else 0.dp
        val wideDockExtraHeight = if (wideLayout) 26.dp * fontOverflowScale else 0.dp
        val dockHeight = if (wideLayout) FlareTokens.WideDockHeight + wideDockExtraHeight else FlareTokens.CompactNavHeight + compactNavExtraHeight
        val contentBottomInset = when {
            wideLayout -> 114.dp + wideDockExtraHeight + bottomSystemInset
            state.playback.current != null -> 136.dp + compactNavExtraHeight + compactMiniExtraHeight + compactBottomClearance
            else -> 66.dp + compactNavExtraHeight + compactBottomClearance
        }
        val initialDestination = remember { state.destination }
        var homeScrollNonce by rememberSaveable { mutableIntStateOf(0) }
        var discoverScrollNonce by rememberSaveable { mutableIntStateOf(0) }
        var searchScrollNonce by rememberSaveable { mutableIntStateOf(0) }
        var libraryScrollNonce by rememberSaveable { mutableIntStateOf(0) }
        val visitedTabs = remember { mutableStateMapOf<MainDestination, Boolean>() }
        LaunchedEffect(state.destination) {
            visitedTabs[state.destination] = true
        }
        val navigateMain: (MainDestination) -> Unit = { dest ->
            val hasSelection = state.selectedArtist != null ||
                state.selectedAlbum != null ||
                state.selectedPlaylist != null
            val atRootSameTab = dest == state.destination &&
                homeSubpage == HomeSubpage.NONE &&
                !historyOpen &&
                !settingsOpen &&
                !statsOpen &&
                !hasSelection &&
                !(dest == MainDestination.LIBRARY && state.tab != LibraryTab.CATALOG)
            homeSubpage = HomeSubpage.NONE
            historyOpen = false
            settingsOpen = false
            statsOpen = false
            if (atRootSameTab) {
                when (dest) {
                    MainDestination.HOME -> homeScrollNonce++
                    MainDestination.DISCOVER -> discoverScrollNonce++
                    MainDestination.SEARCH -> searchScrollNonce++
                    MainDestination.LIBRARY -> libraryScrollNonce++
                }
            } else {
                actions.navigate(dest)
            }
        }
        val detailOpen = state.selectedArtist != null ||
            state.selectedAlbum != null ||
            (state.selectedPlaylist != null && (state.destination != MainDestination.SEARCH || historyOpen))
        fun closeHomeSubpage() {
            homeSubpage = HomeSubpage.NONE
            if (state.destination == MainDestination.DISCOVER &&
                (state.songLanguage != state.discoverLanguage || state.albumLanguage != state.discoverLanguage)
            ) {
                actions.selectDiscoverLanguage(state.discoverLanguage)
            }
        }
        BackHandler(enabled = homeSubpage != HomeSubpage.NONE && !historyOpen && !settingsOpen && !statsOpen && !detailOpen && !drawerOpen) {
            closeHomeSubpage()
        }
        BackHandler(enabled = historyOpen && !detailOpen && !drawerOpen) {
            historyOpen = false
        }
        BackHandler(enabled = settingsOpen && !detailOpen && !historyOpen && !drawerOpen) {
            settingsOpen = false
        }
        BackHandler(enabled = statsOpen && !detailOpen && !settingsOpen && !historyOpen && !drawerOpen) {
            statsOpen = false
        }
        BackHandler(enabled = drawerOpen) {
            closeDrawer()
        }

        val overlayRouteKey = when {
            state.selectedArtist != null -> "artist:" + (state.selectedArtist.id ?: state.selectedArtist.name)
            state.selectedAlbum != null -> "album:" + state.selectedAlbum.id
            state.selectedPlaylist != null && (state.destination != MainDestination.SEARCH || historyOpen) -> "playlist:" + state.selectedPlaylist.id
            historyOpen -> "history"
            settingsOpen -> "settings"
            statsOpen -> "stats"
            state.destination == MainDestination.HOME && homeSubpage != HomeSubpage.NONE -> "home:" + homeSubpage.name
            state.destination == MainDestination.DISCOVER && homeSubpage != HomeSubpage.NONE -> "discover:" + homeSubpage.name
            state.destination == MainDestination.LIBRARY && state.tab != LibraryTab.CATALOG -> "library:" + state.tab.name
            else -> null
        }
        val hasOverlay = overlayRouteKey != null
        LaunchedEffect(hasOverlay, drawerOpen) {
            actions.setSecondaryOverlayActive(hasOverlay || drawerOpen)
        }
        DisposableEffect(Unit) {
            onDispose { actions.setSecondaryOverlayActive(false) }
        }
        LaunchedEffect(state.destination, hasOverlay, drawerOpen, sleepSheetOpen, miniPlayerActive) {
            if (state.destination != MainDestination.SEARCH || hasOverlay || drawerOpen || sleepSheetOpen || !miniPlayerActive) {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
        }
        val pageBackground = MaterialTheme.colorScheme.background

        // Main shell (tabs + overlays + floating dock) pushes rightward as a whole when the Spotify drawer opens.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (drawerProgress > 0.001f) {
                        translationX = pushDistancePx * drawerProgress
                        if (tabletDrawer) {
                            shape = RoundedCornerShape(18.dp * drawerProgress)
                            clip = true
                        }
                    }
                }
                .background(pageBackground),
        ) {
        // Main content spans full height and scrolls underneath both the status bar and the floating dock.
        Box(
            Modifier.fillMaxSize()
                .then(if (drawerOpen) Modifier.clearAndSetSemantics {} else Modifier),
        ) {
            MainDestination.entries.forEach { tab ->
                if (tab == state.destination || visitedTabs[tab] == true) {
                    key(tab) {
                        val isCurrentTab = state.destination == tab
                        val isActiveTab = isCurrentTab && !hasOverlay
                        val initialAlpha = if (tab == initialDestination && !hasOverlay) 1f else 0f
                        val tabAlphaAnim = remember { Animatable(initialAlpha) }
                        val initialOffset = if (tab == initialDestination && !hasOverlay) {
                            0f
                        } else if (tab.ordinal >= state.destination.ordinal) {
                            0.024f
                        } else {
                            -0.024f
                        }
                        val tabOffsetAnim = remember { Animatable(initialOffset) }
                        LaunchedEffect(isActiveTab, state.destination) {
                            val targetOffset = when {
                                isActiveTab -> 0f
                                tab.ordinal < state.destination.ordinal -> -0.024f
                                else -> 0.024f
                            }
                            launch {
                                tabAlphaAnim.animateTo(
                                    targetValue = if (isActiveTab) 1f else 0f,
                                    animationSpec = tween(
                                        durationMillis = if (isActiveTab) 190 else 150,
                                        easing = FastOutSlowInEasing,
                                    ),
                                )
                            }
                            launch {
                                tabOffsetAnim.animateTo(
                                    targetValue = targetOffset,
                                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                                )
                            }
                        }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .zIndex(if (isActiveTab) 1f else 0f)
                                .focusProperties {
                                    canFocus = isActiveTab && !drawerOpen && !sleepSheetOpen && miniPlayerActive
                                }
                                .then(if (!isActiveTab) Modifier.clearAndSetSemantics {} else Modifier)
                                .pointerInput(isActiveTab) {
                                    if (!isActiveTab) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                            }
                                        }
                                    }
                                }
                                .graphicsLayer {
                                    alpha = tabAlphaAnim.value
                                    translationX = size.width * tabOffsetAnim.value
                                }
                                .drawWithContent {
                                    if (tabAlphaAnim.value > 0.005f) {
                                        drawContent()
                                    }
                                }
                                .background(pageBackground),
                        ) {
                            when (tab) {
                                MainDestination.HOME -> HomeScreen(
                                    state = state,
                                    actions = actions,
                                    loader = imageLoader,
                                    openHistory = {
                                        historyTabIndex = 0
                                        historyOpen = true
                                    },
                                    openQueue = openQueue,
                                    openAllSongs = { homeSubpage = HomeSubpage.ALL_SONGS },
                                    topInset = topInset,
                                    bottomInset = contentBottomInset,
                                    scrollToTopNonce = homeScrollNonce,
                                )
                                MainDestination.DISCOVER -> DiscoverScreen(
                                    state = state,
                                    actions = actions,
                                    loader = imageLoader,
                                    openAllSongs = { homeSubpage = HomeSubpage.ALL_SONGS },
                                    openAllAlbums = { homeSubpage = HomeSubpage.ALL_ALBUMS },
                                    topInset = topInset,
                                    bottomInset = contentBottomInset,
                                    scrollToTopNonce = discoverScrollNonce,
                                )
                                MainDestination.SEARCH -> ListeningSearchPage(
                                    state = state,
                                    actions = actions,
                                    loader = imageLoader,
                                    topInset = topInset,
                                    bottomInset = contentBottomInset,
                                    scrollToTopNonce = searchScrollNonce,
                                )
                                MainDestination.LIBRARY -> ListeningLibraryOverview(
                                    state = state,
                                    actions = actions,
                                    loader = imageLoader,
                                    topInset = topInset,
                                    bottomInset = contentBottomInset,
                                    scrollToTopNonce = libraryScrollNonce,
                                )
                            }
                        }
                    }
                }
            }

            AnimatedContent(
                targetState = overlayRouteKey,
                modifier = Modifier.fillMaxSize().zIndex(2f),
                transitionSpec = {
                    val enteringOverlay = targetState != null
                    val exitingOverlay = initialState != null
                    when {
                        enteringOverlay && !exitingOverlay ->
                            (fadeIn(tween(200, easing = FastOutSlowInEasing)) +
                                slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { it / 16 }) togetherWith
                                fadeOut(tween(120))
                        !enteringOverlay && exitingOverlay ->
                            fadeIn(tween(140)) togetherWith
                                (fadeOut(tween(170, easing = FastOutSlowInEasing)) +
                                    slideOutHorizontally(tween(190, easing = FastOutSlowInEasing)) { it / 16 })
                        else ->
                            fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                    }
                },
                label = "shell_overlay_route",
            ) { activeOverlay ->
                if (activeOverlay != null) {
                    Box(Modifier.fillMaxSize().background(pageBackground)) {
                        when {
                            activeOverlay.startsWith("artist:") ->
                                ListeningArtistPage(state, actions, imageLoader, topInset, contentBottomInset)
                            activeOverlay.startsWith("album:") ->
                                AlbumScreen(state, actions, imageLoader, topInset, contentBottomInset)
                            activeOverlay.startsWith("playlist:") ->
                                ListeningPlaylistDetailPage(state, actions, imageLoader, topInset, contentBottomInset)
                            activeOverlay == "history" -> PlayHistoryPage(
                                state = state,
                                actions = actions,
                                loader = imageLoader,
                                topInset = topInset,
                                bottomInset = contentBottomInset,
                                onOpenQueue = openQueue,
                                selectedTabIndex = historyTabIndex,
                                onSelectTabIndex = { historyTabIndex = it },
                                onBack = { historyOpen = false },
                            )
                            activeOverlay == "settings" -> SettingsScreen(
                                state = state,
                                actions = actions,
                                topInset = topInset,
                                bottomInset = contentBottomInset,
                                onBack = { settingsOpen = false },
                                onOpenSleepTimer = { sleepSheetOpen = true },
                            )
                            activeOverlay == "stats" -> ListeningStatsPage(
                                state = state,
                                actions = actions,
                                loader = imageLoader,
                                topInset = topInset,
                                bottomInset = contentBottomInset,
                                onBack = { statsOpen = false },
                            )
                            activeOverlay == "discover:" + HomeSubpage.ALL_SONGS.name -> HomeAllSongsScreen(
                                state = state,
                                actions = actions,
                                loader = imageLoader,
                                topInset = topInset,
                                bottomInset = contentBottomInset,
                                onBack = ::closeHomeSubpage,
                            )
                            activeOverlay == "discover:" + HomeSubpage.ALL_ALBUMS.name -> HomeAllAlbumsScreen(
                                state = state,
                                actions = actions,
                                loader = imageLoader,
                                topInset = topInset,
                                bottomInset = contentBottomInset,
                                onBack = ::closeHomeSubpage,
                            )
                            activeOverlay == "home:" + HomeSubpage.ALL_SONGS.name -> HomeAllSongsScreen(
                                state = state,
                                actions = actions,
                                loader = imageLoader,
                                topInset = topInset,
                                bottomInset = contentBottomInset,
                                onBack = ::closeHomeSubpage,
                            )
                            activeOverlay == "home:" + HomeSubpage.ALL_ALBUMS.name -> HomeAllAlbumsScreen(
                                state = state,
                                actions = actions,
                                loader = imageLoader,
                                topInset = topInset,
                                bottomInset = contentBottomInset,
                                onBack = ::closeHomeSubpage,
                            )
                            else -> CatalogScreen(state, actions, imageLoader, topInset, contentBottomInset)
                        }
                    }
                }
            }

            if (topInset > 0.dp && hasOverlay) {
                Box(
                    Modifier.align(Alignment.TopCenter)
                        .zIndex(3f)
                        .fillMaxWidth()
                        .height(topInset)
                        .background(
                            Brush.verticalGradient(
                                0f to pageBackground.copy(alpha = 0.88f),
                                1f to pageBackground.copy(alpha = 0f),
                            ),
                        ),
                )
            }
        }

        if (wideLayout) {
            // Wide dock overlay: floating side-by-side navigation & mini player.
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = FlareTokens.FloatingDockHorizontalPadding, vertical = 10.dp)
                    .then(if (drawerOpen) Modifier.clearAndSetSemantics {} else Modifier),
                contentAlignment = Alignment.BottomCenter,
            ) {
                val wideDockTotalWidth = ((shellMaxWidth - 40.dp) * 0.86f).coerceIn(760.dp, 1020.dp)
                val wideDockGap = 12.dp
                val wideNavWidth = if (state.playback.current != null) {
                    ((wideDockTotalWidth - wideDockGap) * 0.58f).coerceIn(440.dp, 580.dp)
                } else {
                    ((shellMaxWidth - 40.dp) * 0.52f).coerceIn(440.dp, 580.dp)
                }
                val wideMiniWidth = (wideDockTotalWidth - wideDockGap - wideNavWidth).coerceIn(308.dp, 428.dp)
                Row(
                    Modifier
                        .graphicsLayer {
                            val p = playerExpandProgress().coerceIn(0f, 1f)
                            if (p > 0.001f) {
                                alpha = 1f - 0.35f * p
                            }
                        }
                        .animateContentSize(spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow))
                        .then(if (wideDock) Modifier.testTag("dock_wide") else Modifier.testTag("dock_compact")),
                    horizontalArrangement = Arrangement.spacedBy(wideDockGap, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MainNavigation(
                        destination = state.destination,
                        navigate = navigateMain,
                        dockHeight = dockHeight,
                        wideStyle = true,
                        modifier = Modifier.width(wideNavWidth),
                    )
                    AnimatedVisibility(
                        visible = state.playback.current != null,
                        enter = fadeIn(tween(260)) + expandHorizontally(tween(320), expandFrom = Alignment.Start) + slideInVertically(tween(300)) { it / 2 },
                        exit = fadeOut(tween(200)) + shrinkHorizontally(tween(240), shrinkTowards = Alignment.Start) + slideOutVertically(tween(220)) { it / 2 },
                    ) {
                        ShellMiniPlayer(
                            state.playback, state.lyricState, state.translationEnabled, actions, imageLoader, openPlayer,
                            dockHeight = dockHeight,
                            wideStyle = true,
                            miniPlayerActive = miniPlayerActive,
                            playerTransitionRegistry = playerTransitionRegistry,
                            playerExpandProgress = playerExpandProgress,
                            playerSheetMounted = playerSheetMounted,
                            onExpandDragStart = onExpandDragStart,
                            onExpandDragDelta = onExpandDragDelta,
                            onExpandDragEnd = onExpandDragEnd,
                            modifier = Modifier.width(wideMiniWidth),
                        )
                    }
                }
            }
        } else {
            // Compact / floating-window dock: edge-to-edge full-width mini player strip stacked cleanly
            // over the opaque bottom navigation bar with strict 1:1 vertical slide synchronization.
            val darkDock = isFlareDarkTheme()
            val compactDockBg = if (darkDock) Color(0xFF121212) else Color(0xFFFBFBFC)
            val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            Column(
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .testTag("dock_compact")
                    .then(if (drawerOpen) Modifier.clearAndSetSemantics {} else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state.playback.current != null) {
                    Box(
                        Modifier.fillMaxWidth()
                            .height(0.6.dp)
                            .graphicsLayer {
                                alpha = 1f - (playerExpandProgress() / 0.24f).coerceIn(0f, 1f)
                            }
                            .background(dividerColor),
                    )
                }
                AnimatedVisibility(
                    visible = state.playback.current != null,
                    enter = fadeIn(tween(220)) + expandVertically(tween(240), expandFrom = Alignment.Bottom),
                    exit = fadeOut(tween(180)) + shrinkVertically(tween(200), shrinkTowards = Alignment.Bottom),
                ) {
                    ShellMiniPlayer(
                        state.playback, state.lyricState, state.translationEnabled, actions, imageLoader, openPlayer,
                        dockHeight = FlareTokens.CompactMiniPlayerHeight + compactMiniExtraHeight,
                        wideStyle = false,
                        miniPlayerActive = miniPlayerActive,
                        playerTransitionRegistry = playerTransitionRegistry,
                        playerExpandProgress = playerExpandProgress,
                        playerSheetMounted = playerSheetMounted,
                        onExpandDragStart = onExpandDragStart,
                        onExpandDragDelta = onExpandDragDelta,
                        onExpandDragEnd = onExpandDragEnd,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(
                    Modifier.fillMaxWidth()
                        .graphicsLayer {
                            val p = playerExpandProgress().coerceIn(0f, 1f)
                            translationY = size.height * p
                        }
                        .background(compactDockBg),
                ) {
                    Box(
                        Modifier.fillMaxWidth()
                            .height(0.6.dp)
                            .background(dividerColor.copy(alpha = if (state.playback.current != null) 0.22f else 0.35f)),
                    )
                    MainNavigation(
                        state.destination,
                        navigateMain,
                        dockHeight = dockHeight,
                        wideStyle = false,
                    )
                    Spacer(Modifier.height(compactBottomClearance))
                }
            }
        }
        if (drawerVisible) {
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(50f)
                    .background(Color.Black.copy(alpha = 0.56f * drawerProgress))
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = drawerDragState,
                        onDragStopped = onDrawerDragStopped,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = drawerOpen,
                        onClick = closeDrawer,
                    )
                    .testTag("drawer_scrim"),
            )
        }
        }
        if (drawerVisible) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = drawerOuterStart,
                        top = if (tabletDrawer) max(safeTopInset, 10.dp) else 0.dp,
                        bottom = if (tabletDrawer) max(bottomSystemInset, 12.dp) else 0.dp,
                    ),
            ) {
                SpotifyAccountDrawer(
                    state = state,
                    actions = actions,
                    tabletMode = tabletDrawer,
                    topInset = safeTopInset,
                    bottomInset = bottomSystemInset,
                    onClose = closeDrawer,
                    onOpenStats = openStats,
                    onOpenHistory = {
                        settingsOpen = false
                        statsOpen = false
                        historyTabIndex = 0
                        historyOpen = true
                    },
                    onOpenSleepTimer = { sleepSheetOpen = true },
                    onOpenSettings = {
                        historyOpen = false
                        statsOpen = false
                        settingsOpen = true
                    },
                    modifier = Modifier
                        .width(drawerWidth)
                        .graphicsLayer {
                            translationX = -drawerStartHiddenPx * (1f - drawerProgress)
                        }
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = drawerDragState,
                            onDragStopped = onDrawerDragStopped,
                        ),
                )
            }
        }
        if (sleepSheetOpen) {
            SleepTimerSheet(
                actions = actions,
                hasCurrentTrack = state.playback.current != null,
                onDismiss = { sleepSheetOpen = false },
            )
        }
    }
    }
}

@Composable
private fun MainNavigation(
    destination: MainDestination,
    navigate: (MainDestination) -> Unit,
    modifier: Modifier = Modifier,
    dockHeight: Dp = FlareTokens.CompactNavHeight,
    wideStyle: Boolean = false,
) {
    val containerModifier = if (wideStyle) {
        modifier
            .fillMaxWidth()
            .height(dockHeight)
            .floatingDockSurface(shape = RoundedCornerShape(22.dp), elevation = 14.dp)
    } else {
        modifier
            .fillMaxWidth()
            .height(dockHeight)
    }
    Box(
        containerModifier,
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = if (wideStyle) 8.dp else 4.dp, vertical = if (wideStyle) 4.dp else 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(
                item = MainDestination.HOME,
                selected = destination,
                selectedIcon = ClientIcons.Home,
                unselectedIcon = ClientIcons.HomeOutlined,
                label = stringResource(R.string.ft_home),
                tag = "nav_home",
                wideStyle = wideStyle,
                navigate = navigate,
            )
            NavItem(
                item = MainDestination.DISCOVER,
                selected = destination,
                selectedIcon = ClientIcons.Discover,
                unselectedIcon = ClientIcons.Discover,
                label = stringResource(R.string.ft_discover_page),
                tag = "nav_discover",
                wideStyle = wideStyle,
                navigate = navigate,
            )
            NavItem(
                item = MainDestination.SEARCH,
                selected = destination,
                selectedIcon = ClientIcons.Search,
                unselectedIcon = ClientIcons.Search,
                label = stringResource(R.string.ft_search_page),
                tag = "nav_search",
                wideStyle = wideStyle,
                navigate = navigate,
            )
            NavItem(
                item = MainDestination.LIBRARY,
                selected = destination,
                selectedIcon = ClientIcons.Library,
                unselectedIcon = ClientIcons.Library,
                label = stringResource(R.string.ft_library_page),
                tag = "nav_library",
                wideStyle = wideStyle,
                navigate = navigate,
            )
        }
    }
}

@Composable
private fun RowScope.NavItem(
    item: MainDestination,
    selected: MainDestination,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    label: String,
    tag: String,
    wideStyle: Boolean = false,
    navigate: (MainDestination) -> Unit,
) {
    val active = selected == item
    val color by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(180),
        label = "nav_tint",
    )
    val activeFill by animateColorAsState(
        targetValue = if (wideStyle && active) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent,
        animationSpec = tween(180),
        label = "nav_fill",
    )
    Column(
        Modifier.weight(1f)
            .fillMaxHeight()
            .then(if (wideStyle) Modifier.clip(RoundedCornerShape(16.dp)).background(activeFill) else Modifier)
            .selectable(active, role = Role.Tab, onClick = { navigate(item) })
            .padding(horizontal = 4.dp, vertical = if (wideStyle) 4.dp else 2.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(if (active) selectedIcon else unselectedIcon, null, Modifier.size(if (wideStyle) 24.dp else 22.dp), tint = color)
        Spacer(Modifier.height(if (wideStyle) 3.dp else 3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.let {
                if (wideStyle) it.copy(fontSize = 13.sp, lineHeight = 16.sp) else it.copy(fontSize = 12.sp, lineHeight = 15.sp)
            },
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun ShellTopHeaderRow(
    topInset: Dp,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(top = topInset)
            .padding(start = 10.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
internal fun FrostedHeaderScaffold(
    topInset: Dp,
    modifier: Modifier = Modifier,
    headerContent: @Composable RowScope.() -> Unit,
    bodyContent: @Composable (topClearance: Dp) -> Unit,
) {
    val density = LocalDensity.current
    val bgColor = MaterialTheme.colorScheme.background
    val fallbackHeaderHeight = topInset + 60.dp
    var measuredHeaderHeightPx by remember { mutableIntStateOf(0) }
    val topClearance = if (measuredHeaderHeightPx > 0) {
        with(density) { measuredHeaderHeightPx.toDp() }
    } else {
        fallbackHeaderHeight
    }
    val featherHeight = 18.dp
    val scrimBrush = remember(bgColor) {
        Brush.verticalGradient(
            0f to bgColor.copy(alpha = 0.99f),
            0.72f to bgColor.copy(alpha = 0.94f),
            0.88f to bgColor.copy(alpha = 0.55f),
            1f to Color.Transparent,
        )
    }

    Box(modifier.fillMaxSize()) {
        bodyContent(topClearance)

        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            Box(
                Modifier.fillMaxWidth()
                    .height(topClearance + featherHeight)
                    .background(scrimBrush),
            )
            ShellTopHeaderRow(
                topInset = topInset,
                modifier = Modifier.onSizeChanged { measuredHeaderHeightPx = it.height },
                content = headerContent,
            )
        }
    }
}

@Composable
internal fun AccountAvatar(state: ClientUiState, actions: ClientActions) {
    val openDrawer = LocalOpenAccountDrawer.current
    var expanded by remember { mutableStateOf(false) }
    val registry = LocalAvatarAnchorRegistry.current
    val label = stringResource(R.string.ft_account)
    val account = state.accountName.ifBlank { state.username.ifBlank { stringResource(R.string.app_name) } }
    val hidden = registry?.hideHomeAvatar == true
    Box {
        Box(
            Modifier.size(44.dp).clickable {
                if (openDrawer != null) openDrawer() else expanded = true
            }.semantics { contentDescription = label }.testTag("account_menu"),
            contentAlignment = Alignment.Center,
        ) {
            DisposableEffect(registry) {
                onDispose { registry?.homeAvatarBounds = null }
            }
            AccountAvatarBadge(
                label = account,
                avatarUrl = state.avatarUrl,
                size = 34.dp,
                filled = true,
                textStyle = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .onGloballyPositioned { coords ->
                        registry?.homeAvatarBounds = registry.toRootLocalRect(coords)
                    }
                    .graphicsLayer { alpha = if (hidden) 0f else 1f },
            )
        }
        if (openDrawer == null) {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                Column(Modifier.widthIn(min = 200.dp, max = 300.dp).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(account, style = MaterialTheme.typography.titleMedium)
                    if (state.instanceUrl.isNotBlank()) Text(
                        state.instanceUrl,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ft_refresh)) },
                    leadingIcon = { Icon(ClientIcons.Refresh, null) },
                    onClick = { expanded = false; actions.refresh() },
                    modifier = Modifier.testTag("account_refresh"),
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ft_sign_out)) },
                    leadingIcon = { Icon(ClientIcons.Logout, null) },
                    enabled = !state.busy,
                    onClick = { expanded = false; actions.logout() },
                    modifier = Modifier.testTag("sign_out"),
                )
            }
        }
    }
}

private class HomeScrollJumpCoordinator {
    var programmaticScroll: Boolean = false
    var job: Job? = null
    var generation: Int = 0
}

@Composable
private fun HomeAnchorPillsRow(
    anchorState: State<HomeFeedFilter>,
    cardsIndex: Int,
    continueIndex: Int?,
    discoveryIndex: Int?,
    libraryIndex: Int?,
    onJump: (HomeFeedFilter, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeAnchor = anchorState.value
    val currentAnchor = when {
        activeAnchor == HomeFeedFilter.CONTINUE && continueIndex == null -> HomeFeedFilter.ALL
        activeAnchor == HomeFeedFilter.DISCOVERY && discoveryIndex == null -> HomeFeedFilter.ALL
        activeAnchor == HomeFeedFilter.LIBRARY && libraryIndex == null -> HomeFeedFilter.ALL
        else -> activeAnchor
    }
    val onAllClick = remember(cardsIndex, onJump) { { onJump(HomeFeedFilter.ALL, cardsIndex) } }
    val onContinueClick = remember(continueIndex, onJump) {
        continueIndex?.let { idx -> { onJump(HomeFeedFilter.CONTINUE, idx) } }
    }
    val onDiscoveryClick = remember(discoveryIndex, onJump) {
        discoveryIndex?.let { idx -> { onJump(HomeFeedFilter.DISCOVERY, idx) } }
    }
    val onLibraryClick = remember(libraryIndex, onJump) {
        libraryIndex?.let { idx -> { onJump(HomeFeedFilter.LIBRARY, idx) } }
    }
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactCategoryPill(
            label = stringResource(R.string.ft_all),
            selected = currentAnchor == HomeFeedFilter.ALL,
            primaryHighlight = true,
            modifier = Modifier.testTag("home_filter_all"),
            onClick = onAllClick,
        )
        if (onContinueClick != null) {
            CompactCategoryPill(
                label = stringResource(R.string.ft_continue_listening),
                selected = currentAnchor == HomeFeedFilter.CONTINUE,
                primaryHighlight = true,
                modifier = Modifier.testTag("home_filter_continue"),
                onClick = onContinueClick,
            )
        }
        if (onDiscoveryClick != null) {
            CompactCategoryPill(
                label = stringResource(R.string.ft_listening_random),
                selected = currentAnchor == HomeFeedFilter.DISCOVERY,
                primaryHighlight = true,
                modifier = Modifier.testTag("home_filter_discovery"),
                onClick = onDiscoveryClick,
            )
        }
        if (onLibraryClick != null) {
            CompactCategoryPill(
                label = stringResource(R.string.ft_from_your_library),
                selected = currentAnchor == HomeFeedFilter.LIBRARY,
                primaryHighlight = true,
                modifier = Modifier.testTag("home_filter_library"),
                onClick = onLibraryClick,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeScreen(
    state: ClientUiState,
    actions: ClientActions,
    loader: ImageLoader?,
    openHistory: () -> Unit,
    openQueue: () -> Unit,
    openAllSongs: () -> Unit,
    topInset: Dp,
    bottomInset: Dp,
    scrollToTopNonce: Int = 0,
) {
    val listeningInteractions = LocalListeningInteractions.current
    val prefetchStrategy = remember { LazyListPrefetchStrategy(nestedPrefetchItemCount = 3) }
    val listState = rememberLazyListState(prefetchStrategy = prefetchStrategy)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val topAnchorThresholdPx = with(density) { 48.dp.roundToPx() }
    val activeAnchorState = rememberSaveable { mutableStateOf(HomeFeedFilter.ALL) }
    val jumpCoordinator = remember { HomeScrollJumpCoordinator() }

    val homeSongs = state.homeLibrarySongs.ifEmpty { state.songs }
    val homeLoading = state.homeLibraryLoading || (state.loading && homeSongs.isEmpty())
    val hasDiscoverySection = state.discoverySongs.isNotEmpty() || state.discoveryLoading || state.discoveryNotice != null
    var nextItemIndex = 0
    val cardsIndex = nextItemIndex++
    if (state.notice != null) nextItemIndex++
    val continueIndex = if (state.continueListeningSongs.isNotEmpty()) {
        val target = nextItemIndex
        nextItemIndex += 2
        target
    } else null
    val discoveryIndex = if (hasDiscoverySection) nextItemIndex++ else null
    val libraryIndex = if (homeSongs.isNotEmpty() || homeLoading) nextItemIndex else null

    val jumpToSection = remember(listState, scope) {
        { anchor: HomeFeedFilter, index: Int ->
            activeAnchorState.value = anchor
            jumpCoordinator.job?.cancel()
            val generation = ++jumpCoordinator.generation
            jumpCoordinator.job = scope.launch {
                jumpCoordinator.programmaticScroll = true
                try {
                    val distance = index - listState.firstVisibleItemIndex
                    if (abs(distance) >= 3) {
                        val intermediate = if (distance > 0) (index - 2).coerceAtLeast(0) else index + 2
                        listState.scrollToItem(intermediate)
                    }
                    listState.animateScrollToItem(index)
                } finally {
                    if (jumpCoordinator.generation == generation) {
                        jumpCoordinator.programmaticScroll = false
                    }
                }
            }
            Unit
        }
    }

    LaunchedEffect(scrollToTopNonce) {
        if (scrollToTopNonce > 0) {
            jumpToSection(HomeFeedFilter.ALL, 0)
        }
    }

    LaunchedEffect(listState, continueIndex, discoveryIndex, libraryIndex, topAnchorThresholdPx) {
        snapshotFlow {
            if (!listState.isScrollInProgress || jumpCoordinator.programmaticScroll) {
                null
            } else {
                val firstIdx = listState.firstVisibleItemIndex
                val firstOffset = listState.firstVisibleItemScrollOffset
                val canForward = listState.canScrollForward
                val lastVisible = if (!canForward) {
                    listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                } else {
                    -1
                }
                when {
                    firstIdx == 0 && firstOffset < topAnchorThresholdPx -> HomeFeedFilter.ALL
                    libraryIndex != null && (firstIdx >= libraryIndex || (!canForward && lastVisible >= libraryIndex)) -> HomeFeedFilter.LIBRARY
                    discoveryIndex != null && (firstIdx >= discoveryIndex || (!canForward && lastVisible >= discoveryIndex)) -> HomeFeedFilter.DISCOVERY
                    continueIndex != null && firstIdx >= continueIndex -> HomeFeedFilter.CONTINUE
                    else -> HomeFeedFilter.ALL
                }
            }
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { anchor ->
                activeAnchorState.value = anchor
            }
    }

    BoxWithConstraints(Modifier.fillMaxSize().testTag("home_page")) {
        val shelfAvailableWidth = maxWidth
        val isCompactPhone = maxWidth < 600.dp
        val cardWidth = ((maxWidth - 48.dp) / 2.15f).coerceIn(148.dp, 208.dp)
        val quickCardWidth = when {
            maxWidth >= 840.dp -> ((maxWidth - 48.dp) * 0.34f).coerceIn(328.dp, 376.dp)
            maxWidth >= 600.dp -> ((maxWidth - 44.dp) * 0.50f).coerceIn(300.dp, 360.dp)
            else -> (maxWidth - 32.dp).coerceAtLeast(292.dp)
        }
        val quickCardHeight = when {
            maxWidth >= 840.dp -> (quickCardWidth * 0.58f).coerceIn(190.dp, 214.dp)
            maxWidth >= 600.dp -> (quickCardWidth * 0.58f).coerceIn(174.dp, 208.dp)
            else -> (quickCardWidth * 0.54f).coerceIn(176.dp, 204.dp)
        }
        val favoritePlaylist = state.playlists.firstOrNull { it.kind == "favorite" }
        val toFavorites = remember(actions, favoritePlaylist) {
            {
                actions.navigate(MainDestination.LIBRARY)
                if (favoritePlaylist != null) {
                    actions.openPlaylist(favoritePlaylist)
                } else {
                    actions.selectTab(LibraryTab.FAVORITES)
                }
            }
        }
        val continueListeningItems = remember(
            state.continueListeningSongs,
            state.favoriteSongs,
            state.playlists,
            state.artists,
            state.spotlightArtist,
            state.recentSearches,
            state.playHistory,
            state.playStats,
        ) {
            buildContinueListeningCardItems(state)
        }

        FrostedHeaderScaffold(
            topInset = topInset,
            modifier = Modifier.fillMaxSize(),
            headerContent = {
                AccountAvatar(state, actions)
                HomeAnchorPillsRow(
                    anchorState = activeAnchorState,
                    cardsIndex = cardsIndex,
                    continueIndex = continueIndex,
                    discoveryIndex = discoveryIndex,
                    libraryIndex = libraryIndex,
                    onJump = jumpToSection,
                    modifier = Modifier.weight(1f),
                )
            },
        ) { topClearance ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("home_feed"),
                contentPadding = PaddingValues(top = topClearance + 2.dp, bottom = bottomInset),
            ) {
                item(key = "featured-cards", contentType = "home_cards") {
                val favoriteCount = state.favoriteSongs.size.takeIf { it > 0 } ?: favoritePlaylist?.songCount ?: 0
                val favoriteSubtitle = if (favoriteCount > 0) {
                    pluralStringResource(R.plurals.ft_track_count, favoriteCount, favoriteCount)
                } else {
                    stringResource(R.string.ft_favorites)
                }
                val libraryCoverPool = remember(state.songs) {
                    state.songs.mapNotNull { it.coverUrl?.trim()?.takeIf(String::isNotEmpty) }
                        .distinct()
                        .take(8)
                }
                val favoriteCovers = remember(
                    state.latestFavoriteCoverUrl,
                    favoritePlaylist?.customCoverUrl,
                    favoritePlaylist?.previewCovers,
                    state.favoriteSongs,
                    libraryCoverPool,
                ) {
                    (listOfNotNull(
                        state.latestFavoriteCoverUrl?.trim()?.takeIf(String::isNotEmpty),
                        favoritePlaylist?.customCoverUrl?.trim()?.takeIf(String::isNotEmpty),
                    ) +
                        favoritePlaylist?.previewCovers.orEmpty().mapNotNull { it.trim().takeIf(String::isNotEmpty) } +
                        state.favoriteSongs.mapNotNull { it.coverUrl?.trim()?.takeIf(String::isNotEmpty) } +
                        libraryCoverPool)
                        .distinct()
                        .take(8)
                }
                val historyCoverSong = state.homeHistoryPreviewSong ?: state.playHistory.firstOrNull()
                val historyCovers = remember(historyCoverSong?.coverUrl, state.playHistory) {
                    (listOfNotNull(historyCoverSong?.coverUrl?.trim()?.takeIf(String::isNotEmpty)) +
                        state.playHistory.mapNotNull { it.coverUrl?.trim()?.takeIf(String::isNotEmpty) })
                        .distinct()
                        .take(4)
                }
                val queueCoverSong = state.playback.current ?: state.playback.queue.firstOrNull()
                val queueCovers = remember(queueCoverSong?.coverUrl, state.playback.queue) {
                    (listOfNotNull(queueCoverSong?.coverUrl?.trim()?.takeIf(String::isNotEmpty)) +
                        state.playback.queue.mapNotNull { it.coverUrl?.trim()?.takeIf(String::isNotEmpty) })
                        .distinct()
                        .take(4)
                }
                val queueSubtitle = state.playback.current?.title
                    ?: if (state.playback.queue.isNotEmpty()) {
                        pluralStringResource(R.plurals.ft_track_count, state.playback.queue.size, state.playback.queue.size)
                    } else {
                        stringResource(R.string.ft_queue_empty)
                    }
                val topPlayed = state.playStats?.songs?.firstOrNull()
                val totalPlays = state.playStats?.totalPlays ?: 0L
                val statsSubtitle = topPlayed?.song?.title ?: if (totalPlays > 0L) {
                    stringResource(R.string.ft_listening_play_count, totalPlays.toInt())
                } else {
                    stringResource(R.string.ft_listening_empty_stats)
                }
                val statsCovers = remember(topPlayed?.song?.coverUrl, state.playStats) {
                    (listOfNotNull(topPlayed?.song?.coverUrl?.trim()?.takeIf(String::isNotEmpty)) +
                        state.playStats?.songs?.mapNotNull { it.song.coverUrl?.trim()?.takeIf(String::isNotEmpty) }.orEmpty())
                        .distinct()
                        .take(4)
                }

                // Order: 1. Favorites -> 2. Queue -> 3. History -> 4. Stats
                val heroCardSpecs = listOf(
                    HomeHeroCardSpec(
                        key = "card-favorites",
                        tag = "home_favorites",
                        title = favoritePlaylist?.name ?: stringResource(R.string.ft_liked_songs),
                        subtitle = favoriteSubtitle,
                        badgeText = stringResource(R.string.ft_favorites),
                        icon = ClientIcons.StarFilled,
                        accentColor = Color(0xFFEF3150),
                        coverUrls = favoriteCovers,
                        backdropCoverUrl = favoriteCovers.firstOrNull(),
                        showFavoriteStarCover = true,
                        onClick = toFavorites,
                        quickActionIcon = ClientIcons.Play,
                        quickActionDescription = stringResource(R.string.ft_play_favorites),
                        quickActionTag = "home_favorites_play",
                        onQuickAction = actions::playFavorites,
                        quickActionContainerColor = Color.White,
                        quickActionContentColor = Color(0xFFEF3150),
                    ),
                    HomeHeroCardSpec(
                        key = "card-queue",
                        tag = "home_queue_card",
                        title = stringResource(R.string.ft_queue),
                        subtitle = queueSubtitle,
                        badgeText = stringResource(R.string.ft_queue),
                        icon = ClientIcons.Queue,
                        accentColor = Color(0xFF14B8A6),
                        coverUrls = queueCovers,
                        backdropCoverUrl = queueCovers.firstOrNull() ?: libraryCoverPool.firstOrNull(),
                        onClick = openQueue,
                    ),
                    HomeHeroCardSpec(
                        key = "card-history",
                        tag = "home_history",
                        title = stringResource(R.string.ft_play_history),
                        subtitle = historyCoverSong?.title ?: stringResource(R.string.ft_continue_listening),
                        badgeText = stringResource(R.string.ft_drawer_recent_plays),
                        icon = ClientIcons.History,
                        accentColor = Color(0xFF38BDF8),
                        coverUrls = historyCovers,
                        backdropCoverUrl = historyCovers.firstOrNull() ?: libraryCoverPool.firstOrNull(),
                        onClick = openHistory,
                    ),
                    HomeHeroCardSpec(
                        key = "card-stats",
                        tag = "home_stats_card",
                        title = stringResource(R.string.ft_listening_statistics),
                        subtitle = statsSubtitle,
                        badgeText = stringResource(R.string.ft_listening_statistics),
                        icon = ClientIcons.Stats,
                        accentColor = Color(0xFFA855F7),
                        coverUrls = statsCovers,
                        backdropCoverUrl = statsCovers.firstOrNull() ?: libraryCoverPool.firstOrNull(),
                        onClick = { listeningInteractions?.statistics?.invoke() },
                    ),
                )

                if (isCompactPhone) {
                    val pagerState = rememberPagerState(pageCount = { heroCardSpecs.size })
                    Column(Modifier.fillMaxWidth()) {
                        HorizontalPager(
                            state = pagerState,
                            beyondViewportPageCount = 3,
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            pageSpacing = 12.dp,
                            key = { heroCardSpecs[it].key },
                            modifier = Modifier.fillMaxWidth().testTag("home_featured_cards"),
                        ) { page ->
                            val spec = heroCardSpecs[page]
                            FeaturedHeroCard(
                                spec = spec,
                                loader = loader,
                                width = quickCardWidth,
                                height = quickCardHeight,
                                modifier = Modifier.testTag(spec.tag),
                            )
                        }
                        HomeFeaturedPagerDots(
                            pageCount = heroCardSpecs.size,
                            currentPage = pagerState.currentPage,
                            onSelectPage = { target -> scope.launch { pagerState.animateScrollToPage(target) } },
                        )
                    }
                } else {
                    LazyRow(
                        Modifier.fillMaxWidth().testTag("home_featured_cards"),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(heroCardSpecs, key = { it.key }) { spec ->
                            FeaturedHeroCard(
                                spec = spec,
                                loader = loader,
                                width = quickCardWidth,
                                height = quickCardHeight,
                                modifier = Modifier.testTag(spec.tag),
                            )
                        }
                    }
                }
            }
            state.notice?.let { notice ->
                item(key = "song-notice", contentType = "notice") { InlineNotice(notice, actions::refresh) }
            }
            if (continueListeningItems.isNotEmpty()) {
                item(key = "history-title", contentType = "section_heading") { ShelfHeading(stringResource(R.string.ft_continue_listening)) }
                item(key = "history-shelf", contentType = "song_shelf") {
                    ContinueListeningMixedShelf(
                        items = continueListeningItems,
                        currentSongId = state.playback.current?.id,
                        loader = loader,
                        availableWidth = shelfAvailableWidth,
                        onPlaySong = { song, indexInContinue ->
                            if (indexInContinue >= 0) {
                                actions.playSongs(state.continueListeningSongs, indexInContinue)
                            } else {
                                actions.playSong(song)
                            }
                        },
                        onOpenPlaylist = actions::openPlaylist,
                        onOpenArtist = actions::openArtist,
                    )
                }
            }
            if (hasDiscoverySection) {
                item(key = "listening-discovery", contentType = "discovery_section") {
                    ListeningDiscoverySection(state, actions, loader, availableWidth = shelfAvailableWidth)
                }
            }
            if (homeSongs.isNotEmpty() || homeLoading) {
                item(key = "songs-title", contentType = "section_heading") {
                    ShelfHeading(
                        title = stringResource(R.string.ft_from_your_library),
                        showAll = openAllSongs,
                        actionTag = "home_all_songs",
                        playAll = if (homeSongs.isNotEmpty()) {{ actions.playSongs(homeSongs, 0) }} else null,
                        playAllTag = "home_library_play_all",
                    )
                }
                item(key = "song-shelf", contentType = "song_shelf") {
                    when {
                        homeLoading && homeSongs.isEmpty() -> ShelfLoading(cardWidth)
                        homeSongs.isEmpty() -> EmptyContent(
                            message = stringResource(R.string.ft_empty_catalog),
                            retry = null,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        )
                        else -> UnifiedSongSectionList(
                            songs = homeSongs,
                            currentSongId = state.playback.current?.id,
                            playing = state.playback.playing,
                            buffering = state.playback.buffering,
                            loader = loader,
                            limit = 16,
                            tagPrefix = "home_song_",
                            availableWidth = shelfAvailableWidth,
                        ) { song, _ ->
                            actions.playSingleAsNext(song)
                        }
                    }
                }
            }
            if (!homeLoading && !state.discoveryLoading && state.continueListeningSongs.isEmpty() &&
                state.discoverySongs.isEmpty() && homeSongs.isEmpty()
            ) {
                item(key = "home-empty", contentType = "empty") {
                    EmptyContent(
                        message = stringResource(if (state.notice != null) R.string.ft_unavailable else R.string.ft_empty_catalog),
                        retry = if (state.notice != null) actions::refresh else null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun DiscoverScreen(
    state: ClientUiState,
    actions: ClientActions,
    loader: ImageLoader?,
    openAllSongs: () -> Unit,
    openAllAlbums: () -> Unit,
    topInset: Dp,
    bottomInset: Dp,
    scrollToTopNonce: Int = 0,
) {
    val listeningInteractions = LocalListeningInteractions.current
    val listState = rememberLazyListState()
    LaunchedEffect(scrollToTopNonce) {
        if (scrollToTopNonce > 0) {
            if (listState.firstVisibleItemIndex > 4) {
                listState.scrollToItem(3)
            }
            listState.animateScrollToItem(0)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("discover_page")) {
        val discoverAvailableWidth = maxWidth
        val cardWidth = ((maxWidth - 48.dp) / 2.15f).coerceIn(148.dp, 208.dp)

        FrostedHeaderScaffold(
            topInset = topInset,
            modifier = Modifier.fillMaxSize(),
            headerContent = {
                AccountAvatar(state, actions)
                LanguageFilterRow(
                    selected = state.discoverLanguage,
                    tagPrefix = "discover_lang",
                    counts = state.languageCounts,
                    onSelect = actions::selectDiscoverLanguage,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    primaryHighlight = true,
                    showCounts = false,
                )
            },
        ) { topClearance ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("discover_feed"),
                contentPadding = PaddingValues(top = topClearance + 2.dp, bottom = bottomInset),
            ) {
            state.notice?.let { notice ->
                item(key = "discover-song-notice") { InlineNotice(notice, actions::refresh) }
            }
            state.artistsNotice?.let { notice ->
                item(key = "discover-artist-notice") { InlineNotice(notice, actions::refresh) }
            }
            state.albumsNotice?.let { notice ->
                item(key = "discover-album-notice") { InlineNotice(notice, actions::refresh) }
            }
            if (state.artists.isNotEmpty() || state.artistsLoading) {
                item(key = "discover-artists-title") {
                    ShelfHeading(
                        stringResource(R.string.ft_listening_artists),
                        showAll = listeningInteractions?.artists,
                        actionTag = "discover_all_artists",
                    )
                }
                item(key = "discover-artists-shelf") {
                    when {
                        state.artistsLoading && state.artists.isEmpty() -> ShelfLoading(cardWidth)
                        state.artists.isNotEmpty() -> LazyRow(
                            Modifier.fillMaxWidth().testTag("discover_artist_shelf"),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(state.artists.take(12), key = { it.id ?: ("local:" + it.name) }) { artist ->
                                ListeningArtistCard(artist, loader, Modifier.width(128.dp)) { actions.openArtist(artist) }
                            }
                        }
                    }
                }
            }
            if (state.albums.isNotEmpty() || state.albumsLoading) {
                item(key = "discover-albums-title") {
                    ShelfHeading(
                        stringResource(R.string.ft_your_albums),
                        showAll = openAllAlbums,
                        actionTag = "discover_all_albums",
                    )
                }
                item(key = "discover-albums-shelf") {
                    when {
                        state.albumsLoading && state.albums.isEmpty() -> ShelfLoading(cardWidth)
                        state.albums.isNotEmpty() -> LazyRow(
                            Modifier.fillMaxWidth().testTag("discover_album_shelf"),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(state.albums.take(12), key = { it.id }) { album ->
                                AlbumCard(album, loader, Modifier.width(cardWidth).testTag("album_" + album.id)) { actions.openAlbum(album) }
                            }
                        }
                    }
                }
            }
            if (state.songs.isNotEmpty() || state.loading) {
                item(key = "discover-songs-title") {
                    ShelfHeading(
                        stringResource(R.string.ft_from_your_library),
                        showAll = openAllSongs,
                        actionTag = "discover_all_songs",
                    )
                }
                item(key = "discover-songs-shelf") {
                    when {
                        state.loading && state.songs.isEmpty() -> ShelfLoading(cardWidth)
                        state.songs.isNotEmpty() -> UnifiedSongSectionList(
                            songs = state.songs,
                            currentSongId = state.playback.current?.id,
                            playing = state.playback.playing,
                            buffering = state.playback.buffering,
                            loader = loader,
                            limit = 16,
                            tagPrefix = "discover_song_",
                            availableWidth = discoverAvailableWidth,
                        ) { _, index -> actions.playSongs(state.songs, index) }
                    }
                }
            }
            if (!state.loading && !state.albumsLoading && !state.artistsLoading &&
                state.artists.isEmpty() && state.albums.isEmpty() && state.songs.isEmpty()
            ) {
                item(key = "discover-empty") {
                    EmptyContent(
                        message = stringResource(if (state.notice != null || state.albumsNotice != null || state.artistsNotice != null) R.string.ft_unavailable else R.string.ft_empty_catalog),
                        retry = if (state.notice != null || state.albumsNotice != null || state.artistsNotice != null) actions::refresh else null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                    )
                }
            }
            }
        }
    }
}

@Composable
internal fun LanguageFilterRow(
    selected: String,
    tagPrefix: String,
    onSelect: (String) -> Unit,
    counts: Map<String, Int> = emptyMap(),
    modifier: Modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    primaryHighlight: Boolean = false,
    showCounts: Boolean = true,
) {
    val allLabel = stringResource(R.string.ft_all)
    val zhLabel = stringResource(R.string.ft_lang_zh)
    val enLabel = stringResource(R.string.ft_lang_en)
    val jaLabel = stringResource(R.string.ft_lang_ja)
    val koLabel = stringResource(R.string.ft_lang_ko)
    val instrumentalLabel = stringResource(R.string.ft_lang_instrumental)
    val otherLabel = stringResource(R.string.ft_lang_other)
    val categories = buildList {
        add("all" to allLabel)
        add("zh" to zhLabel)
        add("en" to enLabel)
        add("ja" to jaLabel)
        if ((counts["ko"] ?: 0) > 0 || selected == "ko") add("ko" to koLabel)
        add("instrumental" to instrumentalLabel)
        if ((counts["other"] ?: 0) > 0 || selected == "other") add("other" to otherLabel)
    }
    LazyRow(
        modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(categories, key = { "${tagPrefix}_${it.first}" }) { (key, baseLabel) ->
            val count = if (showCounts) {
                when {
                    counts.isEmpty() -> null
                    key == "all" -> counts["all"] ?: counts.values.sum().takeIf { it > 0 }
                    else -> counts[key]
                }
            } else {
                null
            }
            val label = if (count != null && count > 0) {
                stringResource(R.string.ft_lang_count_label, baseLabel, count)
            } else {
                baseLabel
            }
            CompactCategoryPill(
                label = label,
                selected = selected == key,
                primaryHighlight = primaryHighlight,
                modifier = Modifier.testTag("${tagPrefix}_$key"),
                onClick = { onSelect(key) },
            )
        }
    }
}

@Composable
private fun HomeAllSongsScreen(
    state: ClientUiState,
    actions: ClientActions,
    loader: ImageLoader?,
    topInset: Dp,
    bottomInset: Dp,
    onBack: () -> Unit,
) {
    val multiSelect = rememberSongMultiSelectState()
    var batchBarHeight by remember { mutableStateOf(0.dp) }
    LaunchedEffect(state.songs) { multiSelect.prune(state.songs.map { it.id }.toSet()) }
    BackHandler(enabled = multiSelect.active) { multiSelect.exit() }

    Box(Modifier.fillMaxSize().padding(top = topInset).testTag("home_all_songs_page")) {
        Column(Modifier.fillMaxSize()) {
            if (multiSelect.active) {
                MultiSelectTopBar(
                    selectedCount = multiSelect.selectedIds.size,
                    allSelected = multiSelect.allSelected(state.songs.map { it.id }),
                    tagPrefix = "all_songs",
                    onSelectAllToggle = { multiSelect.toggleSelectAll(state.songs.map { it.id }) },
                    onExit = multiSelect::exit,
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActionIcon(ClientIcons.ChevronLeft, stringResource(R.string.ft_back), onBack)
                    Text(stringResource(R.string.ft_all_songs), Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (state.songs.isNotEmpty()) {
                        TextButton(
                            onClick = { multiSelect.enter() },
                            modifier = Modifier.heightIn(min = FlareTokens.TouchTargetMin).testTag("all_songs_multiselect"),
                        ) {
                            Text(stringResource(R.string.ft_multiselect))
                        }
                        TextButton(
                            onClick = { actions.playSongs(state.songs, 0) },
                            modifier = Modifier.heightIn(min = FlareTokens.TouchTargetMin).testTag("all_songs_play_all"),
                        ) {
                            Text(stringResource(R.string.ft_play_all))
                        }
                    }
                }
            }
            LanguageFilterRow(
                selected = state.songLanguage,
                tagPrefix = "all_songs_lang",
                onSelect = actions::selectSongLanguage,
                counts = state.languageCounts,
            )
            ListeningSongSortRow(state, actions)
            state.notice?.let { InlineNotice(it, actions::refresh, "list_retry") }
            SongFeed(
                state = state,
                actions = actions,
                loader = loader,
                bottomInset = bottomInset,
                modifier = Modifier.weight(1f),
                selectionActive = multiSelect.active,
                selectedIds = multiSelect.selectedIds,
                onLongSelect = { multiSelect.enter(it.id) },
                onToggleSelect = { multiSelect.toggle(it.id) },
                bottomExtraPadding = if (multiSelect.active) maxOf(76.dp, batchBarHeight + 24.dp) else 0.dp,
            )
        }
        BatchSongActionBar(
            visible = multiSelect.active,
            selectedSongs = multiSelect.selectedSongs(state.songs),
            state = state,
            actions = actions,
            canRemoveFromPlaylist = false,
            bottomInset = bottomInset,
            onBarHeightChanged = { batchBarHeight = it },
            onExitMultiSelect = multiSelect::exit,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun HomeAllAlbumsScreen(
    state: ClientUiState,
    actions: ClientActions,
    loader: ImageLoader?,
    topInset: Dp,
    bottomInset: Dp,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(top = topInset).testTag("home_all_albums_page")) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionIcon(ClientIcons.ChevronLeft, stringResource(R.string.ft_back), onBack)
            Text(stringResource(R.string.ft_your_albums), Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
        }
        LanguageFilterRow(
            selected = state.albumLanguage,
            tagPrefix = "all_albums_lang",
            onSelect = actions::selectAlbumLanguage,
            counts = state.languageCounts,
        )
        AlbumGrid(state = state, actions = actions, loader = loader, bottomInset = bottomInset, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CompactCategoryPill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    primaryHighlight: Boolean = false,
    onClick: () -> Unit,
) {
    val activeBg = if (primaryHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val activeFg = if (primaryHighlight) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.surface
    val bgState = animateColorAsState(
        targetValue = if (selected) activeBg else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(180),
        label = "pill_bg",
    )
    val fgState = animateColorAsState(
        targetValue = if (selected) activeFg else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(180),
        label = "pill_fg",
    )
    val baseLabelStyle = MaterialTheme.typography.labelMedium
    val labelStyle = remember(baseLabelStyle) {
        baseLabelStyle.copy(fontWeight = FontWeight.SemiBold)
    }
    Box(
        modifier
            .heightIn(min = 48.dp)
            .flarePressScale(pressedScale = 0.95f, hapticOnTap = true)
            .semantics { semanticSelected = selected }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.heightIn(min = 32.dp)
                .clip(CircleShape)
                .drawBehind { drawRect(bgState.value) }
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = label,
                style = labelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = ColorProducer { fgState.value },
            )
        }
    }
}

private data class HomeHeroCardSpec(
    val key: String,
    val tag: String,
    val title: String,
    val subtitle: String,
    val badgeText: String,
    val icon: ImageVector,
    val accentColor: Color,
    val coverUrls: List<String>,
    val backdropCoverUrl: String? = null,
    val showFavoriteStarCover: Boolean = false,
    val onClick: () -> Unit,
    val quickActionIcon: ImageVector? = null,
    val quickActionDescription: String? = null,
    val quickActionTag: String? = null,
    val onQuickAction: (() -> Unit)? = null,
    val quickActionContainerColor: Color? = null,
    val quickActionContentColor: Color? = null,
)

@Composable
private fun HomeFeaturedPagerDots(
    pageCount: Int,
    currentPage: Int,
    onSelectPage: (Int) -> Unit,
) = PagerIndicatorDots(
    pageCount = pageCount,
    currentPage = currentPage,
    onSelectPage = onSelectPage,
    containerTag = "home_featured_dots",
    dotTagPrefix = "home_featured_dot_",
)

@Composable
private fun PagerIndicatorDots(
    pageCount: Int,
    currentPage: Int,
    onSelectPage: (Int) -> Unit,
    containerTag: String,
    dotTagPrefix: String,
    topPadding: Dp = 10.dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = 2.dp)
            .testTag(containerTag),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val selected = currentPage == index
            val dotWidth by animateDpAsState(
                targetValue = if (selected) 18.dp else 6.dp,
                label = "pager_dot_width",
            )
            val dotColor by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
                },
                label = "pager_dot_color",
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .height(6.dp)
                    .width(dotWidth)
                    .clip(CircleShape)
                    .background(dotColor)
                    .clickable { onSelectPage(index) }
                    .testTag("$dotTagPrefix$index"),
            )
        }
    }
}

/**
 * Editorial featured card for the Home top shelf with layered cover backdrop,
 * 3D fanned artwork stack, directional cinema scrim, and crisp typography.
 */
@Composable
private fun FeaturedHeroCard(
    spec: HomeHeroCardSpec,
    loader: ImageLoader?,
    width: Dp = 264.dp,
    height: Dp = 160.dp,
    modifier: Modifier = Modifier,
) {
    val largeCard = width >= 272.dp
    val cornerSize = 28.dp
    val innerPad = if (largeCard) 16.dp else 14.dp
    val actionBtnSize = if (largeCard) 48.dp else 44.dp
    val shape = RoundedCornerShape(cornerSize)
    Box(
        modifier
            .width(width)
            .height(height)
            .flarePressScale(pressedScale = 0.97f)
            .shadow(6.dp, shape, ambientColor = Color.Black.copy(alpha = 0.20f), spotColor = Color.Black.copy(alpha = 0.20f))
            .clip(shape)
            .background(Color(0xFF0B1016))
            .clickable(onClick = spec.onClick),
    ) {
        // Layer A: Deep editorial base + subtle upper-right radial accent glow
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF121820), Color(0xFF0B1016)),
                        ),
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(spec.accentColor.copy(alpha = 0.32f), Color.Transparent),
                            center = Offset(size.width * 0.78f, size.height * 0.22f),
                            radius = size.maxDimension * 0.85f,
                        ),
                    )
                },
        )

        // Layer B: Pre-blurred 64x64 ambient cover backdrop (scaled softly instead of live GPU blur)
        if (loader != null && !spec.backdropCoverUrl.isNullOrBlank()) {
            PreblurredCoverBackdrop(
                coverUrl = spec.backdropCoverUrl,
                imageLoader = loader,
                alpha = 0.50f,
                targetSize = 64,
                radius = 12,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.35f
                        scaleY = 1.35f
                    },
            )
        }

        // Layer C: Card-specific artwork stage
        if (spec.showFavoriteStarCover) {
            if (spec.coverUrls.size >= 3) {
                HeroCardPhotoWallBackdrop(
                    coverUrls = spec.coverUrls,
                    loader = loader,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            HeroCardFavoritesStack(
                coverUrls = spec.coverUrls,
                loader = loader,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 18.dp, end = 18.dp),
            )
        } else if (spec.coverUrls.isNotEmpty()) {
            HeroCardFanoutArtworkStack(
                coverUrls = spec.coverUrls,
                loader = loader,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp),
            )
        } else {
            HeroCardEmptyEmblem(
                icon = spec.icon,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 20.dp),
            )
        }

        // Layer D: Directional dark editorial scrim for foreground typography contrast
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color(0xFF090E14).copy(alpha = 0.86f),
                            0.52f to Color(0xFF090E14).copy(alpha = 0.46f),
                            0.88f to Color.Transparent,
                        ),
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.35f to Color(0xFF080D13).copy(alpha = 0.28f),
                            0.68f to Color(0xFF080D13).copy(alpha = 0.76f),
                            1f to Color(0xFF080D13).copy(alpha = 0.95f),
                        ),
                    )
                },
        )

        // Outer glass highlight border
        Box(
            Modifier
                .fillMaxSize()
                .border(1.dp, Color.White.copy(alpha = 0.14f), shape),
        )

        // Top-left category pill
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(innerPad)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .border(0.7.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(spec.icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
            Text(
                spec.badgeText,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Bottom title/subtitle + action/chevron anchor
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(start = innerPad, end = innerPad, bottom = innerPad),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    spec.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = if (largeCard) 22.sp else 20.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    spec.subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = if (largeCard) 14.sp else 13.sp),
                    color = Color.White.copy(alpha = 0.84f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (spec.quickActionIcon != null && spec.onQuickAction != null) {
                FilledIconButton(
                    onClick = spec.onQuickAction,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = spec.quickActionContainerColor ?: Color.White,
                        contentColor = spec.quickActionContentColor ?: spec.accentColor,
                    ),
                    modifier = Modifier
                        .size(actionBtnSize)
                        .then(if (spec.quickActionTag != null) Modifier.testTag(spec.quickActionTag) else Modifier)
                        .semantics { if (spec.quickActionDescription != null) contentDescription = spec.quickActionDescription },
                ) {
                    Icon(spec.quickActionIcon, null, Modifier.size(if (largeCard) 22.dp else 20.dp))
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .border(0.7.dp, Color.White.copy(alpha = 0.24f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(ClientIcons.ChevronRight, null, Modifier.size(18.dp), tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun HeroCardPhotoWallBackdrop(
    coverUrls: List<String>,
    loader: ImageLoader?,
    modifier: Modifier = Modifier,
) {
    if (coverUrls.isEmpty()) return
    val tileShape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .graphicsLayer {
                rotationZ = -7f
                scaleX = 1.12f
                scaleY = 1.12f
                alpha = 0.42f
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.wrapContentWidth(unbounded = true),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(2) { rowIdx ->
                val rowOffset = if (rowIdx == 0) 0 else coverUrls.size / 2
                Row(
                    modifier = Modifier
                        .offset(x = if (rowIdx == 1) (-26).dp else 0.dp)
                        .wrapContentWidth(unbounded = true),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(6) { colIdx ->
                        val url = coverUrls[(rowOffset + colIdx) % coverUrls.size]
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(tileShape)
                                .background(Color(0xFF1E2631)),
                        ) {
                            if (loader != null) {
                                AsyncImage(
                                    model = buildCoverImageRequest(LocalContext.current, url, ThumbArtworkPx, loader),
                                    imageLoader = loader,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCardCoverTile(
    url: String,
    loader: ImageLoader?,
    tileSize: Dp,
    cornerRadius: Dp,
    topOffset: Dp,
    endOffset: Dp,
    scale: Float,
    rotationDegrees: Float,
    tileAlpha: Float,
    borderAlpha: Float,
    shadowElevation: Dp,
    shadowAlpha: Float,
) {
    val tileShape = RoundedCornerShape(cornerRadius)
    Box(
        Modifier
            .padding(top = topOffset, end = endOffset)
            .size(tileSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotationDegrees
                alpha = tileAlpha
            }
            .shadow(
                shadowElevation,
                tileShape,
                ambientColor = Color.Black.copy(alpha = shadowAlpha),
                spotColor = Color.Black.copy(alpha = shadowAlpha),
            )
            .clip(tileShape)
            .background(Color(0xFF1F2A36))
            .border(1.dp, Color.White.copy(alpha = borderAlpha), tileShape),
    ) {
        if (loader != null) {
            AsyncImage(
                model = url,
                imageLoader = loader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun HeroCardFavoritesStack(
    coverUrls: List<String>,
    loader: ImageLoader?,
    modifier: Modifier = Modifier,
) {
    val frontShape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier.size(width = 152.dp, height = 104.dp),
        contentAlignment = Alignment.TopEnd,
    ) {
        coverUrls.getOrNull(1)?.let { backUrl ->
            HeroCardCoverTile(
                url = backUrl,
                loader = loader,
                tileSize = 88.dp,
                cornerRadius = 18.dp,
                topOffset = 12.dp,
                endOffset = 52.dp,
                scale = 0.82f,
                rotationDegrees = -14f,
                tileAlpha = 0.72f,
                borderAlpha = 0.22f,
                shadowElevation = 10.dp,
                shadowAlpha = 0.36f,
            )
        }
        coverUrls.getOrNull(0)?.let { midUrl ->
            HeroCardCoverTile(
                url = midUrl,
                loader = loader,
                tileSize = 88.dp,
                cornerRadius = 18.dp,
                topOffset = 6.dp,
                endOffset = 26.dp,
                scale = 0.90f,
                rotationDegrees = -6f,
                tileAlpha = 0.88f,
                borderAlpha = 0.26f,
                shadowElevation = 12.dp,
                shadowAlpha = 0.38f,
            )
        }

        Box(
            Modifier
                .size(88.dp)
                .graphicsLayer { rotationZ = 4f }
                .shadow(14.dp, frontShape, ambientColor = Color.Black.copy(alpha = 0.42f), spotColor = Color.Black.copy(alpha = 0.42f))
                .clip(frontShape)
                .border(1.dp, Color.White.copy(alpha = 0.30f), frontShape),
        ) {
            FavoriteHeartCover(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun HeroCardFanoutArtworkStack(
    coverUrls: List<String>,
    loader: ImageLoader?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(width = 186.dp, height = 116.dp),
        contentAlignment = Alignment.TopEnd,
    ) {
        coverUrls.getOrNull(2)?.let { backUrl ->
            HeroCardCoverTile(
                url = backUrl,
                loader = loader,
                tileSize = 96.dp,
                cornerRadius = 18.dp,
                topOffset = 14.dp,
                endOffset = 68.dp,
                scale = 0.80f,
                rotationDegrees = -18f,
                tileAlpha = 0.58f,
                borderAlpha = 0.22f,
                shadowElevation = 14.dp,
                shadowAlpha = 0.40f,
            )
        }
        coverUrls.getOrNull(1)?.let { midUrl ->
            HeroCardCoverTile(
                url = midUrl,
                loader = loader,
                tileSize = 96.dp,
                cornerRadius = 18.dp,
                topOffset = 8.dp,
                endOffset = 36.dp,
                scale = 0.90f,
                rotationDegrees = -8f,
                tileAlpha = 0.82f,
                borderAlpha = 0.22f,
                shadowElevation = 14.dp,
                shadowAlpha = 0.42f,
            )
        }
        coverUrls.getOrNull(0)?.let { frontUrl ->
            HeroCardCoverTile(
                url = frontUrl,
                loader = loader,
                tileSize = 96.dp,
                cornerRadius = 18.dp,
                topOffset = 2.dp,
                endOffset = 6.dp,
                scale = 1.0f,
                rotationDegrees = 5f,
                tileAlpha = 1.0f,
                borderAlpha = 0.22f,
                shadowElevation = 14.dp,
                shadowAlpha = 0.48f,
            )
        }
    }
}

@Composable
private fun HeroCardEmptyEmblem(
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(104.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { rotationZ = -10f },
                    tint = Color.White.copy(alpha = 0.62f),
                )
            }
        }
    }
}

private sealed interface ContinueListeningCardItem {
    val itemKey: String
    val testTag: String

    data class SongCard(val song: Song, val indexInContinue: Int) : ContinueListeningCardItem {
        override val itemKey: String = "song:${song.id}"
        override val testTag: String = "home_continue_song_${song.id}"
    }

    data class PlaylistCard(val playlist: PlaylistSummary) : ContinueListeningCardItem {
        override val itemKey: String = "playlist:${playlist.id}"
        override val testTag: String = "home_continue_playlist_${playlist.id}"
    }

    data class ArtistCard(val artist: Artist, val fallbackCoverUrl: String? = null) : ContinueListeningCardItem {
        override val itemKey: String = "artist:${artist.id ?: artist.name}"
        override val testTag: String = "home_continue_artist_${artist.name}"
    }
}

private fun buildContinueListeningCardItems(state: ClientUiState): List<ContinueListeningCardItem> {
    if (state.continueListeningSongs.isEmpty()) return emptyList()

    val seenSongIds = HashSet<String>()
    val songs = mutableListOf<ContinueListeningCardItem.SongCard>()
    state.continueListeningSongs.forEachIndexed { index, song ->
        if (songs.size < 18 && seenSongIds.add(song.id)) {
            songs += ContinueListeningCardItem.SongCard(song, indexInContinue = index)
        }
    }
    (state.playHistory + state.favoriteSongs).forEach { song ->
        if (songs.size < 18 && song.canPlay && seenSongIds.add(song.id)) {
            songs += ContinueListeningCardItem.SongCard(song, indexInContinue = -1)
        }
    }

    val playlists = state.playlists
        .filter { it.kind != "favorite" && it.songCount > 0 }
        .sortedByDescending { it.updatedAt.coerceAtLeast(it.createdAt) }
        .distinctBy { it.id }
        .take(5)
        .map { ContinueListeningCardItem.PlaylistCard(it) }

    val fallbackCoversByArtist = buildMap<String, String> {
        (state.continueListeningSongs + state.playHistory + state.favoriteSongs + state.songs).forEach { song ->
            val cover = song.coverUrl?.takeIf(String::isNotBlank) ?: return@forEach
            val key = song.artist.trim().lowercase(Locale.ROOT)
            if (key.isNotEmpty() && !containsKey(key)) put(key, cover)
            song.artists.forEach { a ->
                val aKey = a.name.trim().lowercase(Locale.ROOT)
                if (aKey.isNotEmpty() && !containsKey(aKey)) put(aKey, cover)
            }
        }
    }
    val artistsByName = state.artists.associateBy { it.name.trim().lowercase(Locale.ROOT) }
    val seenArtists = HashSet<String>()
    val artists = mutableListOf<ContinueListeningCardItem.ArtistCard>()
    fun addArtistCandidate(artist: Artist) {
        if (artists.size >= 4) return
        val normalized = artist.name.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty() || !seenArtists.add(normalized)) return
        val resolved = artistsByName[normalized] ?: artist
        val fallbackCover = resolved.photoUrl?.takeIf(String::isNotBlank)
            ?: resolved.coverUrl?.takeIf(String::isNotBlank)
            ?: fallbackCoversByArtist[normalized]
        artists += ContinueListeningCardItem.ArtistCard(resolved, fallbackCover)
    }

    state.recentSearches.asSequence()
        .filter { it.type == "artist" && it.name.isNotBlank() }
        .forEach { entity ->
            addArtistCandidate(Artist(name = entity.name, id = entity.id.ifBlank { null }))
        }
    state.spotlightArtist?.let { spot ->
        addArtistCandidate(Artist(name = spot.artist, songCount = spot.songCount, photoUrl = spot.photoUrl, coverUrl = spot.coverUrl))
    }
    val listeningArtistNames = (state.continueListeningSongs + state.playHistory)
        .map { it.artist.trim().lowercase(Locale.ROOT) }
        .filter { it.isNotEmpty() }
        .toSet()
    state.artists.filter { it.name.trim().lowercase(Locale.ROOT) in listeningArtistNames }.forEach(::addArtistCandidate)
    state.artists.forEach(::addArtistCandidate)
    (state.playStats?.songs?.map { it.song }.orEmpty() + state.playHistory + state.continueListeningSongs).forEach { song ->
        val primary = song.artists.firstOrNull()
        if (primary != null && primary.name.isNotBlank()) {
            addArtistCandidate(Artist(name = primary.name, id = primary.id, coverUrl = song.coverUrl))
        } else if (song.artist.isNotBlank()) {
            addArtistCandidate(Artist(name = song.artist, id = song.artistId, coverUrl = song.coverUrl))
        }
    }

    val result = mutableListOf<ContinueListeningCardItem>()
    var songCursor = 0
    var playlistCursor = 0
    var artistCursor = 0

    // Interleave playlists and artists among songs, capping at 27 items (3 pages of 3x3 on phone)
    while (result.size < 27 && (songCursor < songs.size || playlistCursor < playlists.size || artistCursor < artists.size)) {
        repeat(2) {
            if (result.size < 27 && songCursor < songs.size) result += songs[songCursor++]
        }
        if (result.size < 27 && playlistCursor < playlists.size) result += playlists[playlistCursor++]
        if (result.size < 27 && songCursor < songs.size) result += songs[songCursor++]
        if (result.size < 27 && artistCursor < artists.size) result += artists[artistCursor++]
    }
    return result
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueListeningMixedShelf(
    items: List<ContinueListeningCardItem>,
    currentSongId: String?,
    loader: ImageLoader?,
    availableWidth: Dp,
    onPlaySong: (Song, Int) -> Unit,
    onOpenPlaylist: (PlaylistSummary) -> Unit,
    onOpenArtist: (Artist) -> Unit,
) {
    // Follows YouTube Music Speed Dial responsive column/spacing breakdown
    val cols = when {
        availableWidth >= 900.dp -> 6
        availableWidth >= 600.dp -> 4
        else -> 3
    }
    val rows = if (availableWidth >= 600.dp) 2 else 3
    val pageMargin = 16.dp
    val spacing = when {
        availableWidth >= 900.dp -> 10.dp
        availableWidth >= 600.dp -> 8.dp
        else -> 6.dp
    }
    val itemWidth = ((availableWidth - pageMargin * 2 - spacing * (cols - 1)) / cols).coerceAtLeast(88.dp)
    val itemsPerPage = cols * rows
    val pages = remember(items, itemsPerPage) { items.chunked(itemsPerPage) }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 3,
            contentPadding = PaddingValues(horizontal = pageMargin),
            pageSpacing = 12.dp,
            verticalAlignment = Alignment.Top,
            key = { pageIdx -> "speed_dial_page_${pageIdx}_${pages[pageIdx].firstOrNull()?.itemKey}" },
            modifier = Modifier.fillMaxWidth().testTag("home_continue_shelf"),
        ) { pageIdx ->
            val pageRows = pages[pageIdx].chunked(cols)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                pageRows.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                    ) {
                        rowItems.forEach { item ->
                            when (item) {
                                is ContinueListeningCardItem.SongCard -> {
                                    val isCurrent = item.song.id == currentSongId
                                    ContinueListeningGridTile(
                                        title = item.song.title,
                                        showChevron = false,
                                        highlighted = isCurrent,
                                        size = itemWidth,
                                        onClick = { onPlaySong(item.song, item.indexInContinue) },
                                        modifier = Modifier.testTag(item.testTag),
                                        artwork = {
                                            Box(
                                                Modifier
                                                    .fillMaxSize()
                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(ClientIcons.Music, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                if (loader != null && !item.song.coverUrl.isNullOrBlank()) {
                                                    AsyncImage(
                                                        model = item.song.coverUrl,
                                                        imageLoader = loader,
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize(),
                                                    )
                                                }
                                            }
                                        },
                                    )
                                }

                                is ContinueListeningCardItem.PlaylistCard -> {
                                    ContinueListeningGridTile(
                                        title = item.playlist.name,
                                        showChevron = true,
                                        highlighted = false,
                                        size = itemWidth,
                                        onClick = { onOpenPlaylist(item.playlist) },
                                        modifier = Modifier.testTag(item.testTag),
                                        artwork = {
                                            PlaylistCoverArt(
                                                name = item.playlist.name,
                                                customCoverUrl = item.playlist.customCoverUrl,
                                                previewCovers = item.playlist.previewCovers,
                                                isFavorite = false,
                                                loader = loader,
                                                modifier = Modifier.fillMaxSize(),
                                                radius = 8.dp,
                                            )
                                        },
                                    )
                                }

                                is ContinueListeningCardItem.ArtistCard -> {
                                    val artistCover = item.artist.photoUrl?.takeIf(String::isNotBlank)
                                        ?: item.artist.coverUrl?.takeIf(String::isNotBlank)
                                        ?: item.fallbackCoverUrl
                                    ContinueListeningGridTile(
                                        title = item.artist.name,
                                        showChevron = true,
                                        highlighted = false,
                                        size = itemWidth,
                                        onClick = { onOpenArtist(item.artist) },
                                        modifier = Modifier.testTag(item.testTag),
                                        artwork = {
                                            Box(
                                                Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Brush.linearGradient(
                                                            listOf(
                                                                MaterialTheme.colorScheme.secondaryContainer,
                                                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                                            ),
                                                        ),
                                                    ),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(ClientIcons.Artist, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                if (loader != null && !artistCover.isNullOrBlank()) {
                                                    AsyncImage(
                                                        model = artistCover,
                                                        imageLoader = loader,
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize(),
                                                    )
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (pages.size > 1) {
            PagerIndicatorDots(
                pageCount = pages.size,
                currentPage = pagerState.currentPage,
                onSelectPage = { target -> scope.launch { pagerState.animateScrollToPage(target) } },
                containerTag = "home_continue_dots",
                dotTagPrefix = "home_continue_dot_",
                topPadding = 10.dp,
            )
        }
    }
}

internal data class ContinueListeningTileMetrics(
    val fontSize: TextUnit,
    val lineHeight: TextUnit,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val chevronSize: Dp,
    val chevronSpacing: Dp,
    val cornerRadius: Dp,
    val scrimStartFraction: Float,
    val maxLines: Int,
)

internal fun resolveContinueListeningTileMetrics(tileSize: Dp): ContinueListeningTileMetrics {
    val sizeDp = tileSize.value.coerceAtLeast(80f)
    val t = ((sizeDp - 96f) / (196f - 96f)).coerceIn(0f, 1f)
    val fontSp = lerp(11.5f, 16.0f, t)
    return ContinueListeningTileMetrics(
        fontSize = fontSp.sp,
        lineHeight = (fontSp * 1.25f).sp,
        horizontalPadding = lerp(6f, 12f, t).dp,
        verticalPadding = lerp(5f, 10f, t).dp,
        chevronSize = lerp(12f, 17f, t).dp,
        chevronSpacing = lerp(2f, 4f, t).dp,
        cornerRadius = lerp(8f, 12f, t).dp,
        scrimStartFraction = lerp(0.46f, 0.36f, t),
        maxLines = if (sizeDp >= 148f) 2 else 1,
    )
}

@Composable
internal fun ContinueListeningGridTile(
    title: String,
    showChevron: Boolean,
    highlighted: Boolean,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    artwork: @Composable () -> Unit,
) {
    val metrics = remember(size) { resolveContinueListeningTileMetrics(size) }
    val shape = RoundedCornerShape(metrics.cornerRadius)
    Box(
        modifier
            .size(size)
            .flarePressScale(pressedScale = 0.97f)
            .clip(shape)
            .border(0.6.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), shape)
            .clickable(onClick = onClick),
    ) {
        artwork()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        metrics.scrimStartFraction to Color.Transparent,
                        ((metrics.scrimStartFraction + 1f) * 0.5f) to Color.Black.copy(alpha = 0.42f),
                        1f to Color.Black.copy(alpha = 0.84f),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = metrics.horizontalPadding, vertical = metrics.verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = metrics.fontSize,
                    lineHeight = metrics.lineHeight,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (highlighted) MaterialTheme.colorScheme.primary else Color.White,
                maxLines = metrics.maxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (showChevron) {
                Icon(
                    ClientIcons.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = metrics.chevronSpacing)
                        .size(metrics.chevronSize),
                    tint = Color.White.copy(alpha = 0.90f),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShelfHeading(
    title: String,
    showAll: (() -> Unit)? = null,
    actionTag: String? = null,
    playAll: (() -> Unit)? = null,
    playAllTag: String? = null,
) {
    val stackActions = LocalDensity.current.fontScale >= 1.35f && (playAll != null || showAll != null)
    val playAllButton: @Composable () -> Unit = {
        if (playAll != null) {
            TextButton(
                onClick = playAll,
                modifier = (if (playAllTag != null) Modifier.testTag(playAllTag) else Modifier)
                    .heightIn(min = 48.dp),
            ) {
                Icon(ClientIcons.Play, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.ft_play_all), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    val showAllButton: @Composable () -> Unit = {
        if (showAll != null) {
            TextButton(
                onClick = showAll,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = (if (actionTag != null) Modifier.testTag(actionTag) else Modifier)
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.ft_show_all), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    if (stackActions) {
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 6.dp),
        ) {
            Text(
                title,
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 21.sp, fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                playAllButton()
                showAllButton()
            }
        }
    } else {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 21.sp, fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            playAllButton()
            showAllButton()
        }
    }
}

@Composable
private fun AlbumCard(album: Album, loader: ImageLoader?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)) {
        AlbumArtwork(album, loader, Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(8.dp))
        Text(
            album.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            album.artist,
            Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AlbumArtwork(album: Album, loader: ImageLoader?, modifier: Modifier, radius: Dp = 12.dp) {
    Box(
        modifier.clip(RoundedCornerShape(radius)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(ClientIcons.Music, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (loader != null && !album.coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = album.coverUrl,
                imageLoader = loader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ShelfLoading(width: Dp) {
    Row(Modifier.fillMaxWidth().padding(16.dp).testTag("home_loading"), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(2) {
            Box(
                Modifier.width(width).aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun CatalogScreen(
    state: ClientUiState,
    actions: ClientActions,
    loader: ImageLoader?,
    topInset: Dp,
    bottomInset: Dp,
) {
    val searchPage = state.destination == MainDestination.SEARCH
    val selectedPlaylist = if (!searchPage) state.selectedPlaylist else null
    val inFavoritesDetail = !searchPage && selectedPlaylist == null && state.tab == LibraryTab.FAVORITES
    val inPlaylistDetail = selectedPlaylist != null

    if (inFavoritesDetail) {
        val favLeadCover = state.latestFavoriteCoverUrl
            ?: state.favoriteSongs.firstOrNull { !it.coverUrl.isNullOrBlank() }?.coverUrl
        val previewCovers = remember(state.favoriteSongs) {
            state.favoriteSongs.mapNotNull { it.coverUrl?.takeIf(String::isNotBlank) }.distinct().take(4)
        }
        CollectionDetailScaffold(
            kindLabel = stringResource(R.string.ft_playlist_label),
            title = stringResource(R.string.ft_liked_songs),
            metaLine = pluralStringResource(R.plurals.ft_track_count, state.favoriteSongs.size, state.favoriteSongs.size),
            coverUrl = if (previewCovers.size >= 4) null else favLeadCover,
            previewCovers = previewCovers,
            isFavorite = true,
            fallbackIcon = ClientIcons.Heart,
            loader = loader,
            topInset = topInset,
            bottomInset = bottomInset,
            testTag = "library_page",
            backTag = "library_tab_catalog",
            onBack = { actions.selectTab(LibraryTab.CATALOG) },
            primaryActions = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = actions::playFavorites,
                        enabled = state.favoriteSongs.isNotEmpty() && !state.favoritesLoading,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                        modifier = Modifier.testTag("favorites_play_all"),
                    ) {
                        Icon(ClientIcons.Play, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ft_play_all))
                    }
                }
            },
            loading = state.favoritesLoading && state.favoriteSongs.isEmpty(),
            notice = state.favoritesNotice,
            noticeTag = "list_retry",
            onRetry = actions::refreshFavorites,
            emptyMessage = stringResource(if (state.favoritesNotice != null) R.string.ft_unavailable else R.string.ft_empty_catalog),
            isEmpty = state.favoriteSongs.isEmpty(),
            listTestTag = "favorites_song_list",
        ) {
            itemsIndexed(state.favoriteSongs, key = { index, song -> "$index:${song.id}" }) { _, song ->
                SongRow(
                    song = song,
                    imageLoader = loader,
                    current = song.id == state.playback.current?.id,
                    playing = state.playback.playing,
                    buffering = state.playback.buffering,
                    modifier = Modifier.testTag("song_" + song.id),
                ) { actions.playSong(song) }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(top = topInset).testTag(if (searchPage) "search_page" else "library_page")) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                inPlaylistDetail -> ActionIcon(
                    ClientIcons.ChevronLeft,
                    stringResource(R.string.ft_back),
                    actions::closePlaylist,
                    modifier = Modifier.testTag("library_playlist_back"),
                )
                inFavoritesDetail -> ActionIcon(
                    ClientIcons.ChevronLeft,
                    stringResource(R.string.ft_back),
                    { actions.selectTab(LibraryTab.CATALOG) },
                    modifier = Modifier.testTag("library_tab_catalog"),
                )
                else -> AccountAvatar(state, actions)
            }
            val headerTitle = when {
                searchPage -> stringResource(R.string.ft_search_page)
                inPlaylistDetail -> selectedPlaylist.name
                inFavoritesDetail -> stringResource(R.string.ft_liked_songs)
                else -> stringResource(R.string.ft_library_page)
            }
            Text(
                headerTitle,
                Modifier.weight(1f).padding(start = 6.dp),
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (searchPage) {
            SearchField(state, actions)
            state.notice?.let { InlineNotice(it, actions::refresh, "list_retry") }
            if (state.query.isBlank() && state.songs.isNotEmpty()) {
                Text(
                    stringResource(R.string.ft_from_your_library),
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            key(state.destination, state.tab) {
                SongFeed(
                    state = state,
                    actions = actions,
                    loader = loader,
                    bottomInset = bottomInset,
                    modifier = Modifier.weight(1f),
                )
            }
        } else if (inPlaylistDetail) {
            val playlist = selectedPlaylist
            state.playlistNotice?.let { InlineNotice(it, actions::refreshPlaylist, "playlist_retry") }
            when {
                state.playlistLoading && state.playlistSongs.isEmpty() -> {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.playlistSongs.isEmpty() -> {
                    EmptyContent(
                        message = stringResource(if (state.playlistNotice != null) R.string.ft_unavailable else R.string.ft_empty_catalog),
                        retry = if (state.playlistNotice != null) actions::refreshPlaylist else null,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
                else -> {
                    LazyColumn(
                        Modifier.fillMaxWidth().weight(1f).testTag("library_playlist_songs"),
                        contentPadding = PaddingValues(bottom = bottomInset),
                    ) {
                        item(key = "playlist-detail-header") {
                            PlaylistDetailHeader(
                                playlist = playlist,
                                songs = state.playlistSongs,
                                loader = loader,
                                onPlayAll = {
                                    state.playlistSongs.firstOrNull()?.let(actions::playSong)
                                },
                            )
                        }
                        itemsIndexed(state.playlistSongs, key = { index, song -> "$index:${song.id}" }) { _, song ->
                            SongRow(
                                song = song,
                                imageLoader = loader,
                                current = song.id == state.playback.current?.id,
                                playing = state.playback.playing,
                                buffering = state.playback.buffering,
                            ) { actions.playSong(song) }
                        }
                    }
                }
            }
        } else if (inFavoritesDetail) {
            state.favoritesNotice?.let { InlineNotice(it, actions::refreshFavorites, "list_retry") }
            when {
                state.favoritesLoading && state.favoriteSongs.isEmpty() -> {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.favoriteSongs.isNotEmpty() -> {
                    LazyColumn(
                        Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(bottom = bottomInset),
                    ) {
                        itemsIndexed(state.favoriteSongs, key = { index, song -> "$index:${song.id}" }) { _, song ->
                            SongRow(
                                song = song,
                                imageLoader = loader,
                                current = song.id == state.playback.current?.id,
                                playing = state.playback.playing,
                                buffering = state.playback.buffering,
                            ) { actions.playSong(song) }
                        }
                    }
                }
                else -> {
                    key(state.destination, state.tab) {
                        SongFeed(
                            state = state,
                            actions = actions,
                            loader = loader,
                            bottomInset = bottomInset,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        } else {
            // 资料库: personal account playlists page (Favorites first, then user-created playlists).
            val favoritePlaylist = state.playlists.firstOrNull { it.kind == "favorite" }
            val customPlaylists = state.playlists.filter { it.kind != "favorite" }
            val favoriteCount = favoritePlaylist?.songCount?.takeIf { it > 0 } ?: state.favoriteSongs.size
            val favoriteSubtitle = if (favoriteCount > 0) {
                pluralStringResource(R.plurals.ft_track_count, favoriteCount, favoriteCount)
            } else {
                stringResource(R.string.ft_favorites)
            }
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f).testTag("library_playlists"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomInset),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.playlistsNotice?.let { notice ->
                    item(key = "library-playlists-notice") {
                        InlineNotice(notice, actions::refreshPlaylists, "library_playlists_retry")
                    }
                }
                item(key = "library-favorites") {
                    Surface(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                if (favoritePlaylist != null) {
                                    actions.openPlaylist(favoritePlaylist)
                                } else {
                                    actions.selectTab(LibraryTab.FAVORITES)
                                }
                            }
                            .testTag("library_tab_favorites"),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(
                                Modifier.size(64.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(0.7.dp, Color(0x14000000), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                FavoriteHeartCover(Modifier.fillMaxSize())
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    favoritePlaylist?.name ?: stringResource(R.string.ft_liked_songs),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    favoriteSubtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            if (favoriteCount > 0 || state.favoriteSongs.isNotEmpty()) {
                                FilledIconButton(
                                    onClick = actions::playFavorites,
                                    shape = CircleShape,
                                    modifier = Modifier.size(40.dp).testTag("library_favorites_play"),
                                ) {
                                    Icon(
                                        ClientIcons.Play,
                                        contentDescription = stringResource(R.string.ft_play_favorites),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "library-playlists-heading") {
                    Text(
                        stringResource(R.string.ft_custom_playlists),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                when {
                    state.playlistsLoading && state.playlists.isEmpty() -> {
                        item(key = "library-playlists-loading") {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                    customPlaylists.isEmpty() -> {
                        item(key = "library-playlists-empty") {
                            Surface(
                                Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(18.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(
                                        ClientIcons.Music,
                                        null,
                                        Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        stringResource(R.string.ft_empty_playlists),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        items(customPlaylists, key = { it.id }) { playlist ->
                            PlaylistRow(
                                playlist = playlist,
                                loader = loader,
                                onClick = { actions.openPlaylist(playlist) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: PlaylistSummary,
    loader: ImageLoader?,
    onClick: () -> Unit,
) {
    val coverUrl = playlist.customCoverUrl ?: playlist.previewCovers.firstOrNull()
    Surface(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("playlist_${playlist.id}"),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (loader != null && !coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        imageLoader = loader,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(ClientIcons.Music, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = playlist.description.ifBlank {
                    pluralStringResource(R.plurals.ft_track_count, playlist.songCount, playlist.songCount)
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                ClientIcons.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaylistDetailHeader(
    playlist: PlaylistSummary,
    songs: List<Song>,
    loader: ImageLoader?,
    onPlayAll: () -> Unit,
) {
    val coverUrl = playlist.customCoverUrl ?: playlist.previewCovers.firstOrNull() ?: songs.firstOrNull()?.coverUrl
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (playlist.kind == "favorite") {
                FavoriteHeartCover(Modifier.fillMaxSize())
            } else if (loader != null && !coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = coverUrl,
                    imageLoader = loader,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    ClientIcons.Music,
                    null,
                    Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(Modifier.weight(1f)) {
            if (playlist.description.isNotBlank()) {
                Text(
                    playlist.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                pluralStringResource(R.plurals.ft_track_count, songs.size, songs.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val playLabel = stringResource(R.string.ft_play)
        FilledIconButton(
            onClick = onPlayAll,
            enabled = songs.isNotEmpty(),
            shape = CircleShape,
            modifier = Modifier.size(48.dp).testTag("playlist_play_all").semantics { contentDescription = playLabel },
        ) { Icon(ClientIcons.Play, null, Modifier.size(22.dp)) }
    }
}

@Composable
private fun SearchField(state: ClientUiState, actions: ClientActions) {
    var query by rememberSaveable(state.query, state.destination) { mutableStateOf(state.query) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = { focusManager.clearFocus(); keyboard?.hide(); actions.search(query.trim()) }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text(stringResource(R.string.ft_search_hint)) },
        singleLine = true,
        leadingIcon = { Icon(ClientIcons.Search, null, Modifier.size(22.dp)) },
        trailingIcon = {
            ActionIcon(
                ClientIcons.Search,
                stringResource(R.string.ft_search_action),
                submit,
                modifier = Modifier.testTag("search_submit"),
            )
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { submit() }),
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedBorderColor = MaterialTheme.colorScheme.outline,
            unfocusedBorderColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).testTag("search_field"),
    )
}

@Composable
private fun SongFeed(
    state: ClientUiState,
    actions: ClientActions,
    loader: ImageLoader?,
    bottomInset: Dp,
    modifier: Modifier,
    selectionActive: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onLongSelect: ((Song) -> Unit)? = null,
    onToggleSelect: ((Song) -> Unit)? = null,
    bottomExtraPadding: Dp = 0.dp,
    headerContent: (@Composable () -> Unit)? = null,
) {
    when {
        state.loading && state.songs.isEmpty() -> LoadingContent(modifier.testTag("catalog_loading"))
        state.songs.isEmpty() -> Column(modifier) {
            headerContent?.invoke()
            EmptyContent(
                message = stringResource(
                    when {
                        state.notice != null -> R.string.ft_unavailable
                        state.query.isNotBlank() -> R.string.ft_empty_search
                        state.destination == MainDestination.SEARCH -> R.string.ft_search_intro
                        state.tab == LibraryTab.FAVORITES -> R.string.ft_empty_favorites
                        else -> R.string.ft_empty_catalog
                    },
                ),
                retry = if (state.notice != null) actions::refresh else null,
                modifier = Modifier.weight(1f).testTag("catalog_empty"),
            )
        }
        else -> Column(modifier) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(
                Modifier.fillMaxSize().testTag("song_list"),
                contentPadding = PaddingValues(bottom = bottomInset + bottomExtraPadding),
            ) {
                if (headerContent != null) {
                    item(key = "library-shortcuts") { headerContent() }
                }
                items(state.songs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        imageLoader = loader,
                        current = song.id == state.playback.current?.id,
                        playing = state.playback.playing,
                        buffering = state.playback.buffering,
                        selectionActive = selectionActive,
                        selected = song.id in selectedIds,
                        onLongClick = onLongSelect?.let { { it(song) } },
                        modifier = Modifier.testTag("song_" + song.id),
                    ) {
                        if (selectionActive && onToggleSelect != null) onToggleSelect(song)
                        else actions.playSong(song)
                    }
                }
                if (state.hasMore) {
                    item(key = "more") { LoadMore(state.loadingMore, !state.loading, actions::loadMore, "load_more") }
                }
            }
        }
    }
}

@Composable
private fun AlbumGrid(
    state: ClientUiState,
    actions: ClientActions,
    loader: ImageLoader?,
    bottomInset: Dp,
    modifier: Modifier,
) {
    Column(modifier) {
        state.albumsNotice?.let { InlineNotice(it, actions::refresh, "albums_retry") }
        when {
            state.albumsLoading && state.albums.isEmpty() -> LoadingContent(Modifier.fillMaxSize())
            state.albums.isEmpty() -> EmptyContent(
                stringResource(if (state.albumsNotice != null) R.string.ft_unavailable else R.string.ft_empty_catalog),
                if (state.albumsNotice != null) actions::refresh else null,
                Modifier.fillMaxSize().testTag("albums_empty"),
            )
            else -> {
                if (state.albumsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(148.dp),
                    modifier = Modifier.fillMaxSize().testTag("album_grid"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomInset),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    gridItems(state.albums, key = { it.id }) { album ->
                        AlbumCard(album, loader, Modifier.testTag("album_" + album.id)) { actions.openAlbum(album) }
                    }
                    if (state.albumsHasMore) item(span = { GridItemSpan(maxLineSpan) }) {
                        LoadMore(state.albumsLoadingMore, !state.albumsLoading, actions::loadMoreAlbums, "albums_load_more")
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumScreen(state: ClientUiState, actions: ClientActions, loader: ImageLoader?, topInset: Dp, bottomInset: Dp) {
    val album = state.selectedAlbum ?: return
    val interaction = LocalListeningInteractions.current
    val multiSelect = rememberSongMultiSelectState()
    LaunchedEffect(album.id) { multiSelect.exit() }
    LaunchedEffect(state.albumSongs) { multiSelect.prune(state.albumSongs.map { it.id }.toSet()) }
    BackHandler(enabled = multiSelect.active) { multiSelect.exit() }

    val playLabel = stringResource(R.string.ft_play_album)
    CollectionDetailScaffold(
        kindLabel = stringResource(R.string.ft_album),
        title = album.title,
        subtitle = album.artist.ifBlank { stringResource(R.string.ft_unknown_artist) },
        onSubtitleClick = if (album.artist.isNotBlank()) {
            { actions.openArtist(Artist(album.artist)) }
        } else {
            null
        },
        metaLine = album.knownTrackCount?.let { pluralStringResource(R.plurals.ft_track_count, it, it) }.orEmpty(),
        coverUrl = album.coverUrl ?: state.albumSongs.firstOrNull { !it.coverUrl.isNullOrBlank() }?.coverUrl,
        fallbackIcon = ClientIcons.Disc,
        loader = loader,
        topInset = topInset,
        bottomInset = bottomInset,
        testTag = "album_detail",
        backTag = "album_back",
        onBack = actions::closeAlbum,
        topBarOverride = if (multiSelect.active) {
            {
                MultiSelectTopBar(
                    selectedCount = multiSelect.selectedIds.size,
                    allSelected = multiSelect.allSelected(state.albumSongs.map { it.id }),
                    tagPrefix = "album",
                    onSelectAllToggle = { multiSelect.toggleSelectAll(state.albumSongs.map { it.id }) },
                    onExit = multiSelect::exit,
                )
            }
        } else null,
        bottomOverlay = {
            BatchSongActionBar(
                visible = multiSelect.active,
                selectedSongs = multiSelect.selectedSongs(state.albumSongs),
                state = state,
                actions = actions,
                canRemoveFromPlaylist = false,
                bottomInset = bottomInset,
                onExitMultiSelect = multiSelect::exit,
            )
        },
        primaryActions = {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { state.albumSongs.firstOrNull()?.let(actions::playSong) },
                    enabled = state.albumSongs.isNotEmpty() && !state.albumLoading,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    modifier = Modifier.heightIn(min = FlareTokens.TouchTargetMin).testTag("album_play").semantics { contentDescription = playLabel },
                ) {
                    Icon(ClientIcons.Play, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ft_play_all))
                }
                FilledTonalButton(
                    onClick = { actions.playSongs(state.albumSongs, 0, shuffle = true) },
                    enabled = state.albumSongs.isNotEmpty() && !state.albumLoading,
                    shape = CircleShape,
                    modifier = Modifier.heightIn(min = FlareTokens.TouchTargetMin).testTag("album_shuffle"),
                ) {
                    Icon(ClientIcons.Shuffle, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ft_quick_shuffle))
                }
                FilledTonalButton(
                    onClick = { actions.enqueue(state.albumSongs) },
                    enabled = state.albumSongs.isNotEmpty() && !state.albumLoading,
                    shape = CircleShape,
                    modifier = Modifier.heightIn(min = FlareTokens.TouchTargetMin).testTag("album_enqueue"),
                ) {
                    Text(stringResource(R.string.ft_listening_enqueue))
                }
                OutlinedButton(
                    onClick = { if (multiSelect.active) multiSelect.exit() else multiSelect.enter() },
                    enabled = state.albumSongs.isNotEmpty() && !state.albumLoading,
                    shape = CircleShape,
                    modifier = Modifier.heightIn(min = FlareTokens.TouchTargetMin).testTag("album_multiselect"),
                ) {
                    Text(stringResource(if (multiSelect.active) R.string.ft_exit_multiselect else R.string.ft_multiselect))
                }
                if (interaction != null && state.albumSongs.isNotEmpty()) {
                    FilledTonalIconButton(
                        onClick = { interaction.addToPlaylists(state.albumSongs) },
                        modifier = Modifier.size(FlareTokens.TouchTargetMin).testTag("album_add_to_playlist"),
                    ) {
                        Icon(ClientIcons.Plus, stringResource(R.string.ft_listening_add_to_playlist), Modifier.size(20.dp))
                    }
                }
            }
        },
        loading = state.albumLoading,
        notice = state.albumNotice,
        noticeTag = "album_retry",
        onRetry = { actions.openAlbum(album) },
        emptyMessage = stringResource(R.string.ft_album_empty),
        isEmpty = !state.albumLoading && state.albumNotice == null && state.albumSongs.isEmpty(),
        listTestTag = "album_song_list",
    ) {
        items(state.albumSongs, key = { it.id }) { song ->
            SongRow(
                song = song,
                imageLoader = loader,
                current = song.isCurrentAlbumTrack(state.playback.current, album.id),
                playing = state.playback.playing,
                buffering = state.playback.buffering,
                selectionActive = multiSelect.active,
                selected = song.id in multiSelect.selectedIds,
                onLongClick = { multiSelect.enter(song.id) },
                modifier = Modifier.testTag("song_" + song.id),
            ) {
                if (multiSelect.active) multiSelect.toggle(song.id)
                else actions.playSong(song)
            }
        }
    }
}

@Composable
private fun InlineNotice(notice: ClientNotice, retry: () -> Unit, tag: String = "home_retry") {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Notice(notice)
        if (notice != ClientNotice.PAGE_LIMIT) {
            TextButton(onClick = retry, modifier = Modifier.testTag(tag)) {
                Text(stringResource(R.string.ft_retry))
            }
        }
    }
}

@Composable
private fun EmptyContent(message: String, retry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(ClientIcons.Music, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (retry != null) {
            TextButton(
                onClick = retry,
                modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp).testTag("catalog_retry"),
            ) {
                Text(stringResource(R.string.ft_retry))
            }
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun LoadMore(loading: Boolean, enabled: Boolean, load: () -> Unit, tag: String) {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        TextButton(
            onClick = load,
            enabled = enabled && !loading,
            modifier = Modifier.heightIn(min = 48.dp).testTag(tag),
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(if (loading) R.string.ft_loading_more else R.string.ft_load_more))
        }
    }
}

internal data class MiniPlayerLyricTarget(
    val songId: String,
    val line: LyricLine?,
    val primary: String,
    val secondary: String,
    val wordSynced: Boolean,
)

@Composable
internal fun resolveMiniPlayerLyricTarget(
    song: Song,
    buffering: Boolean,
    lyricState: LyricState,
    translationEnabled: Boolean,
): MiniPlayerLyricTarget {
    val unknownArtistLabel = stringResource(R.string.ft_unknown_artist)
    val fallbackArtist = song.artist.ifBlank { unknownArtistLabel }
    val activeLine = if (lyricState.status == LyricStatus.READY) {
        lyricState.lines.getOrNull(lyricState.activeLyricIndex)
    } else null
    val nextLyricText = if (lyricState.status == LyricStatus.READY && lyricState.activeLyricIndex >= 0) {
        lyricState.lines.getOrNull(lyricState.activeLyricIndex + 1)?.text?.takeIf { it.isNotBlank() }
    } else null
    val line1 = when {
        buffering -> stringResource(R.string.ft_buffering)
        lyricState.status == LyricStatus.INSTRUMENTAL -> stringResource(R.string.ft_instrumental_lyrics)
        activeLine != null && activeLine.text.isNotBlank() -> activeLine.text
        else -> song.title
    }
    val line2 = when {
        buffering -> song.title
        lyricState.status == LyricStatus.INSTRUMENTAL -> "${song.title} · $fallbackArtist"
        activeLine != null && activeLine.text.isNotBlank() ->
            activeLine.translation?.takeIf { translationEnabled && it.isNotBlank() }
                ?: nextLyricText
                ?: "${song.title} · $fallbackArtist"
        else -> fallbackArtist
    }
    val wordSynced = !buffering &&
        lyricState.status == LyricStatus.READY &&
        lyricState.syncMode == "word" &&
        activeLine != null &&
        activeLine.words.isNotEmpty()
    return MiniPlayerLyricTarget(
        songId = song.id,
        line = activeLine,
        primary = line1,
        secondary = line2,
        wordSynced = wordSynced,
    )
}

@Composable
internal fun MiniPlayerLyricSlotContent(
    lineSlot: MiniPlayerLyricTarget,
    positionMs: Long,
    playing: Boolean,
    buffering: Boolean,
    miniPlayerActive: Boolean,
    wideStyle: Boolean,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val activeScroll = miniPlayerActive && playing && !buffering
    Column(modifier.fillMaxWidth()) {
        if (lineSlot.wordSynced && lineSlot.line != null) {
            val livePositionMsState = rememberPlaybackPositionMsState(positionMs)
            KaraokeLyricText(
                line = lineSlot.line,
                basePositionMs = positionMs,
                playing = activeScroll,
                fontSize = if (wideStyle) 16.sp else 14.5.sp,
                lineHeight = if (wideStyle) 21.sp else 19.sp,
                sungColor = MaterialTheme.colorScheme.onSurface,
                unsungColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.46f else 0.60f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Start,
                trackHorizontalFocus = true,
                positionMsState = livePositionMsState,
            )
        } else {
            Text(
                lineSlot.primary,
                style = MaterialTheme.typography.titleMedium.let {
                    if (wideStyle) it.copy(fontSize = 16.sp, lineHeight = 21.sp) else it.copy(fontSize = 14.5.sp, lineHeight = 19.sp)
                },
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(
                        iterations = if (activeScroll) Int.MAX_VALUE else 0,
                        initialDelayMillis = 800,
                    ),
            )
        }
        Text(
            lineSlot.secondary,
            style = MaterialTheme.typography.bodyMedium.let {
                if (wideStyle) it.copy(fontSize = 13.5.sp, lineHeight = 18.sp) else it.copy(fontSize = 12.5.sp, lineHeight = 16.sp)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee(
                    iterations = if (activeScroll) Int.MAX_VALUE else 0,
                    initialDelayMillis = 1000,
                ),
        )
    }
}

/**
 * Docked/floating mini player bar aligned with the bottom navigation (compact mode renders as an
 * edge-to-edge solid strip docked directly above navigation; wide mode keeps a floating capsule):
 * - Play/Pause button only (no next button)
 * - Horizontal swipe gesture to skip Next (swipe left) / Previous (swipe right)
 * - Dual-line lyric/metadata slot ready for synced lyrics
 */
private data class MiniPlayerTrackCardState(
    val song: Song,
    val lyricTarget: MiniPlayerLyricTarget,
)

@Composable
private fun ShellMiniPlayer(
    playback: PlaybackSnapshot,
    lyricState: LyricState,
    translationEnabled: Boolean,
    actions: ClientActions,
    loader: ImageLoader?,
    openPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    dockHeight: Dp = FlareTokens.DockHeight,
    wideStyle: Boolean = false,
    miniPlayerActive: Boolean = true,
    playerTransitionRegistry: PlayerTransitionRegistry? = null,
    playerExpandProgress: () -> Float = { 0f },
    playerSheetMounted: () -> Boolean = { false },
    onExpandDragStart: () -> Unit = {},
    onExpandDragDelta: (Float) -> Float = { 0f },
    onExpandDragEnd: (Float) -> Unit = {},
) {
    val song = playback.current ?: return
    val playing = playback.playing || playback.buffering
    val dark = isFlareDarkTheme()
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 56.dp.toPx() }
    val maxOffsetPx = with(density) { 44.dp.toPx() }
    val flingVelocityPx = with(density) { 620.dp.toPx() }
    val unknownArtistLabel = stringResource(R.string.ft_unknown_artist)
    val lyricTarget = resolveMiniPlayerLyricTarget(
        song = song,
        buffering = playback.buffering,
        lyricState = lyricState,
        translationEnabled = translationEnabled,
    )
    val currentCardSlot = MiniPlayerTrackCardState(song = song, lyricTarget = lyricTarget)

    val miniCornerDp = if (wideStyle) 22.dp else 0.dp
    val compactShape = RoundedCornerShape(miniCornerDp)
    val miniSurfaceColor = if (wideStyle) {
        if (dark) Color(0xFF1F2024) else Color(0xFFF6F6F9)
    } else {
        if (dark) Color(0xFF16171B) else Color(0xFFF3F4F7)
    }
    val surfaceModifier = if (wideStyle) {
        Modifier.floatingDockSurface(shape = compactShape, dark = dark, elevation = 14.dp)
    } else {
        Modifier.background(miniSurfaceColor)
    }
    val haptic = LocalHapticFeedback.current
    val livePositionMsState = rememberPlaybackPositionMsState(playback.positionMs)
    val prevPreviewSlot = remember(
        playback.queue,
        playback.currentIndex,
        playback.previousQueueIndex,
        playback.repeatMode,
        playback.shuffle,
        unknownArtistLabel,
    ) {
        resolveAdjacentQueueSong(playback, -1)?.let { adj ->
            val artist = adj.artist.ifBlank { unknownArtistLabel }
            MiniPlayerTrackCardState(
                song = adj,
                lyricTarget = MiniPlayerLyricTarget(adj.id, null, adj.title, artist, false),
            )
        }
    }
    val nextPreviewSlot = remember(
        playback.queue,
        playback.currentIndex,
        playback.nextQueueIndex,
        playback.repeatMode,
        playback.shuffle,
        unknownArtistLabel,
    ) {
        resolveAdjacentQueueSong(playback, +1)?.let { adj ->
            val artist = adj.artist.ifBlank { unknownArtistLabel }
            MiniPlayerTrackCardState(
                song = adj,
                lyricTarget = MiniPlayerLyricTarget(adj.id, null, adj.title, artist, false),
            )
        }
    }
    val hasPreviousTrack = prevPreviewSlot != null
    fun canGoPreviousNow(): Boolean = hasPreviousTrack || livePositionMsState.value > 3000L
    val canGoNext = nextPreviewSlot != null || (
        playback.nextQueueIndex == null && (
            playback.currentIndex < playback.queue.lastIndex ||
                playback.repeatMode == 2 ||
                (playback.shuffle && playback.queue.size > 1)
            )
        )
    val directionTracker = rememberTrackDirectionTracker(playback)
    val trackDirection = directionTracker.peekDirection(playback)
    val trackCarousel = rememberTrackCarouselController(
        songId = song.id,
        slot = currentCardSlot,
        slideGapDp = 8.dp,
    )

    val latestActions by rememberUpdatedState(actions)
    val latestWideStyle by rememberUpdatedState(wideStyle)
    val latestCanGoNext by rememberUpdatedState(canGoNext)
    val latestCanGoPrev by rememberUpdatedState { canGoPreviousNow() }
    val latestRestartOnly by rememberUpdatedState { !hasPreviousTrack && livePositionMsState.value > 3000L }
    val latestOnExpandStart by rememberUpdatedState(onExpandDragStart)
    val latestOnExpandDelta by rememberUpdatedState(onExpandDragDelta)
    val latestOnExpandEnd by rememberUpdatedState(onExpandDragEnd)
    val miniArtCornerDp = if (wideStyle) 12.dp else 6.dp
    val miniArtShape = RoundedCornerShape(miniArtCornerDp)

    Box(
        modifier
            .fillMaxWidth()
            .height(dockHeight)
            .onGloballyPositioned { coords ->
                if (playerExpandProgress() <= 0.001f || playerTransitionRegistry?.miniBarBounds == null) {
                    playerTransitionRegistry?.updateMiniBar(coords, miniCornerDp, miniSurfaceColor)
                }
            }
            .graphicsLayer {
                if (wideStyle) {
                    alpha = if (playerExpandProgress() >= 0.16f) 0f else 1f
                }
            }
            .then(surfaceModifier)
            .pointerInput(
                trackCarousel,
                swipeThresholdPx,
                maxOffsetPx,
                flingVelocityPx,
            ) {
                val touchSlop = viewConfiguration.touchSlop
                val velocityTracker = VelocityTracker()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startPos = down.position
                    var mode = 0 // 0 = undecided, 1 = horizontal track swipe, 2 = vertical expand drag
                    var lastPos = startPos
                    velocityTracker.resetTracking()
                    velocityTracker.addPosition(down.uptimeMillis, startPos)
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (mode == 1) {
                                    change.consume()
                                    val velX = velocityTracker.calculateVelocity().x
                                    val commitDist = minOf(swipeThresholdPx, trackCarousel.spanPx * 0.25f)
                                    trackCarousel.finishHorizontalDrag(
                                        velocityX = velX,
                                        canGoPrevious = latestCanGoPrev(),
                                        canGoNext = latestCanGoNext,
                                        isPreviousRestartOnly = latestRestartOnly(),
                                        commitDistancePx = commitDist,
                                        flingVelocityPx = flingVelocityPx,
                                        directionTracker = directionTracker,
                                        onCommitHaptic = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                                        onNext = { latestActions.next() },
                                        onPrevious = { latestActions.previous() },
                                    )
                                } else if (mode == 2) {
                                    change.consume()
                                    val velY = velocityTracker.calculateVelocity().y
                                    latestOnExpandEnd(velY)
                                }
                                break
                            }
                            val curPos = change.position
                            val dx = curPos.x - lastPos.x
                            val dy = curPos.y - lastPos.y
                            lastPos = curPos
                            velocityTracker.addPosition(change.uptimeMillis, curPos)

                            if (mode == 0) {
                                val totalDx = curPos.x - startPos.x
                                val totalDy = curPos.y - startPos.y
                                val dist = hypot(totalDx, totalDy)
                                if (dist > touchSlop) {
                                    // YTM §4.3 directional angle lock (±45°..±135°): vertical drag never fights track swipes.
                                    if (abs(totalDy) >= abs(totalDx)) {
                                        if (!latestWideStyle && totalDy < 0f) {
                                            mode = 2
                                            change.consume()
                                            latestOnExpandStart()
                                            latestOnExpandDelta(totalDy)
                                        } else {
                                            break
                                        }
                                    } else {
                                        mode = 1
                                        change.consume()
                                        trackCarousel.startHorizontalDrag()
                                        trackCarousel.dragHorizontalBy(
                                            dx = totalDx,
                                            canGoPrevious = latestCanGoPrev(),
                                            canGoNext = latestCanGoNext,
                                            edgeResistanceLimitPx = maxOffsetPx,
                                        )
                                    }
                                }
                            } else if (mode == 1) {
                                change.consume()
                                trackCarousel.dragHorizontalBy(
                                    dx = dx,
                                    canGoPrevious = latestCanGoPrev(),
                                    canGoNext = latestCanGoNext,
                                    edgeResistanceLimitPx = maxOffsetPx,
                                )
                            } else {
                                change.consume()
                                latestOnExpandDelta(dy)
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        if (mode == 1) {
                            trackCarousel.cancelHorizontalDrag()
                        } else if (mode == 2) {
                            latestOnExpandEnd(0f)
                        }
                        throw cancelled
                    }
                }
            }
            .clickable(onClick = openPlayer)
            .testTag("mini_player"),
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = 8.dp, end = if (wideStyle) 8.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Only the inner song slot scrolls horizontally between tracks; outer dock and controls stay anchored without an extra card frame.
            val cardArtSize = if (wideStyle) 48.dp else 42.dp
            val cardFontOverflowScale = if (density.fontScale > 1.3f) ((density.fontScale - 1f).coerceAtMost(1f)) else 0f
            val cardOuterVerticalPad = lerp(if (wideStyle) 6f else 4f, if (wideStyle) 3f else 2f, cardFontOverflowScale).dp
            val cardInnerVerticalPad = lerp(if (wideStyle) 5f else 4f, if (wideStyle) 2.5f else 2f, cardFontOverflowScale).dp
            TrackCarouselViewport(
                controller = trackCarousel,
                songId = song.id,
                currentSlot = currentCardSlot,
                direction = trackDirection,
                previousPreviewSlot = prevPreviewSlot,
                nextPreviewSlot = nextPreviewSlot,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                clipVerticalAllowance = 3.dp,
                clipWhenIdle = true,
                contentAlignment = Alignment.CenterStart,
            ) { slot, isActiveSlot, _ ->
                key(slot.song.id) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = cardOuterVerticalPad)
                            .testTag(if (isActiveSlot) "mini_player_card_current" else "mini_player_card_preview_${slot.song.id}")
                            .padding(
                                horizontal = if (wideStyle) 10.dp else 8.dp,
                                vertical = cardInnerVerticalPad,
                            ),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(cardArtSize)
                                    .then(
                                        if (isActiveSlot) {
                                            Modifier.onGloballyPositioned { coords ->
                                                if (playerExpandProgress() <= 0.001f || playerTransitionRegistry?.miniArtworkBounds == null) {
                                                    playerTransitionRegistry?.updateMiniArtwork(coords, miniArtCornerDp)
                                                }
                                            }
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .graphicsLayer {
                                        alpha = if (
                                            !wideStyle &&
                                            isActiveSlot &&
                                            playerTransitionRegistry?.shouldHideMiniArtwork(
                                                playerExpandProgress(),
                                                transitionActive = playerSheetMounted(),
                                            ) == true
                                        ) {
                                            0f
                                        } else {
                                            1f
                                        }
                                    }
                                    .clip(miniArtShape)
                                    .border(0.6.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f), miniArtShape),
                            ) {
                                Artwork(
                                    slot.song,
                                    loader,
                                    Modifier.fillMaxSize(),
                                    radius = miniArtCornerDp,
                                    placeholderSize = 20.dp,
                                    decodeSize = cardArtSize,
                                )
                            }
                            Spacer(Modifier.width(if (wideStyle) 12.dp else 10.dp))
                            // Two-line slot matching web PlayerBarLyricPreview (word karaoke / synced line / title+artist).
                            AnimatedContent(
                                targetState = slot.lyricTarget,
                                modifier = Modifier.weight(1f).graphicsLayer {
                                    if (!wideStyle && isActiveSlot) {
                                        val p = playerExpandProgress().coerceIn(0f, 1f)
                                        val fadeP = if (p <= 0.001f) 0f else (p / 0.30f).coerceIn(0f, 1f)
                                        alpha = 1f - fadeP
                                        if (fadeP > 0f) {
                                            translationY = 22.dp.toPx() * fadeP
                                        }
                                    }
                                },
                                transitionSpec = {
                                    (slideInVertically(tween(240)) { it / 2 } + fadeIn(tween(200))) togetherWith
                                        (slideOutVertically(tween(180)) { -it / 2 } + fadeOut(tween(160)))
                                },
                                label = "mini_player_lines",
                            ) { lineSlot ->
                                MiniPlayerLyricSlotContent(
                                    lineSlot = lineSlot,
                                    positionMs = playback.positionMs,
                                    playing = playback.playing,
                                    buffering = playback.buffering,
                                    miniPlayerActive = miniPlayerActive,
                                    wideStyle = wideStyle,
                                    dark = dark,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(if (wideStyle) 4.dp else 2.dp))
            ActionIcon(
                if (playing) ClientIcons.Pause else ClientIcons.Play,
                stringResource(if (playing) R.string.ft_pause else R.string.ft_play),
                actions::togglePlay,
                modifier = Modifier
                    .size(if (wideStyle) 52.dp else 48.dp)
                    .graphicsLayer {
                        val p = playerExpandProgress().coerceIn(0f, 1f)
                        val fadeP = if (p <= 0.001f) 0f else (p / 0.30f).coerceIn(0f, 1f)
                        alpha = 1f - fadeP
                        if (fadeP > 0f) {
                            translationY = 22.dp.toPx() * fadeP
                        }
                    }
                    .testTag("mini_play"),
            )
        }
        // Subtle hairline progress indicator flush inside the bottom edge (drawn without per-tick re-layout).
        MiniPlayerProgressBar(
            fallbackPositionMs = playback.positionMs,
            durationMs = playback.durationMs,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            fillColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(2.dp)
                .graphicsLayer {
                    val p = playerExpandProgress().coerceIn(0f, 1f)
                    alpha = if (p <= 0.001f) 1f else (1f - (p / 0.24f)).coerceIn(0f, 1f)
                },
        )
    }
}

@Composable
internal fun MiniPlayerProgressBar(
    fallbackPositionMs: Long,
    durationMs: Long,
    trackColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier,
) {
    val livePositionMsState = rememberPlaybackPositionMsState(fallbackPositionMs)
    Box(
        modifier.drawBehind {
            drawRect(color = trackColor)
            val progress = if (durationMs > 0) {
                (livePositionMsState.value.toFloat() / durationMs).coerceIn(0f, 1f)
            } else 0f
            if (progress > 0f) {
                drawRect(color = fillColor, size = Size(size.width * progress, size.height))
            }
        },
    )
}
