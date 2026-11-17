package com.flaretune.client

import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.disk.DiskCache
import com.flaretune.client.data.*
import com.flaretune.client.model.*
import com.flaretune.client.playback.*
import com.flaretune.client.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AccountSettingsStateTest {
    @Test fun addCancelSwitchInvalidAndRemoveKeepOtherSavedAccountsAndResetPrivateState() = runTest {
        val f = Fixture(this)
        try {
            advanceUntilIdle()
            val firstNavigation = f.model.state.value.navigationIdentity
            f.model.setSleepTimer(5)
            f.model.beginAddAccount()
            assertEquals(ClientScreen.CONNECT, f.model.state.value.screen)
            assertEquals(2, f.model.state.value.savedAccounts.size)
            assertTrue(f.model.state.value.addingAccount)
            assertEquals(SleepTimerMode.OFF, f.model.sleepTimerFlow.value.mode)
            assertTrue(f.player.state.value.queue.isEmpty())
            f.model.cancelAddAccount()
            advanceUntilIdle()
            assertEquals("A", f.model.state.value.accountName)
            assertFalse(f.model.state.value.addingAccount)

            val beforeSwitchNonce = f.model.state.value.splashNonce
            f.model.switchAccount(f.connections.keyB)
            assertEquals(ClientScreen.RESTORING, f.model.state.value.screen)
            assertTrue(f.model.state.value.busy)
            assertTrue(f.model.state.value.splashNonce > beforeSwitchNonce)
            assertTrue(f.model.state.value.songs.isEmpty())
            assertNull(f.model.state.value.navigationIdentity)
            advanceUntilIdle()
            assertEquals("B", f.model.state.value.accountName)
            assertFalse(f.model.state.value.busy)
            assertNotEquals(firstNavigation, f.model.state.value.navigationIdentity)
            assertEquals(listOf("B"), f.model.state.value.songs.map { it.id })
            assertEquals(f.connections.keyB, f.model.state.value.savedAccounts.single { it.active }.accountKey)

            f.connections.expired = f.connections.keyA
            val beforeExpiredNonce = f.model.state.value.splashNonce
            f.model.switchAccount(f.connections.keyA)
            assertEquals(ClientScreen.RESTORING, f.model.state.value.screen)
            assertTrue(f.model.state.value.busy)
            assertTrue(f.model.state.value.splashNonce > beforeExpiredNonce)
            advanceUntilIdle()
            assertEquals(ClientScreen.CONNECT, f.model.state.value.screen)
            assertFalse(f.model.state.value.busy)
            assertEquals("a", f.model.state.value.username)
            assertEquals(ClientNotice.SESSION_EXPIRED, f.model.state.value.notice)
            assertTrue(f.model.state.value.savedAccounts.single { it.accountKey == f.connections.keyA }.needsReauth)
            assertEquals(2, f.model.state.value.savedAccounts.size)
            f.model.switchAccount(f.connections.keyB)
            advanceUntilIdle()
            f.model.removeSavedAccount(f.connections.keyB)
            advanceUntilIdle()
            assertEquals(ClientScreen.CONNECT, f.model.state.value.screen)
            assertEquals(listOf(f.connections.keyA), f.model.state.value.savedAccounts.map { it.accountKey })
        } finally { f.close() }
    }

    @Test fun newestSwitchRejectsLatePlayerConnectionAndBufferedExpiryFromOldIdentity() = runTest {
        val f = Fixture(this)
        try {
            advanceUntilIdle()
            f.player.connectionGate = CompletableDeferred()
            f.model.switchAccount(f.connections.keyB)
            runCurrent()
            assertTrue(f.model.state.value.busy.not()) // enter has exposed B while its controller connects
            f.model.switchAccount(f.connections.keyA)
            runCurrent()
            f.player.connectionGate!!.complete(Unit)
            advanceUntilIdle()
            assertEquals("A", f.model.state.value.accountName)
            assertEquals(listOf("A"), f.model.state.value.songs.map { it.id })
            f.connections.invalidations.emit(ClientNotice.SESSION_EXPIRED)
            runCurrent()
            assertEquals(ClientScreen.LIBRARY, f.model.state.value.screen)
            assertEquals("A", f.model.state.value.accountName)
        } finally { f.close() }
    }

    @Test fun oldRemoveFallbackCannotRetireANewerSwitchOrUndoLogout() = runTest {
        for (newerSwitch in listOf(true, false)) {
            val f = Fixture(this)
            try {
                advanceUntilIdle()
                f.connections.removeGate = CompletableDeferred()
                f.model.removeSavedAccount(f.connections.keyA)
                runCurrent()
                if (newerSwitch) f.model.switchAccount(f.connections.keyB) else f.model.logout()
                runCurrent()
                val accepted = f.connections.current
                val switches = f.connections.switchCalls
                f.connections.removeGate!!.complete(Unit)
                advanceUntilIdle()
                assertSame(accepted, f.connections.current)
                assertEquals(switches, f.connections.switchCalls)
                assertEquals(if (newerSwitch) ClientScreen.LIBRARY else ClientScreen.CONNECT, f.model.state.value.screen)
            } finally { f.close() }
        }
    }

    @Test fun timerRunsWithoutUiStateTicksAndSwitchCancelsBeforeNewIdentityLoads() = runTest {
        val f = Fixture(this)
        try {
            advanceUntilIdle()
            f.player.play(listOf(accountSong("A")), 0)
            runCurrent()
            var emissions = 0
            val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                f.model.state.collect { emissions++ }
            }
            val before = emissions
            f.model.setSleepTimer(1)
            runCurrent()
            val deadline = f.model.sleepTimerFlow.value.endElapsedRealtimeMs
            assertEquals(60_000L, deadline)
            repeat(59) {
                advanceTimeBy(1_000)
                runCurrent()
                assertEquals(before, emissions)
                assertEquals(deadline, f.model.sleepTimerFlow.value.endElapsedRealtimeMs)
            }
            f.model.switchAccount(f.connections.keyB)
            runCurrent()
            assertEquals(SleepTimerMode.OFF, f.model.sleepTimerFlow.value.mode)
            assertTrue(f.player.state.value.queue.isEmpty())
            advanceTimeBy(2_000)
            runCurrent()
            assertEquals("B", f.model.state.value.accountName)
            collector.cancel()
        } finally { f.close() }
    }

    @Test fun sleepTimerCompletionPausesOnceAndRestoresVolumeWithoutFadeUiTicks() = runTest {
        val f = Fixture(this)
        try {
            advanceUntilIdle()
            f.player.play(listOf(accountSong("A")), 0)
            runCurrent()
            var emissions = 0
            val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                f.model.state.collect { emissions++ }
            }
            val before = emissions
            f.model.setSleepTimer(1)
            advanceTimeBy(60_599)
            runCurrent()
            assertEquals(before, emissions)
            advanceTimeBy(1)
            runCurrent()
            assertFalse(f.player.state.value.playing)
            assertEquals(.7f, f.player.gain, .0001f)
            assertEquals(before + 1, emissions)
            assertEquals(SleepTimerMode.OFF, f.model.sleepTimerFlow.value.mode)
            collector.cancel()
        } finally { f.close() }
    }

    @Test fun logoutRemovesOnlyActiveAndKeepsPreferenceAndOtherAccounts() = runTest {
        val f = Fixture(this)
        try {
            advanceUntilIdle()
            f.model.setKeepScreenOnEnabled(false)
            advanceUntilIdle()
            assertFalse(f.model.state.value.keepScreenOnEnabled)
            assertFalse(f.preferences.readKeepScreenOn())
            f.model.logout()
            advanceUntilIdle()
            assertEquals(ClientScreen.CONNECT, f.model.state.value.screen)
            assertEquals(listOf(f.connections.keyB), f.model.state.value.savedAccounts.map { it.accountKey })
            assertFalse(f.model.state.value.keepScreenOnEnabled)
            f.model.switchAccount(f.connections.keyB)
            advanceUntilIdle()
            assertEquals("B", f.model.state.value.accountName)
            assertFalse(f.model.state.value.keepScreenOnEnabled)
            f.preferences.failWrite = true
            f.model.setKeepScreenOnEnabled(true)
            advanceUntilIdle()
            assertFalse(f.model.state.value.keepScreenOnEnabled)
            assertEquals(ClientNotice.STORAGE_FAILED, f.model.state.value.notice)
        } finally { f.close() }
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    @Test fun cacheCleanupReportsRealDiskSuccessFailureAndUnavailableAndUsesIdentityPartition() = runTest {
        val f = Fixture(this)
        val context = RuntimeEnvironment.getApplication()
        val directory = context.cacheDir.resolve("settings-cache-test").apply { mkdirs() }
        val disk = DiskCache.Builder().directory(directory).build()
        val loader = ImageLoader.Builder(context).diskCache(disk).build()
        try {
            advanceUntilIdle()
            f.model.clearImageCache()
            assertEquals(ImageCacheClearResult.UNAVAILABLE, f.model.state.value.imageCacheClearResult)
            disk.openEditor("cover")!!.run { data.toFile().writeText("private-art"); commit() }
            assertNotNull(disk.openSnapshot("cover")?.also { it.close() })
            f.player.loader = loader
            f.model.clearImageCache()
            advanceUntilIdle()
            assertEquals(ImageCacheClearResult.CLEARED, f.model.state.value.imageCacheClearResult)
            assertNull(disk.openSnapshot("cover"))
            val failingDisk = object : DiskCache by disk {
                override fun clear() { throw IOException("fixture") }
            }
            f.player.loader = object : ImageLoader by loader { override val diskCache = failingDisk }
            f.model.clearImageCache()
            advanceUntilIdle()
            assertEquals(ImageCacheClearResult.FAILED, f.model.state.value.imageCacheClearResult)
            val a = HistoryIdentity("https://music.example", "A")
            assertEquals(imageCacheIdentity(a), imageCacheIdentity(a.copy(origin = "MUSIC.EXAMPLE:443/")))
            assertNotEquals(imageCacheIdentity(a), imageCacheIdentity(a.copy(accountId = "B")))
            assertNotEquals(imageCacheIdentity(a), imageCacheIdentity(a.copy(origin = "https://other.example")))
            val runtime = PlaybackRuntime(context)
            val cacheA = runtime.diskCacheFor(a)
            val cacheB = runtime.diskCacheFor(a.copy(accountId = "B"))
            assertNotSame(cacheA, cacheB)
            assertSame(cacheA, runtime.diskCacheFor(a))
            assertSame(cacheA, runtime.diskCacheFor(a.copy(origin = "MUSIC.EXAMPLE:443/")))
        } finally { f.player.loader = null; loader.shutdown(); f.close() }
    }

    @Test fun updateDisplayNameSetAndClearAvatarUpdateUiAndSavedAccounts() = runTest {
        val f = Fixture(this, coverImageProcessor = CoverImageProcessor { byteArrayOf(1, 2, 3, 4) })
        try {
            advanceUntilIdle()
            assertEquals("A", f.model.state.value.accountName)
            assertNull(f.model.state.value.avatarUrl)

            f.model.updateDisplayName("AliceDisplay")
            advanceUntilIdle()
            assertEquals("AliceDisplay", f.model.state.value.accountName)
            assertNull(f.model.state.value.profileNotice)
            assertEquals("AliceDisplay", f.model.state.value.savedAccounts.first { it.active }.displayName)

            f.model.setAccountAvatar("content://media/external/images/1")
            advanceUntilIdle()
            val expectedAvatarUrl = "https://music.example/api/account/images/avatar-1"
            assertEquals(expectedAvatarUrl, f.model.state.value.avatarUrl)
            assertEquals(1L, f.model.state.value.avatarRevision)
            assertEquals(expectedAvatarUrl, f.model.state.value.savedAccounts.first { it.active }.avatarUrl)

            f.model.clearAccountAvatar()
            advanceUntilIdle()
            assertNull(f.model.state.value.avatarUrl)
            assertEquals(2L, f.model.state.value.avatarRevision)
            assertNull(f.model.state.value.savedAccounts.first { it.active }.avatarUrl)
        } finally { f.close() }
    }

    @Test fun themeModePersistsAcrossSwitchAndLogoutAndSurfacesStorageError() = runTest {        val f = Fixture(this)
        try {
            advanceUntilIdle()
            assertEquals(AppThemeMode.SYSTEM, f.model.state.value.themeMode)
            f.model.setThemeMode(AppThemeMode.DARK)
            advanceUntilIdle()
            assertEquals(AppThemeMode.DARK, f.model.state.value.themeMode)
            assertEquals(AppThemeMode.DARK, f.preferences.themeMode)

            f.model.setThemeMode(AppThemeMode.LIGHT)
            advanceUntilIdle()
            f.model.switchAccount(f.connections.keyB)
            advanceUntilIdle()
            assertEquals(AppThemeMode.LIGHT, f.model.state.value.themeMode)

            f.model.logout()
            advanceUntilIdle()
            assertEquals(AppThemeMode.LIGHT, f.model.state.value.themeMode)

            f.preferences.failWrite = true
            f.model.setThemeMode(AppThemeMode.DARK)
            advanceUntilIdle()
            assertEquals(AppThemeMode.LIGHT, f.model.state.value.themeMode)
            assertEquals(ClientNotice.STORAGE_FAILED, f.model.state.value.notice)
        } finally { f.close() }
    }

    @Test fun profileUpdatePreservesConnectionIdentityAndCatalogLoadsEvenWhileConnectPlayerSuspends() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val origin = "https://music.example"
        val accountKey = SavedAccount.key(origin, "acct-1")
        val store = MemorySessions().apply {
            lastOrigin = origin
            val cookie = "$SESSION_COOKIE_NAME=${"A".repeat(43)}; Path=/; HttpOnly; Secure; Max-Age=3600"
            writeAccounts(SavedAccountsSnapshot(
                listOf(SavedAccount(accountKey, origin, "acct-1", "alice", "Alice", cookie, 10L, false)),
                accountKey,
            ))
        }
        val jsonMedia = "application/json; charset=utf-8".toMediaType()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url
            val path = url.encodedPath
            val body = when {
                path == "/api/instance/status" -> """{"state":"ready"}"""
                path == "/api/auth/session" -> """{"authenticated":true,"mustChangePassword":false,"csrfToken":"csrf-1","user":{"accountId":"acct-1","username":"alice","displayName":"Alice"}}"""
                path == "/api/account/images/slots/avatar/avatar" -> """{"imageId":"avatar-1","url":"/api/account/images/avatar-1","revision":5}"""
                path == "/api/account/profile" -> """{"user":{"accountId":"acct-1","username":"alice","displayName":"Alice Renamed","avatar":null}}"""
                path == "/api/account/playlists" -> """{"ok":true,"data":{"playlists":[{"id":"fav-1","name":"Favorites","kind":"favorite","songCount":1,"revision":1}]}}"""
                path == "/api/account/playlists/fav-1" -> """{"ok":true,"data":{"playlist":{"id":"fav-1","name":"Favorites","kind":"favorite","songCount":1,"revision":1,"songs":[{"id":"song-1","title":"Song One","artist":"Artist","album":"Album","duration":180,"audio_url":"/media/song-1","sortOrder":0,"addedAt":1}]}}}"""
                path == "/api/account/playlist-shelf" -> """{"ok":true,"data":{"shelf":{"revision":1,"items":[{"kind":"member","id":"fav-1"}]}}}"""
                path == "/api/songs" && url.queryParameter("counts") == "language" -> """{"code":200,"data":{}}"""
                path == "/api/songs" -> """{"code":200,"data":{"songs":[{"id":"song-1","title":"Song One","artist":"Artist","album":"Album","duration":180,"audio_url":"/media/song-1"}],"hasMore":false,"nextOffset":1}}"""
                path == "/api/songs/random" -> """{"code":200,"data":{"songs":[]}}"""
                path == "/api/albums" -> """{"code":200,"data":{"albums":[],"hasMore":false}}"""
                path == "/api/stats/plays" -> if (url.queryParameter("view") == "summary") {
                    """{"songs":[],"view":"summary"}"""
                } else {
                    """{"songs":[],"playCounts":{},"totalPlays":0,"totalUniqueSongs":0}"""
                }
                else -> """{}"""
            }
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody(jsonMedia))
                .build()
        }.build()
        val manager = ConnectionManager(store, client, dispatcher)
        val player = AccountPlayer(this).apply { connectionGate = CompletableDeferred() }
        val model = ClientViewModel(manager, player, dispatcher)
        try {
            runCurrent()
            val restoredConnection = manager.current!!
            manager.updateActiveAccountProfile(
                displayName = "Alice Mid",
                avatarUrl = "$origin/api/account/images/avatar-stale",
                updateAvatar = true,
                avatarRevision = 1L,
            )
            assertSame(restoredConnection, manager.current)
            player.connectionGate!!.complete(Unit)
            advanceUntilIdle()
            assertSame(restoredConnection, manager.current)
            assertEquals(ClientScreen.LIBRARY, model.state.value.screen)
            assertEquals(listOf("song-1"), model.state.value.songs.map { it.id })
            assertEquals(listOf("fav-1"), model.state.value.playlists.map { it.id })
            assertEquals(listOf("song-1"), model.state.value.favoriteSongs.map { it.id })
            assertEquals("$origin/api/account/images/avatar-1", model.state.value.avatarUrl)
            assertEquals(5L, model.state.value.avatarRevision)

            model.updateDisplayName("Alice Renamed")
            advanceUntilIdle()
            assertSame(restoredConnection, manager.current)
            assertEquals("Alice Renamed", restoredConnection.auth.account.displayName)
            assertEquals("Alice Renamed", model.state.value.accountName)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }

    @Test fun removeSavedAccountClearsDeletedAccountPlaylistPreferencesOnly() = runTest {
        val backing = object : HistoryStorage {
            val map = mutableMapOf<String, String>()
            override fun read(key: String): String? = map[key]
            override fun write(key: String, value: String) { map[key] = value }
            override fun remove(key: String) { map.remove(key) }
        }
        val playlistPrefs = JsonPlaylistPreferencesStore(backing)
        val identityA = HistoryIdentity("https://music.example", "A")
        val identityB = HistoryIdentity("https://music.example", "B")
        playlistPrefs.writeSortMode(identityA, PlaylistSortMode.NAME)
        playlistPrefs.writeSortMode(identityB, PlaylistSortMode.TRACK_COUNT)

        val f = Fixture(this, playlistPreferences = playlistPrefs)
        try {
            advanceUntilIdle()
            assertEquals(PlaylistSortMode.NAME, f.model.state.value.playlistSortMode)

            // 1. Removing inactive account B clears B's preference while keeping active A's preference.
            f.model.removeSavedAccount(f.connections.keyB)
            advanceUntilIdle()
            assertEquals(PlaylistSortMode.CUSTOM, playlistPrefs.readSortMode(identityB))
            assertEquals(PlaylistSortMode.NAME, playlistPrefs.readSortMode(identityA))

            // 2. Removing active account A clears A's preference too.
            f.model.removeSavedAccount(f.connections.keyA)
            advanceUntilIdle()
            assertEquals(PlaylistSortMode.CUSTOM, playlistPrefs.readSortMode(identityA))
        } finally { f.close() }
    }

    @Test fun bluetoothLyricsModePersistsSyncsToGatewayAndRollsBackOnStorageFailure() = runTest {
        val f = Fixture(this)
        try {
            advanceUntilIdle()
            assertEquals(BluetoothLyricsMode.OFF, f.model.state.value.bluetoothLyricsMode)
            assertEquals(BluetoothLyricsMode.OFF, f.player.bluetoothLyricsModeSet)

            f.model.setBluetoothLyricsMode(BluetoothLyricsMode.BLUETOOTH_AUDIO)
            advanceUntilIdle()
            assertEquals(BluetoothLyricsMode.BLUETOOTH_AUDIO, f.model.state.value.bluetoothLyricsMode)
            assertEquals(BluetoothLyricsMode.BLUETOOTH_AUDIO, f.preferences.bluetoothLyricsMode)
            assertEquals(BluetoothLyricsMode.BLUETOOTH_AUDIO, f.player.bluetoothLyricsModeSet)

            f.preferences.failWrite = true
            f.model.setBluetoothLyricsMode(BluetoothLyricsMode.ALWAYS)
            advanceUntilIdle()
            assertEquals(BluetoothLyricsMode.BLUETOOTH_AUDIO, f.model.state.value.bluetoothLyricsMode)
            assertEquals(BluetoothLyricsMode.BLUETOOTH_AUDIO, f.player.bluetoothLyricsModeSet)
            assertEquals(ClientNotice.STORAGE_FAILED, f.model.state.value.notice)
        } finally { f.close() }
    }

    @Test fun appLanguagePersistsAcrossSwitchAndLogoutAndSurfacesStorageError() = runTest {
        val f = Fixture(this)
        try {
            advanceUntilIdle()
            assertEquals(AppLanguage.SYSTEM, f.model.state.value.appLanguage)
            assertEquals(0L, f.model.state.value.languageChangeToken)

            f.model.setAppLanguage(AppLanguage.JA)
            advanceUntilIdle()
            assertEquals(AppLanguage.JA, f.model.state.value.appLanguage)
            assertEquals(AppLanguage.JA, f.languagePreferences.language)
            assertEquals(1L, f.model.state.value.languageChangeToken)

            f.model.setAppLanguage(AppLanguage.ZH_HANT)
            advanceUntilIdle()
            assertEquals(2L, f.model.state.value.languageChangeToken)

            f.model.switchAccount(f.connections.keyB)
            advanceUntilIdle()
            assertEquals(AppLanguage.ZH_HANT, f.model.state.value.appLanguage)
            assertEquals(2L, f.model.state.value.languageChangeToken)

            f.model.logout()
            advanceUntilIdle()
            assertEquals(AppLanguage.ZH_HANT, f.model.state.value.appLanguage)

            f.languagePreferences.failWrite = true
            f.model.setAppLanguage(AppLanguage.KO)
            advanceUntilIdle()
            assertEquals(AppLanguage.ZH_HANT, f.model.state.value.appLanguage)
            assertEquals(2L, f.model.state.value.languageChangeToken)
            assertEquals(ClientNotice.STORAGE_FAILED, f.model.state.value.notice)
        } finally { f.close() }
    }

    private class Fixture(
        scope: TestScope,
        coverImageProcessor: CoverImageProcessor? = null,
        playlistPreferences: PlaylistPreferencesStore? = null,
    ) {
        private val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val connections = AccountConnections()
        val player = AccountPlayer(scope)
        val preferences = DevicePreferences()
        val languagePreferences = DeviceAppLanguagePreferences()
        val model: ClientViewModel
        init {
            Dispatchers.setMain(dispatcher)
            model = ClientViewModel(
                connections,
                player,
                dispatcher,
                translationPreferences = preferences,
                coverImageProcessor = coverImageProcessor,
                playlistPreferences = playlistPreferences,
                appLanguagePreferences = languagePreferences,
            )
        }
        fun close() { model.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
}

private fun accountSong(id: String) = Song(id, id, audioUrl = "https://music.example/media/$id", language = "instrumental")

private class DevicePreferences : TranslationPreferences {
    var keep = true
    var themeMode = AppThemeMode.SYSTEM
    var bluetoothLyricsMode = BluetoothLyricsMode.OFF
    var failWrite = false
    override fun read() = false
    override fun write(enabled: Boolean) {}
    override fun readKeepScreenOn() = keep
    override fun writeKeepScreenOn(enabled: Boolean) { if (failWrite) throw ApiFailure(Failure.STORAGE); keep = enabled }
    override fun readThemeMode() = themeMode
    override fun writeThemeMode(mode: AppThemeMode) { if (failWrite) throw ApiFailure(Failure.STORAGE); themeMode = mode }
    override fun readBluetoothLyricsMode() = bluetoothLyricsMode
    override fun writeBluetoothLyricsMode(mode: BluetoothLyricsMode) { if (failWrite) throw ApiFailure(Failure.STORAGE); bluetoothLyricsMode = mode }
}

private class DeviceAppLanguagePreferences : AppLanguagePreferences {
    var language = AppLanguage.SYSTEM
    var failWrite = false
    override fun read() = language
    override fun write(language: AppLanguage) {
        if (failWrite) throw ApiFailure(Failure.STORAGE)
        this.language = language
    }
}

private class AccountConnections : Connections {
    val keyA = SavedAccount.key("https://music.example", "A")
    val keyB = SavedAccount.key("https://music.example", "B")
    private val available = linkedMapOf(keyA to "A", keyB to "B")
    private val avatars = mutableMapOf<String, String?>()
    private val avatarRevisions = mutableMapOf<String, Long>()
    private var selected = keyA
    private var addReturn: String? = null
    private val reauth = mutableSetOf<String>()
    var expired: String? = null
    var removeGate: CompletableDeferred<Unit>? = null
    var switchCalls = 0
    override var current: ActiveConnection? = connection("A")
    override val lastOrigin = "https://music.example"
    override val invalidations = MutableSharedFlow<ClientNotice>(extraBufferCapacity = 8)
    override val selectedAccountKey get() = selected
    override val savedAccounts get() = available.map { (key, name) ->
        SavedAccountSummary(
            key, lastOrigin, key.substringAfter('|'),
            key.substringAfter('|').lowercase(), name,
            activeAccountKey == key, key in reauth, avatars[key],
        )
    }
    override suspend fun restore() = current
    override suspend fun login(address: String, username: String, password: String) = connection(username.uppercase()).also { current = it }
    override fun beginAddAccount() {
        addReturn = activeAccountKey
        current?.network?.retire()
        current = null
    }
    override suspend fun cancelAddAccount() = addReturn?.let { switchAccount(it) }
    override suspend fun switchAccount(accountKey: String): ActiveConnection? {
        switchCalls++
        current?.network?.retire()
        current = null
        selected = accountKey
        if (accountKey == expired) { reauth += accountKey; return null }
        val id = accountKey.substringAfter('|')
        return if (accountKey in available) connection(id).also { value -> current = value } else null
    }
    override suspend fun removeSavedAccount(accountKey: String) {
        if (activeAccountKey == accountKey) { current?.network?.retire(); current = null }
        available.remove(accountKey)
        removeGate?.await()
    }
    override fun updateActiveAccountProfile(
        displayName: String?,
        avatarUrl: String?,
        updateAvatar: Boolean,
        avatarRevision: Long?,
    ) {
        val active = current ?: return
        val key = SavedAccount.key(active.network.address.origin, active.auth.account.id)
        if (displayName != null) available[key] = displayName
        if (updateAvatar) avatars[key] = avatarUrl
        if (avatarRevision != null) avatarRevisions[key] = avatarRevision
    }
    override suspend fun logout() { activeAccountKey?.let { removeSavedAccount(it) } }
    override suspend fun changePassword(current: String, replacement: String) {}

    private fun connection(id: String): ActiveConnection {
        val key = SavedAccount.key(lastOriginValue, id)
        val api = object : AccountApi {
            override fun songs(query: String, offset: Int) = SongPage(listOf(accountSong(id)), 50, false)
            override fun favorites() = emptyList<Song>()
            override fun playlists() = emptyList<PlaylistSummary>()
            override fun albums(offset: Int) = AlbumPage(emptyList(), 50, false)
            override fun updateDisplayName(displayName: String): Account {
                val resolved = displayName.trim().ifBlank { id.lowercase() }
                return Account(id, id.lowercase(), resolved, avatars[key], avatarRevisions[key] ?: 0L)
            }
            override fun imageSlot(purpose: String, targetId: String): ImageSlot =
                ImageSlot(if (avatars[key] != null) "avatar-1" else null, avatars[key], avatarRevisions[key] ?: 0L)
            override fun uploadAvatar(bytes: ByteArray): UserImage =
                UserImage("avatar-1", "$lastOriginValue/api/account/images/avatar-1", 512, 512, bytes.size)
            override fun setAvatar(imageId: String?, revision: Long): ImageSlot {
                val url = imageId?.let { "$lastOriginValue/api/account/images/$it" }
                return ImageSlot(imageId, url, revision + 1L)
            }
            override fun changePassword(current: String, replacement: String) {}
            override fun logout() {}
        }
        val name = available[key] ?: id
        return ActiveConnection(SessionNetwork(InstanceAddress.parse(lastOriginValue)), api,
            AuthSession(Account(id, id.lowercase(), name, avatars[key], avatarRevisions[key] ?: 0L), "csrf", false))
    }
    private companion object { const val lastOriginValue = "https://music.example" }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class AccountPlayer(scope: TestScope) : PlaybackGateway {
    override val state = MutableStateFlow(PlaybackSnapshot())
    var loader: ImageLoader? = null
    override val imageLoader get() = loader
    private val timer = PlaybackSleepTimer()
    override val sleepTimerFlow get() = timer.state
    var gain = .7f
    var bluetoothLyricsModeSet: BluetoothLyricsMode? = null
    var connectionGate: CompletableDeferred<Unit>? = null
    init {
        timer.attach(this, scope.backgroundScope, object : PlaybackSleepTimer.Target {
            override var volume: Float
                get() = this@AccountPlayer.gain
                set(value) { this@AccountPlayer.gain = value }
            override var pauseAtEndOfTrack = false
            override fun pause() { state.value = state.value.copy(playing = false) }
        }, { scope.testScheduler.currentTime })
    }
    override suspend fun connect(connection: ActiveConnection) { connectionGate?.await() }
    override fun play(songs: List<Song>, index: Int) {
        state.value = PlaybackSnapshot(current = songs[index], queue = songs, currentIndex = index, playing = true)
    }
    override fun setSleepTimer(minutes: Int) = timer.setSleepTimer(minutes)
    override fun setSleepTimerEndOfTrack() = timer.setSleepTimerEndOfTrack()
    override fun setSleepTimerFinishCurrentTrack(enabled: Boolean) = timer.setSleepTimerFinishCurrentTrack(enabled)
    override fun cancelSleepTimer() = timer.cancel()
    override fun stopAndClear() { timer.cancel(); state.value = PlaybackSnapshot() }
    override fun setVolume(volume: Float) {}
    override fun togglePlay() {}
    override fun next() {}
    override fun previous() {}
    override fun seekTo(positionMs: Long) {}
    override fun toggleShuffle() {}
    override fun cycleRepeat() {}
    override fun select(index: Int) {}
    override fun retry() {}
    override fun setBluetoothLyricsMode(mode: BluetoothLyricsMode) { bluetoothLyricsModeSet = mode }
    override fun release() {}
}
