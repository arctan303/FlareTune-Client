package com.flaretune.client

import android.media.session.PlaybackState
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.BaseMediaSource
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.flaretune.client.playback.PersistentTransportPlayer
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

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ClientApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
class PersistentTransportPlayerTest {
    @Test fun singleSongPublishesBothStandardSkipFlagsAndKeepsNextAsABoundaryNoOp() {
        val f = fixture()
        try {
            f.sources("single"); flush()
            assertTrue(f.exo.isCurrentMediaItemSeekable)
            assertTransport(f, present = true)
            f.exo.seekTo(17_000); flush()
            f.player.seekToNext(); flush()
            assertEquals("single", f.exo.currentMediaItem!!.mediaId)
            assertEquals(17_000L, f.exo.currentPosition)
            assertEquals(Player.REPEAT_MODE_OFF, f.exo.repeatMode)
            assertFalse(f.exo.shuffleModeEnabled)
            f.player.seekToPrevious(); flush()
            assertEquals("single", f.exo.currentMediaItem!!.mediaId)
            assertEquals(0L, f.exo.currentPosition)
            assertTransport(f, present = true)
        } finally { f.close() }
    }

    @Test fun previousBeyondMedia3RestartThresholdStillGoesToThePreviousTrackAndFirstResets() {
        val f = fixture()
        try {
            f.sources("first", "second", "third"); flush()
            f.exo.seekTo(1, f.exo.maxSeekToPreviousPosition + 12_000); flush()
            f.player.seekToPrevious(); flush()
            assertEquals(0, f.exo.currentMediaItemIndex)
            assertEquals("first", f.exo.currentMediaItem!!.mediaId)
            assertEquals(0L, f.exo.currentPosition)
            f.exo.seekTo(11_000); flush()
            f.player.seekToPrevious(); flush()
            assertEquals(0, f.exo.currentMediaItemIndex)
            assertEquals(0L, f.exo.currentPosition)
            assertTransport(f, present = true)
        } finally { f.close() }
    }

    @Test fun multiSongFirstAndLastKeepBothFlagsWithoutChangingTheNativeTimelineOrModes() {
        val f = fixture()
        try {
            f.sources("first", "second"); flush()
            assertTransport(f, present = true)
            f.player.seekToNext(); flush()
            assertEquals(1, f.exo.currentMediaItemIndex)
            assertTransport(f, present = true)
            f.exo.seekTo(18_000); flush()
            f.player.seekToNext(); flush()
            assertEquals(1, f.exo.currentMediaItemIndex)
            assertEquals("second", f.exo.currentMediaItem!!.mediaId)
            assertEquals(18_000L, f.exo.currentPosition)
            assertEquals(listOf("first", "second"), (0 until f.exo.mediaItemCount).map { f.exo.getMediaItemAt(it).mediaId })
            assertEquals(Player.REPEAT_MODE_OFF, f.exo.repeatMode)
            assertFalse(f.exo.shuffleModeEnabled)
        } finally { f.close() }
    }

    @Test fun manualNavigationUsesActualRepeatAndShuffleSuccessorsWithoutChangingModes() {
        val f = fixture()
        try {
            f.sources("one", "two", "three"); flush()
            for (shuffle in listOf(false, true)) for (repeat in listOf(Player.REPEAT_MODE_OFF,
                Player.REPEAT_MODE_ONE, Player.REPEAT_MODE_ALL)) for (start in 0..2) {
                f.exo.shuffleModeEnabled = shuffle; f.exo.repeatMode = repeat
                f.exo.seekTo(start, 12_000); flush()
                val expectedNext = f.exo.nextMediaItemIndex
                f.player.seekToNext(); flush()
                assertEquals(if (expectedNext == C.INDEX_UNSET) start else expectedNext, f.exo.currentMediaItemIndex)
                assertEquals(shuffle, f.exo.shuffleModeEnabled); assertEquals(repeat, f.exo.repeatMode)
                f.exo.seekTo(start, f.exo.maxSeekToPreviousPosition + 12_000); flush()
                val expectedPrevious = f.exo.previousMediaItemIndex
                f.player.seekToPrevious(); flush()
                assertEquals(if (expectedPrevious == C.INDEX_UNSET) start else expectedPrevious, f.exo.currentMediaItemIndex)
                assertEquals(0L, f.exo.currentPosition)
                assertEquals(shuffle, f.exo.shuffleModeEnabled); assertEquals(repeat, f.exo.repeatMode)
                assertTransport(f, present = true)
            }
        } finally { f.close() }
    }

