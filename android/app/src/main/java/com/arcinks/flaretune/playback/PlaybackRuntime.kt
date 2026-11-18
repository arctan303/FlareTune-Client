package com.arcinks.flaretune.playback

import android.content.Context
import android.content.Intent
import coil.ImageLoader
import coil.disk.DiskCache
import com.arcinks.flaretune.data.ActiveConnection
import com.arcinks.flaretune.data.BluetoothLyricsMode
import com.arcinks.flaretune.data.HistoryIdentity
import com.arcinks.flaretune.data.LyricsCoordinator
import com.arcinks.flaretune.data.PlaybackHistoryRepository
import com.arcinks.flaretune.data.PlaybackQueueRepository
import com.arcinks.flaretune.data.TranslationPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaybackRuntime(
    private val context: Context,
    val history: PlaybackHistoryRepository? = null,
    val queueRepository: PlaybackQueueRepository? = null,
    val playEvents: com.arcinks.flaretune.data.PlayEventRepository? = null,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    val sleepTimer = PlaybackSleepTimer()
    val lyricsCoordinator = LyricsCoordinator(scope, ioDispatcher)
    private val mutableBluetoothLyricsMode = MutableStateFlow(BluetoothLyricsMode.OFF)
    val bluetoothLyricsMode: StateFlow<BluetoothLyricsMode> = mutableBluetoothLyricsMode.asStateFlow()
    @Volatile private var bluetoothLyricsModeSet = false
    // Coil 2.x ImageLoader.shutdown does not close DiskCache. Keep one journal owner per directory.
    private val imageDiskCaches = mutableMapOf<String, DiskCache>()

    internal fun diskCacheFor(identity: HistoryIdentity): DiskCache {
        val key = imageCacheIdentity(identity)
        return synchronized(imageDiskCaches) {
            imageDiskCaches.getOrPut(key) {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("coil_image_cache_v2").resolve(key))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
        }
    }
    @Volatile var connection: ActiveConnection? = null
        private set
    var imageLoader: ImageLoader? = null
        private set

    fun install(next: ActiveConnection) {
        if (connection?.network === next.network) return
        clear()
        connection = next
        val identity = HistoryIdentity(next.network.address.origin, next.auth.account.id)
        history?.bind(identity)
        queueRepository?.bind(identity)
        playEvents?.bind(identity, next) { connection?.network === next.network && next.network.isActive }
        imageLoader = ImageLoader.Builder(context)
            .okHttpClient(next.network.mediaClient)
            .diskCache { diskCacheFor(identity) }
            .respectCacheHeaders(false)
            .crossfade(false)
            .build()
    }

    fun detachLocalData() {
        history?.detach()
        queueRepository?.detach()
    }

    fun setBluetoothLyricsMode(mode: BluetoothLyricsMode) {
        bluetoothLyricsModeSet = true
        mutableBluetoothLyricsMode.value = mode
    }

    fun syncBluetoothLyricsModeFromPreferences(preferences: TranslationPreferences) {
        if (bluetoothLyricsModeSet) return
        val persisted = runCatching { preferences.readBluetoothLyricsMode() }.getOrDefault(BluetoothLyricsMode.OFF)
        if (!bluetoothLyricsModeSet) {
            mutableBluetoothLyricsMode.value = persisted
        }
    }

    fun clear() {
        sleepTimer.cancel()
        lyricsCoordinator.clear()
        // Remove authority before either repository changes or the old Service tears down.
        connection = null
        playEvents?.clear()
        detachLocalData()
        imageLoader?.memoryCache?.clear()
        imageLoader?.shutdown()
        imageLoader = null
        context.stopService(Intent(context, PlaybackService::class.java))
    }
}


/** The same private artwork URL may return different bytes for two accounts. */
internal fun imageCacheIdentity(identity: HistoryIdentity): String {
    val normalized = identity.normalized()
    return java.security.MessageDigest.getInstance("SHA-256")
        .digest((normalized.origin + "\u0000" + normalized.accountId).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
