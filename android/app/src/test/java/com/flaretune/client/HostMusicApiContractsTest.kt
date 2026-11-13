package com.flaretune.client

import com.flaretune.client.data.*
import com.flaretune.client.model.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Host HTTP contracts only; every request targets a local TLS fixture. */
class HostMusicApiContractsTest {
    private lateinit var server: MockWebServer
    private lateinit var base: OkHttpClient
    private lateinit var network: SessionNetwork
    private lateinit var api: FlareTuneApi
    private val cookie = "H".repeat(43)

    @Before fun setup() {
        val certificate = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1").addSubjectAlternativeName("::1").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
        base = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
        network = SessionNetwork(InstanceAddress.parse(server.url("/").toString()), baseClient = base)
        api = FlareTuneApi(network)
        server.enqueue(response(JSONObject().put("authenticated", true).put("csrfToken", "host-csrf").put("mustChangePassword", false)
            .put("user", JSONObject().put("accountId", "host-account").put("username", "listener")))
            .setHeader("Set-Cookie", "$SESSION_COOKIE_NAME=$cookie; Path=/; Secure; HttpOnly; Max-Age=3600"))
        api.login("listener", "fixture-only"); take()
    }    @After fun teardown() { network.retire(); server.shutdown(); base.dispatcher.executorService.shutdownNow(); base.connectionPool.evictAll() }
    private fun response(body: JSONObject, status: Int = 200) = MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body.toString())
    private fun enqueue(body: JSONObject, status: Int = 200) { server.enqueue(response(body, status)) }
    private fun take(): RecordedRequest = server.takeRequest(5, TimeUnit.SECONDS)!!
    private fun member(data: JSONObject) = JSONObject().put("ok", true).put("data", data)
    private fun catalog(data: Any) = JSONObject().put("code", 200).put("data", data)
    private fun shelf(revision: Int = 1, ids: List<String> = listOf("favorite-real", "p")) = JSONObject().put("revision", revision).put("updatedAt", 1000)
        .put("items", JSONArray(ids.map { JSONObject().put("kind", "member").put("id", it) }))
    private fun summary(id: String = "p", kind: String = "regular", revision: Int = 3) = JSONObject().put("id", id).put("source", "member")
        .put("name", if (kind == "favorite") "我的收藏" else "Playlist").put("kind", kind).put("description", "").put("songCount", 2)
        .put("revision", revision).put("previewCovers", JSONArray()).put("customCoverUrl", JSONObject.NULL).put("coverRevision", 9)
        .put("createdAt", 1000).put("updatedAt", 2000)
    private fun song(id: String, playable: Boolean = true, order: Int = 0) = JSONObject().put("id", id).put("title", id).put("artist", "Singer").put("album", "Album")
        .put("duration", 30.25).put("audio_url", if (playable) "/media/$id.mp3" else JSONObject.NULL).put("cover_url", JSONObject.NULL)
        .put("language", "en").put("sortOrder", order).put("addedAt", 1000L + order)
    private fun detail(id: String = "p", kind: String = "regular") = summary(id, kind).put("songs", JSONArray()
        .put(song("missing-audio", false, 0)).put(song("playable", true, 1)))
    private fun write(outcome: String = "applied", playlist: JSONObject = summary()) = member(JSONObject().put("outcome", outcome).put("playlist", playlist))
    private fun assertWrite(request: RecordedRequest, method: String, path: String) {
        assertEquals(method, request.method); assertEquals(path, request.requestUrl!!.encodedPath)
        assertEquals("host-account", request.getHeader("X-FlareTune-Expected-Account"))
        assertEquals("host-csrf", request.getHeader("X-CSRF-Token")); assertEquals("FlareTune", request.getHeader("X-Requested-With"))
        assertEquals(network.address.origin, request.getHeader("Origin")); assertEquals("$SESSION_COOKIE_NAME=$cookie", request.getHeader("Cookie"))
    }

    @Test fun createUpdateAndDeleteMatchModernPayloadsAndDeleteKeepsItsJsonRevision() {
        enqueue(write().apply { getJSONObject("data").put("shelf", shelf()) }, 201)
        val created = api.createPlaylist("  Commute  ", "  Notes  ", listOf("a"), "stable-create-key")
        assertEquals("p", created.playlist.id); assertEquals(9L, created.playlist.coverRevision)
        var request = take(); assertWrite(request, "POST", "/api/account/playlists")
        val create = JSONObject(request.body.readUtf8())
        assertEquals("Commute", create.getString("name")); assertEquals("Notes", create.getString("description"))
        assertEquals("stable-create-key", create.getString("idempotencyKey")); assertEquals(listOf("a"), create.getJSONArray("songIds").strings())
        enqueue(write("noop"))
        assertEquals("noop", api.updatePlaylist("p", 3, description = "Updated").outcome)
        request = take(); assertWrite(request, "PUT", "/api/account/playlists/p")
        val update = JSONObject(request.body.readUtf8()); assertEquals(3L, update.getLong("expectedRevision")); assertFalse(update.has("name"))
        enqueue(member(JSONObject().put("outcome", "applied").put("deletedPlaylistId", "p").put("shelf", shelf(2, listOf("favorite-real")))))
        assertEquals("p", api.deletePlaylist("p", 3).deletedPlaylistId)
        request = take(); assertWrite(request, "DELETE", "/api/account/playlists/p")
        assertEquals(setOf("expectedRevision"), JSONObject(request.body.readUtf8()).keys().asSequence().toSet())
    }

    @Test fun completeMembersSurviveMissingAudioAndReplacementNeverDropsTheirIds() {
        enqueue(member(JSONObject().put("playlist", detail())))
        val loaded = api.playlist("p"); take()
        assertEquals(listOf("missing-audio", "playable"), loaded.entries.map { it.song.id })
        assertEquals(listOf("playable"), loaded.songs.map { it.id })
        assertEquals("", loaded.entries.first().song.audioUrl)
        enqueue(write(playlist = detail()))
        val replaced = api.replacePlaylistSongs("p", loaded.entries.map { it.song.id }.reversed(), 3)
        assertNotNull(replaced.detail)
        var request = take(); assertWrite(request, "PUT", "/api/account/playlists/p/songs")
        assertEquals(listOf("playable", "missing-audio"), JSONObject(request.body.readUtf8()).getJSONArray("songIds").strings())
        enqueue(write("noop", detail()))
        api.removePlaylistSong("p", "missing-audio", 3)
        request = take(); assertWrite(request, "DELETE", "/api/account/playlists/p/songs/missing-audio")
        assertEquals(3, JSONObject(request.body.readUtf8()).getInt("expectedRevision"))
    }

    @Test fun favoriteMembershipIsTwoModernReadsAndIncludesUnplayableMembers() {
        enqueue(member(JSONObject().put("playlists", JSONArray().put(summary("favorite-real", "favorite")))))
        enqueue(member(JSONObject().put("playlist", detail("favorite-real", "favorite"))))
        val membership = api.favoriteMembership(listOf("missing-audio", "absent", "playable"))
        assertEquals("favorite-real", membership.favorite.id)
        assertEquals(setOf("missing-audio", "playable"), membership.songIds)
        assertEquals("/api/account/playlists", take().path)
        assertEquals("/api/account/playlists/favorite-real", take().path)
    }

    @Test fun partialAppendRemainsTypedAndAllFiftyTargetsAreSupported() {
        enqueue(member(JSONObject().put("outcome", "partial").put("results", JSONArray()
            .put(JSONObject().put("playlistId", "p").put("outcome", "applied").put("addedSongIds", JSONArray().put("s")).put("noopSongIds", JSONArray()).put("revision", 4))
            .put(JSONObject().put("playlistId", "favorite-real").put("outcome", "failed").put("addedSongIds", JSONArray()).put("noopSongIds", JSONArray()).put("revision", 8).put("error", "REVISION_CONFLICT")))
            .put("affectedPlaylistIds", JSONArray().put("p"))))
        val result = api.addPlaylistSongs(listOf(PlaylistTarget("p", 3), PlaylistTarget("favorite-real", 7)), listOf("s"))
        assertEquals("partial", result.outcome); assertEquals("REVISION_CONFLICT", result.results.last().error)
        val request = take(); assertWrite(request, "POST", "/api/account/playlist-songs")
        assertEquals(7, JSONObject(request.body.readUtf8()).getJSONArray("targets").getJSONObject(1).getInt("expectedRevision"))
        val targets = (0 until 50).map { PlaylistTarget("p-$it", 0) }
        enqueue(member(JSONObject().put("outcome", "noop").put("results", JSONArray(targets.map { JSONObject().put("playlistId", it.playlistId)
            .put("outcome", "noop").put("addedSongIds", JSONArray()).put("noopSongIds", JSONArray().put("s")).put("revision", 0) })).put("affectedPlaylistIds", JSONArray())))
        assertEquals(50, api.addPlaylistSongs(targets, listOf("s")).results.size); take()
    }

    @Test fun conflictSnapshotsMissingIdsAndFavoriteRestrictionsKeepHttpCodesWithoutLoggingOut() {
        enqueue(JSONObject().put("ok", false).put("error", "REVISION_CONFLICT").put("data", JSONObject().put("playlist", summary(revision = 8)).put("shelf", shelf(5))), 409)
        val conflict = assertThrows(ApiFailure::class.java) { api.updatePlaylist("p", 3, name = "New") }
        assertEquals(409, conflict.httpStatus); assertEquals("REVISION_CONFLICT", conflict.code)
        assertEquals(8L, conflict.details!!.playlist!!.revision); assertEquals(5L, conflict.details!!.shelf!!.revision); take()
        enqueue(JSONObject().put("ok", false).put("error", "SONG_NOT_FOUND").put("data", JSONObject().put("missingSongIds", JSONArray().put("gone"))), 404)
        assertEquals(listOf("gone"), assertThrows(ApiFailure::class.java) { api.replacePlaylistSongs("p", listOf("gone"), 3) }.details!!.missingSongIds); take()
        enqueue(JSONObject().put("ok", false).put("error", "FAVORITE_DELETE_FORBIDDEN"), 409)
        assertEquals("FAVORITE_DELETE_FORBIDDEN", assertThrows(ApiFailure::class.java) { api.deletePlaylist("favorite-real", 3) }.code)
        assertTrue(network.isActive)
    }

    @Test fun shelfUsesItsOwnRevisionAndFullOrderedMemberPermutation() {
        enqueue(member(JSONObject().put("shelf", shelf())))
        assertEquals(1L, api.playlistShelf().revision); take()
        enqueue(member(JSONObject().put("outcome", "applied").put("shelf", shelf(2, listOf("p", "favorite-real")))))
        val result = api.updatePlaylistShelf(listOf(ShelfItem("p"), ShelfItem("favorite-real")), 1)
        assertEquals(listOf("p", "favorite-real"), result.shelf.items.map { it.id })
        val request = take(); assertWrite(request, "PUT", "/api/account/playlist-shelf")
        val body = JSONObject(request.body.readUtf8()); assertEquals(1, body.getInt("expectedRevision"))
        assertEquals("member", body.getJSONArray("items").getJSONObject(0).getString("kind"))
    }

    @Test fun mutationLimitsAndUtf8BodyBudgetRejectLocallyWithoutSendingPartialWrites() {
        val before = server.requestCount
        for (work in listOf<() -> Unit>(
            { api.createPlaylist(" ") }, { api.createPlaylist("x".repeat(41)) },
            { api.replacePlaylistSongs("p", listOf("same", "same"), 3) },
            { api.replacePlaylistSongs("p", List(501) { "s-$it" }, 3) },
            { api.addPlaylistSongs(List(51) { PlaylistTarget("p-$it", 0) }, listOf("s")) },
            { api.addPlaylistSongs(List(50) { PlaylistTarget("p-$it", 0) }, List(11) { "s-$it" }) },
            { api.createPlaylist("x", songIds = List(100) { "界".repeat(235) + it }) },
            { api.updatePlaylist("p", -1, name = "New") },
        )) assertEquals(Failure.REQUEST, assertThrows(ApiFailure::class.java) { work() }.failure)
        assertEquals(before, server.requestCount)
    }

    @Test fun artistsAlbumsAndCollectionSortUseActualHostFiltersAndSafeEncodedNames() {
        enqueue(catalog(JSONObject().put("artists", JSONArray().put(JSONObject().put("name", "A / B").put("songCount", 3).put("albumCount", 1)
            .put("photoUrl", "").put("coverUrl", "/media/art.jpg"))).put("total", 1).put("hasMore", false)))
        val artists = api.artists("  A & 中  ", 20, "en")
        assertEquals(server.url("/media/art.jpg").toString(), artists.artists.single().coverUrl)
        var request = take(); assertEquals("A & 中", request.requestUrl!!.queryParameter("q")); assertEquals("en", request.requestUrl!!.queryParameter("language"))
        enqueue(catalog(JSONObject().put("name", "A / B").put("songCount", 3).put("photoUrl", "").put("coverUrl", "")
            .put("songs", JSONArray().put(song("s"))).put("hasMore", true)))
        assertTrue(api.artist("A / B", 2000).capped)
        request = take(); assertEquals("A / B", request.requestUrl!!.pathSegments.last()); assertEquals("2000", request.requestUrl!!.queryParameter("offset"))
        enqueue(catalog(JSONObject().put("albums", JSONArray()).put("hasMore", false)))
        api.browseAlbums("Album & 中", 0, "all", "A / B")
        request = take(); assertEquals("Album & 中", request.requestUrl!!.queryParameter("q")); assertEquals("A / B", request.requestUrl!!.queryParameter("artist")); assertNull(request.requestUrl!!.queryParameter("language"))
        repeat(2) { enqueue(catalog(JSONObject().put("songs", JSONArray()).put("hasMore", false))) }
        api.songs("", 0, "zh", "asc"); assertEquals("asc", take().requestUrl!!.queryParameter("sort"))
        api.songs("keyword", 0, "zh", "asc"); assertNull(take().requestUrl!!.queryParameter("sort"))
    }

    @Test fun randomSpotlightAndLanguageCountsKeepRealEmptyAndSoftExclusionContracts() {
        enqueue(catalog(JSONObject().put("zh", 5).put("en", 2)))
        assertEquals(mapOf("zh" to 5, "en" to 2), api.languageCounts()); assertEquals("language", take().requestUrl!!.queryParameter("counts"))
        enqueue(catalog(JSONObject().put("songs", JSONArray().put(song("recent")))))
        assertEquals("recent", api.randomSongs(20, listOf("recent")).single().id)
        assertEquals("recent", take().requestUrl!!.queryParameter("exclude"))
        enqueue(catalog(JSONObject.NULL)); assertNull(api.spotlightArtist(listOf("Singer"))); assertEquals("Singer", take().requestUrl!!.queryParameter("exclude"))
    }

    @Test fun statsAndEventReceiptsUseServerCountsMillisAndStableAcknowledgementIds() {
        val ranked = song("s", false).put("play_count", 7).put("last_played_at", 1234567L)
        enqueue(member(JSONObject().put("songs", JSONArray().put(ranked)).put("playCounts", JSONObject().put("s", 7)).put("totalPlays", 7).put("totalUniqueSongs", 1).put("topAlbums", JSONArray())))
        val stats = api.playStats(50)
        assertEquals(7L, stats.totalPlays); assertEquals(7L, stats.songs.single().playCount); assertEquals(1234567L, stats.songs.single().lastPlayedAt)
        assertFalse(stats.songs.single().song.canPlay); take()
        enqueue(member(JSONObject().put("songs", JSONArray()).put("view", "summary").put("topAlbums", JSONArray())))
        assertTrue(api.playStats(20, true).summaryOnly); assertEquals("summary", take().requestUrl!!.queryParameter("view"))
        val recentRanked = JSONObject(ranked.toString()).put("play_count", 2).put("last_played_at", 999_000_000L)
        enqueue(member(JSONObject().put("songs", JSONArray().put(recentRanked)).put("view", "summary").put("topAlbums", JSONArray())
            .put("window", JSONObject().put("days",7).put("from",395_200_000L).put("to",1_000_000_000L))))
        val recentStats = api.playStats(30, true, 7)
        assertEquals("s", recentStats.songs.single().song.id)
        assertEquals(2L, recentStats.songs.single().playCount)
        assertEquals(7, recentStats.window?.days)
        val recentRequest = take().requestUrl!!
        assertEquals("7", recentRequest.queryParameter("days"))
        assertEquals("summary", recentRequest.queryParameter("view"))
        assertEquals("30", recentRequest.queryParameter("limit"))
        assertThrows(ApiFailure::class.java) { api.playStats(30, true, 31) }
        val before = server.requestCount; assertEquals(0, api.submitPlayEvents(emptyList()).recorded); assertEquals(before, server.requestCount)
        enqueue(member(JSONObject().put("recorded", 0).put("acceptedEventIds", JSONArray().put("stable_event"))))
        assertEquals(listOf("stable_event"), api.submitPlayEvents(listOf(PlayEvent("stable_event", "s", 1234567))).acceptedEventIds)
        val request = take(); assertWrite(request, "POST", "/api/account/play-stats")
        val event = JSONObject(request.body.readUtf8()).getJSONArray("events").getJSONObject(0)
        assertEquals(setOf("event_id", "song_id", "played_at"), event.keys().asSequence().toSet()); assertEquals(1234567L, event.getLong("played_at"))
    }

    @Test fun coverUploadIsRawWebpAndSlotSetClearAndDiscardUseSeparateRevision() {
        val bytes = "RIFFfixtureWEBP-fixture".toByteArray()
        enqueue(JSONObject().put("id", "image-1").put("url", "/api/account/images/image-1").put("width", 1024).put("height", 1024).put("byteSize", bytes.size), 201)
        val image = api.uploadPlaylistCover(bytes)
        var request = take(); assertWrite(request, "POST", "/api/account/images/upload/playlist")
        assertEquals("image/webp", request.getHeader("Content-Type")); assertArrayEquals(bytes, request.body.readByteArray())
        enqueue(JSONObject().put("imageId", JSONObject.NULL).put("url", JSONObject.NULL).put("revision", 0))
        assertEquals(0L, api.imageSlot("playlist", "p").revision); take()
        enqueue(JSONObject().put("imageId", "image-1").put("url", "/api/account/images/image-1").put("revision", 1))
        assertEquals(image.url, api.setPlaylistCover("p", image.id, 0).url)
        request = take(); assertWrite(request, "PUT", "/api/account/images/slots/playlist/p")
        val set = JSONObject(request.body.readUtf8()); assertEquals(setOf("imageId", "revision"), set.keys().asSequence().toSet()); assertFalse(set.has("expectedRevision"))
        enqueue(JSONObject().put("imageId", JSONObject.NULL).put("url", JSONObject.NULL).put("revision", 2))
        assertNull(api.setPlaylistCover("p", null, 1).imageId)
        request = take(); assertTrue(JSONObject(request.body.readUtf8()).isNull("imageId"))
        enqueue(JSONObject().put("ok", true)); api.discardUserImage("image-1")
        assertWrite(take(), "DELETE", "/api/account/images/image-1")
    }

    @Test fun privateImageUrlsAndIdentityMismatchesAreRejectedWithoutWeakeningSession() {
        enqueue(JSONObject().put("imageId", "image-1").put("url", "https://elsewhere.example/api/account/images/image-1").put("revision", 1))
        assertEquals(Failure.INVALID_RESPONSE, assertThrows(ApiFailure::class.java) { api.imageSlot("playlist", "p") }.failure); take()
        enqueue(write(playlist = summary("other")))
        assertEquals(Failure.INVALID_RESPONSE, assertThrows(ApiFailure::class.java) { api.updatePlaylist("p", 3, name = "New") }.failure); take()
        val before = server.requestCount
        assertEquals(Failure.REQUEST, assertThrows(ApiFailure::class.java) { api.uploadPlaylistCover(ByteArray(5 * 1024 * 1024 + 1)) }.failure)
        assertEquals(Failure.REQUEST, assertThrows(ApiFailure::class.java) { api.setPlaylistCover("../p", "image-1", 0) }.failure)
        assertEquals(before, server.requestCount)
    }

    @Test fun artistPhotoGalleryUsesItsCodeZeroEnvelopeAndReturnsSafeActualMetadata() {
        enqueue(JSONObject().put("code", 0).put("data", JSONObject().put("artist", "Singer").put("artistNames", JSONArray().put("Singer"))
            .put("primary", "/images/portrait.jpg").put("photos", JSONArray().put(JSONObject().put("url", "/images/portrait.jpg")
                .put("artistName", "Singer").put("width", 1024).put("height", 768).put("isRealArtist", true)))))
        val result = api.artistPhotos("Singer")
        assertEquals(server.url("/images/portrait.jpg").toString(), result.primary)
        assertEquals(1024, result.photos.single().width)
        assertEquals("Singer", take().requestUrl!!.queryParameter("name"))
    }
    private fun JSONArray.strings() = (0 until length()).map(::getString)
}
