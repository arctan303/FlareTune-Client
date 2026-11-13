package com.flaretune.client.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import com.flaretune.client.R
import com.flaretune.client.data.SearchEntity
import com.flaretune.client.model.*
import java.text.DateFormat
import java.util.Date

@Composable
internal fun ListeningPageTitle(title: String, back: (() -> Unit)? = null, refresh: (() -> Unit)? = null, refreshTag: String = "listening_refresh") {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) ActionIcon(ClientIcons.ChevronLeft, stringResource(R.string.ft_back), back)
        Text(title, Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (refresh != null) ActionIcon(ClientIcons.Refresh, stringResource(R.string.ft_refresh), refresh, modifier = Modifier.testTag(refreshTag))
    }
}

@Composable
private fun ListeningRetry(notice: ClientNotice?, retry: () -> Unit, tag: String = "listening_retry") {
    if (notice != null) Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Notice(notice)
        TextButton(onClick = retry, modifier = Modifier.testTag(tag)) { Text(stringResource(R.string.ft_retry)) }
    }
}

@Composable
private fun ListeningSectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionTag: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailing != null) {
            trailing()
        }
        if (onAction != null && actionLabel != null) {
            TextButton(
                onClick = onAction,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = if (actionTag != null) Modifier.testTag(actionTag) else Modifier,
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                Icon(ClientIcons.ChevronRight, null, Modifier.size(16.dp))
            }
        }
    }
}

@Composable
internal fun ListeningAlbumTile(album: Album, loader: ImageLoader?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 80.dp).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp).testTag("browse_album_" + album.id),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(Song("album:" + album.id, album.title, album.artist, audioUrl = "", coverUrl = album.coverUrl), loader, Modifier.size(60.dp))
        Column(Modifier.weight(1f)) {
            Text(album.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(album.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.ft_listening_tracks_count, album.trackCount), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(ClientIcons.ChevronRight, null)
    }
}

