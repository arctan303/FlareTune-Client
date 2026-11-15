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
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import coil.ImageLoader
import com.flaretune.client.data.ActiveConnection
import com.flaretune.client.data.BluetoothLyricsMode
import com.flaretune.client.data.LyricsCoordinator
import com.flaretune.client.model.Song
import com.flaretune.client.model.SongRelations
import com.flaretune.client.model.relations
import com.flaretune.client.data.readSongRelations
import com.flaretune.client.data.putSongRelations
import org.json.JSONObject
import com.flaretune.client.ui.PlaybackSnapshot
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AndroidPlaybackGateway(private val context: Context, private val runtime: PlaybackRuntime) : PlaybackGateway {
    override val state = MutableStateFlow(PlaybackSnapshot())
    private val livePositionMs = MutableStateFlow(0L)
    override val positionMsFlow: StateFlow<Long> = livePositionMs
    override val sleepTimerFlow: StateFlow<SleepTimerState> get() = runtime.sleepTimer.state
    override val sharedLyricsCoordinator: LyricsCoordinator get() = runtime.lyricsCoordinator
    override fun setBluetoothLyricsMode(mode: BluetoothLyricsMode) {
        runtime.setBluetoothLyricsMode(mode)
    }
    override fun setSleepTimer(minutes: Int) {
        if (controller != null && runtime.connection?.network?.isActive == true) runtime.sleepTimer.setSleepTimer(minutes)
    }
    override fun setSleepTimerEndOfTrack() {
        if (controller?.currentMediaItem != null) runtime.sleepTimer.setSleepTimerEndOfTrack()
    }
    override fun setSleepTimerFinishCurrentTrack(enabled: Boolean) {
        val timer = runtime.sleepTimer
        val timedActive = timer.state.value.mode == SleepTimerMode.TIMED
        if (timedActive || !enabled || controller?.currentMediaItem != null) {
            timer.setSleepTimerFinishCurrentTrack(enabled)
        }
    }
    override fun cancelSleepTimer() { runtime.sleepTimer.cancel() }
    override val imageLoader: ImageLoader? get() = runtime.imageLoader
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val visible = MutableStateFlow(false)
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            // A temporary service fade is not a user gain preference and must not flood ClientUiState.
            if (runtime.sleepTimer.isFading && events.size() == 1 && events.contains(Player.EVENT_VOLUME_CHANGED)) return
            update(player)
        }
    }
    init {
        scope.launch {
            visible.collectLatest { shown ->
                if (!shown) return@collectLatest
                syncProgressFromController()
                while (isActive) {
                    delay(200)
                    syncProgressFromController()
                }
            }
        }
    }
    private fun syncProgressFromController() {
        val player = controller ?: return
        val pos = player.currentPosition.coerceAtLeast(0L)
        if (livePositionMs.value != pos) livePositionMs.value = pos
        val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        if (dur > 0L && dur != state.value.durationMs) {
            state.value = state.value.copy(positionMs = pos, durationMs = dur)
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
    private fun update(player: Player) {
        val actual = player.playbackSnapshot()
        val snapshot = runtime.sleepTimer.displayedVolume?.let { actual.copy(volume = it) } ?: actual
        state.value = snapshot
        livePositionMs.value = snapshot.positionMs
    }
    override fun play(songs: List<Song>, index: Int) {
        if (songs.isEmpty() || index !in songs.indices) return
        controller?.run { setMediaItems(songs.map { it.item() }, index, 0); prepare(); play() }
    }
    override fun play(songs: List<Song>, index: Int, shuffle: Boolean) {
        if (songs.isEmpty() || index !in songs.indices) return
        controller?.run { shuffleModeEnabled = shuffle; setMediaItems(songs.map { it.item() }, index, 0); prepare(); play() }
    }
    override fun playSingleAsNext(song: Song): Boolean {
        if (!song.canPlay) return false
        val currentController = controller ?: return false
        val command = SessionCommand(PLAYBACK_PLAY_SINGLE_NEXT, Bundle.EMPTY)
        if (currentController.availableSessionCommands.contains(command)) {
            currentController.sendCustomCommand(command, song.toSingleNextBundle())
            return true
        }
        return playSingleAsNext(currentController, song.item())
    }
    override fun enqueue(songs: List<Song>, next: Boolean) {
        controller?.let { enqueuePlayerSongs(it, songs.filter { song -> song.canPlay }.map { song -> song.item() }, next) }
    }
    override fun clearQueue() {
        runtime.sleepTimer.cancel()
        controller?.run { stop(); clearMediaItems() }
        runtime.queueRepository?.save(PlaybackSnapshot())
        livePositionMs.value = 0L
        state.value = state.value.copy(current = null, queue = emptyList(), currentIndex = -1,
            previousQueueIndex = null, nextQueueIndex = null,
            playing = false, buffering = false, positionMs = 0, durationMs = 0, failed = false)
    }
    override fun setVolume(volume: Float) { if (volume.isFinite()) controller?.volume = volume.coerceIn(0f, 1f) }
    override fun togglePlay() { controller?.run { if (isPlaying || (playWhenReady && playbackState == Player.STATE_BUFFERING)) pause() else { if (playbackState == Player.STATE_ENDED) seekToDefaultPosition(); play() } } }
    override fun next() { controller?.run { if (hasNextMediaItem()) seekToNextMediaItem() } }
    override fun previous() { controller?.let(::previousTrack) }
    override fun seekTo(positionMs: Long) {
        controller?.run {
            if (duration > 0) {
                val clamped = positionMs.coerceIn(0, duration)
                seekTo(clamped)
                livePositionMs.value = clamped
            }
        }
    }
    override fun toggleShuffle() { controller?.run { shuffleModeEnabled = !shuffleModeEnabled } }
    override fun cycleRepeat() { controller?.run { repeatMode = (repeatMode + 1) % 3 } }
    override fun setPlaybackMode(shuffle: Boolean, repeatMode: Int) {
        controller?.run {
            shuffleModeEnabled = shuffle
            this.repeatMode = repeatMode.coerceIn(0, 2)
        }
    }
    override fun select(index: Int) { controller?.run { if (index in 0 until mediaItemCount) { seekToDefaultPosition(index); play() } } }
    override fun removeQueueItem(index: Int) { controller?.let { removePlayerQueueItem(it, index) } }
    override fun moveQueueItem(fromIndex: Int, toIndex: Int) { controller?.let { movePlayerQueueItem(it, fromIndex, toIndex) } }
    override fun restoreQueue(songs: List<Song>, currentIndex: Int, positionMs: Long, shuffle: Boolean, repeatMode: Int) {
        controller?.let {
            restorePlayerQueue(it, songs.map(Song::item), currentIndex, positionMs, shuffle, repeatMode)
            update(it)
        }
    }
    override fun retry() { controller?.run { prepare(); play() } }
    override fun stopAndClear() {
        runtime.sleepTimer.cancel()
        runtime.detachLocalData()
        controller?.run { stop(); clearMediaItems() }
        disconnect()
        runtime.clear()
        livePositionMs.value = 0L
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

internal const val EXTRA_ORIGINAL_TITLE = "original_title"
internal const val EXTRA_ORIGINAL_ARTIST = "original_artist"

internal fun Song.item(): MediaItem = MediaItem.Builder().setMediaId(id).setUri(audioUrl)
    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album)
        .setArtworkUri(coverUrl?.let(Uri::parse))
        .setExtras(Bundle().apply {
            putString(EXTRA_ORIGINAL_TITLE, title)
            putString(EXTRA_ORIGINAL_ARTIST, artist)
            putString("audio_url", audioUrl); putDouble("duration_seconds", durationSeconds); putString("language", language)
            putString("song_relations_v1", JSONObject().putSongRelations(id, relations).toString())
        })
        .build()).build()

internal fun MediaItem.song(): Song {
    val links = try {
        mediaMetadata.extras?.getString("song_relations_v1")?.takeIf { it.length <= 64 * 1024 }
            ?.let { JSONObject(it).readSongRelations(mediaId) } ?: SongRelations()
    } catch (_: Exception) { SongRelations() }
    val extras = mediaMetadata.extras
    val rawTitle = if (extras?.containsKey(EXTRA_ORIGINAL_TITLE) == true) {
        extras.getString(EXTRA_ORIGINAL_TITLE)?.takeIf { it.isNotEmpty() } ?: mediaMetadata.title?.toString() ?: mediaId
    } else {
        mediaMetadata.title?.toString() ?: mediaId
    }
    val rawArtist = if (extras?.containsKey(EXTRA_ORIGINAL_ARTIST) == true) {
        extras.getString(EXTRA_ORIGINAL_ARTIST) ?: ""
    } else {
        mediaMetadata.artist?.toString() ?: ""
    }
    return Song(mediaId, rawTitle,
        rawArtist, mediaMetadata.albumTitle?.toString() ?: "",
        extras?.getDouble("duration_seconds") ?: 0.0,
        extras?.getString("audio_url") ?: "", mediaMetadata.artworkUri?.toString(),
        extras?.getString("language"), links.source, links.artists, links.artistId, links.albumId)
}
