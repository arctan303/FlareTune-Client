package com.flaretune.client.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.flaretune.client.ClientApplication
import com.flaretune.client.MainActivity
import com.flaretune.client.R
import com.flaretune.client.data.ActiveConnection
import com.flaretune.client.data.BluetoothLyricsMode
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
    private var activeConnection: ActiveConnection? = null
    private var network: com.flaretune.client.data.SessionNetwork? = null
    private var playerListener: Player.Listener? = null
    private var qualifiedListening: QualifiedListeningTracker? = null
    private var systemControls: SystemMediaControls? = null
    private var sleepTimer: PlaybackSleepTimer? = null
    private var audioManager: AudioManager? = null
    private var audioDeviceCallback: AudioDeviceCallback? = null
    private var hasBluetoothAudioOutput: Boolean = false
    private var bluetoothLyricsController: BluetoothLyricsController? = null
    private var coverPrewarmer: CoverArtworkPrewarmer? = null
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val bitmapScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // A newly created empty Player must not erase the queue being loaded from disk.
    private var seenNonEmptyQueue = false

    @Suppress("DEPRECATION")
    override fun onCreate() {
        super.onCreate()
        val app = application as ClientApplication
        val runtime = app.playbackRuntime
        val connection = runtime.connection
        if (connection == null || !connection.network.isActive || connection.auth.mustChangePassword) {
            stopSelf()
            return
        }
        activeConnection = connection
        network = connection.network
        runtime.syncBluetoothLyricsModeFromPreferences(app.translationPreferences)
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_notification)
            },
        )
        val dataSource = OkHttpDataSource.Factory(connection.network.mediaClient)
        val engine = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSource))
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
                setHandleAudioBecomingNoisy(true)
            }
        val player = PersistentTransportPlayer(engine)
        val btController = BluetoothLyricsController(persistenceScope) {
            player.invalidateSystemMetadata()
        }.also { bluetoothLyricsController = it }
        player.setSystemMetadataTransform { item, baseMetadata, isPlaying ->
            applySystemLyricsMetadata(item, baseMetadata, btController.applied, isPlaying)
        }
        audioManager = getSystemService(AudioManager::class.java)
        hasBluetoothAudioOutput = queryHasBluetoothAudioOutput(audioManager)
        val deviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                refreshBluetoothAudioRoute(player)
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                refreshBluetoothAudioRoute(player)
            }
        }
        audioDeviceCallback = deviceCallback
        audioManager?.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
        val timerWakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FlareTune:SleepTimer").apply { setReferenceCounted(false) }
        sleepTimer = runtime.sleepTimer.also { timer ->
            timer.attach(this, persistenceScope, object : PlaybackSleepTimer.Target {
                override var volume: Float
                    get() = engine.volume
                    set(value) { engine.volume = value }
                override var pauseAtEndOfTrack: Boolean
                    get() = engine.pauseAtEndOfMediaItems
                    set(value) { engine.setPauseAtEndOfMediaItems(value) }
                override fun pause() { engine.pause() }
            }, SystemClock::elapsedRealtime,
                acquireWakeLock = { timeoutMs -> timerWakeLock.acquire(timeoutMs) },
                releaseWakeLock = { if (timerWakeLock.isHeld) timerWakeLock.release() })
        }
        systemControls = SystemMediaControls(this, connection, persistenceScope, app.systemMediaFavorites) {
            runtime.connection?.network === connection.network
        }
        val historyTracker = PlayedTrackTracker { song ->
            if (runtime.connection?.network === connection.network && connection.network.isActive)
                runtime.history?.record(song)
        }
        qualifiedListening = QualifiedListeningTracker { song ->
            if (runtime.connection?.network === connection.network && connection.network.isActive) runtime.playEvents?.record(song)
        }
        val prewarmer = CoverArtworkPrewarmer(this, { runtime.imageLoader }, bitmapScope).also {
            coverPrewarmer = it
        }
        playerListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (sleepTimer?.state?.value?.mode == SleepTimerMode.END_OF_TRACK &&
                    reason in setOf(Player.MEDIA_ITEM_TRANSITION_REASON_SEEK, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)) {
                    sleepTimer?.cancel()
                }
                historyTracker.onMediaItemTransition()
                qualifiedListening?.onMediaItemTransition()
                syncBluetoothLyricsActivation(player)
                refreshBluetoothLyricsMetadata(player)
            }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                qualifiedListening?.onPositionDiscontinuity()
                syncBluetoothLyricsActivation(player)
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM)
                    sleepTimer?.onTrackEnded(this@PlaybackService)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) sleepTimer?.onTrackEnded(this@PlaybackService)
            }
            override fun onEvents(player: Player, events: Player.Events) {
                if (sleepTimer?.isFading == true && events.size() == 1 && events.contains(Player.EVENT_VOLUME_CHANGED)) return
                if (events.size() == 1 && events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)) return
                historyTracker.observe(player.currentMediaItem?.song(), player.isPlaying)
                saveQueue(player)
                observeListening(player)
                systemControls?.observePlayer(player)
                refreshBluetoothLyricsMetadata(player)
                if (events.containsAny(
                        Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                        Player.EVENT_REPEAT_MODE_CHANGED,
                    )
                ) {
                    prewarmer.prewarm(player)
                }
            }
        }.also(player::addListener)
        val activity = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val fallbackArtwork by lazy {
            DataSourceBitmapLoader(
                DataSourceBitmapLoader.DEFAULT_EXECUTOR_SERVICE.get(),
                dataSource,
                null,
                PLAYER_PREWARM_ARTWORK_PX,
            )
        }
        val artwork = CoilBitmapLoader(
            context = this,
            imageLoaderProvider = { runtime.imageLoader },
            fallbackLoaderProvider = { fallbackArtwork },
            scope = bitmapScope,
        )
        mediaSession = MediaSession.Builder(this, player).setSessionActivity(activity)
            .setBitmapLoader(artwork).setCallback(systemControls!!)
            .setSessionExtras(systemControls!!.sessionExtras).build()
        systemControls!!.attach(mediaSession!!)
        persistenceScope.launch {
            runtime.bluetoothLyricsMode.collect { mode ->
                syncBluetoothLyricsActivation(player, mode, hasBluetoothAudioOutput)
                refreshBluetoothLyricsMetadata(player)
            }
        }
        persistenceScope.launch {
            runtime.lyricsCoordinator.state.collect {
                refreshBluetoothLyricsMetadata(player)
            }
        }
        persistenceScope.launch {
            var ticks = 0
            while (isActive) {
                delay(500)
                mediaSession?.player?.let { current ->
                    observeListening(current)
                    advanceBluetoothLyricsPosition(current)
                    if (++ticks % 4 == 0) saveQueue(current)
                }
            }
        }
    }

    private fun refreshBluetoothAudioRoute(player: Player) {
        hasBluetoothAudioOutput = queryHasBluetoothAudioOutput(audioManager)
        syncBluetoothLyricsActivation(player)
        refreshBluetoothLyricsMetadata(player)
    }

    private fun syncBluetoothLyricsActivation(
        player: Player,
        mode: BluetoothLyricsMode = (application as ClientApplication).playbackRuntime.bluetoothLyricsMode.value,
        hasBt: Boolean = hasBluetoothAudioOutput,
    ) {
        val captured = network ?: return
        val conn = activeConnection ?: return
        val runtime = (application as ClientApplication).playbackRuntime
        if (runtime.connection?.network !== captured || !captured.isActive) return
        if (!isBluetoothLyricsActiveForRoute(mode, hasBt)) return
        runtime.lyricsCoordinator.activate(conn, player.currentMediaItem?.song())
        runtime.lyricsCoordinator.updatePosition(player.currentPosition.coerceAtLeast(0L))
    }

    private fun advanceBluetoothLyricsPosition(player: Player) {
        val captured = network ?: return
        val conn = activeConnection ?: return
        val runtime = (application as ClientApplication).playbackRuntime
        if (runtime.connection?.network !== captured || !captured.isActive) return
        if (!player.isPlaying) return
        if (!isBluetoothLyricsActiveForRoute(runtime.bluetoothLyricsMode.value, hasBluetoothAudioOutput)) return
        runtime.lyricsCoordinator.activate(conn, player.currentMediaItem?.song())
        runtime.lyricsCoordinator.updatePosition(player.currentPosition.coerceAtLeast(0L))
    }

    private fun refreshBluetoothLyricsMetadata(player: Player) {
        val captured = network
        val runtime = (application as ClientApplication).playbackRuntime
        if (captured == null || runtime.connection?.network !== captured || !captured.isActive) {
            bluetoothLyricsController?.update(
                song = null,
                isPlaying = false,
                lyricState = runtime.lyricsCoordinator.state.value,
                mode = BluetoothLyricsMode.OFF,
                hasBluetoothAudioOutput = false,
            )
            return
        }
        bluetoothLyricsController?.update(
            song = player.currentMediaItem?.song(),
            isPlaying = player.isPlaying,
            lyricState = runtime.lyricsCoordinator.state.value,
            mode = runtime.bluetoothLyricsMode.value,
            hasBluetoothAudioOutput = hasBluetoothAudioOutput,
        )
    }

    private fun observeListening(player: Player) {
        val captured = network ?: return
        val runtime = (application as ClientApplication).playbackRuntime
        if (runtime.connection?.network !== captured || !captured.isActive) return
        sleepTimer?.observeTrack(this, player.currentPosition.coerceAtLeast(0),
            player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0, player.isPlaying)
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
        sleepTimer?.detach(this)
        sleepTimer = null
        audioDeviceCallback?.let { callback -> audioManager?.unregisterAudioDeviceCallback(callback) }
        audioDeviceCallback = null
        audioManager = null
        bluetoothLyricsController?.clear()
        bluetoothLyricsController = null
        coverPrewarmer?.clear()
        coverPrewarmer = null
        mediaSession?.player?.let { player ->
            observeListening(player)
            saveQueue(player)
            (player as? PersistentTransportPlayer)?.setSystemMetadataTransform(null)
        }
        systemControls?.close()
        systemControls = null
        persistenceScope.cancel()
        bitmapScope.cancel()
        mediaSession?.run {
            playerListener?.let(player::removeListener)
            player.release()
            release()
        }
        playerListener = null
        qualifiedListening = null
        activeConnection = null
        mediaSession = null
        network = null
        super.onDestroy()
    }
}
