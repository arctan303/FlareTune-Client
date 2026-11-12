package com.flaretune.client

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.flaretune.client.model.Song
import com.flaretune.client.playback.item
import com.flaretune.client.playback.movePlayerQueueItem
import com.flaretune.client.playback.playbackSnapshot
import com.flaretune.client.playback.previousTrack
import com.flaretune.client.playback.removePlayerQueueItem
import com.flaretune.client.playback.restorePlayerQueue
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QueuePlaybackOperationsTest {
    @Test fun previousAtTenSecondsSelectsPriorItemOnceWithoutReadingRestartThreshold() {
        val fixture = PlayerCalls(previous = true, position = 10_000)
        previousTrack(fixture.player)
        assertEquals(listOf("hasPreviousMediaItem", "seekToPreviousMediaItem"), fixture.calls.map { it.name })
        assertFalse(fixture.calls.any { it.name == "seekTo" || it.name == "getCurrentPosition" || it.name == "seekToPrevious" })
    }

    @Test fun previousWithNoPriorItemSeeksOnlyToZeroEvenAfterTenSeconds() {
        val fixture = PlayerCalls(previous = false, position = 10_000)
        previousTrack(fixture.player)
        assertEquals(listOf("hasPreviousMediaItem", "seekTo"), fixture.calls.map { it.name })
        assertEquals(listOf(0L), fixture.calls.last().arguments)
    }

    @Test fun removeAndMoveUseNativeTimelineOperationsWithoutRebuildingOrSeeking() {
        val fixture = PlayerCalls(count = 4, position = 12_345)
        removePlayerQueueItem(fixture.player, 1)
        movePlayerQueueItem(fixture.player, 3, 0)
        assertEquals(listOf("removeMediaItem", "moveMediaItem"), fixture.mutations.map { it.name })
        assertEquals(listOf(1), fixture.mutations[0].arguments)
        assertEquals(listOf(3, 0), fixture.mutations[1].arguments)
        assertFalse(fixture.calls.any { it.name.startsWith("seek") || it.name == "setMediaItems" || it.name == "play" })
    }

    @Test fun invalidAndSameIndexEditsSendNoMutationToThePlayer() {
        val fixture = PlayerCalls(count = 3)
        for (index in listOf(-1, 3, Int.MAX_VALUE)) removePlayerQueueItem(fixture.player, index)
        for ((from, to) in listOf(-1 to 0, 0 to -1, 3 to 0, 0 to 3, 1 to 1)) {
            movePlayerQueueItem(fixture.player, from, to)
        }
        assertTrue(fixture.mutations.isEmpty())
    }

    @Test fun restoreIsPausedBeforeTimelineReplacementAndKeepsIndexPositionShuffleAndRepeat() {
        val fixture = PlayerCalls(count = 1, position = 50_000)
        val items = listOf(track("one").item(), track("two").item(), track("one").item())
        restorePlayerQueue(fixture.player, items, 1, 12_345, true, Player.REPEAT_MODE_ALL)
        assertEquals(listOf("setPlayWhenReady", "setMediaItems", "setShuffleModeEnabled", "setRepeatMode", "prepare"), fixture.mutations.map { it.name })
        assertEquals(listOf(false), fixture.mutations[0].arguments)
        assertSame(items, fixture.mutations[1].arguments[0])
        assertEquals(1, fixture.mutations[1].arguments[1])
        assertEquals(12_345L, fixture.mutations[1].arguments[2])
        assertEquals(listOf(true), fixture.mutations[2].arguments)
        assertEquals(listOf(Player.REPEAT_MODE_ALL), fixture.mutations[3].arguments)
        assertFalse(fixture.calls.any { it.name == "play" || it.name == "seekToDefaultPosition" })
    }

    @Test fun invalidRestoresLeaveExistingPlayerUntouchedAndNegativePositionClampsToZero() {
        val fixture = PlayerCalls(count = 2)
        val items = listOf(track("one").item())
        restorePlayerQueue(fixture.player, emptyList(), 0, 0, false, 0)
        restorePlayerQueue(fixture.player, items, -1, 0, false, 0)
        restorePlayerQueue(fixture.player, items, 1, 0, false, 0)
        restorePlayerQueue(fixture.player, items, 0, 0, false, 3)
        assertTrue(fixture.calls.isEmpty())
        restorePlayerQueue(fixture.player, items, 0, -1, false, Player.REPEAT_MODE_ONE)
        assertEquals(0L, fixture.mutations.single { it.name == "setMediaItems" }.arguments[2])
        assertEquals(listOf(Player.REPEAT_MODE_ONE), fixture.mutations.single { it.name == "setRepeatMode" }.arguments)
    }

    @Test fun snapshotsReadRealPlayerMetadataAndPreserveDuplicateQueueEntriesAndLanguage() {
        val songs = listOf(track("one").copy(language = "instrumental"), track("two").copy(language = "UNKNOWN"), track("one").copy(language = "instrumental"))
        val fixture = PlayerCalls(items = songs.map(Song::item), position = 7_890, currentIndex = 1)
        val snapshot = fixture.player.playbackSnapshot()
        assertEquals(songs, snapshot.queue)
        assertEquals(songs[1], snapshot.current)
        assertEquals(1, snapshot.currentIndex)
        assertEquals(7_890L, snapshot.positionMs)
        assertEquals(20_000L, snapshot.durationMs)
        assertTrue(snapshot.shuffle)
        assertEquals(Player.REPEAT_MODE_ALL, snapshot.repeatMode)
        assertTrue(snapshot.playing)
        assertEquals(.6f, snapshot.volume, .0001f)
        assertTrue(fixture.mutations.isEmpty())
    }

    @Test fun emptySnapshotHasNoCurrentIndexAndUnsetDurationBecomesZero() {
        val fixture = PlayerCalls(items = emptyList(), currentIndex = 0, duration = C.TIME_UNSET)
        val snapshot = fixture.player.playbackSnapshot()
        assertEquals(-1, snapshot.currentIndex)
        assertNull(snapshot.current)
        assertEquals(0L, snapshot.durationMs)
        assertTrue(snapshot.queue.isEmpty())
    }

    private fun track(id: String) = Song(id, id, "Singer", "Album", 20.0,
        "https://music.example/media/$id.flac", "https://music.example/media/$id.jpg")

    private data class Call(val name: String, val arguments: List<Any?>)

    /** Records calls on the real Player interface, without reimplementing queue algorithms. */
    private class PlayerCalls(
        previous: Boolean = false,
        count: Int = 0,
        position: Long = 0,
        items: List<MediaItem>? = null,
        currentIndex: Int = 0,
        duration: Long = 20_000,
    ) {
        val calls = mutableListOf<Call>()
        val mutations get() = calls.filterNot { it.name.startsWith("get") || it.name.startsWith("is") || it.name.startsWith("has") }
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "RecordedPlayer"
                else -> {
                    calls += Call(method.name, args?.toList() ?: emptyList())
                    when (method.name) {
                        "hasPreviousMediaItem" -> previous
                        "getMediaItemCount" -> items?.size ?: count
                        "getCurrentPosition" -> position
                        "getMediaItemAt" -> items!![args!![0] as Int]
                        "getCurrentMediaItem" -> items?.getOrNull(currentIndex)
                        "getCurrentMediaItemIndex" -> currentIndex
                        "getDuration" -> duration
                        "getShuffleModeEnabled" -> true
                        "getRepeatMode" -> Player.REPEAT_MODE_ALL
                        "getVolume" -> .6f
                        "isPlaying", "getPlayWhenReady" -> true
                        "getPlaybackState" -> Player.STATE_READY
                        else -> when (method.returnType) {
                            java.lang.Boolean.TYPE -> false
                            java.lang.Integer.TYPE -> 0
                            java.lang.Long.TYPE -> 0L
                            java.lang.Float.TYPE -> 0f
                            java.lang.Double.TYPE -> 0.0
                            else -> null
                        }
                    }
                }
            }
        } as Player
    }
}
