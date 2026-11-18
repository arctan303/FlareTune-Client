package com.arcinks.flaretune

import android.app.Application
import com.arcinks.flaretune.data.AndroidCoverImageProcessor
import com.arcinks.flaretune.data.AndroidListeningLocalStorage
import com.arcinks.flaretune.data.AndroidPlaybackHistoryStore
import com.arcinks.flaretune.data.AndroidPlaybackQueueStore
import com.arcinks.flaretune.data.AndroidSessionStore
import com.arcinks.flaretune.data.AndroidTranslationPreferences
import com.arcinks.flaretune.data.AndroidAppUpdateInstaller
import com.arcinks.flaretune.data.ConnectionManager
import com.arcinks.flaretune.data.GitHubAppUpdateRepository
import com.arcinks.flaretune.data.LyricsDiagnostics
import com.arcinks.flaretune.data.PlaybackHistoryRepository
import com.arcinks.flaretune.data.PlaybackQueueRepository
import com.arcinks.flaretune.playback.PlaybackRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ClientApplication : Application() {
    // Applied before anything resolves resources so a saved interface language wins over the device locale.
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(com.arcinks.flaretune.data.AppLocale.wrap(base))
    }

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
    val systemMediaFavorites by lazy { com.arcinks.flaretune.data.SystemMediaFavoriteChanges() }
    val coverImageProcessor by lazy { com.arcinks.flaretune.data.AndroidCoverImageProcessor(this) }
    val translationPreferences by lazy { AndroidTranslationPreferences(this) }
    val history by lazy { PlaybackHistoryRepository(AndroidPlaybackHistoryStore(this), scope) }
    val queueRepository by lazy { PlaybackQueueRepository(AndroidPlaybackQueueStore(this), scope) }
    private val listeningLocalStorage by lazy { com.arcinks.flaretune.data.AndroidListeningLocalStorage(this) }
    val searchHistoryStore by lazy { com.arcinks.flaretune.data.SearchHistoryStore(listeningLocalStorage) }
    val discoveryCache by lazy { com.arcinks.flaretune.data.DiscoveryCache(listeningLocalStorage) }
    val playEvents by lazy { com.arcinks.flaretune.data.PlayEventRepository(com.arcinks.flaretune.data.JsonPlayEventStore(listeningLocalStorage), scope) }
    val playlistPreferences by lazy { com.arcinks.flaretune.data.JsonPlaylistPreferencesStore(listeningLocalStorage) }
    val historyExtras by lazy { com.arcinks.flaretune.data.ListeningHistoryExtrasRepository(com.arcinks.flaretune.data.JsonListeningHistoryExtrasStore(listeningLocalStorage), scope) }
    val appUpdater by lazy {
        GitHubAppUpdateRepository(
            cacheDir = cacheDir,
            expectedPackageName = packageName,
            currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
            archiveValidator = GitHubAppUpdateRepository.androidArchiveValidator(
                context = this,
                expectedPackageName = packageName,
                minVersionCode = BuildConfig.VERSION_CODE.toLong(),
            ),
        )
    }
    val appUpdateInstaller by lazy { AndroidAppUpdateInstaller(this) }
    val appLanguagePreferences by lazy { com.arcinks.flaretune.data.AndroidAppLanguagePreferences(this) }
    val playbackRuntime by lazy { PlaybackRuntime(this, history, queueRepository, playEvents) }
}
