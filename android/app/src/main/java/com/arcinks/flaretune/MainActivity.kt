package com.arcinks.flaretune

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcinks.flaretune.data.AppLocale
import com.arcinks.flaretune.data.GoogleAuthPkce
import com.arcinks.flaretune.playback.AndroidPlaybackGateway
import com.arcinks.flaretune.ui.ClientApp
import com.arcinks.flaretune.ui.theme.FlareTuneTheme

class MainActivity : ComponentActivity() {
    private lateinit var model: ClientViewModel
    // Baseline of the last applied language change, so recreation does not loop on configuration change.
    private var appliedLanguageToken = -1L

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

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
                return ClientViewModel(
                    app.connections,
                    AndroidPlaybackGateway(app, app.playbackRuntime),
                    history = app.history,
                    queueRepository = app.queueRepository,
                    translationPreferences = app.translationPreferences,
                    coverImageProcessor = app.coverImageProcessor,
                    searchHistoryStore = app.searchHistoryStore,
                    discoveryCache = app.discoveryCache,
                    savedStateHandle = extras.createSavedStateHandle(),
                    systemMediaFavorites = app.systemMediaFavorites,
                    historyExtras = app.historyExtras,
                    playlistPreferences = app.playlistPreferences,
                    appUpdater = app.appUpdater,
                    appUpdateInstaller = app.appUpdateInstaller,
                    appVersionName = BuildConfig.VERSION_NAME,
                    appLanguagePreferences = app.appLanguagePreferences,
                ) as T
            }
        }
        model = ViewModelProvider(this, factory)[ClientViewModel::class.java]
        handleIncomingIntent(intent)
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.googleAuthLaunchUrl) {
                val url = state.googleAuthLaunchUrl ?: return@LaunchedEffect
                model.consumeGoogleAuthLaunch()
                launchGoogleAuthBrowser(url)
            }
            LaunchedEffect(state.languageChangeToken) {
                if (appliedLanguageToken < 0L) {
                    appliedLanguageToken = state.languageChangeToken
                } else if (appliedLanguageToken != state.languageChangeToken) {
                    appliedLanguageToken = state.languageChangeToken
                    recreate()
                }
            }
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (::model.isInitialized) {
            model.onForegroundResume()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        model.saveNavigationState()
        super.onSaveInstanceState(outState)
    }

    override fun onStart() { super.onStart(); model.setUiVisible(true) }
    override fun onStop() { model.setUiVisible(false); super.onStop() }

    private fun handleIncomingIntent(incoming: Intent?) {
        val data = incoming?.data ?: return
        if (!GoogleAuthPkce.isCallbackUri(data)) return
        incoming.data = null
        model.handleGoogleAuthCallback(
            code = data.getQueryParameter("code"),
            error = data.getQueryParameter("error"),
        )
    }

    private fun launchGoogleAuthBrowser(url: String) {
        val uri = Uri.parse(url)
        try {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(this, uri)
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (_: ActivityNotFoundException) {
                model.reportGoogleAuthLaunchFailed()
            }
        }
    }
}
