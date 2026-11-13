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
class SongContextViewModelTest {
    @Test fun albumRelationOpensDirectlyWithoutAnAlreadyLoadedAlbumIndex() = runTest {
        val f = fixture()
        try {
            advanceUntilIdle()
            assertTrue(f.model.state.value.albums.isEmpty())
            f.model.openAlbum(f.api.selected.relatedAlbum()!!); advanceUntilIdle()
            assertEquals(listOf("yt:album:MPREb_selected"), f.api.albumReads)
            assertEquals("yt:album:MPREb_selected", f.model.state.value.selectedAlbum!!.id)
            f.model.openAlbum(Album("../bad", "Untrusted", "")); advanceUntilIdle()
            assertEquals(1, f.api.albumReads.size)
        } finally { f.close() }
    }

    @Test fun explicitAlbumRowWinsOverSameVideoInOlderQueueAndStatsForSingleAndMultiPlay() = runTest {
        val f = fixture()
        try {
            advanceUntilIdle()
            f.player.state.value = PlaybackSnapshot(queue = listOf(f.api.fresh), current = f.api.fresh, currentIndex = 0)
            advanceUntilIdle()
            f.model.openAlbum(f.api.selected.relatedAlbum()!!); advanceUntilIdle()
            val selected = f.model.state.value.albumSongs.single()
            f.model.playSong(selected); advanceUntilIdle()
            assertEquals(selected, f.player.plays.last().single())
            f.model.playSongs(listOf(selected.copy(audioUrl = "https://foreign.example/injected")), 0); advanceUntilIdle()
            assertEquals(selected, f.player.plays.last().single())
            assertEquals("yt:album:MPREb_selected", f.player.state.value.current!!.albumId)
            assertEquals("https://music.example/media/selected-cover", f.player.state.value.current!!.coverUrl)
            assertEquals(0, f.api.resolveReads)
        } finally { f.close() }
    }

    @Test fun unknownCallerMetadataCannotSupplyMediaOrInventAnAlbumContext() = runTest {
        val f = fixture()
        try {
            advanceUntilIdle()
            val supplied = f.api.selected.copy(id = "yt:otherabcdef", audioUrl = "https://foreign.example/injected")
            f.model.playSongs(listOf(supplied), 0); advanceUntilIdle()
            val played = f.player.plays.single().single()
            assertEquals(supplied.id, played.id)
            assertEquals("https://music.example/media/renewed", played.audioUrl)
            assertEquals("yt:album:MPREb_original", played.albumId)
            assertEquals(1, f.api.resolveReads)
        } finally { f.close() }
    }

    @Test fun queueRestoreRefreshesMediaAndKeepsEachOccurrenceSelectedAlbum() = runTest {
        val disk = InMemoryPlaybackQueueStore()
        val first = contextSong("yt:album:MPREb_one", "Edition one")
        val second = contextSong("yt:album:MPREb_two", "Edition two")
        disk.write(owner, SavedPlaybackQueue(listOf(metadataEntry(first), metadataEntry(second)), 1, 9000, true, 2))
        val repository = PlaybackQueueRepository(disk, backgroundScope, StandardTestDispatcher(testScheduler))
        val f = fixture(queue = repository)
        try {
            advanceUntilIdle(); runCurrent(); advanceUntilIdle()
            val restored = f.player.restores.single()
            assertEquals(listOf(first.albumId, second.albumId), restored.queue.map { it.albumId })
            assertEquals(listOf("Edition one", "Edition two"), restored.queue.map { it.album })
            assertTrue(restored.queue.all { it.audioUrl == "https://music.example/media/renewed" })
            assertEquals(1, restored.currentIndex); assertEquals(9000L, restored.positionMs)
            assertTrue(restored.shuffle); assertEquals(2, restored.repeatMode)
        } finally { f.close() }
    }

    @Test fun deviceHistoryHydrationAndExplicitReplayKeepSavedEditionWithFreshMedia() = runTest {
        val disk = InMemoryPlaybackHistoryStore()
        val stored = contextSong("yt:album:MPREb_selected", "Saved edition")
        disk.write(owner, listOf(HistoryEntry(stored.id, stored.title, stored.artist, stored.album,
            stored.durationSeconds, stored.language, 1, stored.relations)))
        val repository = PlaybackHistoryRepository(disk, backgroundScope, StandardTestDispatcher(testScheduler))
        val f = fixture(history = repository)
        try {
            advanceUntilIdle(); runCurrent(); advanceUntilIdle()
            val displayed = f.model.state.value.playHistory.single()
            assertEquals(stored.albumId, displayed.albumId); assertEquals("Saved edition", displayed.album)
            f.model.playHistorySong(displayed); advanceUntilIdle()
            val played = f.player.plays.single().single()
            assertEquals(stored.albumId, played.albumId); assertEquals("Saved edition", played.album)
            assertEquals("https://music.example/media/renewed-single", played.audioUrl)
        } finally { f.close() }
    }

