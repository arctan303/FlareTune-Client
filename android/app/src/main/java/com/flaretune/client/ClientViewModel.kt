package com.flaretune.client

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.flaretune.client.data.*
import com.flaretune.client.model.*
import com.flaretune.client.playback.PlaybackGateway
import com.flaretune.client.playback.SleepTimerState
import com.flaretune.client.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class ClientViewModel(
    private val connections: Connections,
    private val player: PlaybackGateway,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val history: PlaybackHistoryRepository? = null,
    private val queueRepository: PlaybackQueueRepository? = null,
    private val translationPreferences: TranslationPreferences? = null,
    private val coverImageProcessor: CoverImageProcessor? = null,
    private val searchHistoryStore: SearchHistoryStore? = null,
    private val discoveryCache: DiscoveryCache? = null,
    private val savedStateHandle: SavedStateHandle? = null,
    private val systemMediaFavorites: SystemMediaFavoriteChanges? = null,
    private val historyExtras: ListeningHistoryExtrasRepository? = null,
    private val playlistPreferences: PlaylistPreferencesStore? = null,
    private val appUpdater: AppUpdateRepository? = null,
    private val appUpdateInstaller: AppUpdateInstaller? = null,
    private val appVersionName: String = BuildConfig.VERSION_NAME,
    private val appLanguagePreferences: AppLanguagePreferences? = null,
) : ViewModel(), ClientActions {
    private val navigation = ClientNavigationState(savedStateHandle)
    private var preferredVolume = runCatching { translationPreferences?.readVolume() ?: 1f }
        .getOrDefault(1f).takeIf { it.isFinite() && it in 0f..1f } ?: 1f
    private var volumeSaveRevision = 0L
    private var volumeSaveJob: Job? = null
    private val volumeMutex = Mutex()
    private var translationEnabled = runCatching { translationPreferences?.read() ?: false }.getOrDefault(false)
    private var keepScreenOnEnabled = runCatching { translationPreferences?.readKeepScreenOn() ?: true }.getOrDefault(true)
    private var themeMode = runCatching { translationPreferences?.readThemeMode() ?: AppThemeMode.SYSTEM }.getOrDefault(AppThemeMode.SYSTEM)
    private var appLanguage = runCatching { appLanguagePreferences?.read() ?: AppLanguage.SYSTEM }.getOrDefault(AppLanguage.SYSTEM)
    private var appLanguageRevision = 0L
    private var languageChangeToken = 0L
    private var bluetoothLyricsMode = runCatching { translationPreferences?.readBluetoothLyricsMode() ?: BluetoothLyricsMode.OFF }.getOrDefault(BluetoothLyricsMode.OFF)
    private var includePrereleaseUpdates = runCatching { translationPreferences?.readIncludePrereleases() ?: false }.getOrDefault(false)
    private val preferenceMutex = Mutex()
    private var keepScreenRevision = 0L
    private var themeModeRevision = 0L
    private var bluetoothLyricsRevision = 0L
    private var includePrereleaseRevision = 0L
    private var appUpdateRevision = 0L
    private var appUpdateJob: Job? = null
    private var appUpdateState = AppUpdateState(
        currentVersion = appVersionName,
        includePrereleases = includePrereleaseUpdates,
    )
    private var addingAccount = false
    private val mutable = MutableStateFlow(freshUi(instanceUrl = connections.lastOrigin, translationEnabled = translationEnabled, playback = PlaybackSnapshot(volume = preferredVolume)))
    val state: StateFlow<ClientUiState> = mutable
    private val mutablePlaybackPositionMs = MutableStateFlow(player.state.value.positionMs.coerceAtLeast(0L))
    val playbackPositionMs: StateFlow<Long> = mutablePlaybackPositionMs
    val sleepTimerFlow: StateFlow<SleepTimerState> get() = player.sleepTimerFlow
    val imageLoader: ImageLoader? get() = player.imageLoader
    private val ownsLyricsCoordinator = player.sharedLyricsCoordinator == null
    private val lyrics: LyricsCoordinator = player.sharedLyricsCoordinator ?: LyricsCoordinator(viewModelScope, ioDispatcher)
    private var identity = 0L
    private var splashNonce = 0
    private val searchMutex = Mutex()
    private var discoveryRevision = 0L
    private var discoveryJob: Job? = null
    private var discoveryCheckedDate: String? = null
    private val seenSpotlightArtists = mutableListOf<String>()
    private var mutationJob: Job? = null
    private var albumSearchRevision = 0L
    private var albumSearchOffset = 0
    private var albumSearchJob: Job? = null
    private var pendingCreate: Pair<Triple<String, String, List<String>>, String>? = null
    private var pickerRevision = 0L
    private var pickerOffset = 0
    private var pickerJob: Job? = null
    private var artistsRevision = 0L
    private var artistsOffset = 0
    private var artistsJob: Job? = null
    private var artistRevision = 0L
    private var artistOffset = 0
    private var artistJob: Job? = null
    private var artistAlbumsOffset = 0
    private var artistAlbumsRevision = 0L
    private var artistAlbumsJob: Job? = null
    private var statsRevision = 0L
    private var statsJob: Job? = null
    private var requestRevision = 0L
    private var requestJob: Job? = null
    private var offset = 0
    private var homeLibraryRevision = 0L
    private var homeLibraryJob: Job? = null
    private var albumOffset = 0
    private var albumRevision = 0L
    private var detailRevision = 0L
    private var albumJob: Job? = null
    private var detailJob: Job? = null
    private var playlistsRevision = 0L
    private var playlistsJob: Job? = null
    private var playlistRevision = 0L
    private var playlistJob: Job? = null
    private var favoritesRevision = 0L
    private var favoritesJob: Job? = null
    // Per-connection monotonic facts: UI list metadata may arrive out of order.
    private var favoriteOwner: ActiveConnection? = null
    private var knownFavoriteSummary: PlaylistSummary? = null
    private var knownFavoriteDetail: PlaylistDetail? = null
    private var playRevision = 0L
    private var playJob: Job? = null
    private var historyJob: Job? = null
    private var queueRestoreJob: Job? = null
    private var queueRestoreRevision = 0L
    private var queueNoticeJob: Job? = null
    private var queueRestoreNotice: ClientNotice? = null
    private var historyEntries: List<HistoryEntry> = emptyList()
    private val historySongs = mutableMapOf<String, Song>()
    private val historyResolved = mutableSetOf<String>()
    private var historyExtrasSnapshot = ListeningHistoryExtrasSnapshot()
    private val artistCoverCache = mutableMapOf<String, String>()
    private val collectionCoverCache = mutableMapOf<String, String>()
    private val collectionPreviewCoversCache = mutableMapOf<String, List<String>>()
    private val snapshotSongCache = mutableMapOf<String, Song>()
    private var activeQueueOriginType: String = "queue"
    private var activeQueueOriginLabel: String = ""
    private var snapshotNonce: Long = 0L
    private var homeSnapshotEpoch = 0L
    private var homeContinueLocked = false
    private var homeHistoryPreviewLocked = false
    private var recentStatsRevision = 0L
    private var recentStatsJob: Job? = null
    private var recentPlayStats: PlayStats? = null
    private var recentDayPlayStats: PlayStats? = null
    private var continueSearchSongs: List<Song> = emptyList()
    private var continueSearchReady = searchHistoryStore == null
    private var continueSearchRevision = 0L
    private var continueSearchJob: Job? = null
    private var recentStatsReady = false
    private var allStatsReady = false
    private var favoritesReadyForContinue = false
    private var searchHistorySnapshotLocked = false
    private var playlistsLoadedOnce = false
    private var favoritesLoadedOnce = false
    private var profileRevision = 0L
    private var profileJob: Job? = null
    private var instanceProbeRevision = 0L
    private var instanceProbeJob: Job? = null
    private var pendingGoogleOrigin: String? = savedStateHandle?.get<String>(KEY_GOOGLE_ORIGIN)
    private var pendingGoogleVerifier: String? = savedStateHandle?.get<String>(KEY_GOOGLE_VERIFIER)
    private var pendingGoogleStartedAtMs: Long? = savedStateHandle?.get<Long>(KEY_GOOGLE_STARTED_AT)
    private var exchangingGoogleCode = false

    private enum class SearchSnapshotMode { LOAD_AND_LOCK, RECORD_ONLY, APPLY_TO_VISIBLE }

    private fun freshUi(
        screen: ClientScreen = ClientScreen.RESTORING,
        instanceUrl: String = connections.lastOrigin,
        username: String = "",
        accountName: String = "",
        avatarUrl: String? = null,
        avatarRevision: Long = 0L,
        busy: Boolean = false,
        notice: ClientNotice? = null,
        translationEnabled: Boolean = this.translationEnabled,
        playback: PlaybackSnapshot = PlaybackSnapshot(volume = preferredVolume),
        navigationIdentity: String? = null,
        playbackReady: Boolean = false,
        connectStep: ConnectStep = if (username.isNotBlank() && instanceUrl.isNotBlank()) ConnectStep.CREDENTIALS else ConnectStep.INSTANCE,
        googleLoginEnabled: Boolean = false,
        googleNativeLoginEnabled: Boolean = false,
    ): ClientUiState {
        val accounts = runCatching { connections.savedAccounts }
        return ClientUiState(
            screen = screen, instanceUrl = instanceUrl, username = username, accountName = accountName,
            avatarUrl = avatarUrl, avatarRevision = avatarRevision,
            busy = busy, notice = notice ?: if (accounts.isFailure) ClientNotice.STORAGE_FAILED else null,
            translationEnabled = translationEnabled, playback = playback,
            navigationIdentity = navigationIdentity, playbackReady = playbackReady,
            savedAccounts = accounts.getOrDefault(emptyList()), addingAccount = addingAccount,
            splashNonce = splashNonce,
            keepScreenOnEnabled = keepScreenOnEnabled,
            themeMode = themeMode,
            appLanguage = appLanguage,
            languageChangeToken = languageChangeToken,
            bluetoothLyricsMode = bluetoothLyricsMode,
            appUpdate = appUpdateState,
            connectStep = connectStep,
            googleLoginEnabled = googleLoginEnabled,
            googleNativeLoginEnabled = googleNativeLoginEnabled,
        )
    }

    private fun savePendingGoogleAuth(origin: String, verifier: String) {
        val now = System.currentTimeMillis()
        pendingGoogleOrigin = origin
        pendingGoogleVerifier = verifier
        pendingGoogleStartedAtMs = now
        savedStateHandle?.set(KEY_GOOGLE_ORIGIN, origin)
        savedStateHandle?.set(KEY_GOOGLE_VERIFIER, verifier)
        savedStateHandle?.set(KEY_GOOGLE_STARTED_AT, now)
    }

    private fun clearPendingGoogleAuth() {
        pendingGoogleOrigin = null
        pendingGoogleVerifier = null
        pendingGoogleStartedAtMs = null
        savedStateHandle?.remove<String>(KEY_GOOGLE_ORIGIN)
        savedStateHandle?.remove<String>(KEY_GOOGLE_VERIFIER)
        savedStateHandle?.remove<Long>(KEY_GOOGLE_STARTED_AT)
    }

    private fun accountSummaries(): List<SavedAccountSummary> =
        runCatching { connections.savedAccounts }.getOrDefault(emptyList())

    private fun refreshSavedAccounts() {
        val accounts = runCatching { connections.savedAccounts }
        mutable.value = mutable.value.copy(savedAccounts = accounts.getOrDefault(emptyList()),
            addingAccount = addingAccount, notice = if (accounts.isFailure) ClientNotice.STORAGE_FAILED else mutable.value.notice)
    }

    private fun current(): ActiveConnection? = connections.current?.takeIf {
        mutable.value.screen == ClientScreen.LIBRARY && it.network.isActive && !it.auth.mustChangePassword
    }
    private fun valid(ticket: Long, connection: ActiveConnection) =
        ticket == identity && connections.current === connection && connection.network.isActive &&
            mutable.value.screen == ClientScreen.LIBRARY

    private fun cancelRequests() {
        requestRevision++; albumRevision++; detailRevision++
        playlistsRevision++; playlistRevision++; favoritesRevision++; playRevision++
        requestJob?.cancel(); albumJob?.cancel(); detailJob?.cancel()
        playlistsJob?.cancel(); playlistJob?.cancel(); favoritesJob?.cancel(); playJob?.cancel()
        historyJob?.cancel()
        queueRestoreRevision++; queueRestoreJob?.cancel()
        queueNoticeJob?.cancel()
        mutationJob?.cancel(); pendingCreate = null
        pickerRevision++; pickerJob?.cancel()
        artistsRevision++; artistsJob?.cancel(); artistRevision++; artistJob?.cancel()
        artistAlbumsRevision++; artistAlbumsJob?.cancel()
        statsRevision++; statsJob?.cancel(); recentStatsRevision++; recentStatsJob?.cancel()
        continueSearchRevision++; continueSearchJob?.cancel()
        albumSearchRevision++; albumSearchJob?.cancel()
        discoveryRevision++; discoveryJob?.cancel(); discoveryCheckedDate = null; seenSpotlightArtists.clear()
        homeLibraryRevision++; homeLibraryJob?.cancel()
        profileRevision++; profileJob?.cancel()
        instanceProbeRevision++; instanceProbeJob?.cancel()
    }

    private fun resetPrivateData(clearHistory: Boolean = true) {
        navigation.clear()
        favoriteOwner = null
        knownFavoriteSummary = null
        knownFavoriteDetail = null
        homeSnapshotEpoch++
        homeContinueLocked = false
        homeHistoryPreviewLocked = false
        recentPlayStats = null
        recentDayPlayStats = null
        playlistsLoadedOnce = false
        favoritesLoadedOnce = false
        continueSearchSongs = emptyList()
        continueSearchReady = searchHistoryStore == null
        recentStatsReady = false
        allStatsReady = false
        favoritesReadyForContinue = false
        searchHistorySnapshotLocked = false
        secondaryOverlayActive = false
        cancelRequests()
        lyrics.clear()
        queueRestoreNotice = null
        if (clearHistory) { history?.detach(); queueRepository?.detach(); historyExtras?.detach() }
        player.imageLoader?.memoryCache?.clear()
        historyEntries = emptyList()
        historySongs.clear()
        historyResolved.clear()
        historyExtrasSnapshot = ListeningHistoryExtrasSnapshot()
        artistCoverCache.clear()
        collectionCoverCache.clear()
        collectionPreviewCoversCache.clear()
        snapshotSongCache.clear()
        activeQueueOriginType = "queue"
        activeQueueOriginLabel = ""
    }

    private data class NavigationStateSignature(
        val screen: ClientScreen,
        val navigationIdentity: String?,
        val destination: MainDestination,
        val tab: LibraryTab,
        val query: String,
        val songLanguage: String,
        val albumLanguage: String,
        val songSort: String,
        val albumId: String?,
        val playlistId: String?,
        val artistName: String?,
        val artistId: String?,
    )

    private fun navigationStateSignature(ui: ClientUiState) = NavigationStateSignature(
        screen = ui.screen,
        navigationIdentity = ui.navigationIdentity,
        destination = ui.destination,
        tab = ui.tab,
        query = ui.query,
        songLanguage = ui.songLanguage,
        albumLanguage = ui.albumLanguage,
        songSort = ui.songSort,
        albumId = ui.selectedAlbum?.id,
        playlistId = ui.selectedPlaylist?.id,
        artistName = ui.selectedArtist?.name,
        artistId = ui.selectedArtist?.id,
    )

    init {
        player.setBluetoothLyricsMode(bluetoothLyricsMode)
        viewModelScope.launch {
            state.map(::navigationStateSignature).distinctUntilChanged().collect { saveNavigationState() }
        }
        systemMediaFavorites?.let { changes -> viewModelScope.launch {
            changes.updates.collect { update ->
                if (current() !== update.connection) return@collect
                if (update.detail.playlist.kind == "favorite")
                    publishPlaylist(update.detail, notifySystem = false)
            }
        } }
        viewModelScope.launch {
            player.positionMsFlow.collect { posMs ->
                val clamped = posMs.coerceAtLeast(0L)
                if (mutablePlaybackPositionMs.value != clamped) mutablePlaybackPositionMs.value = clamped
                lyrics.updatePosition(clamped)
            }
        }
        viewModelScope.launch {
            player.state.collect { snapshot ->
                if (snapshot.playing) homeHistoryPreviewLocked = true
                mutable.value = mutable.value.copy(playback = snapshot)
                mutablePlaybackPositionMs.value = snapshot.positionMs.coerceAtLeast(0L)
                lyrics.activate(current(), snapshot.current)
                lyrics.updatePosition(snapshot.positionMs)
            }
        }
        viewModelScope.launch { lyrics.state.collect { mutable.value = mutable.value.copy(lyricState = it) } }
        history?.let { repository ->
            viewModelScope.launch {
                repository.state.collect { entries ->
                    if (current() == null) return@collect
                    historyEntries = entries
                    renderHistory()
                    hydrateHistory()
                }
            }
            viewModelScope.launch { repository.notice.collect {
                if (current() != null) mutable.value = mutable.value.copy(historyNotice = it)
            } }
        }
        historyExtras?.let { repository ->
            viewModelScope.launch {
                repository.state.collect { extras ->
                    if (current() == null) return@collect
                    historyExtrasSnapshot = extras
                    renderHistoryExtras()
                }
            }
            viewModelScope.launch { repository.notice.collect { notice ->
                if (current() != null && notice != null) mutable.value = mutable.value.copy(historyNotice = notice)
            } }
        }
        viewModelScope.launch {
            connections.invalidations.collect {
                // A buffered expiry from a retired connection must not reset a newer login/add flow.
                if (connections.current?.network?.isActive == true || addingAccount ||
                    ((mutable.value.screen == ClientScreen.CONNECT || mutable.value.screen == ClientScreen.RESTORING) && mutable.value.busy)) return@collect
                identity++
                resetPrivateData()
                player.stopAndClear()
                addingAccount = false
                val selected = accountSummaries().firstOrNull { account -> account.accountKey == connections.selectedAccountKey }
                val origin = selected?.origin ?: connections.lastOrigin
                val prefilledUsername = selected?.username ?: ""
                mutable.value = freshUi(screen = ClientScreen.CONNECT,
                    instanceUrl = origin, username = prefilledUsername, notice = it)
                if (prefilledUsername.isNotBlank() && origin.isNotBlank()) {
                    probeGoogleStatusQuietly(origin)
                }
            }
        }
        val ticket = identity
        viewModelScope.launch {
            try {
                if (ticket != identity) return@launch
                val connection = connections.restore()
                if (ticket != identity) return@launch
                if (connection == null) {
                    navigation.clear()
                    val selected = accountSummaries().firstOrNull { it.accountKey == connections.selectedAccountKey }
                    val origin = selected?.origin ?: connections.lastOrigin
                    val prefilledUsername = selected?.username ?: ""
                    mutable.value = freshUi(screen = ClientScreen.CONNECT,
                        instanceUrl = origin, username = prefilledUsername,
                        notice = if (selected?.needsReauth == true) ClientNotice.SESSION_EXPIRED else null)
                    if (prefilledUsername.isNotBlank() && origin.isNotBlank()) {
                        probeGoogleStatusQuietly(origin)
                    }
                }
                else enter(connection, ticket)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (ticket == identity) mutable.value = freshUi(screen = ClientScreen.CONNECT, notice = notice(error))
            }
        }
    }

    private fun probeGoogleStatusQuietly(origin: String) {
        val probeTicket = ++instanceProbeRevision
        val viewTicket = identity
        instanceProbeJob?.cancel()
        instanceProbeJob = viewModelScope.launch {
            val probe = runCatching { connections.checkInstance(origin) }.getOrNull() ?: return@launch
            if (probeTicket != instanceProbeRevision || viewTicket != identity) return@launch
            val currentUi = mutable.value
            if (currentUi.screen == ClientScreen.CONNECT &&
                currentUi.connectStep == ConnectStep.CREDENTIALS &&
                currentUi.instanceUrl == probe.origin
            ) {
                mutable.value = currentUi.copy(
                    googleLoginEnabled = probe.googleEnabled,
                    googleNativeLoginEnabled = probe.googleNativeEnabled,
                )
            }
        }
    }

    override fun checkInstance(instanceUrl: String) {
        if (mutable.value.busy) return
        val normalized = try {
            InstanceAddress.parse(instanceUrl).origin
        } catch (error: Exception) {
            mutable.value = mutable.value.copy(notice = notice(error))
            return
        }
        val viewTicket = identity
        val probeTicket = ++instanceProbeRevision
        instanceProbeJob?.cancel()
        clearPendingGoogleAuth()
        mutable.value = mutable.value.copy(
            instanceUrl = normalized,
            busy = true,
            notice = null,
            googleLoginEnabled = false,
            googleNativeLoginEnabled = false,
        )
        instanceProbeJob = viewModelScope.launch {
            try {
                val probe = connections.checkInstance(normalized)
                if (probeTicket != instanceProbeRevision || viewTicket != identity) return@launch
                mutable.value = mutable.value.copy(
                    instanceUrl = probe.origin,
                    connectStep = ConnectStep.CREDENTIALS,
                    googleLoginEnabled = probe.googleEnabled,
                    googleNativeLoginEnabled = probe.googleNativeEnabled,
                    busy = false,
                    notice = null,
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (probeTicket == instanceProbeRevision && viewTicket == identity) {
                    mutable.value = mutable.value.copy(
                        connectStep = ConnectStep.INSTANCE,
                        busy = false,
                        notice = notice(error),
                    )
                }
            }
        }
    }

    override fun editInstanceAddress() {
        if (mutable.value.busy) return
        instanceProbeRevision++
        instanceProbeJob?.cancel()
        clearPendingGoogleAuth()
        exchangingGoogleCode = false
        mutable.value = mutable.value.copy(
            connectStep = ConnectStep.INSTANCE,
            username = "",
            googleLoginEnabled = false,
            googleNativeLoginEnabled = false,
            googleAuthLaunchUrl = null,
            notice = null,
        )
    }

    override fun startGoogleLogin() {
        val ui = mutable.value
        if (ui.screen != ClientScreen.CONNECT || ui.connectStep != ConnectStep.CREDENTIALS || ui.busy) return
        val origin = try {
            InstanceAddress.parse(ui.instanceUrl).origin
        } catch (error: Exception) {
            mutable.value = ui.copy(notice = notice(error))
            return
        }
        val verifier = GoogleAuthPkce.generateVerifier()
        val challenge = GoogleAuthPkce.codeChallenge(verifier)
        savePendingGoogleAuth(origin, verifier)
        exchangingGoogleCode = false
        val startUrl = GoogleAuthPkce.buildNativeStartUri(origin, challenge).toString()
        mutable.value = ui.copy(
            instanceUrl = origin,
            busy = true,
            notice = null,
            googleAuthLaunchUrl = startUrl,
        )
    }

    override fun consumeGoogleAuthLaunch() {
        if (mutable.value.googleAuthLaunchUrl != null) {
            mutable.value = mutable.value.copy(googleAuthLaunchUrl = null)
        }
    }

    override fun reportGoogleAuthLaunchFailed() {
        clearPendingGoogleAuth()
        exchangingGoogleCode = false
        mutable.value = mutable.value.copy(
            busy = false,
            googleAuthLaunchUrl = null,
            notice = ClientNotice.GOOGLE_NO_BROWSER,
        )
    }

    fun onForegroundResume() {
        val ui = mutable.value
        if (ui.screen == ClientScreen.CONNECT &&
            ui.busy &&
            pendingGoogleVerifier != null &&
            ui.googleAuthLaunchUrl == null &&
            !exchangingGoogleCode
        ) {
            clearPendingGoogleAuth()
            mutable.value = ui.copy(busy = false)
        }
        val update = appUpdateState
        if (update.status == AppUpdateStatus.INSTALL_PERMISSION_REQUIRED &&
            appUpdateInstaller?.canRequestPackageInstalls() == true
        ) {
            val path = update.downloadedApkPath
            if (!path.isNullOrBlank()) {
                launchApkInstall(java.io.File(path), appUpdateInstaller)
            } else {
                updateAppUpdateState { it.copy(status = AppUpdateStatus.AVAILABLE, errorKind = null) }
            }
        }
    }

    fun handleGoogleAuthCallback(code: String?, error: String?) {
        val origin = pendingGoogleOrigin
        val verifier = pendingGoogleVerifier
        val startedAt = pendingGoogleStartedAtMs
        val hasPendingFlow = !origin.isNullOrBlank() && !verifier.isNullOrBlank() && startedAt != null
        if (!hasPendingFlow) {
            val currentUi = mutable.value
            if (currentUi.screen != ClientScreen.CONNECT || !currentUi.busy) return
            clearPendingGoogleAuth()
            exchangingGoogleCode = false
            mutable.value = currentUi.copy(
                busy = false,
                googleAuthLaunchUrl = null,
                notice = mapGoogleCallbackError(error),
            )
            return
        }
        clearPendingGoogleAuth()
        val keepGoogleEnabled = mutable.value.googleLoginEnabled
        val keepGoogleNative = mutable.value.googleNativeLoginEnabled
        if (!error.isNullOrBlank()) {
            exchangingGoogleCode = false
            ++identity
            cancelRequests()
            mutable.value = freshUi(
                screen = ClientScreen.CONNECT,
                instanceUrl = origin,
                busy = false,
                connectStep = ConnectStep.CREDENTIALS,
                googleLoginEnabled = keepGoogleEnabled,
                googleNativeLoginEnabled = keepGoogleNative,
                notice = mapGoogleCallbackError(error),
            )
            if (!keepGoogleEnabled) probeGoogleStatusQuietly(origin)
            return
        }
        val notExpired = (System.currentTimeMillis() - startedAt) <= PENDING_GOOGLE_TTL_MS
        if (code.isNullOrBlank() || !notExpired) {
            exchangingGoogleCode = false
            ++identity
            cancelRequests()
            mutable.value = freshUi(
                screen = ClientScreen.CONNECT,
                instanceUrl = origin,
                busy = false,
                connectStep = ConnectStep.CREDENTIALS,
                googleLoginEnabled = keepGoogleEnabled,
                googleNativeLoginEnabled = keepGoogleNative,
                notice = ClientNotice.GOOGLE_FAILED,
            )
            if (!keepGoogleEnabled) probeGoogleStatusQuietly(origin)
            return
        }
        exchangingGoogleCode = true
        val ticket = ++identity
        resetPrivateData()
        player.stopAndClear()
        mutable.value = freshUi(
            screen = ClientScreen.CONNECT,
            instanceUrl = origin,
            busy = true,
            connectStep = ConnectStep.CREDENTIALS,
            googleLoginEnabled = keepGoogleEnabled,
            googleNativeLoginEnabled = keepGoogleNative,
        )
        viewModelScope.launch {
            try {
                if (ticket != identity) return@launch
                val connection = connections.loginWithGoogle(origin, code, verifier)
                if (ticket == identity) enter(connection, ticket)
            } catch (err: Exception) {
                if (err is CancellationException) throw err
                if (ticket == identity) {
                    refreshSavedAccounts()
                    mutable.value = mutable.value.copy(busy = false, notice = notice(err))
                    if (!keepGoogleEnabled) probeGoogleStatusQuietly(origin)
                }
            } finally {
                exchangingGoogleCode = false
            }
        }
    }

    private fun mapGoogleCallbackError(error: String?): ClientNotice = when (error) {
        "cancelled" -> ClientNotice.GOOGLE_CANCELLED
        "unbound" -> ClientNotice.GOOGLE_UNBOUND
        "rate_limited" -> ClientNotice.RATE_LIMITED
        "google_unavailable" -> ClientNotice.GOOGLE_UNAVAILABLE
        else -> ClientNotice.GOOGLE_FAILED
    }

    override fun login(instanceUrl: String, username: String, password: String) {
        if (mutable.value.busy) return
        if (username.isBlank() || password.isEmpty()) {
            mutable.value = mutable.value.copy(notice = ClientNotice.INVALID_LOGIN)
            return
        }
        clearPendingGoogleAuth()
        exchangingGoogleCode = false
        val keepGoogleEnabled = mutable.value.googleLoginEnabled
        val keepGoogleNative = mutable.value.googleNativeLoginEnabled
        val ticket = ++identity
        resetPrivateData()
        player.stopAndClear()
        mutable.value = freshUi(screen = ClientScreen.CONNECT, instanceUrl = instanceUrl.trim(),
            username = username.trim(), busy = true, connectStep = ConnectStep.CREDENTIALS,
            googleLoginEnabled = keepGoogleEnabled, googleNativeLoginEnabled = keepGoogleNative)
        viewModelScope.launch {
            try {
                if (ticket != identity) return@launch
                val connection = connections.login(instanceUrl, username, password)
                if (ticket == identity) enter(connection, ticket)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (ticket == identity) { refreshSavedAccounts(); mutable.value = mutable.value.copy(busy = false, notice = notice(error)) }
            }
        }
    }

    private suspend fun enter(connection: ActiveConnection, ticket: Long) {
        if (ticket != identity || connections.current !== connection || !connection.network.isActive) return
        addingAccount = false
        favoriteOwner = connection
        knownFavoriteSummary = null
        knownFavoriteDetail = null
        val restoredNavigation = if (connection.auth.mustChangePassword) { navigation.clear(); null } else navigation.restore(connection)
        val initialAvatarUrl = connection.auth.account.avatarUrl ?: accountSummaries().firstOrNull { it.active }?.avatarUrl
        val baseState = freshUi(
            screen = if (connection.auth.mustChangePassword) ClientScreen.CHANGE_PASSWORD else ClientScreen.LIBRARY,
            instanceUrl = connection.network.address.origin, username = connection.auth.account.username,
            accountName = connection.auth.account.displayName,
            avatarUrl = initialAvatarUrl,
            avatarRevision = connection.auth.account.avatarRevision,
            translationEnabled = translationEnabled,
            navigationIdentity = ClientNavigationState.identity(connection), playbackReady = false)
        mutable.value = restoredNavigation?.applyTo(baseState) ?: baseState
        if (connection.auth.mustChangePassword) return
        try { connectPlayer(connection) } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (valid(ticket, connection)) mutable.value = mutable.value.copy(notice = ClientNotice.PLAYBACK_FAILED)
        }
        if (valid(ticket, connection)) {
            beginHomeSnapshotRefresh(unlockHistoryPreview = false)
            history?.let { repository ->
                repository.bind(HistoryIdentity(connection.network.address.origin, connection.auth.account.id))
                // A reused application repository may already have emitted during RESTORING.
                historyEntries = repository.state.value
                mutable.value = mutable.value.copy(historyNotice = repository.notice.value)
                renderHistory()
                hydrateHistory()
            }
            historyExtras?.let { repository ->
                repository.bind(HistoryIdentity(connection.network.address.origin, connection.auth.account.id))
                historyExtrasSnapshot = repository.state.value
                renderHistoryExtras()
            }
            queueRepository?.let { repository ->
                repository.bind(HistoryIdentity(connection.network.address.origin, connection.auth.account.id))
                observeQueueNotice(repository, connection, ticket)
            }
            restoreSavedQueue()
            val accountIdentity = HistoryIdentity(connection.network.address.origin, connection.auth.account.id)
            val savedPlaylistSort = runCatching { playlistPreferences?.readSortMode(accountIdentity) ?: PlaylistSortMode.CUSTOM }
                .getOrDefault(PlaylistSortMode.CUSTOM)
            mutable.value = mutable.value.copy(
                playbackReady = true,
                playback = player.state.value,
                playlistSortMode = savedPlaylistSort,
            )
            lyrics.activate(connection, player.state.value.current)
            lyrics.updatePosition(player.state.value.positionMs)
            refreshCatalogAndPlaylists()
            if (restoredNavigation?.hasDetail == true) { load(true); loadAlbums(true); refreshPlaylists() }
            if (restoredNavigation != null && mutable.value.destination == MainDestination.SEARCH) {
                refreshArtists(mutable.value.query); searchAlbums(mutable.value.query)
            }
            loadListeningOverview()
            refreshAvatarSlot(connection, ticket)
        }
    }

    override fun changePassword(currentPassword: String, newPassword: String) {
        if (mutable.value.busy) return
        val ticket = identity
        mutable.value = mutable.value.copy(busy = true, notice = null)
        viewModelScope.launch {
            try {
                connections.changePassword(currentPassword, newPassword)
                if (ticket == identity) {
                    identity++
                    resetPrivateData()
                    player.stopAndClear()
                    mutable.value = freshUi(screen = ClientScreen.CONNECT,
                        instanceUrl = connections.lastOrigin, username = mutable.value.username,
                        notice = ClientNotice.PASSWORD_CHANGED)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (ticket == identity) mutable.value = mutable.value.copy(busy = false, notice = notice(error))
            }
        }
    }

    override fun logout() {
        addingAccount = false
        clearPendingGoogleAuth()
        exchangingGoogleCode = false
        resetAppUpdateInteraction()
        val ticket = ++identity
        resetPrivateData()
        player.stopAndClear()
        mutable.value = freshUi(screen = ClientScreen.CONNECT, instanceUrl = connections.lastOrigin, busy = true)
        viewModelScope.launch {
            var failure: ClientNotice? = null
            try { if (ticket != identity) return@launch; connections.logout() } catch (error: Exception) {
                if (error is CancellationException) throw error
                failure = notice(error)
            }
            if (ticket == identity) { refreshSavedAccounts(); mutable.value = mutable.value.copy(busy = false, notice = failure) }
        }
    }

    override fun beginAddAccount() {
        clearPendingGoogleAuth()
        exchangingGoogleCode = false
        ++identity
        resetPrivateData()
        player.stopAndClear()
        connections.beginAddAccount()
        addingAccount = true
        mutable.value = freshUi(screen = ClientScreen.CONNECT)
    }

    override fun cancelAddAccount() {
        if (!addingAccount) return
        transitionAccount(playSplash = true) { connections.cancelAddAccount() }
    }

    override fun switchAccount(accountKey: String) {
        if (accountSummaries().none { it.accountKey == accountKey }) return
        if (!addingAccount && connections.activeAccountKey == accountKey && current() != null) return
        transitionAccount(accountKey, playSplash = true) { connections.switchAccount(accountKey) }
    }

    private fun transitionAccount(
        accountKey: String? = null,
        playSplash: Boolean = false,
        work: suspend (() -> Boolean) -> ActiveConnection?,
    ) {
        val selected = accountSummaries().firstOrNull { it.accountKey == accountKey }
        clearPendingGoogleAuth()
        exchangingGoogleCode = false
        val ticket = ++identity
        if (playSplash) splashNonce++
        resetPrivateData()
        player.stopAndClear()
        addingAccount = false
        mutable.value = freshUi(
            screen = if (playSplash) ClientScreen.RESTORING else ClientScreen.CONNECT,
            instanceUrl = selected?.origin ?: connections.lastOrigin,
            username = selected?.username ?: "",
            accountName = if (playSplash) (selected?.displayName ?: "") else "",
            busy = true,
        )
        viewModelScope.launch {
            if (ticket != identity) return@launch
            try {
                val connection = work { ticket == identity }
                if (ticket != identity) return@launch
                if (connection != null) enter(connection, ticket)
                else {
                    refreshSavedAccounts()
                    val target = accountSummaries().firstOrNull { it.accountKey == connections.selectedAccountKey } ?: selected
                    val origin = target?.origin ?: mutable.value.instanceUrl
                    val prefilledUsername = target?.username ?: mutable.value.username
                    mutable.value = mutable.value.copy(
                        screen = ClientScreen.CONNECT,
                        busy = false,
                        instanceUrl = origin,
                        username = prefilledUsername,
                        accountName = "",
                        connectStep = if (prefilledUsername.isNotBlank() && origin.isNotBlank()) ConnectStep.CREDENTIALS else ConnectStep.INSTANCE,
                        notice = if (target?.needsReauth == true) ClientNotice.SESSION_EXPIRED else null,
                    )
                    if (prefilledUsername.isNotBlank() && origin.isNotBlank()) probeGoogleStatusQuietly(origin)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (ticket == identity) {
                    refreshSavedAccounts()
                    val target = accountSummaries().firstOrNull { it.accountKey == connections.selectedAccountKey } ?: selected
                    val origin = target?.origin ?: mutable.value.instanceUrl
                    val prefilledUsername = target?.username ?: mutable.value.username
                    mutable.value = mutable.value.copy(
                        screen = ClientScreen.CONNECT,
                        busy = false,
                        instanceUrl = origin,
                        username = prefilledUsername,
                        accountName = "",
                        connectStep = if (prefilledUsername.isNotBlank() && origin.isNotBlank()) ConnectStep.CREDENTIALS else ConnectStep.INSTANCE,
                        notice = notice(error),
                    )
                    if (prefilledUsername.isNotBlank() && origin.isNotBlank()) probeGoogleStatusQuietly(origin)
                }
            }
        }
    }

    override fun removeSavedAccount(accountKey: String) {
        val target = accountSummaries().firstOrNull { it.accountKey == accountKey } ?: return
        val targetHistoryIdentity = HistoryIdentity(target.origin, target.accountId)
        val active = connections.activeAccountKey == accountKey
        if (active || mutable.value.busy || addingAccount) {
            transitionAccount { stillCurrent ->
                connections.removeSavedAccount(accountKey)
                historyExtras?.clearAccount(targetHistoryIdentity)
                playlistPreferences?.clear(targetHistoryIdentity)
                if (!stillCurrent()) return@transitionAccount null
                // Newest first; never let an old fallback retire a newer user intent.
                var next: ActiveConnection? = null
                for (candidate in accountSummaries().filterNot { it.needsReauth }) {
                    if (!stillCurrent()) return@transitionAccount null
                    next = connections.switchAccount(candidate.accountKey)
                    if (!stillCurrent()) return@transitionAccount null
                    if (next != null) break
                }
                next
            }
        } else {
            val ticket = identity
            viewModelScope.launch {
                try {
                    connections.removeSavedAccount(accountKey)
                    historyExtras?.clearAccount(targetHistoryIdentity)
                    playlistPreferences?.clear(targetHistoryIdentity)
                    if (ticket == identity) refreshSavedAccounts()
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    if (ticket == identity) mutable.value = mutable.value.copy(notice = notice(error))
                }
            }
        }
    }

    override fun setSleepTimer(minutes: Int) { if (current() != null) player.setSleepTimer(minutes) }
    override fun setSleepTimerEndOfTrack() { if (current() != null) player.setSleepTimerEndOfTrack() }
    override fun setSleepTimerFinishCurrentTrack(enabled: Boolean) {
        if (current() != null) player.setSleepTimerFinishCurrentTrack(enabled)
    }
    override fun cancelSleepTimer() = player.cancelSleepTimer()

    override fun setKeepScreenOnEnabled(enabled: Boolean) {
        val revision = ++keepScreenRevision
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { preferenceMutex.withLock {
                    if (revision == keepScreenRevision) translationPreferences?.writeKeepScreenOn(enabled)
                } }
                if (revision == keepScreenRevision) {
                    keepScreenOnEnabled = enabled
                    mutable.value = mutable.value.copy(keepScreenOnEnabled = enabled)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (revision == keepScreenRevision) mutable.value = mutable.value.copy(notice = ClientNotice.STORAGE_FAILED)
            }
        }
    }

    override fun setThemeMode(mode: AppThemeMode) {
        val revision = ++themeModeRevision
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { preferenceMutex.withLock {
                    if (revision == themeModeRevision) translationPreferences?.writeThemeMode(mode)
                } }
                if (revision == themeModeRevision) {
                    themeMode = mode
                    mutable.value = mutable.value.copy(themeMode = mode)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (revision == themeModeRevision) mutable.value = mutable.value.copy(notice = ClientNotice.STORAGE_FAILED)
            }
        }
    }

    override fun setAppLanguage(language: AppLanguage) {
        val store = appLanguagePreferences
        if (store == null) {
            // Nothing can be persisted, so do not pretend the choice took effect.
            mutable.value = mutable.value.copy(notice = ClientNotice.STORAGE_FAILED)
            return
        }
        val revision = ++appLanguageRevision
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { preferenceMutex.withLock {
                    if (revision == appLanguageRevision) store.write(language)
                } }
                if (revision == appLanguageRevision) {
                    appLanguage = language
                    languageChangeToken += 1
                    mutable.value = mutable.value.copy(appLanguage = language, languageChangeToken = languageChangeToken)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (revision == appLanguageRevision) mutable.value = mutable.value.copy(notice = ClientNotice.STORAGE_FAILED)
            }
        }
    }

    override fun setBluetoothLyricsMode(mode: BluetoothLyricsMode) {
        val revision = ++bluetoothLyricsRevision
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { preferenceMutex.withLock {
                    if (revision == bluetoothLyricsRevision) translationPreferences?.writeBluetoothLyricsMode(mode)
                } }
                if (revision == bluetoothLyricsRevision) {
                    bluetoothLyricsMode = mode
                    player.setBluetoothLyricsMode(mode)
                    mutable.value = mutable.value.copy(bluetoothLyricsMode = mode)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (revision == bluetoothLyricsRevision) mutable.value = mutable.value.copy(notice = ClientNotice.STORAGE_FAILED)
            }
        }
    }

    private fun updateAppUpdateState(transform: (AppUpdateState) -> AppUpdateState) {
        val next = transform(appUpdateState)
        appUpdateState = next
        mutable.value = mutable.value.copy(appUpdate = next)
    }

    private fun resetAppUpdateInteraction() {
        appUpdateRevision++
        appUpdateJob?.cancel()
        appUpdateJob = null
        val current = appUpdateState
        val nextStatus = when (current.status) {
            AppUpdateStatus.CHECKING -> AppUpdateStatus.IDLE
            AppUpdateStatus.DOWNLOADING -> if (current.availableRelease != null) AppUpdateStatus.AVAILABLE else AppUpdateStatus.IDLE
            else -> current.status
        }
        appUpdateState = current.copy(
            status = nextStatus,
            dialogVisible = false,
            downloadedBytes = if (current.status == AppUpdateStatus.DOWNLOADING) 0L else current.downloadedBytes,
            totalBytes = if (current.status == AppUpdateStatus.DOWNLOADING) -1L else current.totalBytes,
        )
    }

    override fun setIncludePrereleaseUpdates(enabled: Boolean) {
        val revision = ++includePrereleaseRevision
        val previous = includePrereleaseUpdates
        includePrereleaseUpdates = enabled
        updateAppUpdateState { state ->
            val keepActiveState = state.status == AppUpdateStatus.CHECKING ||
                state.status == AppUpdateStatus.DOWNLOADING ||
                state.status == AppUpdateStatus.READY_TO_INSTALL ||
                state.status == AppUpdateStatus.INSTALL_PERMISSION_REQUIRED
            if (keepActiveState) {
                state.copy(includePrereleases = enabled)
            } else {
                state.copy(
                    includePrereleases = enabled,
                    status = AppUpdateStatus.IDLE,
                    availableRelease = null,
                    errorKind = null,
                )
            }
        }
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    preferenceMutex.withLock {
                        if (revision == includePrereleaseRevision) {
                            translationPreferences?.writeIncludePrereleases(enabled)
                        }
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (revision == includePrereleaseRevision) {
                    includePrereleaseUpdates = previous
                    updateAppUpdateState { it.copy(includePrereleases = previous) }
                    mutable.value = mutable.value.copy(notice = ClientNotice.STORAGE_FAILED)
                }
            }
        }
    }

    override fun checkForAppUpdate() {
        if (appUpdateState.status == AppUpdateStatus.CHECKING || appUpdateState.status == AppUpdateStatus.DOWNLOADING) return
        val updater = appUpdater
        if (updater == null) {
            updateAppUpdateState {
                it.copy(
                    status = AppUpdateStatus.FAILED,
                    dialogVisible = false,
                    errorKind = AppUpdateError.REPOSITORY_UNAVAILABLE,
                )
            }
            return
        }
        val revision = ++appUpdateRevision
        val includePrereleases = includePrereleaseUpdates
        val currentVersion = appVersionName
        appUpdateJob?.cancel()
        updateAppUpdateState {
            it.copy(
                currentVersion = currentVersion,
                status = AppUpdateStatus.CHECKING,
                dialogVisible = false,
                downloadedBytes = 0L,
                totalBytes = -1L,
                downloadedApkPath = null,
                errorKind = null,
            )
        }
        appUpdateJob = viewModelScope.launch {
            try {
                val result = withContext(ioDispatcher) {
                    updater.checkLatest(
                        includePrereleases = includePrereleases,
                        currentVersion = currentVersion,
                    )
                }
                if (revision != appUpdateRevision) return@launch
                when (result) {
                    is AppUpdateCheckResult.Available -> {
                        updateAppUpdateState {
                            it.copy(
                                status = AppUpdateStatus.AVAILABLE,
                                dialogVisible = true,
                                availableRelease = result.release,
                                errorKind = null,
                            )
                        }
                    }
                    is AppUpdateCheckResult.UpToDate -> {
                        updateAppUpdateState {
                            it.copy(
                                status = AppUpdateStatus.UP_TO_DATE,
                                dialogVisible = false,
                                availableRelease = null,
                                errorKind = null,
                            )
                        }
                    }
                    AppUpdateCheckResult.NoReleaseFound -> {
                        updateAppUpdateState {
                            it.copy(
                                status = AppUpdateStatus.NO_RELEASE_FOUND,
                                dialogVisible = false,
                                availableRelease = null,
                                errorKind = null,
                            )
                        }
                    }
                    is AppUpdateCheckResult.Failed -> {
                        updateAppUpdateState {
                            it.copy(
                                status = AppUpdateStatus.FAILED,
                                dialogVisible = false,
                                errorKind = result.error,
                            )
                        }
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (revision == appUpdateRevision) {
                    updateAppUpdateState {
                        it.copy(
                            status = AppUpdateStatus.FAILED,
                            dialogVisible = false,
                            errorKind = AppUpdateError.NETWORK,
                        )
                    }
                }
            }
        }
    }

    override fun openAppUpdateDialog() {
        if (appUpdateState.availableRelease == null) return
        updateAppUpdateState { it.copy(dialogVisible = true) }
    }

    override fun dismissAppUpdateDialog() {
        updateAppUpdateState { it.copy(dialogVisible = false) }
    }

    override fun startAppUpdateDownload() {
        val release = appUpdateState.availableRelease ?: return
        if (appUpdateState.status == AppUpdateStatus.DOWNLOADING) return
        val updater = appUpdater
        if (updater == null) {
            updateAppUpdateState {
                it.copy(
                    status = AppUpdateStatus.FAILED,
                    dialogVisible = true,
                    errorKind = AppUpdateError.REPOSITORY_UNAVAILABLE,
                )
            }
            return
        }
        if (release.apkDownloadUrl.isNullOrBlank()) {
            updateAppUpdateState {
                it.copy(
                    status = AppUpdateStatus.FAILED,
                    dialogVisible = true,
                    errorKind = AppUpdateError.NO_APK_ASSET,
                )
            }
            return
        }
        val revision = ++appUpdateRevision
        appUpdateJob?.cancel()
        val initialTotal = release.apkSizeBytes.takeIf { it > 0L } ?: -1L
        updateAppUpdateState {
            it.copy(
                status = AppUpdateStatus.DOWNLOADING,
                dialogVisible = true,
                downloadedBytes = 0L,
                totalBytes = initialTotal,
                downloadedApkPath = null,
                errorKind = null,
            )
        }
        appUpdateJob = viewModelScope.launch {
            try {
                var lastReportedBytes = 0L
                var lastReportedPercent = -1
                val apkFile = withContext(ioDispatcher) {
                    updater.downloadApk(release) { downloaded, total ->
                        val effectiveTotal = if (total > 0L) total else initialTotal
                        val percent = if (effectiveTotal > 0L) ((downloaded * 100L) / effectiveTotal).toInt().coerceIn(0, 100) else -1
                        val bytesDelta = downloaded - lastReportedBytes
                        if (percent != lastReportedPercent || bytesDelta >= 128 * 1024L || (effectiveTotal > 0L && downloaded >= effectiveTotal)) {
                            lastReportedBytes = downloaded
                            lastReportedPercent = percent
                            viewModelScope.launch {
                                if (revision == appUpdateRevision && appUpdateState.status == AppUpdateStatus.DOWNLOADING) {
                                    updateAppUpdateState {
                                        it.copy(
                                            downloadedBytes = downloaded,
                                            totalBytes = effectiveTotal,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (revision != appUpdateRevision) return@launch
                val length = apkFile.length()
                updateAppUpdateState {
                    it.copy(
                        status = AppUpdateStatus.READY_TO_INSTALL,
                        downloadedApkPath = apkFile.absolutePath,
                        downloadedBytes = length,
                        totalBytes = length,
                        errorKind = null,
                    )
                }
                launchApkInstall(apkFile, appUpdateInstaller)
            } catch (error: AppUpdateFailure) {
                if (revision == appUpdateRevision) {
                    updateAppUpdateState {
                        it.copy(
                            status = AppUpdateStatus.FAILED,
                            errorKind = error.error,
                        )
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (revision == appUpdateRevision) {
                    updateAppUpdateState {
                        it.copy(
                            status = AppUpdateStatus.FAILED,
                            errorKind = AppUpdateError.NETWORK,
                        )
                    }
                }
            }
        }
    }

    override fun cancelAppUpdateDownload() {
        appUpdateRevision++
        appUpdateJob?.cancel()
        appUpdateJob = null
        if (appUpdateState.status == AppUpdateStatus.DOWNLOADING || appUpdateState.status == AppUpdateStatus.CHECKING) {
            updateAppUpdateState { state ->
                state.copy(
                    status = if (state.availableRelease != null) AppUpdateStatus.AVAILABLE else AppUpdateStatus.IDLE,
                    downloadedBytes = 0L,
                    totalBytes = -1L,
                    errorKind = null,
                )
            }
        }
    }

    override fun installDownloadedAppUpdate() {
        val path = appUpdateState.downloadedApkPath
        if (path.isNullOrBlank()) {
            startAppUpdateDownload()
            return
        }
        val file = java.io.File(path)
        if (!file.exists()) {
            startAppUpdateDownload()
            return
        }
        launchApkInstall(file, appUpdateInstaller)
    }

    private fun launchApkInstall(apkFile: java.io.File, installer: AppUpdateInstaller?) {
        if (!apkFile.exists()) {
            updateAppUpdateState {
                it.copy(
                    status = AppUpdateStatus.FAILED,
                    downloadedApkPath = null,
                    errorKind = AppUpdateError.STORAGE,
                )
            }
            return
        }
        if (installer == null) {
            updateAppUpdateState {
                it.copy(
                    status = AppUpdateStatus.FAILED,
                    errorKind = AppUpdateError.INSTALL_FAILED,
                )
            }
            return
        }
        if (!installer.canRequestPackageInstalls()) {
            val openedSettings = installer.openUnknownSourcesSettings()
            updateAppUpdateState {
                if (openedSettings) {
                    it.copy(
                        status = AppUpdateStatus.INSTALL_PERMISSION_REQUIRED,
                        errorKind = null,
                    )
                } else {
                    it.copy(
                        status = AppUpdateStatus.FAILED,
                        errorKind = AppUpdateError.INSTALL_FAILED,
                    )
                }
            }
            return
        }
        val launched = installer.installApk(apkFile)
        updateAppUpdateState {
            if (launched) {
                it.copy(status = AppUpdateStatus.READY_TO_INSTALL, errorKind = null)
            } else {
                it.copy(status = AppUpdateStatus.FAILED, errorKind = AppUpdateError.INSTALL_FAILED)
            }
        }
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    override fun clearImageCache() {
        if (mutable.value.imageCacheClearResult == ImageCacheClearResult.CLEARING) return
        val loader = player.imageLoader
        if (loader == null || (loader.memoryCache == null && loader.diskCache == null)) {
            mutable.value = mutable.value.copy(imageCacheClearResult = ImageCacheClearResult.UNAVAILABLE)
            return
        }
        val ticket = identity
        mutable.value = mutable.value.copy(imageCacheClearResult = ImageCacheClearResult.CLEARING)
        viewModelScope.launch {
            val result = try {
                loader.memoryCache?.clear()
                withContext(ioDispatcher) { loader.diskCache?.clear() }
                ImageCacheClearResult.CLEARED
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                ImageCacheClearResult.FAILED
            }
            if (ticket == identity) mutable.value = mutable.value.copy(imageCacheClearResult = result)
        }
    }

    override fun search(query: String) {
        val normalized = query.trim().take(200)
        if (normalized.isNotBlank()) updateSearchHistory(SearchSnapshotMode.RECORD_ONLY) { store, key -> store.addTerm(key, normalized) }
        if (normalized != mutable.value.query) {
            offset = 0
            mutable.value = mutable.value.copy(query = normalized, songs = emptyList(), hasMore = false, loadingMore = false)
        }
        load(true)
        refreshArtists(normalized)
        searchAlbums(normalized)
    }

    override fun selectTab(tab: LibraryTab) {
        if (tab == mutable.value.tab) return
        requestRevision++
        requestJob?.cancel()
        offset = 0
        mutable.value = mutable.value.copy(tab = tab, query = "", songs = emptyList(), hasMore = false,
            loading = false, loadingMore = false)
        if (tab == LibraryTab.ALBUMS) loadAlbums(true) else load(true)
    }

    override fun navigate(destination: MainDestination) {
        val currentState = mutable.value
        val previousDestination = currentState.destination
        val hadDetail = currentState.selectedAlbum != null ||
            currentState.selectedPlaylist != null ||
            currentState.selectedArtist != null
        val hadPickerState = currentState.pickerSongs.isNotEmpty() ||
            currentState.pickerQuery.isNotEmpty() ||
            currentState.pickerLoading ||
            currentState.pickerLoadingMore ||
            currentState.pickerHasMore ||
            currentState.pickerNotice != null
        val needsLibraryCatalogReset = destination == MainDestination.LIBRARY && currentState.tab != LibraryTab.CATALOG
        val needsDiscoverLangSync = destination == MainDestination.DISCOVER &&
            (currentState.songLanguage != currentState.discoverLanguage ||
                currentState.albumLanguage != currentState.discoverLanguage)
        if (destination == previousDestination && !hadDetail && !needsLibraryCatalogReset && !needsDiscoverLangSync) {
            if (hadPickerState) closePlaylistSongPicker()
            if (destination == MainDestination.LIBRARY && currentState.query.isNotBlank()) search("")
            return
        }
        detailRevision++; detailJob?.cancel()
        playlistRevision++; playlistJob?.cancel()
        closePlaylistSongPicker()
        artistRevision++; artistJob?.cancel(); artistOffset = 0
        artistAlbumsRevision++; artistAlbumsJob?.cancel(); artistAlbumsOffset = 0
        mutable.value = mutable.value.copy(
            destination = destination,
            selectedAlbum = null, albumSongs = emptyList(), albumLoading = false, albumNotice = null,
            selectedPlaylist = null, playlistSongs = emptyList(), selectedPlaylistEntries = emptyList(),
            playlistLoading = false, playlistNotice = null,
            selectedArtist = null, artistSongs = emptyList(), artistLoading = false,
            artistLoadingMore = false, artistHasMore = false, artistNotice = null,
            artistAlbums = emptyList(), artistAlbumsLoading = false, artistAlbumsLoadingMore = false,
            artistAlbumsHasMore = false, artistAlbumsNotice = null,
        )
        when (destination) {
            MainDestination.HOME -> {
                val leftSearch = previousDestination == MainDestination.SEARCH
                if (mutable.value.tab != LibraryTab.CATALOG) selectTab(LibraryTab.CATALOG)
                else if (mutable.value.query.isNotBlank()) search("")
                else if (leftSearch) load(true)
                if (mutable.value.homeLibrarySongs.isEmpty() && !mutable.value.homeLibraryLoading) loadHomeLibrarySongs()
                if (!favoritesLoadedOnce || mutable.value.favoritesNotice != null) refreshFavorites()
            }
            MainDestination.DISCOVER -> {
                val leftSearch = previousDestination == MainDestination.SEARCH
                val targetLanguage = mutable.value.discoverLanguage
                val syncLang = mutable.value.songLanguage != targetLanguage || mutable.value.albumLanguage != targetLanguage
                if (syncLang) {
                    mutable.value = mutable.value.copy(songLanguage = targetLanguage, albumLanguage = targetLanguage)
                }
                val clearedSearch = mutable.value.tab == LibraryTab.CATALOG && mutable.value.query.isNotBlank()
                if (mutable.value.tab != LibraryTab.CATALOG) selectTab(LibraryTab.CATALOG)
                else if (clearedSearch) search("")
                else if (leftSearch || syncLang || (mutable.value.songs.isEmpty() && !mutable.value.loading)) load(true)
                if (!clearedSearch) {
                    if (mutable.value.artistQuery.isNotBlank()) refreshArtists("")
                    else if (leftSearch || (mutable.value.artists.isEmpty() && !mutable.value.artistsLoading)) loadArtists(true)
                }
                if (leftSearch || syncLang || (mutable.value.albums.isEmpty() && !mutable.value.albumsLoading)) loadAlbums(true)
            }
            MainDestination.SEARCH -> {
                if (mutable.value.tab != LibraryTab.CATALOG) selectTab(LibraryTab.CATALOG)
            }
            MainDestination.LIBRARY -> {
                if (mutable.value.tab != LibraryTab.CATALOG) selectTab(LibraryTab.CATALOG)
                else if (mutable.value.query.isNotBlank()) search("")
                if (!playlistsLoadedOnce || mutable.value.playlistsNotice != null) refreshPlaylists()
            }
        }
    }

    private fun beginHomeSnapshotRefresh(unlockHistoryPreview: Boolean) {
        homeSnapshotEpoch++
        homeContinueLocked = false
        recentPlayStats = null
        recentDayPlayStats = null
        playlistsLoadedOnce = false
        favoritesLoadedOnce = false
        continueSearchSongs = emptyList()
        continueSearchReady = searchHistoryStore == null
        recentStatsReady = false
        allStatsReady = false
        favoritesReadyForContinue = false
        if (unlockHistoryPreview) {
            homeHistoryPreviewLocked = false
            mutable.value.playHistory.firstOrNull()?.let { latest ->
                mutable.value = mutable.value.copy(homeHistoryPreviewSong = latest)
                homeHistoryPreviewLocked = true
            }
        }
    }

    private fun maybeUpdateContinueListeningSnapshot(epoch: Long) {
        if (epoch != homeSnapshotEpoch || homeContinueLocked) return
        val snapshot = mutable.value
        val allDone = allStatsReady && recentStatsReady && favoritesReadyForContinue && continueSearchReady
        if (!allDone) return
        val mixed = buildContinueListeningMix(
            recentStats = recentPlayStats?.songs.orEmpty(),
            allTimeStats = snapshot.playStats?.songs.orEmpty(),
            favorites = snapshot.favoriteSongs,
            recentSearchSongs = continueSearchSongs,
            recentDayStats = recentDayPlayStats?.songs.orEmpty(),
        )
        if (mixed.isNotEmpty() || allDone) {
            mutable.value = snapshot.copy(continueListeningSongs = mixed)
        }
        if (allDone) homeContinueLocked = true
    }

    override fun refresh() {
        mutable.value.selectedArtist?.let { openArtist(it); return }
        mutable.value.selectedPlaylist?.let { openPlaylist(it); return }
        mutable.value.selectedAlbum?.let { openAlbum(it); return }
        beginHomeSnapshotRefresh(unlockHistoryPreview = true)
        searchHistorySnapshotLocked = false
        updateSearchHistory(SearchSnapshotMode.LOAD_AND_LOCK) { store, key -> store.load(key) }
        refreshContinueSearchSongs()
        refreshCatalogAndPlaylists()
        refreshPlayStats()
        refreshRecentPlayStats()
    }

    private fun refreshCatalogAndPlaylists() {
        mutable.value.selectedArtist?.let { openArtist(it); return }
        mutable.value.selectedPlaylist?.let { openPlaylist(it); return }
        mutable.value.selectedAlbum?.let { openAlbum(it); return }
        if (mutable.value.tab == LibraryTab.ALBUMS) loadAlbums(true)
        else {
            load(true)
            if (mutable.value.query.isBlank() &&
                mutable.value.tab != LibraryTab.FAVORITES &&
                (mutable.value.destination == MainDestination.HOME || mutable.value.homeLibrarySongs.isEmpty())
            ) {
                loadHomeLibrarySongs()
            }
            if (mutable.value.destination == MainDestination.HOME || mutable.value.destination == MainDestination.DISCOVER) loadAlbums(true)
            if (mutable.value.destination == MainDestination.DISCOVER) loadArtists(true)
        }
        refreshPlaylists()
    }

    override fun selectDiscoverLanguage(language: String) {
        if (language !in languages || (
                language == mutable.value.discoverLanguage &&
                    mutable.value.songLanguage == language &&
                    mutable.value.albumLanguage == language
                )
        ) return
        requestRevision++; requestJob?.cancel(); offset = 0
        albumRevision++; albumJob?.cancel(); albumOffset = 0
        artistsRevision++; artistsJob?.cancel(); artistsOffset = 0
        mutable.value = mutable.value.copy(
            discoverLanguage = language,
            songLanguage = language,
            albumLanguage = language,
            songs = emptyList(), hasMore = false, loading = false, loadingMore = false, notice = null,
            albums = emptyList(), albumsHasMore = false, albumsLoading = false, albumsLoadingMore = false, albumsNotice = null,
            artists = emptyList(), artistsHasMore = false, artistsLoading = false, artistsLoadingMore = false, artistsNotice = null,
        )
        load(true)
        loadAlbums(true)
        loadArtists(true)
    }

    override fun selectSongLanguage(language: String) {
        if (language !in languages || language == mutable.value.songLanguage) return
        requestRevision++; requestJob?.cancel(); offset = 0
        mutable.value = mutable.value.copy(songLanguage = language, songs = emptyList(), hasMore = false,
            loading = false, loadingMore = false)
        load(true)
    }

    override fun selectAlbumLanguage(language: String) {
        if (language !in languages || language == mutable.value.albumLanguage) return
        albumRevision++; albumJob?.cancel(); albumOffset = 0
        mutable.value = mutable.value.copy(albumLanguage = language, albums = emptyList(), albumsHasMore = false,
            albumsLoading = false, albumsLoadingMore = false)
        loadAlbums(true)
    }

    override fun loadMoreAlbums() {
        if (mutable.value.albumsHasMore && !mutable.value.albumsLoading && !mutable.value.albumsLoadingMore) loadAlbums(false)
    }

    private fun loadAlbums(reset: Boolean) {
        val connection = current() ?: return
        val ticket = identity
        val revision = ++albumRevision
        val requestedOffset = if (reset) 0 else albumOffset
        val language = mutable.value.albumLanguage
        albumJob?.cancel()
        mutable.value = mutable.value.copy(albumsLoading = reset, albumsLoadingMore = !reset, albumsNotice = null)
        albumJob = viewModelScope.launch {
            try {
                val page = withContext(ioDispatcher) { connection.api.albums(requestedOffset, language) }
                if (!valid(ticket, connection) || revision != albumRevision) return@launch
                albumOffset = page.nextOffset
                mutable.value = mutable.value.copy(
                    albums = (if (reset) page.albums else mutable.value.albums + page.albums).distinctBy { it.id },
                    albumsLoading = false, albumsLoadingMore = false, albumsHasMore = page.hasMore,
                    albumsNotice = if (page.capped) ClientNotice.PAGE_LIMIT else null)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == albumRevision)
                    mutable.value = mutable.value.copy(albumsLoading = false, albumsLoadingMore = false, albumsNotice = notice(error))
            }
        }
    }

    override fun openAlbum(album: Album) {
        val connection = current() ?: return
        if (!validYouTubeAlbumReference(album.id) && !validLocalAlbumReference(album.id)) return
        val canonical = (mutable.value.albums + mutable.value.searchAlbums + mutable.value.artistAlbums).find { it.id == album.id }
            ?: mutable.value.selectedAlbum?.takeIf { it.id == album.id } ?: album
        closePlaylist()
        closeArtist()
        val ticket = identity
        val revision = ++detailRevision
        detailJob?.cancel()
        mutable.value = mutable.value.copy(selectedAlbum = canonical, albumSongs = emptyList(), albumLoading = true, albumNotice = null)
        detailJob = viewModelScope.launch {
            try {
                val detail = withContext(ioDispatcher) { connection.api.album(canonical.id) }
                if (!valid(ticket, connection) || revision != detailRevision) return@launch
                if (detail.album.id != canonical.id) throw ApiFailure(Failure.INVALID_RESPONSE)
                mutable.value = mutable.value.copy(selectedAlbum = detail.album, albumSongs = detail.songs, albumLoading = false)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == detailRevision)
                    mutable.value = mutable.value.copy(albumLoading = false, albumNotice = notice(error))
            }
        }
    }

    override fun closeAlbum() {
        detailRevision++; detailJob?.cancel()
        mutable.value = mutable.value.copy(selectedAlbum = null, albumSongs = emptyList(), albumLoading = false, albumNotice = null)
    }

    private fun loadHomeLibrarySongs() {
        val connection = current() ?: return
        val ticket = identity
        val revision = ++homeLibraryRevision
        homeLibraryJob?.cancel()
        mutable.value = mutable.value.copy(homeLibraryLoading = true)
        homeLibraryJob = viewModelScope.launch {
            try {
                val mixed = withContext(ioDispatcher) {
                    val counts = runCatching { connection.api.languageCounts(includeUntagged = true) }.getOrNull()
                    if (counts.isNullOrEmpty()) {
                        connection.api.songs("", 0, "all", "desc").songs.take(HOME_LIBRARY_LIMIT)
                    } else {
                        val quotas = allocateHomeLibraryQuotas(counts, HOME_LIBRARY_LIMIT)
                        val fetched = coroutineScope {
                            quotas.keys.associateWith { lang ->
                                async {
                                    runCatching { connection.api.songs("", 0, lang, "desc").songs }.getOrNull()
                                }
                            }.mapValues { (_, deferred) -> deferred.await() }
                        }
                        val tracksByLang = fetched.mapNotNull { (lang, list) ->
                            list?.let { lang to it }
                        }.toMap()
                        val anyFailed = fetched.values.any { it == null }
                        val targetCount = minOf(HOME_LIBRARY_LIMIT, counts.values.filter { it > 0 }.sum())
                        val pre = interleaveProportionalTracks(tracksByLang, quotas, limit = HOME_LIBRARY_LIMIT)
                        val fallback = if (anyFailed || pre.size < targetCount) {
                            runCatching { connection.api.songs("", 0, "all", "desc").songs }.getOrDefault(emptyList())
                        } else emptyList()
                        if (fallback.isEmpty()) pre
                        else interleaveProportionalTracks(tracksByLang, quotas, fallbackSongs = fallback, limit = HOME_LIBRARY_LIMIT)
                    }
                }
                if (!valid(ticket, connection) || revision != homeLibraryRevision) return@launch
                mutable.value = mutable.value.copy(homeLibrarySongs = mixed, homeLibraryLoading = false)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == homeLibraryRevision) {
                    mutable.value = mutable.value.copy(homeLibraryLoading = false)
                }
            }
        }
    }

    override fun loadMore() {
        if (mutable.value.hasMore && !mutable.value.loadingMore && !mutable.value.loading) load(false)
    }

    private fun load(reset: Boolean) {
        val connection = current() ?: return
        val ticket = identity
        val snapshot = mutable.value
        val query = snapshot.query
        val tab = snapshot.tab
        val language = if (snapshot.destination == MainDestination.SEARCH && tab == LibraryTab.CATALOG) "all" else snapshot.songLanguage
        val requestedOffset = if (reset) 0 else offset
        val revision = ++requestRevision
        requestJob?.cancel()
        mutable.value = snapshot.copy(loading = reset, loadingMore = !reset, notice = null)
        requestJob = viewModelScope.launch {
            try {
                val page = withContext(ioDispatcher) {
                    if (tab == LibraryTab.FAVORITES) {
                        val songs = connection.api.favorites().filter {
                            (language == "all" || it.language == language) &&
                                (query.isBlank() || listOf(it.title, it.artist, it.album).any { value -> value.contains(query, true) })
                        }
                        SongPage(songs, 0, false)
                    } else connection.api.songs(query, requestedOffset, language, snapshot.songSort)
                }
                if (!valid(ticket, connection) || revision != requestRevision) return@launch
                offset = page.nextOffset
                mutable.value = mutable.value.copy(
                    songs = (if (reset) page.songs else mutable.value.songs + page.songs).distinctBy { it.id },
                    loading = false, loadingMore = false, hasMore = page.hasMore,
                    notice = when { page.capped -> ClientNotice.PAGE_LIMIT
                        page.partialSources -> ClientNotice.SOME_SOURCES_UNAVAILABLE
                        else -> null })
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == requestRevision)
                    mutable.value = mutable.value.copy(loading = false, loadingMore = false, notice = notice(error))
            }
        }
    }

    override fun refreshPlaylists() {
        val connection = current() ?: return
        val ticket = identity
        val revision = ++playlistsRevision
        playlistsJob?.cancel()
        mutable.value = mutable.value.copy(playlistsLoading = true, playlistsNotice = null)
        playlistsJob = viewModelScope.launch {
            try {
                val (items, shelf) = withContext(ioDispatcher) {
                    val rows = connection.api.playlists()
                    val order = try { connection.api.playlistShelf() } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        null
                    }
                    rows to order
                }
                if (!valid(ticket, connection) || revision != playlistsRevision) return@launch
                val retainedItems = retainFavoriteSummary(items)
                val selected = mutable.value.selectedPlaylist
                val updated = selected?.let { item -> retainedItems.find { it.id == item.id } }?.let { item ->
                    // A summary never supplies the complete editable membership at its new revision.
                    if (item.kind == "favorite" && knownFavoriteDetail?.playlist?.revision != item.revision && selected?.kind == "favorite") selected else item
                }
                if (selected != null && updated == null) closePlaylist()
                playlistsLoadedOnce = true
                mutable.value = mutable.value.copy(playlists = retainedItems, playlistShelf = shelf ?: mutable.value.playlistShelf,
                    playlistsLoading = false, selectedPlaylist = updated, playlistsNotice = null)
                favoritesLoadedOnce = true
                refreshFavorites()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == playlistsRevision) {
                    mutable.value = mutable.value.copy(playlistsLoading = false, playlistsNotice = notice(error))
                    refreshFavorites()
                }
            }
        }
    }

    override fun openPlaylist(playlist: PlaylistSummary) {
        val connection = current() ?: return
        val canonical = mutable.value.playlists.find { it.id == playlist.id }
            ?: mutable.value.selectedPlaylist?.takeIf { it.id == playlist.id } ?: return
        closeAlbum()
        closeArtist()
        closePlaylistSongPicker()
        val ticket = identity
        val revision = ++playlistRevision
        playlistJob?.cancel()
        val samePlaylist = mutable.value.selectedPlaylist?.id == canonical.id
        mutable.value = mutable.value.copy(selectedPlaylist = canonical, playlistSongs = emptyList(), selectedPlaylistEntries = emptyList(),
            playlistSongSort = if (samePlaylist) mutable.value.playlistSongSort else PlaylistSongSortMode.DEFAULT,
            playlistLoading = true, playlistNotice = null)
        playlistJob = viewModelScope.launch {
            try {
                val detail = withContext(ioDispatcher) { connection.api.playlist(canonical.id) }
                if (!valid(ticket, connection) || revision != playlistRevision ||
                    mutable.value.selectedPlaylist?.id != canonical.id) return@launch
                publishPlaylist(detail)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == playlistRevision)
                    mutable.value = mutable.value.copy(playlistLoading = false, playlistNotice = notice(error))
            }
        }
    }

    override fun closePlaylist() {
        playlistRevision++; playlistJob?.cancel()
        closePlaylistSongPicker()
        mutable.value = mutable.value.copy(selectedPlaylist = null, playlistSongs = emptyList(), selectedPlaylistEntries = emptyList(),
            playlistSongSort = PlaylistSongSortMode.DEFAULT, playlistLoading = false, playlistNotice = null)
    }

    override fun selectPlaylistSort(mode: PlaylistSortMode) {
        val connection = current() ?: return
        if (mutable.value.playlistSortMode == mode) return
        mutable.value = mutable.value.copy(playlistSortMode = mode)
        val store = playlistPreferences ?: return
        val accountIdentity = HistoryIdentity(connection.network.address.origin, connection.auth.account.id)
        viewModelScope.launch(ioDispatcher) {
            runCatching { store.writeSortMode(accountIdentity, mode) }
        }
    }

    override fun selectPlaylistSongSort(mode: PlaylistSongSortMode) {
        if (mutable.value.playlistSongSort == mode) return
        mutable.value = mutable.value.copy(playlistSongSort = mode)
    }

    override fun refreshPlaylist() { mutable.value.selectedPlaylist?.let(::openPlaylist) }
    override fun refreshFavorites() { loadFavorites(false) }
    override fun playFavorites() { loadFavorites(true) }

    private fun rememberFavoriteSummary(summary: PlaylistSummary): Boolean {
        if (current() !== favoriteOwner || favoriteOwner == null || summary.kind != "favorite") return false
        val known = knownFavoriteSummary
        if (known != null && (summary.id != known.id || summary.revision < known.revision)) return false
        knownFavoriteSummary = summary
        return true
    }

    private fun currentFavoriteDetail(): PlaylistDetail? = knownFavoriteDetail?.takeIf {
        it.playlist.id == knownFavoriteSummary?.id && it.playlist.revision >= (knownFavoriteSummary?.revision ?: -1L)
    }

    private fun retainFavoriteSummary(items: List<PlaylistSummary>): List<PlaylistSummary> {
        items.firstOrNull { it.kind == "favorite" }?.let(::rememberFavoriteSummary)
        val known = knownFavoriteSummary ?: return items
        val result = items.mapNotNull { item -> when {
            item.id == known.id -> known
            item.kind == "favorite" -> null
            else -> item
        } }.toMutableList()
        if (result.none { it.id == known.id }) {
            val previous = mutable.value.playlists.indexOfFirst { it.id == known.id }.coerceAtLeast(0)
            result.add(previous.coerceAtMost(result.size), known)
        }
        return result
    }

    /** One monotonic publication gate for GETs, writes and system acknowledgements. */
    private fun publishFavorites(detail: PlaylistDetail?, notifySystem: Boolean = true): PlaylistDetail? {
        val accepted = detail != null && rememberFavoriteSummary(detail.playlist)
        if (!accepted && knownFavoriteSummary != null) {
            val retained = currentFavoriteDetail()
            val snapshot = mutable.value
            val selectedFavorite = snapshot.selectedPlaylist?.id == knownFavoriteSummary?.id
            mutable.value = snapshot.copy(favoritesLoading = false,
                favoritesNotice = if (retained == null) ClientNotice.REQUEST_FAILED else null,
                selectedPlaylist = if (selectedFavorite && retained != null) retained.playlist else snapshot.selectedPlaylist,
                selectedPlaylistEntries = if (selectedFavorite && retained != null) retained.entries else snapshot.selectedPlaylistEntries,
                playlistSongs = if (selectedFavorite && retained != null) retained.songs else snapshot.playlistSongs,
                playlistLoading = if (selectedFavorite) false else snapshot.playlistLoading,
                playlistNotice = if (selectedFavorite) { if (retained == null) ClientNotice.REQUEST_FAILED else null } else snapshot.playlistNotice)
            return retained
        }
        if (detail != null && !accepted) return null
        if (detail != null) { knownFavoriteDetail = detail; mergePlaylist(detail.playlist) }
        val latest = detail?.entries?.maxWithOrNull(compareBy<PlaylistSong> { it.addedAt }.thenBy { it.sortOrder })
        val snapshot = mutable.value
        val selectedFavorite = detail != null && snapshot.selectedPlaylist?.id == detail.playlist.id
        mutable.value = snapshot.copy(favoriteSongs = detail?.songs.orEmpty(),
            favoriteSongIds = detail?.entries?.map { it.song.id }?.toSet().orEmpty(),
            latestFavoriteCoverUrl = latest?.song?.coverUrl, favoritesLoading = false, favoritesNotice = null,
            selectedPlaylist = if (selectedFavorite) detail!!.playlist else snapshot.selectedPlaylist,
            selectedPlaylistEntries = if (selectedFavorite) detail!!.entries else snapshot.selectedPlaylistEntries,
            playlistSongs = if (selectedFavorite) detail!!.songs else snapshot.playlistSongs,
            playlistLoading = if (selectedFavorite) false else snapshot.playlistLoading,
            playlistNotice = if (selectedFavorite) null else snapshot.playlistNotice)
        if (notifySystem && detail != null) current()?.let { systemMediaFavorites?.publish(it, detail) }
        favoritesLoadedOnce = true
        favoritesReadyForContinue = true
        maybeUpdateContinueListeningSnapshot(homeSnapshotEpoch)
        return detail
    }

    private suspend fun favoriteForPlayback(connection: ActiveConnection, detail: PlaylistDetail?,
        ticket: Long, revision: Long): PlaylistDetail? {
        var complete = publishFavorites(detail)
        if (complete == null && knownFavoriteSummary != null) {
            val retry = withContext(ioDispatcher) { readFavorites(connection, knownFavoriteSummary) }
            if (!valid(ticket, connection) || revision != playRevision) return null
            complete = publishFavorites(retry)
            if (complete == null) throw ApiFailure(Failure.REQUEST)
        }
        return complete
    }

    private fun loadFavorites(playAfter: Boolean) {
        val connection = current() ?: return
        val ticket = identity
        val known = mutable.value.playlists.find { it.kind == "favorite" }
        mutable.value = mutable.value.copy(favoritesLoading = true, favoritesNotice = null)
        if (playAfter) {
            val revision = beginPlaybackCommand()
            playJob?.cancel()
            playJob = viewModelScope.launch {
                try {
                    val detail = withContext(ioDispatcher) { readFavorites(connection, known) }
                    if (!valid(ticket, connection) || revision != playRevision) return@launch
                    val complete = favoriteForPlayback(connection, detail, ticket, revision)
                    if (!complete?.songs.isNullOrEmpty()) {
                        connectPlayer(connection)
                        if (valid(ticket, connection) && revision == playRevision) {
                            val latest = currentFavoriteDetail() ?: throw ApiFailure(Failure.REQUEST)
                            if (latest.songs.isNotEmpty()) {
                                captureQueueSnapshotBeforeReplacement(latest.songs)
                                recordPlaylistCollection(latest.playlist, latest.songs)
                                activeQueueOriginType = "favorites"
                                activeQueueOriginLabel = latest.playlist.name
                                player.play(latest.songs, 0)
                            }
                        }
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    if (valid(ticket, connection) && revision == playRevision)
                        mutable.value = mutable.value.copy(favoritesLoading = false, favoritesNotice = notice(error))
                }
            }
            return
        }
        val revision = ++favoritesRevision
        favoritesJob?.cancel()
        favoritesJob = viewModelScope.launch {
            try {
                val detail = withContext(ioDispatcher) { readFavorites(connection, known) }
                if (!valid(ticket, connection) || revision != favoritesRevision) return@launch
                publishFavorites(detail)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == favoritesRevision) {
                    mutable.value = mutable.value.copy(favoritesLoading = false, favoritesNotice = notice(error))
                    favoritesReadyForContinue = true
                    maybeUpdateContinueListeningSnapshot(homeSnapshotEpoch)
                }
            }
        }
    }

    private fun readFavorites(connection: ActiveConnection, known: PlaylistSummary?): PlaylistDetail? {
        val favorite = known ?: connection.api.playlists().find { it.kind == "favorite" }
        return favorite?.let { connection.api.playlist(it.id) }
    }

    private fun beginPlaybackCommand(): Long {
        homeHistoryPreviewLocked = true
        queueRestoreRevision++
        queueRestoreJob?.cancel()
        mutable.value = mutable.value.copy(queueRestoring = false)
        return ++playRevision
    }
    private fun renderHistory() {
        val snapshot = mutable.value
        val live = (snapshot.playback.queue + snapshot.songs + snapshot.favoriteSongs +
            snapshot.albumSongs + snapshot.playlistSongs).associateBy { it.id }
        val rendered = historyEntries.map { entry ->
            val stored = entry.toSong()
            (historySongs[entry.id] ?: live[entry.id])?.let { songContext(it, stored, preserveCover = false) } ?: stored
        }
        val preview = if (!homeHistoryPreviewLocked && snapshot.homeHistoryPreviewSong == null) {
            rendered.firstOrNull()
        } else {
            snapshot.homeHistoryPreviewSong
        }
        mutable.value = snapshot.copy(
            playHistoryPlayedAt = historyEntries.associate { it.id to it.playedAt },
            playHistory = rendered,
            homeHistoryPreviewSong = preview,
        )
        if (historyExtras != null) renderHistoryExtras()
    }

    private fun renderHistoryExtras() {
        val snapshot = mutable.value
        val allSongs = snapshot.playback.queue + snapshot.songs + snapshot.favoriteSongs +
            snapshot.albumSongs + snapshot.playlistSongs + snapshot.playHistory + snapshot.discoverySongs +
            snapshot.continueListeningSongs + snapshot.artistSongs
        val songsById = allSongs.associateBy { it.id }
        val artistNameCovers = buildMap {
            snapshot.artists.forEach { a -> a.coverUrl?.takeIf(String::isNotBlank)?.let { put(a.name.lowercase(), it) } }
            snapshot.spotlightArtist?.let { s ->
                (s.photoUrl ?: s.coverUrl)?.takeIf(String::isNotBlank)?.let { put(s.artist.lowercase(), it) }
            }
        }
        val artistIdCovers = buildMap {
            snapshot.artists.forEach { a -> if (!a.id.isNullOrBlank() && !a.coverUrl.isNullOrBlank()) put(a.id, a.coverUrl) }
        }
        val visitedArtists = historyExtrasSnapshot.artists.map { entry ->
            val key = entry.canonicalKey()
            val cover = artistCoverCache[key]
                ?: entry.id?.let { artistIdCovers[it] }
                ?: artistNameCovers[entry.name.lowercase()]
                ?: allSongs.firstOrNull { song ->
                    !song.coverUrl.isNullOrBlank() &&
                        ((entry.id != null && (song.artistId == entry.id || song.artists.any { it.id == entry.id })) ||
                            song.artist.equals(entry.name, ignoreCase = true))
                }?.coverUrl
            VisitedArtist(
                id = entry.id,
                name = entry.name,
                source = entry.source,
                visitedAtMs = entry.visitedAtMs,
                coverUrl = cover,
            )
        }
        val albumLookup = (snapshot.albums + snapshot.searchAlbums + snapshot.artistAlbums).associateBy { it.id }
        val playlistLookup = snapshot.playlists.associateBy { it.id }
        val playedCollections = historyExtrasSnapshot.collections.map { entry ->
            val key = entry.canonicalKey()
            when (entry.type) {
                PlayedCollectionType.ALBUM -> {
                    val liveAlbum = albumLookup[entry.id]
                    val cover = collectionCoverCache[key]
                        ?: liveAlbum?.coverUrl
                        ?: allSongs.firstOrNull { !it.coverUrl.isNullOrBlank() && (it.albumId == entry.id || it.album == entry.title) }?.coverUrl
                    PlayedCollection(
                        type = entry.type,
                        id = entry.id,
                        title = entry.title,
                        subtitle = entry.subtitle.ifBlank { liveAlbum?.artist.orEmpty() },
                        trackCount = if (entry.trackCount > 0) entry.trackCount else (liveAlbum?.knownTrackCount ?: 0),
                        source = entry.source,
                        kind = entry.kind,
                        playedAtMs = entry.playedAtMs,
                        coverUrl = cover,
                    )
                }
                PlayedCollectionType.PLAYLIST -> {
                    val livePlaylist = playlistLookup[entry.id]
                    val previews = collectionPreviewCoversCache[key]
                        ?: livePlaylist?.previewCovers.orEmpty()
                    val cover = collectionCoverCache[key]
                        ?: livePlaylist?.customCoverUrl
                        ?: if (entry.kind == "favorite") snapshot.latestFavoriteCoverUrl ?: snapshot.favoriteSongs.firstOrNull { !it.coverUrl.isNullOrBlank() }?.coverUrl else null
                    PlayedCollection(
                        type = entry.type,
                        id = entry.id,
                        title = entry.title,
                        subtitle = entry.subtitle.ifBlank { livePlaylist?.description.orEmpty() },
                        trackCount = if (entry.trackCount > 0) entry.trackCount else (livePlaylist?.songCount ?: 0),
                        source = entry.source,
                        kind = entry.kind,
                        playedAtMs = entry.playedAtMs,
                        coverUrl = cover,
                        previewCovers = previews,
                    )
                }
            }
        }
        val queueSnapshots = historyExtrasSnapshot.queueSnapshots.map { entry ->
            val resolvedSongs = entry.entries.map { item ->
                val stored = item.toSong()
                val rich = snapshotSongCache[item.id] ?: historySongs[item.id] ?: songsById[item.id]
                if (rich != null) songContext(rich, stored, preserveCover = false) else stored
            }
            QueueSnapshot(
                id = entry.id,
                capturedAtMs = entry.capturedAtMs,
                sourceLabel = entry.sourceLabel,
                sourceType = entry.sourceType,
                songs = resolvedSongs,
                currentIndex = entry.currentIndex.coerceIn(0, (resolvedSongs.size - 1).coerceAtLeast(0)),
            )
        }
        mutable.value = mutable.value.copy(
            visitedArtists = visitedArtists,
            playedCollections = playedCollections,
            queueSnapshots = queueSnapshots,
        )
    }

    private fun hydrateHistory() {
        val connection = current() ?: return
        val ids = historyEntries.map { it.id }.filter { it !in historyResolved }
        if (ids.isEmpty()) return
        val ticket = identity
        historyResolved.addAll(ids)
        // Reads are bounded to the store's 200 entries and reuse one resolve request.
        viewModelScope.launch {
            try {
                val contexts = historyEntries.map { it.toSong() }.associateBy { it.id }
                val songs = resolveStoredSongs(connection, ids, contexts)
                if (!valid(ticket, connection)) return@launch
                songs.forEach { historySongs[it.id] = it }
                renderHistory()
                if (!homeHistoryPreviewLocked && mutable.value.playHistory.isNotEmpty()) {
                    mutable.value = mutable.value.copy(homeHistoryPreviewSong = mutable.value.playHistory.firstOrNull())
                    homeHistoryPreviewLocked = true
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection)) {
                    historyResolved.removeAll(ids.toSet())
                    mutable.value = mutable.value.copy(historyNotice = notice(error))
                }
            }
        }
    }

    override fun playSong(song: Song) {
        if (current() == null) return
        val snapshot = mutable.value
        val groups = listOf(snapshot.playlistSongs, snapshot.albumSongs, snapshot.artistSongs,
            snapshot.continueListeningSongs, snapshot.discoverySongs, snapshot.spotlightArtist?.songs.orEmpty(),
            snapshot.playStats?.songs.orEmpty().map { it.song }, snapshot.songs, snapshot.favoriteSongs)
        // A row's current-session list and album edition are stronger evidence than a same-ID cache.
        val songs = groups.firstOrNull { group -> group.any { it === song } }
            ?: groups.firstOrNull { group -> group.any { sameSongContext(it, song) } }
            ?: groups.firstOrNull { group -> group.any { it.id == song.id } }
        if (songs == null) {
            if (snapshot.playHistory.any { it.id == song.id }) playHistorySong(song)
            return
        }
        val index = songs.indexOfFirst { it === song }.takeIf { it >= 0 }
            ?: songs.indexOfFirst { sameSongContext(it, song) }.takeIf { it >= 0 }
            ?: songs.indexOfFirst { it.id == song.id }
        if (index >= 0) playSongs(songs, index)
    }

    private var secondaryOverlayActive = false
    private var playbackFeedbackNonce = 0L

    override fun setSecondaryOverlayActive(active: Boolean) {
        secondaryOverlayActive = active
    }

    override fun dismissPlaybackFeedback(eventId: Long) {
        if (mutable.value.playbackFeedback?.id == eventId) {
            mutable.value = mutable.value.copy(playbackFeedback = null)
        }
    }

    private fun emitPlaybackFeedback(kind: PlaybackFeedbackKind, omittedCount: Int = 0) {
        mutable.value = mutable.value.copy(
            playbackFeedback = PlaybackFeedbackEvent(++playbackFeedbackNonce, kind, omittedCount),
        )
    }

    override fun playHistorySong(song: Song) {
        val connection = current() ?: return
        val entries = historyEntries.toList()
        val clickedIndex = entries.indexOfFirst { it.id == song.id }.takeIf { it >= 0 } ?: return
        val stored = entries[clickedIndex].toSong()
        val ticket = identity
        val revision = beginPlaybackCommand()
        playJob?.cancel()
        playJob = viewModelScope.launch {
            try {
                // Stored history never supplies an authenticated or temporary playback URL.
                val received = withContext(ioDispatcher) { connection.api.song(stored.id) }
                if (!valid(ticket, connection) || revision != playRevision) return@launch
                if (received.id != stored.id) throw ApiFailure(Failure.INVALID_RESPONSE)
                val freshClicked = songContext(received, stored, preserveCover = false)
                if (!freshClicked.canPlay) {
                    mutable.value = mutable.value.copy(historyNotice = ClientNotice.PLAYBACK_FAILED)
                    emitPlaybackFeedback(PlaybackFeedbackKind.TRACK_UNAVAILABLE)
                    return@launch
                }
                connectPlayer(connection)
                if (!valid(ticket, connection) || revision != playRevision) return@launch
                historySongs[freshClicked.id] = freshClicked
                renderHistory()
                val started = player.playSingleAsNext(freshClicked)
                if (started) {
                    emitPlaybackFeedback(PlaybackFeedbackKind.PLAYED_SINGLE_NEXT)
                } else {
                    mutable.value = mutable.value.copy(historyNotice = ClientNotice.PLAYBACK_FAILED)
                    emitPlaybackFeedback(PlaybackFeedbackKind.TRACK_UNAVAILABLE)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == playRevision)
                    mutable.value = mutable.value.copy(historyNotice = notice(error))
            }
        }
    }

    private suspend fun resolveStoredSongs(connection: ActiveConnection, ids: List<String>,
        contexts: Map<String, Song> = emptyMap()): List<Song> =
        withContext(ioDispatcher) {
            val eligible = ids.distinct().filter { id ->
                id.isNotBlank() && id.length <= 256 && id == id.trim() && id !in setOf(".", "..") &&
                    id.none { it.code < 32 || it.code == 127 }
            }
            val shortIds = eligible.filter { it.length <= 80 }
            val resolved = shortIds.chunked(500).flatMap { connection.api.resolveSongs(it) }
            val semaphore = Semaphore(4)
            val longer = coroutineScope {
                eligible.filter { it.length > 80 }.map { id ->
                    async {
                        semaphore.withPermit {
                            try { connection.api.song(id) } catch (error: ApiFailure) {
                                if (error.httpStatus == 404) null else throw error
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            val byId = (resolved + longer).associateBy { it.id }
            eligible.mapNotNull { id -> byId[id]?.takeIf { it.canPlay }?.let { fresh ->
                contexts[id]?.let { songContext(fresh, it, preserveCover = false) } ?: fresh
            } }
        }

    private fun updateQueueNotice(repository: PlaybackQueueRepository) {
        // A successful disk save clears only its own error, retaining partial-restore warnings.
        mutable.value = mutable.value.copy(queueNotice = repository.notice.value ?: queueRestoreNotice)
    }

    private fun observeQueueNotice(repository: PlaybackQueueRepository, connection: ActiveConnection, ticket: Long) {
        queueNoticeJob?.cancel()
        if (valid(ticket, connection)) updateQueueNotice(repository)
        queueNoticeJob = viewModelScope.launch {
            repository.notice.collect {
                if (valid(ticket, connection)) updateQueueNotice(repository)
            }
        }
    }

    override fun retryQueueRestore() {
        val connection = current() ?: return
        queueRepository?.reload()
        if (connection.network.isActive) restoreSavedQueue()
    }

    private fun restoreSavedQueue() {
        val repository = queueRepository ?: return
        val connection = current() ?: return
        if (player.state.value.queue.isNotEmpty()) return
        val ticket = identity
        val revision = ++queueRestoreRevision
        val playTicket = playRevision
        queueRestoreJob?.cancel()
        queueRestoreNotice = null
        mutable.value = mutable.value.copy(queueRestoring = true)
        updateQueueNotice(repository)
        queueRestoreJob = viewModelScope.launch {
            try {
                repository.loaded.first { it }
                if (!valid(ticket, connection) || revision != queueRestoreRevision ||
                    playTicket != playRevision || player.state.value.queue.isNotEmpty()) return@launch
                repository.notice.value?.let {
                    updateQueueNotice(repository)
                    return@launch
                }
                val saved = repository.state.value ?: return@launch
                val songs = resolveStoredSongs(connection, saved.entries.map { it.id }).associateBy { it.id }
                if (!valid(ticket, connection) || revision != queueRestoreRevision ||
                    playTicket != playRevision || player.state.value.queue.isNotEmpty()) return@launch
                val indexed = saved.entries.mapIndexedNotNull { index, entry -> songs[entry.id]?.let {
                    index to songContext(it, entry.toSong(), preserveCover = false)
                } }
                if (indexed.isEmpty()) {
                    queueRestoreNotice = ClientNotice.REQUEST_FAILED
                    updateQueueNotice(repository)
                    return@launch
                }
                val target = indexed.indexOfFirst { it.first >= saved.currentIndex }.takeIf { it >= 0 } ?: indexed.lastIndex
                val sameCurrent = indexed[target].first == saved.currentIndex
                val durationMs = (indexed[target].second.durationSeconds * 1000).toLong()
                val position = if (!sameCurrent) 0L else if (durationMs > 0) saved.positionMs.coerceIn(0L, durationMs) else saved.positionMs
                connectPlayer(connection)
                if (!valid(ticket, connection) || revision != queueRestoreRevision ||
                    playTicket != playRevision || player.state.value.queue.isNotEmpty()) return@launch
                player.restoreQueue(indexed.map { it.second }, target, position, saved.shuffle, saved.repeatMode)
                if (indexed.size != saved.entries.size) {
                    queueRestoreNotice = ClientNotice.REQUEST_FAILED
                    updateQueueNotice(repository)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == queueRestoreRevision) {
                    queueRestoreNotice = notice(error)
                    updateQueueNotice(repository)
                }
            } finally {
                if (valid(ticket, connection) && revision == queueRestoreRevision)
                    mutable.value = mutable.value.copy(queueRestoring = false, playback = player.state.value)
            }
        }
    }





    private suspend fun connectPlayer(connection: ActiveConnection) {
        player.connect(connection)
        if (connection.network.isActive && connections.current === connection) player.setVolume(preferredVolume)
    }

    private fun listeningIdentity(connection: ActiveConnection) =
        HistoryIdentity(connection.network.address.origin, connection.auth.account.id)

    private fun updateSearchHistory(
        mode: SearchSnapshotMode = SearchSnapshotMode.LOAD_AND_LOCK,
        visibleTransform: ((ClientUiState) -> ClientUiState)? = null,
        work: (SearchHistoryStore, HistoryIdentity) -> SearchHistorySnapshot,
    ) {
        val store = searchHistoryStore ?: return
        val connection = current() ?: return
        val ticket = identity
        val key = listeningIdentity(connection)
        viewModelScope.launch {
            try {
                val snapshot = withContext(ioDispatcher) {
                    searchMutex.withLock {
                        if (!valid(ticket, connection)) return@withLock null
                        work(store, key)
                    }
                } ?: return@launch
                if (!valid(ticket, connection)) return@launch
                val currentState = mutable.value
                when (mode) {
                    SearchSnapshotMode.LOAD_AND_LOCK -> {
                        mutable.value = currentState.copy(
                            recentSearches = snapshot.entities,
                            recentSearchTerms = snapshot.terms,
                            searchHistoryNotice = null,
                        )
                        searchHistorySnapshotLocked = true
                    }
                    SearchSnapshotMode.RECORD_ONLY -> {
                        if (!searchHistorySnapshotLocked) {
                            mutable.value = currentState.copy(
                                recentSearches = snapshot.entities,
                                recentSearchTerms = snapshot.terms,
                                searchHistoryNotice = null,
                            )
                        } else {
                            mutable.value = currentState.copy(searchHistoryNotice = null)
                        }
                    }
                    SearchSnapshotMode.APPLY_TO_VISIBLE -> {
                        val updated = if (searchHistorySnapshotLocked && visibleTransform != null) {
                            visibleTransform(currentState).copy(searchHistoryNotice = null)
                        } else {
                            currentState.copy(
                                recentSearches = snapshot.entities,
                                recentSearchTerms = snapshot.terms,
                                searchHistoryNotice = null,
                            )
                        }
                        mutable.value = updated
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection)) mutable.value = mutable.value.copy(searchHistoryNotice = notice(error))
            }
        }
    }
    private fun isSearchRootActive(ui: ClientUiState = mutable.value): Boolean =
        ui.destination == MainDestination.SEARCH &&
            !secondaryOverlayActive &&
            ui.selectedArtist == null &&
            ui.selectedAlbum == null &&
            ui.selectedPlaylist == null

    private fun rememberSearchEntity(entry: SearchEntity, allowed: Boolean = isSearchRootActive()) {
        if (allowed) {
            updateSearchHistory(SearchSnapshotMode.RECORD_ONLY) { store, key -> store.addEntity(key, entry) }
        }
    }
    override fun removeRecentSearch(entry: SearchEntity) = updateSearchHistory(
        mode = SearchSnapshotMode.APPLY_TO_VISIBLE,
        visibleTransform = { s -> s.copy(recentSearches = s.recentSearches.filterNot { com.flaretune.client.data.sameSearchEntity(it, entry) }) },
    ) { store, key -> store.removeEntity(key, entry) }
    override fun clearRecentSearches() = updateSearchHistory(
        mode = SearchSnapshotMode.APPLY_TO_VISIBLE,
        visibleTransform = { s -> s.copy(recentSearches = emptyList()) },
    ) { store, key -> store.clearEntities(key) }
    override fun removeRecentSearchTerm(term: String) = updateSearchHistory(
        mode = SearchSnapshotMode.APPLY_TO_VISIBLE,
        visibleTransform = { s -> s.copy(recentSearchTerms = s.recentSearchTerms.filterNot { it.equals(term.trim(), ignoreCase = true) }) },
    ) { store, key -> store.removeTerm(key, term) }
    override fun clearRecentSearchTerms() = updateSearchHistory(
        mode = SearchSnapshotMode.APPLY_TO_VISIBLE,
        visibleTransform = { s -> s.copy(recentSearchTerms = emptyList()) },
    ) { store, key -> store.clearTerms(key) }
    override fun openRecentSearch(entry: SearchEntity) {
        when (entry.type) {
            "song" -> playSingleAsNextInternal(entry.toSong(), recordSearchEntity = false)
            "artist" -> openArtistInternal(
                Artist(entry.name, id = entry.id.takeIf { it.matches(Regex("yt:artist:UC[A-Za-z0-9_-]{1,126}")) }),
                recordSearchEntity = false,
            )
        }
    }

    private fun loadListeningOverview() {
        updateSearchHistory(SearchSnapshotMode.LOAD_AND_LOCK) { store, key -> store.load(key) }
        refreshContinueSearchSongs()
        loadDiscovery(false)
        refreshPlayStats()
        refreshRecentPlayStats()
        val connection = current() ?: return
        val ticket = identity
        viewModelScope.launch {
            try {
                val counts = withContext(ioDispatcher) { connection.api.languageCounts() }
                if (valid(ticket, connection)) mutable.value = mutable.value.copy(languageCounts = counts)
            } catch (error: Exception) { if (error is CancellationException) throw error }
        }
    }

    private fun today(): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date())
    override fun refreshDiscovery() = loadDiscovery(true)
    private fun loadDiscovery(force: Boolean) {
        val connection = current() ?: return
        if (mutable.value.discoveryLoading) return
        val ticket = identity
        val revision = ++discoveryRevision
        val key = listeningIdentity(connection)
        val day = today()
        discoveryJob?.cancel()
        mutable.value = mutable.value.copy(discoveryLoading = true, discoveryNotice = null)
        discoveryJob = viewModelScope.launch {
            var failure: ClientNotice? = null
            try {
                val cached = try { withContext(ioDispatcher) { discoveryCache?.load(key) } }
                catch (error: Exception) {
                    if (error is CancellationException) throw error
                    failure = notice(error); null
                }
                if (!valid(ticket, connection) || revision != discoveryRevision) return@launch
                // Hydrate metadata in the active session; no temporary media/image URL is read from disk.
                if (!force && cached != null) {
                    try {
                        val ids = cached.songs.map { it.id } + cached.spotlight?.songs.orEmpty().map { it.id }
                        val resolved = resolveStoredSongs(connection, ids).associateBy { it.id }
                        if (!valid(ticket, connection) || revision != discoveryRevision) return@launch
                        val songs = cached.songs.mapNotNull { entry -> resolved[entry.id]?.let {
                            songContext(it, entry.toSong(), preserveCover = false)
                        } }
                        val spotlight = cached.spotlight?.let { item ->
                            SpotlightArtist(item.artist, item.songCount, item.songs.mapNotNull { entry -> resolved[entry.id]?.let {
                                songContext(it, entry.toSong(), preserveCover = false)
                            } }, coverUrl = item.songs.firstNotNullOfOrNull { resolved[it.id]?.coverUrl })
                        }
                        mutable.value = mutable.value.copy(discoverySongs = songs, spotlightArtist = spotlight)
                        spotlight?.artist?.let { seenSpotlightArtists.add(0, it) }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        failure = notice(error)
                    }
                }
                val dailyFresh = cached?.date == day && cached.dailyLoaded
                val spotlightFresh = cached?.date == day && cached.spotlightLoaded
                val refreshDaily = (!dailyFresh || force) &&
                    (!force || (discoveryCache?.remainingCooldownMs(key, "daily") ?: 0) == 0L)
                val refreshSpotlight = (!spotlightFresh || force) &&
                    (!force || (discoveryCache?.remainingCooldownMs(key, "spotlight") ?: 0) == 0L)
                if (refreshDaily) {
                    try {
                        val exclude = mutable.value.discoverySongs.map { it.id }.ifEmpty { cached?.songs.orEmpty().map { it.id } }.take(20)
                        val songs = withContext(ioDispatcher) { connection.api.randomSongs(limit = 20, exclude = exclude) }
                            .filter { it.canPlay }.distinctBy { it.id }.take(20)
                        if (!valid(ticket, connection) || revision != discoveryRevision) return@launch
                        mutable.value = mutable.value.copy(discoverySongs = songs)
                        try { withContext(ioDispatcher) {
                            discoveryCache?.saveDaily(key, day, songs)
                            discoveryCache?.markRefreshed(key, "daily")
                        } } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            failure = notice(error)
                        }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        failure = notice(error)
                    }
                }
                if (refreshSpotlight) {
                    try {
                        val exclude = (listOfNotNull(mutable.value.spotlightArtist?.artist) + seenSpotlightArtists).distinct().take(15)
                        val spotlight = withContext(ioDispatcher) { connection.api.spotlightArtist(exclude) }
                        if (!valid(ticket, connection) || revision != discoveryRevision) return@launch
                        mutable.value = mutable.value.copy(spotlightArtist = spotlight)
                        spotlight?.artist?.let {
                            seenSpotlightArtists.remove(it); seenSpotlightArtists.add(0, it)
                            while (seenSpotlightArtists.size > 15) seenSpotlightArtists.removeAt(seenSpotlightArtists.lastIndex)
                        }
                        try { withContext(ioDispatcher) {
                            discoveryCache?.saveSpotlight(key, day, spotlight)
                            discoveryCache?.markRefreshed(key, "spotlight")
                        } } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            failure = notice(error)
                        }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        failure = notice(error)
                    }
                }
                if (valid(ticket, connection) && revision == discoveryRevision) {
                    discoveryCheckedDate = day
                    mutable.value = mutable.value.copy(discoveryNotice = failure)
                }
            } finally {
                if (valid(ticket, connection) && revision == discoveryRevision)
                    mutable.value = mutable.value.copy(discoveryLoading = false)
            }
        }
    }

    override fun replacePlaylistShelfOrder(ids: List<String>) {
        val shelf = mutable.value.playlistShelf ?: return
        val byId = shelf.items.associateBy { it.id }
        if (mutable.value.libraryMutationConflict || ids.size != byId.size || ids.toSet() != byId.keys) return
        mutateLibrary {
            val result = it.api.updatePlaylistShelf(ids.map { id -> byId.getValue(id) }, shelf.revision)
            LibraryUpdate(shelf = result.shelf, complete = result.outcome in setOf("applied", "noop"))
        }
    }

    override fun selectSongSort(sort: String) {
        if (sort !in setOf("asc", "desc") || sort == mutable.value.songSort) return
        requestRevision++; requestJob?.cancel(); offset = 0
        mutable.value = mutable.value.copy(songSort = sort, songs = emptyList(), hasMore = false, loadingMore = false)
        load(true)
    }

    override fun searchPlaylistSongs(query: String) {
        val value = query.trim().take(200)
        if (value != mutable.value.pickerQuery) mutable.value = mutable.value.copy(
            pickerQuery = value, pickerSongs = emptyList(), pickerHasMore = false)
        loadPicker(true)
    }
    override fun loadMorePlaylistSongs() {
        if (mutable.value.pickerHasMore && !mutable.value.pickerLoading && !mutable.value.pickerLoadingMore) loadPicker(false)
    }
    override fun closePlaylistSongPicker() {
        pickerRevision++; pickerJob?.cancel(); pickerOffset = 0
        mutable.value = mutable.value.copy(pickerSongs = emptyList(), pickerQuery = "", pickerLoading = false,
            pickerLoadingMore = false, pickerHasMore = false, pickerNotice = null)
    }
    private fun loadPicker(reset: Boolean) {
        val connection = current() ?: return
        val selected = mutable.value.selectedPlaylist?.id ?: return
        val ticket = identity
        val revision = ++pickerRevision
        val query = mutable.value.pickerQuery
        val requestedOffset = if (reset) 0 else pickerOffset
        pickerJob?.cancel()
        mutable.value = mutable.value.copy(pickerLoading = reset, pickerLoadingMore = !reset, pickerNotice = null)
        pickerJob = viewModelScope.launch {
            try {
                // Deliberately independent from the global tab/language/query and detail navigation.
                val page = withContext(ioDispatcher) { connection.api.songs(query, requestedOffset, "all") }
                if (!valid(ticket, connection) || revision != pickerRevision || mutable.value.selectedPlaylist?.id != selected) return@launch
                pickerOffset = page.nextOffset
                mutable.value = mutable.value.copy(pickerSongs = (if (reset) page.songs else mutable.value.pickerSongs + page.songs).distinctBy { it.id },
                    pickerLoading = false, pickerLoadingMore = false, pickerHasMore = page.hasMore,
                    pickerNotice = when { page.capped -> ClientNotice.PAGE_LIMIT; page.partialSources -> ClientNotice.SOME_SOURCES_UNAVAILABLE; else -> null })
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == pickerRevision) mutable.value = mutable.value.copy(
                    pickerLoading = false, pickerLoadingMore = false, pickerNotice = notice(error))
            }
        }
    }

    override fun searchAlbums(query: String) {
        val normalized = query.trim().take(200)
        if (normalized != mutable.value.albumSearchQuery) mutable.value = mutable.value.copy(
            albumSearchQuery = normalized, searchAlbums = emptyList(), albumSearchHasMore = false)
        loadAlbumSearch(true)
    }
    override fun loadMoreSearchAlbums() {
        if (mutable.value.albumSearchHasMore && !mutable.value.albumSearchLoading && !mutable.value.albumSearchLoadingMore)
            loadAlbumSearch(false)
    }
    private fun loadAlbumSearch(reset: Boolean) {
        val connection = current() ?: return
        val ticket = identity
        val revision = ++albumSearchRevision
        val query = mutable.value.albumSearchQuery
        val nextOffset = if (reset) 0 else albumSearchOffset
        albumSearchJob?.cancel()
        mutable.value = mutable.value.copy(albumSearchLoading = reset, albumSearchLoadingMore = !reset, albumSearchNotice = null)
        albumSearchJob = viewModelScope.launch {
            try {
                val page = withContext(ioDispatcher) { connection.api.browseAlbums(query = query, offset = nextOffset) }
                if (!valid(ticket, connection) || revision != albumSearchRevision) return@launch
                albumSearchOffset = page.nextOffset
                mutable.value = mutable.value.copy(searchAlbums = (if (reset) page.albums else mutable.value.searchAlbums + page.albums).distinctBy { it.id },
                    albumSearchLoading = false, albumSearchLoadingMore = false, albumSearchHasMore = page.hasMore,
                    albumSearchNotice = if (page.capped) ClientNotice.PAGE_LIMIT else null)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == albumSearchRevision) mutable.value = mutable.value.copy(
                    albumSearchLoading = false, albumSearchLoadingMore = false, albumSearchNotice = notice(error))
            }
        }
    }

    override fun refreshArtists(query: String) {
        val normalized = query.trim().take(200)
        if (normalized != mutable.value.artistQuery) mutable.value = mutable.value.copy(
            artistQuery = normalized, artists = emptyList(), artistsHasMore = false)
        loadArtists(true)
    }
    override fun loadMoreArtists() {
        if (mutable.value.artistsHasMore && !mutable.value.artistsLoading && !mutable.value.artistsLoadingMore) loadArtists(false)
    }
    private fun loadArtists(reset: Boolean) {
        val connection = current() ?: return
        val ticket = identity
        val revision = ++artistsRevision
        val query = mutable.value.artistQuery
        val language = if (mutable.value.destination == MainDestination.SEARCH && query.isNotBlank()) "all" else mutable.value.discoverLanguage
        val nextOffset = if (reset) 0 else artistsOffset
        artistsJob?.cancel()
        mutable.value = mutable.value.copy(artistsLoading = reset, artistsLoadingMore = !reset, artistsNotice = null)
        artistsJob = viewModelScope.launch {
            try {
                val page = withContext(ioDispatcher) { connection.api.artists(query, nextOffset, language) }
                if (!valid(ticket, connection) || revision != artistsRevision) return@launch
                artistsOffset = page.nextOffset
                mutable.value = mutable.value.copy(artists = (if (reset) page.artists else mutable.value.artists + page.artists).distinctBy { it.name },
                    artistsLoading = false, artistsLoadingMore = false, artistsHasMore = page.hasMore,
                    artistsNotice = if (page.capped) ClientNotice.PAGE_LIMIT else null)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == artistsRevision) mutable.value = mutable.value.copy(
                    artistsLoading = false, artistsLoadingMore = false, artistsNotice = notice(error))
            }
        }
    }

    override fun openArtist(artist: Artist) = openArtistInternal(artist, recordSearchEntity = true)

    private fun openArtistInternal(artist: Artist, recordSearchEntity: Boolean) {
        if (current() == null || artist.name.isBlank()) return
        val searchOrigin = recordSearchEntity && isSearchRootActive(mutable.value)
        if (searchOrigin) rememberSearchEntity(SearchEntity.fromArtist(artist.name, artist.id), allowed = true)
        closeAlbum(); closePlaylist(); closeArtist()
        mutable.value = mutable.value.copy(selectedArtist = artist)
        loadArtist(true)
        if (artist.id == null) loadArtistAlbums(true)
    }
    override fun closeArtist() {
        artistRevision++; artistJob?.cancel(); artistOffset = 0
        artistAlbumsRevision++; artistAlbumsJob?.cancel(); artistAlbumsOffset = 0
        mutable.value = mutable.value.copy(selectedArtist = null, artistSongs = emptyList(), artistLoading = false,
            artistLoadingMore = false, artistHasMore = false, artistNotice = null, artistAlbums = emptyList(),
            artistAlbumsLoading = false, artistAlbumsLoadingMore = false, artistAlbumsHasMore = false, artistAlbumsNotice = null)
    }
    override fun loadMoreArtistSongs() {
        if (mutable.value.artistHasMore && !mutable.value.artistLoading && !mutable.value.artistLoadingMore) loadArtist(false)
    }
    private fun loadArtist(reset: Boolean) {
        val connection = current() ?: return
        val selected = mutable.value.selectedArtist ?: return
        val ticket = identity
        val revision = ++artistRevision
        val nextOffset = if (reset) 0 else artistOffset
        artistJob?.cancel()
        mutable.value = mutable.value.copy(artistLoading = reset, artistLoadingMore = !reset, artistNotice = null)
        artistJob = viewModelScope.launch {
            try {
                val detail = withContext(ioDispatcher) { connection.api.artist(selected.id ?: selected.name, nextOffset) }
                if (!valid(ticket, connection) || revision != artistRevision || mutable.value.selectedArtist?.name != selected.name ||
                    mutable.value.selectedArtist?.id != selected.id) return@launch
                artistOffset = detail.nextOffset
                if (reset) {
                    val artistEntry = VisitedArtistEntry(
                        id = detail.artist.id,
                        name = detail.artist.name,
                        source = if (detail.artist.id != null) "youtube" else "local",
                        visitedAtMs = System.currentTimeMillis(),
                    )
                    detail.artist.coverUrl?.takeIf(String::isNotBlank)?.let { artistCoverCache[artistEntry.canonicalKey()] = it }
                    historyExtras?.recordArtist(artistEntry)
                }
                mutable.value = mutable.value.copy(selectedArtist = detail.artist,
                    artistSongs = (if (reset) detail.songs else mutable.value.artistSongs + detail.songs).distinctBy { it.id },
                    artistLoading = false, artistLoadingMore = false, artistHasMore = detail.hasMore,
                    artistNotice = if (detail.capped) ClientNotice.PAGE_LIMIT else null,
                    artistAlbums = if (selected.id != null) detail.albums else mutable.value.artistAlbums,
                    artistAlbumsLoading = if (selected.id != null) false else mutable.value.artistAlbumsLoading,
                    artistAlbumsLoadingMore = if (selected.id != null) false else mutable.value.artistAlbumsLoadingMore,
                    artistAlbumsHasMore = if (selected.id != null) false else mutable.value.artistAlbumsHasMore)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == artistRevision) mutable.value = mutable.value.copy(
                    artistLoading = false, artistLoadingMore = false, artistNotice = notice(error))
            }
        }
    }
    override fun loadMoreArtistAlbums() {
        if (mutable.value.artistAlbumsHasMore && !mutable.value.artistAlbumsLoading && !mutable.value.artistAlbumsLoadingMore) loadArtistAlbums(false)
    }
    private fun loadArtistAlbums(reset: Boolean) {
        val connection = current() ?: return
        val selected = mutable.value.selectedArtist ?: return
        if (selected.id != null) return
        val ticket = identity
        val revision = ++artistAlbumsRevision
        val nextOffset = if (reset) 0 else artistAlbumsOffset
        artistAlbumsJob?.cancel()
        mutable.value = mutable.value.copy(artistAlbumsLoading = reset, artistAlbumsLoadingMore = !reset, artistAlbumsNotice = null)
        artistAlbumsJob = viewModelScope.launch {
            try {
                val page = withContext(ioDispatcher) { connection.api.browseAlbums(offset = nextOffset, artist = selected.name) }
                if (!valid(ticket, connection) || revision != artistAlbumsRevision || mutable.value.selectedArtist?.name != selected.name) return@launch
                artistAlbumsOffset = page.nextOffset
                mutable.value = mutable.value.copy(artistAlbums = (if (reset) page.albums else mutable.value.artistAlbums + page.albums).distinctBy { it.id },
                    artistAlbumsLoading = false, artistAlbumsLoadingMore = false, artistAlbumsHasMore = page.hasMore,
                    artistAlbumsNotice = if (page.capped) ClientNotice.PAGE_LIMIT else null)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == artistAlbumsRevision) mutable.value = mutable.value.copy(
                    artistAlbumsLoading = false, artistAlbumsLoadingMore = false, artistAlbumsNotice = notice(error))
            }
        }
    }
    override fun refreshPlayStats() {
        val connection = current() ?: return
        val ticket = identity
        val epoch = homeSnapshotEpoch
        val revision = ++statsRevision
        statsJob?.cancel()
        mutable.value = mutable.value.copy(statsLoading = true, statsNotice = null)
        statsJob = viewModelScope.launch {
            try {
                val stats = withContext(ioDispatcher) { connection.api.playStats(limit = 50) }
                if (valid(ticket, connection) && revision == statsRevision) {
                    mutable.value = mutable.value.copy(playStats = stats, statsLoading = false)
                    allStatsReady = true
                    maybeUpdateContinueListeningSnapshot(epoch)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == statsRevision) {
                    mutable.value = mutable.value.copy(statsLoading = false, statsNotice = notice(error))
                    allStatsReady = true
                    maybeUpdateContinueListeningSnapshot(epoch)
                }
            }
        }
    }

    private fun refreshContinueSearchSongs() {
        val connection = current() ?: return
        val ticket = identity
        val epoch = homeSnapshotEpoch
        val revision = ++continueSearchRevision
        continueSearchJob?.cancel()
        val store = searchHistoryStore
        if (store == null) {
            continueSearchSongs = emptyList()
            continueSearchReady = true
            maybeUpdateContinueListeningSnapshot(epoch)
            return
        }
        val key = listeningIdentity(connection)
        continueSearchJob = viewModelScope.launch {
            val songs = try {
                // Read disk, not the intentionally locked visible search snapshot.
                val entries = withContext(ioDispatcher) { searchMutex.withLock { store.load(key).entities } }
                    .filter { it.type == "song" }
                val contexts = entries.map { it.toSong() }.associateBy { it.id }
                resolveStoredSongs(connection, entries.map { it.id }, contexts)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                emptyList()
            }
            if (valid(ticket, connection) && revision == continueSearchRevision && epoch == homeSnapshotEpoch) {
                continueSearchSongs = songs
                continueSearchReady = true
                maybeUpdateContinueListeningSnapshot(epoch)
            }
        }
    }

    private fun refreshRecentPlayStats() {
        val connection = current() ?: return
        val ticket = identity
        val epoch = homeSnapshotEpoch
        val revision = ++recentStatsRevision
        recentStatsJob?.cancel()
        recentStatsJob = viewModelScope.launch {
            try {
                val windows = coroutineScope {
                    listOf(7, 1).map { days -> async(ioDispatcher) {
                        try { connection.api.playStats(limit = 50, summaryOnly = true, days = days) }
                        catch (error: Exception) {
                            if (error is CancellationException) throw error
                            null
                        }
                    } }.awaitAll()
                }
                if (valid(ticket, connection) && revision == recentStatsRevision && epoch == homeSnapshotEpoch) {
                    recentPlayStats = windows[0]
                    recentDayPlayStats = windows[1]
                    recentStatsReady = true
                    maybeUpdateContinueListeningSnapshot(epoch)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // An old/failed host contributes no fabricated recent statistics.
                if (valid(ticket, connection) && revision == recentStatsRevision) {
                    recentPlayStats = null
                    recentStatsReady = true
                    maybeUpdateContinueListeningSnapshot(epoch)
                }
            }
        }
    }

    private data class LibraryUpdate(
        val summary: PlaylistSummary? = null,
        val detail: PlaylistDetail? = null,
        val shelf: PlaylistShelf? = null,
        val deletedId: String? = null,
        val results: List<PlaylistTargetResult> = emptyList(),
        val complete: Boolean = true,
        val refreshRemote: Boolean = true,
    )

    override fun dismissLibraryMutationNotice() {
        if (mutable.value.libraryMutationBusy) return
        pendingCreate = null
        mutable.value = mutable.value.copy(libraryMutationNotice = null,
            libraryMutationConflict = false, libraryMutationResults = emptyList())
    }

    private fun mutateLibrary(operation: suspend (ActiveConnection) -> LibraryUpdate) {
        val connection = current() ?: return
        if (mutable.value.libraryMutationBusy) return
        val ticket = identity
        mutable.value = mutable.value.copy(libraryMutationBusy = true, libraryMutationNotice = null,
            libraryMutationConflict = false, libraryMutationResults = emptyList())
        mutationJob = viewModelScope.launch {
            try {
                val result = withContext(ioDispatcher) { operation(connection) }
                if (!valid(ticket, connection)) return@launch
                result.summary?.let(::mergePlaylist)
                result.detail?.let(::publishPlaylist)
                result.shelf?.let { mutable.value = mutable.value.copy(playlistShelf = it) }
                if (result.deletedId != null) {
                    if (mutable.value.selectedPlaylist?.id == result.deletedId) closePlaylist()
                    mutable.value = mutable.value.copy(playlists = mutable.value.playlists.filterNot { it.id == result.deletedId })
                }
                if (result.complete) pendingCreate = null
                mutable.value = mutable.value.copy(libraryMutationBusy = false,
                    libraryMutationNotice = if (result.complete) null else result.results.firstNotNullOfOrNull { limitNotice(it.error) } ?: ClientNotice.REQUEST_FAILED,
                    libraryMutationResults = result.results,
                    libraryMutationConflict = result.results.any { it.error == "REVISION_CONFLICT" },
                    libraryMutationVersion = mutable.value.libraryMutationVersion + if (result.complete) 1 else 0)
                if (result.refreshRemote) {
                    refreshPlaylists()
                    mutable.value.selectedPlaylist?.let(::openPlaylist)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (!valid(ticket, connection)) return@launch
                val conflict = (error as? ApiFailure)?.httpStatus == 409 && (error as? ApiFailure)?.code in setOf("REVISION_CONFLICT", "revision_conflict")
                (error as? ApiFailure)?.details?.let { details ->
                    details.playlist?.let(::mergePlaylist)
                    details.shelf?.let { mutable.value = mutable.value.copy(playlistShelf = it) }
                }
                mutable.value = mutable.value.copy(libraryMutationBusy = false,
                    libraryMutationNotice = notice(error), libraryMutationConflict = conflict)
                if (conflict) {
                    refreshPlaylists()
                    mutable.value.selectedPlaylist?.let(::openPlaylist)
                }
            }
        }
    }

    private fun mergePlaylist(playlist: PlaylistSummary): Boolean {
        if (playlist.kind == "favorite") {
            if (!rememberFavoriteSummary(playlist)) return false
        } else if (playlist.id == knownFavoriteSummary?.id) return false
        val snapshot = mutable.value
        val exists = snapshot.playlists.any { it.id == playlist.id }
        val completeFavorite = playlist.kind != "favorite" || knownFavoriteDetail?.playlist?.revision == playlist.revision
        mutable.value = snapshot.copy(playlists = if (exists)
            snapshot.playlists.map { if (it.id == playlist.id) playlist else it } else snapshot.playlists + playlist,
            selectedPlaylist = if (snapshot.selectedPlaylist?.id == playlist.id && completeFavorite) playlist else snapshot.selectedPlaylist)
        return true
    }

    private fun publishPlaylist(detail: PlaylistDetail, notifySystem: Boolean = true): PlaylistDetail? {
        if (detail.playlist.kind == "favorite") return publishFavorites(detail, notifySystem)
        if (!mergePlaylist(detail.playlist)) return null
        if (mutable.value.selectedPlaylist?.id == detail.playlist.id) mutable.value = mutable.value.copy(
            selectedPlaylistEntries = detail.entries, playlistSongs = detail.songs,
            playlistLoading = false, playlistNotice = null)
        return detail
    }



    private fun PlaylistMutationResult.update() = LibraryUpdate(playlist, detail, shelf,
        complete = outcome == "applied" || outcome == "noop")

    override fun createPlaylist(name: String, description: String, songIds: List<String>) {
        if (current() == null || mutable.value.libraryMutationBusy) return
        val payload = Triple(name.trim(), description.trim(), songIds.distinct().toList())
        if (pendingCreate?.first != payload) pendingCreate = payload to java.util.UUID.randomUUID().toString()
        val key = pendingCreate!!.second
        mutateLibrary { connection ->
            val result = connection.api.createPlaylist(payload.first, payload.second, payload.third, key).update()
            result
        }
    }

    override fun updatePlaylist(playlist: PlaylistSummary, name: String, description: String) {
        if (playlist.kind == "favorite") return
        mutateLibrary { it.api.updatePlaylist(playlist.id, playlist.revision, name, description).update() }
    }

    override fun deletePlaylist(playlist: PlaylistSummary) {
        if (playlist.kind == "favorite") return
        mutateLibrary {
            val result = it.api.deletePlaylist(playlist.id, playlist.revision)
            LibraryUpdate(shelf = result.shelf, deletedId = result.deletedPlaylistId,
                complete = result.outcome in setOf("applied", "noop"))
        }
    }

    override fun toggleFavorite(song: Song) {
        val remove = song.id in mutable.value.favoriteSongIds
        val known = mutable.value.playlists.find { it.kind == "favorite" }
        mutateLibrary {
            val favorite = known ?: it.api.playlists().firstOrNull { row -> row.kind == "favorite" }
                ?: throw ApiFailure(Failure.INVALID_RESPONSE)
            if (remove) it.api.removePlaylistSong(favorite.id, song.id, favorite.revision).update()
            else {
                val result = it.api.addPlaylistSongs(listOf(PlaylistTarget(favorite.id, favorite.revision)), listOf(song.id))
                LibraryUpdate(results = result.results, complete = result.outcome in setOf("applied", "noop"))
            }
        }
    }

    override fun addFavorites(songs: List<Song>) {
        val ids = songs.map { it.id }.distinct()
        if (ids.isEmpty() || mutable.value.libraryMutationBusy) return
        val snapshot = mutable.value
        val toAdd = ids.filterNot { it in snapshot.favoriteSongIds }
        if (toAdd.isEmpty()) {
            mutable.value = snapshot.copy(
                libraryMutationNotice = null,
                libraryMutationVersion = snapshot.libraryMutationVersion + 1,
            )
            emitPlaybackFeedback(PlaybackFeedbackKind.FAVORITED_BATCH)
            return
        }
        val known = snapshot.playlists.find { it.kind == "favorite" }
        mutateLibrary { connection ->
            var favorite = known ?: connection.api.playlists().firstOrNull { row -> row.kind == "favorite" }
                ?: throw ApiFailure(Failure.INVALID_RESPONSE)
            var lastResults = emptyList<PlaylistTargetResult>()
            var allComplete = true
            for (batch in toAdd.chunked(100)) {
                val result = connection.api.addPlaylistSongs(listOf(PlaylistTarget(favorite.id, favorite.revision)), batch)
                lastResults = result.results
                if (result.outcome !in setOf("applied", "noop")) {
                    allComplete = false
                    break
                }
                val nextRevision = result.results.firstOrNull { it.playlistId == favorite.id }?.revision
                if (nextRevision != null) {
                    favorite = favorite.copy(revision = nextRevision)
                } else if (toAdd.size > 100) {
                    favorite = connection.api.playlist(favorite.id).playlist
                }
            }
            if (allComplete) emitPlaybackFeedback(PlaybackFeedbackKind.FAVORITED_BATCH)
            LibraryUpdate(results = lastResults, complete = allComplete)
        }
    }

    override fun addPlaylistSongs(playlistIds: List<String>, songIds: List<String>) {
        val ids = playlistIds.distinct()
        val songs = songIds.distinct()
        if (ids.isEmpty() || songs.isEmpty() || songs.size > 100 || ids.size * songs.size > 500) return
        val summaries = mutable.value.playlists.associateBy { it.id }
        val targets = ids.map { id -> summaries[id]?.let { PlaylistTarget(id, it.revision) } ?: return }
        mutateLibrary {
            val result = it.api.addPlaylistSongs(targets, songs)
            LibraryUpdate(results = result.results, complete = result.outcome in setOf("applied", "noop"))
        }
    }

    override fun removePlaylistSongs(songIds: List<String>) {
        val snapshot = mutable.value
        val selected = snapshot.selectedPlaylist ?: return
        if (snapshot.playlistLoading || snapshot.playlistNotice != null) return
        val remove = songIds.toSet()
        val ids = snapshot.selectedPlaylistEntries.map { it.song.id }.filterNot { it in remove }
        mutateLibrary { it.api.replacePlaylistSongs(selected.id, ids, selected.revision).update() }
    }

    override fun replacePlaylistOrder(songIds: List<String>) {
        val snapshot = mutable.value
        val selected = snapshot.selectedPlaylist ?: return
        val currentIds = snapshot.selectedPlaylistEntries.map { it.song.id }
        if (snapshot.playlistLoading || snapshot.playlistNotice != null || snapshot.libraryMutationConflict ||
            songIds.size != currentIds.size || songIds.toSet().size != songIds.size ||
            songIds.toSet() != currentIds.toSet()) return
        mutateLibrary { it.api.replacePlaylistSongs(selected.id, songIds.toList(), selected.revision).update() }
    }

    override fun movePlaylistShelf(fromIndex: Int, toIndex: Int) {
        val shelf = mutable.value.playlistShelf ?: return
        if (fromIndex !in shelf.items.indices || toIndex !in shelf.items.indices || fromIndex == toIndex) return
        val items = shelf.items.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        mutateLibrary {
            val result = it.api.updatePlaylistShelf(items, shelf.revision)
            LibraryUpdate(shelf = result.shelf, complete = result.outcome in setOf("applied", "noop"))
        }
    }



    private fun sameSongContext(left: Song, right: Song): Boolean = left.id == right.id &&
        left.isYouTube == right.isYouTube && left.albumId == right.albumId && left.title == right.title &&
        left.artist == right.artist && left.album == right.album && left.coverUrl == right.coverUrl

    private fun songContext(fresh: Song, selected: Song, preserveCover: Boolean): Song {
        if (fresh.id != selected.id || fresh.isYouTube != selected.isYouTube) return fresh
        return mergeSongRelations(fresh, selected, preferPrevious = true).copy(
            title = selected.title.ifBlank { fresh.title }, artist = selected.artist.ifBlank { fresh.artist },
            album = selected.album.ifBlank { fresh.album },
            coverUrl = if (preserveCover) selected.coverUrl ?: fresh.coverUrl else fresh.coverUrl)
    }

    private suspend fun preparePlaybackSongs(connection: ActiveConnection, requested: List<Song>): List<Pair<Int, Song>> {
        val snapshot = mutable.value
        // Only account-owned current state may supply the selected context. Caller URLs are never used.
        val owned = snapshot.albumSongs + snapshot.playlistSongs + snapshot.artistSongs + snapshot.songs +
            snapshot.homeLibrarySongs + snapshot.favoriteSongs + snapshot.continueListeningSongs + snapshot.discoverySongs + snapshot.spotlightArtist?.songs.orEmpty() +
            snapshot.playback.queue + snapshot.playStats?.songs.orEmpty().map { it.song } + snapshot.playHistory
        val stored = snapshot.recentSearches.filter { it.type == "song" }.map { it.toSong() } +
            historyEntries.map { it.toSong() }
        val contexts = requested.map { requestedSong ->
            val matches = owned.filter { it.id == requestedSong.id }
            val live = matches.firstOrNull { it === requestedSong }
                ?: matches.firstOrNull { sameSongContext(it, requestedSong) }
                ?: matches.firstOrNull { requestedSong.albumId != null && it.albumId == requestedSong.albumId }
                ?: matches.firstOrNull()
            live?.let { it to true } ?: stored.firstOrNull { sameSongContext(it, requestedSong) }?.let { it to false }
        }
        // Current-session media is trusted; restored or unknown entries must resolve in this connection.
        val missing = requested.mapIndexedNotNull { index, song ->
            song.id.takeUnless { contexts[index]?.let { it.second && it.first.canPlay } == true }
        }
        val fresh = if (missing.isEmpty()) emptyMap() else resolveStoredSongs(connection, missing).associateBy { it.id }
        return requested.mapIndexedNotNull { index, song ->
            val context = contexts[index]
            if (context != null && context.second && context.first.canPlay) index to context.first
            else fresh[song.id]?.let { received ->
                index to if (context == null) received else songContext(received, context.first, context.second)
            }
        }
    }

    override fun playSingleAsNext(song: Song, fromSearch: Boolean) =
        playSingleAsNextInternal(song, recordSearchEntity = fromSearch && isSearchRootActive(mutable.value))

    private fun playSingleAsNextInternal(song: Song, recordSearchEntity: Boolean) {
        val connection = current() ?: return
        val ticket = identity
        val revision = beginPlaybackCommand()
        playJob?.cancel()
        playJob = viewModelScope.launch {
            try {
                val resolved = preparePlaybackSongs(connection, listOf(song)).singleOrNull()?.second
                if (!valid(ticket, connection) || revision != playRevision) return@launch
                if (resolved == null || !resolved.canPlay) {
                    mutable.value = mutable.value.copy(notice = ClientNotice.PLAYBACK_FAILED)
                    emitPlaybackFeedback(PlaybackFeedbackKind.TRACK_UNAVAILABLE)
                    return@launch
                }
                connectPlayer(connection)
                if (valid(ticket, connection) && revision == playRevision) {
                    val started = player.playSingleAsNext(resolved)
                    if (started) {
                        if (recordSearchEntity) rememberSearchEntity(SearchEntity.fromSong(resolved), allowed = true)
                        emitPlaybackFeedback(PlaybackFeedbackKind.PLAYED_SINGLE_NEXT)
                    } else {
                        mutable.value = mutable.value.copy(notice = ClientNotice.PLAYBACK_FAILED)
                        emitPlaybackFeedback(PlaybackFeedbackKind.TRACK_UNAVAILABLE)
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == playRevision)
                    mutable.value = mutable.value.copy(notice = notice(error))
            }
        }
    }

    override fun playSongs(songs: List<Song>, startIndex: Int, shuffle: Boolean) =
        playSongsInternal(songs, startIndex, shuffle, recordSearchEntity = isSearchRootActive(mutable.value))

    private fun playSongsInternal(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false, recordSearchEntity: Boolean) {
        val connection = current() ?: return
        if (startIndex !in songs.indices) return
        val captured = songs.toList()
        val uiBefore = mutable.value
        val (originType, originLabel, albumToRecord, playlistToRecord) = detectQueueSource(uiBefore, captured)
        val ticket = identity
        val revision = beginPlaybackCommand()
        playJob?.cancel()
        playJob = viewModelScope.launch {
            try {
                val indexed = preparePlaybackSongs(connection, captured)
                val target = indexed.indexOfFirst { it.first == startIndex }
                if (!valid(ticket, connection) || revision != playRevision || target < 0) return@launch
                if (recordSearchEntity) rememberSearchEntity(SearchEntity.fromSong(indexed[target].second), allowed = true)
                connectPlayer(connection)
                if (valid(ticket, connection) && revision == playRevision) {
                    val resolvedSongs = indexed.map { it.second }
                    captureQueueSnapshotBeforeReplacement(resolvedSongs)
                    albumToRecord?.let { recordAlbumCollection(it, resolvedSongs) }
                    playlistToRecord?.let { recordPlaylistCollection(it, resolvedSongs) }
                    activeQueueOriginType = originType
                    activeQueueOriginLabel = originLabel
                    player.play(resolvedSongs, target, shuffle)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == playRevision)
                    mutable.value = mutable.value.copy(notice = notice(error))
            }
        }
    }

    override fun playPlaylist(playlist: PlaylistSummary, shuffle: Boolean) {
        val connection = current() ?: return
        val ticket = identity
        val revision = beginPlaybackCommand()
        playJob?.cancel()
        playJob = viewModelScope.launch {
            try {
                val received = withContext(ioDispatcher) { connection.api.playlist(playlist.id) }
                if (!valid(ticket, connection) || revision != playRevision) return@launch
                val detail = if (received.playlist.kind == "favorite")
                    favoriteForPlayback(connection, received, ticket, revision) else publishPlaylist(received)
                if (detail == null || detail.songs.isEmpty()) return@launch
                connectPlayer(connection)
                if (valid(ticket, connection) && revision == playRevision) {
                    val latest = if (received.playlist.kind == "favorite") currentFavoriteDetail() ?: throw ApiFailure(Failure.REQUEST) else detail
                    if (latest.songs.isNotEmpty()) {
                        captureQueueSnapshotBeforeReplacement(latest.songs)
                        recordPlaylistCollection(latest.playlist, latest.songs)
                        activeQueueOriginType = if (latest.playlist.kind == "favorite") "favorites" else "playlist"
                        activeQueueOriginLabel = latest.playlist.name
                        player.play(latest.songs, 0, shuffle)
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == playRevision)
                    mutable.value = mutable.value.copy(playlistNotice = notice(error))
            }
        }
    }

    override fun enqueuePlaylist(playlist: PlaylistSummary) {
        val connection = current() ?: return
        val ticket = identity
        val revision = beginPlaybackCommand()
        playJob?.cancel()
        playJob = viewModelScope.launch {
            try {
                val received = withContext(ioDispatcher) { connection.api.playlist(playlist.id) }
                if (!valid(ticket, connection) || revision != playRevision) return@launch
                val detail = if (received.playlist.kind == "favorite")
                    favoriteForPlayback(connection, received, ticket, revision) else publishPlaylist(received)
                val songs = detail?.songs.orEmpty()
                if (songs.isEmpty()) {
                    emitPlaybackFeedback(PlaybackFeedbackKind.TRACK_UNAVAILABLE)
                    return@launch
                }
                connectPlayer(connection)
                if (valid(ticket, connection) && revision == playRevision) {
                    player.enqueue(songs, false)
                    emitPlaybackFeedback(PlaybackFeedbackKind.ENQUEUED_BATCH)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == playRevision)
                    mutable.value = mutable.value.copy(playlistNotice = notice(error))
            }
        }
    }

    override fun playNext(song: Song) = appendToQueue(listOf(song), true)
    override fun playNextSongs(songs: List<Song>) {
        val connection = current() ?: return
        if (songs.isEmpty()) return
        val ticket = identity
        val revision = beginPlaybackCommand()
        playJob?.cancel()
        playJob = viewModelScope.launch {
            try {
                val playable = preparePlaybackSongs(connection, songs.toList()).map { it.second }
                if (!valid(ticket, connection) || revision != playRevision) return@launch
                if (playable.isEmpty()) {
                    emitPlaybackFeedback(PlaybackFeedbackKind.TRACK_UNAVAILABLE)
                    return@launch
                }
                connectPlayer(connection)
                if (valid(ticket, connection) && revision == playRevision) {
                    player.enqueue(playable, true)
                    emitPlaybackFeedback(PlaybackFeedbackKind.PLAYED_BATCH_NEXT)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == playRevision)
                    mutable.value = mutable.value.copy(queueNotice = notice(error))
            }
        }
    }
    override fun enqueue(songs: List<Song>) = appendToQueue(songs, false)
    private fun appendToQueue(songs: List<Song>, next: Boolean) {
        val connection = current() ?: return
        if (songs.isEmpty()) return
        val ticket = identity
        val revision = beginPlaybackCommand()
        playJob?.cancel()
        playJob = viewModelScope.launch {
            try {
                val playable = preparePlaybackSongs(connection, songs.toList()).map { it.second }
                if (!valid(ticket, connection) || revision != playRevision || playable.isEmpty()) return@launch
                connectPlayer(connection)
                if (valid(ticket, connection) && revision == playRevision) player.enqueue(playable, next)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == playRevision)
                    mutable.value = mutable.value.copy(queueNotice = notice(error))
            }
        }
    }

    override fun clearQueue() {
        if (current() == null) return
        beginPlaybackCommand(); playJob?.cancel()
        player.clearQueue()
        // Also clear a cold-start saved queue that has not yet reached the Player.
        queueRepository?.save(PlaybackSnapshot())
    }

    override fun removeHistory(songId: String) {
        val repository = history ?: return
        mutateLibrary { if (!repository.remove(songId)) throw ApiFailure(Failure.STORAGE); LibraryUpdate(refreshRemote = false) }
    }
    override fun clearHistory() {
        val repository = history ?: return
        mutateLibrary { if (!repository.clearEntries()) throw ApiFailure(Failure.STORAGE); LibraryUpdate(refreshRemote = false) }
    }

    override fun removeVisitedArtist(artistKey: String) {
        val repository = historyExtras ?: return
        mutateLibrary { if (!repository.removeArtist(artistKey)) throw ApiFailure(Failure.STORAGE); LibraryUpdate(refreshRemote = false) }
    }

    override fun clearVisitedArtists() {
        val repository = historyExtras ?: return
        mutateLibrary { if (!repository.clearArtists()) throw ApiFailure(Failure.STORAGE); LibraryUpdate(refreshRemote = false) }
    }

    override fun removePlayedCollection(collectionKey: String) {
        val repository = historyExtras ?: return
        mutateLibrary { if (!repository.removeCollection(collectionKey)) throw ApiFailure(Failure.STORAGE); LibraryUpdate(refreshRemote = false) }
    }

    override fun clearPlayedCollections() {
        val repository = historyExtras ?: return
        mutateLibrary { if (!repository.clearCollections()) throw ApiFailure(Failure.STORAGE); LibraryUpdate(refreshRemote = false) }
    }

    override fun removeQueueSnapshot(snapshotId: String) {
        val repository = historyExtras ?: return
        mutateLibrary { if (!repository.removeQueueSnapshot(snapshotId)) throw ApiFailure(Failure.STORAGE); LibraryUpdate(refreshRemote = false) }
    }

    override fun clearQueueSnapshots() {
        val repository = historyExtras ?: return
        mutateLibrary { if (!repository.clearQueueSnapshots()) throw ApiFailure(Failure.STORAGE); LibraryUpdate(refreshRemote = false) }
    }

    override fun openPlayedCollection(collection: PlayedCollection) {
        when (collection.type) {
            PlayedCollectionType.ALBUM -> openAlbum(
                Album(
                    id = collection.id,
                    title = collection.title,
                    artist = collection.subtitle,
                    coverUrl = collection.coverUrl,
                    trackCount = collection.trackCount,
                ),
            )
            PlayedCollectionType.PLAYLIST -> {
                val summary = mutable.value.playlists.find { it.id == collection.id } ?: PlaylistSummary(
                    id = collection.id,
                    name = collection.title,
                    kind = collection.kind,
                    description = collection.subtitle,
                    songCount = collection.trackCount,
                    customCoverUrl = collection.coverUrl,
                    previewCovers = collection.previewCovers,
                )
                openPlaylist(summary)
            }
        }
    }

    override fun playPlayedCollection(collection: PlayedCollection) {
        val connection = current() ?: return
        when (collection.type) {
            PlayedCollectionType.ALBUM -> {
                val ticket = identity
                val revision = beginPlaybackCommand()
                playJob?.cancel()
                playJob = viewModelScope.launch {
                    try {
                        val detail = withContext(ioDispatcher) { connection.api.album(collection.id) }
                        if (!valid(ticket, connection) || revision != playRevision) return@launch
                        val playable = preparePlaybackSongs(connection, detail.songs).map { it.second }
                        if (!valid(ticket, connection) || revision != playRevision || playable.isEmpty()) return@launch
                        connectPlayer(connection)
                        if (valid(ticket, connection) && revision == playRevision) {
                            captureQueueSnapshotBeforeReplacement(playable)
                            recordAlbumCollection(detail.album, playable)
                            activeQueueOriginType = "album"
                            activeQueueOriginLabel = detail.album.title
                            player.play(playable, 0)
                        }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        if (valid(ticket, connection) && revision == playRevision)
                            mutable.value = mutable.value.copy(historyNotice = notice(error))
                    }
                }
            }
            PlayedCollectionType.PLAYLIST -> {
                val summary = mutable.value.playlists.find { it.id == collection.id } ?: PlaylistSummary(
                    id = collection.id,
                    name = collection.title,
                    kind = collection.kind,
                    description = collection.subtitle,
                    songCount = collection.trackCount,
                    customCoverUrl = collection.coverUrl,
                    previewCovers = collection.previewCovers,
                )
                playPlaylist(summary)
            }
        }
    }

    override fun restoreQueueSnapshot(snapshotId: String) {
        val connection = current() ?: return
        val stored = historyExtrasSnapshot.queueSnapshots.find { it.id == snapshotId } ?: return
        if (stored.entries.isEmpty()) return
        val ticket = identity
        val revision = beginPlaybackCommand()
        playJob?.cancel()
        playJob = viewModelScope.launch {
            try {
                val contexts = stored.entries.associate { it.id to it.toSong() }
                val resolved = resolveStoredSongs(connection, stored.entries.map { it.id }, contexts).associateBy { it.id }
                if (!valid(ticket, connection) || revision != playRevision) return@launch
                val indexed = stored.entries.mapIndexedNotNull { index, entry ->
                    resolved[entry.id]?.let { fresh -> index to songContext(fresh, entry.toSong(), preserveCover = false) }
                }
                val playableIndexed = indexed.filter { it.second.canPlay }
                if (playableIndexed.isEmpty()) {
                    mutable.value = mutable.value.copy(historyNotice = ClientNotice.SOME_SOURCES_UNAVAILABLE)
                    emitPlaybackFeedback(PlaybackFeedbackKind.QUEUE_RESTORE_UNAVAILABLE)
                    return@launch
                }
                val omittedCount = (stored.entries.size - playableIndexed.size).coerceAtLeast(0)
                val target = playableIndexed.indexOfFirst { it.first >= stored.currentIndex }.takeIf { it >= 0 } ?: playableIndexed.lastIndex
                val queue = playableIndexed.map { it.second }
                queue.forEach { if (!it.coverUrl.isNullOrBlank()) snapshotSongCache[it.id] = it }
                connectPlayer(connection)
                if (!valid(ticket, connection) || revision != playRevision) return@launch
                captureQueueSnapshotBeforeReplacement(queue)
                activeQueueOriginType = stored.sourceType.ifBlank { "queue" }
                activeQueueOriginLabel = stored.sourceLabel
                player.play(queue, target)
                if (omittedCount > 0) {
                    mutable.value = mutable.value.copy(historyNotice = ClientNotice.SOME_SOURCES_UNAVAILABLE)
                    emitPlaybackFeedback(PlaybackFeedbackKind.QUEUE_RESTORED_PARTIAL, omittedCount = omittedCount)
                } else {
                    mutable.value = mutable.value.copy(historyNotice = null)
                    emitPlaybackFeedback(PlaybackFeedbackKind.QUEUE_RESTORED)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == playRevision)
                    mutable.value = mutable.value.copy(historyNotice = notice(error))
            }
        }
    }

    private data class DetectedQueueSource(
        val type: String,
        val label: String,
        val album: Album? = null,
        val playlist: PlaylistSummary? = null,
    )

    private fun detectQueueSource(ui: ClientUiState, requested: List<Song>): DetectedQueueSource {
        fun sameOrder(other: List<Song>) = requested.isNotEmpty() && requested.size == other.size &&
            requested.indices.all { requested[it].id == other[it].id }
        val selectedAlbum = ui.selectedAlbum
        if (selectedAlbum != null && ui.albumSongs.isNotEmpty() && sameOrder(ui.albumSongs)) {
            return DetectedQueueSource("album", selectedAlbum.title, album = selectedAlbum)
        }
        val selectedPlaylist = ui.selectedPlaylist
        val activePlaylistSongs = if (selectedPlaylist != null) {
            sortPlaylistEntries(ui.selectedPlaylistEntries, ui.playlistSongSort)
                .mapNotNull { entry -> entry.song.takeIf { entry.canPlay } }
                .ifEmpty { ui.playlistSongs }
        } else emptyList()
        if (selectedPlaylist != null && activePlaylistSongs.isNotEmpty() && sameOrder(activePlaylistSongs)) {
            val type = if (selectedPlaylist.kind == "favorite") "favorites" else "playlist"
            return DetectedQueueSource(type, selectedPlaylist.name, playlist = selectedPlaylist)
        }
        val selectedArtist = ui.selectedArtist
        if (selectedArtist != null && ui.artistSongs.isNotEmpty() && sameOrder(ui.artistSongs)) {
            return DetectedQueueSource("artist", selectedArtist.name)
        }
        if (sameOrder(ui.playHistory)) return DetectedQueueSource("history", "")
        if (sameOrder(ui.continueListeningSongs)) return DetectedQueueSource("continue", "")
        if (sameOrder(ui.discoverySongs)) return DetectedQueueSource("discovery", "")
        if (sameOrder(ui.homeLibrarySongs)) return DetectedQueueSource("library", "")
        if (sameOrder(ui.favoriteSongs)) return DetectedQueueSource("favorites", "")
        if (ui.playStats?.songs?.map { it.song }?.let(::sameOrder) == true) return DetectedQueueSource("stats", "")
        if (ui.destination == MainDestination.SEARCH && sameOrder(ui.songs)) {
            return DetectedQueueSource("search", ui.query)
        }
        return DetectedQueueSource("library", "")
    }

    private fun captureQueueSnapshotBeforeReplacement(incomingSongs: List<Song>) {
        val repository = historyExtras ?: return
        val existing = player.state.value
        val surviving = existing.queue.mapIndexedNotNull { originalIndex, song ->
            if (song.id.isBlank() || (!song.canPlay && song.title.isBlank())) return@mapIndexedNotNull null
            val entry = runCatching { metadataEntry(song) }.getOrNull() ?: return@mapIndexedNotNull null
            Triple(originalIndex, song, entry)
        }
        if (surviving.size < 2) return
        val validOld = surviving.map { it.second }
        val entries = surviving.map { it.third }
        if (validOld.map { it.id } == incomingSongs.map { it.id }) return
        val now = System.currentTimeMillis()
        val mappedIndex = if (existing.currentIndex in existing.queue.indices) {
            surviving.indexOfFirst { it.first >= existing.currentIndex }
                .takeIf { it >= 0 } ?: surviving.lastIndex
        } else {
            0
        }
        validOld.forEach { if (!it.coverUrl.isNullOrBlank()) snapshotSongCache[it.id] = it }
        repository.recordQueueSnapshot(
            QueueSnapshotEntry(
                id = "qs-$now-${++snapshotNonce}",
                capturedAtMs = now,
                sourceLabel = activeQueueOriginLabel,
                sourceType = activeQueueOriginType,
                entries = entries,
                currentIndex = mappedIndex,
            ),
        )
    }

    private fun recordAlbumCollection(album: Album, songs: List<Song>) {
        val source = if (album.id.startsWith("yt:")) "youtube" else "local"
        val entry = PlayedCollectionEntry(
            type = PlayedCollectionType.ALBUM,
            id = album.id,
            title = album.title,
            subtitle = album.artist,
            trackCount = album.knownTrackCount ?: songs.size,
            source = source,
            kind = "album",
            playedAtMs = System.currentTimeMillis(),
        )
        val cover = album.coverUrl ?: songs.firstOrNull { !it.coverUrl.isNullOrBlank() }?.coverUrl
        if (!cover.isNullOrBlank()) collectionCoverCache[entry.canonicalKey()] = cover
        historyExtras?.recordCollection(entry)
    }

    private fun recordPlaylistCollection(playlist: PlaylistSummary, songs: List<Song>) {
        val entry = PlayedCollectionEntry(
            type = PlayedCollectionType.PLAYLIST,
            id = playlist.id,
            title = playlist.name,
            subtitle = playlist.description,
            trackCount = songs.size.takeIf { it > 0 } ?: playlist.songCount,
            source = "local",
            kind = playlist.kind,
            playedAtMs = System.currentTimeMillis(),
        )
        val key = entry.canonicalKey()
        playlist.customCoverUrl?.takeIf(String::isNotBlank)?.let { collectionCoverCache[key] = it }
        val previews = playlist.previewCovers.ifEmpty {
            songs.mapNotNull { it.coverUrl?.takeIf(String::isNotBlank) }.distinct().take(4)
        }
        if (previews.isNotEmpty()) collectionPreviewCoversCache[key] = previews
        historyExtras?.recordCollection(entry)
    }

    override fun setPlaylistCover(playlist: PlaylistSummary, sourceUri: String) {
        val processor = coverImageProcessor ?: return
        if (playlist.kind == "favorite") return
        val ticket = identity
        mutateLibrary { connection ->
            // Slot CAS belongs to a separate revision domain from playlist metadata.
            val slot = connection.api.imageSlot("playlist", playlist.id)
            currentCoroutineContext().ensureActive()
            val bytes = processor.prepare(sourceUri)
            currentCoroutineContext().ensureActive()
            if (!valid(ticket, connection)) throw CancellationException()
            var draft: UserImage? = null
            var applied = false
            try {
                draft = connection.api.uploadPlaylistCover(bytes)
                currentCoroutineContext().ensureActive()
                if (!valid(ticket, connection)) throw CancellationException()
                val updated = connection.api.setPlaylistCover(playlist.id, draft.id, slot.revision)
                applied = true
                LibraryUpdate(summary = playlist.copy(customCoverUrl = updated.url, coverRevision = updated.revision))
            } finally {
                if (!applied && draft != null) withContext(NonCancellable) {
                    // Referenced images are rejected by the host, including an uncertain successful CAS.
                    runCatching { connection.api.discardUserImage(draft.id) }
                }
            }
        }
    }

    override fun clearPlaylistCover(playlist: PlaylistSummary) {
        if (playlist.kind == "favorite") return
        mutateLibrary {
            val slot = it.api.imageSlot("playlist", playlist.id)
            val updated = it.api.setPlaylistCover(playlist.id, null, slot.revision)
            LibraryUpdate(summary = playlist.copy(customCoverUrl = updated.url, coverRevision = updated.revision))
        }
    }

    private data class ProfileUpdate(
        val displayName: String? = null,
        val avatarUrl: String? = null,
        val updateAvatar: Boolean = false,
        val avatarRevision: Long? = null,
    )

    private fun refreshAvatarSlot(connection: ActiveConnection, ticket: Long) {
        val revision = ++profileRevision
        viewModelScope.launch {
            val slot = runCatching { withContext(ioDispatcher) { connection.api.imageSlot("avatar", "avatar") } }.getOrNull()
                ?: return@launch
            if (!valid(ticket, connection) || revision != profileRevision) return@launch
            applyProfileUpdate(ProfileUpdate(avatarUrl = slot.url, updateAvatar = true, avatarRevision = slot.revision))
        }
    }

    private fun applyProfileUpdate(update: ProfileUpdate) {
        connections.updateActiveAccountProfile(
            displayName = update.displayName,
            avatarUrl = update.avatarUrl,
            updateAvatar = update.updateAvatar,
            avatarRevision = update.avatarRevision,
        )
        val currentUi = mutable.value
        mutable.value = currentUi.copy(
            accountName = update.displayName ?: currentUi.accountName,
            avatarUrl = if (update.updateAvatar) update.avatarUrl else currentUi.avatarUrl,
            avatarRevision = update.avatarRevision ?: currentUi.avatarRevision,
            profileBusy = false,
            profileNotice = null,
            savedAccounts = accountSummaries(),
        )
    }

    private fun mutateProfile(operation: suspend (ActiveConnection) -> ProfileUpdate) {
        val connection = current() ?: return
        if (mutable.value.profileBusy) return
        val ticket = identity
        val revision = ++profileRevision
        mutable.value = mutable.value.copy(profileBusy = true, profileNotice = null)
        profileJob?.cancel()
        profileJob = viewModelScope.launch {
            try {
                val update = withContext(ioDispatcher) { operation(connection) }
                if (!valid(ticket, connection) || revision != profileRevision) return@launch
                applyProfileUpdate(update)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (valid(ticket, connection) && revision == profileRevision) {
                    mutable.value = mutable.value.copy(profileBusy = false, profileNotice = notice(error))
                }
            }
        }
    }

    override fun dismissProfileNotice() {
        if (!mutable.value.profileBusy) {
            mutable.value = mutable.value.copy(profileNotice = null)
        }
    }

    override fun updateDisplayName(displayName: String) {
        mutateProfile { connection ->
            val updated = connection.api.updateDisplayName(displayName.trim())
            ProfileUpdate(displayName = updated.displayName)
        }
    }

    override fun setAccountAvatar(sourceUri: String) {
        val processor = coverImageProcessor ?: return
        val ticket = identity
        mutateProfile { connection ->
            val slot = connection.api.imageSlot("avatar", "avatar")
            currentCoroutineContext().ensureActive()
            val bytes = processor.prepareAvatar(sourceUri)
            currentCoroutineContext().ensureActive()
            if (!valid(ticket, connection)) throw CancellationException()
            var draft: UserImage? = null
            var applied = false
            try {
                draft = connection.api.uploadAvatar(bytes)
                currentCoroutineContext().ensureActive()
                if (!valid(ticket, connection)) throw CancellationException()
                val updated = connection.api.setAvatar(draft.id, slot.revision)
                applied = true
                ProfileUpdate(avatarUrl = updated.url, updateAvatar = true, avatarRevision = updated.revision)
            } finally {
                if (!applied && draft != null) withContext(NonCancellable) {
                    runCatching { connection.api.discardUserImage(draft.id) }
                }
            }
        }
    }

    override fun clearAccountAvatar() {
        mutateProfile { connection ->
            val slot = connection.api.imageSlot("avatar", "avatar")
            val updated = connection.api.setAvatar(null, slot.revision)
            ProfileUpdate(avatarUrl = updated.url, updateAvatar = true, avatarRevision = updated.revision)
        }
    }

    override fun removeQueueItem(index: Int) {
        if (current() == null || index !in player.state.value.queue.indices) return
        beginPlaybackCommand(); playJob?.cancel()
        player.removeQueueItem(index)
    }

    override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val indices = player.state.value.queue.indices
        if (current() == null || fromIndex !in indices || toIndex !in indices || fromIndex == toIndex) return
        beginPlaybackCommand(); playJob?.cancel()
        player.moveQueueItem(fromIndex, toIndex)
    }
    override fun toggleTranslation() = setTranslationEnabled(!translationEnabled)

    override fun setTranslationEnabled(enabled: Boolean) {
        if (current() == null) return
        try {
            translationPreferences?.write(enabled)
            translationEnabled = enabled
            mutable.value = mutable.value.copy(translationEnabled = enabled, translationNotice = null)
        } catch (_: Exception) {
            mutable.value = mutable.value.copy(translationNotice = ClientNotice.STORAGE_FAILED)
        }
    }
    override fun retryLyrics() = lyrics.retry()
    override fun requestLyricTranslation() = lyrics.requestTranslation()
    override fun setVolume(volume: Float) {
        if (!volume.isFinite()) return
        val gain = volume.coerceIn(0f, 1f)
        preferredVolume = gain
        player.setVolume(gain)
        mutable.value = mutable.value.copy(playback = mutable.value.playback.copy(volume = gain))
        val revision = ++volumeSaveRevision
        volumeSaveJob?.cancel()
        volumeSaveJob = viewModelScope.launch {
            delay(180)
            try {
                withContext(ioDispatcher) { volumeMutex.withLock {
                    if (revision == volumeSaveRevision) translationPreferences?.writeVolume(gain)
                } }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (revision == volumeSaveRevision && current() != null)
                    mutable.value = mutable.value.copy(notice = ClientNotice.STORAGE_FAILED)
            }
        }
    }
    override fun togglePlay() = player.togglePlay()
    override fun next() { beginPlaybackCommand(); playJob?.cancel(); player.next() }
    override fun previous() { beginPlaybackCommand(); playJob?.cancel(); player.previous() }
    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    override fun toggleShuffle() = player.toggleShuffle()
    override fun cycleRepeat() = player.cycleRepeat()
    override fun setPlaybackMode(shuffle: Boolean, repeatMode: Int, currentShuffle: Boolean, currentRepeatMode: Int) =
        player.setPlaybackMode(shuffle, repeatMode)
    override fun selectQueueItem(index: Int) { beginPlaybackCommand(); playJob?.cancel(); player.select(index) }
    override fun retryPlayback() = player.retry()
    fun saveNavigationState() { current()?.let { navigation.save(it, mutable.value) } }

    fun setUiVisible(visible: Boolean) {
        player.setUiVisible(visible)
        if (visible && current() != null && discoveryCheckedDate != today()) loadDiscovery(false)
    }
    override fun onCleared() {
        appUpdateJob?.cancel()
        if (ownsLyricsCoordinator) lyrics.clear()
        player.release()
    }

    private companion object {
        const val KEY_GOOGLE_ORIGIN = "ft.pending_google_origin"
        const val KEY_GOOGLE_VERIFIER = "ft.pending_google_verifier"
        const val KEY_GOOGLE_STARTED_AT = "ft.pending_google_started_at"
        const val PENDING_GOOGLE_TTL_MS = 15 * 60 * 1000L
        val languages = setOf("all", "zh", "en", "ja", "ko", "instrumental", "ru", "es", "fr", "de",
            "sv", "vi", "yue", "it", "th", "pt", "other")
    }
}

private fun limitNotice(code: String?): ClientNotice? = when (code) {
    "PLAYLIST_LIMIT_REACHED" -> ClientNotice.PLAYLIST_LIMIT
    "PLAYLIST_SONG_LIMIT_REACHED" -> ClientNotice.PLAYLIST_SONG_LIMIT
    "image_upload_limit" -> ClientNotice.IMAGE_UPLOAD_LIMIT
    "image_too_large" -> ClientNotice.IMAGE_TOO_LARGE
    else -> null
}

private fun notice(error: Exception): ClientNotice {
    limitNotice((error as? ApiFailure)?.code)?.let { return it }
    return when ((error as? ApiFailure)?.failure) {
        Failure.INVALID_URL -> ClientNotice.INVALID_URL
        Failure.NOT_READY -> ClientNotice.NOT_READY
        Failure.NETWORK -> ClientNotice.NETWORK
        Failure.INVALID_LOGIN -> ClientNotice.INVALID_LOGIN
        Failure.SESSION_EXPIRED, Failure.RETIRED -> ClientNotice.SESSION_EXPIRED
        Failure.RATE_LIMITED -> ClientNotice.RATE_LIMITED
        Failure.INVALID_RESPONSE -> ClientNotice.INVALID_RESPONSE
        Failure.STORAGE -> ClientNotice.STORAGE_FAILED
        Failure.FORBIDDEN -> ClientNotice.FORBIDDEN
        Failure.GOOGLE_UNAVAILABLE -> ClientNotice.GOOGLE_UNAVAILABLE
        Failure.GOOGLE_FAILED -> ClientNotice.GOOGLE_FAILED
        else -> ClientNotice.REQUEST_FAILED
    }
}
