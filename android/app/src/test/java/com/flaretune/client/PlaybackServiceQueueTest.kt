package com.flaretune.client

import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import com.flaretune.client.data.ActiveConnection
import com.flaretune.client.data.FlareTuneApi
import com.flaretune.client.data.HistoryIdentity
import com.flaretune.client.data.InstanceAddress
import com.flaretune.client.data.PlaybackQueueRepository
import com.flaretune.client.data.PlaybackQueueStore
import com.flaretune.client.data.QueueEntry
import com.flaretune.client.data.SavedPlaybackQueue
import com.flaretune.client.data.SessionNetwork
import com.flaretune.client.model.Account
import com.flaretune.client.model.AuthSession
import com.flaretune.client.model.Song
import com.flaretune.client.playback.PlaybackRuntime
import com.flaretune.client.playback.PlaybackService
import com.flaretune.client.playback.item
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ClientApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
class PlaybackServiceQueueTest {
    @Test fun anInitiallyEmptyServiceDoesNotEraseTheQueueWaitingForRestore() = runTest {
        val old = SavedPlaybackQueue(listOf(QueueEntry("saved", "Saved track")), 0, 3456, true, Player.REPEAT_MODE_ALL)
        val f = fixture(old)
        try {
            flushMain()
            assertEquals(old, f.repository.state.value)
            advanceTimeBy(2_100)
            flushMain()
            assertEquals(old, f.store.saved)
            assertEquals(0, f.store.writes)
            assertEquals(0, f.store.clears)
            f.destroy()
            flushMain()
            assertEquals(old, f.store.saved)
            assertEquals(0, f.store.clears)
        } finally { close(f) }
    }

    @Test fun realPlayerTimelineSeekModesAndEditsPersistWithoutPreparingOrPlayingAudio() = runTest {
        val f = fixture()
        try {
            flushMain()
            f.player.setMediaItems(listOf(track("one").item(), track("two").item(), track("three").item()), 1, 12_345)
            f.player.shuffleModeEnabled = true
            f.player.repeatMode = Player.REPEAT_MODE_ALL
            flushMain()
            assertEquals(listOf("one", "two", "three"), f.savedIds())
            assertEquals(1, f.store.saved!!.currentIndex)
            assertEquals(12_345L, f.store.saved!!.positionMs)
            assertTrue(f.store.saved!!.shuffle)
            assertEquals(Player.REPEAT_MODE_ALL, f.store.saved!!.repeatMode)
            assertFalse(f.player.playWhenReady)
            assertEquals(Player.STATE_IDLE, f.player.playbackState)

            f.player.seekTo(1, 23_456)
            flushMain()
            assertEquals(23_456L, f.store.saved!!.positionMs)
            f.player.moveMediaItem(0, 2)
            flushMain()
            assertEquals(listOf("two", "three", "one"), f.savedIds())
            assertEquals(0, f.store.saved!!.currentIndex)
            assertEquals(23_456L, f.store.saved!!.positionMs)
            f.player.removeMediaItem(1)
            flushMain()
            assertEquals(listOf("two", "one"), f.savedIds())
            assertEquals(0, f.store.saved!!.currentIndex)
            assertEquals(23_456L, f.store.saved!!.positionMs)
            f.player.removeMediaItem(1)
            f.player.removeMediaItem(0)
            flushMain()
            assertNull(f.store.saved)
            assertNull(f.repository.state.value)
            assertTrue(f.store.clears > 0)
        } finally { close(f) }
    }

    @Test fun runtimeClearRetiresAuthorityBeforeLateEventsAndServiceDestruction() = runTest {
        val f = fixture()
        try {
            flushMain()
            f.player.setMediaItems(listOf(track("one").item()), 0, 1234)
            flushMain()
            assertEquals(listOf("one"), f.savedIds())
            val writes = f.store.writes
            f.runtime.clear()
            assertNull(f.runtime.connection)
            flushMain()
            assertNull(f.store.saved)
            f.player.setMediaItems(listOf(track("late").item()), 0, 9876)
            flushMain()
            f.destroy()
            flushMain()
            assertEquals(writes, f.store.writes)
            assertNull(f.store.saved)
            assertNull(f.repository.state.value)
        } finally { close(f) }
    }

