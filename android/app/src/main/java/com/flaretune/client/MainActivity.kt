package com.flaretune.client

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flaretune.client.playback.AndroidPlaybackGateway
import com.flaretune.client.ui.ClientApp
import com.flaretune.client.ui.theme.FlareTuneTheme

class MainActivity : ComponentActivity() {
    private lateinit var model: ClientViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashView -> splashView.remove() }
        }
        val playColdOpenSplash = savedInstanceState == null
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ClientApplication
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return ClientViewModel(app.connections, AndroidPlaybackGateway(app, app.playbackRuntime), history = app.history, queueRepository = app.queueRepository, translationPreferences = app.translationPreferences, coverImageProcessor = app.coverImageProcessor, searchHistoryStore = app.searchHistoryStore, discoveryCache = app.discoveryCache, savedStateHandle = extras.createSavedStateHandle(), systemMediaFavorites = app.systemMediaFavorites, historyExtras = app.historyExtras, playlistPreferences = app.playlistPreferences) as T
            }
        }
        model = ViewModelProvider(this, factory)[ClientViewModel::class.java]
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            FlareTuneTheme(themeMode = state.themeMode) {
                ClientApp(
                    state = state,
                    actions = model,
                    imageLoader = model.imageLoader,
                    positionMsFlow = model.playbackPositionMs,
                    sleepTimerFlow = model.sleepTimerFlow,
                    playColdOpenSplash = playColdOpenSplash,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent) }
    override fun onSaveInstanceState(outState: Bundle) {
        model.saveNavigationState()
        super.onSaveInstanceState(outState)
    }

    override fun onStart() { super.onStart(); model.setUiVisible(true) }
    override fun onStop() { model.setUiVisible(false); super.onStop() }
}
