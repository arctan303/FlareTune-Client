package com.flaretune.client

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.flaretune.client.data.*
import com.flaretune.client.model.*
import com.flaretune.client.playback.PlaybackGateway
import com.flaretune.client.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClientStateTest {
    private val song = Song("one", "First song", audioUrl = "https://music.example/media/one.mp3")
    private val auth = AuthSession(Account("account-one", "listener", "Listener"), "csrf", false)

    @Test fun restoredSessionLoadsSongsAndRefreshFailurePreservesContent() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val api = FakeAccountApi(listOf(song))
        val connections = FakeConnections(active(api))
        val player = FakePlayback()
        val model = ClientViewModel(connections, player, dispatcher)
        try {
            advanceUntilIdle()
            assertEquals(ClientScreen.LIBRARY, model.state.value.screen)
            assertEquals(listOf(song), model.state.value.songs)
            api.fail = true
            model.refresh()
            advanceUntilIdle()
            assertEquals(listOf(song), model.state.value.songs)
            assertEquals(ClientNotice.NETWORK, model.state.value.notice)
            api.fail = false
            model.refresh()
            advanceUntilIdle()
            assertNull(model.state.value.notice)
        } finally { model.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun limitedAccountNeverConnectsThePlayerOrReadsCatalog() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val api = FakeAccountApi(listOf(song))
        val connections = FakeConnections(active(api).copy(auth = auth.copy(mustChangePassword = true)))
        val player = FakePlayback()
        val model = ClientViewModel(connections, player, dispatcher)
        try {
            advanceUntilIdle()
            assertEquals(ClientScreen.CHANGE_PASSWORD, model.state.value.screen)
            assertEquals(0, player.connected)
            assertEquals(0, api.requests)
            model.changePassword("temporary", "new-password")
            advanceUntilIdle()
            assertEquals(ClientScreen.CONNECT, model.state.value.screen)
            assertEquals(ClientNotice.PASSWORD_CHANGED, model.state.value.notice)
        } finally { model.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun logoutImmediatelyClearsCatalogAndQueueBeforeServerRevocationFinishes() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val connections = FakeConnections(active(FakeAccountApi(listOf(song))))
        val player = FakePlayback()
        val model = ClientViewModel(connections, player, dispatcher)
        try {
            advanceUntilIdle()
            model.playSong(song); advanceUntilIdle()
            assertNotNull(player.state.value.current)
            connections.logoutGate = CompletableDeferred()
            model.logout(); runCurrent()
            assertEquals(ClientScreen.CONNECT, model.state.value.screen)
            assertTrue(model.state.value.songs.isEmpty())
            assertTrue(player.state.value.queue.isEmpty())
            assertTrue(model.state.value.busy)
            connections.logoutGate!!.complete(Unit); advanceUntilIdle()
            assertFalse(model.state.value.busy)
        } finally { model.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun supersededQueuedSearchAndPageFailureDoNotReplaceCurrentResults() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val api = FakeAccountApi(listOf(song))
        val connections = FakeConnections(active(api))
        val model = ClientViewModel(connections, FakePlayback(), dispatcher)
        try {
            advanceUntilIdle()
            model.search("discarded")
            model.search("latest")
            advanceUntilIdle()
            assertEquals("latest", model.state.value.query)
            assertEquals("latest", api.lastQuery)
            assertEquals(listOf(song), model.state.value.songs)
            api.fail = true
            model.loadMore(); advanceUntilIdle()
            assertEquals(listOf(song), model.state.value.songs)
            assertEquals(50, api.lastOffset)
            api.fail = false
            model.loadMore(); advanceUntilIdle()
            assertEquals(50, api.lastOffset)
            assertEquals(1, model.state.value.songs.size)
        } finally { model.viewModelScope.cancel(); Dispatchers.resetMain() }
    }


    @Test fun failedNewQueryCannotReuseThePreviousQueryCursorOrResults() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val api = FakeAccountApi(listOf(song))
        val model = ClientViewModel(FakeConnections(active(api)), FakePlayback(), dispatcher)
        try {
            advanceUntilIdle()
            model.loadMore(); advanceUntilIdle()
            assertEquals(50, api.lastOffset)
            api.fail = true
            model.search("different"); advanceUntilIdle()
            assertTrue(model.state.value.songs.isEmpty())
            assertFalse(model.state.value.hasMore)
            val failedRequests = api.requests
            model.loadMore(); advanceUntilIdle()
            assertEquals(failedRequests, api.requests)
            api.fail = false
            model.refresh(); advanceUntilIdle()
            assertEquals("different", api.lastQuery)
            assertEquals(0, api.lastOffset)
            assertEquals(listOf(song), model.state.value.songs)
        } finally { model.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun failedTabSwitchCannotLoadMoreWithThePreviousCatalogCursor() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val api = FakeAccountApi(listOf(song))
        val model = ClientViewModel(FakeConnections(active(api)), FakePlayback(), dispatcher)
        try {
            advanceUntilIdle()
            model.loadMore(); advanceUntilIdle()
            api.fail = true
            model.selectTab(LibraryTab.FAVORITES); advanceUntilIdle()
            assertEquals(ClientNotice.NETWORK, model.state.value.notice)
            assertFalse(model.state.value.hasMore)
            assertTrue(model.state.value.songs.isEmpty())
            val failedRequests = api.requests
            model.loadMore(); advanceUntilIdle()
            assertEquals(failedRequests, api.requests)
            api.fail = false
            model.refresh(); advanceUntilIdle()
            assertEquals(listOf(song), model.state.value.songs)
            model.selectTab(LibraryTab.CATALOG); advanceUntilIdle()
            assertEquals(0, api.lastOffset)
        } finally { model.viewModelScope.cancel(); Dispatchers.resetMain() }
    }


    @Test fun albumDetailsPlayTheCompleteAlbumRatherThanTheCurrentSongPage() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val api = FakeAccountApi(listOf(song))
        val player = FakePlayback()
        val model = ClientViewModel(FakeConnections(active(api)), player, dispatcher)
        try {
            advanceUntilIdle()
            model.openAlbum(api.albumItem); advanceUntilIdle()
            assertEquals(api.albumItem.id, model.state.value.selectedAlbum?.id)
            assertEquals(3, model.state.value.albumSongs.size)
            val last = model.state.value.albumSongs.last()
            model.playSong(last); advanceUntilIdle()
            assertEquals(3, player.state.value.queue.size)
            assertEquals(last.id, player.state.value.current?.id)
            model.navigate(MainDestination.SEARCH)
            assertNull(model.state.value.selectedAlbum)
            assertTrue(model.state.value.albumSongs.isEmpty())
        } finally { model.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun albumListReturningAfterLogoutCannotRestorePrivateMetadata() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val api = FakeAccountApi(listOf(song))
        val model = ClientViewModel(FakeConnections(active(api)), FakePlayback(), dispatcher)
        try {
            advanceUntilIdle()
            api.onAlbums = { model.logout() }
            model.refresh(); advanceUntilIdle()
            assertEquals(ClientScreen.CONNECT, model.state.value.screen)
            assertTrue(model.state.value.albums.isEmpty())
            assertTrue(model.state.value.albumSongs.isEmpty())
        } finally { model.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun albumDetailReturningAfterLogoutCannotReopenTheAlbum() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val api = FakeAccountApi(listOf(song))
        val model = ClientViewModel(FakeConnections(active(api)), FakePlayback(), dispatcher)
        try {
            advanceUntilIdle()
            api.onDetail = { model.logout() }
            model.openAlbum(api.albumItem); advanceUntilIdle()
            assertEquals(ClientScreen.CONNECT, model.state.value.screen)
            assertNull(model.state.value.selectedAlbum)
            assertTrue(model.state.value.albumSongs.isEmpty())
        } finally { model.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun navigationResetsSearchAndPlayerVolumeIsClamped() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val player = FakePlayback()
        val model = ClientViewModel(FakeConnections(active(FakeAccountApi(listOf(song)))), player, dispatcher)
        try {
            advanceUntilIdle()
            model.navigate(MainDestination.SEARCH)
            model.search("a query"); advanceUntilIdle()
            model.navigate(MainDestination.LIBRARY); advanceUntilIdle()
            assertEquals(MainDestination.LIBRARY, model.state.value.destination)
            assertEquals("", model.state.value.query)
            model.setVolume(.4f); advanceUntilIdle()
            assertEquals(.4f, player.state.value.volume)
            model.setVolume(3f); advanceUntilIdle()
            assertEquals(1f, player.state.value.volume)
            model.setVolume(Float.NaN); advanceUntilIdle()
            assertEquals(1f, player.state.value.volume)
        } finally { model.viewModelScope.cancel(); Dispatchers.resetMain() }
    }


    @Test fun switchingToAlbumsDuringSongRefreshClearsTheCancelledLoadingState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val api = FakeAccountApi(listOf(song))
        val model = ClientViewModel(FakeConnections(active(api)), FakePlayback(), dispatcher)
        try {
            advanceUntilIdle()
            model.navigate(MainDestination.LIBRARY)
            model.refresh()
            assertTrue(model.state.value.loading)
            model.selectTab(LibraryTab.ALBUMS)
            advanceUntilIdle()
            assertFalse(model.state.value.loading)
            assertFalse(model.state.value.loadingMore)
            assertFalse(model.state.value.albumsLoading)
            assertTrue(model.state.value.albums.isNotEmpty())
        } finally { model.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun discoverNavigationIsolatesSearchQueryAndFiltersArtistsAlbumsSongs() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val api = FakeAccountApi(listOf(song, Song("two", "Second song", artist = "Another", audioUrl = "https://music.example/media/two.mp3")))
        val model = ClientViewModel(FakeConnections(active(api)), FakePlayback(), dispatcher)
        try {
            advanceUntilIdle()
            model.navigate(MainDestination.SEARCH)
            model.search("rain"); advanceUntilIdle()
            assertEquals("rain", model.state.value.query)
            model.navigate(MainDestination.DISCOVER); advanceUntilIdle()
            assertEquals(MainDestination.DISCOVER, model.state.value.destination)
            assertEquals("", model.state.value.query)
            model.selectDiscoverLanguage("zh"); advanceUntilIdle()
            assertEquals("zh", model.state.value.discoverLanguage)
            assertEquals("zh", model.state.value.songLanguage)
            assertEquals("zh", model.state.value.albumLanguage)
            assertEquals("zh", api.lastArtistLanguage)
            assertEquals("zh", api.lastSongLanguage)
            assertEquals("zh", api.lastAlbumLanguage)
        } finally { model.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun discoverLanguageDoesNotFilterSearchSongsAndReturningRestoresDiscoverCatalog() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val api = FakeAccountApi(listOf(song, Song("two", "Second song", artist = "Another", audioUrl = "https://music.example/media/two.mp3")))
        val model = ClientViewModel(FakeConnections(active(api)), FakePlayback(), dispatcher)
        try {
            advanceUntilIdle()
            model.navigate(MainDestination.DISCOVER); advanceUntilIdle()
            model.selectDiscoverLanguage("zh"); advanceUntilIdle()
            assertEquals("zh", model.state.value.discoverLanguage)
            assertEquals("zh", api.lastSongLanguage)
            model.navigate(MainDestination.SEARCH); advanceUntilIdle()
            model.search("rain"); advanceUntilIdle()
            assertEquals("all", api.lastSongLanguage)
            assertEquals("rain", model.state.value.query)
            model.refreshArtists("Someone"); advanceUntilIdle()
            assertEquals("Someone", model.state.value.artistQuery)
            model.navigate(MainDestination.DISCOVER); advanceUntilIdle()
            assertEquals("", model.state.value.query)
            assertEquals("", model.state.value.artistQuery)
            assertEquals("zh", model.state.value.discoverLanguage)
            assertEquals("zh", model.state.value.songLanguage)
            assertEquals("zh", model.state.value.albumLanguage)
            assertEquals("zh", api.lastSongLanguage)
            assertEquals("zh", api.lastAlbumLanguage)
            assertEquals("zh", api.lastArtistLanguage)
        } finally { model.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun playHistorySongBuildsQueueFromHistoryEntriesStartingAtClickedTrack() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val song2 = Song("two", "Second song", audioUrl = "https://music.example/media/two.mp3")
        val song3 = Song("three", "Third song", audioUrl = "https://music.example/media/three.mp3")
        val api = FakeAccountApi(listOf(song, song2, song3))
        val player = FakePlayback()
        val historyScope = CoroutineScope(SupervisorJob() + dispatcher)
        val store = InMemoryPlaybackHistoryStore()
        store.write(
            HistoryIdentity("https://music.example", auth.account.id),
            listOf(
                HistoryEntry("three", "Third song", "", "", 200.0, null, 300),
                HistoryEntry("two", "Second song", "", "", 190.0, null, 200),
                HistoryEntry("one", "First song", "", "", 180.0, null, 100),
            ),
        )
        val repository = PlaybackHistoryRepository(store, historyScope, dispatcher)
        val model = ClientViewModel(FakeConnections(active(api)), player, dispatcher, history = repository)
        try {
            advanceUntilIdle()
            val historyOrder = model.state.value.playHistory.map { it.id }
            assertEquals(listOf("three", "two", "one"), historyOrder)
            val clicked = model.state.value.playHistory[1]
            model.playHistorySong(clicked); advanceUntilIdle()
            assertEquals("two", player.state.value.current?.id)
            assertEquals(listOf("three", "two", "one"), player.state.value.queue.map { it.id })
            assertEquals(1, player.state.value.currentIndex)
        } finally { model.viewModelScope.cancel(); historyScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun playHistorySongStillPlaysClickedTrackWhenOtherHistoryResolutionFails() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val song2 = Song("two", "Second song", audioUrl = "https://music.example/media/two.mp3")
        val song3 = Song("three", "Third song", audioUrl = "https://music.example/media/three.mp3")
        val api = FakeAccountApi(listOf(song, song2, song3))
        val player = FakePlayback()
        val historyScope = CoroutineScope(SupervisorJob() + dispatcher)
        val store = InMemoryPlaybackHistoryStore()
        store.write(
            HistoryIdentity("https://music.example", auth.account.id),
            listOf(
                HistoryEntry("three", "Third song", "", "", 200.0, null, 300),
                HistoryEntry("two", "Second song", "", "", 190.0, null, 200),
                HistoryEntry("one", "First song", "", "", 180.0, null, 100),
            ),
        )
        val repository = PlaybackHistoryRepository(store, historyScope, dispatcher)
        val model = ClientViewModel(FakeConnections(active(api)), player, dispatcher, history = repository)
        try {
            advanceUntilIdle()
            api.failResolveSongs = true
            val clicked = model.state.value.playHistory[1]
            model.playHistorySong(clicked); advanceUntilIdle()
            assertEquals("two", player.state.value.current?.id)
            assertEquals(listOf("two"), player.state.value.queue.map { it.id })
            assertEquals(0, player.state.value.currentIndex)
            assertNull(model.state.value.historyNotice)
        } finally { model.viewModelScope.cancel(); historyScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun continueListeningMixesRecentRepeatsAllTimeStatsAndFavoritesWithDeduplication() {
        val recentA = RankedSong(Song("r1", "Repeat One", "Artist A", audioUrl = "https://music.example/media/r1.mp3"), 5, 500)
        val recentDupTitle = RankedSong(Song("r2-alt", "Repeat One", "Artist A", audioUrl = "https://music.example/media/r2a.mp3"), 3, 450)
        val recentB = RankedSong(Song("r2", "Repeat Two", "Artist B", audioUrl = "https://music.example/media/r2.mp3"), 3, 400)
        val unplayable = RankedSong(Song("dead", "Unavailable", "Artist X", audioUrl = ""), 10, 900)
        val frequentA = RankedSong(Song("f1", "Frequent One", "Artist C", audioUrl = "https://music.example/media/f1.mp3"), 20, 300)
        val frequentB = RankedSong(Song("f2", "Frequent Two", "Artist D", audioUrl = "https://music.example/media/f2.mp3"), 18, 250)
        val favA = Song("fav1", "Liked One", "Artist E", audioUrl = "https://music.example/media/fav1.mp3")
        val favB = Song("fav2", "Liked Two", "Artist F", audioUrl = "https://music.example/media/fav2.mp3")
        val mixed = buildContinueListeningMix(
            recentStats = listOf(recentA, recentDupTitle, recentB, unplayable),
            allTimeStats = listOf(unplayable, frequentA, frequentB),
            favorites = listOf(favA, favB, recentA.song),
            limit = 6,
            random = kotlin.random.Random(42),
        )
        assertEquals(6, mixed.size)
        assertEquals(6, mixed.map { it.id }.distinct().size)
        // Different stable IDs may be different editions of the same title and artist.
        assertTrue(mixed.all { it.canPlay })
        assertTrue(mixed.any { it.id == "r1" || it.id == "r2" })
        assertTrue(mixed.any { it.id == "f1" || it.id == "f2" })
        assertTrue(mixed.any { it.id == "fav1" || it.id == "fav2" })
    }

    @Test fun continueListeningAndHomePreviewLockAcrossPlaybackUntilManualRefresh() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val s1 = Song("one", "Track One", "Singer A", audioUrl = "https://music.example/media/1.mp3")
        val s2 = Song("two", "Track Two", "Singer B", audioUrl = "https://music.example/media/2.mp3")
        val s3 = Song("three", "Track Three", "Singer C", audioUrl = "https://music.example/media/3.mp3")
        val s4 = Song("four", "Track Four", "Singer D", audioUrl = "https://music.example/media/4.mp3")
        val s5 = Song("five", "Track Five", "Singer E", audioUrl = "https://music.example/media/5.mp3")
        val s6 = Song("six", "Track Six", "Singer F", audioUrl = "https://music.example/media/6.mp3")
        val s7 = Song("seven", "Track Seven", "Singer G", audioUrl = "https://music.example/media/7.mp3")
        val api = FakeAccountApi(listOf(s1, s2, s3, s4, s5, s6, s7)).apply {
            recentStats = PlayStats(songs = listOf(RankedSong(s2, 4, 200), RankedSong(s3, 2, 190)), summaryOnly = true)
            allTimeStats = PlayStats(songs = listOf(RankedSong(s4, 15, 180), RankedSong(s5, 12, 170)))
            favoritePlaylistSongs = listOf(s6)
        }
        val player = FakePlayback()
        val historyScope = CoroutineScope(SupervisorJob() + dispatcher)
        val store = InMemoryPlaybackHistoryStore()
        store.write(
            HistoryIdentity("https://music.example", auth.account.id),
            listOf(HistoryEntry("one", "Track One", "Singer A", "", 180.0, null, 100)),
        )
        val repository = PlaybackHistoryRepository(store, historyScope, dispatcher)
        val model = ClientViewModel(FakeConnections(active(api)), player, dispatcher, history = repository)
        try {
            advanceUntilIdle()
            val initialMix = model.state.value.continueListeningSongs.map { it.id }
            assertEquals(listOf("two", "three", "four", "five", "six"), initialMix.sortedBy {
                listOf("two", "three", "four", "five", "six").indexOf(it)
            })
            assertEquals("one", model.state.value.homeHistoryPreviewSong?.id)
            assertTrue(7 in api.requestedStatsDays)

            // Recording playback & changing favorites later updates live history, NOT the locked Home snapshots.
            api.allTimeStats = PlayStats(songs = listOf(RankedSong(s7, 99, 500)))
            api.recentStats = PlayStats(songs = listOf(RankedSong(s7, 20, 500)), summaryOnly = true)
            api.favoritePlaylistSongs = listOf(s7)
            repository.record(s7, playedAt = 900)
            model.playSong(s7)
            model.navigate(MainDestination.HOME)
            advanceUntilIdle()
            assertEquals("seven", model.state.value.playHistory.first().id)
            assertEquals("one", model.state.value.homeHistoryPreviewSong?.id)
            assertEquals(initialMix, model.state.value.continueListeningSongs.map { it.id })

            // Explicit manual refresh updates the snapshots cleanly.
            model.refresh()
            advanceUntilIdle()
            assertEquals("seven", model.state.value.homeHistoryPreviewSong?.id)
            assertTrue("seven" in model.state.value.continueListeningSongs.map { it.id })
        } finally { model.viewModelScope.cancel(); historyScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun continueListeningGracefullyFallsBackWhenRecentStatsWindowFails() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val s1 = Song("f1", "Frequent", "Artist 1", audioUrl = "https://music.example/media/f1.mp3")
        val s2 = Song("fav1", "Favorite", "Artist 2", audioUrl = "https://music.example/media/fav1.mp3")
        val api = FakeAccountApi(listOf(s1, s2)).apply {
            failRecentStats = true
            allTimeStats = PlayStats(songs = listOf(RankedSong(s1, 10, 100)))
            favoritePlaylistSongs = listOf(s2)
        }
        val model = ClientViewModel(FakeConnections(active(api)), FakePlayback(), dispatcher)
        try {
            advanceUntilIdle()
            assertEquals(setOf("f1", "fav1"), model.state.value.continueListeningSongs.map { it.id }.toSet())
            assertNull(model.state.value.statsNotice)
        } finally { model.viewModelScope.cancel(); Dispatchers.resetMain() }
    }


    @Test fun continueListeningUsesResolvedSearchSongsAndRefreshReadsDiskBeyondTheVisibleSnapshot() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val searchA = song.copy(id="searched-a", title="Search A", coverUrl="https://music.example/a.jpg")
        val searchB = song.copy(id="searched-b", title="Search B")
        val recent = song.copy(id="recent", title="Recent")
        val frequent = song.copy(id="frequent", title="Frequent")
        val liked = song.copy(id="liked", title="Liked")
        val storage = ContinueSearchStorage()
        val store = SearchHistoryStore(storage)
        val key = HistoryIdentity("https://music.example", auth.account.id)
        store.addTerm(key,"This term is not a song")
        store.addEntity(key,SearchEntity.fromArtist("Not a song either"))
        store.addEntity(key,SearchEntity.fromSong(searchA))
        val api = FakeAccountApi(listOf(searchA,searchB,recent,frequent,liked)).apply {
            favoritePlaylistSongs=listOf(liked)
            allTimeStats=PlayStats(songs=listOf(RankedSong(frequent,20,100)))
            recentStats=PlayStats(songs=listOf(RankedSong(recent,3,200)),summaryOnly=true)
        }
        val player=FakePlayback()
        val model=ClientViewModel(FakeConnections(active(api)),player,dispatcher,searchHistoryStore=store)
        try {
            advanceUntilIdle()
            val initial=model.state.value.continueListeningSongs
            assertEquals(setOf("searched-a","recent","frequent","liked"),initial.map { it.id }.toSet())
            assertEquals(searchA.coverUrl,initial.single { it.id==searchA.id }.coverUrl)
            assertTrue(api.resolveRequests.flatten().contains(searchA.id))
            assertFalse(api.resolveRequests.flatten().contains("Not a song either"))
            assertTrue(api.requestedStatsDays.containsAll(listOf(1,7)))
            val index=initial.indexOfFirst { it.id==searchA.id }
            model.playSongs(initial,index); advanceUntilIdle()
            assertEquals(initial.map { it.id },player.state.value.queue.map { it.id })
            assertEquals(searchA.id,player.state.value.current?.id)

            // Search cards stay locked for this session, but refresh must consume the latest disk data.
            model.navigate(MainDestination.SEARCH); advanceUntilIdle()
            model.playSong(searchB); advanceUntilIdle()
            assertEquals(listOf(searchA.id),model.state.value.recentSearches.filter { it.type=="song" }.map { it.id })
            assertTrue(store.load(key).entities.any { it.id==searchB.id })
            assertEquals(initial.map { it.id },model.state.value.continueListeningSongs.map { it.id })
            model.refresh(); advanceUntilIdle()
            assertTrue(model.state.value.continueListeningSongs.any { it.id==searchB.id })
        } finally { model.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun aFailedSearchResolutionReleasesItsQuotaToOtherSources() = runTest {
        val dispatcher=StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        val store=SearchHistoryStore(ContinueSearchStorage())
        store.addEntity(HistoryIdentity("https://music.example",auth.account.id),SearchEntity.fromSong(song.copy(id="missing")))
        val fallback=(0 until 18).map { song.copy(id="fallback-$it",title="Song $it") }
        val api=FakeAccountApi(fallback).apply {
            failResolveSongs=true
            allTimeStats=PlayStats(songs=fallback.map { RankedSong(it,10,100) })
        }
        val model=ClientViewModel(FakeConnections(active(api)),FakePlayback(),dispatcher,searchHistoryStore=store)
        try {
            advanceUntilIdle()
            assertEquals(12,model.state.value.continueListeningSongs.size)
            assertEquals(12,model.state.value.continueListeningSongs.map { it.id }.distinct().size)
            assertTrue(model.state.value.continueListeningSongs.none { it.id=="missing" })
        } finally { model.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun aLateSearchResolutionCannotRepopulateAReplacementAccount() = runTest {
        val dispatcher=StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        val store=SearchHistoryStore(ContinueSearchStorage())
        store.addEntity(HistoryIdentity("https://music.example",auth.account.id),SearchEntity.fromSong(song))
        val oldApi=FakeAccountApi(listOf(song))
        val connections=FakeConnections(active(oldApi))
        lateinit var model:ClientViewModel
        oldApi.onResolve={ model.logout() }
        model=ClientViewModel(connections,FakePlayback(),dispatcher,searchHistoryStore=store)
        try {
            advanceUntilIdle()
            assertEquals(ClientScreen.CONNECT,model.state.value.screen)
            assertTrue(model.state.value.continueListeningSongs.isEmpty())
            val replacement=song.copy(id="new-account-song")
            val newApi=FakeAccountApi(listOf(replacement)).apply {
                allTimeStats=PlayStats(songs=listOf(RankedSong(replacement,4,100)))
            }
            connections.current=active(newApi).copy(auth=auth.copy(account=Account("other-account","other","Other")))
            model.login("https://music.example","other","password"); advanceUntilIdle()
            assertEquals(listOf(replacement.id),model.state.value.continueListeningSongs.map { it.id })
            assertTrue(newApi.resolveRequests.isEmpty())
        } finally { model.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun playbackPositionFlowUpdatesWithoutReEmittingUiState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val progressFlow = MutableStateFlow(0L)
        val player = object : PlaybackGateway by FakePlayback() {
            override val state = MutableStateFlow(PlaybackSnapshot(current = song, queue = listOf(song), currentIndex = 0, playing = true, durationMs = 180_000L))
            override val positionMsFlow = progressFlow
        }
        val saved = SavedStateHandle()
        val model = ClientViewModel(
            FakeConnections(active(FakeAccountApi(listOf(song)))),
            player,
            dispatcher,
            savedStateHandle = saved,
        )
        try {
            advanceUntilIdle()
            val savedSnapshot = saved.get<ArrayList<String>>(ClientNavigationState.KEY)
            assertNotNull(savedSnapshot)
            var uiEmissions = 0
            val collector = launch(dispatcher) {
                var first = true
                model.state.collect {
                    if (first) first = false else uiEmissions++
                }
            }
            advanceUntilIdle()
            val emissionsBeforeTicks = uiEmissions
            progressFlow.value = 250L; advanceUntilIdle()
            progressFlow.value = 500L; advanceUntilIdle()
            progressFlow.value = 1200L; advanceUntilIdle()
            assertEquals(1200L, model.playbackPositionMs.value)
            assertEquals(emissionsBeforeTicks, uiEmissions)
            assertEquals(savedSnapshot, saved.get<ArrayList<String>>(ClientNavigationState.KEY))
            collector.cancel()
        } finally { model.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun navigateSkipsRedundantHomeLibraryRefetchAndRestoresDiscoverAfterSearch() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val extraSongs = listOf(
            song,
            song.copy(id = "two", title = "Blue hour", language = "en"),
        )
        val api = FakeAccountApi(extraSongs).apply {
            favoritePlaylistSongs = listOf(song)
        }
        val model = ClientViewModel(
            FakeConnections(active(api)),
            FakePlayback(),
            dispatcher,
        )
        try {
            advanceUntilIdle()
            val initialSongRequests = api.requests
            val initialPlaylistRequests = api.playlistsRequests
            val initialPlaylistDetailRequests = api.playlistDetailRequests
            assertTrue(initialSongRequests >= 1)
            assertTrue(initialPlaylistRequests >= 1)
            assertTrue(initialPlaylistDetailRequests >= 1)

            // Switching HOME -> LIBRARY -> HOME should not trigger redundant catalog/playlist/favorites fetches.
            model.navigate(MainDestination.LIBRARY)
            advanceUntilIdle()
            model.navigate(MainDestination.HOME)
            advanceUntilIdle()
            assertEquals(initialSongRequests, api.requests)
            assertEquals(initialPlaylistRequests, api.playlistsRequests)
            assertEquals(initialPlaylistDetailRequests, api.playlistDetailRequests)

            // Searching in SEARCH then leaving SEARCH -> DISCOVER must clear query and reload catalog.
            model.navigate(MainDestination.SEARCH)
            advanceUntilIdle()
            model.search("rain")
            advanceUntilIdle()
            assertEquals("rain", model.state.value.query)
            val beforeLeaveSearch = api.requests

            model.navigate(MainDestination.DISCOVER)
            advanceUntilIdle()
            assertEquals("", model.state.value.query)
            assertEquals(MainDestination.DISCOVER, model.state.value.destination)
            assertTrue(api.requests > beforeLeaveSearch)

            // Changing subpage song/album language inside DISCOVER then tapping DISCOVER tab again
            // must resync songLanguage and albumLanguage back to discoverLanguage.
            model.selectDiscoverLanguage("zh")
            advanceUntilIdle()
            model.selectSongLanguage("en")
            advanceUntilIdle()
            assertEquals("en", model.state.value.songLanguage)
            assertEquals("zh", model.state.value.discoverLanguage)
            val beforeSyncDiscover = api.requests
            model.navigate(MainDestination.DISCOVER)
            advanceUntilIdle()
            assertEquals("zh", model.state.value.songLanguage)
            assertEquals("zh", model.state.value.albumLanguage)
            assertTrue(api.requests > beforeSyncDiscover)

            // Entering LibraryTab.FAVORITES then calling navigate(LIBRARY) must exit back to CATALOG.
            model.navigate(MainDestination.LIBRARY)
            advanceUntilIdle()
            model.selectTab(LibraryTab.FAVORITES)
            advanceUntilIdle()
            assertEquals(LibraryTab.FAVORITES, model.state.value.tab)
            model.navigate(MainDestination.LIBRARY)
            advanceUntilIdle()
            assertEquals(LibraryTab.CATALOG, model.state.value.tab)
        } finally { model.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    private fun active(api: AccountApi) = ActiveConnection(
        SessionNetwork(InstanceAddress.parse("https://music.example")), api, auth)
}

private class FakeAccountApi(private val songs: List<Song>) : AccountApi {
    var requests = 0
    var fail = false
    var failResolveSongs = false
    val resolveRequests=mutableListOf<List<String>>()
    var onResolve:(()->Unit)?=null
    var failRecentStats = false
    var allTimeStats = PlayStats()
    var recentStats = PlayStats(summaryOnly = true)
    var favoritePlaylistSongs: List<Song> = emptyList()
    val requestedStatsDays = mutableListOf<Int?>()
    var lastQuery = ""
    var lastOffset = 0
    var lastSongLanguage = "all"
    var lastAlbumLanguage = "all"
    var lastArtistLanguage = "all"
    override fun songs(query: String, offset: Int): SongPage {
        requests++; lastQuery = query; lastOffset = offset
        if (fail) throw ApiFailure(Failure.NETWORK)
        return SongPage(songs, offset + 50, true)
    }
    override fun songs(query: String, offset: Int, language: String, sort: String): SongPage {
        lastSongLanguage = language
        return songs(query, offset)
    }
    override fun song(id: String): Song {
        return songs.firstOrNull { it.id == id } ?: throw ApiFailure(Failure.REQUEST)
    }
    override fun resolveSongs(ids: List<String>): List<Song> {
        resolveRequests += ids.toList()
        onResolve?.invoke()
        if (failResolveSongs) throw ApiFailure(Failure.NETWORK)
        return ids.mapNotNull { id -> songs.firstOrNull { it.id == id } }
    }
    override fun artists(query: String, offset: Int, language: String): ArtistPage {
        lastArtistLanguage = language
        return ArtistPage(listOf(Artist("FlareTune", songs.size, 1)), 0, false, 1)
    }
    override fun favorites(): List<Song> {
        requests++
        if (fail) throw ApiFailure(Failure.NETWORK)
        return songs
    }
    var playlistsRequests = 0
    var playlistDetailRequests = 0
    override fun playlists(): List<PlaylistSummary> {
        playlistsRequests++
        return if (favoritePlaylistSongs.isEmpty()) emptyList() else {
            listOf(PlaylistSummary("fav-id", "Liked", "favorite", songCount = favoritePlaylistSongs.size, revision = 1))
        }
    }
    override fun playlist(id: String): PlaylistDetail {
        playlistDetailRequests++
        if (id != "fav-id" || favoritePlaylistSongs.isEmpty()) throw ApiFailure(Failure.REQUEST)
        return PlaylistDetail(
            PlaylistSummary("fav-id", "Liked", "favorite", songCount = favoritePlaylistSongs.size, revision = 1),
            favoritePlaylistSongs.mapIndexed { index, item -> PlaylistSong(item, index) },
        )
    }
    override fun playStats(limit: Int, summaryOnly: Boolean): PlayStats = playStats(limit, summaryOnly, null)
    override fun playStats(limit: Int, summaryOnly: Boolean, days: Int?): PlayStats {
        requestedStatsDays += days
        if (days != null) {
            if (failRecentStats) throw ApiFailure(Failure.REQUEST)
            return recentStats
        }
        return allTimeStats
    }

    val albumItem = Album("album-one", "Evening", "FlareTune", trackCount = 3)
    var onAlbums: (() -> Unit)? = null
    var onDetail: (() -> Unit)? = null
    override fun albums(offset: Int): AlbumPage {
        onAlbums?.invoke()
        if (fail) throw ApiFailure(Failure.NETWORK)
        return AlbumPage(listOf(albumItem), offset + 20, false)
    }
    override fun albums(offset: Int, language: String): AlbumPage {
        lastAlbumLanguage = language
        return albums(offset)
    }
    override fun album(id: String): AlbumDetail {
        onDetail?.invoke()
        if (fail) throw ApiFailure(Failure.NETWORK)
        return AlbumDetail(albumItem, (0..2).map { songs.first().copy(id = "album-track-" + it) })
    }
    override fun changePassword(current: String, replacement: String) = Unit
    override fun logout() = Unit
}

private class FakeConnections(override var current: ActiveConnection?) : Connections {
    override val invalidations = MutableSharedFlow<ClientNotice>(extraBufferCapacity = 1)
    override val lastOrigin = "https://music.example"
    var logoutGate: CompletableDeferred<Unit>? = null
    override suspend fun restore() = current
    override suspend fun login(address: String, username: String, password: String) = current!!
    override suspend fun changePassword(current: String, replacement: String) { this.current = null }
    override suspend fun logout() { current = null; logoutGate?.await() }
}

private class FakePlayback : PlaybackGateway {
    override val state = MutableStateFlow(PlaybackSnapshot())
    override val imageLoader: ImageLoader? = null
    var connected = 0
    override suspend fun connect(connection: ActiveConnection) { connected++ }
    override fun play(songs: List<Song>, index: Int) { state.value = PlaybackSnapshot(current = songs[index], queue = songs, currentIndex = index, playing = true) }
    override fun stopAndClear() { state.value = PlaybackSnapshot() }
    override fun setVolume(volume: Float) { state.value = state.value.copy(volume = volume) }
    override fun togglePlay() {}
    override fun next() {}
    override fun previous() {}
    override fun seekTo(positionMs: Long) {}
    override fun toggleShuffle() {}
    override fun cycleRepeat() {}
    override fun select(index: Int) {}
    override fun retry() {}
    override fun release() {}
}

private class ContinueSearchStorage:HistoryStorage {
    private val values=mutableMapOf<String,String>()
    override fun read(key:String)=values[key]
    override fun write(key:String,value:String) { values[key]=value }
    override fun remove(key:String) { values.remove(key) }
}
