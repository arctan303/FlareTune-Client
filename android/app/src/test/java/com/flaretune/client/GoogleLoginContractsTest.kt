package com.flaretune.client

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.flaretune.client.data.*
import com.flaretune.client.model.*
import com.flaretune.client.playback.PlaybackGateway
import com.flaretune.client.ui.ClientScreen
import com.flaretune.client.ui.ConnectStep
import com.flaretune.client.ui.PlaybackSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GoogleLoginContractsTest {
    private lateinit var server: MockWebServer
    private lateinit var base: OkHttpClient
    private val token = "G".repeat(43)
    private val sessionBody = """{"authenticated":true,"mustChangePassword":false,"csrfToken":"csrf-google","user":{"accountId":"google-acc","username":"guser","displayName":"Google User"}}"""

    @Before
    fun setup() {
        val certificate = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1")
            .addSubjectAlternativeName("::1")
            .build()
        val tlsServer = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer().apply { useHttps(tlsServer.sslSocketFactory(), false); start() }
        base = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
    }

    @After
    fun teardown() {
        server.shutdown()
        base.dispatcher.executorService.shutdownNow()
        base.connectionPool.evictAll()
    }

    private fun response(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun pkceBuildsValidS256ChallengeAndNativeStartUri() {
        val verifier = GoogleAuthPkce.generateVerifier()
        assertTrue(verifier.matches(Regex("[A-Za-z0-9_-]{43}")))
        val challenge = GoogleAuthPkce.codeChallenge(verifier)
        val expected = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.UTF_8)),
        )
        assertEquals(expected, challenge)

        val uri = GoogleAuthPkce.buildNativeStartUri("https://music.example.com", challenge)
        assertEquals("https", uri.scheme)
        assertEquals("music.example.com", uri.host)
        assertEquals("/auth/google/native/start", uri.path)
        assertEquals(challenge, uri.getQueryParameter("challenge"))

        assertTrue(GoogleAuthPkce.isCallbackUri(Uri.parse("flaretune://auth/google/callback?code=abc")))
        assertFalse(GoogleAuthPkce.isCallbackUri(Uri.parse("https://music.example.com/auth/google/callback")))
    }

    @Test
    fun connectionManagerCheckInstanceAndGoogleExchangeSendExpectedProtocolAndSession() = runBlocking<Unit> {
        server.enqueue(response("""{"state":"ready"}"""))
        server.enqueue(response("""{"enabled":true,"nativeEnabled":true}"""))
        val store = MemorySessions()
        val manager = ConnectionManager(store, base)
        val probe = manager.checkInstance(server.url("/").toString())
        assertTrue(probe.googleEnabled)
        assertTrue(probe.googleNativeEnabled)

        val statusReq = server.takeRequest(5, TimeUnit.SECONDS)!!
        val googleReq = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/instance/status", statusReq.path)
        assertEquals("/api/auth/google/status?client=android", googleReq.path)

        server.enqueue(response("""{"state":"ready"}"""))
        server.enqueue(
            response(sessionBody).setHeader(
                "Set-Cookie",
                "$SESSION_COOKIE_NAME=$token; Path=/; HttpOnly; Secure; Max-Age=3600; SameSite=Strict",
            ),
        )
        val connection = manager.loginWithGoogle(server.url("/").toString(), "C".repeat(43), "V".repeat(43))
        assertEquals("guser", connection.auth.account.username)
        server.takeRequest(5, TimeUnit.SECONDS)!!
        val exchangeReq = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/auth/google/exchange", exchangeReq.path)
        assertEquals("POST", exchangeReq.method)
        assertEquals(connection.network.address.origin, exchangeReq.getHeader("Origin"))
        assertEquals("FlareTune", exchangeReq.getHeader("X-Requested-With"))
        val json = JSONObject(exchangeReq.body.readUtf8())
        assertEquals("C".repeat(43), json.getString("code"))
        assertEquals("V".repeat(43), json.getString("codeVerifier"))
    }

    @Test
    fun connectionManagerCheckInstanceFallsBackOn404And401AndRejects401Exchange() = runBlocking<Unit> {
        server.enqueue(response("""{"state":"ready"}"""))
        server.enqueue(response("""{"error":"not_found"}""", code = 404))
        val manager = ConnectionManager(MemorySessions(), base)
        val probe404 = manager.checkInstance(server.url("/").toString())
        assertFalse(probe404.googleEnabled)
        assertFalse(probe404.googleNativeEnabled)

        server.enqueue(response("""{"state":"ready"}"""))
        server.enqueue(response("""{"error":"authentication_required"}""", code = 401))
        val probe401 = manager.checkInstance(server.url("/").toString())
        assertFalse(probe401.googleEnabled)
        assertFalse(probe401.googleNativeEnabled)

        server.enqueue(response("""{"state":"ready"}"""))
        server.enqueue(response("""{"error":"google_failed"}""", code = 401))
        val failure = try {
            manager.loginWithGoogle(server.url("/").toString(), "C".repeat(43), "V".repeat(43))
            null
        } catch (err: ApiFailure) {
            err
        }
        assertNotNull(failure)
        assertEquals(Failure.GOOGLE_FAILED, failure!!.failure)
    }

    @Test
    fun viewModelTwoStepLoginAndSavedStateHandleGoogleCallbackSurviveRecreation() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val connections = FakeGoogleConnections()
        val savedState = SavedStateHandle()
        val model1 = ClientViewModel(connections, FakeGooglePlayback(), dispatcher, savedStateHandle = savedState)
        try {
            advanceUntilIdle()
            assertEquals(ClientScreen.CONNECT, model1.state.value.screen)
            assertEquals(ConnectStep.INSTANCE, model1.state.value.connectStep)

            model1.checkInstance("http://insecure.example")
            assertEquals(ClientNotice.INVALID_URL, model1.state.value.notice)
            assertEquals(ConnectStep.INSTANCE, model1.state.value.connectStep)

            model1.checkInstance("https://music.example.com")
            advanceUntilIdle()
            assertEquals(ConnectStep.CREDENTIALS, model1.state.value.connectStep)
            assertEquals("https://music.example.com", model1.state.value.instanceUrl)
            assertTrue(model1.state.value.googleLoginEnabled)
            assertTrue(model1.state.value.googleNativeLoginEnabled)

            model1.startGoogleLogin()
            val launchUrl = model1.state.value.googleAuthLaunchUrl
            assertNotNull(launchUrl)
            assertTrue(launchUrl!!.startsWith("https://music.example.com/auth/google/native/start?challenge="))
            model1.consumeGoogleAuthLaunch()

            // Simulate process death / ViewModel recreation while Custom Tabs is open.
            model1.viewModelScope.cancel()
            val model2 = ClientViewModel(connections, FakeGooglePlayback(), dispatcher, savedStateHandle = savedState)
            try {
                advanceUntilIdle()
                model2.handleGoogleAuthCallback(code = "H".repeat(43), error = null)
                advanceUntilIdle()
                assertEquals(ClientScreen.LIBRARY, model2.state.value.screen)
                assertEquals("guser", model2.state.value.username)
                assertEquals("H".repeat(43), connections.lastGoogleCode)
            } finally {
                model2.viewModelScope.cancel()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun viewModelHandlesGoogleErrorsAndForegroundResumeDismissalCleanly() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val connections = FakeGoogleConnections()
        val model = ClientViewModel(connections, FakeGooglePlayback(), dispatcher, savedStateHandle = SavedStateHandle())
        try {
            advanceUntilIdle()
            model.checkInstance("https://music.example.com")
            advanceUntilIdle()

            model.startGoogleLogin()
            model.consumeGoogleAuthLaunch()
            assertTrue(model.state.value.busy)

            // User closes Custom Tabs manually without redirect -> foreground resume resets busy state.
            model.onForegroundResume()
            assertFalse(model.state.value.busy)

            // Explicit unbound / cancelled redirect notices.
            model.startGoogleLogin()
            model.consumeGoogleAuthLaunch()
            model.handleGoogleAuthCallback(code = null, error = "unbound")
            assertFalse(model.state.value.busy)
            assertEquals(ClientNotice.GOOGLE_UNBOUND, model.state.value.notice)

            model.startGoogleLogin()
            model.consumeGoogleAuthLaunch()
            model.handleGoogleAuthCallback(code = null, error = "cancelled")
            assertEquals(ClientNotice.GOOGLE_CANCELLED, model.state.value.notice)

            model.editInstanceAddress()
            assertEquals(ConnectStep.INSTANCE, model.state.value.connectStep)
            assertFalse(model.state.value.googleLoginEnabled)
            assertNull(model.state.value.notice)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun unsolicitedCallbackWhileSignedInIsIgnoredAndProcessDeathErrorDoesNotRaceWithInitRestore() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val connections = FakeGoogleConnections().apply {
            restorableConnection = createActiveConnection("https://saved.example.com", "saved-user")
        }
        val signedInModel = ClientViewModel(connections, FakeGooglePlayback(), dispatcher, savedStateHandle = SavedStateHandle())
        try {
            advanceUntilIdle()
            assertEquals(ClientScreen.LIBRARY, signedInModel.state.value.screen)

            // Unsolicited deep links while already signed in must not disrupt LIBRARY.
            signedInModel.handleGoogleAuthCallback(code = null, error = "cancelled")
            signedInModel.handleGoogleAuthCallback(code = "X".repeat(43), error = null)
            advanceUntilIdle()
            assertEquals(ClientScreen.LIBRARY, signedInModel.state.value.screen)
            assertEquals("saved-user", signedInModel.state.value.username)
        } finally {
            signedInModel.viewModelScope.cancel()
        }

        // Process death while adding an account / signing in via Custom Tabs, followed by error callback before init restore runs.
        connections.restorableConnection = null
        val savedState = SavedStateHandle()
        val beforeDeath = ClientViewModel(connections, FakeGooglePlayback(), dispatcher, savedStateHandle = savedState)
        advanceUntilIdle()
        beforeDeath.checkInstance("https://music.example.com")
        advanceUntilIdle()
        beforeDeath.startGoogleLogin()
        beforeDeath.consumeGoogleAuthLaunch()
        beforeDeath.viewModelScope.cancel()

        connections.restorableConnection = createActiveConnection("https://saved.example.com", "saved-user")
        connections.restoreCalls = 0
        val afterDeath = ClientViewModel(connections, FakeGooglePlayback(), dispatcher, savedStateHandle = savedState)
        try {
            // Delivered synchronously from MainActivity.onCreate before init coroutine runs.
            afterDeath.handleGoogleAuthCallback(code = null, error = "unbound")
            advanceUntilIdle()
            assertEquals(0, connections.restoreCalls)
            assertEquals(ClientScreen.CONNECT, afterDeath.state.value.screen)
            assertEquals(ConnectStep.CREDENTIALS, afterDeath.state.value.connectStep)
            assertEquals("https://music.example.com", afterDeath.state.value.instanceUrl)
            assertEquals(ClientNotice.GOOGLE_UNBOUND, afterDeath.state.value.notice)
            assertTrue(afterDeath.state.value.googleLoginEnabled)
            assertTrue(afterDeath.state.value.googleNativeLoginEnabled)
        } finally {
            afterDeath.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private fun createActiveConnection(origin: String, username: String): ActiveConnection {
        val normalized = InstanceAddress.parse(origin)
        val network = SessionNetwork(normalized)
        val api = object : AccountApi {
            override fun songs(query: String, offset: Int) = SongPage(emptyList(), 0, false)
            override fun favorites() = emptyList<Song>()
            override fun albums(offset: Int) = AlbumPage(emptyList(), 0, false)
            override fun album(id: String) = AlbumDetail(Album(id, "A", "B"), emptyList())
            override fun changePassword(current: String, replacement: String) {}
            override fun logout() {}
        }
        return ActiveConnection(network, api, AuthSession(Account("acc-$username", username, username), "csrf", false))
    }

    private class FakeGoogleConnections : Connections {
        override var current: ActiveConnection? = null
        override val lastOrigin: String = ""
        override val invalidations = MutableSharedFlow<ClientNotice>()
        var lastGoogleCode: String? = null
        var restorableConnection: ActiveConnection? = null
        var restoreCalls: Int = 0

        override suspend fun restore(): ActiveConnection? {
            restoreCalls++
            return restorableConnection?.also { current = it }
        }

        override suspend fun checkInstance(address: String): InstanceProbeResult =
            InstanceProbeResult(
                origin = InstanceAddress.parse(address).origin,
                googleEnabled = true,
                googleNativeEnabled = true,
            )

        override suspend fun login(address: String, username: String, password: String): ActiveConnection =
            throw ApiFailure(Failure.INVALID_LOGIN)

        override suspend fun loginWithGoogle(address: String, code: String, codeVerifier: String): ActiveConnection {
            lastGoogleCode = code
            val normalized = InstanceAddress.parse(address)
            val network = SessionNetwork(normalized)
            val api = object : AccountApi {
                override fun songs(query: String, offset: Int) = SongPage(emptyList(), 0, false)
                override fun favorites() = emptyList<Song>()
                override fun albums(offset: Int) = AlbumPage(emptyList(), 0, false)
                override fun album(id: String) = AlbumDetail(Album(id, "A", "B"), emptyList())
                override fun changePassword(current: String, replacement: String) {}
                override fun logout() {}
            }
            val auth = AuthSession(Account("acc-g", "guser", "Google User"), "csrf", false)
            return ActiveConnection(network, api, auth).also { current = it }
        }

        override suspend fun changePassword(current: String, replacement: String) {}
        override suspend fun logout() { current = null }
    }

    private class FakeGooglePlayback : PlaybackGateway {
        override val state: StateFlow<PlaybackSnapshot> = MutableStateFlow(PlaybackSnapshot())
        override val imageLoader: ImageLoader? = null
        override suspend fun connect(connection: ActiveConnection) {}
        override fun play(songs: List<Song>, index: Int) {}
        override fun setVolume(volume: Float) {}
        override fun togglePlay() {}
        override fun next() {}
        override fun previous() {}
        override fun seekTo(positionMs: Long) {}
        override fun toggleShuffle() {}
        override fun cycleRepeat() {}
        override fun select(index: Int) {}
        override fun retry() {}
        override fun stopAndClear() {}
        override fun release() {}
    }
}