    @Test fun clearingAndRestoringQueueUpdatesGettersListenersAndPublishedCompatFlagsTogether() {
        val f = fixture()
        val commands = mutableListOf<Player.Commands>()
        val events = mutableListOf<Player.Commands>()
        try {
            f.player.addListener(object : Player.Listener {
                override fun onAvailableCommandsChanged(availableCommands: Player.Commands) { commands += availableCommands }
                override fun onEvents(player: Player, playerEvents: Player.Events) {
                    if (playerEvents.contains(Player.EVENT_AVAILABLE_COMMANDS_CHANGED)) events += player.availableCommands
                }
            })
            assertTransport(f, present = false)
            f.sources("one"); flush()
            assertTransport(f, present = true)
            assertEquals(f.player.availableCommands, commands.last())
            assertEquals(f.player.availableCommands, events.last())
            f.exo.clearMediaItems(); flush()
            assertTransport(f, present = false)
            assertEquals(f.player.availableCommands, commands.last())
            assertEquals(f.player.availableCommands, events.last())
            f.sources("restored"); flush()
            assertTransport(f, present = true)
            assertEquals(f.player.availableCommands, commands.last())
            assertEquals(f.player.availableCommands, events.last())
        } finally { f.close() }
    }

    @Test fun pendingFiniteAudioKeepsTransportWhileItsNewTimelineIsStillUnprepared() {
        val f = fixture()
        try {
            f.exo.setMediaItems(listOf(item("pending"))); flush()
            assertTrue(f.exo.currentTimeline.getWindow(0, Timeline.Window()).isPlaceholder)
            assertFalse(f.exo.isCurrentMediaItemSeekable)
            assertTransport(f, present = true)
            f.exo.seekTo(9000); flush()
            f.player.seekToPrevious(); flush()
            assertEquals(0L, f.exo.currentPosition)
            f.player.seekToNext(); flush()
            assertEquals("pending", f.exo.currentMediaItem!!.mediaId)
            assertTransport(f, present = true)
        } finally { f.close() }
    }

