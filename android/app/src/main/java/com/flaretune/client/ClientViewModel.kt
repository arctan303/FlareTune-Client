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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class ClientViewModel(
    private val connections: Connections,
    private val player: PlaybackGateway,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val history: PlaybackHistoryRepository? = null,
    private val queueRepository: PlaybackQueueRepository? = null,
    private val translationPreferences: TranslationPreferences? = null,
) : ViewModel(), ClientActions {
    private var translationEnabled = runCatching { translationPreferences?.read() ?: false }.getOrDefault(false)
    private val mutable = MutableStateFlow(ClientUiState(instanceUrl = connections.lastOrigin, translationEnabled = translationEnabled))
    val state: StateFlow<ClientUiState> = mutable
    val imageLoader: ImageLoader? get() = player.imageLoader
    private val lyrics = LyricsCoordinator(viewModelScope, ioDispatcher)
    private var identity = 0L
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
        try { player.connect(connection) } catch (error: Exception) {
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
        if (normalized != mutable.value.query) {
            offset = 0
            mutable.value = mutable.value.copy(query = normalized, songs = emptyList(), hasMore = false, loadingMore = false)
        }
        load(true)
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
        val canonical = mutable.value.albums.find { it.id == album.id }
            ?: mutable.value.selectedAlbum?.takeIf { it.id == album.id } ?: return
        closePlaylist()
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
                    } else connection.api.songs(query, requestedOffset, language)
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
                val items = withContext(ioDispatcher) { connection.api.playlists() }
                if (!valid(ticket, connection) || revision != playlistsRevision) return@launch
                val selected = mutable.value.selectedPlaylist
                val updated = selected?.let { item -> items.find { it.id == item.id } }
                if (selected != null && updated == null) closePlaylist()
                mutable.value = mutable.value.copy(playlists = items, playlistsLoading = false,
                    selectedPlaylist = updated, playlistsNotice = null)
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
        val ticket = identity
        val revision = ++playlistRevision
        playlistJob?.cancel()
        mutable.value = mutable.value.copy(selectedPlaylist = canonical, playlistSongs = emptyList(),
            playlistLoading = true, playlistNotice = null)
        playlistJob = viewModelScope.launch {
            try {
                val detail = withContext(ioDispatcher) { connection.api.playlist(canonical.id) }
                if (!valid(ticket, connection) || revision != playlistRevision ||
                    mutable.value.selectedPlaylist?.id != canonical.id) return@launch
                mutable.value = mutable.value.copy(selectedPlaylist = detail.playlist,
                    playlistSongs = detail.songs, playlistLoading = false)
                if (detail.playlist.kind == "favorite") publishFavorites(detail)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == playlistRevision)
                    mutable.value = mutable.value.copy(playlistLoading = false, playlistNotice = notice(error))
            }
        }
    }

    override fun closePlaylist() {
        playlistRevision++; playlistJob?.cancel()
        mutable.value = mutable.value.copy(selectedPlaylist = null, playlistSongs = emptyList(),
            playlistLoading = false, playlistNotice = null)
    }

    override fun refreshPlaylist() { mutable.value.selectedPlaylist?.let(::openPlaylist) }
    override fun refreshFavorites() { loadFavorites(false) }
    override fun playFavorites() { loadFavorites(true) }

    private fun publishFavorites(detail: PlaylistDetail?) {
        val latest = detail?.entries?.maxWithOrNull(compareBy<PlaylistSong> { it.addedAt }.thenBy { it.sortOrder })
        mutable.value = mutable.value.copy(favoriteSongs = detail?.songs.orEmpty(),
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
                        player.connect(connection)
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
        mutable.value = snapshot.copy(playHistory = historyEntries.map { entry ->
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
            snapshot.songs.any { it.id == song.id } -> snapshot.songs
            snapshot.favoriteSongs.any { it.id == song.id } -> snapshot.favoriteSongs
            else -> emptyList()
        }
        val index = songs.indexOfFirst { it.id == song.id }
        if (index < 0) {
            if (snapshot.playHistory.any { it.id == song.id }) playHistorySong(song)
            return
        }
        val ticket = identity
        val revision = beginPlaybackCommand()
        playJob?.cancel()
        playJob = viewModelScope.launch {
            try {
                player.connect(connection)
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
                player.connect(connection)
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
                player.connect(connection)
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
    override fun setVolume(volume: Float) { if (volume.isFinite()) player.setVolume(volume.coerceIn(0f, 1f)) }
    override fun togglePlay() = player.togglePlay()
    override fun next() { beginPlaybackCommand(); playJob?.cancel(); player.next() }
    override fun previous() { beginPlaybackCommand(); playJob?.cancel(); player.previous() }
    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    override fun toggleShuffle() = player.toggleShuffle()
    override fun cycleRepeat() = player.cycleRepeat()
    override fun selectQueueItem(index: Int) { beginPlaybackCommand(); playJob?.cancel(); player.select(index) }
    override fun retryPlayback() = player.retry()
    fun setUiVisible(visible: Boolean) = player.setUiVisible(visible)
    override fun onCleared() { lyrics.clear(); player.release() }

    private companion object {
        val languages = setOf("all", "zh", "en", "ja", "ko", "instrumental", "ru", "es", "fr", "de",
            "sv", "vi", "yue", "it", "th", "pt", "other")
    }
}

private fun notice(error: Exception): ClientNotice = when ((error as? ApiFailure)?.failure) {
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
