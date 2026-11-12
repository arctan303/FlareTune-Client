package com.flaretune.client.playback

import coil.ImageLoader
import com.flaretune.client.data.ActiveConnection
import com.flaretune.client.model.Song
import com.flaretune.client.ui.PlaybackSnapshot
import kotlinx.coroutines.flow.StateFlow

interface PlaybackGateway {
    val state: StateFlow<PlaybackSnapshot>
    val imageLoader: ImageLoader?
    suspend fun connect(connection: ActiveConnection)
    fun play(songs: List<Song>, index: Int)
    fun setVolume(volume: Float)
    fun togglePlay()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun cycleRepeat()
    fun select(index: Int)
    fun removeQueueItem(index: Int) {}
    fun moveQueueItem(fromIndex: Int, toIndex: Int) {}
    fun restoreQueue(songs: List<Song>, currentIndex: Int, positionMs: Long, shuffle: Boolean, repeatMode: Int) {}
    fun retry()
    fun stopAndClear()
    fun setUiVisible(visible: Boolean) {}
    fun release()
}
