package com.flaretune.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.flaretune.client.data.*
import com.flaretune.client.model.*
import com.flaretune.client.playback.PlaybackGateway
import com.flaretune.client.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class ClientViewModel(
    private val connections: Connections,
    private val player: PlaybackGateway,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val history: PlaybackHistoryRepository? = null,
    private val queueRepository: PlaybackQueueRepository? = null,
    private val translationPreferences: TranslationPreferences? = null,
    private val coverImageProcessor: CoverImageProcessor? = null,
    private val searchHistoryStore: SearchHistoryStore? = null,
    private val discoveryCache: DiscoveryCache? = null,
) : ViewModel(), ClientActions {
    private var preferredVolume = runCatching { translationPreferences?.readVolume() ?: 1f }
        .getOrDefault(1f).takeIf { it.isFinite() && it in 0f..1f } ?: 1f
    private var volumeSaveRevision = 0L
    private var volumeSaveJob: Job? = null
    private val volumeMutex = Mutex()
    private var translationEnabled = runCatching { translationPreferences?.read() ?: false }.getOrDefault(false)
    private val mutable = MutableStateFlow(ClientUiState(instanceUrl = connections.lastOrigin, translationEnabled = translationEnabled, playback = PlaybackSnapshot(volume = preferredVolume)))
    val state: StateFlow<ClientUiState> = mutable
    val imageLoader: ImageLoader? get() = player.imageLoader
    private val lyrics = LyricsCoordinator(viewModelScope, ioDispatcher)
    private var identity = 0L
    private val searchMutex = Mutex()
    private var discoveryRevision = 0L
    private var discoveryJob: Job? = null
    private var discoveryCheckedDate: String? = null
    private val seenSpotlightArtists = mutableListOf<String>()
    private var mutationJob: Job? = null
    private var albumSearchRevision = 0L
    private var albumSearchOffset = 0
    private var albumSearchJob: Job? = null
    private var pendingCreate: Pair<Triple<String, String, List<String>>, String>? = null
    private var pickerRevision = 0L
    private var pickerOffset = 0
    private var pickerJob: Job? = null
    private var artistsRevision = 0L
    private var artistsOffset = 0
    private var artistsJob: Job? = null
    private var artistRevision = 0L
    private var artistOffset = 0
    private var artistJob: Job? = null
    private var artistAlbumsOffset = 0
    private var artistAlbumsRevision = 0L
    private var artistAlbumsJob: Job? = null
    private var statsRevision = 0L
    private var statsJob: Job? = null
    private var requestRevision = 0L
    private var requestJob: Job? = null
    private var offset = 0
    private var albumOffset = 0
    private var albumRevision = 0L
    private var detailRevision = 0L
    private var albumJob: Job? = null
    private var detailJob: Job? = null
    private var playlistsRevision = 0L
    private var playlistsJob: Job? = null
    private var playlistRevision = 0L
    private var playlistJob: Job? = null
    private var favoritesRevision = 0L
    private var favoritesJob: Job? = null
    private var playRevision = 0L
    private var playJob: Job? = null
    private var historyJob: Job? = null
    private var queueRestoreJob: Job? = null
    private var queueRestoreRevision = 0L
    private var queueNoticeJob: Job? = null
    private var queueRestoreNotice: ClientNotice? = null
    private var historyEntries: List<HistoryEntry> = emptyList()
    private val historySongs = mutableMapOf<String, Song>()
    private val historyResolved = mutableSetOf<String>()

    private fun current(): ActiveConnection? = connections.current?.takeIf {
        mutable.value.screen == ClientScreen.LIBRARY && it.network.isActive && !it.auth.mustChangePassword
    }
    private fun valid(ticket: Long, connection: ActiveConnection) =
        ticket == identity && connections.current === connection && connection.network.isActive &&
            mutable.value.screen == ClientScreen.LIBRARY

    private fun cancelRequests() {
        requestRevision++; albumRevision++; detailRevision++
        playlistsRevision++; playlistRevision++; favoritesRevision++; playRevision++
        requestJob?.cancel(); albumJob?.cancel(); detailJob?.cancel()
        playlistsJob?.cancel(); playlistJob?.cancel(); favoritesJob?.cancel(); playJob?.cancel()
        historyJob?.cancel()
        queueRestoreRevision++; queueRestoreJob?.cancel()
        queueNoticeJob?.cancel()
        mutationJob?.cancel(); pendingCreate = null
        pickerRevision++; pickerJob?.cancel()
        artistsRevision++; artistsJob?.cancel(); artistRevision++; artistJob?.cancel()
        artistAlbumsRevision++; artistAlbumsJob?.cancel(); statsRevision++; statsJob?.cancel()
        albumSearchRevision++; albumSearchJob?.cancel()
        discoveryRevision++; discoveryJob?.cancel(); discoveryCheckedDate = null; seenSpotlightArtists.clear()
    }

    private fun resetPrivateData(clearHistory: Boolean = true) {
        cancelRequests()
        lyrics.clear()
        queueRestoreNotice = null
        if (clearHistory) { history?.clear(); queueRepository?.clear() }
        historyEntries = emptyList()
        historySongs.clear()
        historyResolved.clear()
    }

    init {
        viewModelScope.launch {
            player.state.collect { snapshot ->
                mutable.value = mutable.value.copy(playback = snapshot)
                lyrics.activate(current(), snapshot.current)
                lyrics.updatePosition(snapshot.positionMs)
            }
        }
        viewModelScope.launch { lyrics.state.collect { mutable.value = mutable.value.copy(lyricState = it) } }
        history?.let { repository ->
            viewModelScope.launch {
                repository.state.collect { entries ->
                    if (current() == null) return@collect
                    historyEntries = entries
                    renderHistory()
                    hydrateHistory()
                }
            }
            viewModelScope.launch { repository.notice.collect {
                if (current() != null) mutable.value = mutable.value.copy(historyNotice = it)
            } }
        }
        viewModelScope.launch {
            connections.invalidations.collect {
                identity++
                resetPrivateData()
                player.stopAndClear()
                mutable.value = ClientUiState(screen = ClientScreen.CONNECT,
                    instanceUrl = connections.lastOrigin, notice = it)
            }
        }
        val ticket = identity
        viewModelScope.launch {
            try {
                val connection = connections.restore()
                if (ticket != identity) return@launch
                if (connection == null) mutable.value = mutable.value.copy(screen = ClientScreen.CONNECT)
                else enter(connection, ticket)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (ticket == identity) mutable.value = mutable.value.copy(screen = ClientScreen.CONNECT, notice = notice(error))
            }
        }
    }

    override fun login(instanceUrl: String, username: String, password: String) {
        if (mutable.value.busy) return
        if (username.isBlank() || password.isEmpty()) {
            mutable.value = mutable.value.copy(notice = ClientNotice.INVALID_LOGIN)
            return
        }
        val ticket = ++identity
        resetPrivateData()
        player.stopAndClear()
        mutable.value = ClientUiState(screen = ClientScreen.CONNECT, instanceUrl = instanceUrl.trim(),
            username = username.trim(), busy = true)
        viewModelScope.launch {
            try {
                val connection = connections.login(instanceUrl, username, password)
                if (ticket == identity) enter(connection, ticket)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (ticket == identity) mutable.value = mutable.value.copy(busy = false, notice = notice(error))
            }
        }
    }

    private suspend fun enter(connection: ActiveConnection, ticket: Long) {
        mutable.value = ClientUiState(
            screen = if (connection.auth.mustChangePassword) ClientScreen.CHANGE_PASSWORD else ClientScreen.LIBRARY,
            instanceUrl = connection.network.address.origin, username = connection.auth.account.username,
            accountName = connection.auth.account.displayName, translationEnabled = translationEnabled)
        if (connection.auth.mustChangePassword) return
        try { connectPlayer(connection) } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (valid(ticket, connection)) mutable.value = mutable.value.copy(notice = ClientNotice.PLAYBACK_FAILED)
        }
        if (valid(ticket, connection)) {
            history?.let { repository ->
                repository.bind(HistoryIdentity(connection.network.address.origin, connection.auth.account.id))
                // A reused application repository may already have emitted during RESTORING.
                historyEntries = repository.state.value
                mutable.value = mutable.value.copy(historyNotice = repository.notice.value)
                renderHistory()
                hydrateHistory()
            }
            queueRepository?.let { repository ->
                repository.bind(HistoryIdentity(connection.network.address.origin, connection.auth.account.id))
                observeQueueNotice(repository, connection, ticket)
            }
            restoreSavedQueue()
            lyrics.activate(connection, player.state.value.current)
            lyrics.updatePosition(player.state.value.positionMs)
            refresh()
            loadListeningOverview()
        }
    }

    override fun changePassword(currentPassword: String, newPassword: String) {
        if (mutable.value.busy) return
        val ticket = identity
        mutable.value = mutable.value.copy(busy = true, notice = null)
        viewModelScope.launch {
            try {
                connections.changePassword(currentPassword, newPassword)
                if (ticket == identity) {
                    identity++
                    resetPrivateData()
                    player.stopAndClear()
                    mutable.value = ClientUiState(screen = ClientScreen.CONNECT,
                        instanceUrl = connections.lastOrigin, username = mutable.value.username,
                        notice = ClientNotice.PASSWORD_CHANGED)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (ticket == identity) mutable.value = mutable.value.copy(busy = false, notice = notice(error))
            }
        }
    }

    override fun logout() {
        val ticket = ++identity
        resetPrivateData()
        player.stopAndClear()
        mutable.value = ClientUiState(screen = ClientScreen.CONNECT, instanceUrl = connections.lastOrigin, busy = true)
        viewModelScope.launch {
            var failure: ClientNotice? = null
            try { connections.logout() } catch (error: Exception) {
                if (error is CancellationException) throw error
                failure = notice(error)
            }
            if (ticket == identity) mutable.value = mutable.value.copy(busy = false, notice = failure)
        }
    }

    override fun search(query: String) {
        val normalized = query.trim().take(200)
        if (normalized.isNotBlank()) updateSearchHistory { store, key -> store.addTerm(key, normalized) }
        if (normalized != mutable.value.query) {
            offset = 0
            mutable.value = mutable.value.copy(query = normalized, songs = emptyList(), hasMore = false, loadingMore = false)
        }
        load(true)
        refreshArtists(normalized)
        searchAlbums(normalized)
    }

    override fun selectTab(tab: LibraryTab) {
        if (tab == mutable.value.tab) return
        requestRevision++
        requestJob?.cancel()
        offset = 0
        mutable.value = mutable.value.copy(tab = tab, query = "", songs = emptyList(), hasMore = false,
            loading = false, loadingMore = false)
        if (tab == LibraryTab.ALBUMS) loadAlbums(true) else load(true)
    }

    override fun navigate(destination: MainDestination) {
        closeAlbum()
        closePlaylist()
        closeArtist()
        mutable.value = mutable.value.copy(destination = destination)
        if (destination == MainDestination.HOME) {
            if (mutable.value.tab != LibraryTab.CATALOG) selectTab(LibraryTab.CATALOG)
            else if (mutable.value.query.isNotBlank()) search("")
            refreshFavorites()
        } else if (destination == MainDestination.SEARCH && mutable.value.tab != LibraryTab.CATALOG) {
            selectTab(LibraryTab.CATALOG)
        } else if (destination == MainDestination.LIBRARY) {
            if (mutable.value.query.isNotBlank()) search("")
            refreshPlaylists()
        }
    }

    override fun refresh() {
        mutable.value.selectedArtist?.let { openArtist(it); return }
        mutable.value.selectedPlaylist?.let { openPlaylist(it); return }
        mutable.value.selectedAlbum?.let { openAlbum(it); return }
        if (mutable.value.tab == LibraryTab.ALBUMS) loadAlbums(true)
        else {
            load(true)
            if (mutable.value.destination == MainDestination.HOME) loadAlbums(true)
        }
        refreshPlaylists()
    }

    override fun selectSongLanguage(language: String) {
        if (language !in languages || language == mutable.value.songLanguage) return
        requestRevision++; requestJob?.cancel(); offset = 0
        mutable.value = mutable.value.copy(songLanguage = language, songs = emptyList(), hasMore = false,
            loading = false, loadingMore = false)
        load(true)
    }

    override fun selectAlbumLanguage(language: String) {
        if (language !in languages || language == mutable.value.albumLanguage) return
        albumRevision++; albumJob?.cancel(); albumOffset = 0
        mutable.value = mutable.value.copy(albumLanguage = language, albums = emptyList(), albumsHasMore = false,
            albumsLoading = false, albumsLoadingMore = false)
        loadAlbums(true)
    }

    override fun loadMoreAlbums() {
        if (mutable.value.albumsHasMore && !mutable.value.albumsLoading && !mutable.value.albumsLoadingMore) loadAlbums(false)
    }

    private fun loadAlbums(reset: Boolean) {
        val connection = current() ?: return
        val ticket = identity
        val revision = ++albumRevision
        val requestedOffset = if (reset) 0 else albumOffset
        val language = mutable.value.albumLanguage
        albumJob?.cancel()
        mutable.value = mutable.value.copy(albumsLoading = reset, albumsLoadingMore = !reset, albumsNotice = null)
        albumJob = viewModelScope.launch {
            try {
                val page = withContext(ioDispatcher) { connection.api.albums(requestedOffset, language) }
                if (!valid(ticket, connection) || revision != albumRevision) return@launch
                albumOffset = page.nextOffset
                mutable.value = mutable.value.copy(
                    albums = (if (reset) page.albums else mutable.value.albums + page.albums).distinctBy { it.id },
                    albumsLoading = false, albumsLoadingMore = false, albumsHasMore = page.hasMore,
                    albumsNotice = if (page.capped) ClientNotice.PAGE_LIMIT else null)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == albumRevision)
                    mutable.value = mutable.value.copy(albumsLoading = false, albumsLoadingMore = false, albumsNotice = notice(error))
            }
        }
    }

    override fun openAlbum(album: Album) {
        val connection = current() ?: return
        val canonical = (mutable.value.albums + mutable.value.searchAlbums + mutable.value.artistAlbums).find { it.id == album.id }
            ?: mutable.value.selectedAlbum?.takeIf { it.id == album.id } ?: return
        closePlaylist()
        closeArtist()
        val ticket = identity
        val revision = ++detailRevision
        detailJob?.cancel()
        mutable.value = mutable.value.copy(selectedAlbum = canonical, albumSongs = emptyList(), albumLoading = true, albumNotice = null)
        detailJob = viewModelScope.launch {
            try {
                val detail = withContext(ioDispatcher) { connection.api.album(canonical.id) }
                if (!valid(ticket, connection) || revision != detailRevision) return@launch
                mutable.value = mutable.value.copy(selectedAlbum = detail.album, albumSongs = detail.songs, albumLoading = false)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == detailRevision)
                    mutable.value = mutable.value.copy(albumLoading = false, albumNotice = notice(error))
            }
        }
    }

    override fun closeAlbum() {
        detailRevision++; detailJob?.cancel()
        mutable.value = mutable.value.copy(selectedAlbum = null, albumSongs = emptyList(), albumLoading = false, albumNotice = null)
    }

    override fun loadMore() {
        if (mutable.value.hasMore && !mutable.value.loadingMore && !mutable.value.loading) load(false)
    }

    private fun load(reset: Boolean) {
        val connection = current() ?: return
        val ticket = identity
        val snapshot = mutable.value
        val query = snapshot.query
        val tab = snapshot.tab
        val language = snapshot.songLanguage
        val requestedOffset = if (reset) 0 else offset
        val revision = ++requestRevision
        requestJob?.cancel()
        mutable.value = snapshot.copy(loading = reset, loadingMore = !reset, notice = null)
        requestJob = viewModelScope.launch {
            try {
                val page = withContext(ioDispatcher) {
                    if (tab == LibraryTab.FAVORITES) {
                        val songs = connection.api.favorites().filter {
                            (language == "all" || it.language == language) &&
                                (query.isBlank() || listOf(it.title, it.artist, it.album).any { value -> value.contains(query, true) })
                        }
                        SongPage(songs, 0, false)
                    } else connection.api.songs(query, requestedOffset, language, snapshot.songSort)
                }
                if (!valid(ticket, connection) || revision != requestRevision) return@launch
                offset = page.nextOffset
                mutable.value = mutable.value.copy(
                    songs = (if (reset) page.songs else mutable.value.songs + page.songs).distinctBy { it.id },
                    loading = false, loadingMore = false, hasMore = page.hasMore,
                    notice = when { page.capped -> ClientNotice.PAGE_LIMIT
                        page.partialSources -> ClientNotice.SOME_SOURCES_UNAVAILABLE
                        else -> null })
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == requestRevision)
                    mutable.value = mutable.value.copy(loading = false, loadingMore = false, notice = notice(error))
            }
        }
    }

    override fun refreshPlaylists() {
        val connection = current() ?: return
        val ticket = identity
        val revision = ++playlistsRevision
        playlistsJob?.cancel()
        mutable.value = mutable.value.copy(playlistsLoading = true, playlistsNotice = null)
        playlistsJob = viewModelScope.launch {
            try {
                val (items, shelf) = withContext(ioDispatcher) {
                    val rows = connection.api.playlists()
                    val order = try { connection.api.playlistShelf() } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        null
                    }
                    rows to order
                }
                if (!valid(ticket, connection) || revision != playlistsRevision) return@launch
                val selected = mutable.value.selectedPlaylist
                val updated = selected?.let { item -> items.find { it.id == item.id } }
                if (selected != null && updated == null) closePlaylist()
                mutable.value = mutable.value.copy(playlists = items, playlistShelf = shelf ?: mutable.value.playlistShelf,
                    playlistsLoading = false, selectedPlaylist = updated, playlistsNotice = null)
                refreshFavorites()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == playlistsRevision)
                    mutable.value = mutable.value.copy(playlistsLoading = false, playlistsNotice = notice(error))
            }
        }
    }

    override fun openPlaylist(playlist: PlaylistSummary) {
        val connection = current() ?: return
        val canonical = mutable.value.playlists.find { it.id == playlist.id }
            ?: mutable.value.selectedPlaylist?.takeIf { it.id == playlist.id } ?: return
        closeAlbum()
        closeArtist()
        closePlaylistSongPicker()
        val ticket = identity
        val revision = ++playlistRevision
        playlistJob?.cancel()
        mutable.value = mutable.value.copy(selectedPlaylist = canonical, playlistSongs = emptyList(), selectedPlaylistEntries = emptyList(),
            playlistLoading = true, playlistNotice = null)
        playlistJob = viewModelScope.launch {
            try {
                val detail = withContext(ioDispatcher) { connection.api.playlist(canonical.id) }
                if (!valid(ticket, connection) || revision != playlistRevision ||
                    mutable.value.selectedPlaylist?.id != canonical.id) return@launch
                publishPlaylist(detail)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == playlistRevision)
                    mutable.value = mutable.value.copy(playlistLoading = false, playlistNotice = notice(error))
            }
        }
    }

    override fun closePlaylist() {
        playlistRevision++; playlistJob?.cancel()
        closePlaylistSongPicker()
        mutable.value = mutable.value.copy(selectedPlaylist = null, playlistSongs = emptyList(), selectedPlaylistEntries = emptyList(),
            playlistLoading = false, playlistNotice = null)
    }

    override fun refreshPlaylist() { mutable.value.selectedPlaylist?.let(::openPlaylist) }
    override fun refreshFavorites() { loadFavorites(false) }
    override fun playFavorites() { loadFavorites(true) }

    private fun publishFavorites(detail: PlaylistDetail?) {
        val latest = detail?.entries?.maxWithOrNull(compareBy<PlaylistSong> { it.addedAt }.thenBy { it.sortOrder })
        mutable.value = mutable.value.copy(favoriteSongs = detail?.songs.orEmpty(), favoriteSongIds = detail?.entries?.map { it.song.id }?.toSet().orEmpty(),
            latestFavoriteCoverUrl = latest?.song?.coverUrl, favoritesLoading = false, favoritesNotice = null)
    }

    private fun loadFavorites(playAfter: Boolean) {
        val connection = current() ?: return
        val ticket = identity
        val known = mutable.value.playlists.find { it.kind == "favorite" }
        mutable.value = mutable.value.copy(favoritesLoading = true, favoritesNotice = null)
        if (playAfter) {
            val revision = beginPlaybackCommand()
            playJob?.cancel()
            playJob = viewModelScope.launch {
                try {
                    val detail = withContext(ioDispatcher) { readFavorites(connection, known) }
                    if (!valid(ticket, connection) || revision != playRevision) return@launch
                    publishFavorites(detail)
                    if (!detail?.songs.isNullOrEmpty()) {
                        connectPlayer(connection)
                        if (valid(ticket, connection) && revision == playRevision) player.play(detail!!.songs, 0)
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    if (valid(ticket, connection) && revision == playRevision)
                        mutable.value = mutable.value.copy(favoritesLoading = false, favoritesNotice = notice(error))
                }
            }
            return
        }
        val revision = ++favoritesRevision
        favoritesJob?.cancel()
        favoritesJob = viewModelScope.launch {
            try {
                val detail = withContext(ioDispatcher) { readFavorites(connection, known) }
                if (!valid(ticket, connection) || revision != favoritesRevision) return@launch
                publishFavorites(detail)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == favoritesRevision)
                    mutable.value = mutable.value.copy(favoritesLoading = false, favoritesNotice = notice(error))
            }
        }
    }

    private fun readFavorites(connection: ActiveConnection, known: PlaylistSummary?): PlaylistDetail? {
        val favorite = known ?: connection.api.playlists().find { it.kind == "favorite" }
        return favorite?.let { connection.api.playlist(it.id) }
    }

    private fun beginPlaybackCommand(): Long {
        queueRestoreRevision++
        queueRestoreJob?.cancel()
        mutable.value = mutable.value.copy(queueRestoring = false)
        return ++playRevision
    }
    private fun renderHistory() {
        val snapshot = mutable.value
        val live = (snapshot.playback.queue + snapshot.songs + snapshot.favoriteSongs +
            snapshot.albumSongs + snapshot.playlistSongs).associateBy { it.id }
        mutable.value = snapshot.copy(playHistoryPlayedAt = historyEntries.associate { it.id to it.playedAt }, playHistory = historyEntries.map { entry ->
            live[entry.id] ?: historySongs[entry.id] ?: entry.toSong()
        })
    }

    private fun hydrateHistory() {
        val connection = current() ?: return
        val ids = historyEntries.map { it.id }.filter { it !in historyResolved }
        if (ids.isEmpty()) return
        val ticket = identity
        historyResolved.addAll(ids)
        // Reads are bounded to the store's 200 entries and reuse one resolve request.
        viewModelScope.launch {
            try {
                val songs = resolveStoredSongs(connection, ids)
                if (!valid(ticket, connection)) return@launch
                songs.forEach { historySongs[it.id] = it }
                renderHistory()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection)) {
                    historyResolved.removeAll(ids.toSet())
                    mutable.value = mutable.value.copy(historyNotice = notice(error))
                }
            }
        }
    }

    override fun playSong(song: Song) {
        val connection = current() ?: return
        val snapshot = mutable.value
        val songs = when {
            snapshot.selectedPlaylist != null -> snapshot.playlistSongs
            snapshot.selectedAlbum != null -> snapshot.albumSongs
            snapshot.selectedArtist != null -> snapshot.artistSongs
            snapshot.discoverySongs.any { it.id == song.id } -> snapshot.discoverySongs
            snapshot.spotlightArtist?.songs?.any { it.id == song.id } == true -> snapshot.spotlightArtist.songs
            snapshot.playStats?.songs?.any { it.song.id == song.id } == true -> snapshot.playStats.songs.map { it.song }
            snapshot.songs.any { it.id == song.id } -> snapshot.songs
            snapshot.favoriteSongs.any { it.id == song.id } -> snapshot.favoriteSongs
            else -> emptyList()
        }
        val index = songs.indexOfFirst { it.id == song.id }
        if (index < 0) {
            if (snapshot.playHistory.any { it.id == song.id }) playHistorySong(song)
            return
        }
        rememberSearchEntity(SearchEntity.fromSong(songs[index]))
        val ticket = identity
        val revision = beginPlaybackCommand()
        playJob?.cancel()
        playJob = viewModelScope.launch {
            try {
                connectPlayer(connection)
                if (valid(ticket, connection) && revision == playRevision) player.play(songs, index)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == playRevision)
                    mutable.value = mutable.value.copy(notice = ClientNotice.PLAYBACK_FAILED)
            }
        }
    }

    override fun playHistorySong(song: Song) {
        val connection = current() ?: return
        if (historyEntries.none { it.id == song.id }) return
        val ticket = identity
        val revision = beginPlaybackCommand()
        playJob?.cancel()
        playJob = viewModelScope.launch {
            try {
                // Stored history never supplies an authenticated or temporary playback URL.
                val fresh = withContext(ioDispatcher) { connection.api.song(song.id) }
                if (!valid(ticket, connection) || revision != playRevision) return@launch
                connectPlayer(connection)
                if (!valid(ticket, connection) || revision != playRevision) return@launch
                historySongs[fresh.id] = fresh
                renderHistory()
                player.play(listOf(fresh), 0)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == playRevision)
                    mutable.value = mutable.value.copy(historyNotice = notice(error))
            }
        }
    }

    private suspend fun resolveStoredSongs(connection: ActiveConnection, ids: List<String>): List<Song> =
        withContext(ioDispatcher) {
            val eligible = ids.distinct().filter { id ->
                id.isNotBlank() && id.length <= 256 && id == id.trim() && id !in setOf(".", "..") &&
                    id.none { it.code < 32 || it.code == 127 }
            }
            val shortIds = eligible.filter { it.length <= 80 }
            val resolved = if (shortIds.isEmpty()) emptyList() else connection.api.resolveSongs(shortIds)
            val semaphore = Semaphore(4)
            val longer = coroutineScope {
                eligible.filter { it.length > 80 }.map { id ->
                    async {
                        semaphore.withPermit {
                            try { connection.api.song(id) } catch (error: ApiFailure) {
                                if (error.httpStatus == 404) null else throw error
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            val byId = (resolved + longer).associateBy { it.id }
            eligible.mapNotNull(byId::get)
        }

    private fun updateQueueNotice(repository: PlaybackQueueRepository) {
        // A successful disk save clears only its own error, retaining partial-restore warnings.
        mutable.value = mutable.value.copy(queueNotice = repository.notice.value ?: queueRestoreNotice)
    }

    private fun observeQueueNotice(repository: PlaybackQueueRepository, connection: ActiveConnection, ticket: Long) {
        queueNoticeJob?.cancel()
        if (valid(ticket, connection)) updateQueueNotice(repository)
        queueNoticeJob = viewModelScope.launch {
            repository.notice.collect {
                if (valid(ticket, connection)) updateQueueNotice(repository)
            }
        }
    }

    override fun retryQueueRestore() {
        val connection = current() ?: return
        queueRepository?.reload()
        if (connection.network.isActive) restoreSavedQueue()
    }

    private fun restoreSavedQueue() {
        val repository = queueRepository ?: return
        val connection = current() ?: return
        if (player.state.value.queue.isNotEmpty()) return
        val ticket = identity
        val revision = ++queueRestoreRevision
        val playTicket = playRevision
        queueRestoreJob?.cancel()
        queueRestoreNotice = null
        mutable.value = mutable.value.copy(queueRestoring = true)
        updateQueueNotice(repository)
        queueRestoreJob = viewModelScope.launch {
            try {
                repository.loaded.first { it }
                if (!valid(ticket, connection) || revision != queueRestoreRevision ||
                    playTicket != playRevision || player.state.value.queue.isNotEmpty()) return@launch
                repository.notice.value?.let {
                    updateQueueNotice(repository)
                    return@launch
                }
                val saved = repository.state.value ?: return@launch
                val songs = resolveStoredSongs(connection, saved.entries.map { it.id }).associateBy { it.id }
                if (!valid(ticket, connection) || revision != queueRestoreRevision ||
                    playTicket != playRevision || player.state.value.queue.isNotEmpty()) return@launch
                val indexed = saved.entries.mapIndexedNotNull { index, entry -> songs[entry.id]?.let { index to it } }
                if (indexed.isEmpty()) {
                    queueRestoreNotice = ClientNotice.REQUEST_FAILED
                    updateQueueNotice(repository)
                    return@launch
                }
                val target = indexed.indexOfFirst { it.first >= saved.currentIndex }.takeIf { it >= 0 } ?: indexed.lastIndex
                val sameCurrent = indexed[target].first == saved.currentIndex
                val durationMs = (indexed[target].second.durationSeconds * 1000).toLong()
                val position = if (!sameCurrent) 0L else if (durationMs > 0) saved.positionMs.coerceIn(0L, durationMs) else saved.positionMs
                connectPlayer(connection)
                if (!valid(ticket, connection) || revision != queueRestoreRevision ||
                    playTicket != playRevision || player.state.value.queue.isNotEmpty()) return@launch
                player.restoreQueue(indexed.map { it.second }, target, position, saved.shuffle, saved.repeatMode)
                if (indexed.size != saved.entries.size) {
                    queueRestoreNotice = ClientNotice.REQUEST_FAILED
                    updateQueueNotice(repository)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == queueRestoreRevision) {
                    queueRestoreNotice = notice(error)
                    updateQueueNotice(repository)
                }
            } finally {
                if (valid(ticket, connection) && revision == queueRestoreRevision)
                    mutable.value = mutable.value.copy(queueRestoring = false)
            }
        }
    }





    private suspend fun connectPlayer(connection: ActiveConnection) {
        player.connect(connection)
        if (connection.network.isActive && connections.current === connection) player.setVolume(preferredVolume)
    }

    private fun listeningIdentity(connection: ActiveConnection) =
        HistoryIdentity(connection.network.address.origin, connection.auth.account.id)

    private fun updateSearchHistory(work: (SearchHistoryStore, HistoryIdentity) -> SearchHistorySnapshot) {
        val store = searchHistoryStore ?: return
        val connection = current() ?: return
        val ticket = identity
        val key = listeningIdentity(connection)
        viewModelScope.launch {
            try {
                val snapshot = withContext(ioDispatcher) {
                    searchMutex.withLock {
                        if (!valid(ticket, connection)) return@withLock null
                        work(store, key)
                    }
                } ?: return@launch
                if (valid(ticket, connection)) mutable.value = mutable.value.copy(
                    recentSearches = snapshot.entities, recentSearchTerms = snapshot.terms, searchHistoryNotice = null)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection)) mutable.value = mutable.value.copy(searchHistoryNotice = notice(error))
            }
        }
    }
    private fun rememberSearchEntity(entry: SearchEntity) {
        if (mutable.value.destination == MainDestination.SEARCH) updateSearchHistory { store, key -> store.addEntity(key, entry) }
    }
    override fun removeRecentSearch(entry: SearchEntity) = updateSearchHistory { store, key -> store.removeEntity(key, entry) }
    override fun clearRecentSearches() = updateSearchHistory { store, key -> store.clearEntities(key) }
    override fun removeRecentSearchTerm(term: String) = updateSearchHistory { store, key -> store.removeTerm(key, term) }
    override fun clearRecentSearchTerms() = updateSearchHistory { store, key -> store.clearTerms(key) }
    override fun openRecentSearch(entry: SearchEntity) {
        when (entry.type) {
            "song" -> playSongs(listOf(entry.toSong()))
            "artist" -> openArtist(Artist(entry.name))
        }
    }

    private fun loadListeningOverview() {
        updateSearchHistory { store, key -> store.load(key) }
        loadDiscovery(false)
        refreshPlayStats()
        val connection = current() ?: return
        val ticket = identity
        viewModelScope.launch {
            try {
                val counts = withContext(ioDispatcher) { connection.api.languageCounts() }
                if (valid(ticket, connection)) mutable.value = mutable.value.copy(languageCounts = counts)
            } catch (error: Exception) { if (error is CancellationException) throw error }
        }
    }

    private fun today(): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date())
    override fun refreshDiscovery() = loadDiscovery(true)
    private fun loadDiscovery(force: Boolean) {
        val connection = current() ?: return
        if (mutable.value.discoveryLoading) return
        val ticket = identity
        val revision = ++discoveryRevision
        val key = listeningIdentity(connection)
        val day = today()
        discoveryJob?.cancel()
        mutable.value = mutable.value.copy(discoveryLoading = true, discoveryNotice = null)
        discoveryJob = viewModelScope.launch {
            var failure: ClientNotice? = null
            try {
                val cached = try { withContext(ioDispatcher) { discoveryCache?.load(key) } }
                catch (error: Exception) {
                    if (error is CancellationException) throw error
                    failure = notice(error); null
                }
                if (!valid(ticket, connection) || revision != discoveryRevision) return@launch
                // Hydrate metadata in the active session; no temporary media/image URL is read from disk.
                if (!force && cached != null) {
                    try {
                        val ids = cached.songs.map { it.id } + cached.spotlight?.songs.orEmpty().map { it.id }
                        val resolved = resolveStoredSongs(connection, ids).associateBy { it.id }
                        if (!valid(ticket, connection) || revision != discoveryRevision) return@launch
                        val songs = cached.songs.mapNotNull { resolved[it.id] }
                        val spotlight = cached.spotlight?.let { item ->
                            SpotlightArtist(item.artist, item.songCount, item.songs.mapNotNull { resolved[it.id] },
                                coverUrl = item.songs.firstNotNullOfOrNull { resolved[it.id]?.coverUrl })
                        }
                        mutable.value = mutable.value.copy(discoverySongs = songs, spotlightArtist = spotlight)
                        spotlight?.artist?.let { seenSpotlightArtists.add(0, it) }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        failure = notice(error)
                    }
                }
                val dailyFresh = cached?.date == day && cached.dailyLoaded
                val spotlightFresh = cached?.date == day && cached.spotlightLoaded
                val refreshDaily = (!dailyFresh || force) &&
                    (!force || (discoveryCache?.remainingCooldownMs(key, "daily") ?: 0) == 0L)
                val refreshSpotlight = (!spotlightFresh || force) &&
                    (!force || (discoveryCache?.remainingCooldownMs(key, "spotlight") ?: 0) == 0L)
                if (refreshDaily) {
                    try {
                        val exclude = mutable.value.discoverySongs.map { it.id }.ifEmpty { cached?.songs.orEmpty().map { it.id } }.take(20)
                        val songs = withContext(ioDispatcher) { connection.api.randomSongs(limit = 20, exclude = exclude) }
                            .filter { it.canPlay }.distinctBy { it.id }.take(20)
                        if (!valid(ticket, connection) || revision != discoveryRevision) return@launch
                        mutable.value = mutable.value.copy(discoverySongs = songs)
                        try { withContext(ioDispatcher) {
                            discoveryCache?.saveDaily(key, day, songs)
                            discoveryCache?.markRefreshed(key, "daily")
                        } } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            failure = notice(error)
                        }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        failure = notice(error)
                    }
                }
                if (refreshSpotlight) {
                    try {
                        val exclude = (listOfNotNull(mutable.value.spotlightArtist?.artist) + seenSpotlightArtists).distinct().take(15)
                        val spotlight = withContext(ioDispatcher) { connection.api.spotlightArtist(exclude) }
                        if (!valid(ticket, connection) || revision != discoveryRevision) return@launch
                        mutable.value = mutable.value.copy(spotlightArtist = spotlight)
                        spotlight?.artist?.let {
                            seenSpotlightArtists.remove(it); seenSpotlightArtists.add(0, it)
                            while (seenSpotlightArtists.size > 15) seenSpotlightArtists.removeAt(seenSpotlightArtists.lastIndex)
                        }
                        try { withContext(ioDispatcher) {
                            discoveryCache?.saveSpotlight(key, day, spotlight)
                            discoveryCache?.markRefreshed(key, "spotlight")
                        } } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            failure = notice(error)
                        }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        failure = notice(error)
                    }
                }
                if (valid(ticket, connection) && revision == discoveryRevision) {
                    discoveryCheckedDate = day
                    mutable.value = mutable.value.copy(discoveryNotice = failure)
                }
            } finally {
                if (valid(ticket, connection) && revision == discoveryRevision)
                    mutable.value = mutable.value.copy(discoveryLoading = false)
            }
        }
    }

    override fun replacePlaylistShelfOrder(ids: List<String>) {
        val shelf = mutable.value.playlistShelf ?: return
        val byId = shelf.items.associateBy { it.id }
        if (mutable.value.libraryMutationConflict || ids.size != byId.size || ids.toSet() != byId.keys) return
        mutateLibrary {
            val result = it.api.updatePlaylistShelf(ids.map { id -> byId.getValue(id) }, shelf.revision)
            LibraryUpdate(shelf = result.shelf, complete = result.outcome in setOf("applied", "noop"))
        }
    }

    override fun selectSongSort(sort: String) {
        if (sort !in setOf("asc", "desc") || sort == mutable.value.songSort) return
        requestRevision++; requestJob?.cancel(); offset = 0
        mutable.value = mutable.value.copy(songSort = sort, songs = emptyList(), hasMore = false, loadingMore = false)
        load(true)
    }

    override fun searchPlaylistSongs(query: String) {
        val value = query.trim().take(200)
        if (value != mutable.value.pickerQuery) mutable.value = mutable.value.copy(
            pickerQuery = value, pickerSongs = emptyList(), pickerHasMore = false)
        loadPicker(true)
    }
    override fun loadMorePlaylistSongs() {
        if (mutable.value.pickerHasMore && !mutable.value.pickerLoading && !mutable.value.pickerLoadingMore) loadPicker(false)
    }
    override fun closePlaylistSongPicker() {
        pickerRevision++; pickerJob?.cancel(); pickerOffset = 0
        mutable.value = mutable.value.copy(pickerSongs = emptyList(), pickerQuery = "", pickerLoading = false,
            pickerLoadingMore = false, pickerHasMore = false, pickerNotice = null)
    }
    private fun loadPicker(reset: Boolean) {
        val connection = current() ?: return
        val selected = mutable.value.selectedPlaylist?.id ?: return
        val ticket = identity
        val revision = ++pickerRevision
        val query = mutable.value.pickerQuery
        val requestedOffset = if (reset) 0 else pickerOffset
        pickerJob?.cancel()
        mutable.value = mutable.value.copy(pickerLoading = reset, pickerLoadingMore = !reset, pickerNotice = null)
        pickerJob = viewModelScope.launch {
            try {
                // Deliberately independent from the global tab/language/query and detail navigation.
                val page = withContext(ioDispatcher) { connection.api.songs(query, requestedOffset, "all") }
                if (!valid(ticket, connection) || revision != pickerRevision || mutable.value.selectedPlaylist?.id != selected) return@launch
                pickerOffset = page.nextOffset
                mutable.value = mutable.value.copy(pickerSongs = (if (reset) page.songs else mutable.value.pickerSongs + page.songs).distinctBy { it.id },
                    pickerLoading = false, pickerLoadingMore = false, pickerHasMore = page.hasMore,
                    pickerNotice = when { page.capped -> ClientNotice.PAGE_LIMIT; page.partialSources -> ClientNotice.SOME_SOURCES_UNAVAILABLE; else -> null })
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == pickerRevision) mutable.value = mutable.value.copy(
                    pickerLoading = false, pickerLoadingMore = false, pickerNotice = notice(error))
            }
        }
    }

    override fun searchAlbums(query: String) {
        val normalized = query.trim().take(200)
        if (normalized != mutable.value.albumSearchQuery) mutable.value = mutable.value.copy(
            albumSearchQuery = normalized, searchAlbums = emptyList(), albumSearchHasMore = false)
        loadAlbumSearch(true)
    }
    override fun loadMoreSearchAlbums() {
        if (mutable.value.albumSearchHasMore && !mutable.value.albumSearchLoading && !mutable.value.albumSearchLoadingMore)
            loadAlbumSearch(false)
    }
    private fun loadAlbumSearch(reset: Boolean) {
        val connection = current() ?: return
        val ticket = identity
        val revision = ++albumSearchRevision
        val query = mutable.value.albumSearchQuery
        val nextOffset = if (reset) 0 else albumSearchOffset
        albumSearchJob?.cancel()
        mutable.value = mutable.value.copy(albumSearchLoading = reset, albumSearchLoadingMore = !reset, albumSearchNotice = null)
        albumSearchJob = viewModelScope.launch {
            try {
                val page = withContext(ioDispatcher) { connection.api.browseAlbums(query = query, offset = nextOffset) }
                if (!valid(ticket, connection) || revision != albumSearchRevision) return@launch
                albumSearchOffset = page.nextOffset
                mutable.value = mutable.value.copy(searchAlbums = (if (reset) page.albums else mutable.value.searchAlbums + page.albums).distinctBy { it.id },
                    albumSearchLoading = false, albumSearchLoadingMore = false, albumSearchHasMore = page.hasMore,
                    albumSearchNotice = if (page.capped) ClientNotice.PAGE_LIMIT else null)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == albumSearchRevision) mutable.value = mutable.value.copy(
                    albumSearchLoading = false, albumSearchLoadingMore = false, albumSearchNotice = notice(error))
            }
        }
    }

    override fun refreshArtists(query: String) {
        val normalized = query.trim().take(200)
        if (normalized != mutable.value.artistQuery) mutable.value = mutable.value.copy(
            artistQuery = normalized, artists = emptyList(), artistsHasMore = false)
        loadArtists(true)
    }
    override fun loadMoreArtists() {
        if (mutable.value.artistsHasMore && !mutable.value.artistsLoading && !mutable.value.artistsLoadingMore) loadArtists(false)
    }
    private fun loadArtists(reset: Boolean) {
        val connection = current() ?: return
        val ticket = identity
        val revision = ++artistsRevision
        val query = mutable.value.artistQuery
        val nextOffset = if (reset) 0 else artistsOffset
        artistsJob?.cancel()
        mutable.value = mutable.value.copy(artistsLoading = reset, artistsLoadingMore = !reset, artistsNotice = null)
        artistsJob = viewModelScope.launch {
            try {
                val page = withContext(ioDispatcher) { connection.api.artists(query, nextOffset) }
                if (!valid(ticket, connection) || revision != artistsRevision) return@launch
                artistsOffset = page.nextOffset
                mutable.value = mutable.value.copy(artists = (if (reset) page.artists else mutable.value.artists + page.artists).distinctBy { it.name },
                    artistsLoading = false, artistsLoadingMore = false, artistsHasMore = page.hasMore,
                    artistsNotice = if (page.capped) ClientNotice.PAGE_LIMIT else null)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == artistsRevision) mutable.value = mutable.value.copy(
                    artistsLoading = false, artistsLoadingMore = false, artistsNotice = notice(error))
            }
        }
    }

    override fun openArtist(artist: Artist) {
        rememberSearchEntity(SearchEntity.fromArtist(artist.name))
        if (current() == null || artist.name.isBlank()) return
        closeAlbum(); closePlaylist(); closeArtist()
        mutable.value = mutable.value.copy(selectedArtist = artist)
        loadArtist(true); loadArtistAlbums(true)
    }
    override fun closeArtist() {
        artistRevision++; artistJob?.cancel(); artistOffset = 0
        artistAlbumsRevision++; artistAlbumsJob?.cancel(); artistAlbumsOffset = 0
        mutable.value = mutable.value.copy(selectedArtist = null, artistSongs = emptyList(), artistLoading = false,
            artistLoadingMore = false, artistHasMore = false, artistNotice = null, artistAlbums = emptyList(),
            artistAlbumsLoading = false, artistAlbumsLoadingMore = false, artistAlbumsHasMore = false, artistAlbumsNotice = null)
    }
    override fun loadMoreArtistSongs() {
        if (mutable.value.artistHasMore && !mutable.value.artistLoading && !mutable.value.artistLoadingMore) loadArtist(false)
    }
    private fun loadArtist(reset: Boolean) {
        val connection = current() ?: return
        val selected = mutable.value.selectedArtist ?: return
        val ticket = identity
        val revision = ++artistRevision
        val nextOffset = if (reset) 0 else artistOffset
        artistJob?.cancel()
        mutable.value = mutable.value.copy(artistLoading = reset, artistLoadingMore = !reset, artistNotice = null)
        artistJob = viewModelScope.launch {
            try {
                val detail = withContext(ioDispatcher) { connection.api.artist(selected.name, nextOffset) }
                if (!valid(ticket, connection) || revision != artistRevision || mutable.value.selectedArtist?.name != selected.name) return@launch
                artistOffset = detail.nextOffset
                mutable.value = mutable.value.copy(selectedArtist = detail.artist,
                    artistSongs = (if (reset) detail.songs else mutable.value.artistSongs + detail.songs).distinctBy { it.id },
                    artistLoading = false, artistLoadingMore = false, artistHasMore = detail.hasMore,
                    artistNotice = if (detail.capped) ClientNotice.PAGE_LIMIT else null)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == artistRevision) mutable.value = mutable.value.copy(
                    artistLoading = false, artistLoadingMore = false, artistNotice = notice(error))
            }
        }
    }
    override fun loadMoreArtistAlbums() {
        if (mutable.value.artistAlbumsHasMore && !mutable.value.artistAlbumsLoading && !mutable.value.artistAlbumsLoadingMore) loadArtistAlbums(false)
    }
    private fun loadArtistAlbums(reset: Boolean) {
        val connection = current() ?: return
        val selected = mutable.value.selectedArtist ?: return
        val ticket = identity
        val revision = ++artistAlbumsRevision
        val nextOffset = if (reset) 0 else artistAlbumsOffset
        artistAlbumsJob?.cancel()
        mutable.value = mutable.value.copy(artistAlbumsLoading = reset, artistAlbumsLoadingMore = !reset, artistAlbumsNotice = null)
        artistAlbumsJob = viewModelScope.launch {
            try {
                val page = withContext(ioDispatcher) { connection.api.browseAlbums(offset = nextOffset, artist = selected.name) }
                if (!valid(ticket, connection) || revision != artistAlbumsRevision || mutable.value.selectedArtist?.name != selected.name) return@launch
                artistAlbumsOffset = page.nextOffset
                mutable.value = mutable.value.copy(artistAlbums = (if (reset) page.albums else mutable.value.artistAlbums + page.albums).distinctBy { it.id },
                    artistAlbumsLoading = false, artistAlbumsLoadingMore = false, artistAlbumsHasMore = page.hasMore,
                    artistAlbumsNotice = if (page.capped) ClientNotice.PAGE_LIMIT else null)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == artistAlbumsRevision) mutable.value = mutable.value.copy(
                    artistAlbumsLoading = false, artistAlbumsLoadingMore = false, artistAlbumsNotice = notice(error))
            }
        }
    }
    override fun refreshPlayStats() {
        val connection = current() ?: return
        val ticket = identity
        val revision = ++statsRevision
        statsJob?.cancel()
        mutable.value = mutable.value.copy(statsLoading = true, statsNotice = null)
        statsJob = viewModelScope.launch {
            try {
                val stats = withContext(ioDispatcher) { connection.api.playStats(limit = 50) }
                if (valid(ticket, connection) && revision == statsRevision)
                    mutable.value = mutable.value.copy(playStats = stats, statsLoading = false)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == statsRevision)
                    mutable.value = mutable.value.copy(statsLoading = false, statsNotice = notice(error))
            }
        }
    }

    private data class LibraryUpdate(
        val summary: PlaylistSummary? = null,
        val detail: PlaylistDetail? = null,
        val shelf: PlaylistShelf? = null,
        val deletedId: String? = null,
        val results: List<PlaylistTargetResult> = emptyList(),
        val complete: Boolean = true,
        val refreshRemote: Boolean = true,
    )

    override fun dismissLibraryMutationNotice() {
        if (mutable.value.libraryMutationBusy) return
        pendingCreate = null
        mutable.value = mutable.value.copy(libraryMutationNotice = null,
            libraryMutationConflict = false, libraryMutationResults = emptyList())
    }

    private fun mutateLibrary(operation: suspend (ActiveConnection) -> LibraryUpdate) {
        val connection = current() ?: return
        if (mutable.value.libraryMutationBusy) return
        val ticket = identity
        mutable.value = mutable.value.copy(libraryMutationBusy = true, libraryMutationNotice = null,
            libraryMutationConflict = false, libraryMutationResults = emptyList())
        mutationJob = viewModelScope.launch {
            try {
                val result = withContext(ioDispatcher) { operation(connection) }
                if (!valid(ticket, connection)) return@launch
                result.summary?.let(::mergePlaylist)
                result.detail?.let(::publishPlaylist)
                result.shelf?.let { mutable.value = mutable.value.copy(playlistShelf = it) }
                if (result.deletedId != null) {
                    if (mutable.value.selectedPlaylist?.id == result.deletedId) closePlaylist()
                    mutable.value = mutable.value.copy(playlists = mutable.value.playlists.filterNot { it.id == result.deletedId })
                }
                if (result.complete) pendingCreate = null
                mutable.value = mutable.value.copy(libraryMutationBusy = false,
                    libraryMutationNotice = if (result.complete) null else result.results.firstNotNullOfOrNull { limitNotice(it.error) } ?: ClientNotice.REQUEST_FAILED,
                    libraryMutationResults = result.results,
                    libraryMutationConflict = result.results.any { it.error == "REVISION_CONFLICT" },
                    libraryMutationVersion = mutable.value.libraryMutationVersion + if (result.complete) 1 else 0)
                if (result.refreshRemote) {
                    refreshPlaylists()
                    mutable.value.selectedPlaylist?.let(::openPlaylist)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (!valid(ticket, connection)) return@launch
                val conflict = (error as? ApiFailure)?.httpStatus == 409 && (error as? ApiFailure)?.code in setOf("REVISION_CONFLICT", "revision_conflict")
                (error as? ApiFailure)?.details?.let { details ->
                    details.playlist?.let(::mergePlaylist)
                    details.shelf?.let { mutable.value = mutable.value.copy(playlistShelf = it) }
                }
                mutable.value = mutable.value.copy(libraryMutationBusy = false,
                    libraryMutationNotice = notice(error), libraryMutationConflict = conflict)
                if (conflict) {
                    refreshPlaylists()
                    mutable.value.selectedPlaylist?.let(::openPlaylist)
                }
            }
        }
    }

    private fun mergePlaylist(playlist: PlaylistSummary) {
        val snapshot = mutable.value
        val exists = snapshot.playlists.any { it.id == playlist.id }
        mutable.value = snapshot.copy(playlists = if (exists)
            snapshot.playlists.map { if (it.id == playlist.id) playlist else it } else snapshot.playlists + playlist,
            selectedPlaylist = if (snapshot.selectedPlaylist?.id == playlist.id) playlist else snapshot.selectedPlaylist)
    }

    private fun publishPlaylist(detail: PlaylistDetail) {
        mergePlaylist(detail.playlist)
        if (mutable.value.selectedPlaylist?.id == detail.playlist.id) mutable.value = mutable.value.copy(
            selectedPlaylistEntries = detail.entries, playlistSongs = detail.songs,
            playlistLoading = false, playlistNotice = null)
        if (detail.playlist.kind == "favorite") publishFavorites(detail)
    }

    private fun PlaylistMutationResult.update() = LibraryUpdate(playlist, detail, shelf,
        complete = outcome == "applied" || outcome == "noop")

    override fun createPlaylist(name: String, description: String, songIds: List<String>) {
        if (current() == null || mutable.value.libraryMutationBusy) return
        val payload = Triple(name.trim(), description.trim(), songIds.distinct().toList())
        if (pendingCreate?.first != payload) pendingCreate = payload to java.util.UUID.randomUUID().toString()
        val key = pendingCreate!!.second
        mutateLibrary { connection ->
            val result = connection.api.createPlaylist(payload.first, payload.second, payload.third, key).update()
            result
        }
    }

    override fun updatePlaylist(playlist: PlaylistSummary, name: String, description: String) {
        if (playlist.kind == "favorite") return
        mutateLibrary { it.api.updatePlaylist(playlist.id, playlist.revision, name, description).update() }
    }

    override fun deletePlaylist(playlist: PlaylistSummary) {
        if (playlist.kind == "favorite") return
        mutateLibrary {
            val result = it.api.deletePlaylist(playlist.id, playlist.revision)
            LibraryUpdate(shelf = result.shelf, deletedId = result.deletedPlaylistId,
                complete = result.outcome in setOf("applied", "noop"))
        }
    }

    override fun toggleFavorite(song: Song) {
        val remove = song.id in mutable.value.favoriteSongIds
        val known = mutable.value.playlists.find { it.kind == "favorite" }
        mutateLibrary {
            val favorite = known ?: it.api.playlists().firstOrNull { row -> row.kind == "favorite" }
                ?: throw ApiFailure(Failure.INVALID_RESPONSE)
            if (remove) it.api.removePlaylistSong(favorite.id, song.id, favorite.revision).update()
            else {
                val result = it.api.addPlaylistSongs(listOf(PlaylistTarget(favorite.id, favorite.revision)), listOf(song.id))
                LibraryUpdate(results = result.results, complete = result.outcome in setOf("applied", "noop"))
            }
        }
    }

    override fun addPlaylistSongs(playlistIds: List<String>, songIds: List<String>) {
        val ids = playlistIds.distinct()
        val songs = songIds.distinct()
        if (ids.isEmpty() || songs.isEmpty() || songs.size > 100 || ids.size * songs.size > 500) return
        val summaries = mutable.value.playlists.associateBy { it.id }
        val targets = ids.map { id -> summaries[id]?.let { PlaylistTarget(id, it.revision) } ?: return }
        mutateLibrary {
            val result = it.api.addPlaylistSongs(targets, songs)
            LibraryUpdate(results = result.results, complete = result.outcome in setOf("applied", "noop"))
        }
    }

    override fun removePlaylistSongs(songIds: List<String>) {
        val snapshot = mutable.value
        val selected = snapshot.selectedPlaylist ?: return
        if (snapshot.playlistLoading || snapshot.playlistNotice != null) return
        val remove = songIds.toSet()
        val ids = snapshot.selectedPlaylistEntries.map { it.song.id }.filterNot { it in remove }
        mutateLibrary { it.api.replacePlaylistSongs(selected.id, ids, selected.revision).update() }
    }

    override fun replacePlaylistOrder(songIds: List<String>) {
        val snapshot = mutable.value
        val selected = snapshot.selectedPlaylist ?: return
        val currentIds = snapshot.selectedPlaylistEntries.map { it.song.id }
        if (snapshot.playlistLoading || snapshot.playlistNotice != null || snapshot.libraryMutationConflict ||
            songIds.size != currentIds.size || songIds.toSet().size != songIds.size ||
            songIds.toSet() != currentIds.toSet()) return
        mutateLibrary { it.api.replacePlaylistSongs(selected.id, songIds.toList(), selected.revision).update() }
    }

    override fun movePlaylistShelf(fromIndex: Int, toIndex: Int) {
        val shelf = mutable.value.playlistShelf ?: return
        if (fromIndex !in shelf.items.indices || toIndex !in shelf.items.indices || fromIndex == toIndex) return
        val items = shelf.items.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        mutateLibrary {
            val result = it.api.updatePlaylistShelf(items, shelf.revision)
            LibraryUpdate(shelf = result.shelf, complete = result.outcome in setOf("applied", "noop"))
        }
    }



    private suspend fun preparePlaybackSongs(connection: ActiveConnection, requested: List<Song>): List<Pair<Int, Song>> {
        val snapshot = mutable.value
        // Caller metadata may outlive a previous account's composed UI. Only this session's
        // owned state supplies live URLs; every other ID is resolved by its active API.
        val known = (snapshot.songs + snapshot.albumSongs + snapshot.favoriteSongs + snapshot.playlistSongs +
            snapshot.artistSongs + snapshot.discoverySongs + snapshot.spotlightArtist?.songs.orEmpty() +
            snapshot.playback.queue + snapshot.playStats?.songs.orEmpty().map { it.song }).filter { it.canPlay }.associateBy { it.id }
        val missing = requested.map { it.id }.filterNot { it in known }
        val fresh = if (missing.isEmpty()) emptyMap() else resolveStoredSongs(connection, missing).associateBy { it.id }
        return requested.mapIndexedNotNull { index, song ->
            (known[song.id] ?: fresh[song.id])?.takeIf { it.canPlay }?.let { index to it }
        }
    }

    override fun playSongs(songs: List<Song>, startIndex: Int, shuffle: Boolean) {
        val connection = current() ?: return
        if (startIndex !in songs.indices) return
        val captured = songs.toList()
        val ticket = identity
        val revision = beginPlaybackCommand()
        playJob?.cancel()
        playJob = viewModelScope.launch {
            try {
                val indexed = preparePlaybackSongs(connection, captured)
                val target = indexed.indexOfFirst { it.first == startIndex }
                if (!valid(ticket, connection) || revision != playRevision || target < 0) return@launch
                rememberSearchEntity(SearchEntity.fromSong(indexed[target].second))
                connectPlayer(connection)
                if (valid(ticket, connection) && revision == playRevision) player.play(indexed.map { it.second }, target, shuffle)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == playRevision)
                    mutable.value = mutable.value.copy(notice = notice(error))
            }
        }
    }

    override fun playPlaylist(playlist: PlaylistSummary, shuffle: Boolean) {
        val connection = current() ?: return
        val ticket = identity
        val revision = beginPlaybackCommand()
        playJob?.cancel()
        playJob = viewModelScope.launch {
            try {
                val detail = withContext(ioDispatcher) { connection.api.playlist(playlist.id) }
                if (!valid(ticket, connection) || revision != playRevision) return@launch
                publishPlaylist(detail)
                if (detail.songs.isEmpty()) return@launch
                connectPlayer(connection)
                if (valid(ticket, connection) && revision == playRevision)
                    player.play(detail.songs, 0, shuffle)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == playRevision)
                    mutable.value = mutable.value.copy(playlistNotice = notice(error))
            }
        }
    }

    override fun playNext(song: Song) = appendToQueue(listOf(song), true)
    override fun enqueue(songs: List<Song>) = appendToQueue(songs, false)
    private fun appendToQueue(songs: List<Song>, next: Boolean) {
        val connection = current() ?: return
        if (songs.isEmpty()) return
        val ticket = identity
        val revision = beginPlaybackCommand()
        playJob?.cancel()
        playJob = viewModelScope.launch {
            try {
                val playable = preparePlaybackSongs(connection, songs.toList()).map { it.second }
                if (!valid(ticket, connection) || revision != playRevision || playable.isEmpty()) return@launch
                connectPlayer(connection)
                if (valid(ticket, connection) && revision == playRevision) player.enqueue(playable, next)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == playRevision)
                    mutable.value = mutable.value.copy(queueNotice = notice(error))
            }
        }
    }

    override fun clearQueue() {
        if (current() == null) return
        beginPlaybackCommand(); playJob?.cancel()
        player.clearQueue()
        // Also clear a cold-start saved queue that has not yet reached the Player.
        queueRepository?.save(PlaybackSnapshot())
    }

    override fun removeHistory(songId: String) {
        val repository = history ?: return
        mutateLibrary { if (!repository.remove(songId)) throw ApiFailure(Failure.STORAGE); LibraryUpdate(refreshRemote = false) }
    }
    override fun clearHistory() {
        val repository = history ?: return
        mutateLibrary { if (!repository.clearEntries()) throw ApiFailure(Failure.STORAGE); LibraryUpdate(refreshRemote = false) }
    }

    override fun setPlaylistCover(playlist: PlaylistSummary, sourceUri: String) {
        val processor = coverImageProcessor ?: return
        if (playlist.kind == "favorite") return
        val ticket = identity
        mutateLibrary { connection ->
            // Slot CAS belongs to a separate revision domain from playlist metadata.
            val slot = connection.api.imageSlot("playlist", playlist.id)
            currentCoroutineContext().ensureActive()
            val bytes = processor.prepare(sourceUri)
            currentCoroutineContext().ensureActive()
            if (!valid(ticket, connection)) throw CancellationException()
            var draft: UserImage? = null
            var applied = false
            try {
                draft = connection.api.uploadPlaylistCover(bytes)
                currentCoroutineContext().ensureActive()
                if (!valid(ticket, connection)) throw CancellationException()
                val updated = connection.api.setPlaylistCover(playlist.id, draft.id, slot.revision)
                applied = true
                LibraryUpdate(summary = playlist.copy(customCoverUrl = updated.url, coverRevision = updated.revision))
            } finally {
                if (!applied && draft != null) withContext(NonCancellable) {
                    // Referenced images are rejected by the host, including an uncertain successful CAS.
                    runCatching { connection.api.discardUserImage(draft.id) }
                }
            }
        }
    }

    override fun clearPlaylistCover(playlist: PlaylistSummary) {
        if (playlist.kind == "favorite") return
        mutateLibrary {
            val slot = it.api.imageSlot("playlist", playlist.id)
            val updated = it.api.setPlaylistCover(playlist.id, null, slot.revision)
            LibraryUpdate(summary = playlist.copy(customCoverUrl = updated.url, coverRevision = updated.revision))
        }
    }

    override fun removeQueueItem(index: Int) {
        if (current() == null || index !in player.state.value.queue.indices) return
        beginPlaybackCommand(); playJob?.cancel()
        player.removeQueueItem(index)
    }

    override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val indices = player.state.value.queue.indices
        if (current() == null || fromIndex !in indices || toIndex !in indices || fromIndex == toIndex) return
        beginPlaybackCommand(); playJob?.cancel()
        player.moveQueueItem(fromIndex, toIndex)
    }
    override fun toggleTranslation() = setTranslationEnabled(!translationEnabled)

    override fun setTranslationEnabled(enabled: Boolean) {
        if (current() == null) return
        try {
            translationPreferences?.write(enabled)
            translationEnabled = enabled
            mutable.value = mutable.value.copy(translationEnabled = enabled, translationNotice = null)
        } catch (_: Exception) {
            mutable.value = mutable.value.copy(translationNotice = ClientNotice.STORAGE_FAILED)
        }
    }
    override fun retryLyrics() = lyrics.retry()
    override fun requestLyricTranslation() = lyrics.requestTranslation()
    override fun setVolume(volume: Float) {
        if (!volume.isFinite()) return
        val gain = volume.coerceIn(0f, 1f)
        preferredVolume = gain
        player.setVolume(gain)
        mutable.value = mutable.value.copy(playback = mutable.value.playback.copy(volume = gain))
        val revision = ++volumeSaveRevision
        volumeSaveJob?.cancel()
        volumeSaveJob = viewModelScope.launch {
            delay(180)
            try {
                withContext(ioDispatcher) { volumeMutex.withLock {
                    if (revision == volumeSaveRevision) translationPreferences?.writeVolume(gain)
                } }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (revision == volumeSaveRevision && current() != null)
                    mutable.value = mutable.value.copy(notice = ClientNotice.STORAGE_FAILED)
            }
        }
    }
    override fun togglePlay() = player.togglePlay()
    override fun next() { beginPlaybackCommand(); playJob?.cancel(); player.next() }
    override fun previous() { beginPlaybackCommand(); playJob?.cancel(); player.previous() }
    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    override fun toggleShuffle() = player.toggleShuffle()
    override fun cycleRepeat() = player.cycleRepeat()
    override fun selectQueueItem(index: Int) { beginPlaybackCommand(); playJob?.cancel(); player.select(index) }
    override fun retryPlayback() = player.retry()
    fun setUiVisible(visible: Boolean) {
        player.setUiVisible(visible)
        if (visible && current() != null && discoveryCheckedDate != today()) loadDiscovery(false)
    }
    override fun onCleared() { lyrics.clear(); player.release() }

    private companion object {
        val languages = setOf("all", "zh", "en", "ja", "ko", "instrumental", "ru", "es", "fr", "de",
            "sv", "vi", "yue", "it", "th", "pt", "other")
    }
}