    @Test fun prepareBufferingReadyAndEndedKeepPublishedTransportForFiniteAudio() {
        val f = fixture()
        val seen = mutableListOf<Int>()
        try {
            f.sources("prepared"); flush()
            f.player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    seen += playbackState
                    assertTrue(f.player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS))
                    assertTrue(f.player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
                }
            })
            f.exo.prepare()
            assertEquals(Player.STATE_BUFFERING, f.exo.playbackState)
            await { f.exo.playbackState == Player.STATE_READY }
            assertTransport(f, present = true)
            f.exo.seekTo(120_000)
            // Exo clamps an end seek to a valid final sample. Consume it instead of leaving
            // a paused player at that sample, which can never naturally become ENDED.
            f.exo.play()
            await { f.exo.playbackState == Player.STATE_ENDED }
            assertTransport(f, present = true)
            assertTrue(seen.contains(Player.STATE_BUFFERING))
            assertTrue(seen.contains(Player.STATE_READY)); assertTrue(seen.contains(Player.STATE_ENDED))
        } finally { f.close() }
    }

    @Test fun aNonSeekableSourceDoesNotGainSyntheticTransportCommands() {
        val f = fixture()
        try {
            f.exo.setMediaSource(KnownTimelineSource(item("not-seekable"), seekable = false)); flush()
            assertFalse(f.exo.isCurrentMediaItemSeekable)
            assertFalse(f.exo.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
            assertEquals(f.exo.availableCommands, f.player.availableCommands)
        } finally { f.close() }
    }

    private fun assertTransport(f: Fixture, present: Boolean) {
        assertEquals(present, f.player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS))
        assertEquals(present, f.player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
        val published = publishedPlatformState(f.session)
        assertEquals(present, published.actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L)
        assertEquals(present, published.actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L)
    }

    private fun fixture(): Fixture {
        val context = RuntimeEnvironment.getApplication() as ClientApplication
        val exo = ExoPlayer.Builder(context).build()
        val player = PersistentTransportPlayer(exo)
        val session = MediaSession.Builder(context, player).build()
        val host = Robolectric.buildService(PersistentTransportHostService::class.java).create()
        host.get().addSession(session)
        flush()
        return Fixture(exo, player, session, host)
    }

    private class Fixture(val exo: ExoPlayer, val player: PersistentTransportPlayer, val session: MediaSession,
        val host: ServiceController<PersistentTransportHostService>) {
        fun sources(vararg ids: String) { exo.setMediaSources(ids.map { KnownTimelineSource(item(it)) }) }
        fun close() { host.destroy(); session.release(); player.release() }
    }

    /** Known, seekable real Exo timelines without network, decoding or audio-device dependencies. */
    private class KnownTimelineSource(private val item: MediaItem, private val seekable: Boolean = true) : BaseMediaSource() {
        private val silence = SilenceMediaSource(120_000_000L)
        override fun getMediaItem(): MediaItem = item
        override fun getInitialTimeline(): Timeline = SinglePeriodTimeline(120_000_000L, seekable, false, false, null, item)
        override fun prepareSourceInternal(mediaTransferListener: TransferListener?) { refreshSourceInfo(initialTimeline) }
        override fun maybeThrowSourceInfoRefreshError() {}
        override fun createPeriod(id: MediaPeriodId, allocator: Allocator, startPositionUs: Long): MediaPeriod =
            silence.createPeriod(id, allocator, startPositionUs)
        override fun releasePeriod(mediaPeriod: MediaPeriod) { silence.releasePeriod(mediaPeriod) }
        override fun releaseSourceInternal() {}
    }

    private fun publishedPlatformState(session: MediaSession): PlaybackState {
        val sessionImpl = field(session, "impl")
        val legacyStub = field(sessionImpl, "sessionLegacyStub")
        val compat = field(legacyStub, "sessionCompat")
        val compatImpl = field(compat, "impl")
        val emitted = field(compatImpl, "playbackState")
        return emitted.javaClass.getMethod("getPlaybackState").invoke(emitted) as PlaybackState
    }
    private fun field(owner: Any, name: String): Any {
        var type: Class<*>? = owner.javaClass
        while (type != null) {
            val declared = type.declaredFields.firstOrNull { it.name == name }
            if (declared != null) return requireNotNull(declared.apply { isAccessible = true }.get(owner))
            type = type.superclass
        }
        error("Missing actual Media3 output field: $name")
    }

    class PersistentTransportHostService : MediaSessionService() {
        override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = null
    }
    companion object {
        private fun item(id: String) = MediaItem.Builder().setMediaId(id).setUri("https://music.example/audio/$id").build()
        private fun flush() { repeat(3) { shadowOf(Looper.getMainLooper()).idle() } }
        private fun await(condition: () -> Boolean) {
            repeat(200) {
                // PAUSED Robolectric's media clock does not follow Thread.sleep wall time.
                shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(20))
                flush()
                if (condition()) return
                Thread.sleep(5)
            }
            assertTrue("Real ExoPlayer did not reach the expected playback state", condition())
        }
    }
}
