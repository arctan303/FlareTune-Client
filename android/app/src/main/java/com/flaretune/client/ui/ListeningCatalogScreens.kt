package com.flaretune.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import com.flaretune.client.R
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
    if (notice != null) Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Notice(notice)
        TextButton(onClick = retry, modifier = Modifier.testTag(tag)) { Text(stringResource(R.string.ft_retry)) }
    }
}

@Composable
internal fun ListeningAlbumTile(album: Album, loader: ImageLoader?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 80.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp).testTag("browse_album_" + album.id),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
private fun ListeningArtistTile(artist: Artist, loader: ImageLoader?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 80.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp).testTag("browse_artist_" + artist.name),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Artwork(Song("artist:" + artist.name, artist.name, audioUrl = "", coverUrl = artist.photoUrl ?: artist.coverUrl), loader,
            Modifier.size(60.dp).clip(CircleShape), radius = 30.dp)
        Column(Modifier.weight(1f)) {
            Text(artist.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.ft_listening_artist_counts, artist.songCount, artist.albumCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(ClientIcons.ChevronRight, null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArtistBrowseSheet(state: ClientUiState, actions: ClientActions, loader: ImageLoader?, close: () -> Unit) {
    ModalBottomSheet(onDismissRequest = close) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).testTag("artists_sheet")) {
            ListeningPageTitle(stringResource(R.string.ft_listening_artists), refresh = { actions.refreshArtists(state.artistQuery) })
            OutlinedTextField(state.artistQuery, actions::refreshArtists, singleLine = true,
                label = { Text(stringResource(R.string.ft_listening_search_artists)) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("artist_search"))
            if (state.artistsLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
            ListeningRetry(state.artistsNotice, { actions.refreshArtists(state.artistQuery) })
            LazyColumn(Modifier.weight(1f)) {
                if (!state.artistsLoading && state.artists.isEmpty()) item { Text(stringResource(R.string.ft_listening_empty_artists), Modifier.padding(24.dp)) }
                items(state.artists, key = { it.name }) { artist -> ListeningArtistTile(artist, loader) { actions.openArtist(artist); close() } }
                if (state.artistsHasMore) item { TextButton(onClick = actions::loadMoreArtists, enabled = !state.artistsLoadingMore, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ft_load_more)) } }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListeningAlbumSearchSheet(state: ClientUiState, actions: ClientActions, loader: ImageLoader?, close: () -> Unit) {
    ModalBottomSheet(onDismissRequest = close) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).testTag("album_search_sheet")) {
            ListeningPageTitle(stringResource(R.string.ft_albums), refresh = { actions.searchAlbums(state.albumSearchQuery) })
            OutlinedTextField(state.albumSearchQuery, actions::searchAlbums, singleLine = true,
                label = { Text(stringResource(R.string.ft_listening_search_albums)) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("album_search_query"))
            if (state.albumSearchLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
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
    var albumsTab by rememberSaveable(artist.name) { mutableStateOf(false) }
    val interaction = LocalListeningInteractions.current
    Column(Modifier.fillMaxSize().padding(top = topInset).testTag("artist_detail")) {
        ListeningPageTitle(artist.name, actions::closeArtist, { actions.openArtist(artist) }, "artist_refresh")
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!albumsTab, { albumsTab = false }, label = { Text(stringResource(R.string.ft_songs)) })
            FilterChip(albumsTab, { albumsTab = true }, label = { Text(stringResource(R.string.ft_albums)) })
            if (!albumsTab && state.artistSongs.isNotEmpty()) {
                TextButton(onClick = { actions.playSongs(state.artistSongs) }, modifier = Modifier.testTag("artist_play")) { Text(stringResource(R.string.ft_play)) }
                TextButton(onClick = { actions.enqueue(state.artistSongs) }, modifier = Modifier.testTag("artist_enqueue")) { Text(stringResource(R.string.ft_listening_enqueue)) }
                TextButton(onClick = { interaction?.addToPlaylists?.invoke(state.artistSongs) }) { Text(stringResource(R.string.ft_listening_add_to_playlist)) }
            }
        }
        val loading = if (albumsTab) state.artistAlbumsLoading else state.artistLoading
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        ListeningRetry(if (albumsTab) state.artistAlbumsNotice else state.artistNotice, { actions.openArtist(artist) })
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = bottomInset)) {
            if (albumsTab) {
                if (!loading && state.artistAlbums.isEmpty()) item { Text(stringResource(R.string.ft_listening_empty_albums), Modifier.padding(24.dp)) }
                items(state.artistAlbums, key = { it.id }) { album -> ListeningAlbumTile(album, loader) { actions.openAlbum(album) } }
                if (state.artistAlbumsHasMore) item { TextButton(onClick = actions::loadMoreArtistAlbums, enabled = !state.artistAlbumsLoadingMore, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ft_load_more)) } }
            } else {
                if (!loading && state.artistSongs.isEmpty()) item { Text(stringResource(R.string.ft_empty_search), Modifier.padding(24.dp)) }
                itemsIndexed(state.artistSongs, key = { _, song -> song.id }) { index, song ->
                    SongRow(song, loader, song.id == state.playback.current?.id, state.playback.playing, state.playback.buffering,
                        modifier = Modifier.testTag("artist_song_" + song.id)) { actions.playSongs(state.artistSongs, index) }
                }
                if (state.artistHasMore) item { TextButton(onClick = actions::loadMoreArtistSongs, enabled = !state.artistLoadingMore, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ft_load_more)) } }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListeningStatsSheet(state: ClientUiState, actions: ClientActions, loader: ImageLoader?, close: () -> Unit) {
    var artistsTab by remember { mutableStateOf(false) }
    val ranked = state.playStats?.songs.orEmpty()
    val artists = ranked.flatMap { rankedSong -> listeningArtistNames(rankedSong.song.artist).map { it to rankedSong.playCount } }
        .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }.entries.sortedByDescending { it.value }
    ModalBottomSheet(onDismissRequest = close) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).testTag("listening_stats")) {
            ListeningPageTitle(stringResource(R.string.ft_listening_statistics), refresh = actions::refreshPlayStats)
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        SongRow(row.song, loader, row.song.id == state.playback.current?.id, state.playback.playing, state.playback.buffering) { actions.playSongs(ranked.map { it.song }, index) }
                        Text(stringResource(R.string.ft_listening_play_count, row.playCount), Modifier.padding(start = 76.dp, bottom = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ListeningSearchPage(state: ClientUiState, actions: ClientActions, loader: ImageLoader?, topInset: Dp, bottomInset: Dp) {
    var query by rememberSaveable(state.query) { mutableStateOf(state.query) }
    var kind by rememberSaveable { mutableStateOf("songs") }
    fun search() { actions.search(query); actions.refreshArtists(query); actions.searchAlbums(query) }
    LaunchedEffect(Unit) { actions.refreshArtists(state.query); actions.searchAlbums(state.query) }
    Column(Modifier.fillMaxSize().padding(top = topInset).testTag("search_page")) {
        ListeningPageTitle(stringResource(R.string.ft_search_page), refresh = { actions.refresh(); actions.refreshArtists(query); actions.searchAlbums(query) }, refreshTag = "library_refresh")
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it }, singleLine = true, label = { Text(stringResource(R.string.ft_search_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier.weight(1f).testTag("search_field"))
            ActionIcon(ClientIcons.Search, stringResource(R.string.ft_search_page), { search() }, modifier = Modifier.testTag("search_submit"))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(kind == "songs", { kind = "songs" }, label = { Text(stringResource(R.string.ft_songs)) }, modifier = Modifier.testTag("search_type_songs"))
            FilterChip(kind == "artists", { kind = "artists" }, label = { Text(stringResource(R.string.ft_listening_artists)) }, modifier = Modifier.testTag("search_type_artists"))
            FilterChip(kind == "albums", { kind = "albums" }, label = { Text(stringResource(R.string.ft_albums)) }, modifier = Modifier.testTag("search_type_albums"))
        }
        if (query.isBlank() && (state.recentSearchTerms.isNotEmpty() || state.recentSearches.isNotEmpty())) {
            Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).testTag("recent_searches")) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ft_listening_recent_searches), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { actions.clearRecentSearches(); actions.clearRecentSearchTerms() }) { Text(stringResource(R.string.ft_listening_clear)) }
                }
                LazyColumn {
                    items(state.recentSearchTerms, key = { "term:" + it }) { term ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { query = term; search() }, modifier = Modifier.weight(1f)) { Text(term, Modifier.fillMaxWidth(), maxLines = 1) }
                            ActionIcon(ClientIcons.Close, stringResource(R.string.ft_listening_remove_search), { actions.removeRecentSearchTerm(term) })
                        }
                    }
                    items(state.recentSearches, key = { it.type + ":" + it.id }) { entry ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { actions.openRecentSearch(entry) }, modifier = Modifier.weight(1f)) { Text(entry.name, Modifier.fillMaxWidth(), maxLines = 1) }
                            ActionIcon(ClientIcons.Close, stringResource(R.string.ft_listening_remove_search), { actions.removeRecentSearch(entry) })
                        }
                    }
                }
            }
        }
        val loading = when (kind) { "artists" -> state.artistsLoading; "albums" -> state.albumSearchLoading; else -> state.loading }
        if (kind == "songs") LanguageFilterRow(state.songLanguage, "search_song_lang", actions::selectSongLanguage)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        ListeningRetry(when (kind) { "artists" -> state.artistsNotice; "albums" -> state.albumSearchNotice; else -> state.notice }, {
            when (kind) {
                "artists" -> actions.refreshArtists(state.artistQuery)
                "albums" -> actions.searchAlbums(state.albumSearchQuery)
                else -> actions.refresh()
            }
        }, "catalog_retry")
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("catalog_list"), contentPadding = PaddingValues(bottom = bottomInset)) {
            when (kind) {
                "artists" -> {
                    if (!loading && state.artists.isEmpty()) item { Text(stringResource(R.string.ft_listening_empty_artists), Modifier.padding(24.dp)) }
                    items(state.artists, key = { it.name }) { artist -> ListeningArtistTile(artist, loader) { actions.openArtist(artist) } }
                    if (state.artistsHasMore) item { TextButton(onClick = actions::loadMoreArtists, enabled = !state.artistsLoadingMore, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ft_load_more)) } }
                }
                "albums" -> {
                    if (!loading && state.searchAlbums.isEmpty()) item { Text(stringResource(R.string.ft_listening_empty_albums), Modifier.padding(24.dp)) }
                    items(state.searchAlbums, key = { it.id }) { album -> ListeningAlbumTile(album, loader) { actions.openAlbum(album) } }
                    if (state.albumSearchHasMore) item { TextButton(onClick = actions::loadMoreSearchAlbums, enabled = !state.albumSearchLoadingMore, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ft_load_more)) } }
                }
                else -> {
                    if (!loading && state.songs.isEmpty()) item { Text(stringResource(R.string.ft_empty_search), Modifier.padding(24.dp)) }
                    itemsIndexed(state.songs, key = { _, song -> song.id }) { index, song ->
                        SongRow(song, loader, song.id == state.playback.current?.id, state.playback.playing, state.playback.buffering,
                            modifier = Modifier.testTag("song_" + song.id)) { actions.playSongs(state.songs, index) }
                    }
                    if (state.hasMore) item { TextButton(onClick = actions::loadMore, enabled = !state.loadingMore, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ft_load_more)) } }
                }
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
        ListeningPageTitle(stringResource(R.string.ft_library_page), refresh = actions::refreshPlaylists, refreshTag = "library_refresh")
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { interaction?.createPlaylist?.invoke(emptyList()) }, modifier = Modifier.heightIn(min = 48.dp).testTag("create_playlist")) { Text(stringResource(R.string.ft_listening_create_playlist)) }
            TextButton(onClick = { interaction?.artists?.invoke() }) { Text(stringResource(R.string.ft_listening_artists)) }
            TextButton(onClick = { interaction?.albums?.invoke("") }) { Text(stringResource(R.string.ft_albums)) }
            TextButton(onClick = { editShelf = true }, enabled = state.playlistShelf != null && playlists.isNotEmpty() && !state.libraryMutationBusy,
                modifier = Modifier.testTag("edit_shelf")) { Text(stringResource(R.string.ft_listening_shelf_order)) }
        }
        if (state.playlistsLoading || state.libraryMutationBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        ListeningRetry(state.playlistsNotice, actions::refreshPlaylists)
        state.libraryMutationNotice?.let { Notice(it, Modifier.padding(horizontal = 16.dp)) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("library_playlists"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomInset),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (playlists.none { it.kind == "favorite" }) item(key = "favorite-entry") {
                Surface(Modifier.fillMaxWidth().clickable { actions.selectTab(LibraryTab.FAVORITES) }.testTag("library_tab_favorites"), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(ClientIcons.Heart, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.ft_liked_songs), Modifier.padding(start = 16.dp), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            items(playlists, key = { it.id }) { playlist ->
                Surface(Modifier.fillMaxWidth().clickable { actions.openPlaylist(playlist) }.testTag(if (playlist.kind == "favorite") "library_tab_favorites" else "library_playlist_" + playlist.id),
                    shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 88.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val cover = playlist.customCoverUrl ?: playlist.previewCovers.firstOrNull()
                            Artwork(Song("playlist:" + playlist.id, playlist.name, audioUrl = "", coverUrl = cover), loader, Modifier.size(60.dp))
                            Column(Modifier.weight(1f)) {
                                Text(playlist.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(stringResource(R.string.ft_listening_tracks_count, playlist.songCount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (playlist.description.isNotBlank()) Text(playlist.description, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        ActionIcon(ClientIcons.More, stringResource(R.string.ft_more_options), { interaction?.playlist?.invoke(playlist) }, modifier = Modifier.testTag("playlist_more_" + playlist.id))
                    }
                }
            }
            if (!state.playlistsLoading && playlists.none { it.kind != "favorite" }) item { Text(stringResource(R.string.ft_empty_playlists), Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    if (editShelf) ListeningShelfOrderDialog(state, actions, playlists) { editShelf = false }
}

@Composable
private fun ListeningShelfOrderDialog(state: ClientUiState, actions: ClientActions, playlists: List<PlaylistSummary>, close: () -> Unit) {
    val original = remember { playlists.map { it.id } }
    val baseRevision = remember { state.playlistShelf?.revision }
    var draft by remember { mutableStateOf(original) }
    var submitted by remember { mutableStateOf<Long?>(null) }
    val stale = state.playlistShelf?.revision != baseRevision || playlists.map { it.id }.toSet() != original.toSet() || state.libraryMutationConflict
    LaunchedEffect(state.libraryMutationVersion) { if (submitted != null && state.libraryMutationVersion > submitted!!) close() }
    AlertDialog(onDismissRequest = { if (!state.libraryMutationBusy) close() }, title = { Text(stringResource(R.string.ft_listening_shelf_order)) },
        text = {
            Column {
                if (stale) Text(stringResource(R.string.ft_listening_order_conflict), color = MaterialTheme.colorScheme.error)
                state.libraryMutationNotice?.let { Notice(it) }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp).testTag("shelf_order_list")) {
                    itemsIndexed(draft, key = { _, id -> id }) { index, id ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(playlists.firstOrNull { it.id == id }?.name.orEmpty(), Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            TextButton(enabled = index > 0 && !state.libraryMutationBusy && !stale, modifier = Modifier.testTag("shelf_up_" + id), onClick = {
                                draft = draft.toMutableList().apply { add(index - 1, removeAt(index)) }
                            }) { Text(stringResource(R.string.ft_listening_up)) }
                            TextButton(enabled = index < draft.lastIndex && !state.libraryMutationBusy && !stale, modifier = Modifier.testTag("shelf_down_" + id), onClick = {
                                draft = draft.toMutableList().apply { add(index + 1, removeAt(index)) }
                            }) { Text(stringResource(R.string.ft_listening_down)) }
                        }
                    }
                }
            }
        }, dismissButton = { TextButton(onClick = close, enabled = !state.libraryMutationBusy) { Text(stringResource(R.string.ft_library_cancel)) } },
        confirmButton = { TextButton(enabled = !state.libraryMutationBusy && !stale && draft != original,
            modifier = Modifier.testTag("shelf_order_save"), onClick = { submitted = state.libraryMutationVersion; actions.replacePlaylistShelfOrder(draft) }) { Text(stringResource(R.string.ft_listening_save)) } })
}

@Composable
internal fun ListeningPlaylistDetailPage(state: ClientUiState, actions: ClientActions, loader: ImageLoader?, topInset: Dp, bottomInset: Dp) {
    val playlist = state.selectedPlaylist ?: return
    val interaction = LocalListeningInteractions.current
    Column(Modifier.fillMaxSize().padding(top = topInset).testTag("library_page")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ActionIcon(ClientIcons.ChevronLeft, stringResource(R.string.ft_back), actions::closePlaylist, modifier = Modifier.testTag("library_playlist_back"))
            Text(playlist.name, Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            ActionIcon(ClientIcons.Refresh, stringResource(R.string.ft_refresh), actions::refreshPlaylist, modifier = Modifier.testTag("library_refresh"))
            ActionIcon(ClientIcons.More, stringResource(R.string.ft_more_options), { interaction?.playlist?.invoke(playlist) }, modifier = Modifier.testTag("playlist_more_" + playlist.id))
        }
        if (state.playlistLoading || state.libraryMutationBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        ListeningRetry(state.playlistNotice, actions::refreshPlaylist)
        state.libraryMutationNotice?.let { Notice(it, Modifier.padding(horizontal = 16.dp)) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("playlist_detail_tracks"), contentPadding = PaddingValues(bottom = bottomInset)) {
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(Song("playlist:" + playlist.id, playlist.name, audioUrl = "", coverUrl = playlist.customCoverUrl ?: playlist.previewCovers.firstOrNull()), loader, Modifier.size(96.dp), radius = 14.dp)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.ft_listening_tracks_count, playlist.songCount), style = MaterialTheme.typography.titleMedium)
                        if (playlist.description.isNotBlank()) Text(playlist.description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { actions.playPlaylist(playlist) }, enabled = state.playlistSongs.isNotEmpty() && !state.playlistLoading, modifier = Modifier.testTag("playlist_play")) { Text(stringResource(R.string.ft_play)) }
                    OutlinedButton(onClick = { interaction?.addSongs?.invoke(playlist) }, enabled = !state.libraryMutationBusy, modifier = Modifier.testTag("playlist_add_songs")) { Text(stringResource(R.string.ft_listening_add_songs)) }
                    TextButton(onClick = { interaction?.playlist?.invoke(playlist) }) { Text(stringResource(R.string.ft_listening_edit_order)) }
                }
            }
            if (!state.playlistLoading && state.selectedPlaylistEntries.isEmpty()) item { Text(stringResource(R.string.ft_listening_empty_playlist), Modifier.padding(24.dp)) }
            items(state.selectedPlaylistEntries, key = { it.song.id }) { entry ->
                Column {
                    SongRow(entry.song, loader, entry.song.id == state.playback.current?.id, state.playback.playing, state.playback.buffering,
                        modifier = Modifier.testTag("playlist_song_" + entry.song.id), clickEnabled = entry.canPlay) { actions.playSong(entry.song) }
                    if (!entry.canPlay) Text(stringResource(R.string.ft_listening_unplayable), Modifier.padding(start = 76.dp, bottom = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ft_listening_daily), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            ActionIcon(ClientIcons.Refresh, stringResource(R.string.ft_refresh), actions::refreshDiscovery, enabled = !state.discoveryLoading, modifier = Modifier.testTag("discovery_refresh"))
        }
        if (state.discoveryLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        ListeningRetry(state.discoveryNotice, actions::refreshDiscovery)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            itemsIndexed(state.discoverySongs, key = { _, song -> song.id }) { index, song ->
                Column(Modifier.width(160.dp).clickable { actions.playSongs(state.discoverySongs, index) }.testTag("discovery_song_" + song.id)) {
                    Artwork(song, loader, Modifier.size(160.dp), radius = 12.dp)
                    Text(song.title, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        if (spotlight != null) {
            Text(stringResource(R.string.ft_listening_spotlight), Modifier.padding(horizontal = 16.dp, vertical = 16.dp), style = MaterialTheme.typography.titleLarge)
            ListeningArtistTile(Artist(spotlight.artist, spotlight.songCount, photoUrl = spotlight.photoUrl, coverUrl = spotlight.coverUrl), loader) {
                actions.openArtist(Artist(spotlight.artist, spotlight.songCount, photoUrl = spotlight.photoUrl, coverUrl = spotlight.coverUrl))
            }
        }
    }
}
