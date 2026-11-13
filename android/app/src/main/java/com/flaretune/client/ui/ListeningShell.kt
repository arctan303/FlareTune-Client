package com.flaretune.client.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected as semanticSelected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import com.flaretune.client.R
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
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

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
) {
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    var homeSubpage by rememberSaveable { mutableStateOf(HomeSubpage.NONE) }

    val safeTopInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    val topInset = max(safeTopInset, 24.dp)
    val bottomSystemInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val compactBottomClearance = max(bottomSystemInset, 14.dp)

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val shellMaxWidth = maxWidth
        val wideLayout = shellMaxWidth >= 840.dp
        val wideDock = wideLayout && state.playback.current != null
        val fontScale = LocalDensity.current.fontScale
        val fontOverflowScale = if (fontScale > 1.3f) ((fontScale - 1f).coerceAtMost(1f)) else 0f
        val compactNavExtraHeight = if (!wideLayout) 12.dp * fontOverflowScale else 0.dp
        val compactMiniExtraHeight = if (!wideLayout) 16.dp * fontOverflowScale else 0.dp
        val wideDockExtraHeight = if (wideLayout) 10.dp * fontOverflowScale else 0.dp
        val dockHeight = if (wideLayout) FlareTokens.WideDockHeight + wideDockExtraHeight else FlareTokens.CompactNavHeight + compactNavExtraHeight
        val contentBottomInset = when {
            wideLayout -> 114.dp + wideDockExtraHeight + bottomSystemInset
            state.playback.current != null -> 136.dp + compactNavExtraHeight + compactMiniExtraHeight + compactBottomClearance
            else -> 66.dp + compactNavExtraHeight + compactBottomClearance
        }
        val navigateMain: (MainDestination) -> Unit = { dest ->
            homeSubpage = HomeSubpage.NONE
            historyOpen = false
            actions.navigate(dest)
        }
        val detailOpen = state.selectedArtist != null ||
            state.selectedAlbum != null ||
            (state.selectedPlaylist != null && state.destination != MainDestination.SEARCH)
        BackHandler(enabled = historyOpen && !detailOpen) {
            historyOpen = false
        }
        fun closeHomeSubpage() {
            homeSubpage = HomeSubpage.NONE
            if (state.destination == MainDestination.DISCOVER &&
                (state.songLanguage != state.discoverLanguage || state.albumLanguage != state.discoverLanguage)
            ) {
                actions.selectDiscoverLanguage(state.discoverLanguage)
            }
        }
        BackHandler(enabled = homeSubpage != HomeSubpage.NONE && !historyOpen && !detailOpen) {
            closeHomeSubpage()
        }

        val routeKey = when {
            state.selectedArtist != null -> "artist:" + (state.selectedArtist.id ?: state.selectedArtist.name)
            state.selectedAlbum != null -> "album:" + state.selectedAlbum.id
            state.selectedPlaylist != null && state.destination != MainDestination.SEARCH -> "playlist:" + state.selectedPlaylist.id
            historyOpen -> "history"
            state.destination == MainDestination.HOME -> "home:" + homeSubpage.name
            state.destination == MainDestination.DISCOVER -> "discover:" + homeSubpage.name
            else -> "dest:" + state.destination.name + ":" + state.tab.name
        }

        // Main content spans full height and scrolls underneath both the status bar and the floating dock.
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = routeKey,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                label = "shell_route",
            ) { activeRoute ->
                when {
                    activeRoute.startsWith("artist:") -> ListeningArtistPage(state, actions, imageLoader, topInset, contentBottomInset)
                    activeRoute.startsWith("album:") -> AlbumScreen(state, actions, imageLoader, topInset, contentBottomInset)
                    activeRoute.startsWith("playlist:") -> ListeningPlaylistDetailPage(state, actions, imageLoader, topInset, contentBottomInset)
                    activeRoute == "history" -> PlayHistoryPage(
                        state = state,
                        actions = actions,
                        loader = imageLoader,
                        topInset = topInset,
                        bottomInset = contentBottomInset,
                        onBack = { historyOpen = false },
                    )
                    activeRoute.startsWith("dest:" + MainDestination.SEARCH.name + ":") -> ListeningSearchPage(state, actions, imageLoader, topInset, contentBottomInset)
                    activeRoute == "dest:" + MainDestination.LIBRARY.name + ":" + LibraryTab.CATALOG.name -> ListeningLibraryOverview(state, actions, imageLoader, topInset, contentBottomInset)
                    activeRoute == "discover:" + HomeSubpage.ALL_SONGS.name -> HomeAllSongsScreen(
                        state = state,
                        actions = actions,
                        loader = imageLoader,
                        topInset = topInset,
                        bottomInset = contentBottomInset,
                        onBack = ::closeHomeSubpage,
                    )
                    activeRoute == "discover:" + HomeSubpage.ALL_ALBUMS.name -> HomeAllAlbumsScreen(
                        state = state,
                        actions = actions,
                        loader = imageLoader,
                        topInset = topInset,
                        bottomInset = contentBottomInset,
                        onBack = ::closeHomeSubpage,
                    )
                    activeRoute == "discover:" + HomeSubpage.NONE.name -> DiscoverScreen(
                        state = state,
                        actions = actions,
                        loader = imageLoader,
                        openAllSongs = { homeSubpage = HomeSubpage.ALL_SONGS },
                        openAllAlbums = { homeSubpage = HomeSubpage.ALL_ALBUMS },
                        topInset = topInset,
                        bottomInset = contentBottomInset,
                    )
                    activeRoute == "home:" + HomeSubpage.ALL_SONGS.name -> HomeAllSongsScreen(
                        state = state,
                        actions = actions,
                        loader = imageLoader,
                        topInset = topInset,
                        bottomInset = contentBottomInset,
                        onBack = ::closeHomeSubpage,
                    )
                    activeRoute == "home:" + HomeSubpage.ALL_ALBUMS.name -> HomeAllAlbumsScreen(
                        state = state,
                        actions = actions,
                        loader = imageLoader,
                        topInset = topInset,
                        bottomInset = contentBottomInset,
                        onBack = ::closeHomeSubpage,
                    )
                    activeRoute == "home:" + HomeSubpage.NONE.name -> HomeScreen(
                        state = state,
                        actions = actions,
                        loader = imageLoader,
                        openHistory = { historyOpen = true },
                        openQueue = openQueue,
                        openAllSongs = { homeSubpage = HomeSubpage.ALL_SONGS },
                        topInset = topInset,
                        bottomInset = contentBottomInset,
                    )
                    else -> CatalogScreen(state, actions, imageLoader, topInset, contentBottomInset)
                }
            }
            val hasFrostedHeader = !detailOpen && !historyOpen && (
                routeKey == "home:" + HomeSubpage.NONE.name ||
                    routeKey == "discover:" + HomeSubpage.NONE.name ||
                    routeKey.startsWith("dest:" + MainDestination.SEARCH.name + ":") ||
                    routeKey == "dest:" + MainDestination.LIBRARY.name + ":" + LibraryTab.CATALOG.name
                )
            if (topInset > 0.dp && !hasFrostedHeader) {
                val bgColor = MaterialTheme.colorScheme.background
                Box(
                    Modifier.align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(topInset)
                        .background(
                            Brush.verticalGradient(
                                0f to bgColor.copy(alpha = 0.88f),
                                1f to bgColor.copy(alpha = 0f),
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
                    .padding(horizontal = FlareTokens.FloatingDockHorizontalPadding, vertical = 10.dp),
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
                            modifier = Modifier.width(wideMiniWidth),
                        )
                    }
                }
            }
        } else {
            // Compact / floating-window dock: Spotify-style solid, non-floating, flush-to-bottom compact stack
            // with guaranteed bottom clearance so freeform gesture pills never overlap navigation labels.
            val darkDock = isSystemInDarkTheme()
            val compactDockBg = if (darkDock) Color(0xFF121212) else Color(0xFFFBFBFC)
            Column(
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .testTag("dock_compact")
                    .background(compactDockBg)
                    .padding(bottom = compactBottomClearance),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.fillMaxWidth()
                        .height(0.6.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                )
                AnimatedVisibility(
                    visible = state.playback.current != null,
                    enter = fadeIn(tween(220)) + expandVertically(tween(240), expandFrom = Alignment.Bottom),
                    exit = fadeOut(tween(180)) + shrinkVertically(tween(200), shrinkTowards = Alignment.Bottom),
                ) {
                    Box(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 2.dp)) {
                        ShellMiniPlayer(
                            state.playback, state.lyricState, state.translationEnabled, actions, imageLoader, openPlayer,
                            dockHeight = FlareTokens.CompactMiniPlayerHeight + compactMiniExtraHeight,
                            wideStyle = false,
                            miniPlayerActive = miniPlayerActive,
                        )
                    }
                }
                MainNavigation(state.destination, navigateMain, dockHeight = dockHeight, wideStyle = false)
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
    val hostView = LocalView.current
    val bgColor = MaterialTheme.colorScheme.background
    val useBackdropBlur = Build.VERSION.SDK_INT >= 31 && hostView.isHardwareAccelerated
    val contentLayer = if (useBackdropBlur) rememberGraphicsLayer() else null
    val fallbackHeaderHeight = topInset + 60.dp
    var measuredHeaderHeightPx by remember { mutableIntStateOf(0) }
    val topClearance = if (measuredHeaderHeightPx > 0) {
        with(density) { measuredHeaderHeightPx.toDp() }
    } else {
        fallbackHeaderHeight
    }
    val featherHeight = 18.dp
    val blurRadiusPx = with(density) { 20.dp.toPx() }

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().then(
                if (contentLayer != null) {
                    Modifier.drawWithContent {
                        contentLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(contentLayer)
                    }
                } else {
                    Modifier
                },
            ),
        ) {
            bodyContent(topClearance)
        }

        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            Box(
                Modifier.fillMaxWidth()
                    .height(topClearance + featherHeight)
                    .then(
                        if (contentLayer != null) {
                            Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        } else {
                            Modifier
                        },
                    )
                    .drawWithContent {
                        drawContent()
                        if (contentLayer != null) {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to bgColor.copy(alpha = 0.64f),
                                    0.66f to bgColor.copy(alpha = 0.46f),
                                    0.88f to bgColor.copy(alpha = 0.16f),
                                    1f to Color.Transparent,
                                ),
                            )
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to Color.Black,
                                    0.72f to Color.Black,
                                    1f to Color.Transparent,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        } else {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to bgColor.copy(alpha = 0.88f),
                                    0.68f to bgColor.copy(alpha = 0.72f),
                                    0.88f to bgColor.copy(alpha = 0.30f),
                                    1f to Color.Transparent,
                                ),
                            )
                        }
                    },
            ) {
                if (contentLayer != null) {
                    Box(
                        Modifier.matchParentSize()
                            .clipToBounds()
                            .graphicsLayer {
                                renderEffect = BlurEffect(blurRadiusPx, blurRadiusPx, TileMode.Clamp)
                            }
                            .drawWithContent {
                                drawRect(bgColor)
                                drawLayer(contentLayer)
                            },
                    )
                }
            }
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
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(R.string.ft_account)
    val account = state.accountName.ifBlank { state.username.ifBlank { stringResource(R.string.app_name) } }
    Box {
        Box(
            Modifier.size(44.dp).clickable { expanded = true }.semantics { contentDescription = label }.testTag("account_menu"),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(34.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Text(
                    account.take(1).uppercase(Locale.ROOT),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
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
) {
    val listeningInteractions = LocalListeningInteractions.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var activeAnchor by rememberSaveable { mutableStateOf(HomeFeedFilter.ALL) }
    var programmaticScroll by remember { mutableStateOf(false) }

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
    val libraryIndex = if (state.songs.isNotEmpty() || state.loading) nextItemIndex else null

    val currentAnchor = when {
        activeAnchor == HomeFeedFilter.CONTINUE && continueIndex == null -> HomeFeedFilter.ALL
        activeAnchor == HomeFeedFilter.DISCOVERY && discoveryIndex == null -> HomeFeedFilter.ALL
        activeAnchor == HomeFeedFilter.LIBRARY && libraryIndex == null -> HomeFeedFilter.ALL
        else -> activeAnchor
    }

    LaunchedEffect(listState, continueIndex, discoveryIndex, libraryIndex) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index
            val atBottom = layout.visibleItemsInfo.isNotEmpty() && !listState.canScrollForward
            Triple(
                listState.isScrollInProgress,
                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset,
                atBottom to lastVisible,
            )
        }.collect { (scrolling, firstPos, bottomPos) ->
            if (scrolling && !programmaticScroll) {
                val (firstIdx, firstOffset) = firstPos
                val (atBottom, lastVisible) = bottomPos
                activeAnchor = when {
                    firstIdx == 0 && firstOffset < 120 -> HomeFeedFilter.ALL
                    libraryIndex != null && (firstIdx >= libraryIndex || (atBottom && (lastVisible ?: -1) >= libraryIndex)) -> HomeFeedFilter.LIBRARY
                    discoveryIndex != null && (firstIdx >= discoveryIndex || (atBottom && (lastVisible ?: -1) >= discoveryIndex)) -> HomeFeedFilter.DISCOVERY
                    continueIndex != null && firstIdx >= continueIndex -> HomeFeedFilter.CONTINUE
                    else -> HomeFeedFilter.ALL
                }
            }
        }
    }

    fun jumpToSection(anchor: HomeFeedFilter, index: Int) {
        activeAnchor = anchor
        scope.launch {
            programmaticScroll = true
            try {
                listState.animateScrollToItem(index)
            } finally {
                programmaticScroll = false
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().testTag("home_page")) {
        val cardWidth = ((maxWidth - 48.dp) / 2.15f).coerceIn(148.dp, 208.dp)
        val quickCardWidth = when {
            maxWidth >= 840.dp -> ((maxWidth - 48.dp) * 0.34f).coerceIn(328.dp, 376.dp)
            maxWidth >= 600.dp -> ((maxWidth - 44.dp) * 0.50f).coerceIn(300.dp, 360.dp)
            else -> ((maxWidth - 32.dp) * 0.80f).coerceIn(264.dp, 344.dp)
        }
        val quickCardHeight = when {
            maxWidth >= 840.dp -> (quickCardWidth * 0.58f).coerceIn(190.dp, 214.dp)
            maxWidth >= 600.dp -> (quickCardWidth * 0.58f).coerceIn(174.dp, 208.dp)
            else -> (quickCardWidth * 0.58f).coerceIn(160.dp, 200.dp)
        }
        val favoritePlaylist = state.playlists.firstOrNull { it.kind == "favorite" }
        val toFavorites = {
            actions.navigate(MainDestination.LIBRARY)
            if (favoritePlaylist != null) {
                actions.openPlaylist(favoritePlaylist)
            } else {
                actions.selectTab(LibraryTab.FAVORITES)
            }
        }

        FrostedHeaderScaffold(
            topInset = topInset,
            modifier = Modifier.fillMaxSize(),
            headerContent = {
                AccountAvatar(state, actions)
                LazyRow(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item(key = "home_filter_all") {
                        CompactCategoryPill(
                            label = stringResource(R.string.ft_all),
                            selected = currentAnchor == HomeFeedFilter.ALL,
                            primaryHighlight = true,
                            modifier = Modifier.testTag("home_filter_all"),
                            onClick = { jumpToSection(HomeFeedFilter.ALL, cardsIndex) },
                        )
                    }
                    if (continueIndex != null) {
                        item(key = "home_filter_continue") {
                            CompactCategoryPill(
                                label = stringResource(R.string.ft_continue_listening),
                                selected = currentAnchor == HomeFeedFilter.CONTINUE,
                                primaryHighlight = true,
                                modifier = Modifier.testTag("home_filter_continue"),
                                onClick = { jumpToSection(HomeFeedFilter.CONTINUE, continueIndex) },
                            )
                        }
                    }
                    if (discoveryIndex != null) {
                        item(key = "home_filter_discovery") {
                            CompactCategoryPill(
                                label = stringResource(R.string.ft_listening_random),
                                selected = currentAnchor == HomeFeedFilter.DISCOVERY,
                                primaryHighlight = true,
                                modifier = Modifier.testTag("home_filter_discovery"),
                                onClick = { jumpToSection(HomeFeedFilter.DISCOVERY, discoveryIndex) },
                            )
                        }
                    }
                    if (libraryIndex != null) {
                        item(key = "home_filter_library") {
                            CompactCategoryPill(
                                label = stringResource(R.string.ft_from_your_library),
                                selected = currentAnchor == HomeFeedFilter.LIBRARY,
                                primaryHighlight = true,
                                modifier = Modifier.testTag("home_filter_library"),
                                onClick = { jumpToSection(HomeFeedFilter.LIBRARY, libraryIndex) },
                            )
                        }
                    }
                }
            },
        ) { topClearance ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("home_feed"),
                contentPadding = PaddingValues(top = topClearance + 2.dp, bottom = bottomInset),
            ) {
                item(key = "featured-cards") {
                val favoriteCount = state.favoriteSongs.size.takeIf { it > 0 } ?: favoritePlaylist?.songCount ?: 0
                val favoriteSubtitle = if (favoriteCount > 0) {
                    pluralStringResource(R.plurals.ft_track_count, favoriteCount, favoriteCount)
                } else {
                    stringResource(R.string.ft_favorites)
                }
                val favoriteCoverUrl = state.latestFavoriteCoverUrl
                    ?: favoritePlaylist?.customCoverUrl?.takeIf { it.isNotBlank() }
                    ?: favoritePlaylist?.previewCovers?.firstOrNull { it.isNotBlank() }
                    ?: state.favoriteSongs.firstOrNull { !it.coverUrl.isNullOrBlank() }?.coverUrl
                val historyCoverSong = state.homeHistoryPreviewSong ?: state.playHistory.firstOrNull()
                val queueCoverSong = state.playback.current ?: state.playback.queue.firstOrNull()
                val topPlayed = state.playStats?.songs?.firstOrNull()
                val totalPlays = state.playStats?.totalPlays ?: 0L
                val statsSubtitle = topPlayed?.song?.title ?: if (totalPlays > 0L) {
                    stringResource(R.string.ft_listening_play_count, totalPlays.toInt())
                } else {
                    stringResource(R.string.ft_listening_empty_stats)
                }
                LazyRow(
                    Modifier.fillMaxWidth().testTag("home_featured_cards"),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "card-favorites") {
                        FeaturedHeroCard(
                            title = favoritePlaylist?.name ?: stringResource(R.string.ft_liked_songs),
                            subtitle = favoriteSubtitle,
                            icon = ClientIcons.Heart,
                            accentTint = Color(0xFFE11D48),
                            coverUrl = favoriteCoverUrl,
                            loader = loader,
                            modifier = Modifier.testTag("home_favorites"),
                            width = quickCardWidth,
                            height = quickCardHeight,
                            onClick = toFavorites,
                            quickActionIcon = ClientIcons.Play,
                            quickActionDescription = stringResource(R.string.ft_play_favorites),
                            quickActionTag = "home_favorites_play",
                            onQuickAction = actions::playFavorites,
                        )
                    }
                    item(key = "card-history") {
                        FeaturedHeroCard(
                            title = stringResource(R.string.ft_play_history),
                            subtitle = historyCoverSong?.title ?: stringResource(R.string.ft_continue_listening),
                            icon = ClientIcons.History,
                            accentTint = Color(0xFF38BDF8),
                            coverUrl = historyCoverSong?.coverUrl,
                            loader = loader,
                            modifier = Modifier.testTag("home_history"),
                            width = quickCardWidth,
                            height = quickCardHeight,
                            onClick = openHistory,
                        )
                    }
                    item(key = "card-queue") {
                        val queueSubtitle = state.playback.current?.title
                            ?: if (state.playback.queue.isNotEmpty()) {
                                pluralStringResource(R.plurals.ft_track_count, state.playback.queue.size, state.playback.queue.size)
                            } else {
                                stringResource(R.string.ft_queue_empty)
                            }
                        FeaturedHeroCard(
                            title = stringResource(R.string.ft_queue),
                            subtitle = queueSubtitle,
                            icon = ClientIcons.Queue,
                            accentTint = Color(0xFFA78BFA),
                            coverUrl = queueCoverSong?.coverUrl,
                            loader = loader,
                            modifier = Modifier.testTag("home_queue_card"),
                            width = quickCardWidth,
                            height = quickCardHeight,
                            onClick = openQueue,
                        )
                    }
                    item(key = "card-stats") {
                        FeaturedHeroCard(
                            title = stringResource(R.string.ft_listening_statistics),
                            subtitle = statsSubtitle,
                            icon = ClientIcons.Stats,
                            accentTint = Color(0xFFF59E0B),
                            coverUrl = topPlayed?.song?.coverUrl,
                            loader = loader,
                            modifier = Modifier.testTag("home_stats_card"),
                            width = quickCardWidth,
                            height = quickCardHeight,
                            onClick = { listeningInteractions?.statistics?.invoke() },
                        )
                    }
                }
            }
            state.notice?.let { notice ->
                item(key = "song-notice") { InlineNotice(notice, actions::refresh) }
            }
            if (state.continueListeningSongs.isNotEmpty()) {
                item(key = "history-title") { ShelfHeading(stringResource(R.string.ft_continue_listening)) }
                item(key = "history-shelf") {
                    UnifiedSongSectionList(
                        songs = state.continueListeningSongs,
                        currentSongId = state.playback.current?.id,
                        playing = state.playback.playing,
                        buffering = state.playback.buffering,
                        loader = loader,
                        limit = 12,
                        tagPrefix = "home_continue_song_",
                    ) { _, index -> actions.playSongs(state.continueListeningSongs, index) }
                }
            }
            if (hasDiscoverySection) {
                item(key = "listening-discovery") {
                    ListeningDiscoverySection(state, actions, loader)
                }
            }
            if (state.songs.isNotEmpty() || state.loading) {
                item(key = "songs-title") { ShelfHeading(stringResource(R.string.ft_from_your_library), openAllSongs, actionTag = "home_all_songs") }
                item(key = "song-shelf") {
                    when {
                        state.loading && state.songs.isEmpty() -> ShelfLoading(cardWidth)
                        state.songs.isEmpty() -> EmptyContent(
                            message = stringResource(R.string.ft_empty_catalog),
                            retry = null,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        )
                        else -> UnifiedSongSectionList(
                            songs = state.songs,
                            currentSongId = state.playback.current?.id,
                            playing = state.playback.playing,
                            buffering = state.playback.buffering,
                            loader = loader,
                            limit = 16,
                            tagPrefix = "home_song_",
                        ) { song, _ -> actions.playSong(song) }
                    }
                }
            }
            if (!state.loading && !state.discoveryLoading && state.continueListeningSongs.isEmpty() &&
                state.discoverySongs.isEmpty() && state.songs.isEmpty()
            ) {
                item(key = "home-empty") {
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
) {
    val listeningInteractions = LocalListeningInteractions.current
    BoxWithConstraints(Modifier.fillMaxSize().testTag("discover_page")) {
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
                Modifier.fillMaxSize().testTag("discover_feed"),
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
    Column(Modifier.fillMaxSize().padding(top = topInset).testTag("home_all_songs_page")) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionIcon(ClientIcons.ChevronLeft, stringResource(R.string.ft_back), onBack)
            Text(stringResource(R.string.ft_all_songs), Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
        }
        LanguageFilterRow(
            selected = state.songLanguage,
            tagPrefix = "all_songs_lang",
            onSelect = actions::selectSongLanguage,
            counts = state.languageCounts,
        )
        ListeningSongSortRow(state, actions)
        state.notice?.let { InlineNotice(it, actions::refresh, "list_retry") }
        SongFeed(state = state, actions = actions, loader = loader, bottomInset = bottomInset, modifier = Modifier.weight(1f))
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
    val bg by animateColorAsState(
        targetValue = if (selected) activeBg else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(180),
        label = "pill_bg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) activeFg else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(180),
        label = "pill_fg",
    )
    Box(
        modifier
            .heightIn(min = 48.dp)
            .semantics { semanticSelected = selected }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.heightIn(min = 32.dp)
                .clip(CircleShape)
                .background(bg)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
        }
    }
}

/**
 * Responsive featured card for the Home horizontal strip.
 */
@Composable
private fun FeaturedHeroCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentTint: Color,
    coverUrl: String?,
    loader: ImageLoader?,
    modifier: Modifier = Modifier,
    width: Dp = 264.dp,
    height: Dp = 160.dp,
    onClick: () -> Unit,
    thumbnail: (@Composable () -> Unit)? = null,
    quickActionIcon: ImageVector? = null,
    quickActionDescription: String? = null,
    quickActionTag: String? = null,
    onQuickAction: (() -> Unit)? = null,
) {
    val largeCard = width >= 272.dp
    val cornerSize = if (largeCard) 18.dp else 16.dp
    val innerPad = if (largeCard) 16.dp else 14.dp
    val badgeSize = if (largeCard) 40.dp else 36.dp
    val badgeIconSize = if (largeCard) 20.dp else 18.dp
    val thumbSize = if (largeCard) 68.dp else 60.dp
    val actionBtnSize = if (largeCard) 52.dp else 48.dp
    val shape = RoundedCornerShape(cornerSize)
    Box(
        modifier
            .width(width)
            .height(height)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        accentTint.copy(alpha = 0.28f),
                        MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ),
            )
            .border(0.8.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), shape)
            .clickable(onClick = onClick),
    ) {
        if (loader != null && !coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = coverUrl,
                imageLoader = loader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.30f,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.12f),
                    0.5f to Color.Black.copy(alpha = 0.38f),
                    1f to Color.Black.copy(alpha = 0.78f),
                ),
            ),
        )
        Row(
            Modifier.fillMaxWidth().padding(innerPad).align(Alignment.TopStart),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier.size(badgeSize)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color.White.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(badgeIconSize), tint = Color.White)
            }
            if (thumbnail != null) {
                Box(
                    Modifier.size(thumbSize)
                        .clip(RoundedCornerShape(12.dp))
                        .border(0.8.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(12.dp)),
                ) {
                    thumbnail()
                }
            } else if (loader != null && !coverUrl.isNullOrBlank()) {
                Box(
                    Modifier.size(thumbSize)
                        .clip(RoundedCornerShape(12.dp))
                        .border(0.8.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(12.dp)),
                ) {
                    AsyncImage(
                        model = coverUrl,
                        imageLoader = loader,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().align(Alignment.BottomStart).padding(start = innerPad, end = innerPad - 4.dp, bottom = innerPad - 2.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = if (largeCard) 22.sp else 20.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = if (largeCard) 15.sp else 14.sp),
                    color = Color.White.copy(alpha = 0.80f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (quickActionIcon != null && onQuickAction != null) {
                FilledIconButton(
                    onClick = onQuickAction,
                    shape = CircleShape,
                    modifier = Modifier.size(actionBtnSize)
                        .then(if (quickActionTag != null) Modifier.testTag(quickActionTag) else Modifier)
                        .semantics { if (quickActionDescription != null) contentDescription = quickActionDescription },
                ) {
                    Icon(quickActionIcon, null, Modifier.size(if (largeCard) 22.dp else 20.dp))
                }
            }
        }
    }
}

@Composable
private fun ShelfHeading(title: String, showAll: (() -> Unit)? = null, actionTag: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 21.sp, fontWeight = FontWeight.Bold),
        )
        if (showAll != null) {
            TextButton(
                onClick = showAll,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = if (actionTag != null) Modifier.testTag(actionTag) else Modifier,
            ) {
                Text(stringResource(R.string.ft_show_all), style = MaterialTheme.typography.labelLarge)
            }
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
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = { keyboard?.hide(); actions.search(query.trim()) }
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
                contentPadding = PaddingValues(bottom = bottomInset),
            ) {
                if (headerContent != null) {
                    item(key = "library-shortcuts") { headerContent() }
                }
                items(state.songs, key = { it.id }) { song ->
                    SongRow(
                        song, loader, song.id == state.playback.current?.id, state.playback.playing,
                        state.playback.buffering, Modifier.testTag("song_" + song.id),
                    ) { actions.playSong(song) }
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
        primaryActions = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { state.albumSongs.firstOrNull()?.let(actions::playSong) },
                    enabled = state.albumSongs.isNotEmpty() && !state.albumLoading,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    modifier = Modifier.testTag("album_play").semantics { contentDescription = playLabel },
                ) {
                    Icon(ClientIcons.Play, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ft_play_all))
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
                modifier = Modifier.testTag("song_" + song.id),
            ) { actions.playSong(song) }
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

