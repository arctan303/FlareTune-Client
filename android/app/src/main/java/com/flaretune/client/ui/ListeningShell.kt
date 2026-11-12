package com.flaretune.client.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import com.flaretune.client.R
import com.flaretune.client.model.Album
import com.flaretune.client.model.ClientNotice
import com.flaretune.client.model.LyricState
import com.flaretune.client.model.LyricStatus
import com.flaretune.client.model.PlaylistSummary
import com.flaretune.client.model.Song
import com.flaretune.client.ui.theme.FlareTokens
import com.flaretune.client.ui.theme.floatingDockSurface
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

private enum class HomeSubpage { NONE, ALL_SONGS, ALL_ALBUMS }

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
) {
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    var queueOpen by rememberSaveable { mutableStateOf(false) }
    var homeSubpage by rememberSaveable { mutableStateOf(HomeSubpage.NONE) }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val wideLayout = maxWidth >= 840.dp
        val wideDock = wideLayout && state.playback.current != null
        val contentBottomInset = when {
            wideLayout -> 104.dp
            state.playback.current != null -> 168.dp
            else -> 98.dp
        }
        val navigateMain: (MainDestination) -> Unit = { dest ->
            homeSubpage = HomeSubpage.NONE
            actions.navigate(dest)
        }

        // Main content spans full height and scrolls underneath both the status bar and the floating dock.
        Box(Modifier.fillMaxSize()) {
            when {
                state.selectedAlbum != null -> AlbumScreen(state, actions, imageLoader, topInset, contentBottomInset)
                state.destination == MainDestination.HOME -> when (homeSubpage) {
                    HomeSubpage.ALL_SONGS -> HomeAllSongsScreen(
                        state = state,
                        actions = actions,
                        loader = imageLoader,
                        topInset = topInset,
                        bottomInset = contentBottomInset,
                        onBack = { homeSubpage = HomeSubpage.NONE },
                    )
                    HomeSubpage.ALL_ALBUMS -> HomeAllAlbumsScreen(
                        state = state,
                        actions = actions,
                        loader = imageLoader,
                        topInset = topInset,
                        bottomInset = contentBottomInset,
                        onBack = { homeSubpage = HomeSubpage.NONE },
                    )
                    HomeSubpage.NONE -> HomeScreen(
                        state = state,
                        actions = actions,
                        loader = imageLoader,
                        openPlayer = openPlayer,
                        openHistory = { historyOpen = true },
                        openQueue = { queueOpen = true },
                        openAllSongs = { homeSubpage = HomeSubpage.ALL_SONGS },
                        openAllAlbums = { homeSubpage = HomeSubpage.ALL_ALBUMS },
                        topInset = topInset,
                        bottomInset = contentBottomInset,
                    )
                }
                else -> CatalogScreen(state, actions, imageLoader, topInset, contentBottomInset)
            }
            if (topInset > 0.dp) {
                val bgColor = MaterialTheme.colorScheme.background
                Box(
                    Modifier.align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(topInset + 14.dp)
                        .background(
                            Brush.verticalGradient(
                                0f to bgColor.copy(alpha = 0.88f),
                                0.65f to bgColor.copy(alpha = 0.42f),
                                1f to bgColor.copy(alpha = 0f),
                            ),
                        ),
                )
            }
        }

        // Floating dock overlay (centered, identical height & radius across nav and mini player).
        Box(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = FlareTokens.FloatingDockHorizontalPadding, vertical = 10.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (wideLayout) {
                Row(
                    Modifier
                        .animateContentSize(spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow))
                        .then(if (wideDock) Modifier.testTag("dock_wide") else Modifier.testTag("dock_compact")),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MainNavigation(
                        destination = state.destination,
                        navigate = navigateMain,
                        modifier = Modifier.width(FlareTokens.WideNavWidth),
                    )
                    AnimatedVisibility(
                        visible = state.playback.current != null,
                        enter = fadeIn(tween(260)) + expandHorizontally(tween(320), expandFrom = Alignment.Start) + slideInVertically(tween(300)) { it / 2 },
                        exit = fadeOut(tween(200)) + shrinkHorizontally(tween(240), shrinkTowards = Alignment.Start) + slideOutVertically(tween(220)) { it / 2 },
                    ) {
                        ShellMiniPlayer(
                            state.playback, state.lyricState, actions, imageLoader, openPlayer,
                            modifier = Modifier.width(FlareTokens.WideMiniPlayerWidth),
                        )
                    }
                }
            } else {
                Column(
                    Modifier.widthIn(max = FlareTokens.FloatingDockMaxWidth).fillMaxWidth().testTag("dock_compact"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AnimatedVisibility(
                        visible = state.playback.current != null,
                        enter = fadeIn(tween(280)) + expandVertically(tween(300), expandFrom = Alignment.Bottom) + slideInVertically(tween(300)) { it / 2 },
                        exit = fadeOut(tween(200)) + shrinkVertically(tween(220), shrinkTowards = Alignment.Bottom) + slideOutVertically(tween(220)) { it / 2 },
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            ShellMiniPlayer(state.playback, state.lyricState, actions, imageLoader, openPlayer)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    MainNavigation(state.destination, navigateMain)
                }
            }
        }

        if (historyOpen) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val scope = rememberCoroutineScope()
            val closeHistory: () -> Unit = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) historyOpen = false
                }
            }
            ModalBottomSheet(
                onDismissRequest = { historyOpen = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                PlayHistorySheet(
                    state = state,
                    loader = imageLoader,
                    onPlay = { song ->
                        actions.playHistorySong(song)
                        closeHistory()
                    },
                    onClose = closeHistory,
                )
            }
        }
        if (queueOpen) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val scope = rememberCoroutineScope()
            val closeQueue: () -> Unit = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) queueOpen = false
                }
            }
            ModalBottomSheet(
                onDismissRequest = { queueOpen = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                QueuePanel(
                    state = state.playback,
                    loader = imageLoader,
                    close = closeQueue,
                    select = { index ->
                        actions.selectQueueItem(index)
                        closeQueue()
                    },
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
) {
    val shape = RoundedCornerShape(FlareTokens.RadiusDock)
    Box(
        modifier
            .fillMaxWidth()
            .height(FlareTokens.DockHeight)
            .floatingDockSurface(shape = shape, elevation = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(
                item = MainDestination.HOME,
                selected = destination,
                selectedIcon = ClientIcons.Home,
                unselectedIcon = ClientIcons.HomeOutlined,
                label = stringResource(R.string.ft_home),
                tag = "nav_home",
                navigate = navigate,
            )
            NavItem(
                item = MainDestination.SEARCH,
                selected = destination,
                selectedIcon = ClientIcons.Search,
                unselectedIcon = ClientIcons.Search,
                label = stringResource(R.string.ft_search_page),
                tag = "nav_search",
                navigate = navigate,
            )
            NavItem(
                item = MainDestination.LIBRARY,
                selected = destination,
                selectedIcon = ClientIcons.Library,
                unselectedIcon = ClientIcons.Library,
                label = stringResource(R.string.ft_library_page),
                tag = "nav_library",
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
    navigate: (MainDestination) -> Unit,
) {
    val active = selected == item
    val color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val activeFill = if (active) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent
    Column(
        Modifier.weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(activeFill)
            .selectable(active, role = Role.Tab, onClick = { navigate(item) })
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(if (active) selectedIcon else unselectedIcon, null, Modifier.size(22.dp), tint = color)
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AccountAvatar(state: ClientUiState, actions: ClientActions) {
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(R.string.ft_account)
    val account = state.accountName.ifBlank { state.username.ifBlank { stringResource(R.string.app_name) } }
    Box {
        Box(
            Modifier.size(48.dp).clickable { expanded = true }.semantics { contentDescription = label }.testTag("account_menu"),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
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
    openPlayer: () -> Unit,
    openHistory: () -> Unit,
    openQueue: () -> Unit,
    openAllSongs: () -> Unit,
    openAllAlbums: () -> Unit,
    topInset: Dp,
    bottomInset: Dp,
) {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("home_page")) {
        val cardWidth = ((maxWidth - 48.dp) / 2.15f).coerceIn(148.dp, 208.dp)
        val favoritePlaylist = state.playlists.firstOrNull { it.kind == "favorite" }
        val toFavorites = {
            actions.navigate(MainDestination.LIBRARY)
            if (favoritePlaylist != null) {
                actions.openPlaylist(favoritePlaylist)
            } else {
                actions.selectTab(LibraryTab.FAVORITES)
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topInset, bottom = bottomInset),
        ) {
            item(key = "home-header") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AccountAvatar(state, actions)
                    Text(
                        stringResource(R.string.ft_home),
                        Modifier.weight(1f).padding(start = 6.dp),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    ActionIcon(
                        ClientIcons.Refresh,
                        stringResource(R.string.ft_refresh),
                        actions::refresh,
                        enabled = !state.loading && !state.albumsLoading,
                    )
                }
            }
            item(key = "featured-cards") {
                val favoriteCoverUrl = state.latestFavoriteCoverUrl
                    ?: state.favoriteSongs.firstOrNull()?.coverUrl
                    ?: favoritePlaylist?.previewCovers?.firstOrNull()
                    ?: favoritePlaylist?.customCoverUrl
                val favoriteCount = state.favoriteSongs.size.takeIf { it > 0 } ?: favoritePlaylist?.songCount ?: 0
                val favoriteSubtitle = if (favoriteCount > 0) {
                    pluralStringResource(R.plurals.ft_track_count, favoriteCount, favoriteCount)
                } else {
                    stringResource(R.string.ft_favorites)
                }
                val historyCoverSong = state.playHistory.firstOrNull()
                val queueCoverSong = state.playback.current ?: state.playback.queue.firstOrNull()
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
                            accentTint = MaterialTheme.colorScheme.primary,
                            coverUrl = favoriteCoverUrl,
                            loader = loader,
                            modifier = Modifier.testTag("home_favorites"),
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
                            onClick = openQueue,
                        )
                    }
                }
            }
            state.notice?.let { notice ->
                item(key = "song-notice") { InlineNotice(notice, actions::refresh) }
            }
            if (state.albums.isNotEmpty() || state.albumsLoading || state.albumLanguage != "all") {
                item(key = "albums-title") { ShelfHeading(stringResource(R.string.ft_your_albums), openAllAlbums) }
                item(key = "album-categories") {
                    LanguageFilterRow(
                        selected = state.albumLanguage,
                        tagPrefix = "album_lang",
                        onSelect = actions::selectAlbumLanguage,
                    )
                }
                item(key = "album-shelf") {
                    when {
                        state.albumsLoading && state.albums.isEmpty() -> ShelfLoading(cardWidth)
                        state.albums.isEmpty() -> EmptyContent(
                            message = stringResource(R.string.ft_empty_catalog),
                            retry = null,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        )
                        else -> LazyRow(
                            Modifier.fillMaxWidth().testTag("home_album_shelf"),
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
            state.albumsNotice?.let { notice ->
                item(key = "album-notice") { InlineNotice(notice, actions::refresh) }
            }
            if (state.songs.isNotEmpty() || (state.loading && state.songLanguage != "all") || state.songLanguage != "all") {
                item(key = "songs-title") { ShelfHeading(stringResource(R.string.ft_from_your_library), openAllSongs, actionTag = "home_all_songs") }
                item(key = "song-categories") {
                    LanguageFilterRow(
                        selected = state.songLanguage,
                        tagPrefix = "song_lang",
                        onSelect = actions::selectSongLanguage,
                    )
                }
                item(key = "song-shelf") {
                    when {
                        state.loading && state.songs.isEmpty() -> ShelfLoading(cardWidth)
                        state.songs.isEmpty() -> EmptyContent(
                            message = stringResource(R.string.ft_empty_catalog),
                            retry = null,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        )
                        else -> SongShelf(state.songs.take(12), loader, cardWidth, "home_song_") { song, _ -> actions.playSong(song) }
                    }
                }
            }
            if (state.playback.queue.isNotEmpty()) {
                item(key = "queue-title") { ShelfHeading(stringResource(R.string.ft_continue_listening), openHistory) }
                item(key = "queue-shelf") {
                    SongShelf(state.playback.queue.take(12), loader, cardWidth, "home_queue_") { _, index ->
                        if (index != state.playback.currentIndex) actions.selectQueueItem(index)
                        openPlayer()
                    }
                }
            }
            if (state.loading && state.songs.isEmpty() && state.songLanguage == "all") {
                item(key = "song-loading") { ShelfLoading(cardWidth) }
            }
            if (!state.loading && !state.albumsLoading && state.songs.isEmpty() && state.albums.isEmpty() &&
                state.songLanguage == "all" && state.albumLanguage == "all"
            ) {
                item(key = "home-empty") {
                    EmptyContent(
                        message = stringResource(if (state.notice != null || state.albumsNotice != null) R.string.ft_unavailable else R.string.ft_empty_catalog),
                        retry = if (state.notice != null || state.albumsNotice != null) actions::refresh else null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageFilterRow(
    selected: String,
    tagPrefix: String,
    onSelect: (String) -> Unit,
) {
    val categories = listOf(
        "all" to stringResource(R.string.ft_all),
        "zh" to "华语",
        "en" to "欧美",
        "ja" to "日语",
        "instrumental" to "纯音乐",
    )
    LazyRow(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories, key = { "${tagPrefix}_${it.first}" }) { (key, label) ->
            CompactCategoryPill(
                label = label,
                selected = selected == key,
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
            ActionIcon(
                ClientIcons.Refresh,
                stringResource(R.string.ft_refresh),
                actions::refresh,
                enabled = !state.loading,
            )
        }
        LanguageFilterRow(
            selected = state.songLanguage,
            tagPrefix = "all_songs_lang",
            onSelect = actions::selectSongLanguage,
        )
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
            ActionIcon(
                ClientIcons.Refresh,
                stringResource(R.string.ft_refresh),
                actions::refresh,
                enabled = !state.albumsLoading,
            )
        }
        LanguageFilterRow(
            selected = state.albumLanguage,
            tagPrefix = "all_albums_lang",
            onSelect = actions::selectAlbumLanguage,
        )
        AlbumGrid(state = state, actions = actions, loader = loader, bottomInset = bottomInset, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CompactCategoryPill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.height(34.dp)
                .clip(CircleShape)
                .background(bg)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = fg)
        }
    }
}

/**
 * Fixed-proportion rectangular featured card for the Home horizontal strip.
 * Width stays bounded (252.dp) regardless of tablet/desktop window size.
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
    onClick: () -> Unit,
    quickActionIcon: ImageVector? = null,
    quickActionDescription: String? = null,
    quickActionTag: String? = null,
    onQuickAction: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .width(248.dp)
            .height(142.dp)
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
            Modifier.fillMaxWidth().padding(14.dp).align(Alignment.TopStart),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier.size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(18.dp), tint = Color.White)
            }
            if (loader != null && !coverUrl.isNullOrBlank()) {
                Box(
                    Modifier.size(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(0.8.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(10.dp)),
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
            Modifier.fillMaxWidth().align(Alignment.BottomStart).padding(start = 14.dp, end = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (quickActionIcon != null && onQuickAction != null) {
                FilledIconButton(
                    onClick = onQuickAction,
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp)
                        .then(if (quickActionTag != null) Modifier.testTag(quickActionTag) else Modifier)
                        .semantics { if (quickActionDescription != null) contentDescription = quickActionDescription },
                ) {
                    Icon(quickActionIcon, null, Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ShelfHeading(title: String, showAll: (() -> Unit)? = null, actionTag: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 26.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
        if (showAll != null) {
            TextButton(
                onClick = showAll,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = if (actionTag != null) Modifier.testTag(actionTag) else Modifier,
            ) {
                Text(stringResource(R.string.ft_show_all), style = MaterialTheme.typography.labelMedium)
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
private fun SongShelf(songs: List<Song>, loader: ImageLoader?, width: Dp, tagPrefix: String, onSelect: (Song, Int) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(songs, key = { index, song -> index.toString() + ":" + song.id }) { index, song ->
            Column(Modifier.width(width).clip(RoundedCornerShape(10.dp)).clickable { onSelect(song, index) }.testTag(tagPrefix + song.id)) {
                Artwork(song, loader, Modifier.fillMaxWidth().aspectRatio(1f), radius = 12.dp, placeholderSize = 40.dp)
                Spacer(Modifier.height(8.dp))
                Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    song.artist.ifBlank { stringResource(R.string.ft_unknown_artist) },
                    Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
            ActionIcon(
                ClientIcons.Refresh,
                stringResource(R.string.ft_refresh),
                {
                    when {
                        inPlaylistDetail -> actions.refreshPlaylist()
                        !searchPage && !inFavoritesDetail -> actions.refreshPlaylists()
                        else -> actions.refresh()
                    }
                },
                enabled = when {
                    inPlaylistDetail -> !state.playlistLoading
                    !searchPage && !inFavoritesDetail -> !state.playlistsLoading
                    else -> !state.loading && !state.albumsLoading
                },
                modifier = Modifier.testTag("library_refresh"),
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
            val favoriteCoverUrl = state.latestFavoriteCoverUrl
                ?: state.favoriteSongs.firstOrNull()?.coverUrl
                ?: favoritePlaylist?.previewCovers?.firstOrNull()
                ?: favoritePlaylist?.customCoverUrl
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
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                                MaterialTheme.colorScheme.surfaceContainer,
                                            ),
                                        ),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (loader != null && !favoriteCoverUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = favoriteCoverUrl,
                                        imageLoader = loader,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Icon(ClientIcons.Heart, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurface)
                                }
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
            if (loader != null && !coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = coverUrl,
                    imageLoader = loader,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    if (playlist.kind == "favorite") ClientIcons.Heart else ClientIcons.Music,
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
    BoxWithConstraints(Modifier.fillMaxSize().padding(top = topInset).testTag("album_detail")) {
        val wide = maxWidth >= 700.dp
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ActionIcon(
                    ClientIcons.ChevronLeft,
                    stringResource(R.string.ft_back),
                    actions::closeAlbum,
                    modifier = Modifier.testTag("album_back"),
                )
                Text(stringResource(R.string.ft_album), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                ActionIcon(
                    ClientIcons.Refresh,
                    stringResource(R.string.ft_refresh),
                    { actions.openAlbum(album) },
                    enabled = !state.albumLoading,
                    modifier = Modifier.testTag("album_refresh"),
                )
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomInset)) {
                item(key = "album-hero") {
                    if (wide) {
                        Row(
                            Modifier.fillMaxWidth().padding(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(32.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            AlbumArtwork(album, loader, Modifier.size(240.dp))
                            AlbumDescription(album, state, actions, Modifier.weight(1f))
                        }
                    } else {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                AlbumArtwork(album, loader, Modifier.widthIn(max = 260.dp).fillMaxWidth().aspectRatio(1f))
                            }
                            Spacer(Modifier.height(24.dp))
                            AlbumDescription(album, state, actions, Modifier.fillMaxWidth())
                        }
                    }
                }
                state.albumNotice?.let { notice ->
                    item(key = "album-error") { InlineNotice(notice, { actions.openAlbum(album) }, "album_retry") }
                }
                if (state.albumLoading) {
                    item(key = "album-loading") {
                        Row(
                            Modifier.fillMaxWidth().padding(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.ft_album_loading), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                items(state.albumSongs, key = { it.id }) { song ->
                    SongRow(
                        song, loader, song.id == state.playback.current?.id, state.playback.playing,
                        state.playback.buffering, Modifier.testTag("song_" + song.id),
                    ) { actions.playSong(song) }
                }
                if (!state.albumLoading && state.albumNotice == null && state.albumSongs.isEmpty()) {
                    item(key = "album-empty") {
                        EmptyContent(stringResource(R.string.ft_album_empty), null, Modifier.fillMaxWidth().heightIn(min = 160.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumDescription(album: Album, state: ClientUiState, actions: ClientActions, modifier: Modifier) {
    Column(modifier) {
        Text(album.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            album.artist,
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                pluralStringResource(R.plurals.ft_track_count, album.trackCount, album.trackCount),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val playLabel = stringResource(R.string.ft_play_album)
            FilledIconButton(
                onClick = { state.albumSongs.firstOrNull()?.let(actions::playSong) },
                enabled = state.albumSongs.isNotEmpty() && !state.albumLoading,
                shape = CircleShape,
                modifier = Modifier.size(56.dp).testTag("album_play").semantics { contentDescription = playLabel },
            ) { Icon(ClientIcons.Play, null, Modifier.size(26.dp)) }
        }
    }
}

@Composable
private fun PlayHistorySheet(
    state: ClientUiState,
    loader: ImageLoader?,
    onPlay: (Song) -> Unit,
    onClose: () -> Unit,
) {
    val historyItems = state.playHistory
    val windowHeight = with(LocalDensity.current) {
        androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height.toDp()
    }
    val sheetHeight = (windowHeight * 0.72f).coerceAtLeast(300.dp)
    val absorbBottomOverscroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (available.y < 0f) available.copy(x = 0f) else Offset.Zero

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                if (available.y < 0f) available.copy(x = 0f) else Velocity.Zero
        }
    }
    Column(
        Modifier.fillMaxWidth()
            .then(if (historyItems.isEmpty()) Modifier.heightIn(max = sheetHeight) else Modifier.height(sheetHeight))
            .navigationBarsPadding()
            .testTag("history_panel"),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ft_play_history), style = MaterialTheme.typography.titleLarge)
                Text(
                    pluralStringResource(R.plurals.ft_track_count, historyItems.size, historyItems.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ActionIcon(ClientIcons.Close, stringResource(R.string.ft_close), onClose)
        }
        state.historyNotice?.let { notice ->
            Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { Notice(notice) }
        }
        if (historyItems.isEmpty()) {
            EmptyContent(
                message = stringResource(R.string.ft_empty_catalog),
                retry = null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
            )
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f).nestedScroll(absorbBottomOverscroll),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                itemsIndexed(historyItems, key = { index, song -> "$index:${song.id}" }) { _, song ->
                    SongRow(
                        song = song,
                        imageLoader = loader,
                        current = song.id == state.playback.current?.id,
                        playing = state.playback.playing,
                        buffering = state.playback.buffering,
                    ) { onPlay(song) }
                }
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
    actions: ClientActions,
    loader: ImageLoader?,
    openPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = playback.current ?: return
    val playing = playback.playing || playback.buffering
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(FlareTokens.RadiusDock)
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
            activeLine.translation?.takeIf { it.isNotBlank() }
                ?: nextLyricText
                ?: "${song.title} · $fallbackArtist"
        else -> fallbackArtist
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(FlareTokens.DockHeight)
            .floatingDockSurface(shape = shape, dark = dark, elevation = 14.dp)
            .clickable(onClick = openPlayer)
            .testTag("mini_player"),
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Only the inner song card slides on horizontal swipe; the outer dock container stays anchored.
            Box(
                Modifier.weight(1f)
                    .fillMaxHeight()
                    .clipToBounds()
                    .pointerInput(song.id, playback.queue.size, playback.currentIndex) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val nextOffset = (dragOffset.value + dragAmount * 0.75f).coerceIn(-maxOffsetPx, maxOffsetPx)
                                scope.launch { dragOffset.snapTo(nextOffset) }
                            },
                            onDragEnd = {
                                val delta = dragOffset.value
                                if (abs(delta) >= swipeThresholdPx * 0.55f) {
                                    if (delta < 0f) actions.next() else actions.previous()
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
                        Modifier.size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(0.6.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f), RoundedCornerShape(10.dp)),
                    ) {
                        Artwork(song, loader, Modifier.fillMaxSize(), radius = 10.dp, placeholderSize = 22.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    // Two-line slot matching web PlayerBarLyricPreview (synced lyrics / instrumental / title+artist).
                    AnimatedContent(
                        targetState = Triple(song.id, line1, line2),
                        modifier = Modifier.weight(1f),
                        transitionSpec = {
                            (slideInVertically(tween(240)) { it / 2 } + fadeIn(tween(200))) togetherWith
                                (slideOutVertically(tween(180)) { -it / 2 } + fadeOut(tween(160)))
                        },
                        label = "mini_player_lines",
                    ) { (_, primaryLine, secondaryLine) ->
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                primaryLine,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                secondaryLine,
                                style = MaterialTheme.typography.bodyMedium,
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
                modifier = Modifier.testTag("mini_play"),
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