    @Test fun inactiveNetworkCannotPersistLateSeekOrFinalSnapshot() = runTest {
        val f = fixture()
        try {
            flushMain()
            f.player.setMediaItems(listOf(track("one").item()), 0, 1234)
            flushMain()
            val saved = f.store.saved
            val writes = f.store.writes
            f.network.retire()
            f.player.seekTo(4567)
            flushMain()
            f.destroy()
            flushMain()
            assertEquals(saved, f.store.saved)
            assertEquals(writes, f.store.writes)
        } finally { close(f) }
    }

    private fun TestScope.fixture(initial: SavedPlaybackQueue? = null): Fixture {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val app = RuntimeEnvironment.getApplication() as ClientApplication
        val store = RecordingStore(initial)
        val repository = PlaybackQueueRepository(store, backgroundScope, dispatcher)
        val runtime = PlaybackRuntime(app, queueRepository = repository)
        // Keep injection in the test; production Service constructs its real ExoPlayer unchanged.
        app.javaClass.getDeclaredField("playbackRuntime\$delegate").apply {
            isAccessible = true
            set(app, lazyOf(runtime))
        }
        val base = OkHttpClient.Builder().addInterceptor { throw AssertionError("No media/network request is allowed in this lifecycle test") }.build()
        val network = SessionNetwork(InstanceAddress.parse("https://music.example"), baseClient = base)
        runtime.install(ActiveConnection(network, object : com.flaretune.client.data.AccountApi {
            override fun songs(query: String, offset: Int): com.flaretune.client.model.SongPage = error("Unexpected songs read")
            override fun favorites(): List<Song> = error("Unexpected legacy favorites read")
            override fun changePassword(current: String, replacement: String) = error("Unexpected password write")
            override fun logout() = error("Unexpected logout")
            override fun playlists() = listOf(com.flaretune.client.model.PlaylistSummary("queue-favorite", "Favorites", "favorite"))
            override fun playlist(id: String): com.flaretune.client.model.PlaylistDetail {
                check(id == "queue-favorite")
                return com.flaretune.client.model.PlaylistDetail(playlists().single(), emptyList())
            }
        },
            AuthSession(Account("queue-account", "listener", "Listener"), "fixture-csrf", false)))
        val controller = Robolectric.buildService(PlaybackService::class.java).create()
        val session = PlaybackService::class.java.getDeclaredField("mediaSession").run {
            isAccessible = true
            get(controller.get()) as MediaSession
        }
        return Fixture(store, repository, runtime, network, base, controller, session.player)
    }

    private fun TestScope.flushMain() {
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
    }

    private fun TestScope.close(fixture: Fixture) {
        try {
            fixture.runtime.clear()
            fixture.destroy()
            flushMain()
        } finally {
            fixture.network.retire()
            fixture.base.dispatcher.executorService.shutdownNow()
            fixture.base.connectionPool.evictAll()
            Dispatchers.resetMain()
        }
    }

    private fun track(id: String) = Song(id, id, "Singer", "Album", 40.0,
        "https://music.example/media/$id.mp3", language = "en")

    private class RecordingStore(var saved: SavedPlaybackQueue?) : PlaybackQueueStore {
        var writes = 0
        var clears = 0
        override fun read(identity: HistoryIdentity): SavedPlaybackQueue? = saved
        override fun write(identity: HistoryIdentity, queue: SavedPlaybackQueue) { saved = queue; writes++ }
        override fun clear(identity: HistoryIdentity) { saved = null; clears++ }
    }

    private class Fixture(
        val store: RecordingStore,
        val repository: PlaybackQueueRepository,
        val runtime: PlaybackRuntime,
        val network: SessionNetwork,
        val base: OkHttpClient,
        val controller: ServiceController<PlaybackService>,
        val player: Player,
    ) {
        private var destroyed = false
        fun savedIds(): List<String>? = store.saved?.entries?.map { it.id }
        fun destroy() { if (!destroyed) { destroyed = true; controller.destroy() } }
    }
}