private fun limitNotice(code: String?): ClientNotice? = when (code) {
    "PLAYLIST_LIMIT_REACHED" -> ClientNotice.PLAYLIST_LIMIT
    "PLAYLIST_SONG_LIMIT_REACHED" -> ClientNotice.PLAYLIST_SONG_LIMIT
    "image_upload_limit" -> ClientNotice.IMAGE_UPLOAD_LIMIT
    "image_too_large" -> ClientNotice.IMAGE_TOO_LARGE
    else -> null
}

private fun notice(error: Exception): ClientNotice {
    limitNotice((error as? ApiFailure)?.code)?.let { return it }
    return when ((error as? ApiFailure)?.failure) {
        Failure.INVALID_URL -> ClientNotice.INVALID_URL
        Failure.NOT_READY -> ClientNotice.NOT_READY
        Failure.NETWORK -> ClientNotice.NETWORK
        Failure.INVALID_LOGIN -> ClientNotice.INVALID_LOGIN
        Failure.SESSION_EXPIRED, Failure.RETIRED -> ClientNotice.SESSION_EXPIRED
        Failure.RATE_LIMITED -> ClientNotice.RATE_LIMITED
        Failure.INVALID_RESPONSE -> ClientNotice.INVALID_RESPONSE
        Failure.STORAGE -> ClientNotice.STORAGE_FAILED
        Failure.FORBIDDEN -> ClientNotice.FORBIDDEN
        else -> ClientNotice.REQUEST_FAILED
    }
}
