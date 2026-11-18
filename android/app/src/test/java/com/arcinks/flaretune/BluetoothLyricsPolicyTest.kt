package com.arcinks.flaretune

import android.content.Context
import android.media.AudioDeviceInfo
import android.os.Build
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import com.arcinks.flaretune.data.BluetoothLyricsMode
import com.arcinks.flaretune.model.LyricLine
import com.arcinks.flaretune.model.LyricState
import com.arcinks.flaretune.model.LyricStatus
import com.arcinks.flaretune.model.Song
import com.arcinks.flaretune.playback.BluetoothLyricsController
import com.arcinks.flaretune.playback.PersistentTransportPlayer
import com.arcinks.flaretune.playback.PlaybackRuntime
import com.arcinks.flaretune.playback.applySystemLyricsMetadata
import com.arcinks.flaretune.playback.formatBluetoothLyricsArtist
import com.arcinks.flaretune.playback.hasBluetoothAudioOutput
import com.arcinks.flaretune.playback.isBluetoothAudioOutputDeviceType
import com.arcinks.flaretune.playback.item
import com.arcinks.flaretune.playback.resolveSystemLyricsMetadata
import com.arcinks.flaretune.playback.song
import com.arcinks.flaretune.playback.toSingleNextBundle
import com.arcinks.flaretune.playback.toSingleNextSong
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BluetoothLyricsPolicyTest {

    private val sampleSong = Song(
        id = "song-1",
        title = "Midnight Train",
        artist = "Blue Harbor",
        album = "Night Lines",
        durationSeconds = 215.0,
        audioUrl = "https://music.example.com/audio/song-1",
        coverUrl = "https://music.example.com/covers/song-1",
    )

    private fun readyLyricState(
        songId: String = sampleSong.id,
        text: String = "First chorus line",
        translation: String? = "第一段副歌",
        activeIndex: Int = 0,
    ): LyricState = LyricState(
        songId = songId,
        status = LyricStatus.READY,
        lines = listOf(LyricLine(text = text, translation = translation, timeSeconds = 1.0)),
        syncMode = "line",
        activeLyricIndex = activeIndex,
    )

    @Test
    fun bluetoothAudioDeviceTypes_coverSupportedOutputsAndRespectSdkLevels() {
        assertTrue(isBluetoothAudioOutputDeviceType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, Build.VERSION_CODES.M))
        assertFalse(isBluetoothAudioOutputDeviceType(AudioDeviceInfo.TYPE_HEARING_AID, Build.VERSION_CODES.O_MR1))
        assertTrue(isBluetoothAudioOutputDeviceType(AudioDeviceInfo.TYPE_HEARING_AID, Build.VERSION_CODES.P))
        assertFalse(isBluetoothAudioOutputDeviceType(AudioDeviceInfo.TYPE_BLE_HEADSET, Build.VERSION_CODES.R))
        assertTrue(isBluetoothAudioOutputDeviceType(AudioDeviceInfo.TYPE_BLE_HEADSET, Build.VERSION_CODES.S))
        assertTrue(isBluetoothAudioOutputDeviceType(AudioDeviceInfo.TYPE_BLE_SPEAKER, Build.VERSION_CODES.S))
        assertFalse(isBluetoothAudioOutputDeviceType(AudioDeviceInfo.TYPE_BLE_BROADCAST, Build.VERSION_CODES.S_V2))
        assertTrue(isBluetoothAudioOutputDeviceType(AudioDeviceInfo.TYPE_BLE_BROADCAST, Build.VERSION_CODES.TIRAMISU))

        assertFalse(isBluetoothAudioOutputDeviceType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, Build.VERSION_CODES.TIRAMISU))
        assertFalse(isBluetoothAudioOutputDeviceType(AudioDeviceInfo.TYPE_WIRED_HEADSET, Build.VERSION_CODES.TIRAMISU))
        assertFalse(isBluetoothAudioOutputDeviceType(AudioDeviceInfo.TYPE_USB_HEADSET, Build.VERSION_CODES.TIRAMISU))

        assertFalse(hasBluetoothAudioOutput(listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_WIRED_HEADSET), Build.VERSION_CODES.TIRAMISU))
        assertTrue(hasBluetoothAudioOutput(listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BLE_HEADSET), Build.VERSION_CODES.TIRAMISU))
    }

    @Test
    fun resolveSystemLyricsMetadata_handlesThreeStatesAndBluetoothRoute() {
        val lyrics = readyLyricState()

        val offWithBt = resolveSystemLyricsMetadata(sampleSong, isPlaying = true, lyrics, BluetoothLyricsMode.OFF, hasBluetoothAudioOutput = true)!!
        assertFalse(offWithBt.isLyricActive)
        assertEquals("Midnight Train", offWithBt.title)
        assertEquals("Blue Harbor", offWithBt.artist)

        val btOnlyNoBt = resolveSystemLyricsMetadata(sampleSong, isPlaying = true, lyrics, BluetoothLyricsMode.BLUETOOTH_AUDIO, hasBluetoothAudioOutput = false)!!
        assertFalse(btOnlyNoBt.isLyricActive)
        assertEquals("Midnight Train", btOnlyNoBt.title)

        val btOnlyWithBt = resolveSystemLyricsMetadata(sampleSong, isPlaying = true, lyrics, BluetoothLyricsMode.BLUETOOTH_AUDIO, hasBluetoothAudioOutput = true)!!
        assertTrue(btOnlyWithBt.isLyricActive)
        assertEquals("First chorus line", btOnlyWithBt.title)
        assertEquals("Midnight Train - Blue Harbor", btOnlyWithBt.artist)

        val alwaysNoBt = resolveSystemLyricsMetadata(sampleSong, isPlaying = true, lyrics, BluetoothLyricsMode.ALWAYS, hasBluetoothAudioOutput = false)!!
        assertTrue(alwaysNoBt.isLyricActive)
        assertEquals("First chorus line", alwaysNoBt.title)
    }

    @Test
    fun resolveSystemLyricsMetadata_formatsArtistWithAndWithoutArtist_andNeverAppendsTranslation() {
        val noArtistSong = sampleSong.copy(artist = "  ")
        assertEquals("Midnight Train - Blue Harbor", formatBluetoothLyricsArtist(sampleSong))
        assertEquals("Midnight Train", formatBluetoothLyricsArtist(noArtistSong))

        val resolved = resolveSystemLyricsMetadata(
            song = noArtistSong,
            isPlaying = true,
            lyricState = readyLyricState(text = "Only original line", translation = "不应拼接译文"),
            mode = BluetoothLyricsMode.ALWAYS,
            hasBluetoothAudioOutput = false,
        )!!
        assertTrue(resolved.isLyricActive)
        assertEquals("Only original line", resolved.title)
        assertEquals("Midnight Train", resolved.artist)
    }

    @Test
    fun resolveSystemLyricsMetadata_fallsBackOnPauseBlankInstrumentalFailedOrMismatchedSong() {
        val cases = listOf(
            // Paused
            Triple(false, readyLyricState(), sampleSong),
            // Blank line / interlude
            Triple(true, readyLyricState(text = "   "), sampleSong),
            // Negative active index (intro before first line)
            Triple(true, readyLyricState(activeIndex = -1), sampleSong),
            // Instrumental
            Triple(true, LyricState(songId = sampleSong.id, status = LyricStatus.INSTRUMENTAL), sampleSong),
            // Loading / Empty / Failed
            Triple(true, LyricState(songId = sampleSong.id, status = LyricStatus.LOADING), sampleSong),
            Triple(true, LyricState(songId = sampleSong.id, status = LyricStatus.EMPTY), sampleSong),
            Triple(true, LyricState(songId = sampleSong.id, status = LyricStatus.FAILED), sampleSong),
            // Mismatched songId
            Triple(true, readyLyricState(songId = "other-song"), sampleSong),
        )
        for ((playing, lyricState, song) in cases) {
            val resolved = resolveSystemLyricsMetadata(
                song = song,
                isPlaying = playing,
                lyricState = lyricState,
                mode = BluetoothLyricsMode.ALWAYS,
                hasBluetoothAudioOutput = true,
            )!!
            assertFalse("Expected fallback for playing=$playing, status=${lyricState.status}", resolved.isLyricActive)
            assertEquals(song.title, resolved.title)
            assertEquals(song.artist, resolved.artist)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun controller_throttlesRapidLyricChangesAndAppliesFallbacksImmediately() = runTest {
        var nowMs = 1_000L
        var invalidations = 0
        val controller = BluetoothLyricsController(
            scope = this,
            clockMs = { nowMs },
            minIntervalMs = 350L,
            onAppliedChanged = { invalidations++ },
        )

        // First active lyric emits immediately
        controller.update(sampleSong, isPlaying = true, readyLyricState(text = "Line 1"), BluetoothLyricsMode.ALWAYS, false)
        assertEquals("Line 1", controller.applied?.title)
        assertEquals(1, invalidations)

        // Rapid change within 100ms is throttled until 350ms window elapses
        nowMs += 100L
        controller.update(sampleSong, isPlaying = true, readyLyricState(text = "Line 2"), BluetoothLyricsMode.ALWAYS, false)
        assertEquals("Line 1", controller.applied?.title)
        assertEquals(1, invalidations)

        advanceTimeBy(249L)
        runCurrent()
        assertEquals("Line 1", controller.applied?.title)

        nowMs += 250L
        advanceTimeBy(1L)
        runCurrent()
        assertEquals("Line 2", controller.applied?.title)
        assertEquals(2, invalidations)

        // Rapid change scheduled, then paused -> fallback must execute immediately and cancel pending lyric
        nowMs += 50L
        controller.update(sampleSong, isPlaying = true, readyLyricState(text = "Line 3"), BluetoothLyricsMode.ALWAYS, false)
        assertEquals("Line 2", controller.applied?.title)

        controller.update(sampleSong, isPlaying = false, readyLyricState(text = "Line 3"), BluetoothLyricsMode.ALWAYS, false)
        assertEquals("Midnight Train", controller.applied?.title)
        assertEquals("Blue Harbor", controller.applied?.artist)
        assertEquals(false, controller.applied?.isLyricActive)
        assertEquals(3, invalidations)

        advanceTimeBy(500L)
        runCurrent()
        assertEquals("Midnight Train", controller.applied?.title)
        assertEquals(3, invalidations)
    }

    @Test
    fun mediaItemAndNextBundle_preserveOriginalTitleAndArtistWithoutPollution() {
        val item = sampleSong.item()
        val pollutedItem = item.buildUpon()
            .setMediaMetadata(
                item.mediaMetadata.buildUpon()
                    .setTitle("Live Lyric Line")
                    .setArtist("Midnight Train - Blue Harbor")
                    .build(),
            )
            .build()

        val parsedFromPolluted = pollutedItem.song()
        assertEquals("Midnight Train", parsedFromPolluted.title)
        assertEquals("Blue Harbor", parsedFromPolluted.artist)

        val bundle = sampleSong.toSingleNextBundle()
        bundle.putString("title", "Live Lyric Line")
        val parsedBundle = bundle.toSingleNextSong()!!
        assertEquals("Midnight Train", parsedBundle.title)
        assertEquals("Blue Harbor", parsedBundle.artist)
    }

    @Test
    fun persistentTransportPlayer_mapsSystemMetadataWithoutPollutingMediaItemSong() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = ExoPlayer.Builder(context).build()
        val player = PersistentTransportPlayer(engine)
        val session = MediaSession.Builder(context, player).setId("bt-lyrics-test").build()
        try {
            var activeApplied = applySystemLyricsMetadata(
                currentItem = sampleSong.item(),
                baseMetadata = MediaMetadata.EMPTY,
                applied = null,
                isPlaying = false,
            )
            var lyricOverride = resolveSystemLyricsMetadata(
                sampleSong,
                isPlaying = true,
                readyLyricState(text = "Sailing past midnight"),
                BluetoothLyricsMode.ALWAYS,
                hasBluetoothAudioOutput = true,
            )
            var forcePlayingForState = false
            player.setSystemMetadataTransform { currentItem, baseMetadata, isPlaying ->
                applySystemLyricsMetadata(
                    currentItem = currentItem,
                    baseMetadata = baseMetadata,
                    applied = lyricOverride,
                    isPlaying = isPlaying || forcePlayingForState,
                )
            }

            player.setMediaItem(sampleSong.item())
            player.prepare()
            shadowOf(Looper.getMainLooper()).idle()

            // Before playing, system metadata keeps original song title/artist
            assertEquals("Midnight Train", player.mediaMetadata.title?.toString())
            assertEquals("Blue Harbor", player.mediaMetadata.artist?.toString())
            assertEquals("Midnight Train", player.currentMediaItem?.song()?.title)

            // Activate lyric override in playing state
            forcePlayingForState = true
            player.invalidateSystemMetadata()
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals("Sailing past midnight", player.mediaMetadata.title?.toString())
            assertEquals("Midnight Train - Blue Harbor", player.mediaMetadata.artist?.toString())
            // App internal read via MediaItem.song() MUST remain completely unpolluted
            val internalSong = player.currentMediaItem?.song()
            assertNotNull(internalSong)
            assertEquals("Midnight Train", internalSong!!.title)
            assertEquals("Blue Harbor", internalSong.artist)

            // Fallback on pause reverts immediately
            forcePlayingForState = false
            lyricOverride = resolveSystemLyricsMetadata(
                sampleSong,
                isPlaying = false,
                readyLyricState(text = "Sailing past midnight"),
                BluetoothLyricsMode.ALWAYS,
                hasBluetoothAudioOutput = true,
            )
            player.invalidateSystemMetadata()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("Midnight Train", player.mediaMetadata.title?.toString())
            assertEquals("Blue Harbor", player.mediaMetadata.artist?.toString())
            assertEquals("Midnight Train", activeApplied.title?.toString())
        } finally {
            session.release()
            player.release()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun playbackRuntime_exposesSharedCoordinatorAndBluetoothModeState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val runtime = PlaybackRuntime(context)
        assertEquals(BluetoothLyricsMode.OFF, runtime.bluetoothLyricsMode.value)
        runtime.setBluetoothLyricsMode(BluetoothLyricsMode.BLUETOOTH_AUDIO)
        assertEquals(BluetoothLyricsMode.BLUETOOTH_AUDIO, runtime.bluetoothLyricsMode.value)

        assertEquals(LyricStatus.IDLE, runtime.lyricsCoordinator.state.value.status)
        runtime.clear()
        assertEquals(LyricStatus.IDLE, runtime.lyricsCoordinator.state.value.status)
    }
}
