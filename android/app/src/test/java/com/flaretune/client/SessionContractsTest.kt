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

    private fun secondLoginResponses() {
        server.enqueue(response("""{"state":"ready"}"""))
        server.enqueue(response(sessionBody.replace("account-one", "account-two").replace("listener", "second"))
            .setHeader("Set-Cookie", "$SESSION_COOKIE_NAME=${"B".repeat(43)}; Path=/; HttpOnly; Secure; Max-Age=3600"))
    }
    private fun restoreResponses(body: String = sessionBody) {
        server.enqueue(response("""{"state":"ready"}"""))
        server.enqueue(response(body))
    }

    @Test fun addingSameOriginAccountKeepsBothAndCancelRevalidatesOriginal() = runBlocking<Unit> {
        val store = MemorySessions()
        val manager = manager(store)
        loginResponses()
        val first = manager.login(server.url("/").toString(), "listener", "temporary")
        val firstKey = manager.activeAccountKey!!
        manager.beginAddAccount()
        manager.beginAddAccount()
        assertFalse(first.network.isActive)
        assertNull(manager.current)
        assertEquals(1, manager.savedAccounts.size)
        restoreResponses()
        assertEquals("account-one", manager.cancelAddAccount()!!.auth.account.id)
        assertEquals(firstKey, manager.activeAccountKey)
        manager.beginAddAccount()
        secondLoginResponses()
        manager.login(server.url("/").toString(), "second", "another-password")
        assertEquals(2, manager.savedAccounts.size)
        assertEquals(setOf("account-one", "account-two"), manager.savedAccounts.map { it.accountId }.toSet())
        assertEquals(1, manager.savedAccounts.count { it.active })
        restoreResponses()
        val selected = manager.switchAccount(firstKey)!!
        assertEquals("account-one", selected.auth.account.id)
        assertEquals(2, manager.savedAccounts.size)
        assertEquals(firstKey, store.readAccounts().activeAccountKey)
    }

    @Test fun expiredTargetLosesOnlyItsCookieAndRetainsReauthSummary() = runBlocking<Unit> {
        val store = MemorySessions()
        val manager = manager(store)
        loginResponses(); manager.login(server.url("/").toString(), "listener", "temporary")
        val firstKey = manager.activeAccountKey!!
        secondLoginResponses(); manager.login(server.url("/").toString(), "second", "another-password")
        val secondKey = manager.activeAccountKey!!
        server.enqueue(response("""{"state":"ready"}"""))
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        assertNull(manager.switchAccount(firstKey))
        assertNull(manager.current)
        assertTrue(manager.savedAccounts.single { it.accountKey == firstKey }.needsReauth)
        assertNull(store.readAccounts().accounts.single { it.accountKey == firstKey }.cookie)
        assertFalse(manager.savedAccounts.single { it.accountKey == secondKey }.needsReauth)
        assertNotNull(store.readAccounts().accounts.single { it.accountKey == secondKey }.cookie)
        val before = server.requestCount
        assertNull(manager.switchAccount(firstKey))
        assertEquals(before, server.requestCount)
    }

    @Test fun validCookieCannotSilentlySwitchTheStoredAccountIdentity() = runBlocking<Unit> {
        val store = MemorySessions()
        val manager = manager(store)
        loginResponses(); manager.login(server.url("/").toString(), "listener", "temporary")
        val firstKey = manager.activeAccountKey!!
        restoreResponses(sessionBody.replace("account-one", "different-account"))
        assertNull(manager.switchAccount(firstKey))
        assertTrue(manager.savedAccounts.single().needsReauth)
        assertEquals("account-one", manager.savedAccounts.single().accountId)
    }

    @Test fun overlappingSwitchCannotCommitOlderAccountOrEraseNewerCookie() = runBlocking<Unit> {
        supervisorScope {
            val store = MemorySessions()
            val manager = manager(store)
            loginResponses(); manager.login(server.url("/").toString(), "listener", "temporary")
            val firstKey = manager.activeAccountKey!!
            secondLoginResponses(); manager.login(server.url("/").toString(), "second", "another-password")
            val secondKey = manager.activeAccountKey!!
            repeat(4) { server.takeRequest(5, TimeUnit.SECONDS) }
            server.enqueue(response("""{"state":"ready"}"""))
            server.enqueue(response(sessionBody).setBodyDelay(350, TimeUnit.MILLISECONDS))
            val older = async(Dispatchers.IO) { manager.switchAccount(firstKey) }
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertEquals("/api/auth/session", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
            restoreResponses(sessionBody.replace("account-one", "account-two").replace("listener", "second"))
            val newer = manager.switchAccount(secondKey)!!
            try { older.await(); fail("Older switch was accepted") }
            catch (error: ApiFailure) { assertEquals(Failure.RETIRED, error.failure) }
            assertSame(newer, manager.current)
            assertEquals(secondKey, store.readAccounts().activeAccountKey)
            assertEquals(2, store.readAccounts().accounts.size)
        }
    }

    @Test fun logoutAndExplicitRemovalLeaveOtherSavedCredentialsIntact() = runBlocking<Unit> {
        val store = MemorySessions()
        val manager = manager(store)
        loginResponses(); manager.login(server.url("/").toString(), "listener", "temporary")
        val firstKey = manager.activeAccountKey!!
        secondLoginResponses(); manager.login(server.url("/").toString(), "second", "another-password")
        val second = manager.current!!
        server.enqueue(response("""{"ok":true}"""))
        manager.logout()
        assertFalse(second.network.isActive)
        assertNull(manager.restore()) // Explicit logout returns to account selection, not automatic login.
        assertEquals(listOf(firstKey), manager.savedAccounts.map { it.accountKey })
        assertNotNull(store.readAccounts().accounts.single().cookie)
        manager.removeSavedAccount(firstKey)
        assertTrue(store.readAccounts().accounts.isEmpty())
    }

    @Test fun accountsFromDifferentOriginsUseOnlyTheirOwnCookie() = runBlocking<Unit> {
        val other = MockWebServer().apply { useHttps(tlsServer.sslSocketFactory(), false); start() }
        try {
            val store = MemorySessions()
            val manager = manager(store)
            loginResponses(); manager.login(server.url("/").toString(), "listener", "temporary")
            val firstKey = manager.activeAccountKey!!
            other.enqueue(response("""{"state":"ready"}"""))
            other.enqueue(response(sessionBody).setHeader("Set-Cookie",
                "$SESSION_COOKIE_NAME=${"B".repeat(43)}; Path=/; HttpOnly; Secure; Max-Age=3600"))
            manager.login(other.url("/").toString(), "listener", "temporary")
            assertEquals(2, manager.savedAccounts.size)
            repeat(2) { server.takeRequest(5, TimeUnit.SECONDS) }
            restoreResponses()
            manager.switchAccount(firstKey)
            server.takeRequest(5, TimeUnit.SECONDS)
            assertEquals("$SESSION_COOKIE_NAME=$token", server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("Cookie"))
            assertEquals(2, other.requestCount)
        } finally { other.shutdown() }
    }

    @Test fun activeInvalidationMarksOnlyTheActiveAccountAndNeverErasesOtherCookies() = runBlocking<Unit> {
        val store = MemorySessions()
        val manager = manager(store)
        loginResponses(); manager.login(server.url("/").toString(), "listener", "temporary")
        val firstKey = manager.activeAccountKey!!
        secondLoginResponses(); val second = manager.login(server.url("/").toString(), "second", "temporary")
        val secondKey = manager.activeAccountKey!!
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        assertThrows(ApiFailure::class.java) { second.api.favorites() }
        assertNull(manager.current)
        assertFalse(second.network.isActive)
        assertTrue(manager.savedAccounts.single { it.accountKey == secondKey }.needsReauth)
        assertNull(store.readAccounts().accounts.single { it.accountKey == secondKey }.cookie)
        assertNotNull(store.readAccounts().accounts.single { it.accountKey == firstKey }.cookie)
    }

    @Test fun failedCredentialDeletionFailsClosedAndCannotResumeTheOldCookieInProcess() = runBlocking<Unit> {
        val memory = MemorySessions()
        var failWrites = false
        val failing = object : SessionStore by memory {
            override fun writeAccounts(snapshot: SavedAccountsSnapshot) {
                if (failWrites) throw ApiFailure(Failure.STORAGE)
                memory.writeAccounts(snapshot)
            }
        }
        val manager = ConnectionManager(failing, base)
        loginResponses(); val old = manager.login(server.url("/").toString(), "listener", "temporary")
        val firstKey = manager.activeAccountKey!!
        failWrites = true
        server.enqueue(response("""{"ok":true}"""))
        try { manager.logout(); fail("Storage failure was hidden") }
        catch (error: ApiFailure) { assertEquals(Failure.STORAGE, error.failure) }
        assertFalse(old.network.isActive)
        assertNull(manager.current)
        assertNull(manager.restore())
        assertTrue(manager.savedAccounts.single().needsReauth)
        failWrites = false
        val before = server.requestCount
        assertNull(manager.switchAccount(firstKey))
        assertEquals(before, server.requestCount)
        assertNull(memory.readAccounts().accounts.single().cookie)
    }

    @Test fun migratedLegacyAccountGetsCanonicalIdentityAndExpiredReauthLeavesNoPlaceholder() = runBlocking<Unit> {
        val store = MemorySessions()
        store.saved = SavedConnection(InstanceAddress.parse(server.url("/").toString()).origin,
            "$SESSION_COOKIE_NAME=$token; Path=/; HttpOnly; Secure; Max-Age=3600")
        val manager = manager(store)
        val legacyKey = store.readAccounts().activeAccountKey!!
        server.enqueue(response("""{"state":"ready"}"""))
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        assertNull(manager.restore())
        assertTrue(manager.savedAccounts.single().needsReauth)
        loginResponses()
        val active = manager.login(server.url("/").toString(), "listener", "temporary")
        assertEquals("account-one", active.auth.account.id)
        assertEquals(1, manager.savedAccounts.size)
        assertNotEquals(legacyKey, manager.activeAccountKey)
        assertEquals("account-one", store.readAccounts().accounts.single().accountId)
    }

    @Test fun removingSavedAccountDuringUnfinishedLoginCannotRecreateItsCredential() = runBlocking<Unit> {
        supervisorScope {
            val store = MemorySessions()
            val manager = manager(store)
            loginResponses(); manager.login(server.url("/").toString(), "listener", "temporary")
            val firstKey = manager.activeAccountKey!!
            repeat(2) { server.takeRequest(5, TimeUnit.SECONDS) }
            manager.beginAddAccount()
            server.enqueue(response("""{"state":"ready"}"""))
            server.enqueue(response(sessionBody).setHeader("Set-Cookie",
                "$SESSION_COOKIE_NAME=$token; Path=/; Secure; HttpOnly; Max-Age=3600").setBodyDelay(350, TimeUnit.MILLISECONDS))
            val pending = async(Dispatchers.IO) { manager.login(server.url("/").toString(), "listener", "temporary") }
            repeat(2) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
            manager.removeSavedAccount(firstKey)
            try { pending.await(); fail("Removed credential was recreated by an old login") }
            catch (error: ApiFailure) { assertEquals(Failure.RETIRED, error.failure) }
            assertNull(manager.current)
            assertTrue(store.readAccounts().accounts.isEmpty())
        }
    }

    private class QueuedIoDispatcher : CoroutineDispatcher() {
        private val queued = java.util.ArrayDeque<Runnable>()
        override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) { queued.addLast(block) }
        fun runNext() { queued.removeFirst().run() }
        fun runLast() { queued.removeLast().run() }
    }

    private suspend fun assertRetired(pending: Deferred<*>) {
        try { pending.await(); fail("An earlier queued account intent was accepted") }
        catch (error: ApiFailure) { assertEquals(Failure.RETIRED, error.failure) }
    }

    private fun seedSavedAccount(store: MemorySessions): String {
        val origin = InstanceAddress.parse(server.url("/").toString()).origin
        val key = SavedAccount.key(origin, "account-one")
        store.writeAccounts(SavedAccountsSnapshot(listOf(SavedAccount(key, origin, "account-one", "listener", "Listener",
            "$SESSION_COOKIE_NAME=$token; Path=/; HttpOnly; Secure; Max-Age=3600")), key))
        return key
    }

    @Test fun queuedLoginCannotClaimNewIdentityAfterALaterLogoutBeforeItsIoRuns() = runBlocking<Unit> {
        supervisorScope {
            val io = QueuedIoDispatcher()
            val store = MemorySessions()
            val manager = ConnectionManager(store, base, io)
            val earlier = async(start = CoroutineStart.UNDISPATCHED) {
                manager.login(server.url("/").toString(), "listener", "temporary")
            }
            val later = async(start = CoroutineStart.UNDISPATCHED) { manager.logout() }
            io.runLast(); later.await()
            val expected = store.readAccounts()
            val expectedOrigin = store.lastOrigin
            io.runNext(); assertRetired(earlier)
            assertNull(manager.current)
            assertNull(manager.selectedAccountKey)
            assertEquals(expected, store.readAccounts())
            assertEquals(expectedOrigin, store.lastOrigin)
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun queuedOlderLoginCannotOverwriteANewerLoginAccountOrOrigin() = runBlocking<Unit> {
        supervisorScope {
            val io = QueuedIoDispatcher()
            val store = MemorySessions()
            val manager = ConnectionManager(store, base, io)
            val earlier = async(start = CoroutineStart.UNDISPATCHED) {
                manager.login(server.url("/").toString(), "listener", "temporary")
            }
            val newerOrigin = server.url("/").newBuilder().host("127.0.0.1").build().toString()
            secondLoginResponses()
            val later = async(start = CoroutineStart.UNDISPATCHED) { manager.login(newerOrigin, "second", "temporary") }
            io.runLast(); val newer = later.await()
            val expected = store.readAccounts()
            val expectedOrigin = store.lastOrigin
            io.runNext(); assertRetired(earlier)
            assertSame(newer, manager.current)
            assertEquals("account-two", newer.auth.account.id)
            assertEquals(InstanceAddress.parse(newerOrigin).origin, expectedOrigin)
            assertEquals(expectedOrigin, store.lastOrigin)
            assertEquals(expected, store.readAccounts())
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun queuedSwitchCannotUndoALaterBeginAddAccount() = runBlocking<Unit> {
        supervisorScope {
            val io = QueuedIoDispatcher()
            val store = MemorySessions()
            val firstKey = seedSavedAccount(store)
            val manager = ConnectionManager(store, base, io)
            val earlier = async(start = CoroutineStart.UNDISPATCHED) { manager.switchAccount(firstKey) }
            manager.beginAddAccount()
            val expected = store.readAccounts()
            val expectedOrigin = store.lastOrigin
            io.runNext(); assertRetired(earlier)
            assertNull(manager.current)
            assertNull(manager.selectedAccountKey)
            assertEquals(expected, store.readAccounts())
            assertEquals(expectedOrigin, store.lastOrigin)
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun queuedOlderSwitchCannotOverwriteANewerSwitchWhenIoExecutionReverses() = runBlocking<Unit> {
        supervisorScope {
            val io = QueuedIoDispatcher()
            val store = MemorySessions()
            val firstKey = seedSavedAccount(store)
            val first = store.readAccounts().accounts.single()
            val secondKey = SavedAccount.key(first.origin, "account-two")
            val second = first.copy(accountKey = secondKey, accountId = "account-two", username = "second",
                cookie = "$SESSION_COOKIE_NAME=${"B".repeat(43)}; Path=/; HttpOnly; Secure; Max-Age=3600")
            store.writeAccounts(SavedAccountsSnapshot(listOf(first, second), firstKey))
            val manager = ConnectionManager(store, base, io)
            val earlier = async(start = CoroutineStart.UNDISPATCHED) { manager.switchAccount(firstKey) }
            restoreResponses(sessionBody.replace("account-one", "account-two").replace("listener", "second"))
            val later = async(start = CoroutineStart.UNDISPATCHED) { manager.switchAccount(secondKey) }
            io.runLast(); val newer = later.await()!!
            val expected = store.readAccounts()
            io.runNext(); assertRetired(earlier)
            assertSame(newer, manager.current)
            assertEquals(secondKey, manager.selectedAccountKey)
            assertEquals(secondKey, manager.activeAccountKey)
            assertEquals(expected, store.readAccounts())
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun queuedLogoutCannotDeleteOrRevokeANewerLogin() = runBlocking<Unit> {
        supervisorScope {
            val io = QueuedIoDispatcher()
            val store = MemorySessions()
            val manager = ConnectionManager(store, base, io)
            loginResponses()
            val initial = async(start = CoroutineStart.UNDISPATCHED) { manager.login(server.url("/").toString(), "listener", "temporary") }
            io.runNext(); val old = initial.await()
            val earlier = async(start = CoroutineStart.UNDISPATCHED) { manager.logout() }
            assertFalse(old.network.isActive)
            assertTrue(store.readAccounts().accounts.isEmpty())
            secondLoginResponses()
            val later = async(start = CoroutineStart.UNDISPATCHED) { manager.login(server.url("/").toString(), "second", "temporary") }
            io.runLast(); val newer = later.await()
            val expected = store.readAccounts()
            io.runNext(); assertRetired(earlier)
            assertSame(newer, manager.current)
            assertEquals(expected, store.readAccounts())
            assertEquals("account-two", store.readAccounts().accounts.single().accountId)
            assertEquals(4, server.requestCount) // Two logins; no stale logout request.
        }
    }

    @Test fun queuedRestoreAndCancelDoNotReclaimGenerationAfterALaterLogout() = runBlocking<Unit> {
        supervisorScope {
            for (cancelAdd in listOf(false, true)) {
                val io = QueuedIoDispatcher()
                val store = MemorySessions()
                seedSavedAccount(store)
                val manager = ConnectionManager(store, base, io)
                if (cancelAdd) manager.beginAddAccount()
                val earlier = async(start = CoroutineStart.UNDISPATCHED) {
                    if (cancelAdd) manager.cancelAddAccount() else manager.restore()
                }
                val later = async(start = CoroutineStart.UNDISPATCHED) { manager.logout() }
                io.runLast(); later.await()
                io.runNext(); assertRetired(earlier)
                assertNull(manager.current)
                assertTrue(store.readAccounts().accounts.isEmpty())
            }
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun queuedPasswordChangeRemainsBoundToCapturedAccountBeforeItsIoRuns() = runBlocking<Unit> {
        supervisorScope {
            val io = QueuedIoDispatcher()
            val store = MemorySessions()
            val manager = ConnectionManager(store, base, io)
            loginResponses()
            val initial = async(start = CoroutineStart.UNDISPATCHED) { manager.login(server.url("/").toString(), "listener", "temporary") }
            io.runNext(); initial.await()
            val earlier = async(start = CoroutineStart.UNDISPATCHED) { manager.changePassword("temporary", "replacement") }
            manager.beginAddAccount()
            io.runNext(); assertRetired(earlier)
            assertNull(manager.current)
            assertEquals(1, store.readAccounts().accounts.size)
            assertFalse(store.readAccounts().accounts.single().needsReauth)
            assertEquals(2, server.requestCount) // Only the initial login.
        }
    }

    @Test fun localRemovalCancelsAQueuedUnknownIdentityLoginBeforeItsIoRuns() = runBlocking<Unit> {
        supervisorScope {
            val io = QueuedIoDispatcher()
            val store = MemorySessions()
            val firstKey = seedSavedAccount(store)
            val manager = ConnectionManager(store, base, io)
            val earlier = async(start = CoroutineStart.UNDISPATCHED) { manager.login(server.url("/").toString(), "listener", "temporary") }
            manager.removeSavedAccount(firstKey)
            io.runNext(); assertRetired(earlier)
            assertNull(manager.current)
            assertTrue(store.readAccounts().accounts.isEmpty())
            assertEquals(0, server.requestCount)
        }
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
    private var snapshot = SavedAccountsSnapshot()
    var saved: SavedConnection?
        @Synchronized get() = read()
        @Synchronized set(value) {
            snapshot = value?.let { SavedAccount.legacy(it) }?.let { SavedAccountsSnapshot(listOf(it), it.accountKey) }
                ?: SavedAccountsSnapshot()
        }
    override var lastOrigin: String = ""
    @Synchronized override fun read(): SavedConnection? = snapshot.accounts.find {
        it.accountKey == snapshot.activeAccountKey && !it.needsReauth
    }?.let { row -> row.cookie?.let { SavedConnection(row.origin, it) } }
    override fun write(connection: SavedConnection) { saved = connection }
    @Synchronized override fun clear() { snapshot = SavedAccountsSnapshot() }
    @Synchronized override fun readAccounts() = snapshot
    @Synchronized override fun writeAccounts(snapshot: SavedAccountsSnapshot) { this.snapshot = snapshot }
}