private data class MiniPlayerLyricTarget(
    val songId: String,
    val line: LyricLine?,
    val primary: String,
    val secondary: String,
    val wordSynced: Boolean,
)

/**
 * Floating liquid-glass mini player aligned with the bottom navigation dock:
 * - Play/Pause button only (no next button)
 * - Horizontal swipe gesture to skip Next (swipe left) / Previous (swipe right)
 * - Dual-line lyric/metadata slot ready for synced lyrics
 */
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
) {
    val song = playback.current ?: return
    val playing = playback.playing || playback.buffering
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(if (wideStyle) 22.dp else FlareTokens.RadiusDock)
    val scope = rememberCoroutineScope()
    val dragOffset = remember(song.id) { Animatable(0f) }
    val swipeThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
    val maxOffsetPx = with(LocalDensity.current) { 44.dp.toPx() }
    val fallbackArtist = song.artist.ifBlank { stringResource(R.string.ft_unknown_artist) }
    val activeLine = if (lyricState.status == LyricStatus.READY) {
        lyricState.lines.getOrNull(lyricState.activeLyricIndex)
    } else null
    val nextLyricText = if (lyricState.status == LyricStatus.READY && lyricState.activeLyricIndex >= 0) {
        lyricState.lines.getOrNull(lyricState.activeLyricIndex + 1)?.text?.takeIf { it.isNotBlank() }
    } else null
    val line1 = when {
        playback.buffering -> stringResource(R.string.ft_buffering)
        lyricState.status == LyricStatus.INSTRUMENTAL -> stringResource(R.string.ft_instrumental_lyrics)
        activeLine != null && activeLine.text.isNotBlank() -> activeLine.text
        else -> song.title
    }
    val line2 = when {
        playback.buffering -> song.title
        lyricState.status == LyricStatus.INSTRUMENTAL -> "${song.title} · $fallbackArtist"
        activeLine != null && activeLine.text.isNotBlank() ->
            activeLine.translation?.takeIf { translationEnabled && it.isNotBlank() }
                ?: nextLyricText
                ?: "${song.title} · $fallbackArtist"
        else -> fallbackArtist
    }
    val wordSynced = !playback.buffering &&
        lyricState.status == LyricStatus.READY &&
        lyricState.syncMode == "word" &&
        activeLine != null &&
        activeLine.words.isNotEmpty()
    val lyricTarget = MiniPlayerLyricTarget(
        songId = song.id,
        line = activeLine,
        primary = line1,
        secondary = line2,
        wordSynced = wordSynced,
    )

    val compactShape = RoundedCornerShape(if (wideStyle) 22.dp else 10.dp)
    val surfaceModifier = if (wideStyle) {
        Modifier.floatingDockSurface(shape = compactShape, dark = dark, elevation = 14.dp)
    } else {
        val compactBg = if (dark) Color(0xFF232428) else Color(0xFFEEEEF1)
        Modifier.clip(compactShape).background(compactBg)
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(dockHeight)
            .then(surfaceModifier)
            .clickable(onClick = openPlayer)
            .testTag("mini_player"),
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = if (wideStyle) 12.dp else 8.dp, end = if (wideStyle) 8.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Only the inner song card slides on horizontal swipe; the outer dock container stays anchored.
            val haptic = LocalHapticFeedback.current
            val canGoPrevious = playback.currentIndex > 0 || playback.positionMs > 3000 ||
                playback.repeatMode == 2 || (playback.shuffle && playback.queue.size > 1)
            val canGoNext = playback.currentIndex < playback.queue.lastIndex ||
                playback.repeatMode == 2 || (playback.shuffle && playback.queue.size > 1)
            Box(
                Modifier.weight(1f)
                    .fillMaxHeight()
                    .clipToBounds()
                    .pointerInput(song.id, playback.queue.size, playback.currentIndex, playback.repeatMode, playback.shuffle) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val target = dragOffset.value + dragAmount * 0.75f
                                val allowed = (target < 0f && canGoNext) || (target > 0f && canGoPrevious)
                                val limit = if (allowed) maxOffsetPx else maxOffsetPx * 0.35f
                                val scale = if (allowed) 0.75f else 0.25f
                                val nextOffset = (dragOffset.value + dragAmount * scale).coerceIn(-limit, limit)
                                scope.launch { dragOffset.snapTo(nextOffset) }
                            },
                            onDragEnd = {
                                val delta = dragOffset.value
                                val threshold = swipeThresholdPx * 0.55f
                                if (abs(delta) >= threshold) {
                                    if (delta < 0f && canGoNext) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        actions.next()
                                    } else if (delta > 0f && canGoPrevious) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        actions.previous()
                                    }
                                }
                                scope.launch { dragOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                            },
                            onDragCancel = {
                                scope.launch { dragOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                            },
                        )
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    Modifier.fillMaxWidth()
                        .graphicsLayer {
                            translationX = dragOffset.value
                            alpha = (1f - (abs(dragOffset.value) / maxOffsetPx) * 0.35f).coerceIn(0.65f, 1f)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(if (wideStyle) 50.dp else 44.dp)
                            .clip(RoundedCornerShape(if (wideStyle) 12.dp else 8.dp))
                            .border(0.6.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f), RoundedCornerShape(if (wideStyle) 12.dp else 8.dp)),
                    ) {
                        Artwork(song, loader, Modifier.fillMaxSize(), radius = if (wideStyle) 12.dp else 8.dp, placeholderSize = 20.dp)
                    }
                    Spacer(Modifier.width(if (wideStyle) 14.dp else 10.dp))
                    // Two-line slot matching web PlayerBarLyricPreview (word karaoke / synced line / title+artist).
                    AnimatedContent(
                        targetState = lyricTarget,
                        modifier = Modifier.weight(1f),
                        transitionSpec = {
                            (slideInVertically(tween(240)) { it / 2 } + fadeIn(tween(200))) togetherWith
                                (slideOutVertically(tween(180)) { -it / 2 } + fadeOut(tween(160)))
                        },
                        label = "mini_player_lines",
                    ) { slot ->
                        Column(Modifier.fillMaxWidth()) {
                            if (slot.wordSynced && slot.line != null) {
                                KaraokeLyricText(
                                    line = slot.line,
                                    basePositionMs = playback.positionMs,
                                    playing = miniPlayerActive && playback.playing && !playback.buffering,
                                    fontSize = if (wideStyle) 16.sp else 14.5.sp,
                                    lineHeight = if (wideStyle) 21.sp else 19.sp,
                                    sungColor = MaterialTheme.colorScheme.onSurface,
                                    unsungColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.46f else 0.60f),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    trackHorizontalFocus = false,
                                )
                            } else {
                                Text(
                                    slot.primary,
                                    style = MaterialTheme.typography.titleMedium.let {
                                        if (wideStyle) it.copy(fontSize = 16.sp, lineHeight = 21.sp) else it.copy(fontSize = 14.5.sp, lineHeight = 19.sp)
                                    },
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                slot.secondary,
                                style = MaterialTheme.typography.bodyMedium.let {
                                    if (wideStyle) it.copy(fontSize = 13.5.sp, lineHeight = 18.sp) else it.copy(fontSize = 12.5.sp, lineHeight = 16.sp)
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            ActionIcon(
                if (playing) ClientIcons.Pause else ClientIcons.Play,
                stringResource(if (playing) R.string.ft_pause else R.string.ft_play),
                actions::togglePlay,
                modifier = Modifier.size(if (wideStyle) 52.dp else 48.dp).testTag("mini_play"),
            )
        }
        // Subtle hairline progress indicator flush inside the bottom edge.
        Box(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(2.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        ) {
            val progress = if (playback.durationMs > 0) {
                (playback.positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f)
            } else 0f
            Box(
                Modifier.fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)),
            )
        }
    }
}
