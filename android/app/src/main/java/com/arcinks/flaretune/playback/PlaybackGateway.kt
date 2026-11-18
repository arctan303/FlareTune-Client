package com.arcinks.flaretune.playback

import coil.ImageLoader
import com.arcinks.flaretune.data.ActiveConnection
import com.arcinks.flaretune.data.BluetoothLyricsMode
import com.arcinks.flaretune.data.LyricsCoordinator
import com.arcinks.flaretune.model.Song
import com.arcinks.flaretune.ui.PlaybackSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface PlaybackGateway {
    val state: StateFlow<PlaybackSnapshot>
    val positionMsFlow: Flow<Long> get() = state.map { it.positionMs }.distinctUntilChanged()
    val sleepTimerFlow: StateFlow<SleepTimerState> get() = inactiveSleepTimer
    val sharedLyricsCoordinator: LyricsCoordinator? get() = null
    fun setBluetoothLyricsMode(mode: BluetoothLyricsMode) {}
    fun setSleepTimer(minutes: Int) {}
    fun setSleepTimerEndOfTrack() {}
    fun setSleepTimerFinishCurrentTrack(enabled: Boolean) {
        if (enabled) setSleepTimerEndOfTrack() else cancelSleepTimer()
    }
    fun cancelSleepTimer() {}
    val imageLoader: ImageLoader?
    suspend fun connect(connection: ActiveConnection)
    fun play(songs: List<Song>, index: Int)
    fun play(songs: List<Song>, index: Int, shuffle: Boolean) {
        play(songs, index)
        if (state.value.shuffle != shuffle) toggleShuffle()
    }
    fun playSingleAsNext(song: Song): Boolean {
        play(listOf(song), 0)
        return true
    }
    fun enqueue(songs: List<Song>, next: Boolean = false) {}
    fun clearQueue() {}
    fun setVolume(volume: Float)
    fun togglePlay()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun cycleRepeat()
    fun setPlaybackMode(shuffle: Boolean, repeatMode: Int) {
        if (state.value.shuffle != shuffle) toggleShuffle()
        var current = state.value.repeatMode
        var guard = 0
        while (current != repeatMode && guard++ < 3) {
            cycleRepeat()
            current = (current + 1) % 3
        }
    }
    fun select(index: Int)
    fun removeQueueItem(index: Int) {}
    fun removeQueueItemIfUnchanged(index: Int, expectedQueue: List<Song>): Boolean {
        if (index !in expectedQueue.indices || state.value.queue != expectedQueue) return false
        removeQueueItem(index)
        return true
    }
    fun moveQueueItemIfUnchanged(fromIndex: Int, toIndex: Int, expectedQueue: List<Song>): Boolean {
        if (fromIndex !in expectedQueue.indices || toIndex !in expectedQueue.indices ||
            fromIndex == toIndex || state.value.queue != expectedQueue
        ) return false
        moveQueueItem(fromIndex, toIndex)
        return true
    }
    fun moveQueueItem(fromIndex: Int, toIndex: Int) {}
    fun restoreQueue(songs: List<Song>, currentIndex: Int, positionMs: Long, shuffle: Boolean, repeatMode: Int) {}
    fun retry()
    fun stopAndClear()
    fun setUiVisible(visible: Boolean) {}
    fun release()
}

private val inactiveSleepTimer: StateFlow<SleepTimerState> = MutableStateFlow(SleepTimerState())
