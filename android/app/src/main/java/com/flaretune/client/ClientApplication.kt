package com.flaretune.client

import android.app.Application
import com.flaretune.client.data.AndroidSessionStore
import com.flaretune.client.data.ConnectionManager
import com.flaretune.client.data.AndroidPlaybackHistoryStore
import com.flaretune.client.data.PlaybackHistoryRepository
import com.flaretune.client.data.AndroidPlaybackQueueStore
import com.flaretune.client.data.PlaybackQueueRepository
import com.flaretune.client.data.AndroidTranslationPreferences
import com.flaretune.client.data.LyricsDiagnostics
import com.flaretune.client.playback.PlaybackRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ClientApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            LyricsDiagnostics.install { android.util.Log.d("FlareTuneLyrics", it) }
        } else LyricsDiagnostics.install(null)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val connections by lazy {
        ConnectionManager(AndroidSessionStore(this)).also { manager ->
            // Register when the real application first needs a session, independently of any Activity.
            scope.launch { manager.invalidations.collect { playbackRuntime.clear() } }
        }
    }
    val coverImageProcessor by lazy { com.flaretune.client.data.AndroidCoverImageProcessor(this) }
    val translationPreferences by lazy { AndroidTranslationPreferences(this) }
    val history by lazy { PlaybackHistoryRepository(AndroidPlaybackHistoryStore(this), scope) }
    val queueRepository by lazy { PlaybackQueueRepository(AndroidPlaybackQueueStore(this), scope) }
    private val listeningLocalStorage by lazy { com.flaretune.client.data.AndroidListeningLocalStorage(this) }
    val searchHistoryStore by lazy { com.flaretune.client.data.SearchHistoryStore(listeningLocalStorage) }
    val discoveryCache by lazy { com.flaretune.client.data.DiscoveryCache(listeningLocalStorage) }
    val playEvents by lazy { com.flaretune.client.data.PlayEventRepository(com.flaretune.client.data.JsonPlayEventStore(listeningLocalStorage), scope) }
    val playbackRuntime by lazy { PlaybackRuntime(this, history, queueRepository, playEvents) }
}
