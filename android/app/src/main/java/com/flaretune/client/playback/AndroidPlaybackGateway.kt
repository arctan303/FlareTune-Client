package com.flaretune.client.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.ImageLoader
import com.flaretune.client.data.ActiveConnection
import com.flaretune.client.model.Song
import com.flaretune.client.ui.PlaybackSnapshot
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AndroidPlaybackGateway(private val context: Context, private val runtime: PlaybackRuntime) : PlaybackGateway {
    override val state = MutableStateFlow(PlaybackSnapshot())
    override val imageLoader: ImageLoader? get() = runtime.imageLoader
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val visible = MutableStateFlow(false)
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { update(player) }
    }
    init {
        scope.launch {
            visible.collectLatest { shown ->
                if (shown) while (isActive) {
                    controller?.let { player ->
                        state.value = state.value.copy(positionMs = player.currentPosition.coerceAtLeast(0),
                            durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0)
                    }
                    delay(350)
                }
            }
        }
    }
    override suspend fun connect(connection: ActiveConnection) {
        if (controller != null && runtime.connection?.network === connection.network) return
        disconnect()
        runtime.install(connection)
        val next = MediaController.Builder(context, SessionToken(context,
            ComponentName(context, PlaybackService::class.java))).buildAsync()
        future = next
        val connected = withTimeout(12_000) {
            suspendCancellableCoroutine { continuation ->
                next.addListener({
                    try {
                        val value = next.get()
                        if (continuation.isActive && future === next) continuation.resume(value)
                        else MediaController.releaseFuture(next)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }, ContextCompat.getMainExecutor(context))
                continuation.invokeOnCancellation { MediaController.releaseFuture(next) }
            }
        }
        if (!connection.network.isActive) { MediaController.releaseFuture(next); return }
        controller = connected
        connected.addListener(listener)
        update(connected)
    }
    private fun update(player: Player) { state.value = player.playbackSnapshot() }
    override fun play(songs: List<Song>, index: Int) {
        if (songs.isEmpty() || index !in songs.indices) return
        controller?.run { setMediaItems(songs.map { it.item() }, index, 0); prepare(); play() }
    }
    override fun setVolume(volume: Float) { if (volume.isFinite()) controller?.volume = volume.coerceIn(0f, 1f) }
    override fun togglePlay() { controller?.run { if (isPlaying || (playWhenReady && playbackState == Player.STATE_BUFFERING)) pause() else { if (playbackState == Player.STATE_ENDED) seekToDefaultPosition(); play() } } }
    override fun next() { controller?.run { if (hasNextMediaItem()) seekToNextMediaItem() } }
    override fun previous() { controller?.let(::previousTrack) }
    override fun seekTo(positionMs: Long) { controller?.run { if (duration > 0) seekTo(positionMs.coerceIn(0, duration)) } }
    override fun toggleShuffle() { controller?.run { shuffleModeEnabled = !shuffleModeEnabled } }
    override fun cycleRepeat() { controller?.run { repeatMode = (repeatMode + 1) % 3 } }
    override fun select(index: Int) { controller?.run { if (index in 0 until mediaItemCount) { seekToDefaultPosition(index); play() } } }
    override fun removeQueueItem(index: Int) { controller?.let { removePlayerQueueItem(it, index) } }
    override fun moveQueueItem(fromIndex: Int, toIndex: Int) { controller?.let { movePlayerQueueItem(it, fromIndex, toIndex) } }
    override fun restoreQueue(songs: List<Song>, currentIndex: Int, positionMs: Long, shuffle: Boolean, repeatMode: Int) {
        controller?.let { restorePlayerQueue(it, songs.map(Song::item), currentIndex, positionMs, shuffle, repeatMode) }
    }
    override fun retry() { controller?.run { prepare(); play() } }
    override fun stopAndClear() {
        controller?.run { stop(); clearMediaItems() }
        disconnect()
        runtime.clear()
        state.value = PlaybackSnapshot()
    }
    private fun disconnect() {
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
    }
    override fun setUiVisible(visible: Boolean) { this.visible.value = visible }
    override fun release() { disconnect(); scope.cancel() }
}

internal fun Song.item(): MediaItem = MediaItem.Builder().setMediaId(id).setUri(audioUrl)
    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album)
        .setArtworkUri(coverUrl?.let(Uri::parse))
        .setExtras(Bundle().apply { putString("audio_url", audioUrl); putDouble("duration_seconds", durationSeconds); putString("language", language) })
        .build()).build()

internal fun MediaItem.song(): Song = Song(mediaId, mediaMetadata.title?.toString() ?: mediaId,
    mediaMetadata.artist?.toString() ?: "", mediaMetadata.albumTitle?.toString() ?: "",
    mediaMetadata.extras?.getDouble("duration_seconds") ?: 0.0,
    mediaMetadata.extras?.getString("audio_url") ?: "", mediaMetadata.artworkUri?.toString(), mediaMetadata.extras?.getString("language"))
