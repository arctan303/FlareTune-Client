package com.arcinks.flaretune

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.arcinks.flaretune.data.*
import com.arcinks.flaretune.model.*
import com.arcinks.flaretune.playback.PlaybackGateway
import com.arcinks.flaretune.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationRestorationTest {
    @Test fun returningToTheSameViewModelKeepsSearchDetailAndFilters() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = fixture(SavedStateHandle())
        try {
            advanceUntilIdle()
            f.model.navigate(MainDestination.SEARCH); f.model.search("evening")
            f.model.selectSongLanguage("en"); advanceUntilIdle()
            f.model.openAlbum(f.api.album); advanceUntilIdle()
            f.model.setUiVisible(false); f.model.setUiVisible(true); advanceUntilIdle()
            assertEquals(MainDestination.SEARCH, f.model.state.value.destination)
            assertEquals("evening", f.model.state.value.query)
            assertEquals("en", f.model.state.value.songLanguage)
            assertEquals("album-one", f.model.state.value.selectedAlbum?.id)
        } finally { f.model.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun aNewViewModelRestoresTheSavedRouteAndRefetchesDetail() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val handle = SavedStateHandle()
        val first = fixture(handle)
        advanceUntilIdle()
        first.model.navigate(MainDestination.SEARCH); first.model.search("evening")
        first.model.selectSongLanguage("ja"); advanceUntilIdle()
        first.model.openAlbum(first.api.album); advanceUntilIdle()
        first.model.saveNavigationState()
        val saved = ArrayList(handle.get<ArrayList<String>>(ClientNavigationState.KEY)!!)
        first.model.viewModelScope.cancel()
        val second = fixture(SavedStateHandle(mapOf(ClientNavigationState.KEY to saved)))
        try {
            advanceUntilIdle()
            val state = second.model.state.value
            assertEquals(MainDestination.SEARCH, state.destination)
            assertEquals("evening", state.query)
            assertEquals("ja", state.songLanguage)
            assertEquals("album-one", state.selectedAlbum?.id)
            assertEquals(listOf("fresh-detail"), state.albumSongs.map { it.id })
            assertEquals(listOf("album-one"), second.api.requestedAlbums)
            assertFalse(saved.any { it.contains("https:") || it.contains("csrf") || it.contains("media.mp3") })
        } finally { second.model.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun playlistAndArtistRoutesReloadWithoutSavingTheirMedia() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        for (artist in listOf(false, true)) {
            val handle = SavedStateHandle()
            val first = fixture(handle)
            advanceUntilIdle(); first.model.navigate(MainDestination.LIBRARY)
            if (artist) first.model.openArtist(Artist("An artist")) else first.model.openPlaylist(first.api.personal)
            advanceUntilIdle(); first.model.saveNavigationState(); first.model.viewModelScope.cancel()
            val second = fixture(handle)
            try {
                advanceUntilIdle()
                assertEquals(MainDestination.LIBRARY, second.model.state.value.destination)
                if (artist) assertEquals("An artist", second.model.state.value.selectedArtist?.name)
                else {
                    assertEquals("personal", second.model.state.value.selectedPlaylist?.id)
                    assertEquals(listOf("fresh-detail"), second.model.state.value.playlistSongs.map { it.id })
                }
            } finally { second.model.viewModelScope.cancel() }
        }
        Dispatchers.resetMain()
    }

    @Test fun aDifferentAccountOrInstanceCannotRestorePrivateNavigation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        for ((origin, account) in listOf("https://music.example" to "other-account", "https://other.example" to "same-account")) {
            val handle = SavedStateHandle()
            val first = fixture(handle)
            advanceUntilIdle(); first.model.navigate(MainDestination.SEARCH); first.model.search("private-query"); advanceUntilIdle()
            first.model.viewModelScope.cancel()
            val second = fixture(handle, origin, account)
            try {
                advanceUntilIdle()
                assertEquals(MainDestination.HOME, second.model.state.value.destination)
                assertEquals("", second.model.state.value.query)
                assertNull(second.model.state.value.selectedPlaylist)
            } finally { second.model.viewModelScope.cancel() }
        }
        Dispatchers.resetMain()
    }

    @Test fun logoutRemovesSavedNavigationImmediatelyAndMalformedStateIsRejected() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val handle = SavedStateHandle()
        val f = fixture(handle)
        try {
            advanceUntilIdle(); f.model.navigate(MainDestination.LIBRARY); advanceUntilIdle()
            assertNotNull(handle.get<Any>(ClientNavigationState.KEY))
            f.model.logout()
            assertNull(handle.get<Any>(ClientNavigationState.KEY))
            advanceUntilIdle()
            handle[ClientNavigationState.KEY] = arrayListOf("bad", "state")
            assertNull(ClientNavigationState(handle).restore(f.connection))
            assertNull(handle.get<Any>(ClientNavigationState.KEY))
        } finally { f.model.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun systemFavoritesUpdateTheSameAccountWithoutFeedbackOrRevisionRollback() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val changes = SystemMediaFavoriteChanges()
        val f = fixture(SavedStateHandle(), changes = changes)
        try {
            advanceUntilIdle()
            val detail = PlaylistDetail(f.api.favorite.copy(revision = 5), listOf(PlaylistSong(f.api.song)))
            changes.publish(f.connection, detail); advanceUntilIdle()
            assertEquals(setOf("fresh-detail"), f.model.state.value.favoriteSongIds)
            changes.publish(f.connection, PlaylistDetail(f.api.favorite.copy(revision = 2), emptyList())); advanceUntilIdle()
            assertEquals(setOf("fresh-detail"), f.model.state.value.favoriteSongIds)
            val other = f.connection.copy(network = SessionNetwork(InstanceAddress.parse("https://music.example")))
            changes.publish(other, PlaylistDetail(f.api.favorite.copy(revision = 99), emptyList())); advanceUntilIdle()
            assertEquals(setOf("fresh-detail"), f.model.state.value.favoriteSongIds)
        } finally { f.model.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun remoteArtistRouteRetainsItsOpaqueIdAcrossRecreationAndRecentSearch() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val handle = SavedStateHandle()
        val first = fixture(handle)
        advanceUntilIdle()
        first.model.openArtist(Artist("An artist", id = "yt:artist:UCfixture")); advanceUntilIdle()
        first.model.saveNavigationState(); first.model.viewModelScope.cancel()
        val second = fixture(handle)
        try {
            advanceUntilIdle()
            assertEquals("yt:artist:UCfixture", second.model.state.value.selectedArtist?.id)
            assertEquals(listOf("yt:artist:UCfixture"), second.api.requestedArtists)
            assertTrue(second.api.artistAlbumFilters.isEmpty())
            assertEquals(listOf("album-one"), second.model.state.value.artistAlbums.map { it.id })
            second.model.openRecentSearch(SearchEntity.fromArtist("An artist", "yt:artist:UCother")); advanceUntilIdle()
            assertEquals("yt:artist:UCother", second.api.requestedArtists.last())
        } finally { second.model.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun legalLongAlbumAndArtistRoutesSurviveRecreationWithoutTruncation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        for (artist in listOf(false, true)) {
            val handle = SavedStateHandle()
            val first = fixture(handle)
            advanceUntilIdle()
            val route = if (artist) first.model.state.value.copy(selectedArtist = Artist("A".repeat(300)))
                else first.model.state.value.copy(selectedAlbum = Album("A".repeat(3200), "Long album", "Artist"))
            ClientNavigationState(handle).save(first.connection, route)
            first.model.viewModelScope.cancel()
            val second = fixture(handle)
            try {
                advanceUntilIdle()
                if (artist) assertEquals("A".repeat(300), second.model.state.value.selectedArtist?.name)
                else {
                    assertEquals("A".repeat(3200), second.model.state.value.selectedAlbum?.id)
                    assertEquals(listOf("A".repeat(3200)), second.api.requestedAlbums)
                }
            } finally { second.model.viewModelScope.cancel() }
        }
        Dispatchers.resetMain()
    }

    @Test fun restoredUnreadMetadataNeverPretendsItsCountsAreZero() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val handle = SavedStateHandle()
        val f = fixture(handle)
        try {
            advanceUntilIdle()
            val navigation = ClientNavigationState(handle)
            navigation.save(f.connection, f.model.state.value.copy(selectedAlbum = f.api.album.copy(trackCount = 12)))
            val album = navigation.restore(f.connection)!!.applyTo(ClientUiState()).selectedAlbum!!
            assertNull(album.knownTrackCount)
            navigation.save(f.connection, f.model.state.value.copy(selectedArtist = Artist("An artist", 12, 4)))
            val artist = navigation.restore(f.connection)!!.applyTo(ClientUiState()).selectedArtist!!
            assertNull(artist.knownSongCount)
            assertNull(artist.knownAlbumCount)
        } finally { f.model.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun recentSearchPersistsCaseSensitiveOpaqueArtistIds() {
        val values = mutableMapOf<String, String>()
        val store = SearchHistoryStore(object : HistoryStorage {
            override fun read(key: String) = values[key]
            override fun write(key: String, value: String) { values[key] = value }
            override fun remove(key: String) { values.remove(key) }
        })
        val owner = HistoryIdentity("https://music.example", "same-account")
        store.addEntity(owner, SearchEntity.fromArtist("Same name", "yt:artist:UCabc"))
        store.addEntity(owner, SearchEntity.fromArtist("Same name", "yt:artist:UCABC"))
        store.addEntity(owner, SearchEntity.fromArtist("Same name"))
        assertEquals(setOf("yt:artist:UCabc", "yt:artist:UCABC", "Same name"), store.load(owner).entities.map { it.id }.toSet())
    }

    private fun TestScope.fixture(handle: SavedStateHandle, origin: String = "https://music.example", account: String = "same-account",
        changes: SystemMediaFavoriteChanges? = null): RouteFixture {
        val api = RouteApi()
        val connection = ActiveConnection(SessionNetwork(InstanceAddress.parse(origin)), api,
            AuthSession(Account(account, "listener", "Listener"), "fixture-csrf", false))
        val connections = RouteConnections(connection)
        val model = ClientViewModel(connections, RoutePlayback(), StandardTestDispatcher(testScheduler),
            savedStateHandle = handle, systemMediaFavorites = changes)
        return RouteFixture(model, api, connection)
    }
}

private data class RouteFixture(val model: ClientViewModel, val api: RouteApi, val connection: ActiveConnection)
private class RouteApi : AccountApi {
    val album = Album("album-one", "Evening", "Artist")
    val favorite = PlaylistSummary("liked", "Liked", "favorite", revision = 3)
    val personal = PlaylistSummary("personal", "Personal", "playlist", revision = 4)
    val song = Song("fresh-detail", "Fresh", audioUrl = "https://music.example/media.mp3")
    val requestedAlbums = mutableListOf<String>()
    val requestedArtists = mutableListOf<String>()
    val artistAlbumFilters = mutableListOf<String>()
    override fun songs(query: String, offset: Int) = SongPage(listOf(song), 1, false)
    override fun favorites() = emptyList<Song>()
    override fun albums(offset: Int) = AlbumPage(listOf(album), 1, false)
    override fun album(id: String): AlbumDetail { requestedAlbums += id; return AlbumDetail(album.copy(id = id), listOf(song)) }
    override fun playlists() = listOf(favorite, personal)
    override fun playlist(id: String) = PlaylistDetail(if (id == favorite.id) favorite else personal,
        if (id == favorite.id) emptyList() else listOf(PlaylistSong(song)))
    override fun artists(query: String, offset: Int, language: String) = ArtistPage(emptyList(), 0, false)
    override fun artist(name: String, offset: Int): ArtistDetail {
        requestedArtists += name
        val id = name.takeIf { it.startsWith("yt:artist:") }
        return ArtistDetail(Artist(if (id == null) name else "An artist", id = id), listOf(song), 1, false, albums = listOf(album))
    }
    override fun browseAlbums(query: String, offset: Int, language: String, artist: String?): AlbumPage {
        artist?.let(artistAlbumFilters::add)
        return AlbumPage(listOf(album), 1, false)
    }
    override fun changePassword(current: String, replacement: String) {}
    override fun logout() {}
}
private class RouteConnections(override var current: ActiveConnection?) : Connections {
    override val lastOrigin = current!!.network.address.origin
    override val invalidations = MutableSharedFlow<ClientNotice>(extraBufferCapacity = 1)
    override suspend fun restore() = current
    override suspend fun login(address: String, username: String, password: String) = current!!
    override suspend fun changePassword(current: String, replacement: String) {}
    override suspend fun logout() { current = null }
}
private class RoutePlayback : PlaybackGateway {
    override val state = MutableStateFlow(PlaybackSnapshot())
    override val imageLoader: ImageLoader? = null
    override suspend fun connect(connection: ActiveConnection) {}
    override fun play(songs: List<Song>, index: Int) {}
    override fun setVolume(volume: Float) {}
    override fun togglePlay() {}
    override fun next() {}
    override fun previous() {}
    override fun seekTo(positionMs: Long) {}
    override fun toggleShuffle() {}
    override fun cycleRepeat() {}
    override fun select(index: Int) {}
    override fun retry() {}
    override fun stopAndClear() { state.value = PlaybackSnapshot() }
    override fun release() {}
}
