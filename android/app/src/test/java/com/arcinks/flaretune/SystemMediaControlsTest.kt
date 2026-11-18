package com.arcinks.flaretune

import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Looper
import android.os.Process
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult
import com.arcinks.flaretune.data.*
import com.arcinks.flaretune.model.*
import com.arcinks.flaretune.playback.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.android.controller.ServiceController

@OptIn(ExperimentalCoroutinesApi::class)
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ClientApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
class SystemMediaControlsTest {
    @Test fun twoExtrasPublishToThePlatformSessionWhileStandardTransportAndReservedSlotsRemain() = runTest {
        val f = fixture()
        try {
            f.player.setMediaItems(listOf(item("one"), item("two")))
            f.controls.observePlayer(f.player); flush()
            val preferences = f.session.mediaButtonPreferences
            assertEquals(2, preferences.size)
            assertEquals(SYSTEM_MEDIA_CYCLE_ORDER, preferences[0].sessionCommand!!.customAction)
            assertEquals(CommandButton.ICON_REPEAT_OFF, preferences[0].icon)
            assertEquals(CommandButton.ICON_HEART_UNFILLED, preferences[1].icon)
            assertTrue(preferences.all { it.isEnabled && it.slots.contains(CommandButton.SLOT_OVERFLOW) })
            val accepted = f.controls.onConnectAsync(f.session, ownController()).get()
            assertTrue(accepted.availablePlayerCommands.contains(Player.COMMAND_PLAY_PAUSE))
            assertTrue(accepted.availablePlayerCommands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
            assertTrue(accepted.availablePlayerCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
            // Robolectric's ShadowMediaController stores an unrelated private state and does not
            // connect a controller to a MediaSession. Read the actual Compat state Media3 has
            // already passed to the framework, rather than injecting a fake platform getter value.
            val state = publishedPlatformState(f.session)
            assertEquals(preferences.map { it.sessionCommand!!.customAction }, state.customActions.map { it.action })
            assertTrue(state.actions and PlaybackState.ACTION_PLAY_PAUSE != 0L)
            assertTrue(state.actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L)
            assertTrue(f.controls.sessionExtras.getBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_PREV))
            assertTrue(f.controls.sessionExtras.getBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_NEXT))
            assertEquals(1, f.api.reads)
            repeat(8) { f.controls.observePlayer(f.player) }; flush()
            assertEquals(1, f.api.reads)
        } finally { close(f) }
    }

    @Test fun orderCyclesTheActualPlayerAndReflectsModesChangedByTheApp() = runTest {
        val f = fixture()
        try {
            f.player.setMediaItem(item("one")); f.controls.observePlayer(f.player); flush()
            val expected = listOf(SystemMediaOrder.REPEAT_ALL, SystemMediaOrder.REPEAT_ONE, SystemMediaOrder.SHUFFLE, SystemMediaOrder.SEQUENTIAL)
            val combinations = listOf(false to Player.REPEAT_MODE_ALL, false to Player.REPEAT_MODE_ONE,
                true to Player.REPEAT_MODE_OFF, false to Player.REPEAT_MODE_OFF)
            expected.forEachIndexed { index, order ->
                val mode = f.session.mediaButtonPreferences[0].sessionCommand!!
                assertEquals(SessionResult.RESULT_SUCCESS, f.controls.onCustomCommand(f.session, ownController(), mode, Bundle.EMPTY).get().resultCode)
                assertEquals(combinations[index].first, f.player.shuffleModeEnabled)
                assertEquals(combinations[index].second, f.player.repeatMode)
                assertEquals(order, systemMediaOrder(f.player.shuffleModeEnabled, f.player.repeatMode))
            }
            f.player.shuffleModeEnabled = true
            f.player.repeatMode = Player.REPEAT_MODE_ALL
            f.controls.observePlayer(f.player)
            assertEquals(CommandButton.ICON_SHUFFLE_ON, f.session.mediaButtonPreferences[0].icon)
            f.player.repeatMode = Player.REPEAT_MODE_ONE
            f.controls.observePlayer(f.player)
            assertEquals(CommandButton.ICON_SHUFFLE_ON, f.session.mediaButtonPreferences[0].icon)
            assertEquals(SystemMediaOrder.SHUFFLE, systemMediaOrder(true, Player.REPEAT_MODE_ONE))
        } finally { close(f) }
    }

