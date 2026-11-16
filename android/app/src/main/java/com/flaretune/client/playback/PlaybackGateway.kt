package com.flaretune.client.playback

import coil.ImageLoader
import com.flaretune.client.data.ActiveConnection
import com.flaretune.client.data.BluetoothLyricsMode
import com.flaretune.client.data.LyricsCoordinator
import com.flaretune.client.model.Song
import com.flaretune.client.ui.PlaybackSnapshot
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
    fun moveQueueItem(fromIndex: Int, toIndex: Int) {}
    fun restoreQueue(songs: List<Song>, currentIndex: Int, positionMs: Long, shuffle: Boolean, repeatMode: Int) {}
    fun retry()
    fun stopAndClear()
    fun setUiVisible(visible: Boolean) {}
    fun release()
}

private val inactiveSleepTimer: StateFlow<SleepTimerState> = MutableStateFlow(SleepTimerState())
