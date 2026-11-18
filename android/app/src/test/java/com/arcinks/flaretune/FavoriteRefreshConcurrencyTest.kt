package com.arcinks.flaretune

import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.arcinks.flaretune.data.*
import com.arcinks.flaretune.model.*
import com.arcinks.flaretune.playback.PlaybackGateway
import com.arcinks.flaretune.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteRefreshConcurrencyTest {
    @Test fun lateFavoriteGetCannotRollbackNewSystemAcknowledgementOrBroadcastOldMembership() = runTest {
        val f = fixture()
        try {
            f.ready()
            val request = f.api.blockNextDetail()
            f.model.refreshFavorites(); request.entered.await()
            f.acknowledge(detail(4, "new-one", "new-two"))
            request.release()
            f.request("favoritesJob").join()
            assertLatest(f, 4, "new-one", "new-two")
            assertFalse(f.model.state.value.favoritesLoading)
            assertTrue(f.broadcasts.contains(4L))
            assertTrue(f.broadcasts.all { it >= 4 })
        } finally { f.close() }
    }

    @Test fun explicitPlayFavoritesUsesNewCompleteAcknowledgementInsteadOfTheLateOldGet() = runTest {
        val f = fixture()
        try {
            f.ready()
            val request = f.api.blockNextDetail()
            f.model.playFavorites(); request.entered.await()
            val intent = f.request("playJob")
            f.acknowledge(detail(4, "new-one", "new-two", "new-three"))
            request.release(); intent.join()
            assertFalse(intent.isCancelled)
            assertLatest(f, 4, "new-one", "new-two", "new-three")
            assertEquals(listOf("new-one", "new-two", "new-three"), f.player.plays.single().map { it.id })
            assertTrue(f.broadcasts.all { it >= 4 })
        } finally { f.close() }
    }

    @Test fun lateIndexCannotLowerFavoriteMetadataThenAdmitAnOldDetail() = runTest {
        val f = fixture()
        try {
            f.ready()
            val request = f.api.blockNextIndex()
            f.model.refreshPlaylists(); request.entered.await()
            f.acknowledge(detail(4, "new-one"))
            request.release(); f.request("playlistsJob").join(); f.request("favoritesJob").join()
            assertLatest(f, 4, "new-one")
            assertTrue("Observed publication revisions: " + f.broadcasts, f.broadcasts.all { it >= 4 })
        } finally { f.close() }
    }

    @Test fun lateOpenFavoriteDetailAndReopeningWithOldDataKeepTheConfirmedMembersAndRevision() = runTest {
        val f = fixture()
        try {
            f.ready()
            val request = f.api.blockNextDetail()
            f.model.openPlaylist(f.api.favorite.playlist); request.entered.await()
            f.acknowledge(detail(4, "new-one", "new-two"))
            request.release(); f.request("playlistJob").join()
            assertLatest(f, 4, "new-one", "new-two")
            assertEquals(4L, f.model.state.value.selectedPlaylist!!.revision)
            assertEquals(listOf("new-one", "new-two"), f.model.state.value.selectedPlaylistEntries.map { it.song.id })
            f.model.openPlaylist(f.model.state.value.selectedPlaylist!!); f.request("playlistJob").join()
            assertEquals(4L, f.model.state.value.selectedPlaylist!!.revision)
            assertEquals(listOf("new-one", "new-two"), f.model.state.value.selectedPlaylistEntries.map { it.song.id })
            assertFalse(f.model.state.value.playlistLoading)
            assertNull(f.model.state.value.playlistNotice)
        } finally { f.close() }
    }

    @Test fun missingFavoriteInALateIndexCannotEraseKnownFavoriteOrItsOpenPage() = runTest {
        val f = fixture()
        try {
            f.ready(); f.model.openPlaylist(f.api.favorite.playlist); f.request("playlistJob").join()
            f.api.indexOverride = emptyList()
            val request = f.api.blockNextIndex()
            f.model.refreshPlaylists(); request.entered.await()
            f.acknowledge(detail(4, "new-one"))
            request.release(); f.request("playlistsJob").join(); f.request("favoritesJob").join()
            assertLatest(f, 4, "new-one")
            assertEquals("liked", f.model.state.value.selectedPlaylist!!.id)
            assertEquals(listOf("new-one"), f.model.state.value.selectedPlaylistEntries.map { it.song.id })
        } finally { f.close() }
    }

    @Test fun nullFromAnInFlightNoFavoriteLookupCannotClearANewerAcknowledgement() = runTest {
        val f = fixture(withFavorite = false)
        try {
            f.ready()
            val request = f.api.blockNextIndex()
            f.model.refreshFavorites(); request.entered.await()
            f.acknowledge(detail(4, "new-one"))
            request.release(); f.request("favoritesJob").join()
            assertLatest(f, 4, "new-one")
            assertTrue(f.broadcasts.all { it >= 4 })
        } finally { f.close() }
    }

    @Test fun newerSummaryWithOlderMembersCannotCreateAFalseCompletePlaybackAndRetryIsBounded() = runTest {
        val f = fixture()
        try {
            f.ready()
            f.api.indexOverride = listOf(f.api.favorite.playlist.copy(revision = 5))
            f.api.favorite = detail(4, "still-old")
            f.model.refreshPlaylists(); f.request("playlistsJob").join(); f.request("favoritesJob").join()
            assertEquals(5L, f.model.state.value.playlists.single { it.kind == "favorite" }.revision)
            val before = f.api.detailReads.get()
            f.model.playFavorites(); f.request("playJob").join()
            assertTrue(f.player.plays.isEmpty())
            assertEquals(2, f.api.detailReads.get() - before)
            assertEquals(ClientNotice.REQUEST_FAILED, f.model.state.value.favoritesNotice)
            f.api.favorite = detail(5, "confirmed-one", "confirmed-two")
            f.model.playFavorites(); f.request("playJob").join()
            assertEquals(listOf("confirmed-one", "confirmed-two"), f.player.plays.single().map { it.id })
            assertLatest(f, 5, "confirmed-one", "confirmed-two")
        } finally { f.close() }
    }

    @Test fun logoutAndEnteringAnotherIdentityResetTheFavoriteRevisionFloor() = runTest {
        val f = fixture()
        val secondNetwork = SessionNetwork(InstanceAddress.parse("https://music.example"))
        try {
            f.ready(); f.acknowledge(detail(8, "private-old-account"))
            f.model.logout(); f.model.state.first { it.screen == ClientScreen.CONNECT && !it.busy }
            val secondApi = DelayedFavoriteApi(detail(1, "other-account"))
            val second = ActiveConnection(secondNetwork, secondApi,
                AuthSession(Account("second-account", "second", "Second"), "fixture-csrf", false))
            f.connections.nextLogin = second
            f.model.login("https://music.example", "second", "password")
            f.model.state.first { it.screen == ClientScreen.LIBRARY && it.playbackReady }
            f.request("playlistsJob").join(); f.request("favoritesJob").join()
            assertLatest(f, 1, "other-account")
            f.changes.publish(f.connection, detail(99, "late-old-account")); runCurrent()
            assertLatest(f, 1, "other-account")
        } finally { secondNetwork.retire(); f.close() }
    }

    @Test fun playbackBecomesReadyOnlyAfterConnectAndReadsItsLatestNonEmittingSnapshot() = runTest {
        val connect = CompletableDeferred<Unit>()
        val player = RecordingPlayback(nonEmitting = true, connectGate = connect)
        player.connectSnapshot = snapshot(listOf(song("already-playing")), 0, 1234)
        val f = fixture(player = player)
        try {
            player.connectEntered.await()
            assertEquals(ClientScreen.LIBRARY, f.model.state.value.screen)
            assertFalse(f.model.state.value.playbackReady)
            connect.complete(Unit)
            f.model.state.first { it.playbackReady }
            assertEquals("already-playing", f.model.state.value.playback.current?.id)
            assertEquals(1234L, f.model.state.value.playback.positionMs)
        } finally { connect.complete(Unit); f.close() }
    }

    @Test fun queueRestoreFinallyPublishesLatestSnapshotWithoutWaitingForAPlayerEmission() = runTest {
        val player = RecordingPlayback(nonEmitting = true)
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val load = Gate()
        val saved = SavedPlaybackQueue(listOf(QueueEntry("restored", "Restored")), 0, 4321, true, 2)
        val repository = PlaybackQueueRepository(object : PlaybackQueueStore {
            override fun read(identity: HistoryIdentity): SavedPlaybackQueue { load.block(); return saved }
            override fun write(identity: HistoryIdentity, queue: SavedPlaybackQueue) = Unit
            override fun clear(identity: HistoryIdentity) = Unit
        }, backgroundScope, Dispatchers.IO)
        val f = fixture(player = player, repository = repository)
        try {
            load.entered.await()
            f.model.state.first { it.playbackReady && it.queueRestoring }
            load.release()
            f.model.state.first { it.playbackReady && !it.queueRestoring }
            assertEquals("restored", f.model.state.value.playback.current?.id)
            assertEquals(4321L, f.model.state.value.playback.positionMs)
            assertTrue(f.model.state.value.playback.shuffle)
            assertEquals(2, f.model.state.value.playback.repeatMode)
            assertFalse(f.model.state.value.playback.playing)
        } finally { load.release(); f.close() }
    }

    private fun TestScope.fixture(withFavorite: Boolean = true, player: RecordingPlayback = RecordingPlayback(),
        repository: PlaybackQueueRepository? = null): Fixture {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api = DelayedFavoriteApi(if (withFavorite) detail(3, "old") else detail(0))
        if (!withFavorite) api.indexOverride = emptyList()
        val connection = ActiveConnection(SessionNetwork(InstanceAddress.parse("https://music.example")), api,
            AuthSession(Account("account-one", "listener", "Listener"), "fixture-csrf", false))
        val connections = TestConnections(connection)
        val changes = SystemMediaFavoriteChanges()
        val model = ClientViewModel(connections, player, Dispatchers.IO, queueRepository = repository, systemMediaFavorites = changes)
        val f = Fixture(model, api, connection, connections, changes, player)
        // Record at publication time: a queued setup event must not arrive after ready() clears
        // the recorder and be mistaken for a stale publication from the operation under test.
        f.observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            changes.updates.collect { f.broadcasts += it.detail.playlist.revision }
        }
        return f
    }

    private class Fixture(val model: ClientViewModel, val api: DelayedFavoriteApi, val connection: ActiveConnection,
        val connections: TestConnections, val changes: SystemMediaFavoriteChanges, val player: RecordingPlayback) {
        val broadcasts = mutableListOf<Long>()
        lateinit var observer: Job
        suspend fun ready() {
            model.state.first { it.screen == ClientScreen.LIBRARY && it.playbackReady }
            request("playlistsJob").join(); request("favoritesJob").join()
            broadcasts.clear()
        }
        suspend fun acknowledge(acknowledged: PlaylistDetail) {
            changes.publish(connection, acknowledged)
            model.state.first { it.favoriteSongIds == acknowledged.songIds.toSet() }
        }
        fun request(name: String): Job = model.javaClass.getDeclaredField(name).run {
            isAccessible = true; requireNotNull(get(model) as Job?)
        }
        fun close() {
            api.releaseAll(); observer.cancel(); model.viewModelScope.cancel()
            connection.network.retire(); connections.current?.network?.retire(); Dispatchers.resetMain()
        }
    }

    private class Gate {
        val entered = CompletableDeferred<Unit>()
        private val released = CountDownLatch(1)
        fun block() { entered.complete(Unit); check(released.await(5, TimeUnit.SECONDS)) { "Test GET was not released" } }
        fun release() = released.countDown()
    }

    private class DelayedFavoriteApi(initial: PlaylistDetail) : AccountApi {
        @Volatile var favorite = initial
        @Volatile var indexOverride: List<PlaylistSummary>? = null
        val detailReads = AtomicInteger()
        private val detailGate = AtomicReference<Gate?>()
        private val indexGate = AtomicReference<Gate?>()
        private val gates = ConcurrentLinkedQueue<Gate>()
        fun blockNextDetail() = Gate().also { gates += it; check(detailGate.compareAndSet(null, it)) }
        fun blockNextIndex() = Gate().also { gates += it; check(indexGate.compareAndSet(null, it)) }
        fun releaseAll() { gates.forEach(Gate::release) }
        override fun playlists(): List<PlaylistSummary> {
            val captured = indexOverride ?: listOf(favorite.playlist)
            indexGate.getAndSet(null)?.block()
            return captured
        }
        override fun playlist(id: String): PlaylistDetail {
            check(id == "liked")
            val captured = favorite; detailReads.incrementAndGet()
            detailGate.getAndSet(null)?.block()
            return captured
        }
        override fun songs(query: String, offset: Int) = SongPage(emptyList(), 0, false)
        override fun favorites(): List<Song> = error("Modern favorites must be used")
        override fun albums(offset: Int) = AlbumPage(emptyList(), 0, false)
        override fun song(id: String) = FavoriteRefreshConcurrencyTest.song(id)
        override fun changePassword(current: String, replacement: String) = Unit
        override fun logout() = Unit
    }

    private class TestConnections(initial: ActiveConnection) : Connections {
        @Volatile override var current: ActiveConnection? = initial
        var nextLogin: ActiveConnection? = null
        override val lastOrigin = initial.network.address.origin
        override val invalidations = MutableSharedFlow<ClientNotice>()
        override suspend fun restore() = current
        override suspend fun login(address: String, username: String, password: String): ActiveConnection =
            requireNotNull(nextLogin ?: current).also { current = it }
        override suspend fun changePassword(current: String, replacement: String) = Unit
        override suspend fun logout() { current = null }
    }

    private class RecordingPlayback(nonEmitting: Boolean = false, val connectGate: CompletableDeferred<Unit>? = null) : PlaybackGateway {
        private val normal = MutableStateFlow(PlaybackSnapshot())
        private val quiet = QuietState()
        override val state: StateFlow<PlaybackSnapshot> = if (nonEmitting) quiet else normal
        val connectEntered = CompletableDeferred<Unit>()
        var connectSnapshot: PlaybackSnapshot? = null
        val plays = mutableListOf<List<Song>>()
        override val imageLoader: ImageLoader? = null
        private fun update(next: PlaybackSnapshot) { if (state === quiet) quiet.latest = next else normal.value = next }
        override suspend fun connect(connection: ActiveConnection) { connectEntered.complete(Unit); connectGate?.await(); connectSnapshot?.let(::update) }
        override fun play(songs: List<Song>, index: Int) { plays += songs; update(snapshot(songs, index, 0).copy(playing = true)) }
        override fun restoreQueue(songs: List<Song>, currentIndex: Int, positionMs: Long, shuffle: Boolean, repeatMode: Int) {
            update(snapshot(songs, currentIndex, positionMs).copy(shuffle = shuffle, repeatMode = repeatMode))
        }
        override fun stopAndClear() = update(PlaybackSnapshot())
        override fun setVolume(volume: Float) = Unit
        override fun togglePlay() = Unit
        override fun next() = Unit
        override fun previous() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun toggleShuffle() = Unit
        override fun cycleRepeat() = Unit
        override fun select(index: Int) = Unit
        override fun retry() = Unit
        override fun release() = Unit
    }

    @OptIn(InternalCoroutinesApi::class)
    private class QuietState : StateFlow<PlaybackSnapshot> {
        @Volatile var latest = PlaybackSnapshot()
        override val value get() = latest
        override val replayCache get() = listOf(latest)
        override suspend fun collect(collector: FlowCollector<PlaybackSnapshot>): Nothing { collector.emit(latest); awaitCancellation() }
    }

    companion object {
        private fun song(id: String) = Song(id, "Track $id", durationSeconds = 120.0, audioUrl = "https://music.example/audio/$id")
        private fun detail(revision: Long, vararg ids: String) = PlaylistDetail(
            PlaylistSummary("liked", "Liked", "favorite", revision = revision), ids.map { PlaylistSong(song(it)) })
        private fun snapshot(songs: List<Song>, index: Int, position: Long) = PlaybackSnapshot(current = songs[index], queue = songs,
            currentIndex = index, positionMs = position, durationMs = 120_000)
        private fun assertLatest(f: Fixture, revision: Long, vararg ids: String) {
            assertEquals(ids.toSet(), f.model.state.value.favoriteSongIds)
            assertEquals(ids.toList(), f.model.state.value.favoriteSongs.map { it.id })
            assertEquals(revision, f.model.state.value.playlists.single { it.kind == "favorite" }.revision)
        }
    }
}
