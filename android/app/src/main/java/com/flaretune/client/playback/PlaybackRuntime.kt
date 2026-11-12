package com.flaretune.client.playback

import android.content.Context
import android.content.Intent
import coil.ImageLoader
import com.flaretune.client.data.ActiveConnection
import com.flaretune.client.data.HistoryIdentity
import com.flaretune.client.data.PlaybackHistoryRepository
import com.flaretune.client.data.PlaybackQueueRepository

class PlaybackRuntime(
    private val context: Context,
    val history: PlaybackHistoryRepository? = null,
    val queueRepository: PlaybackQueueRepository? = null,
) {
    var connection: ActiveConnection? = null
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
        imageLoader = ImageLoader.Builder(context).okHttpClient(next.network.mediaClient)
            .diskCache(null).crossfade(true).build()
    }

    fun clear() {
        // Remove authority before either repository changes or the old Service tears down.
        connection = null
        history?.clear()
        queueRepository?.clear()
        imageLoader?.memoryCache?.clear()
        imageLoader?.shutdown()
        imageLoader = null
        context.stopService(Intent(context, PlaybackService::class.java))
    }
}
