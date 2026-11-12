package com.flaretune.client

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

    private fun active(api: AccountApi) = ActiveConnection(
        SessionNetwork(InstanceAddress.parse("https://music.example")), api, auth)
}

private class FakeAccountApi(private val songs: List<Song>) : AccountApi {
    var requests = 0
    var fail = false
    var lastQuery = ""
    var lastOffset = 0
    override fun songs(query: String, offset: Int): SongPage {
        requests++; lastQuery = query; lastOffset = offset
        if (fail) throw ApiFailure(Failure.NETWORK)
        return SongPage(songs, offset + 50, true)
    }
    override fun favorites(): List<Song> {
        requests++
        if (fail) throw ApiFailure(Failure.NETWORK)
        return songs
    }

    val albumItem = Album("album-one", "Evening", "FlareTune", trackCount = 3)
    var onAlbums: (() -> Unit)? = null
    var onDetail: (() -> Unit)? = null
    override fun albums(offset: Int): AlbumPage {
        onAlbums?.invoke()
        if (fail) throw ApiFailure(Failure.NETWORK)
        return AlbumPage(listOf(albumItem), offset + 20, false)
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
