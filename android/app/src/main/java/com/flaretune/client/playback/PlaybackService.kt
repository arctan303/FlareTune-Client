package com.flaretune.client.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.flaretune.client.ClientApplication
import com.flaretune.client.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var network: com.flaretune.client.data.SessionNetwork? = null
    private var playerListener: Player.Listener? = null
    private var qualifiedListening: QualifiedListeningTracker? = null
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // A newly created empty Player must not erase the queue being loaded from disk.
    private var seenNonEmptyQueue = false

    @Suppress("DEPRECATION")
    override fun onCreate() {
        super.onCreate()
        val runtime = (application as ClientApplication).playbackRuntime
        val connection = runtime.connection
        if (connection == null || !connection.network.isActive || connection.auth.mustChangePassword) {
            stopSelf()
            return
        }
        network = connection.network
        val dataSource = OkHttpDataSource.Factory(connection.network.mediaClient)
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSource))
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
                setHandleAudioBecomingNoisy(true)
            }
        val historyTracker = PlayedTrackTracker { song ->
            if (runtime.connection?.network === connection.network && connection.network.isActive)
                runtime.history?.record(song)
        }
        qualifiedListening = QualifiedListeningTracker { song ->
            if (runtime.connection?.network === connection.network && connection.network.isActive) runtime.playEvents?.record(song)
        }
        playerListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                historyTracker.onMediaItemTransition()
                qualifiedListening?.onMediaItemTransition()
            }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                qualifiedListening?.onPositionDiscontinuity()
            }
            override fun onEvents(player: Player, events: Player.Events) {
                historyTracker.observe(player.currentMediaItem?.song(), player.isPlaying)
                saveQueue(player)
                observeListening(player)
            }
        }.also(player::addListener)
        val activity = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val artwork = CacheBitmapLoader(DataSourceBitmapLoader(
            DataSourceBitmapLoader.DEFAULT_EXECUTOR_SERVICE.get(), dataSource, null, 512))
        mediaSession = MediaSession.Builder(this, player).setSessionActivity(activity)
            .setBitmapLoader(artwork).build()
        persistenceScope.launch {
            var ticks = 0
            while (isActive) {
                delay(500)
                mediaSession?.player?.let { current ->
                    observeListening(current)
                    if (++ticks % 4 == 0) saveQueue(current)
                }
            }
        }
    }

    private fun observeListening(player: Player) {
        val captured = network ?: return
        val runtime = (application as ClientApplication).playbackRuntime
        if (runtime.connection?.network !== captured || !captured.isActive) return
        // Progress sampling does not rebuild the full queue every half-second.
        qualifiedListening?.observe(com.flaretune.client.ui.PlaybackSnapshot(
            current = player.currentMediaItem?.song(), playing = player.isPlaying,
            buffering = player.playWhenReady && player.playbackState == Player.STATE_BUFFERING,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0,
            failed = player.playerError != null,
        ))
    }
    private fun saveQueue(player: Player) {
        val captured = network ?: return
        val runtime = (application as ClientApplication).playbackRuntime
        if (runtime.connection?.network !== captured || !captured.isActive) return
        val snapshot = player.playbackSnapshot()
        if (snapshot.queue.isNotEmpty()) seenNonEmptyQueue = true
        if (!seenNonEmptyQueue) return
        // Check immediately before save as well; repository generation guards async disk work.
        if (runtime.connection?.network === captured && captured.isActive) runtime.queueRepository?.save(snapshot)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        val captured = network ?: return null
        val runtime = (application as ClientApplication).playbackRuntime
        return mediaSession.takeIf { captured.isActive && runtime.connection?.network === captured }
    }

    override fun onDestroy() {
        mediaSession?.player?.let { observeListening(it); saveQueue(it) }
        persistenceScope.cancel()
        mediaSession?.run {
            playerListener?.let(player::removeListener)
            player.release()
            release()
        }
        playerListener = null
        qualifiedListening = null
        mediaSession = null
        network = null
        super.onDestroy()
    }
}