    @Test fun lateResolveCannotPlayAfterTheSessionLogsOut() = runTest {
        val f = fixture()
        try {
            advanceUntilIdle()
            f.api.onResolve = { f.model.logout() }
            f.model.playSongs(listOf(contextSong("yt:album:MPREb_selected", "Caller").copy(id = "yt:otherabcdef")), 0)
            advanceUntilIdle()
            assertTrue(f.player.plays.isEmpty())
            assertEquals(ClientScreen.CONNECT, f.model.state.value.screen)
        } finally { f.close() }
    }

    private fun TestScope.fixture(history: PlaybackHistoryRepository? = null, queue: PlaybackQueueRepository? = null): ContextFixture {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val api = ContextApi()
        val player = ContextPlayback()
        val connection = ActiveConnection(SessionNetwork(InstanceAddress.parse(owner.origin)), api,
            AuthSession(Account(owner.accountId, "listener", "Listener"), "fixture-only", false))
        return ContextFixture(ClientViewModel(ContextConnections(connection), player, dispatcher, history, queue), api, player, connection)
    }
    private data class ContextFixture(val model: ClientViewModel, val api: ContextApi, val player: ContextPlayback,
        val connection: ActiveConnection) {
        fun close() { model.viewModelScope.cancel(); connection.network.retire(); Dispatchers.resetMain() }
    }
    companion object {
        private val owner = HistoryIdentity("https://music.example", "context-account")
        private fun contextSong(albumId: String, album: String) = Song("yt:abcdefghijk", "Song", "Britney Spears", album, 212.0,
            "https://music.example/media/selected", "https://music.example/media/selected-cover", "en", "youtube",
            listOf(SongArtist("yt:artist:UCbritney", "Britney Spears")), "yt:artist:UCbritney", albumId)
    }
    private class ContextApi : AccountApi {
        val selected = contextSong("yt:album:MPREb_selected", "Selected edition")
        val fresh = contextSong("yt:album:MPREb_original", "Original edition").copy(
            audioUrl = "https://music.example/media/renewed", coverUrl = "https://music.example/media/original-cover")
        var resolveReads = 0
        var onResolve: (() -> Unit)? = null
        val albumReads = mutableListOf<String>()
        override fun songs(query: String, offset: Int) = SongPage(listOf(fresh), 1, false)
        override fun albums(offset: Int) = AlbumPage(emptyList(), 0, false)
        override fun album(id: String): AlbumDetail {
            albumReads += id
            return AlbumDetail(Album(id, "Selected edition", "Britney Spears"), listOf(selected.copy(albumId = id)))
        }
        override fun favorites() = emptyList<Song>()
        override fun playlists() = emptyList<PlaylistSummary>()
        override fun resolveSongs(ids: List<String>): List<Song> { resolveReads++; onResolve?.invoke(); return ids.map { fresh.copy(id = it) } }
        override fun song(id: String) = fresh.copy(id = id, audioUrl = "https://music.example/media/renewed-single")
        override fun playStats(limit: Int, summaryOnly: Boolean) = PlayStats(songs = listOf(RankedSong(fresh, 1, 1)))
        override fun changePassword(current: String, replacement: String) {}
        override fun logout() {}
    }
    private class ContextConnections(override var current: ActiveConnection?) : Connections {
        override val lastOrigin = current!!.network.address.origin
        override val invalidations = MutableSharedFlow<ClientNotice>(extraBufferCapacity = 1)
        override suspend fun restore() = current
        override suspend fun login(address: String, username: String, password: String) = current!!
        override suspend fun changePassword(current: String, replacement: String) {}
        override suspend fun logout() { current = null }
    }
    private class ContextPlayback : PlaybackGateway {
        override val state = MutableStateFlow(PlaybackSnapshot())
        override val imageLoader: ImageLoader? = null
        val plays = mutableListOf<List<Song>>()
        val restores = mutableListOf<PlaybackSnapshot>()
        override suspend fun connect(connection: ActiveConnection) {}
        override fun play(songs: List<Song>, index: Int) {
            plays += songs
            state.value = PlaybackSnapshot(current = songs[index], queue = songs, currentIndex = index)
        }
        override fun restoreQueue(songs: List<Song>, currentIndex: Int, positionMs: Long, shuffle: Boolean, repeatMode: Int) {
            state.value = PlaybackSnapshot(current = songs[currentIndex], queue = songs, currentIndex = currentIndex,
                positionMs = positionMs, shuffle = shuffle, repeatMode = repeatMode)
            restores += state.value
        }
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
}