@Composable
private fun ListeningAlbumCard(album: Album, loader: ImageLoader?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).testTag("browse_album_" + album.id),
    ) {
        Artwork(
            Song("album:" + album.id, album.title, album.artist, audioUrl = "", coverUrl = album.coverUrl),
            loader,
            Modifier.fillMaxWidth().aspectRatio(1f),
            radius = 12.dp,
            placeholderSize = 36.dp,
        )
        Spacer(Modifier.height(8.dp))
        Text(album.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            album.artist.ifBlank { stringResource(R.string.ft_listening_tracks_count, album.trackCount) },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ListeningArtistTile(artist: Artist, loader: ImageLoader?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 80.dp).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp).testTag("browse_artist_" + artist.name),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(
            Song("artist:" + artist.name, artist.name, audioUrl = "", coverUrl = artist.photoUrl ?: artist.coverUrl),
            loader,
            Modifier.size(60.dp).clip(CircleShape),
            radius = 30.dp,
        )
        Column(Modifier.weight(1f)) {
            Text(artist.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.ft_listening_artist_counts, artist.songCount, artist.albumCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(ClientIcons.ChevronRight, null)
    }
}

@Composable
private fun ListeningArtistCard(artist: Artist, loader: ImageLoader?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp).testTag("browse_artist_" + artist.name),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val imageUrl = artist.photoUrl ?: artist.coverUrl
        Box(
            Modifier.size(112.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ClientIcons.Artist, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (loader != null && !imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    imageLoader = loader,
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            artist.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.ft_listening_artist_counts, artist.songCount, artist.albumCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun ArtistBrowseSheet(state: ClientUiState, actions: ClientActions, loader: ImageLoader?, close: () -> Unit) {
    FlareModalSheet(onDismissRequest = close) {
        val absorbBottom = rememberAbsorbBottomOverscroll()
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).navigationBarsPadding().nestedScroll(absorbBottom).testTag("artists_sheet")) {
            FlareSheetHeader(
                title = stringResource(R.string.ft_listening_artists),
                onClose = close,
                trailingActions = {
                    ActionIcon(ClientIcons.Refresh, stringResource(R.string.ft_refresh), { actions.refreshArtists(state.artistQuery) })
                },
            )
            OutlinedTextField(
                value = state.artistQuery,
                onValueChange = actions::refreshArtists,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                label = { Text(stringResource(R.string.ft_listening_search_artists)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("artist_search"),
            )
            if (state.artistsLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            ListeningRetry(state.artistsNotice, { actions.refreshArtists(state.artistQuery) })
            LazyColumn(Modifier.weight(1f)) {
                if (!state.artistsLoading && state.artists.isEmpty()) item { Text(stringResource(R.string.ft_listening_empty_artists), Modifier.padding(24.dp)) }
                items(state.artists, key = { it.name }) { artist -> ListeningArtistTile(artist, loader) { actions.openArtist(artist); close() } }
                if (state.artistsHasMore) item { TextButton(onClick = actions::loadMoreArtists, enabled = !state.artistsLoadingMore, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ft_load_more)) } }
            }
        }
    }
}

@Composable
internal fun ListeningAlbumSearchSheet(state: ClientUiState, actions: ClientActions, loader: ImageLoader?, close: () -> Unit) {
    FlareModalSheet(onDismissRequest = close) {
        val absorbBottom = rememberAbsorbBottomOverscroll()
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).navigationBarsPadding().nestedScroll(absorbBottom).testTag("album_search_sheet")) {
            FlareSheetHeader(
                title = stringResource(R.string.ft_albums),
                onClose = close,
                trailingActions = {
                    ActionIcon(ClientIcons.Refresh, stringResource(R.string.ft_refresh), { actions.searchAlbums(state.albumSearchQuery) })
                },
            )
            OutlinedTextField(
                value = state.albumSearchQuery,
                onValueChange = actions::searchAlbums,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                label = { Text(stringResource(R.string.ft_listening_search_albums)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("album_search_query"),
            )
            if (state.albumSearchLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            ListeningRetry(state.albumSearchNotice, { actions.searchAlbums(state.albumSearchQuery) })
            LazyColumn(Modifier.weight(1f)) {
                if (!state.albumSearchLoading && state.searchAlbums.isEmpty()) item { Text(stringResource(R.string.ft_listening_empty_albums), Modifier.padding(24.dp)) }
                items(state.searchAlbums, key = { it.id }) { album -> ListeningAlbumTile(album, loader) { actions.openAlbum(album); close() } }
                if (state.albumSearchHasMore) item { TextButton(onClick = actions::loadMoreSearchAlbums, enabled = !state.albumSearchLoadingMore, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ft_load_more)) } }
            }
        }
    }
}

@Composable
internal fun ListeningArtistPage(state: ClientUiState, actions: ClientActions, loader: ImageLoader?, topInset: Dp, bottomInset: Dp) {
    val artist = state.selectedArtist ?: return
    var subview by rememberSaveable(artist.name) { mutableStateOf("overview") }
    val interaction = LocalListeningInteractions.current
    val listState = rememberLazyListState()
    val scrolledPastHero by remember(subview) {
        derivedStateOf { subview != "overview" || listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 260 }
    }
    val topBarAlpha by animateFloatAsState(
        targetValue = if (scrolledPastHero) 0.94f else 0f,
        animationSpec = tween(180),
        label = "artist_bar_alpha",
    )
    val bgColor = MaterialTheme.colorScheme.background
    val heroImageUrl = artist.photoUrl
        ?: artist.coverUrl
        ?: state.artistSongs.firstOrNull { !it.coverUrl.isNullOrBlank() }?.coverUrl
        ?: state.artistAlbums.firstOrNull { !it.coverUrl.isNullOrBlank() }?.coverUrl

    val totalSongCount = artist.songCount.takeIf { it > 0 } ?: state.artistSongs.size
    val totalAlbumCount = artist.albumCount.takeIf { it > 0 } ?: state.artistAlbums.size

    Box(Modifier.fillMaxSize().background(bgColor).testTag("artist_detail")) {
        Column(Modifier.fillMaxSize()) {
            when (subview) {
                "songs" -> {
                    Column(Modifier.fillMaxSize().padding(top = topInset)) {
                        ListeningPageTitle(
                            title = artist.name + " · " + stringResource(R.string.ft_songs),
                            back = { subview = "overview" },
                            refresh = { actions.openArtist(artist) },
                            refreshTag = "artist_refresh",
                        )
                        if (state.artistLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        ListeningRetry(state.artistNotice, { actions.openArtist(artist) })
                        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = bottomInset)) {
                            itemsIndexed(state.artistSongs, key = { _, song -> song.id }) { index, song ->
                                SongRow(
                                    song, loader, song.id == state.playback.current?.id, state.playback.playing, state.playback.buffering,
                                    modifier = Modifier.testTag("artist_song_" + song.id),
                                ) { actions.playSongs(state.artistSongs, index) }
                            }
                            if (state.artistHasMore) item {
                                TextButton(onClick = actions::loadMoreArtistSongs, enabled = !state.artistLoadingMore, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.ft_load_more))
                                }
                            }
                        }
                    }
                }
                "albums" -> {
                    Column(Modifier.fillMaxSize().padding(top = topInset)) {
                        ListeningPageTitle(
                            title = artist.name + " · " + stringResource(R.string.ft_albums),
                            back = { subview = "overview" },
                            refresh = { actions.openArtist(artist) },
                            refreshTag = "artist_refresh",
                        )
                        if (state.artistAlbumsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        ListeningRetry(state.artistAlbumsNotice, { actions.openArtist(artist) })
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(152.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomInset),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            items(state.artistAlbums, key = { it.id }) { album ->
                                ListeningAlbumCard(album, loader, Modifier.fillMaxWidth()) { actions.openAlbum(album) }
                            }
                            if (state.artistAlbumsHasMore) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    TextButton(onClick = actions::loadMoreArtistAlbums, enabled = !state.artistAlbumsLoadingMore, modifier = Modifier.fillMaxWidth()) {
                                        Text(stringResource(R.string.ft_load_more))
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = bottomInset),
                    ) {
                        item(key = "artist-hero") {
                            Box(Modifier.fillMaxWidth().height(316.dp + topInset)) {
                                if (loader != null && !heroImageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = heroImageUrl,
                                        imageLoader = loader,
                                        contentDescription = artist.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Box(
                                        Modifier.fillMaxSize().background(
                                            Brush.verticalGradient(
                                                listOf(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.background),
                                            ),
                                        ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(ClientIcons.Artist, null, Modifier.size(96.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
                                    }
                                }
                                Box(
                                    Modifier.fillMaxSize().background(
                                        Brush.verticalGradient(
                                            0f to Color.Black.copy(alpha = 0.25f),
                                            0.45f to Color.Black.copy(alpha = 0.35f),
                                            0.82f to bgColor.copy(alpha = 0.88f),
                                            1f to bgColor,
                                        ),
                                    ),
                                )
                                Column(
                                    Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.ft_artist_label),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White.copy(alpha = 0.82f),
                                    )
                                    Text(
                                        artist.name,
                                        style = MaterialTheme.typography.displaySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        stringResource(R.string.ft_listening_artist_counts, totalSongCount, totalAlbumCount),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.78f),
                                    )
                                    Spacer(Modifier.height(14.dp))
                                    Row(
                                        Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Button(
                                            onClick = { actions.playSongs(state.artistSongs) },
                                            enabled = state.artistSongs.isNotEmpty(),
                                            shape = CircleShape,
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                                            modifier = Modifier.testTag("artist_play"),
                                        ) {
                                            Icon(ClientIcons.Play, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(stringResource(R.string.ft_play))
                                        }
                                        FilledTonalButton(
                                            onClick = { actions.enqueue(state.artistSongs) },
                                            enabled = state.artistSongs.isNotEmpty(),
                                            shape = CircleShape,
                                            modifier = Modifier.testTag("artist_enqueue"),
                                        ) {
                                            Text(stringResource(R.string.ft_listening_enqueue))
                                        }
                                        if (interaction != null && state.artistSongs.isNotEmpty()) {
                                            FilledTonalIconButton(
                                                onClick = { interaction.addToPlaylists(state.artistSongs) },
                                                modifier = Modifier.size(40.dp),
                                            ) {
                                                Icon(ClientIcons.Plus, stringResource(R.string.ft_listening_add_to_playlist), Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (state.artistLoading || state.artistAlbumsLoading) {
                            item(key = "artist-loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        }
                        if (state.artistNotice != null || state.artistAlbumsNotice != null) {
                            item(key = "artist-notice") {
                                ListeningRetry(state.artistNotice ?: state.artistAlbumsNotice, { actions.openArtist(artist) })
                            }
                        }

                        item(key = "artist-songs-section") {
                            ListeningSectionHeader(
                                title = stringResource(R.string.ft_songs),
                                actionLabel = if (state.artistSongs.size > 4 || state.artistHasMore) stringResource(R.string.ft_see_all) else null,
                                onAction = if (state.artistSongs.isNotEmpty()) {{ subview = "songs" }} else null,
                            )
                            if (!state.artistLoading && state.artistSongs.isEmpty()) {
                                Text(stringResource(R.string.ft_empty_search), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                SongColumnShelf(
                                    songs = state.artistSongs.take(20),
                                    currentSongId = state.playback.current?.id,
                                    playing = state.playback.playing,
                                    buffering = state.playback.buffering,
                                    loader = loader,
                                    rowsPerColumn = if (state.artistSongs.size >= 8) 4 else 3,
                                    tagPrefix = "artist_song_",
                                ) { _, index -> actions.playSongs(state.artistSongs, index) }
                            }
                        }

                        item(key = "artist-albums-section") {
                            ListeningSectionHeader(
                                title = stringResource(R.string.ft_albums),
                                actionLabel = if (state.artistAlbums.size > 3 || state.artistAlbumsHasMore) stringResource(R.string.ft_see_all) else null,
                                onAction = if (state.artistAlbums.isNotEmpty()) {{ subview = "albums" }} else null,
                            )
                            if (!state.artistAlbumsLoading && state.artistAlbums.isEmpty()) {
                                Text(stringResource(R.string.ft_listening_empty_albums), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                LazyRow(
                                    Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    items(state.artistAlbums.take(12), key = { it.id }) { album ->
                                        ListeningAlbumCard(album, loader, Modifier.width(152.dp)) { actions.openAlbum(album) }
                                    }
                                }
                            }
                        }
                    }

                    // Overlay floating/sticky bar over hero
                    Row(
                        Modifier.fillMaxWidth()
                            .background(bgColor.copy(alpha = topBarAlpha))
                            .padding(top = topInset + 4.dp, start = 8.dp, end = 8.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val iconSurface = if (scrolledPastHero) Color.Transparent else Color.Black.copy(alpha = 0.36f)
                        val iconTint = if (scrolledPastHero) MaterialTheme.colorScheme.onSurface else Color.White
                        Surface(
                            color = iconSurface,
                            shape = CircleShape,
                        ) {
                            IconButton(onClick = actions::closeArtist) {
                                Icon(ClientIcons.ChevronLeft, stringResource(R.string.ft_back), tint = iconTint)
                            }
                        }
                        AnimatedVisibility(
                            visible = scrolledPastHero,
                            enter = fadeIn(tween(180)),
                            exit = fadeOut(tween(150)),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                artist.name,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                        if (!scrolledPastHero) Spacer(Modifier.weight(1f))
                        Surface(
                            color = iconSurface,
                            shape = CircleShape,
                        ) {
                            IconButton(
                                onClick = { actions.openArtist(artist) },
                                modifier = Modifier.testTag("artist_refresh"),
                            ) {
                                Icon(ClientIcons.Refresh, stringResource(R.string.ft_refresh), tint = iconTint)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ListeningStatsSheet(state: ClientUiState, actions: ClientActions, loader: ImageLoader?, close: () -> Unit) {
    var artistsTab by remember { mutableStateOf(false) }
    val ranked = state.playStats?.songs.orEmpty()
    val artists = ranked.flatMap { rankedSong -> listeningArtistNames(rankedSong.song.artist).map { it to rankedSong.playCount } }
        .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }.entries.sortedByDescending { it.value }
    FlareModalSheet(onDismissRequest = close) {
        val absorbBottom = rememberAbsorbBottomOverscroll()
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).navigationBarsPadding().nestedScroll(absorbBottom).testTag("listening_stats")) {
            FlareSheetHeader(
                title = stringResource(R.string.ft_listening_statistics),
                onClose = close,
                trailingActions = {
                    ActionIcon(ClientIcons.Refresh, stringResource(R.string.ft_refresh), actions::refreshPlayStats)
                },
            )
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(!artistsTab, { artistsTab = false }, label = { Text(stringResource(R.string.ft_songs)) })
                FilterChip(artistsTab, { artistsTab = true }, label = { Text(stringResource(R.string.ft_listening_artists)) })
            }
            if (state.statsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            ListeningRetry(state.statsNotice, actions::refreshPlayStats)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (!state.statsLoading && ranked.isEmpty()) item { Text(stringResource(R.string.ft_listening_empty_stats), Modifier.padding(24.dp)) }
                if (artistsTab) items(artists, key = { it.key }) { artist ->
                    Row(Modifier.fillMaxWidth().clickable { actions.openArtist(Artist(artist.key)); close() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(artist.key, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.ft_listening_play_count, artist.value), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else itemsIndexed(ranked, key = { _, row -> row.song.id }) { index, row ->
                    Column {
                        SongRow(row.song, loader, row.song.id == state.playback.current?.id, state.playback.playing, row.song.id == state.playback.current?.id && state.playback.buffering) { actions.playSongs(ranked.map { it.song }, index) }
                        Text(stringResource(R.string.ft_listening_play_count, row.playCount), Modifier.padding(start = 76.dp, bottom = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ListeningSearchPage(state: ClientUiState, actions: ClientActions, loader: ImageLoader?, topInset: Dp, bottomInset: Dp) {
    var query by rememberSaveable(state.query) { mutableStateOf(state.query) }
    var submittedQuery by rememberSaveable(state.query) { mutableStateOf(state.query) }
    var sectionDetail by rememberSaveable(submittedQuery) { mutableStateOf("overview") }
    val keyboard = LocalSoftwareKeyboardController.current

    fun submitSearch(target: String = query) {
        val cleaned = target.trim()
        query = target
        submittedQuery = cleaned
        sectionDetail = "overview"
        keyboard?.hide()
        actions.search(cleaned)
        if (cleaned.isNotBlank()) {
            actions.refreshArtists(cleaned)
            actions.searchAlbums(cleaned)
        }
    }

    LaunchedEffect(Unit) {
        if (state.query.isNotBlank()) {
            actions.refreshArtists(state.query)
            actions.searchAlbums(state.query)
        }
    }

    val activeSearching = submittedQuery.isNotBlank()
    val songCoverLookup = remember(state.songs, state.discoverySongs, state.playHistory) {
        (state.songs + state.discoverySongs + state.playHistory)
            .filter { !it.coverUrl.isNullOrBlank() }
            .associate { it.id to it.coverUrl!! }
    }
    val artistCoverLookup = remember(state.artists, state.spotlightArtist) {
        buildMap {
            state.artists.forEach { a ->
                val url = a.photoUrl ?: a.coverUrl
                if (!url.isNullOrBlank()) put(a.name.lowercase(), url)
            }
            state.spotlightArtist?.let { s ->
                val url = s.photoUrl ?: s.coverUrl
                if (!url.isNullOrBlank()) put(s.artist.lowercase(), url)
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(top = topInset).testTag("search_page")) {
        // Slim pill search header with a single magnifying glass inside the pill.
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { next ->
                    query = next
                    if (next.isEmpty() && submittedQuery.isNotEmpty()) {
                        submittedQuery = ""
                        sectionDetail = "overview"
                        actions.search("")
                    }
                },
                singleLine = true,
                shape = CircleShape,
                placeholder = { Text(stringResource(R.string.ft_search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = {
                    IconButton(
                        onClick = { submitSearch() },
                        modifier = Modifier.testTag("search_submit"),
                    ) {
                        Icon(ClientIcons.Search, stringResource(R.string.ft_search_action), Modifier.size(20.dp))
                    }
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            query = ""
                            submittedQuery = ""
                            sectionDetail = "overview"
                            actions.search("")
                        }) {
                            Icon(ClientIcons.Close, stringResource(R.string.ft_clear_search), Modifier.size(18.dp))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                modifier = Modifier.weight(1f).testTag("search_field"),
            )
            ActionIcon(
                ClientIcons.Refresh,
                stringResource(R.string.ft_refresh),
                {
                    actions.refresh()
                    if (activeSearching) {
                        actions.refreshArtists(submittedQuery)
                        actions.searchAlbums(submittedQuery)
                    }
                },
                modifier = Modifier.testTag("library_refresh"),
            )
        }

        val anyLoading = if (activeSearching) {
            state.loading || state.artistsLoading || state.albumSearchLoading
        } else {
            state.loading && state.notice == null
        }
        if (anyLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        val visibleNotice = when (sectionDetail) {
            "artists" -> state.artistsNotice ?: state.notice
            "albums" -> state.albumSearchNotice ?: state.notice
            else -> state.notice ?: state.artistsNotice ?: state.albumSearchNotice
        }
        ListeningRetry(
            notice = visibleNotice,
            retry = {
                when (sectionDetail) {
                    "artists" -> actions.refreshArtists(submittedQuery)
                    "albums" -> actions.searchAlbums(submittedQuery)
                    else -> {
                        actions.refresh()
                        if (activeSearching) {
                            actions.refreshArtists(submittedQuery)
                            actions.searchAlbums(submittedQuery)
                        }
                    }
                }
            },
            tag = "catalog_retry",
        )

        if (!activeSearching) {
            // PRE-SEARCH: Single "Recent searches" section cleanly split by whitespace into
            // 1) Text search history pills (removable individually) and
            // 2) Direct picks (opened songs / artists / albums / playlists, no nested card backgrounds).
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().testTag("search_home_feed"),
                contentPadding = PaddingValues(top = 8.dp, bottom = bottomInset),
            ) {
                if (state.recentSearchTerms.isNotEmpty() || state.recentSearches.isNotEmpty()) {
                    item(key = "recent-searches") {
                        Column(Modifier.fillMaxWidth().testTag("recent_searches")) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.ft_listening_recent_searches),
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                TextButton(onClick = { actions.clearRecentSearches(); actions.clearRecentSearchTerms() }) {
                                    Text(stringResource(R.string.ft_listening_clear))
                                }
                            }
                            if (state.recentSearchTerms.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.ft_search_history_terms),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                                FlowRow(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    state.recentSearchTerms.forEach { term ->
                                        key("term:" + term) {
                                            RecentSearchTermChip(
                                                term = term,
                                                onSelect = { submitSearch(term) },
                                                onRemove = { actions.removeRecentSearchTerm(term) },
                                            )
                                        }
                                    }
                                }
                            }
                            if (state.recentSearchTerms.isNotEmpty() && state.recentSearches.isNotEmpty()) {
                                Spacer(Modifier.height(22.dp))
                            }
                            if (state.recentSearches.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.ft_search_history_direct),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                                LazyRow(
                                    Modifier.fillMaxWidth().padding(top = 6.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    items(state.recentSearches, key = { it.type + ":" + it.id }) { entry ->
                                        RecentSearchEntityCard(
                                            entry = entry,
                                            coverUrl = if (entry.type == "artist") {
                                                artistCoverLookup[entry.name.lowercase()]
                                            } else {
                                                songCoverLookup[entry.id]
                                            },
                                            loader = loader,
                                            onOpen = { actions.openRecentSearch(entry) },
                                            onRemove = { actions.removeRecentSearch(entry) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    item(key = "search-empty-recent") {
                        Box(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 56.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.ft_search_intro),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        } else {
            // POST-SEARCH: Web-aligned horizontal multi-section overview + View All subviews.
            when (sectionDetail) {
                "artists" -> {
                    Column(Modifier.weight(1f).fillMaxWidth()) {
                        SearchSubviewBackBar(
                            label = stringResource(R.string.ft_listening_artists),
                            onBack = { sectionDetail = "overview" },
                        )
                        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("catalog_list"), contentPadding = PaddingValues(bottom = bottomInset)) {
                            if (!state.artistsLoading && state.artists.isEmpty()) {
                                item { Text(stringResource(R.string.ft_listening_empty_artists), Modifier.padding(24.dp)) }
                            }
                            items(state.artists, key = { it.name }) { artist ->
                                ListeningArtistTile(artist, loader) { actions.openArtist(artist) }
                            }
                            if (state.artistsHasMore) item {
                                TextButton(onClick = actions::loadMoreArtists, enabled = !state.artistsLoadingMore, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.ft_load_more))
                                }
                            }
                        }
                    }
                }
                "songs" -> {
                    Column(Modifier.weight(1f).fillMaxWidth()) {
                        SearchSubviewBackBar(
                            label = stringResource(R.string.ft_songs),
                            onBack = { sectionDetail = "overview" },
                        )
                        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("catalog_list"), contentPadding = PaddingValues(bottom = bottomInset)) {
                            if (!state.loading && state.songs.isEmpty()) {
                                item { Text(stringResource(R.string.ft_empty_search), Modifier.padding(24.dp)) }
                            }
                            itemsIndexed(state.songs, key = { _, song -> song.id }) { index, song ->
                                SongRow(
                                    song, loader, song.id == state.playback.current?.id, state.playback.playing, state.playback.buffering,
                                    modifier = Modifier.testTag("song_" + song.id),
                                ) { actions.playSongs(state.songs, index) }
                            }
                            if (state.hasMore) item {
                                TextButton(onClick = actions::loadMore, enabled = !state.loadingMore, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.ft_load_more))
                                }
                            }
                        }
                    }
                }
                "albums" -> {
                    Column(Modifier.weight(1f).fillMaxWidth()) {
                        SearchSubviewBackBar(
                            label = stringResource(R.string.ft_albums),
                            onBack = { sectionDetail = "overview" },
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(152.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth().testTag("catalog_list"),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomInset),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            if (!state.albumSearchLoading && state.searchAlbums.isEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text(stringResource(R.string.ft_listening_empty_albums), Modifier.padding(16.dp))
                                }
                            }
                            items(state.searchAlbums, key = { it.id }) { album ->
                                ListeningAlbumCard(album, loader, Modifier.fillMaxWidth()) { actions.openAlbum(album) }
                            }
                            if (state.albumSearchHasMore) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    TextButton(onClick = actions::loadMoreSearchAlbums, enabled = !state.albumSearchLoadingMore, modifier = Modifier.fillMaxWidth()) {
                                        Text(stringResource(R.string.ft_load_more))
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    val nothingFound = !anyLoading &&
                        state.artists.isEmpty() &&
                        state.songs.isEmpty() &&
                        state.searchAlbums.isEmpty() &&
                        visibleNotice == null

                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth().testTag("catalog_list"),
                        contentPadding = PaddingValues(bottom = bottomInset),
                    ) {
                        if (nothingFound) {
                            item(key = "search-empty") {
                                Text(
                                    stringResource(R.string.ft_empty_search),
                                    Modifier.padding(24.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (state.artists.isNotEmpty()) {
                            item(key = "search-artists-section") {
                                ListeningSectionHeader(
                                    title = stringResource(R.string.ft_listening_artists),
                                    actionLabel = if (state.artists.size > 4 || state.artistsHasMore) stringResource(R.string.ft_see_all) else null,
                                    onAction = if (state.artists.isNotEmpty()) {{ sectionDetail = "artists" }} else null,
                                )
                                LazyRow(
                                    Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    items(state.artists.take(12), key = { it.name }) { artist ->
                                        ListeningArtistCard(artist, loader, Modifier.width(128.dp)) { actions.openArtist(artist) }
                                    }
                                }
                            }
                        }
                        if (state.songs.isNotEmpty()) {
                            item(key = "search-songs-section") {
                                ListeningSectionHeader(
                                    title = stringResource(R.string.ft_songs),
                                    actionLabel = if (state.songs.size > 6 || state.hasMore) stringResource(R.string.ft_see_all) else null,
                                    onAction = if (state.songs.isNotEmpty()) {{ sectionDetail = "songs" }} else null,
                                    trailing = {
                                        TextButton(
                                            onClick = { actions.playSongs(state.songs, 0) },
                                            modifier = Modifier.testTag("search_play_all"),
                                        ) {
                                            Icon(ClientIcons.Play, null, Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(stringResource(R.string.ft_play_all))
                                        }
                                    },
                                )
                                SongColumnShelf(
                                    songs = state.songs.take(20),
                                    currentSongId = state.playback.current?.id,
                                    playing = state.playback.playing,
                                    buffering = state.playback.buffering,
                                    loader = loader,
                                    rowsPerColumn = if (state.songs.size >= 8) 4 else 3,
                                    tagPrefix = "song_",
                                ) { _, index -> actions.playSongs(state.songs, index) }
                            }
                        }
                        if (state.searchAlbums.isNotEmpty()) {
                            item(key = "search-albums-section") {
                                ListeningSectionHeader(
                                    title = stringResource(R.string.ft_albums),
                                    actionLabel = if (state.searchAlbums.size > 4 || state.albumSearchHasMore) stringResource(R.string.ft_see_all) else null,
                                    onAction = if (state.searchAlbums.isNotEmpty()) {{ sectionDetail = "albums" }} else null,
                                )
                                LazyRow(
                                    Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    items(state.searchAlbums.take(12), key = { it.id }) { album ->
                                        ListeningAlbumCard(album, loader, Modifier.width(152.dp)) { actions.openAlbum(album) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSubviewBackBar(label: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) {
            Icon(ClientIcons.ChevronLeft, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.ft_back))
        }
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun RecentSearchEntityCard(
    entry: SearchEntity,
    coverUrl: String?,
    loader: ImageLoader?,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val isArtist = entry.type == "artist"
    val subtitle = when (entry.type) {
        "artist" -> stringResource(R.string.ft_artist_label)
        "album" -> entry.artist.ifBlank { stringResource(R.string.ft_album_label) }
        "playlist" -> stringResource(R.string.ft_playlist_label)
        else -> entry.artist.ifBlank { stringResource(R.string.ft_songs) }
    }
    Column(
        modifier = Modifier.width(96.dp).clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onOpen,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(88.dp)) {
            Box(
                Modifier.size(80.dp)
                    .align(Alignment.Center)
                    .clip(if (isArtist) CircleShape else RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when (entry.type) {
                        "artist" -> ClientIcons.Artist
                        "album" -> ClientIcons.Disc
                        else -> ClientIcons.Music
                    },
                    null,
                    Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (loader != null && !coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        imageLoader = loader,
                        contentDescription = entry.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.align(Alignment.TopEnd).size(22.dp),
            ) {
                Box(Modifier.fillMaxSize().clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
                    Icon(ClientIcons.Close, stringResource(R.string.ft_listening_remove_search), Modifier.size(13.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            entry.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RecentSearchTermChip(term: String, onSelect: () -> Unit, onRemove: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
        tonalElevation = 1.dp,
        modifier = Modifier.border(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), CircleShape),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.clickable(onClick = onSelect)
                    .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    term,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp),
                )
            }
            Box(
                Modifier.clickable(onClick = onRemove)
                    .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    ClientIcons.Close,
                    stringResource(R.string.ft_listening_remove_search),
                    Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal fun listeningTime(playedAt: Long?): String = if (playedAt == null || playedAt <= 0) ""
else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(playedAt))

@Composable
internal fun ListeningSongSortRow(state: ClientUiState, actions: ClientActions) {
    if (state.songLanguage == "all" || state.query.isNotBlank()) return
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(state.songSort == "desc", { actions.selectSongSort("desc") }, label = { Text(stringResource(R.string.ft_listening_sort_newest)) }, modifier = Modifier.testTag("song_sort_desc"))
        FilterChip(state.songSort == "asc", { actions.selectSongSort("asc") }, label = { Text(stringResource(R.string.ft_listening_sort_oldest)) }, modifier = Modifier.testTag("song_sort_asc"))
    }
}

@Composable
internal fun ListeningLibraryOverview(state: ClientUiState, actions: ClientActions, loader: ImageLoader?, topInset: Dp, bottomInset: Dp) {
    val interaction = LocalListeningInteractions.current
    var editShelf by remember { mutableStateOf(false) }
    val positions = state.playlistShelf?.items?.mapIndexed { index, item -> item.id to index }?.toMap().orEmpty()
    val playlists = state.playlists.sortedBy { positions[it.id] ?: Int.MAX_VALUE }

    Column(Modifier.fillMaxSize().padding(top = topInset).testTag("library_page")) {
        ListeningPageTitle(
            title = stringResource(R.string.ft_library_page),
            refresh = actions::refreshPlaylists,
            refreshTag = "library_refresh",
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = { interaction?.createPlaylist?.invoke(emptyList()) },
                shape = CircleShape,
                modifier = Modifier.heightIn(min = 42.dp).testTag("create_playlist"),
            ) {
                Icon(ClientIcons.Plus, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.ft_listening_create_playlist))
            }
            OutlinedButton(
                onClick = { editShelf = true },
                enabled = state.playlistShelf != null && playlists.isNotEmpty() && !state.libraryMutationBusy,
                shape = CircleShape,
                modifier = Modifier.heightIn(min = 42.dp).testTag("edit_shelf"),
            ) {
                Text(stringResource(R.string.ft_listening_shelf_order))
            }
        }
        if (state.playlistsLoading || state.libraryMutationBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        ListeningRetry(state.playlistsNotice, actions::refreshPlaylists)
        state.libraryMutationNotice?.let { Notice(it, Modifier.padding(horizontal = 16.dp)) }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(154.dp),
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("library_playlists"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = bottomInset),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (playlists.none { it.kind == "favorite" }) {
                item(key = "favorite-entry") {
                    PlaylistGridCard(
                        name = stringResource(R.string.ft_liked_songs),
                        subtitle = pluralStringResourceText(state.favoriteSongs.size),
                        customCoverUrl = state.latestFavoriteCoverUrl,
                        previewCovers = state.favoriteSongs.mapNotNull { it.coverUrl }.distinct().take(4),
                        isFavorite = true,
                        loader = loader,
                        cardTag = "library_tab_favorites",
                        moreTag = null,
                        onOpen = { actions.selectTab(LibraryTab.FAVORITES) },
                        onMore = null,
                    )
                }
            }
            items(playlists, key = { it.id }) { playlist ->
                PlaylistGridCard(
                    name = playlist.name,
                    subtitle = stringResource(R.string.ft_listening_tracks_count, playlist.songCount),
                    customCoverUrl = playlist.customCoverUrl,
                    previewCovers = playlist.previewCovers,
                    isFavorite = playlist.kind == "favorite",
                    loader = loader,
                    cardTag = if (playlist.kind == "favorite") "library_tab_favorites" else "library_playlist_" + playlist.id,
                    moreTag = "playlist_more_" + playlist.id,
                    onOpen = { actions.openPlaylist(playlist) },
                    onMore = { interaction?.playlist?.invoke(playlist) },
                )
            }
            if (!state.playlistsLoading && playlists.none { it.kind != "favorite" }) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.ft_empty_playlists),
                        Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (editShelf) ListeningShelfOrderSheet(state, actions, playlists) { editShelf = false }
}

@Composable
private fun pluralStringResourceText(count: Int): String =
    stringResource(R.string.ft_listening_tracks_count, count)

@Composable
private fun PlaylistGridCard(
    name: String,
    subtitle: String,
    customCoverUrl: String?,
    previewCovers: List<String>,
    isFavorite: Boolean,
    loader: ImageLoader?,
    cardTag: String,
    moreTag: String?,
    onOpen: () -> Unit,
    onMore: (() -> Unit)?,
) {
    Column(
        Modifier.fillMaxWidth().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onOpen,
        ).testTag(cardTag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            PlaylistCoverArt(
                name = name,
                customCoverUrl = customCoverUrl,
                previewCovers = previewCovers,
                isFavorite = isFavorite,
                loader = loader,
                modifier = Modifier.fillMaxSize(),
                radius = 14.dp,
            )
            if (onMore != null && moreTag != null) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    IconButton(
                        onClick = onMore,
                        modifier = Modifier.size(34.dp).testTag(moreTag),
                    ) {
                        Icon(
                            ClientIcons.More,
                            stringResource(R.string.ft_more_options),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun ListeningShelfOrderSheet(
    state: ClientUiState,
    actions: ClientActions,
    playlists: List<PlaylistSummary>,
    close: () -> Unit,
) {
    val original = remember { playlists.map { it.id } }
    val baseRevision = remember { state.playlistShelf?.revision }
    var draft by remember { mutableStateOf(original) }
    var submitted by remember { mutableStateOf<Long?>(null) }
    val stale = state.playlistShelf?.revision != baseRevision || playlists.map { it.id }.toSet() != original.toSet() || state.libraryMutationConflict
    LaunchedEffect(state.libraryMutationVersion) { if (submitted != null && state.libraryMutationVersion > submitted!!) close() }

    FlareModalSheet(
        onDismissRequest = { if (!state.libraryMutationBusy) close() },
        sheetGesturesEnabled = !state.libraryMutationBusy,
    ) {
        val absorbBottom = rememberAbsorbBottomOverscroll()
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).navigationBarsPadding().nestedScroll(absorbBottom)) {
            FlareSheetHeader(
                title = stringResource(R.string.ft_listening_shelf_order),
                subtitle = stringResource(R.string.ft_listening_tracks_count, draft.size),
                onClose = close,
                closeEnabled = !state.libraryMutationBusy,
            )
            Column(Modifier.padding(horizontal = 20.dp)) {
                if (stale) Text(stringResource(R.string.ft_listening_order_conflict), color = MaterialTheme.colorScheme.error)
                state.libraryMutationNotice?.let { Notice(it) }
            }
            LazyColumn(
                Modifier.weight(1f, fill = false).fillMaxWidth().padding(horizontal = 20.dp).testTag("shelf_order_list"),
            ) {
                itemsIndexed(draft, key = { _, id -> id }) { index, id ->
                    val name = playlists.firstOrNull { it.id == id }?.name.orEmpty()
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        TextButton(
                            enabled = index > 0 && !state.libraryMutationBusy && !stale,
                            modifier = Modifier.testTag("shelf_up_" + id),
                            onClick = { draft = draft.toMutableList().apply { add(index - 1, removeAt(index)) } },
                        ) { Text(stringResource(R.string.ft_listening_up)) }
                        TextButton(
                            enabled = index < draft.lastIndex && !state.libraryMutationBusy && !stale,
                            modifier = Modifier.testTag("shelf_down_" + id),
                            onClick = { draft = draft.toMutableList().apply { add(index + 1, removeAt(index)) } },
                        ) { Text(stringResource(R.string.ft_listening_down)) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = close, enabled = !state.libraryMutationBusy) {
                    Text(stringResource(R.string.ft_library_cancel))
                }
                Button(
                    enabled = !state.libraryMutationBusy && !stale && draft != original,
                    modifier = Modifier.testTag("shelf_order_save"),
                    onClick = {
                        submitted = state.libraryMutationVersion
                        actions.replacePlaylistShelfOrder(draft)
                    },
                ) { Text(stringResource(R.string.ft_listening_save)) }
            }
        }
    }
}

@Composable
internal fun ListeningPlaylistDetailPage(state: ClientUiState, actions: ClientActions, loader: ImageLoader?, topInset: Dp, bottomInset: Dp) {
    val playlist = state.selectedPlaylist ?: return
    val interaction = LocalListeningInteractions.current
    val isFav = playlist.kind == "favorite"
    val previewCovers = remember(playlist, state.selectedPlaylistEntries) {
        playlist.previewCovers.ifEmpty {
            state.selectedPlaylistEntries.mapNotNull { it.song.coverUrl?.takeIf(String::isNotBlank) }.distinct().take(4)
        }
    }

    CollectionDetailScaffold(
        kindLabel = stringResource(R.string.ft_playlist_label),
        title = playlist.name,
        subtitle = playlist.description.takeIf { it.isNotBlank() },
        metaLine = stringResource(R.string.ft_listening_tracks_count, playlist.songCount),
        coverUrl = playlist.customCoverUrl ?: previewCovers.firstOrNull()?.takeIf { previewCovers.size < 4 },
        previewCovers = previewCovers,
        isFavorite = isFav,
        fallbackIcon = if (isFav) ClientIcons.Heart else ClientIcons.Music,
        loader = loader,
        topInset = topInset,
        bottomInset = bottomInset,
        testTag = "library_page",
        backTag = "library_playlist_back",
        onBack = actions::closePlaylist,
        refreshTag = "library_refresh",
        refreshing = state.playlistLoading,
        onRefresh = actions::refreshPlaylist,
        headerExtraActions = {
            ActionIcon(
                ClientIcons.More,
                stringResource(R.string.ft_more_options),
                { interaction?.playlist?.invoke(playlist) },
                modifier = Modifier.testTag("playlist_more_" + playlist.id),
            )
        },
        primaryActions = {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { actions.playPlaylist(playlist) },
                    enabled = state.playlistSongs.isNotEmpty() && !state.playlistLoading,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                    modifier = Modifier.testTag("playlist_play"),
                ) {
                    Icon(ClientIcons.Play, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ft_play))
                }
                OutlinedButton(
                    onClick = { interaction?.addSongs?.invoke(playlist) },
                    enabled = !state.libraryMutationBusy,
                    shape = CircleShape,
                    modifier = Modifier.testTag("playlist_add_songs"),
                ) {
                    Text(stringResource(R.string.ft_listening_add_songs))
                }
                TextButton(
                    onClick = { interaction?.playlist?.invoke(playlist) },
                ) {
                    Text(stringResource(R.string.ft_listening_edit_order))
                }
            }
        },
        loading = state.playlistLoading || state.libraryMutationBusy,
        notice = state.playlistNotice,
        noticeTag = "playlist_retry",
        onRetry = actions::refreshPlaylist,
        secondaryNotice = state.libraryMutationNotice,
        emptyMessage = stringResource(R.string.ft_listening_empty_playlist),
        isEmpty = !state.playlistLoading && state.selectedPlaylistEntries.isEmpty(),
        listTestTag = "playlist_detail_tracks",
    ) {
        items(state.selectedPlaylistEntries, key = { it.song.id }) { entry ->
            Column(Modifier.fillMaxWidth()) {
                SongRow(
                    entry.song, loader, entry.song.id == state.playback.current?.id, state.playback.playing, state.playback.buffering,
                    modifier = Modifier.testTag("playlist_song_" + entry.song.id),
                    clickEnabled = entry.canPlay,
                ) { actions.playSong(entry.song) }
                if (!entry.canPlay) {
                    Text(
                        stringResource(R.string.ft_listening_unplayable),
                        Modifier.padding(start = 76.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ListeningDiscoverySection(state: ClientUiState, actions: ClientActions, loader: ImageLoader?) {
    val spotlight = state.spotlightArtist
    if (state.discoverySongs.isEmpty() && spotlight == null && !state.discoveryLoading && state.discoveryNotice == null) return
    Column(Modifier.fillMaxWidth().testTag("home_discovery")) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.ft_listening_daily), Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            ActionIcon(
                ClientIcons.Refresh,
                stringResource(R.string.ft_refresh),
                actions::refreshDiscovery,
                enabled = !state.discoveryLoading,
                modifier = Modifier.testTag("discovery_refresh"),
            )
        }
        if (state.discoveryLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        ListeningRetry(state.discoveryNotice, actions::refreshDiscovery)
        if (state.discoverySongs.isNotEmpty()) {
            SongColumnShelf(
                songs = state.discoverySongs,
                currentSongId = state.playback.current?.id,
                playing = state.playback.playing,
                buffering = state.playback.buffering,
                loader = loader,
                rowsPerColumn = if (state.discoverySongs.size >= 8) 4 else 3,
                tagPrefix = "discovery_song_",
            ) { _, index -> actions.playSongs(state.discoverySongs, index) }
        }
        if (spotlight != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.ft_listening_spotlight),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        actions.openArtist(Artist(spotlight.artist, spotlight.songCount, photoUrl = spotlight.photoUrl, coverUrl = spotlight.coverUrl))
                    }
                    .testTag("browse_artist_" + spotlight.artist),
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    val photo = spotlight.photoUrl ?: spotlight.coverUrl
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(ClientIcons.Artist, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (loader != null && !photo.isNullOrBlank()) {
                            AsyncImage(model = photo, imageLoader = loader, contentDescription = spotlight.artist, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(spotlight.artist, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(R.string.ft_listening_tracks_count, spotlight.songCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(ClientIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
