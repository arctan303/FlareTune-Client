package com.flaretune.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flaretune.client.playback.AndroidPlaybackGateway
import com.flaretune.client.ui.ClientApp
import com.flaretune.client.ui.theme.FlareTuneTheme

class MainActivity : ComponentActivity() {
    private lateinit var model: ClientViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ClientApplication
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ClientViewModel(app.connections, AndroidPlaybackGateway(app, app.playbackRuntime), history = app.history, queueRepository = app.queueRepository, translationPreferences = app.translationPreferences, coverImageProcessor = app.coverImageProcessor, searchHistoryStore = app.searchHistoryStore, discoveryCache = app.discoveryCache) as T
            }
        }
        model = ViewModelProvider(this, factory)[ClientViewModel::class.java]
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            FlareTuneTheme { ClientApp(state, model, model.imageLoader) }
        }
    }

    override fun onStart() { super.onStart(); model.setUiVisible(true) }
    override fun onStop() { model.setUiVisible(false); super.onStop() }
}