    @Test fun favoriteActsWithoutAnActivityIgnoresForgedArgumentsAndRejectsOldTrackActions() = runTest {
        val f = fixture()
        try {
            f.player.setMediaItems(listOf(item("one"), item("two")))
            f.controls.observePlayer(f.player); flush()
            val command = f.session.mediaButtonPreferences[1].sessionCommand!!
            val result = f.controls.onCustomCommand(f.session, ownController(), command,
                Bundle().apply { putString("songId", "forged-other-song"); putString("accountId", "forged-account") })
            flush()
            assertEquals(SessionResult.RESULT_SUCCESS, result.get().resultCode)
            assertEquals(listOf("one"), f.api.writtenSongs)
            assertEquals(CommandButton.ICON_HEART_FILLED, f.session.mediaButtonPreferences[1].icon)
            f.player.seekToDefaultPosition(1); f.controls.observePlayer(f.player); flush()
            assertEquals(CommandButton.ICON_HEART_UNFILLED, f.session.mediaButtonPreferences[1].icon)
            assertEquals(SessionResult.RESULT_ERROR_BAD_VALUE,
                f.controls.onCustomCommand(f.session, ownController(), command, Bundle.EMPTY).get().resultCode)
            assertEquals(listOf("one"), f.api.writtenSongs)
        } finally { close(f) }
    }

    @Test fun untrustedControllerGetsNoWriteCommandsAndRetiredConnectionCannotChangeModesOrFavorites() = runTest {
        val f = fixture()
        try {
            f.player.setMediaItem(item("one")); f.controls.observePlayer(f.player); flush()
            val outsider = controller("com.android.systemui", Process.myUid() + 7, trusted = true, verified = false)
            val accepted = f.controls.onConnectAsync(f.session, outsider).get()
            val command = f.session.mediaButtonPreferences[1].sessionCommand!!
            assertFalse(accepted.availableSessionCommands.contains(command))
            assertFalse(accepted.availableSessionCommands.contains(f.session.mediaButtonPreferences[0].sessionCommand!!))
            assertEquals(SessionResult.RESULT_ERROR_PERMISSION_DENIED,
                f.controls.onCustomCommand(f.session, outsider, command, Bundle.EMPTY).get().resultCode)
            val spoofedNotification = MediaSession.ControllerInfo.createTestOnlyControllerInfo(
                (RuntimeEnvironment.getApplication() as ClientApplication).packageName, Process.myPid(), Process.myUid() + 7,
                1, 1, false, Bundle().apply {
                    putBoolean(androidx.media3.session.MediaController.KEY_MEDIA_NOTIFICATION_CONTROLLER_FLAG, true)
                }, false)
            assertTrue(f.session.isMediaNotificationController(spoofedNotification))
            assertFalse(f.controls.onConnectAsync(f.session, spoofedNotification).get().availableSessionCommands.contains(command))
            assertEquals(SessionResult.RESULT_ERROR_PERMISSION_DENIED,
                f.controls.onCustomCommand(f.session, spoofedNotification, command, Bundle.EMPTY).get().resultCode)
            f.current = false
            assertEquals(SessionResult.RESULT_ERROR_PERMISSION_DENIED,
                f.controls.onCustomCommand(f.session, ownController(), command, Bundle.EMPTY).get().resultCode)
            assertEquals(SessionResult.RESULT_ERROR_PERMISSION_DENIED,
                f.controls.onCustomCommand(f.session, ownController(), f.session.mediaButtonPreferences[0].sessionCommand!!, Bundle.EMPTY).get().resultCode)
            assertFalse(f.player.shuffleModeEnabled)
            assertEquals(Player.REPEAT_MODE_OFF, f.player.repeatMode)
            assertTrue(f.api.writtenSongs.isEmpty())
        } finally { close(f) }
    }

    @Test fun authorityRequiresOwnUidOrAnActuallyTrustedVerifiedSystemSurface() {
        val own = 12001
        assertTrue(mayUseSystemMediaCommands(own, own, false, false, "anything"))
        assertTrue(mayUseSystemMediaCommands(own, 1000, true, false, "android"))
        assertTrue(mayUseSystemMediaCommands(own, 20001, true, true, "com.android.systemui"))
        assertFalse(mayUseSystemMediaCommands(own, 1000, false, true, "android"))
        assertFalse(mayUseSystemMediaCommands(own, 20001, false, true, "com.android.systemui"))
        assertFalse(mayUseSystemMediaCommands(own, 20001, true, false, "com.android.systemui"))
        assertFalse(mayUseSystemMediaCommands(own, 20001, true, true, "trusted.notification.listener"))
    }

