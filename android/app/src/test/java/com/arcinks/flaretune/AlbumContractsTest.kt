package com.arcinks.flaretune

import com.arcinks.flaretune.data.ApiFailure
import com.arcinks.flaretune.data.Failure
import com.arcinks.flaretune.data.FlareTuneApi
import com.arcinks.flaretune.data.InstanceAddress
import com.arcinks.flaretune.data.SESSION_COOKIE_NAME
import com.arcinks.flaretune.data.SessionNetwork
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Fixtures follow localAlbumRead.js envelopes and opaque album IDs. */
class AlbumContractsTest {
    private lateinit var server: MockWebServer
    private lateinit var base: OkHttpClient
    private lateinit var network: SessionNetwork
    private val token = "L".repeat(43)
    private val albumId = Base64.getUrlEncoder().withoutPadding()
        .encodeToString("""["A / B","夜曲 ? #"]""".toByteArray(Charsets.UTF_8))

    @Before fun setup() {
        val certificate = HeldCertificate.Builder().commonName("localhost")
            .addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1")
            .addSubjectAlternativeName("::1").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
        base = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
        network = SessionNetwork(InstanceAddress.parse(server.url("/").toString()), baseClient = base)
    }

    @After fun teardown() {
        network.retire()
        server.shutdown()
        base.dispatcher.executorService.shutdownNow()
        base.connectionPool.evictAll()
    }

    private fun response(body: JSONObject) = MockResponse().setHeader("Content-Type", "application/json").setBody(body.toString())
    private fun envelope(data: JSONObject) = JSONObject().put("code", 200).put("data", data)
    private fun card(id: String = albumId, cover: Any = "/media/night.jpg") = JSONObject()
        .put("id", id).put("title", "夜曲 ? #").put("artist", "A / B")
        .put("coverUrl", cover).put("trackCount", 2)
    private fun page(cards: JSONArray = JSONArray().put(card()), hasMore: Boolean = true) =
        envelope(JSONObject().put("albums", cards).put("hasMore", hasMore).put("limit", 20))

