package com.arcinks.flaretune

import android.app.Application
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.arcinks.flaretune.playback.enqueuePlayerSongs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class QueueEnqueueTest {
    @Test fun insertAndAppendRelocateTargetsWhileNativePlayerKeepsCurrentPositionModesAndPause() {
        val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).setLooper(Looper.getMainLooper()).build()
        try {
            player.setMediaItems(listOf(item("a"), item("b"), item("c")), 1, 12_345)
            player.shuffleModeEnabled = true
            player.repeatMode = Player.REPEAT_MODE_ALL
            enqueuePlayerSongs(player, listOf(item("a"), item("a")), true)
            assertEquals(listOf("b", "a", "c"), ids(player))
            assertEquals("b", player.currentMediaItem?.mediaId)
            assertEquals(12_345L, player.currentPosition)
            assertFalse(player.playWhenReady)
            assertTrue(player.shuffleModeEnabled)
            assertEquals(Player.REPEAT_MODE_ALL, player.repeatMode)
            enqueuePlayerSongs(player, listOf(item("a"), item("d")), false)
            assertEquals(listOf("b", "c", "a", "d"), ids(player))
            assertEquals(12_345L, player.currentPosition)
            assertFalse(player.playWhenReady)
            assertEquals(Player.STATE_IDLE, player.playbackState)
        } finally { player.release() }
    }

    @Test fun currentSongRequestIsANoopAndUnrelatedDuplicateEntriesRemainOwnedByQueue() {
        val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).setLooper(Looper.getMainLooper()).build()
        try {
            player.setMediaItems(listOf(item("a"), item("a"), item("b")), 2, 9876)
            enqueuePlayerSongs(player, listOf(item("b")), true)
            assertEquals(listOf("a", "a", "b"), ids(player))
            assertEquals(9876L, player.currentPosition)
            enqueuePlayerSongs(player, listOf(item("new")), true)
            assertEquals(listOf("a", "a", "b", "new"), ids(player))
        } finally { player.release() }
    }

    @Test fun insertIntoEmptyQueueStartsButCollectionAppendOnlyPreparesPaused() {
        for (next in listOf(true, false)) {
            val calls = mutableListOf<Pair<String, List<Any?>>>()
            val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
                calls += method.name to args.orEmpty().toList()
                when (method.returnType) {
                    java.lang.Integer.TYPE -> 0
                    java.lang.Boolean.TYPE -> false
                    java.lang.Long.TYPE -> 0L
                    java.lang.Float.TYPE -> 0f
                    else -> null
                }
            } as Player
            enqueuePlayerSongs(player, listOf(item("a")), next)
            assertEquals(listOf(next), calls.single { it.first == "setPlayWhenReady" }.second)
            assertTrue(calls.any { it.first == "prepare" })
            assertEquals(next, calls.any { it.first == "play" })
        }
    }

    private fun item(id: String) = MediaItem.Builder().setMediaId(id).setUri("https://example.invalid/$id").build()
    private fun ids(player: Player) = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
}
