package com.arcinks.flaretune

import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.arcinks.flaretune.data.*
import com.arcinks.flaretune.model.*
import com.arcinks.flaretune.playback.PlaybackGateway
import com.arcinks.flaretune.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchRetryContractsTest {
    @Test fun eachSearchRetryTouchesOnlyItsOwnCategoryAndRetainsTheOthers() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val api = SearchApi()
        val network = SessionNetwork(InstanceAddress.parse("https://music.example"))
        val connection = ActiveConnection(network, api, AuthSession(Account("account", "listener", "Listener"), "csrf", false))
        val model = ClientViewModel(SearchConnections(connection), SearchPlayback(), dispatcher)
        try {
            advanceUntilIdle()
            model.navigate(MainDestination.SEARCH)
            model.search("current query")
            advanceUntilIdle()
            val successful = model.state.value

            for (category in listOf("songs", "artists", "albums")) {
                api.calls.clear()
                api.failingCategory = category
                when (category) {
                    "songs" -> model.retrySearchSongs()
                    "artists" -> model.retrySearchArtists()
                    else -> model.retrySearchAlbums()
                }
                advanceUntilIdle()
                assertEquals(listOf("$category:current query:0"), api.calls)
                val failed = model.state.value
                assertEquals(successful.songs, failed.songs)
                assertEquals(successful.artists, failed.artists)
                assertEquals(successful.searchAlbums, failed.searchAlbums)
                assertEquals(if (category == "songs") ClientNotice.REQUEST_FAILED else null, failed.notice)
                assertEquals(if (category == "artists") ClientNotice.REQUEST_FAILED else null, failed.artistsNotice)
                assertEquals(if (category == "albums") ClientNotice.REQUEST_FAILED else null, failed.albumSearchNotice)

                api.failingCategory = null
                api.calls.clear()
                when (category) {
                    "songs" -> model.retrySearchSongs()
                    "artists" -> model.retrySearchArtists()
                    else -> model.retrySearchAlbums()
                }
                advanceUntilIdle()
                assertEquals(listOf("$category:current query:0"), api.calls)
                assertNull(model.state.value.notice)
                assertNull(model.state.value.artistsNotice)
                assertNull(model.state.value.albumSearchNotice)
            }

            api.failingCategory = "all"
            model.search("all failed")
            advanceUntilIdle()
            api.failingCategory = null
            api.calls.clear()
            model.retrySearchSongs()
            advanceUntilIdle()
            assertEquals(listOf("songs:all failed:0"), api.calls)
            assertNull(model.state.value.notice)
            assertEquals(ClientNotice.REQUEST_FAILED, model.state.value.artistsNotice)
            assertEquals(ClientNotice.REQUEST_FAILED, model.state.value.albumSearchNotice)
        } finally {
            model.viewModelScope.cancel()
            network.retire()
            Dispatchers.resetMain()
        }
    }

    @Test fun retryResponseSupersededByANewQueryCannotReplaceNewResults() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val api = SearchApi()
        val network = SessionNetwork(InstanceAddress.parse("https://music.example"))
        val connection = ActiveConnection(network, api, AuthSession(Account("account", "listener", "Listener"), "csrf", false))
        val model = ClientViewModel(SearchConnections(connection), SearchPlayback(), dispatcher)
        try {
            advanceUntilIdle()
            model.navigate(MainDestination.SEARCH)
            model.search("old")
            advanceUntilIdle()
            api.onSongs = {
                api.onSongs = null
                model.search("new")
            }
            model.retrySearchSongs()
            advanceUntilIdle()
            assertEquals("new", model.state.value.query)
            assertEquals(listOf("song-new"), model.state.value.songs.map { it.id })
            assertEquals(listOf("artist-new"), model.state.value.artists.map { it.name })
            assertEquals(listOf("album-new"), model.state.value.searchAlbums.map { it.id })

            model.navigate(MainDestination.HOME)
            advanceUntilIdle()
            api.calls.clear()
            model.retrySearchSongs()
            model.retrySearchArtists()
            model.retrySearchAlbums()
            advanceUntilIdle()
            assertTrue(api.calls.isEmpty())
        } finally {
            model.viewModelScope.cancel()
            network.retire()
            Dispatchers.resetMain()
        }
    }

    private class SearchApi : AccountApi {
        val calls = mutableListOf<String>()
        var failingCategory: String? = null
        var onSongs: (() -> Unit)? = null
        private fun record(category: String, query: String, offset: Int) {
            calls += "$category:$query:$offset"
            if (failingCategory == category || failingCategory == "all") throw ApiFailure(Failure.REQUEST, 503, "service_unavailable")
        }
        override fun songs(query: String, offset: Int): SongPage {
            record("songs", query, offset)
            onSongs?.invoke()
            return SongPage(listOf(Song("song-$query", query, audioUrl = "https://music.example/media/song")), 20, false)
        }
        override fun artists(query: String, offset: Int, language: String): ArtistPage {
            record("artists", query, offset)
            return ArtistPage(listOf(Artist("artist-$query")), 20, false)
        }
        override fun browseAlbums(query: String, offset: Int, language: String, artist: String?): AlbumPage {
            record("albums", query, offset)
            return AlbumPage(listOf(Album("album-$query", query, "artist")), 20, false)
        }
        override fun favorites() = emptyList<Song>()
        override fun albums(offset: Int) = AlbumPage(emptyList(), 0, false)
        override fun changePassword(current: String, replacement: String) {}
        override fun logout() {}
    }

    private class SearchConnections(override var current: ActiveConnection?) : Connections {
        override val lastOrigin = "https://music.example"
        override val invalidations = MutableSharedFlow<ClientNotice>()
        override suspend fun restore() = current
        override suspend fun login(address: String, username: String, password: String) = current!!
        override suspend fun changePassword(current: String, replacement: String) {}
        override suspend fun logout() { current = null }
    }

    private class SearchPlayback : PlaybackGateway {
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
        override fun stopAndClear() {}
        override fun release() {}
    }
}
