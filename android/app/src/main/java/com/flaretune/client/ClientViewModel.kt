package com.flaretune.client

import androidx.lifecycle.SavedStateHandle
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
    savedStateHandle: SavedStateHandle? = null,
    private val systemMediaFavorites: SystemMediaFavoriteChanges? = null,
) : ViewModel(), ClientActions {
    private val navigation = ClientNavigationState(savedStateHandle)
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
    // Per-connection monotonic facts: UI list metadata may arrive out of order.
    private var favoriteOwner: ActiveConnection? = null
    private var knownFavoriteSummary: PlaylistSummary? = null
    private var knownFavoriteDetail: PlaylistDetail? = null
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
    private var homeSnapshotEpoch = 0L
    private var homeContinueLocked = false
    private var homeHistoryPreviewLocked = false
    private var recentStatsRevision = 0L
    private var recentStatsJob: Job? = null
    private var recentPlayStats: PlayStats? = null
    private var recentDayPlayStats: PlayStats? = null
    private var continueSearchSongs: List<Song> = emptyList()
    private var continueSearchReady = searchHistoryStore == null
    private var continueSearchRevision = 0L
    private var continueSearchJob: Job? = null
    private var recentStatsReady = false
    private var allStatsReady = false
    private var favoritesReadyForContinue = false
    private var searchHistorySnapshotLocked = false

    private enum class SearchSnapshotMode { LOAD_AND_LOCK, RECORD_ONLY, APPLY_TO_VISIBLE }

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
        artistAlbumsRevision++; artistAlbumsJob?.cancel()
        statsRevision++; statsJob?.cancel(); recentStatsRevision++; recentStatsJob?.cancel()
        continueSearchRevision++; continueSearchJob?.cancel()
        albumSearchRevision++; albumSearchJob?.cancel()
        discoveryRevision++; discoveryJob?.cancel(); discoveryCheckedDate = null; seenSpotlightArtists.clear()
    }

    private fun resetPrivateData(clearHistory: Boolean = true) {
        navigation.clear()
        favoriteOwner = null
        knownFavoriteSummary = null
        knownFavoriteDetail = null
        homeSnapshotEpoch++
        homeContinueLocked = false
        homeHistoryPreviewLocked = false
        recentPlayStats = null
        recentDayPlayStats = null
        continueSearchSongs = emptyList()
        continueSearchReady = searchHistoryStore == null
        recentStatsReady = false
        allStatsReady = false
        favoritesReadyForContinue = false
        searchHistorySnapshotLocked = false
        cancelRequests()
        lyrics.clear()
        queueRestoreNotice = null
        if (clearHistory) { history?.clear(); queueRepository?.clear() }
        historyEntries = emptyList()
        historySongs.clear()
        historyResolved.clear()
    }

    init {
        viewModelScope.launch { state.collect { saveNavigationState() } }
        systemMediaFavorites?.let { changes -> viewModelScope.launch {
            changes.updates.collect { update ->
                if (current() !== update.connection) return@collect
                if (update.detail.playlist.kind == "favorite")
                    publishPlaylist(update.detail, notifySystem = false)
            }
        } }
        viewModelScope.launch {
            player.state.collect { snapshot ->
                if (snapshot.playing) homeHistoryPreviewLocked = true
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
                if (connection == null) { navigation.clear(); mutable.value = mutable.value.copy(screen = ClientScreen.CONNECT) }
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
        favoriteOwner = connection
        knownFavoriteSummary = null
        knownFavoriteDetail = null
        val restoredNavigation = if (connection.auth.mustChangePassword) { navigation.clear(); null } else navigation.restore(connection)
        val baseState = ClientUiState(
            screen = if (connection.auth.mustChangePassword) ClientScreen.CHANGE_PASSWORD else ClientScreen.LIBRARY,
            instanceUrl = connection.network.address.origin, username = connection.auth.account.username,
            accountName = connection.auth.account.displayName, translationEnabled = translationEnabled,
            navigationIdentity = ClientNavigationState.identity(connection), playbackReady = false)
        mutable.value = restoredNavigation?.applyTo(baseState) ?: baseState
        if (connection.auth.mustChangePassword) return
        try { connectPlayer(connection) } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (valid(ticket, connection)) mutable.value = mutable.value.copy(notice = ClientNotice.PLAYBACK_FAILED)
        }
        if (valid(ticket, connection)) {
            beginHomeSnapshotRefresh(unlockHistoryPreview = false)
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
            mutable.value = mutable.value.copy(playbackReady = true, playback = player.state.value)
            lyrics.activate(connection, player.state.value.current)
            lyrics.updatePosition(player.state.value.positionMs)
            refreshCatalogAndPlaylists()
            if (restoredNavigation?.hasDetail == true) { load(true); loadAlbums(true); refreshPlaylists() }
            if (restoredNavigation != null && mutable.value.destination == MainDestination.SEARCH) {
                refreshArtists(mutable.value.query); searchAlbums(mutable.value.query)
            }
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
        if (normalized.isNotBlank()) updateSearchHistory(SearchSnapshotMode.RECORD_ONLY) { store, key -> store.addTerm(key, normalized) }
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
        val previousDestination = mutable.value.destination
        closeAlbum()
        closePlaylist()
        closeArtist()
        mutable.value = mutable.value.copy(destination = destination)
        when (destination) {
            MainDestination.HOME -> {
                val leftSearch = previousDestination == MainDestination.SEARCH
                if (mutable.value.tab != LibraryTab.CATALOG) selectTab(LibraryTab.CATALOG)
                else if (mutable.value.query.isNotBlank()) search("")
                else if (leftSearch) load(true)
                refreshFavorites()
            }
            MainDestination.DISCOVER -> {
                val leftSearch = previousDestination == MainDestination.SEARCH
                val targetLanguage = mutable.value.discoverLanguage
                val syncLang = mutable.value.songLanguage != targetLanguage || mutable.value.albumLanguage != targetLanguage
                if (syncLang) {
                    mutable.value = mutable.value.copy(songLanguage = targetLanguage, albumLanguage = targetLanguage)
                }
                val clearedSearch = mutable.value.tab == LibraryTab.CATALOG && mutable.value.query.isNotBlank()
                if (mutable.value.tab != LibraryTab.CATALOG) selectTab(LibraryTab.CATALOG)
                else if (clearedSearch) search("")
                else if (leftSearch || syncLang || (mutable.value.songs.isEmpty() && !mutable.value.loading)) load(true)
                if (!clearedSearch) {
                    if (mutable.value.artistQuery.isNotBlank()) refreshArtists("")
                    else if (leftSearch || (mutable.value.artists.isEmpty() && !mutable.value.artistsLoading)) loadArtists(true)
                }
                if (leftSearch || syncLang || (mutable.value.albums.isEmpty() && !mutable.value.albumsLoading)) loadAlbums(true)
            }
            MainDestination.SEARCH -> {
                if (mutable.value.tab != LibraryTab.CATALOG) selectTab(LibraryTab.CATALOG)
            }
            MainDestination.LIBRARY -> {
                if (mutable.value.query.isNotBlank()) search("")
                refreshPlaylists()
            }
        }
    }

    private fun beginHomeSnapshotRefresh(unlockHistoryPreview: Boolean) {
        homeSnapshotEpoch++
        homeContinueLocked = false
        recentPlayStats = null
        recentDayPlayStats = null
        continueSearchSongs = emptyList()
        continueSearchReady = searchHistoryStore == null
        recentStatsReady = false
        allStatsReady = false
        favoritesReadyForContinue = false
        if (unlockHistoryPreview) {
            homeHistoryPreviewLocked = false
            mutable.value.playHistory.firstOrNull()?.let { latest ->
                mutable.value = mutable.value.copy(homeHistoryPreviewSong = latest)
                homeHistoryPreviewLocked = true
            }
        }
    }

    private fun maybeUpdateContinueListeningSnapshot(epoch: Long) {
        if (epoch != homeSnapshotEpoch || homeContinueLocked) return
        val snapshot = mutable.value
        val allDone = allStatsReady && recentStatsReady && favoritesReadyForContinue && continueSearchReady
        if (!allDone) return
        val mixed = buildContinueListeningMix(
            recentStats = recentPlayStats?.songs.orEmpty(),
            allTimeStats = snapshot.playStats?.songs.orEmpty(),
            favorites = snapshot.favoriteSongs,
            recentSearchSongs = continueSearchSongs,
            recentDayStats = recentDayPlayStats?.songs.orEmpty(),
        )
        if (mixed.isNotEmpty() || allDone) {
            mutable.value = snapshot.copy(continueListeningSongs = mixed)
        }
        if (allDone) homeContinueLocked = true
    }

    override fun refresh() {
        mutable.value.selectedArtist?.let { openArtist(it); return }
        mutable.value.selectedPlaylist?.let { openPlaylist(it); return }
        mutable.value.selectedAlbum?.let { openAlbum(it); return }
        beginHomeSnapshotRefresh(unlockHistoryPreview = true)
        searchHistorySnapshotLocked = false
        updateSearchHistory(SearchSnapshotMode.LOAD_AND_LOCK) { store, key -> store.load(key) }
        refreshContinueSearchSongs()
        refreshCatalogAndPlaylists()
        refreshPlayStats()
        refreshRecentPlayStats()
    }

    private fun refreshCatalogAndPlaylists() {
        mutable.value.selectedArtist?.let { openArtist(it); return }
        mutable.value.selectedPlaylist?.let { openPlaylist(it); return }
        mutable.value.selectedAlbum?.let { openAlbum(it); return }
        if (mutable.value.tab == LibraryTab.ALBUMS) loadAlbums(true)
        else {
            load(true)
            if (mutable.value.destination == MainDestination.HOME || mutable.value.destination == MainDestination.DISCOVER) loadAlbums(true)
            if (mutable.value.destination == MainDestination.DISCOVER) loadArtists(true)
        }
        refreshPlaylists()
    }

    override fun selectDiscoverLanguage(language: String) {
        if (language !in languages || (
                language == mutable.value.discoverLanguage &&
                    mutable.value.songLanguage == language &&
                    mutable.value.albumLanguage == language
                )
        ) return
        requestRevision++; requestJob?.cancel(); offset = 0
        albumRevision++; albumJob?.cancel(); albumOffset = 0
        artistsRevision++; artistsJob?.cancel(); artistsOffset = 0
        mutable.value = mutable.value.copy(
            discoverLanguage = language,
            songLanguage = language,
            albumLanguage = language,
            songs = emptyList(), hasMore = false, loading = false, loadingMore = false, notice = null,
            albums = emptyList(), albumsHasMore = false, albumsLoading = false, albumsLoadingMore = false, albumsNotice = null,
            artists = emptyList(), artistsHasMore = false, artistsLoading = false, artistsLoadingMore = false, artistsNotice = null,
        )
        load(true)
        loadAlbums(true)
        loadArtists(true)
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
        if (!validYouTubeAlbumReference(album.id) && !validLocalAlbumReference(album.id)) return
        val canonical = (mutable.value.albums + mutable.value.searchAlbums + mutable.value.artistAlbums).find { it.id == album.id }
            ?: mutable.value.selectedAlbum?.takeIf { it.id == album.id } ?: album
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
                if (detail.album.id != canonical.id) throw ApiFailure(Failure.INVALID_RESPONSE)
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
        val language = if (snapshot.destination == MainDestination.SEARCH && tab == LibraryTab.CATALOG) "all" else snapshot.songLanguage
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
                val retainedItems = retainFavoriteSummary(items)
                val selected = mutable.value.selectedPlaylist
                val updated = selected?.let { item -> retainedItems.find { it.id == item.id } }?.let { item ->
                    // A summary never supplies the complete editable membership at its new revision.
                    if (item.kind == "favorite" && knownFavoriteDetail?.playlist?.revision != item.revision && selected?.kind == "favorite") selected else item
                }
                if (selected != null && updated == null) closePlaylist()
                mutable.value = mutable.value.copy(playlists = retainedItems, playlistShelf = shelf ?: mutable.value.playlistShelf,
                    playlistsLoading = false, selectedPlaylist = updated, playlistsNotice = null)
                refreshFavorites()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == playlistsRevision) {
                    mutable.value = mutable.value.copy(playlistsLoading = false, playlistsNotice = notice(error))
                    refreshFavorites()
                }
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

    private fun rememberFavoriteSummary(summary: PlaylistSummary): Boolean {
        if (current() !== favoriteOwner || favoriteOwner == null || summary.kind != "favorite") return false
        val known = knownFavoriteSummary
        if (known != null && (summary.id != known.id || summary.revision < known.revision)) return false
        knownFavoriteSummary = summary
        return true
    }

    private fun currentFavoriteDetail(): PlaylistDetail? = knownFavoriteDetail?.takeIf {
        it.playlist.id == knownFavoriteSummary?.id && it.playlist.revision >= (knownFavoriteSummary?.revision ?: -1L)
    }

    private fun retainFavoriteSummary(items: List<PlaylistSummary>): List<PlaylistSummary> {
        items.firstOrNull { it.kind == "favorite" }?.let(::rememberFavoriteSummary)
        val known = knownFavoriteSummary ?: return items
        val result = items.mapNotNull { item -> when {
            item.id == known.id -> known
            item.kind == "favorite" -> null
            else -> item
        } }.toMutableList()
        if (result.none { it.id == known.id }) {
            val previous = mutable.value.playlists.indexOfFirst { it.id == known.id }.coerceAtLeast(0)
            result.add(previous.coerceAtMost(result.size), known)
        }
        return result
    }

    /** One monotonic publication gate for GETs, writes and system acknowledgements. */
    private fun publishFavorites(detail: PlaylistDetail?, notifySystem: Boolean = true): PlaylistDetail? {
        val accepted = detail != null && rememberFavoriteSummary(detail.playlist)
        if (!accepted && knownFavoriteSummary != null) {
            val retained = currentFavoriteDetail()
            val snapshot = mutable.value
            val selectedFavorite = snapshot.selectedPlaylist?.id == knownFavoriteSummary?.id
            mutable.value = snapshot.copy(favoritesLoading = false,
                favoritesNotice = if (retained == null) ClientNotice.REQUEST_FAILED else null,
                selectedPlaylist = if (selectedFavorite && retained != null) retained.playlist else snapshot.selectedPlaylist,
                selectedPlaylistEntries = if (selectedFavorite && retained != null) retained.entries else snapshot.selectedPlaylistEntries,
                playlistSongs = if (selectedFavorite && retained != null) retained.songs else snapshot.playlistSongs,
                playlistLoading = if (selectedFavorite) false else snapshot.playlistLoading,
                playlistNotice = if (selectedFavorite) { if (retained == null) ClientNotice.REQUEST_FAILED else null } else snapshot.playlistNotice)
            return retained
        }
        if (detail != null && !accepted) return null
        if (detail != null) { knownFavoriteDetail = detail; mergePlaylist(detail.playlist) }
        val latest = detail?.entries?.maxWithOrNull(compareBy<PlaylistSong> { it.addedAt }.thenBy { it.sortOrder })
        val snapshot = mutable.value
        val selectedFavorite = detail != null && snapshot.selectedPlaylist?.id == detail.playlist.id
        mutable.value = snapshot.copy(favoriteSongs = detail?.songs.orEmpty(),
            favoriteSongIds = detail?.entries?.map { it.song.id }?.toSet().orEmpty(),
            latestFavoriteCoverUrl = latest?.song?.coverUrl, favoritesLoading = false, favoritesNotice = null,
            selectedPlaylist = if (selectedFavorite) detail!!.playlist else snapshot.selectedPlaylist,
            selectedPlaylistEntries = if (selectedFavorite) detail!!.entries else snapshot.selectedPlaylistEntries,
            playlistSongs = if (selectedFavorite) detail!!.songs else snapshot.playlistSongs,
            playlistLoading = if (selectedFavorite) false else snapshot.playlistLoading,
            playlistNotice = if (selectedFavorite) null else snapshot.playlistNotice)
        if (notifySystem && detail != null) current()?.let { systemMediaFavorites?.publish(it, detail) }
        favoritesReadyForContinue = true
        maybeUpdateContinueListeningSnapshot(homeSnapshotEpoch)
        return detail
    }

    private suspend fun favoriteForPlayback(connection: ActiveConnection, detail: PlaylistDetail?,
        ticket: Long, revision: Long): PlaylistDetail? {
        var complete = publishFavorites(detail)
        if (complete == null && knownFavoriteSummary != null) {
            val retry = withContext(ioDispatcher) { readFavorites(connection, knownFavoriteSummary) }
            if (!valid(ticket, connection) || revision != playRevision) return null
            complete = publishFavorites(retry)
            if (complete == null) throw ApiFailure(Failure.REQUEST)
        }
        return complete
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
                    val complete = favoriteForPlayback(connection, detail, ticket, revision)
                    if (!complete?.songs.isNullOrEmpty()) {
                        connectPlayer(connection)
                        if (valid(ticket, connection) && revision == playRevision) {
                            val latest = currentFavoriteDetail() ?: throw ApiFailure(Failure.REQUEST)
                            if (latest.songs.isNotEmpty()) player.play(latest.songs, 0)
                        }
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
                if (valid(ticket, connection) && revision == favoritesRevision) {
                    mutable.value = mutable.value.copy(favoritesLoading = false, favoritesNotice = notice(error))
                    favoritesReadyForContinue = true
                    maybeUpdateContinueListeningSnapshot(homeSnapshotEpoch)
                }
            }
        }
    }

    private fun readFavorites(connection: ActiveConnection, known: PlaylistSummary?): PlaylistDetail? {
        val favorite = known ?: connection.api.playlists().find { it.kind == "favorite" }
        return favorite?.let { connection.api.playlist(it.id) }
    }

    private fun beginPlaybackCommand(): Long {
        homeHistoryPreviewLocked = true
        queueRestoreRevision++
        queueRestoreJob?.cancel()
        mutable.value = mutable.value.copy(queueRestoring = false)
        return ++playRevision
    }
    private fun renderHistory() {
        val snapshot = mutable.value
        val live = (snapshot.playback.queue + snapshot.songs + snapshot.favoriteSongs +
            snapshot.albumSongs + snapshot.playlistSongs).associateBy { it.id }
        val rendered = historyEntries.map { entry ->
            val stored = entry.toSong()
            (historySongs[entry.id] ?: live[entry.id])?.let { songContext(it, stored, preserveCover = false) } ?: stored
        }
        val preview = if (!homeHistoryPreviewLocked && snapshot.homeHistoryPreviewSong == null) {
            rendered.firstOrNull()
        } else {
            snapshot.homeHistoryPreviewSong
        }
        mutable.value = snapshot.copy(
            playHistoryPlayedAt = historyEntries.associate { it.id to it.playedAt },
            playHistory = rendered,
            homeHistoryPreviewSong = preview,
        )
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
                val contexts = historyEntries.map { it.toSong() }.associateBy { it.id }
                val songs = resolveStoredSongs(connection, ids, contexts)
                if (!valid(ticket, connection)) return@launch
                songs.forEach { historySongs[it.id] = it }
                renderHistory()
                if (!homeHistoryPreviewLocked && mutable.value.playHistory.isNotEmpty()) {
                    mutable.value = mutable.value.copy(homeHistoryPreviewSong = mutable.value.playHistory.firstOrNull())
                    homeHistoryPreviewLocked = true
                }
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
        if (current() == null) return
        val snapshot = mutable.value
        val groups = listOf(snapshot.playlistSongs, snapshot.albumSongs, snapshot.artistSongs,
            snapshot.continueListeningSongs, snapshot.discoverySongs, snapshot.spotlightArtist?.songs.orEmpty(),
            snapshot.playStats?.songs.orEmpty().map { it.song }, snapshot.songs, snapshot.favoriteSongs)
        // A row's current-session list and album edition are stronger evidence than a same-ID cache.
        val songs = groups.firstOrNull { group -> group.any { it === song } }
            ?: groups.firstOrNull { group -> group.any { sameSongContext(it, song) } }
            ?: groups.firstOrNull { group -> group.any { it.id == song.id } }
        if (songs == null) {
            if (snapshot.playHistory.any { it.id == song.id }) playHistorySong(song)
            return
        }
        val index = songs.indexOfFirst { it === song }.takeIf { it >= 0 }
            ?: songs.indexOfFirst { sameSongContext(it, song) }.takeIf { it >= 0 }
            ?: songs.indexOfFirst { it.id == song.id }
        if (index >= 0) playSongs(songs, index)
    }

    override fun playHistorySong(song: Song) {
        val connection = current() ?: return
        val entries = historyEntries.toList()
        val clickedIndex = entries.indexOfFirst { it.id == song.id }.takeIf { it >= 0 } ?: return
        val stored = entries[clickedIndex].toSong()
        val ticket = identity
        val revision = beginPlaybackCommand()
        playJob?.cancel()
        playJob = viewModelScope.launch {
            try {
                // Stored history never supplies an authenticated or temporary playback URL.
                val received = withContext(ioDispatcher) { connection.api.song(stored.id) }
                if (!valid(ticket, connection) || revision != playRevision) return@launch
                if (received.id != stored.id) throw ApiFailure(Failure.INVALID_RESPONSE)
                val freshClicked = songContext(received, stored, preserveCover = false)
                val otherEntries = entries.filterNot { it.id == stored.id }
                val otherResolved = if (otherEntries.isEmpty()) emptyMap() else {
                    try {
                        resolveStoredSongs(
                            connection = connection,
                            ids = otherEntries.map { it.id },
                            contexts = otherEntries.associate { it.id to it.toSong() },
                        ).associateBy { it.id }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        emptyMap()
                    }
                }
                if (!valid(ticket, connection) || revision != playRevision) return@launch
                val queue = entries.mapNotNull { entry ->
                    if (entry.id == stored.id) freshClicked else otherResolved[entry.id]
                }.ifEmpty { listOf(freshClicked) }
                val targetIndex = queue.indexOfFirst { it.id == freshClicked.id }.coerceAtLeast(0)
                connectPlayer(connection)
                if (!valid(ticket, connection) || revision != playRevision) return@launch
                queue.forEach { historySongs[it.id] = it }
                renderHistory()
                player.play(queue, targetIndex)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == playRevision)
                    mutable.value = mutable.value.copy(historyNotice = notice(error))
            }
        }
    }

    private suspend fun resolveStoredSongs(connection: ActiveConnection, ids: List<String>,
        contexts: Map<String, Song> = emptyMap()): List<Song> =
        withContext(ioDispatcher) {
            val eligible = ids.distinct().filter { id ->
                id.isNotBlank() && id.length <= 256 && id == id.trim() && id !in setOf(".", "..") &&
                    id.none { it.code < 32 || it.code == 127 }
            }
            val shortIds = eligible.filter { it.length <= 80 }
            val resolved = shortIds.chunked(500).flatMap { connection.api.resolveSongs(it) }
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
            eligible.mapNotNull { id -> byId[id]?.takeIf { it.canPlay }?.let { fresh ->
                contexts[id]?.let { songContext(fresh, it, preserveCover = false) } ?: fresh
            } }
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
                val indexed = saved.entries.mapIndexedNotNull { index, entry -> songs[entry.id]?.let {
                    index to songContext(it, entry.toSong(), preserveCover = false)
                } }
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
                    mutable.value = mutable.value.copy(queueRestoring = false, playback = player.state.value)
            }
        }
    }





    private suspend fun connectPlayer(connection: ActiveConnection) {
        player.connect(connection)
        if (connection.network.isActive && connections.current === connection) player.setVolume(preferredVolume)
    }

    private fun listeningIdentity(connection: ActiveConnection) =
        HistoryIdentity(connection.network.address.origin, connection.auth.account.id)

    private fun updateSearchHistory(
        mode: SearchSnapshotMode = SearchSnapshotMode.LOAD_AND_LOCK,
        visibleTransform: ((ClientUiState) -> ClientUiState)? = null,
        work: (SearchHistoryStore, HistoryIdentity) -> SearchHistorySnapshot,
    ) {
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
                if (!valid(ticket, connection)) return@launch
                val currentState = mutable.value
                when (mode) {
                    SearchSnapshotMode.LOAD_AND_LOCK -> {
                        mutable.value = currentState.copy(
                            recentSearches = snapshot.entities,
                            recentSearchTerms = snapshot.terms,
                            searchHistoryNotice = null,
                        )
                        searchHistorySnapshotLocked = true
                    }
                    SearchSnapshotMode.RECORD_ONLY -> {
                        if (!searchHistorySnapshotLocked) {
                            mutable.value = currentState.copy(
                                recentSearches = snapshot.entities,
                                recentSearchTerms = snapshot.terms,
                                searchHistoryNotice = null,
                            )
                        } else {
                            mutable.value = currentState.copy(searchHistoryNotice = null)
                        }
                    }
                    SearchSnapshotMode.APPLY_TO_VISIBLE -> {
                        val updated = if (searchHistorySnapshotLocked && visibleTransform != null) {
                            visibleTransform(currentState).copy(searchHistoryNotice = null)
                        } else {
                            currentState.copy(
                                recentSearches = snapshot.entities,
                                recentSearchTerms = snapshot.terms,
                                searchHistoryNotice = null,
                            )
                        }
                        mutable.value = updated
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection)) mutable.value = mutable.value.copy(searchHistoryNotice = notice(error))
            }
        }
    }
    private fun rememberSearchEntity(entry: SearchEntity) {
        if (mutable.value.destination == MainDestination.SEARCH) {
            updateSearchHistory(SearchSnapshotMode.RECORD_ONLY) { store, key -> store.addEntity(key, entry) }
        }
    }
    override fun removeRecentSearch(entry: SearchEntity) = updateSearchHistory(
        mode = SearchSnapshotMode.APPLY_TO_VISIBLE,
        visibleTransform = { s -> s.copy(recentSearches = s.recentSearches.filterNot { com.flaretune.client.data.sameSearchEntity(it, entry) }) },
    ) { store, key -> store.removeEntity(key, entry) }
    override fun clearRecentSearches() = updateSearchHistory(
        mode = SearchSnapshotMode.APPLY_TO_VISIBLE,
        visibleTransform = { s -> s.copy(recentSearches = emptyList()) },
    ) { store, key -> store.clearEntities(key) }
    override fun removeRecentSearchTerm(term: String) = updateSearchHistory(
        mode = SearchSnapshotMode.APPLY_TO_VISIBLE,
        visibleTransform = { s -> s.copy(recentSearchTerms = s.recentSearchTerms.filterNot { it.equals(term.trim(), ignoreCase = true) }) },
    ) { store, key -> store.removeTerm(key, term) }
    override fun clearRecentSearchTerms() = updateSearchHistory(
        mode = SearchSnapshotMode.APPLY_TO_VISIBLE,
        visibleTransform = { s -> s.copy(recentSearchTerms = emptyList()) },
    ) { store, key -> store.clearTerms(key) }
    override fun openRecentSearch(entry: SearchEntity) {
        when (entry.type) {
            "song" -> playSongsInternal(listOf(entry.toSong()), recordSearchEntity = false)
            "artist" -> openArtistInternal(
                Artist(entry.name, id = entry.id.takeIf { it.matches(Regex("yt:artist:UC[A-Za-z0-9_-]{1,126}")) }),
                recordSearchEntity = false,
            )
        }
    }

    private fun loadListeningOverview() {
        updateSearchHistory(SearchSnapshotMode.LOAD_AND_LOCK) { store, key -> store.load(key) }
        refreshContinueSearchSongs()
        loadDiscovery(false)
        refreshPlayStats()
        refreshRecentPlayStats()
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
                        val songs = cached.songs.mapNotNull { entry -> resolved[entry.id]?.let {
                            songContext(it, entry.toSong(), preserveCover = false)
                        } }
                        val spotlight = cached.spotlight?.let { item ->
                            SpotlightArtist(item.artist, item.songCount, item.songs.mapNotNull { entry -> resolved[entry.id]?.let {
                                songContext(it, entry.toSong(), preserveCover = false)
                            } }, coverUrl = item.songs.firstNotNullOfOrNull { resolved[it.id]?.coverUrl })
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
        val language = if (mutable.value.destination == MainDestination.SEARCH && query.isNotBlank()) "all" else mutable.value.discoverLanguage
        val nextOffset = if (reset) 0 else artistsOffset
        artistsJob?.cancel()
        mutable.value = mutable.value.copy(artistsLoading = reset, artistsLoadingMore = !reset, artistsNotice = null)
        artistsJob = viewModelScope.launch {
            try {
                val page = withContext(ioDispatcher) { connection.api.artists(query, nextOffset, language) }
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

    override fun openArtist(artist: Artist) = openArtistInternal(artist, recordSearchEntity = true)

    private fun openArtistInternal(artist: Artist, recordSearchEntity: Boolean) {
        if (recordSearchEntity) rememberSearchEntity(SearchEntity.fromArtist(artist.name, artist.id))
        if (current() == null || artist.name.isBlank()) return
        closeAlbum(); closePlaylist(); closeArtist()
        mutable.value = mutable.value.copy(selectedArtist = artist)
        loadArtist(true)
        if (artist.id == null) loadArtistAlbums(true)
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
                val detail = withContext(ioDispatcher) { connection.api.artist(selected.id ?: selected.name, nextOffset) }
                if (!valid(ticket, connection) || revision != artistRevision || mutable.value.selectedArtist?.name != selected.name ||
                    mutable.value.selectedArtist?.id != selected.id) return@launch
                artistOffset = detail.nextOffset
                mutable.value = mutable.value.copy(selectedArtist = detail.artist,
                    artistSongs = (if (reset) detail.songs else mutable.value.artistSongs + detail.songs).distinctBy { it.id },
                    artistLoading = false, artistLoadingMore = false, artistHasMore = detail.hasMore,
                    artistNotice = if (detail.capped) ClientNotice.PAGE_LIMIT else null,
                    artistAlbums = if (selected.id != null) detail.albums else mutable.value.artistAlbums,
                    artistAlbumsLoading = if (selected.id != null) false else mutable.value.artistAlbumsLoading,
                    artistAlbumsLoadingMore = if (selected.id != null) false else mutable.value.artistAlbumsLoadingMore,
                    artistAlbumsHasMore = if (selected.id != null) false else mutable.value.artistAlbumsHasMore)
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
        if (selected.id != null) return
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
        val epoch = homeSnapshotEpoch
        val revision = ++statsRevision
        statsJob?.cancel()
        mutable.value = mutable.value.copy(statsLoading = true, statsNotice = null)
        statsJob = viewModelScope.launch {
            try {
                val stats = withContext(ioDispatcher) { connection.api.playStats(limit = 50) }
                if (valid(ticket, connection) && revision == statsRevision) {
                    mutable.value = mutable.value.copy(playStats = stats, statsLoading = false)
                    allStatsReady = true
                    maybeUpdateContinueListeningSnapshot(epoch)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == statsRevision) {
                    mutable.value = mutable.value.copy(statsLoading = false, statsNotice = notice(error))
                    allStatsReady = true
                    maybeUpdateContinueListeningSnapshot(epoch)
                }
            }
        }
    }

    private fun refreshContinueSearchSongs() {
        val connection = current() ?: return
        val ticket = identity
        val epoch = homeSnapshotEpoch
        val revision = ++continueSearchRevision
        continueSearchJob?.cancel()
        val store = searchHistoryStore
        if (store == null) {
            continueSearchSongs = emptyList()
            continueSearchReady = true
            maybeUpdateContinueListeningSnapshot(epoch)
            return
        }
        val key = listeningIdentity(connection)
        continueSearchJob = viewModelScope.launch {
            val songs = try {
                // Read disk, not the intentionally locked visible search snapshot.
                val entries = withContext(ioDispatcher) { searchMutex.withLock { store.load(key).entities } }
                    .filter { it.type == "song" }
                val contexts = entries.map { it.toSong() }.associateBy { it.id }
                resolveStoredSongs(connection, entries.map { it.id }, contexts)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                emptyList()
            }
            if (valid(ticket, connection) && revision == continueSearchRevision && epoch == homeSnapshotEpoch) {
                continueSearchSongs = songs
                continueSearchReady = true
                maybeUpdateContinueListeningSnapshot(epoch)
            }
        }
    }

    private fun refreshRecentPlayStats() {
        val connection = current() ?: return
        val ticket = identity
        val epoch = homeSnapshotEpoch
        val revision = ++recentStatsRevision
        recentStatsJob?.cancel()
        recentStatsJob = viewModelScope.launch {
            try {
                val windows = coroutineScope {
                    listOf(7, 1).map { days -> async(ioDispatcher) {
                        try { connection.api.playStats(limit = 50, summaryOnly = true, days = days) }
                        catch (error: Exception) {
                            if (error is CancellationException) throw error
                            null
                        }
                    } }.awaitAll()
                }
                if (valid(ticket, connection) && revision == recentStatsRevision && epoch == homeSnapshotEpoch) {
                    recentPlayStats = windows[0]
                    recentDayPlayStats = windows[1]
                    recentStatsReady = true
                    maybeUpdateContinueListeningSnapshot(epoch)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // An old/failed host contributes no fabricated recent statistics.
                if (valid(ticket, connection) && revision == recentStatsRevision) {
                    recentPlayStats = null
                    recentStatsReady = true
                    maybeUpdateContinueListeningSnapshot(epoch)
                }
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

    private fun mergePlaylist(playlist: PlaylistSummary): Boolean {
        if (playlist.kind == "favorite") {
            if (!rememberFavoriteSummary(playlist)) return false
        } else if (playlist.id == knownFavoriteSummary?.id) return false
        val snapshot = mutable.value
        val exists = snapshot.playlists.any { it.id == playlist.id }
        val completeFavorite = playlist.kind != "favorite" || knownFavoriteDetail?.playlist?.revision == playlist.revision
        mutable.value = snapshot.copy(playlists = if (exists)
            snapshot.playlists.map { if (it.id == playlist.id) playlist else it } else snapshot.playlists + playlist,
            selectedPlaylist = if (snapshot.selectedPlaylist?.id == playlist.id && completeFavorite) playlist else snapshot.selectedPlaylist)
        return true
    }

    private fun publishPlaylist(detail: PlaylistDetail, notifySystem: Boolean = true): PlaylistDetail? {
        if (detail.playlist.kind == "favorite") return publishFavorites(detail, notifySystem)
        if (!mergePlaylist(detail.playlist)) return null
        if (mutable.value.selectedPlaylist?.id == detail.playlist.id) mutable.value = mutable.value.copy(
            selectedPlaylistEntries = detail.entries, playlistSongs = detail.songs,
            playlistLoading = false, playlistNotice = null)
        return detail
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



    private fun sameSongContext(left: Song, right: Song): Boolean = left.id == right.id &&
        left.isYouTube == right.isYouTube && left.albumId == right.albumId && left.title == right.title &&
        left.artist == right.artist && left.album == right.album && left.coverUrl == right.coverUrl

    private fun songContext(fresh: Song, selected: Song, preserveCover: Boolean): Song {
        if (fresh.id != selected.id || fresh.isYouTube != selected.isYouTube) return fresh
        return mergeSongRelations(fresh, selected, preferPrevious = true).copy(
            title = selected.title.ifBlank { fresh.title }, artist = selected.artist.ifBlank { fresh.artist },
            album = selected.album.ifBlank { fresh.album },
            coverUrl = if (preserveCover) selected.coverUrl ?: fresh.coverUrl else fresh.coverUrl)
    }

    private suspend fun preparePlaybackSongs(connection: ActiveConnection, requested: List<Song>): List<Pair<Int, Song>> {
        val snapshot = mutable.value
        // Only account-owned current state may supply the selected context. Caller URLs are never used.
        val owned = snapshot.albumSongs + snapshot.playlistSongs + snapshot.artistSongs + snapshot.songs +
            snapshot.favoriteSongs + snapshot.continueListeningSongs + snapshot.discoverySongs + snapshot.spotlightArtist?.songs.orEmpty() +
            snapshot.playback.queue + snapshot.playStats?.songs.orEmpty().map { it.song } + snapshot.playHistory
        val stored = snapshot.recentSearches.filter { it.type == "song" }.map { it.toSong() } +
            historyEntries.map { it.toSong() }
        val contexts = requested.map { requestedSong ->
            val matches = owned.filter { it.id == requestedSong.id }
            val live = matches.firstOrNull { it === requestedSong }
                ?: matches.firstOrNull { sameSongContext(it, requestedSong) }
                ?: matches.firstOrNull { requestedSong.albumId != null && it.albumId == requestedSong.albumId }
                ?: matches.firstOrNull()
            live?.let { it to true } ?: stored.firstOrNull { sameSongContext(it, requestedSong) }?.let { it to false }
        }
        // Current-session media is trusted; restored or unknown entries must resolve in this connection.
        val missing = requested.mapIndexedNotNull { index, song ->
            song.id.takeUnless { contexts[index]?.let { it.second && it.first.canPlay } == true }
        }
        val fresh = if (missing.isEmpty()) emptyMap() else resolveStoredSongs(connection, missing).associateBy { it.id }
        return requested.mapIndexedNotNull { index, song ->
            val context = contexts[index]
            if (context != null && context.second && context.first.canPlay) index to context.first
            else fresh[song.id]?.let { received ->
                index to if (context == null) received else songContext(received, context.first, context.second)
            }
        }
    }

    override fun playSongs(songs: List<Song>, startIndex: Int, shuffle: Boolean) =
        playSongsInternal(songs, startIndex, shuffle, recordSearchEntity = true)

    private fun playSongsInternal(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false, recordSearchEntity: Boolean) {
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
                if (recordSearchEntity) rememberSearchEntity(SearchEntity.fromSong(indexed[target].second))
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
                val received = withContext(ioDispatcher) { connection.api.playlist(playlist.id) }
                if (!valid(ticket, connection) || revision != playRevision) return@launch
                val detail = if (received.playlist.kind == "favorite")
                    favoriteForPlayback(connection, received, ticket, revision) else publishPlaylist(received)
                if (detail == null || detail.songs.isEmpty()) return@launch
                connectPlayer(connection)
                if (valid(ticket, connection) && revision == playRevision) {
                    val latest = if (received.playlist.kind == "favorite") currentFavoriteDetail() ?: throw ApiFailure(Failure.REQUEST) else detail
                    if (latest.songs.isNotEmpty()) player.play(latest.songs, 0, shuffle)
                }
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
    override fun setPlaybackMode(shuffle: Boolean, repeatMode: Int, currentShuffle: Boolean, currentRepeatMode: Int) =
        player.setPlaybackMode(shuffle, repeatMode)
    override fun selectQueueItem(index: Int) { beginPlaybackCommand(); playJob?.cancel(); player.select(index) }
    override fun retryPlayback() = player.retry()
    fun saveNavigationState() { current()?.let { navigation.save(it, mutable.value) } }

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