    @Test fun aPlayerSeekBeforeDeferredOnEventsAlreadyInvalidatesTheOldFavoriteAction() = runTest {
        val f = fixture()
        try {
            f.player.setMediaItems(listOf(item("one"), item("two")))
            f.controls.observePlayer(f.player); flush()
            val old = f.session.mediaButtonPreferences[1].sessionCommand!!
            f.player.seekToDefaultPosition(1)
            // Deliberately do not call controls.observePlayer: the Service onEvents callback can be deferred.
            val result = f.controls.onCustomCommand(f.session, ownController(), old, Bundle.EMPTY)
            flush()
            assertEquals(SessionResult.RESULT_ERROR_BAD_VALUE, result.get().resultCode)
            assertTrue(f.api.writtenSongs.isEmpty())
        } finally { close(f) }
    }

    private fun TestScope.fixture(): Fixture {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val app = RuntimeEnvironment.getApplication() as ClientApplication
        val api = FakeApi()
        val network = SessionNetwork(InstanceAddress.parse("https://music.example"), baseClient = OkHttpClient.Builder()
            .addInterceptor { throw AssertionError("No real HTTP or audio is allowed") }.build())
        val connection = ActiveConnection(network, api, AuthSession(Account("system-account", "listener", "Listener"), "fixture-csrf", false))
        val player = ExoPlayer.Builder(app).build()
        val f = Fixture(player, api, network)
        val controls = SystemMediaControls(app, connection, backgroundScope, SystemMediaFavoriteChanges(), dispatcher) { f.current }
        val session = MediaSession.Builder(app, player).setCallback(controls).setSessionExtras(controls.sessionExtras).build()
        f.controls = controls; f.session = session
        controls.attach(session)
        // A real MediaSessionService creates the library's notification proxy. That proxy's
        // available commands determine the platform PlaybackState custom actions.
        f.host = Robolectric.buildService(MediaControlsHostService::class.java).create()
        f.host.get().addSession(session)
        flush()
        return f
    }

    private fun TestScope.flush() {
        repeat(3) { shadowOf(Looper.getMainLooper()).idle(); runCurrent() }
    }

    private fun close(f: Fixture) {
        f.host.destroy(); f.controls.close(); f.session.release(); f.player.release(); f.network.retire(); Dispatchers.resetMain()
    }

    private class Fixture(val player: Player, val api: FakeApi, val network: SessionNetwork) {
        var current = true
        lateinit var controls: SystemMediaControls
        lateinit var session: MediaSession
        lateinit var host: ServiceController<MediaControlsHostService>
    }

    private class FakeApi : AccountApi {
        var reads = 0
        var revision = 1L
        val writtenSongs = mutableListOf<String>()
        val saved = mutableSetOf<String>()
        override fun songs(query: String, offset: Int): SongPage = error("Unexpected songs")
        override fun favorites(): List<Song> = error("No legacy favorites")
        override fun changePassword(current: String, replacement: String): Unit = error("Unexpected password write")
        override fun logout(): Unit = error("Unexpected logout")
        override fun playlists() = listOf(PlaylistSummary("system-favorite", "Favorites", "favorite", revision = revision))
        override fun playlist(id: String): PlaylistDetail {
            check(id == "system-favorite"); reads++
            return PlaylistDetail(playlists().single(), saved.map { PlaylistSong(Song(it, "Track $it", audioUrl = "")) })
        }
        override fun addPlaylistSongs(targets: List<PlaylistTarget>, songIds: List<String>): PlaylistAddResult {
            check(targets == listOf(PlaylistTarget("system-favorite", revision)))
            writtenSongs += songIds; saved += songIds; revision++
            return PlaylistAddResult("applied", listOf(PlaylistTargetResult("system-favorite", "applied", songIds)), listOf("system-favorite"))
        }
    }

    private fun ownController() = controller((RuntimeEnvironment.getApplication() as ClientApplication).packageName, Process.myUid(), true, true)
    private fun controller(name: String, uid: Int, trusted: Boolean, verified: Boolean) = MediaSession.ControllerInfo.createTestOnlyControllerInfo(
        name, Process.myPid(), uid, MediaSession.ControllerInfo.LEGACY_CONTROLLER_VERSION,
        MediaSession.ControllerInfo.LEGACY_CONTROLLER_INTERFACE_VERSION, trusted, Bundle.EMPTY, verified)
    private fun item(id: String) = MediaItem.Builder().setMediaId(id).setUri("https://music.example/audio/$id").build()

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

    class MediaControlsHostService : MediaSessionService() {
        override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = null
    }
}
