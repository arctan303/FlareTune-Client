package com.flaretune.client

import com.flaretune.client.data.*
import kotlinx.coroutines.*
import okhttp3.*
import okio.buffer
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import javax.crypto.spec.SecretKeySpec

class SessionContractsTest {
    private lateinit var server: MockWebServer
    private lateinit var base: OkHttpClient
    private lateinit var tlsServer: HandshakeCertificates
    private val token = "A".repeat(43)
    private val sessionBody = """{"authenticated":true,"mustChangePassword":false,"csrfToken":"csrf-fixture","user":{"accountId":"account-one","username":"listener","displayName":"Listener"}}"""

    @Before fun setup() {
        val certificate = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1").addSubjectAlternativeName("::1").build()
        tlsServer = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer().apply { useHttps(tlsServer.sslSocketFactory(), false); start() }
        base = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
    }
    @After fun teardown() { server.shutdown(); base.dispatcher.executorService.shutdownNow(); base.connectionPool.evictAll() }
    private fun response(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun loginResponses() {
        server.enqueue(response("""{"state":"ready"}"""))
        server.enqueue(response(sessionBody).setHeader("Set-Cookie",
            "$SESSION_COOKIE_NAME=$token; Path=/; HttpOnly; Secure; Max-Age=3600; SameSite=Strict"))
    }
    private fun manager(store: MemorySessions = MemorySessions()) = ConnectionManager(store, base)

    @Test fun fixtureHttpsAndSharedClientCanReadPublicStatus() {
        server.enqueue(response("""{"state":"ready"}"""))
        val network = SessionNetwork(InstanceAddress.parse(server.url("/").toString()), baseClient = base)
        network.apiClient.newCall(Request.Builder().url(server.url("/api/instance/status")).build()).execute().use {
            assertEquals(200, it.code)
            assertEquals("ready", JSONObject(it.body!!.string()).getString("state"))
        }
    }
    @Test fun rootAddressRequiresHttpsAndNoEmbeddedCredentials() {
        assertEquals("https://music.example", InstanceAddress.parse("music.example/").origin)
        for (bad in listOf("http://music.example", "https://user:password@music.example", "https://music.example/nested",
            "https://music.example/?key=secret", "https://music.example/#fragment")) {
            assertThrows(ApiFailure::class.java) { InstanceAddress.parse(bad) }
        }
    }

    @Test fun loginUsesExistingHeadersAndCookieThenMediaSharesTheSession() = runBlocking<Unit> {
        loginResponses()
        val store = MemorySessions()
        val connection = manager(store).login(server.url("/").toString(), "listener", "only-in-request")
        val status = server.takeRequest(5, TimeUnit.SECONDS)!!
        val login = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/instance/status", status.path)
        assertEquals("/api/auth/login", login.path)
        assertEquals("POST", login.method)
        assertEquals(connection.network.address.origin, login.getHeader("Origin"))
        assertEquals("FlareTune", login.getHeader("X-Requested-With"))
        assertEquals("only-in-request", JSONObject(login.body.readUtf8()).getString("password"))
        assertNotNull(store.saved)
        assertFalse(store.saved.toString().contains("only-in-request"))

        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-3/4").setBody("wave"))
        connection.network.mediaClient.newCall(Request.Builder().url(server.url("/media/song"))
            .header("Range", "bytes=0-3").build()).execute().use { assertEquals(206, it.code) }
        val media = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("$SESSION_COOKIE_NAME=$token", media.getHeader("Cookie"))
        assertEquals("bytes=0-3", media.getHeader("Range"))
    }

    @Test fun cookiesNeverGoToAnotherPortOrHostAndRetiredResponsesCannotReviveThem() = runBlocking<Unit> {
        loginResponses()
        val connection = manager().login(server.url("/").toString(), "listener", "temporary")
        repeat(2) { server.takeRequest(5, TimeUnit.SECONDS) }
        val other = MockWebServer().apply { useHttps(tlsServer.sslSocketFactory(), false); start() }
        try {
            other.enqueue(MockResponse().setBody("public-image"))
            connection.network.mediaClient.newCall(Request.Builder().url(other.url("/image")).build()).execute().close()
            assertNull(other.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("Cookie"))
            assertTrue(connection.network.jar.loadForRequest(HttpUrl.Builder().scheme("https").host("different.example").build()).isEmpty())
            val stale = Cookie.parse(server.url("/"), "$SESSION_COOKIE_NAME=$token; Path=/; HttpOnly; Secure; Max-Age=3600")!!
            connection.network.retire()
            connection.network.jar.saveFromResponse(server.url("/"), listOf(stale))
            assertNull(connection.network.jar.serialized())
            assertTrue(connection.network.jar.loadForRequest(server.url("/media/song")).isEmpty())
        } finally { other.shutdown() }
    }

    @Test fun apiRedirectCannotForwardLoginPassword() = runBlocking<Unit> {
        val other = MockWebServer().apply { useHttps(tlsServer.sslSocketFactory(), false); start() }
        try {
            server.enqueue(response("""{"state":"ready"}"""))
            server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", other.url("/steal")))
            val store = MemorySessions()
            val error = assertThrows(ApiFailure::class.java) {
                runBlocking { manager(store).login(server.url("/").toString(), "listener", "never-forward") }
            }
            assertEquals(Failure.REQUEST, error.failure)
            assertEquals(0, other.requestCount)
            assertNull(store.saved)
        } finally { other.shutdown() }
    }

    @Test fun malformedOrNonHostCookiesDoNotCreateAStoredSession() {
        val address = InstanceAddress.parse(server.url("/").toString())
        for (cookie in listOf(
            "$SESSION_COOKIE_NAME=$token; Path=/; HttpOnly",
            "$SESSION_COOKIE_NAME=$token; Path=/other; Secure; HttpOnly",
            "$SESSION_COOKIE_NAME=bad; Path=/; Secure; HttpOnly",
            "$SESSION_COOKIE_NAME=$token; Domain=localhost; Path=/; Secure; HttpOnly")) {
            val jar = SessionCookieJar(address)
            Cookie.parse(address.url, cookie)?.let { jar.saveFromResponse(address.url, listOf(it)) }
            assertNull(jar.serialized())
        }
    }

    @Test fun searchAndCollectionRespectTheServerEnvelopeAndPaging() = runBlocking<Unit> {
        loginResponses()
        val connection = manager().login(server.url("/").toString(), "listener", "temporary")
        repeat(2) { server.takeRequest(5, TimeUnit.SECONDS) }
        server.enqueue(response("""{"code":200,"data":{"songs":[{"id":"one","title":"Song","artist":"Artist","duration":12,"audio_url":"song.mp3","cover_url":null},{"id":"invalid","audio_url":null}],"hasMore":true}}"""))
        val page = connection.api.songs("", 50)
        assertEquals(1, page.songs.size)
        assertEquals(server.url("/media/song.mp3").toString(), page.songs.single().audioUrl)
        assertNull(page.songs.single().coverUrl)
        assertEquals(100, page.nextOffset)
        assertTrue(page.hasMore)
        val collection = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("2", collection.requestUrl!!.queryParameter("page"))
        assertEquals("50", collection.requestUrl!!.queryParameter("limit"))
        assertEquals("account-one", collection.getHeader("X-FlareTune-Expected-Account"))
        server.enqueue(response("""{"code":200,"data":{"songs":[],"hasMore":false}}"""))
        connection.api.songs("a & 中文", 100)
        val search = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("a & 中文", search.requestUrl!!.queryParameter("q"))
        assertEquals("100", search.requestUrl!!.queryParameter("offset"))
    }

    @Test fun expiredSessionCannotRestoreButNetworkFailureKeepsOnlyEncryptedResumeMaterial() = runBlocking<Unit> {
        val store = MemorySessions()
        loginResponses()
        manager(store).login(server.url("/").toString(), "listener", "temporary")
        val saved = store.saved
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        val offline = manager(store)
        assertThrows(ApiFailure::class.java) { runBlocking { offline.restore() } }
        assertNull(offline.current)
        assertEquals(saved, store.saved)
        server.enqueue(response("""{"state":"ready"}"""))
        server.enqueue(response("""{"authenticated":false}"""))
        assertNull(manager(store).restore())
        assertNull(store.saved)
    }

    @Test fun logoutRetiresOldNetworkAndRevokesWithCsrf() = runBlocking<Unit> {
        loginResponses()
        val store = MemorySessions()
        val manager = manager(store)
        val old = manager.login(server.url("/").toString(), "listener", "temporary")
        repeat(2) { server.takeRequest(5, TimeUnit.SECONDS) }
        server.enqueue(response("""{"ok":true}"""))
        manager.logout()
        assertNull(manager.current)
        assertNull(store.saved)
        assertFalse(old.network.isActive)
        val logout = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/auth/logout", logout.path)
        assertEquals("csrf-fixture", logout.getHeader("X-CSRF-Token"))
        assertEquals("$SESSION_COOKIE_NAME=$token", logout.getHeader("Cookie"))
        assertThrows(ApiFailure::class.java) { old.api.favorites() }
    }

    @Test fun lateLoginCannotCommitAfterLogout() = runBlocking<Unit> {
        supervisorScope {
            server.enqueue(response("""{"state":"ready"}"""))
            server.enqueue(response(sessionBody).setHeader("Set-Cookie",
                "$SESSION_COOKIE_NAME=$token; Path=/; Secure; HttpOnly; Max-Age=3600").setBodyDelay(350, TimeUnit.MILLISECONDS))
            val store = MemorySessions()
            val manager = manager(store)
            val pending = async(Dispatchers.IO) { manager.login(server.url("/").toString(), "listener", "temporary") }
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            manager.logout()
            try { pending.await(); fail("Retired login was accepted") } catch (error: ApiFailure) { assertEquals(Failure.RETIRED, error.failure) }
            assertNull(manager.current)
            assertNull(store.saved)
        }
    }


    @Test fun pendingRestoreCannotCommitAfterLogout() = runBlocking<Unit> {
        supervisorScope {
            val store = MemorySessions()
            loginResponses()
            manager(store).login(server.url("/").toString(), "listener", "temporary")
            repeat(2) { server.takeRequest(5, TimeUnit.SECONDS) }
            val restoring = manager(store)
            server.enqueue(response("""{"state":"ready"}"""))
            server.enqueue(response(sessionBody).setBodyDelay(350, TimeUnit.MILLISECONDS))
            val pending = async(Dispatchers.IO) { restoring.restore() }
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertEquals("/api/auth/session", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
            restoring.logout()
            try { pending.await(); fail("Restore revived a logged-out session") }
            catch (error: ApiFailure) { assertEquals(Failure.RETIRED, error.failure) }
            assertNull(restoring.current)
            assertNull(store.saved)
        }
    }

    @Test fun completedOldPasswordRequestCannotClearANewerLogin() = runBlocking<Unit> {
        supervisorScope {
            val completedResponse = java.util.concurrent.CountDownLatch(1)
            val releaseOldResponse = java.util.concurrent.CountDownLatch(1)
            // Pause after a real successful response has been parsed, before its call returns.
            val gatedClient = base.newBuilder().addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (chain.request().url.encodedPath != "/api/auth/change-password") response
                else {
                    val original = response.body!!
                    val source = object : okio.ForwardingSource(original.source()) {
                        override fun close() {
                            completedResponse.countDown()
                            check(releaseOldResponse.await(5, TimeUnit.SECONDS))
                            super.close()
                        }
                    }.buffer()
                    response.newBuilder().body(object : ResponseBody() {
                        override fun contentType() = original.contentType()
                        override fun contentLength() = original.contentLength()
                        override fun source() = source
                    }).build()
                }
            }.build()
            val store = MemorySessions()
            val manager = ConnectionManager(store, gatedClient)
            loginResponses()
            val old = manager.login(server.url("/").toString(), "listener", "temporary")
            server.enqueue(response("""{"ok":true}"""))
            val pending = async(Dispatchers.IO) { manager.changePassword("temporary", "replacement") }
            try {
                assertTrue(completedResponse.await(5, TimeUnit.SECONDS))
                server.enqueue(response("""{"state":"ready"}"""))
                server.enqueue(response(sessionBody.replace("account-one", "account-two").replace("listener", "second"))
                    .setHeader("Set-Cookie", "$SESSION_COOKIE_NAME=BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB; Path=/; HttpOnly; Secure; Max-Age=3600"))
                val newer = manager.login(server.url("/").toString(), "second", "another-password")
                releaseOldResponse.countDown()
                try { pending.await(); fail("Superseded password operation was accepted") }
                catch (error: ApiFailure) { assertEquals(Failure.RETIRED, error.failure) }
                assertSame(newer, manager.current)
                assertEquals("account-two", newer.auth.account.id)
                assertTrue(newer.network.isActive)
                assertFalse(old.network.isActive)
                assertTrue(store.saved!!.cookie.contains("B".repeat(43)))
            } finally { releaseOldResponse.countDown() }
        }
    }

    @Test fun finishingOldLogoutRevokesOnlyItsCapturedAccount() = runBlocking<Unit> {
        val store = MemorySessions()
        val manager = manager(store)
        loginResponses()
        manager.login(server.url("/").toString(), "listener", "temporary")
        repeat(2) { server.takeRequest(5, TimeUnit.SECONDS) }
        server.enqueue(response("""{"ok":true}""").setBodyDelay(350, TimeUnit.MILLISECONDS))
        val pending = async(Dispatchers.IO) { manager.logout() }
        val revoke = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/auth/logout", revoke.path)
        assertEquals("account-one", revoke.getHeader("X-FlareTune-Expected-Account"))
        assertEquals("$SESSION_COOKIE_NAME=$token", revoke.getHeader("Cookie"))
        loginResponses()
        val newer = manager.login(server.url("/").toString(), "listener", "temporary")
        pending.await()
        assertSame(newer, manager.current)
        assertNotNull(store.saved)
    }

    @Test fun encryptionIsRandomizedAndRejectsTamperingOrAnotherKey() {
        val cipher = SessionCipher { SecretKeySpec(ByteArray(32) { 7 }, "AES") }
        val bytes = "sensitive-session-fixture".toByteArray()
        val encrypted = cipher.encrypt(bytes)
        assertArrayEquals(bytes, cipher.decrypt(encrypted))
        assertFalse(encrypted.contentEquals(cipher.encrypt(bytes)))
        val changed = encrypted.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        assertThrows(Exception::class.java) { cipher.decrypt(changed) }
        assertThrows(Exception::class.java) { SessionCipher { SecretKeySpec(ByteArray(32) { 8 }, "AES") }.decrypt(encrypted) }
        assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains("sensitive-session-fixture"))
    }
}

class MemorySessions : SessionStore {
    @Volatile var saved: SavedConnection? = null
    override var lastOrigin: String = ""
    override fun read() = saved
    override fun write(connection: SavedConnection) { saved = connection }
    override fun clear() { saved = null }
}
