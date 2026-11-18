package com.arcinks.flaretune

import com.arcinks.flaretune.data.*
import com.arcinks.flaretune.model.*
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

/** Reproduce real host DTOs through the API's origin/account guarded transport. */
class SongRelationsApiContractsTest {
    private lateinit var server: MockWebServer
    private lateinit var base: OkHttpClient
    private lateinit var network: SessionNetwork
    private lateinit var api: FlareTuneApi
    @Before fun setup() {
        val certificate = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1").addSubjectAlternativeName("::1").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
        base = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
        network = SessionNetwork(InstanceAddress.parse(server.url("/").toString()), baseClient = base)
        api = FlareTuneApi(network)
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
            .setHeader("Set-Cookie", SESSION_COOKIE_NAME + "=" + "R".repeat(43) + "; Path=/; Secure; HttpOnly; Max-Age=3600")
            .setBody(JSONObject().put("authenticated", true).put("mustChangePassword", false).put("csrfToken", "relation-fixture")
                .put("user", JSONObject().put("accountId", "relation-account").put("username", "listener")).toString()))
        api.login("listener", "fixture-only"); server.takeRequest(5, TimeUnit.SECONDS)!!
    }
    @After fun teardown() {
        network.retire(); server.shutdown(); base.dispatcher.executorService.shutdownNow(); base.connectionPool.evictAll()
    }
    private fun enqueue(data: JSONObject) = server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
        .setBody(JSONObject().put("code", 200).put("data", data).toString()))
    private fun take() = server.takeRequest(5, TimeUnit.SECONDS)!!.also {
        assertEquals("relation-account", it.getHeader("X-FlareTune-Expected-Account"))
    }
    private fun song() = JSONObject().put("id", "yt:abcdefghijk").put("source", "youtube").put("title", "Song")
        .put("artist", "Britney Spears").put("artists", JSONArray().put(JSONObject().put("id", "yt:artist:UCbritney")
            .put("sourceId", "UCbritney").put("name", "Britney Spears")))
        .put("artistId", "yt:artist:UCbritney").put("album", "Original album").put("albumId", "yt:album:MPREb_original")
        .put("audio_url", "/media/youtube/audio/abcdefghijk")
    private fun check(song: Song) {
        assertEquals("youtube", song.source)
        assertEquals(listOf(SongArtist("yt:artist:UCbritney", "Britney Spears")), song.artists)
        assertEquals("yt:artist:UCbritney", song.artistId)
        assertEquals("yt:album:MPREb_original", song.albumId)
    }
    @Test fun catalogSearchSingleAndResolveAllRetainProviderRelations() {
        enqueue(JSONObject().put("songs", JSONArray().put(song())).put("hasMore", false))
        check(api.songs("Britney", 0).songs.single()); take()
        enqueue(song()); check(api.song("yt:abcdefghijk")); take()
        enqueue(JSONObject().put("songs", JSONArray().put(song())))
        check(api.resolveSongs(listOf("yt:abcdefghijk")).single())
        assertEquals("/api/songs/resolve", take().requestUrl!!.encodedPath)
    }
    @Test fun validatedAlbumContextSuppliesMissingLocalAndProviderAlbumId() {
        val localId = "WyJBcnRpc3QiLCJBbGJ1bSJd"
        enqueue(JSONObject().put("id", localId).put("title", "Local album").put("artist", "Local")
            .put("trackCount", 1).put("songs", JSONArray().put(JSONObject().put("id", "local").put("title", "Song")
                .put("audio_url", "/media/local.flac"))))
        val local = api.album(localId).songs.single(); take()
        assertEquals(localId, local.albumId); assertEquals(localId, local.relatedAlbum()!!.id)
        assertEquals("Local album", local.album)
        val remoteId = "yt:album:MPREb_selected"
        enqueue(JSONObject().put("id", remoteId).put("source", "youtube").put("title", "Selected edition")
            .put("artist", "Britney Spears").put("songs", JSONArray().put(song().put("albumId", JSONObject.NULL))))
        val remote = api.album(remoteId).songs.single(); take()
        assertEquals(remoteId, remote.albumId)
        assertEquals("yt:artist:UCbritney", remote.relatedArtists().single().id)
    }
    @Test fun missingProviderReferencesRemainUnavailableInsteadOfLocalNames() {
        enqueue(song().put("artists", JSONArray().put(JSONObject().put("name", "Britney Spears").put("id", JSONObject.NULL)))
            .put("artistId", JSONObject.NULL).put("albumId", JSONObject.NULL))
        val result = api.song("yt:abcdefghijk"); take()
        assertTrue(result.relatedArtists().isEmpty()); assertNull(result.relatedAlbum())
    }
    @Test fun accountPlaylistMembershipMetadataRetainsProviderAssociationsWithoutMedia() {
        val metadata = song().put("audio_url", JSONObject.NULL).put("sortOrder", 0).put("addedAt", 1)
        val playlist = JSONObject().put("id", "playlist").put("kind", "regular").put("name", "My playlist")
            .put("revision", 1).put("songCount", 1).put("songs", JSONArray().put(metadata))
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
            .setBody(JSONObject().put("ok", true).put("data", JSONObject().put("playlist", playlist)).toString()))
        val membership = api.playlist("playlist").entries.single().song
        check(membership); assertFalse(membership.canPlay); take()
    }
    @Test fun malformedProviderRelationIsReportedAsInvalidResponse() {
        enqueue(song().put("albumId", "https://foreign.example/album"))
        try { api.song("yt:abcdefghijk"); fail("Malformed association accepted") }
        catch (failure: ApiFailure) { assertEquals(Failure.INVALID_RESPONSE, failure.failure) }
        take()
    }
}
