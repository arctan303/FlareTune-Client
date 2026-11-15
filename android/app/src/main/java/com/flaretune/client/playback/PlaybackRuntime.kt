package com.flaretune.client.playback

import android.content.Context
import android.content.Intent
import coil.ImageLoader
import coil.disk.DiskCache
import com.flaretune.client.data.ActiveConnection
import com.flaretune.client.data.HistoryIdentity
import com.flaretune.client.data.PlaybackHistoryRepository
import com.flaretune.client.data.PlaybackQueueRepository

class PlaybackRuntime(
    private val context: Context,
    val history: PlaybackHistoryRepository? = null,
    val queueRepository: PlaybackQueueRepository? = null,
    val playEvents: com.flaretune.client.data.PlayEventRepository? = null,
) {
    val sleepTimer = PlaybackSleepTimer()
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

    fun clear() {
        sleepTimer.cancel()
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