    private fun authenticatedApi(): FlareTuneApi {
        server.enqueue(response(JSONObject().put("authenticated", true).put("mustChangePassword", false)
            .put("csrfToken", "album-fixture-csrf").put("user", JSONObject()
                .put("accountId", "album-account").put("username", "listener").put("displayName", "Listener")))
            .setHeader("Set-Cookie", "$SESSION_COOKIE_NAME=$token; Path=/; Secure; HttpOnly; Max-Age=3600"))
        return FlareTuneApi(network).also {
            it.login("listener", "fixture-password")
            assertEquals("/api/auth/login", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
        }
    }

    @Test fun albumCardsUseRealPagingMetadataAndExistingAccountHeaders() {
        val api = authenticatedApi()
        server.enqueue(response(page().put("data", page().getJSONObject("data").put("offset", 40))))
        val result = api.albums(40)
        assertEquals(60, result.nextOffset)
        assertTrue(result.hasMore)
        assertFalse(result.capped)
        val album = result.albums.single()
        assertEquals(albumId, album.id)
        assertEquals("夜曲 ? #", album.title)
        assertEquals("A / B", album.artist)
        assertEquals(2, album.trackCount)
        assertEquals(server.url("/media/night.jpg").toString(), album.coverUrl)
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/albums", request.requestUrl!!.encodedPath)
        assertEquals("20", request.requestUrl!!.queryParameter("limit"))
        assertEquals("40", request.requestUrl!!.queryParameter("offset"))
        // Albums accept an omitted language filter, including tracks without classification.
        assertNull(request.requestUrl!!.queryParameter("language"))
        assertEquals("album-account", request.getHeader("X-FlareTune-Expected-Account"))
        assertEquals("$SESSION_COOKIE_NAME=$token", request.getHeader("Cookie"))
        assertEquals("GET", request.method)
    }

    @Test fun albumDetailReadsAllTracksAndUsesAlbumArtworkWhenATrackHasNone() {
        val api = authenticatedApi()
        val first = JSONObject().put("id", "track-one").put("title", "First").put("artist", "A / B")
            .put("album", "夜曲 ? #").put("audio_url", "night/first.flac")
            .put("cover_url", JSONObject.NULL).put("duration", 245.25)
        val second = JSONObject().put("id", "track-two").put("title", "Second").put("artist", "A")
            .put("audio_url", "https://cdn.example/second.mp3").put("cover_url", "https://cdn.example/second.jpg")
        server.enqueue(response(envelope(card().put("songs", JSONArray().put(first).put(second)))))
        val detail = api.album(albumId)
        assertEquals(albumId, detail.album.id)
        assertEquals(listOf("track-one", "track-two"), detail.songs.map { it.id })
        assertEquals(server.url("/media/night/first.flac").toString(), detail.songs[0].audioUrl)
        assertEquals(detail.album.coverUrl, detail.songs[0].coverUrl)
        assertEquals(245.25, detail.songs[0].durationSeconds, .001)
        assertEquals("https://cdn.example/second.mp3", detail.songs[1].audioUrl)
        assertEquals("https://cdn.example/second.jpg", detail.songs[1].coverUrl)
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/albums/$albumId", request.requestUrl!!.encodedPath)
        assertNull(request.requestUrl!!.query)
        assertEquals("album-account", request.getHeader("X-FlareTune-Expected-Account"))
        assertEquals("$SESSION_COOKIE_NAME=$token", request.getHeader("Cookie"))
    }

    @Test fun invalidAlbumIdsCannotEscapeTheAlbumPathOrSendAnyRequest() {
        val api = FlareTuneApi(network)
        for (id in listOf("", ".", "..", "../auth/logout", "abc/def", "abc?query", "abc#fragment",
            "https://other.example", "%2e%2e", "中文", " leading", "x".repeat(3201))) {
            assertEquals(Failure.REQUEST, assertThrows(ApiFailure::class.java) { api.album(id) }.failure)
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun missingBlankAndNonHttpsArtworkStayAbsent() {
        val api = authenticatedApi()
        val cards = JSONArray().put(card("one", JSONObject.NULL))
            .put(card("two", "")).put(card("three", "http://cdn.example/insecure.jpg"))
        server.enqueue(response(page(cards, false)))
        val albums = api.albums().albums
        assertEquals(3, albums.size)
        assertTrue(albums.all { it.coverUrl == null })
    }

    @Test fun malformedEnvelopesAndServerFailuresAreNotReportedAsEmptySuccess() {
        val api = authenticatedApi()
        for (body in listOf(
            envelope(JSONObject()),
            JSONObject().put("code", 0).put("data", JSONObject().put("albums", JSONArray())),
            page(JSONArray().put(card().put("id", JSONObject.NULL))),
            page(JSONArray().put(card().put("artist", JSONObject.NULL))))) {
            server.enqueue(response(body))
            assertEquals(Failure.INVALID_RESPONSE, assertThrows(ApiFailure::class.java) { api.albums() }.failure)
        }
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        assertEquals(Failure.REQUEST, assertThrows(ApiFailure::class.java) { api.albums() }.failure)
        server.enqueue(MockResponse().setResponseCode(404).setBody("{}"))
        assertEquals(Failure.REQUEST, assertThrows(ApiFailure::class.java) { api.album(albumId) }.failure)
    }

    @Test fun offsetLimitIsExplicitInsteadOfPretendingTheEntireCatalogEnded() {
        val api = authenticatedApi()
        server.enqueue(response(page()))
        val page = api.albums(2000)
        assertEquals(2020, page.nextOffset)
        assertFalse(page.hasMore)
        assertTrue(page.capped)
        val requestCount = server.requestCount
        for (offset in listOf(-1, 2001, Int.MAX_VALUE)) {
            assertEquals(Failure.REQUEST, assertThrows(ApiFailure::class.java) { api.albums(offset) }.failure)
        }
        assertEquals(requestCount, server.requestCount)
    }

    @Test fun mismatchedAlbumIdentityOrMissingTrackListFailsTheDetailRead() {
        val api = authenticatedApi()
        server.enqueue(response(envelope(card("different").put("songs", JSONArray()))))
        assertEquals(Failure.INVALID_RESPONSE, assertThrows(ApiFailure::class.java) { api.album(albumId) }.failure)
        server.enqueue(response(envelope(card())))
        assertEquals(Failure.INVALID_RESPONSE, assertThrows(ApiFailure::class.java) { api.album(albumId) }.failure)
    }
}
