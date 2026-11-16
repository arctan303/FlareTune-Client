package com.flaretune.client

import com.flaretune.client.data.*
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
import java.util.concurrent.TimeUnit

/** Same-origin TLS fixtures reproduce the actual mixed local/YT response union. */
class ExternalCatalogContractsTest {
    private lateinit var server: MockWebServer
    private lateinit var base: OkHttpClient
    private lateinit var network: SessionNetwork
    private lateinit var api: FlareTuneApi
    private val cookie = "E".repeat(43)

    @Before fun setup() {
        val certificate = HeldCertificate.Builder().commonName("localhost")
            .addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1")
            .addSubjectAlternativeName("::1").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
        base = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
        network = SessionNetwork(InstanceAddress.parse(server.url("/").toString()), baseClient = base)
        api = FlareTuneApi(network)
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
            .setHeader("Set-Cookie", SESSION_COOKIE_NAME + "=" + cookie + "; Path=/; Secure; HttpOnly; Max-Age=3600")
            .setBody(JSONObject().put("authenticated", true).put("mustChangePassword", false)
                .put("csrfToken", "external-fixture-csrf").put("user", JSONObject()
                    .put("accountId", "external-fixture-account").put("username", "listener")).toString()))
        api.login("listener", "fixture-only")
        assertEquals("/api/auth/login", server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!.encodedPath)
    }

    @After fun teardown() {
        network.retire(); server.shutdown()
        base.dispatcher.executorService.shutdownNow(); base.connectionPool.evictAll()
    }

    private fun enqueue(data: JSONObject) = server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
        .setBody(JSONObject().put("code", 200).put("data", data).toString()))
    private fun page(field: String, rows: JSONArray) = JSONObject().put(field, rows)
        .put("total", 0).put("limit", 20).put("offset", 0).put("hasMore", false)
    private fun take() = server.takeRequest(5, TimeUnit.SECONDS)!!.also {
        assertEquals("external-fixture-account", it.getHeader("X-FlareTune-Expected-Account"))
        assertEquals(SESSION_COOKIE_NAME + "=" + cookie, it.getHeader("Cookie"))
    }
    private fun localArtist() = JSONObject().put("name", "Local").put("songCount", 7).put("albumCount", 2)
        .put("photoUrl", "").put("coverUrl", "")
    private fun remoteArtist(id: String = "yt:artist:UCfixture") = JSONObject().put("id", id).put("source", "youtube")
        .put("name", "Provider artist").put("songCount", JSONObject.NULL).put("photoUrl", JSONObject.NULL)
        .put("coverUrl", JSONObject.NULL)
    private fun localAlbum() = JSONObject().put("id", "WyJMb2NhbCIsIkFsYnVtIl0").put("title", "Local album")
        .put("artist", "Local").put("trackCount", 4).put("coverUrl", "")
    private fun remoteAlbum(id: String = "yt:album:MPREb_fixture") = JSONObject().put("id", id).put("source", "youtube")
        .put("title", "Provider album").put("artist", "Provider artist").put("coverUrl", JSONObject.NULL)
    private fun song() = JSONObject().put("id", "yt:abcdefghijk").put("title", "Song")
        .put("audio_url", "/media/youtube/audio/abcdefghijk")

    @Test fun mixedArtistSearchPreservesLocalCountsAndUnknownProviderCountsAndId() {
        enqueue(page("artists", JSONArray().put(localArtist()).put(remoteArtist())))
        val rows = api.artists("fixture", 0).artists
        assertEquals(2, rows.size)
        assertEquals(7, rows[0].knownSongCount); assertEquals(2, rows[0].knownAlbumCount)
        assertNull(rows[0].id)
        assertEquals("yt:artist:UCfixture", rows[1].id)
        assertNull(rows[1].knownSongCount); assertNull(rows[1].knownAlbumCount)
        assertNull(rows[1].photoUrl)
        assertEquals("/api/artists", take().requestUrl!!.encodedPath)
    }

    @Test fun mixedAlbumSearchAcceptsOnlyBoundedProviderIdAndDoesNotInventTrackCount() {
        enqueue(page("albums", JSONArray().put(localAlbum()).put(remoteAlbum())))
        val rows = api.browseAlbums("fixture", 0).albums
        assertEquals(2, rows.size); assertEquals(4, rows[0].knownTrackCount)
        assertEquals("yt:album:MPREb_fixture", rows[1].id)
        assertNull(rows[1].knownTrackCount)
        take()
    }

    @Test fun providerAlbumDetailUsesOpaqueIdAndRealReturnedSongCount() {
        val id = "yt:album:MPREb_fixture"
        enqueue(remoteAlbum(id).put("songCount", 1).put("songs", JSONArray().put(song())))
        val detail = api.album(id)
        assertEquals(id, detail.album.id); assertEquals(1, detail.album.knownTrackCount)
        assertEquals(listOf("yt:abcdefghijk"), detail.songs.map { it.id })
        assertEquals("/api/albums/" + id, take().requestUrl!!.encodedPath.replace("%3A", ":"))
    }

    @Test fun providerArtistDetailVerifiesOpaqueIdInsteadOfTreatingItAsDisplayName() {
        val id = "yt:artist:UCfixture"
        enqueue(remoteArtist(id).put("songCount", 1).put("hasMore", false)
            .put("songs", JSONArray().put(song())).put("albums", JSONArray().put(remoteAlbum())))
        val detail = api.artist(id)
        assertEquals(id, detail.artist.id); assertEquals("Provider artist", detail.artist.name)
        assertEquals(1, detail.artist.knownSongCount); assertNull(detail.artist.knownAlbumCount)
        assertEquals(listOf("yt:album:MPREb_fixture"), detail.albums.map { it.id })
        assertNull(detail.albums.single().knownTrackCount)
        assertEquals("/api/artists/" + id, take().requestUrl!!.encodedPath.replace("%3A", ":"))
        enqueue(remoteArtist("yt:artist:UCanother").put("songCount", 1).put("hasMore", false)
            .put("songs", JSONArray()).put("albums", JSONArray()))
        assertEquals(Failure.INVALID_RESPONSE, assertThrows(ApiFailure::class.java) { api.artist(id) }.failure)
        take()
    }

    @Test fun localArtistAndAlbumValidationStillRejectsNullOrMalformedRequiredCounts() {
        enqueue(page("artists", JSONArray().put(localArtist().put("songCount", JSONObject.NULL))))
        assertEquals(Failure.INVALID_RESPONSE, assertThrows(ApiFailure::class.java) { api.artists("local") }.failure); take()
        enqueue(page("albums", JSONArray().put(localAlbum().put("trackCount", JSONObject.NULL))))
        assertEquals(Failure.INVALID_RESPONSE, assertThrows(ApiFailure::class.java) { api.browseAlbums("local") }.failure); take()
        enqueue(page("albums", JSONArray().put(localAlbum().put("artist", ""))))
        assertEquals(Failure.INVALID_RESPONSE, assertThrows(ApiFailure::class.java) { api.browseAlbums("local") }.failure); take()
    }

    @Test fun providerUnknownArtistAndCountsAreAllowedButWrongTypesAndSourcesAreRejected() {
        enqueue(page("albums", JSONArray().put(remoteAlbum().put("artist", JSONObject.NULL))))
        assertEquals("", api.browseAlbums("unknown").albums.single().artist); take()
        for (bad in listOf(-1, 1.5, "7")) {
            enqueue(page("artists", JSONArray().put(remoteArtist().put("songCount", bad))))
            assertEquals(Failure.INVALID_RESPONSE, assertThrows(ApiFailure::class.java) { api.artists("bad") }.failure); take()
        }
        enqueue(page("albums", JSONArray().put(remoteAlbum().put("source", "local"))))
        assertEquals(Failure.INVALID_RESPONSE, assertThrows(ApiFailure::class.java) { api.browseAlbums("bad") }.failure); take()
        enqueue(page("artists", JSONArray().put(remoteArtist().put("source", "local"))))
        assertEquals(Failure.INVALID_RESPONSE, assertThrows(ApiFailure::class.java) { api.artists("bad") }.failure); take()
    }

    @Test fun malformedOrUnboundedProviderIdsAreRejectedBeforeNetworkAndInResponses() {
        for (id in listOf("yt:album:other", "yt:album:MPREb_", "yt:album:MPREb_x/y", "yt:album:MPREb_" + "x".repeat(123))) {
            assertEquals(Failure.REQUEST, assertThrows(ApiFailure::class.java) { api.album(id) }.failure)
        }
        for (id in listOf("yt:artist:other", "yt:artist:UC", "yt:artist:UCx/y", "yt:artist:UC" + "x".repeat(127))) {
            assertEquals(Failure.REQUEST, assertThrows(ApiFailure::class.java) { api.artist(id) }.failure)
        }
        assertEquals(1, server.requestCount) // Only the authenticated setup request; no malformed ID reached HTTP.
        enqueue(page("artists", JSONArray().put(remoteArtist("yt:artist:UC"))))
        assertEquals(Failure.INVALID_RESPONSE, assertThrows(ApiFailure::class.java) { api.artists("bad") }.failure); take()
        enqueue(page("albums", JSONArray().put(remoteAlbum("yt:album:MPREb_"))))
        assertEquals(Failure.INVALID_RESPONSE, assertThrows(ApiFailure::class.java) { api.browseAlbums("bad") }.failure); take()
    }

    @Test fun emptySearchBranchesRemainSuccessfulAndUnknownCountDefaultsAreCompatible() {
        enqueue(page("artists", JSONArray())); enqueue(page("albums", JSONArray()))
        assertTrue(api.artists("no match").artists.isEmpty()); take()
        assertTrue(api.browseAlbums("no match").albums.isEmpty()); take()
        assertEquals(3, com.flaretune.client.model.Artist("Legacy", songCount = 3).knownSongCount)
        assertEquals(4, com.flaretune.client.model.Album("id", "Title", "Artist", trackCount = 4).knownTrackCount)
    }
}
