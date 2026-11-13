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

/** Real TLS and the host's modern envelopes; no hostname-verifier bypass. */
class ClientApiContractsTest {
    private lateinit var server: MockWebServer
    private lateinit var base: OkHttpClient
    private lateinit var network: SessionNetwork
    private val token = "C".repeat(43)

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

    private fun response(body: JSONObject, status: Int = 200) = MockResponse().setResponseCode(status)
        .setHeader("Content-Type", "application/json").setBody(body.toString())
    private fun catalog(data: JSONObject) = JSONObject().put("code", 200).put("data", data)
    private fun member(data: JSONObject) = JSONObject().put("ok", true).put("data", data)
    private fun take(): RecordedRequest = server.takeRequest(5, TimeUnit.SECONDS)!!
    private fun api(): FlareTuneApi {
        server.enqueue(response(JSONObject().put("authenticated", true).put("mustChangePassword", false)
            .put("csrfToken", "client-api-csrf").put("user", JSONObject().put("accountId", "client-account")
                .put("username", "listener").put("displayName", "Listener")))
            .setHeader("Set-Cookie", "$SESSION_COOKIE_NAME=$token; Path=/; Secure; HttpOnly; Max-Age=3600"))
        return FlareTuneApi(network).also { it.login("listener", "not-saved"); take() }
    }
    @Test fun lyricConflictMessageCannotInvalidateTheAccountButExactAccountCodeDoes() {
        var invalidations = 0
        network.retire()
        network = SessionNetwork(InstanceAddress.parse(server.url("/").toString()), baseClient = base,
            onInvalidated = { invalidations++ })
        val api = api()
        server.enqueue(response(JSONObject().put("code", 409).put("error", "LYRIC_RESULT_CHANGED")
            .put("message", "account_changed"), 409))
        try { api.lyrics("track-one"); fail("Expected lyric conflict") } catch (error: ApiFailure) {
            assertEquals("LYRIC_RESULT_CHANGED", error.code)
        }
        assertEquals(0, invalidations)
        assertTrue(network.isActive)
        server.enqueue(response(JSONObject().put("error", "account_changed"), 409))
        try { api.lyrics("track-one"); fail("Expected account conflict") } catch (error: ApiFailure) {
            assertEquals(Failure.SESSION_EXPIRED, error.failure)
        }
        assertEquals(1, invalidations)
        assertFalse(network.isActive)
    }
    private fun song(id: String = "track-one", language: Any = "yue") = JSONObject().put("id", id)
        .put("title", "Night").put("artist", "Singer").put("album", "Album").put("duration", 12.25)
        .put("audio_url", "/media/audio/$id.flac").put("cover_url", "/media/covers/$id.jpg").put("language", language)
    private fun songPage(rows: JSONArray = JSONArray().put(song())) = catalog(JSONObject().put("songs", rows).put("hasMore", false))
    private fun summary(id: String = "playlist-real-id", kind: String = "regular") = JSONObject().put("id", id)
        .put("name", if (kind == "favorite") "我的收藏" else "Night songs").put("kind", kind).put("source", "member")
        .put("description", "Listen later").put("songCount", 2).put("revision", 7L)
        .put("previewCovers", JSONArray().put("/media/a.jpg").put("https://cdn.example/b.jpg"))
        .put("customCoverUrl", "/api/account/images/cover").put("createdAt", 1000L).put("updatedAt", 2000L)
    private fun lyric(state: String = "ready", mode: String = "word") = JSONObject().put("version", 2)
        .put("source", "manual").put("format", "lrc").put("syncMode", mode).put("sourceId", "lyrics-workbench")
        .put("resultRef", "signed.fixture-ref").put("offsetMs", 250).put("quality", "good")
        .put("lines", JSONArray().put(JSONObject().put("text", "Hello world").put("time", 1.25).put("endTime", 3.25)
            .put("tlyric", "旧行内译文").put("words", JSONArray()
                .put(JSONObject().put("text", "Hello ").put("startTime", 1.25).put("endTime", 2.25))
                .put(JSONObject().put("text", "world").put("startTime", 2.25).put("endTime", 3.25)))))
        .put("translation", JSONObject().put("supported", true).put("state", state).also {
            if (state == "ready") it.put("lines", JSONArray().put("你好，世界"))
        }).put("translationAvailable", state == "ready").put("translationState", state)

    @Test fun languageFiltersUseLegalCollectionsAndOmitAllOnSearchAndAlbums() {
        val api = api()
        server.enqueue(response(songPage()))
        assertEquals("yue", api.songs("", 0, "all").songs.single().language)
        var request = take()
        assertEquals("/api/songs", request.requestUrl!!.encodedPath)
        assertTrue(request.requestUrl!!.queryParameter("language")!!.contains("instrumental"))
        assertFalse(request.requestUrl!!.queryParameter("language")!!.contains("all"))
        assertEquals("client-account", request.getHeader("X-FlareTune-Expected-Account"))
        assertEquals("$SESSION_COOKIE_NAME=$token", request.getHeader("Cookie"))
        server.enqueue(response(songPage(JSONArray().put(song(language = "ZH-Hant")))))
        assertEquals("ZH-Hant", api.songs("", 50, "zh").songs.single().language)
        request = take()
        assertEquals("zh", request.requestUrl!!.queryParameter("language"))
        assertEquals("2", request.requestUrl!!.queryParameter("page"))
        server.enqueue(response(songPage(JSONArray().put(song(language = JSONObject.NULL)))))
        assertNull(api.songs("a & 中", 0, "all").songs.single().language)
        request = take()
        assertNull(request.requestUrl!!.queryParameter("language"))
        assertEquals("a & 中", request.requestUrl!!.queryParameter("q"))
        server.enqueue(response(songPage()))
        api.songs("night", 50, "ja")
        assertEquals("ja", take().requestUrl!!.queryParameter("language"))
        repeat(2) { server.enqueue(response(catalog(JSONObject().put("albums", JSONArray()).put("hasMore", false)))) }
        api.albums(0, "all"); assertNull(take().requestUrl!!.queryParameter("language"))
        api.albums(20, "en"); assertEquals("en", take().requestUrl!!.queryParameter("language"))
        val before = server.requestCount
        assertEquals(Failure.REQUEST, assertThrows(ApiFailure::class.java) { api.songs("", 0, "ZH") }.failure)
        assertEquals(Failure.REQUEST, assertThrows(ApiFailure::class.java) { api.albums(0, "unknown") }.failure)
        assertEquals(before, server.requestCount)
    }

    @Test fun searchPaginationPreservesServerCapAndRejectsAnImpossibleContinuation() {
        val api = api()
        server.enqueue(response(catalog(JSONObject().put("songs", JSONArray().put(song())).put("hasMore", false)
            .put("nextOffset", JSONObject.NULL).put("capped", true))))
        val capped = api.songs("night", 2000, "all")
        assertTrue(capped.capped); assertFalse(capped.hasMore); take()
        server.enqueue(response(catalog(JSONObject().put("songs", JSONArray().put(song())).put("hasMore", true).put("nextOffset", 50))))
        val first = api.songs("night", 0, "en")
        assertTrue(first.hasMore); assertEquals(50, first.nextOffset); take()
        server.enqueue(response(catalog(JSONObject().put("songs", JSONArray()).put("hasMore", true).put("nextOffset", JSONObject.NULL))))
        assertEquals(Failure.INVALID_RESPONSE, assertThrows(ApiFailure::class.java) { api.songs("night", 0) }.failure)
    }

    @Test fun modernPlaylistsRetainRealFavoriteIdsArtworkRevisionAndAddedAtOrder() {
        val api = api()
        server.enqueue(response(member(JSONObject().put("playlists", JSONArray().put(summary("fav_unique", "favorite")).put(summary())))))
        val summaries = api.playlists()
        assertEquals("fav_unique", summaries.first().id)
        assertEquals(7L, summaries.last().revision)
        assertEquals(server.url("/api/account/images/cover").toString(), summaries.last().customCoverUrl)
        assertEquals(server.url("/media/a.jpg").toString(), summaries.last().previewCovers.first())
        assertEquals("/api/account/playlists", take().path)
        val songs = JSONArray().put(song("second").put("sortOrder", 1).put("addedAt", 5000L))
            .put(song("first").put("sortOrder", 0).put("addedAt", 4000L))
        server.enqueue(response(member(JSONObject().put("playlist", summary().put("songs", songs)))))
        val detail = api.playlist("playlist-real-id")
        assertEquals(listOf("first", "second"), detail.songs.map { it.id })
        assertEquals(listOf(4000L, 5000L), detail.entries.map { it.addedAt })
        assertEquals(listOf(0, 1), detail.entries.map { it.sortOrder })
        assertEquals(2, detail.playlist.songCount)
        assertEquals(1000L, detail.playlist.createdAt)
        assertEquals(2000L, detail.playlist.updatedAt)
        val request = take()
        assertEquals("/api/account/playlists/playlist-real-id", request.path)
        assertEquals("client-account", request.getHeader("X-FlareTune-Expected-Account"))
    }

    @Test fun songAndPlaylistIdsStayWithinOneEncodedPathSegmentAndMustMatchResponse() {
        val api = api()
        val id = "track/part?name#中"
        server.enqueue(response(catalog(song(id))))
        assertEquals(id, api.song(id).id)
        val request = take()
        assertEquals(id, request.requestUrl!!.pathSegments.last())
        assertTrue(request.requestUrl!!.encodedPath.startsWith("/api/songs/track%2F"))
        assertNull(request.requestUrl!!.query)
        server.enqueue(response(member(JSONObject().put("playlist", summary("other").put("songs", JSONArray())))))
        assertEquals(Failure.INVALID_RESPONSE, assertThrows(ApiFailure::class.java) { api.playlist("expected") }.failure)
        take()
        val count = server.requestCount
        assertEquals(Failure.REQUEST, assertThrows(ApiFailure::class.java) { api.song("..") }.failure)
        assertEquals(count, server.requestCount)
    }

    @Test fun unifiedLyricsKeepSecondsWordTimingPairedTranslationAndGetResultRef() {
        val api = api()
        server.enqueue(response(catalog(lyric())))
        val doc = api.lyrics("track-one", "opaque.ref+/?&")
        assertEquals("lyrics-workbench", doc.sourceId)
        assertEquals(LyricTranslationStatus.READY, doc.translationState)
        assertTrue(doc.translationSupported)
        assertEquals("word", doc.syncMode)
        assertEquals("你好，世界", doc.lines.single().translation)
        assertEquals(1.25, doc.lines.single().timeSeconds!!, 0.00001)
        assertEquals(3.25, doc.lines.single().endTimeSeconds!!, 0.00001)
        assertEquals(2.25, doc.lines.single().words.last().startTimeSeconds, 0.00001)
        val request = take()
        assertEquals("/api/lyrics", request.requestUrl!!.encodedPath)
        assertEquals("track-one", request.requestUrl!!.queryParameter("songId"))
        assertEquals("opaque.ref+/?&", request.requestUrl!!.queryParameter("resultRef"))
        assertEquals("client-account", request.getHeader("X-FlareTune-Expected-Account"))
        assertEquals("$SESSION_COOKIE_NAME=$token", request.getHeader("Cookie"))
    }

    @Test fun translationPostUsesOnlyCurrentRefAndKeepsPendingStateAndCsrf() {
        val api = api()
        server.enqueue(response(catalog(lyric("pending"))))
        val doc = api.translateLyrics("current.result-ref")
        assertEquals(LyricTranslationStatus.PENDING, doc.translationState)
        assertEquals("旧行内译文", doc.lines.single().translation)
        val request = take()
        assertEquals("POST", request.method)
        assertEquals("/api/lyrics/translation", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals(setOf("resultRef"), body.keys().asSequence().toSet())
        assertEquals("current.result-ref", body.getString("resultRef"))
        assertEquals("client-api-csrf", request.getHeader("X-CSRF-Token"))
        assertEquals("FlareTune", request.getHeader("X-Requested-With"))
        assertEquals(network.address.origin, request.getHeader("Origin"))
        assertEquals("$SESSION_COOKIE_NAME=$token", request.getHeader("Cookie"))
    }

    @Test fun optionalTranslationLanguagesArePreservedWithoutGuessingMissingOrUnknownTags() {
        val api = api()
        val examples = listOf("zh-CN" to "zh", "en" to "zh-Hant", "x-future" to "future-target", "" to "", null to null)
        for ((language, target) in examples) {
            val payload = lyric("idle")
            payload.getJSONObject("translation").apply {
                put("language", language ?: JSONObject.NULL)
                put("targetLanguage", target ?: JSONObject.NULL)
            }
            server.enqueue(response(catalog(payload)))
            val doc = api.lyrics("track-one")
            assertEquals(language, doc.translationLanguage)
            assertEquals(target, doc.translationTargetLanguage)
            take()
        }
        server.enqueue(response(catalog(lyric())))
        val legacy = api.lyrics("track-one")
        assertNull(legacy.translationLanguage)
        assertNull(legacy.translationTargetLanguage)
        assertEquals("你好，世界", legacy.lines.single().translation)
        take()
        val pending = lyric("pending").apply { getJSONObject("translation").put("targetLanguage", "en") }
        server.enqueue(response(catalog(pending)))
        assertEquals("en", api.translateLyrics("current.result-ref").translationTargetLanguage)
        take()
    }

    @Test fun invalidWordTimelineSafelyFallsBackWithoutInventingTimeAndPlainLyricsRemainUntimed() {
        val api = api()
        val malformed = lyric("idle")
        malformed.getJSONArray("lines").getJSONObject(0).getJSONArray("words").getJSONObject(1).put("startTime", 1.5)
        server.enqueue(response(catalog(malformed)))
        val downgraded = api.lyrics("track-one")
        assertEquals("line", downgraded.syncMode)
        assertTrue(downgraded.lines.single().words.isEmpty())
        assertEquals(1.25, downgraded.lines.single().timeSeconds!!, 0.00001); take()
        val plain = lyric("unavailable", "none").put("format", "plain")
            .put("lines", JSONArray().put(JSONObject().put("text", "Untimed text").put("tlyric", "静态译文")))
        plain.getJSONObject("translation").put("supported", false)
        server.enqueue(response(catalog(plain)))
        val doc = api.lyrics("track-one")
        assertNull(doc.lines.single().timeSeconds)
        assertTrue(doc.lines.single().words.isEmpty())
        assertEquals("静态译文", doc.lines.single().translation)
        assertEquals("none", doc.syncMode)
    }

    @Test fun instrumentalUnionNeedsNoOrdinaryResultFieldsAndEmptyNormalSuccessIsRejected() {
        val api = api()
        server.enqueue(response(catalog(JSONObject().put("version", 2).put("status", "not_needed").put("reason", "instrumental")
            .put("source", JSONObject.NULL).put("format", JSONObject.NULL).put("syncMode", "none").put("lines", JSONArray())
            .put("translation", JSONObject().put("supported", false).put("state", "not_needed")))))
        val instrumental = api.lyrics("track-one")
        assertTrue(instrumental.instrumental)
        assertEquals(LyricTranslationStatus.NOT_NEEDED, instrumental.translationState)
        assertNull(instrumental.resultRef); assertTrue(instrumental.lines.isEmpty()); take()
        for (bad in listOf(lyric().put("lines", JSONArray()), lyric().put("version", 1),
            lyric().put("translation", JSONObject().put("supported", true).put("state", "ready").put("lines", JSONArray())))) {
            server.enqueue(response(catalog(bad)))
            assertEquals(Failure.INVALID_RESPONSE, assertThrows(ApiFailure::class.java) { api.lyrics("track-one") }.failure)
            take()
        }
    }

    @Test fun lyric404AndResult409KeepMachineReasonsWithoutRetiringTheAccount() {
        val api = api()
        for (reason in listOf("lyrics_not_found", "song_not_found")) {
            server.enqueue(response(JSONObject().put("code", 404).put("reason", reason).put("data", JSONObject.NULL), 404))
            val error = assertThrows(ApiFailure::class.java) { api.lyrics("track-one") }
            assertEquals(404, error.httpStatus); assertEquals(reason, error.reason)
            assertEquals(Failure.REQUEST, error.failure); assertTrue(network.isActive); take()
        }
        server.enqueue(response(JSONObject().put("code", 409).put("error", "LYRIC_RESULT_RETIRED").put("message", "Reload"), 409))
        val stale = assertThrows(ApiFailure::class.java) { api.lyrics("track-one", "old.ref") }
        assertEquals(409, stale.httpStatus); assertEquals("LYRIC_RESULT_RETIRED", stale.code)
        assertEquals(Failure.REQUEST, stale.failure); assertTrue(network.isActive); take()
        server.enqueue(response(JSONObject().put("error", "<unsafe upstream message>").put("reason", "bad reason"), 502))
        val unsafe = assertThrows(ApiFailure::class.java) { api.lyrics("track-one") }
        assertNull(unsafe.code); assertNull(unsafe.reason); assertEquals(502, unsafe.httpStatus)
    }

    @Test fun exactAccountChangedRetiresCookiesWhileLyricConflictDoesNot() {
        val api = api()
        server.enqueue(response(JSONObject().put("error", "account_changed"), 409))
        val changed = assertThrows(ApiFailure::class.java) { api.playlists() }
        assertEquals(Failure.SESSION_EXPIRED, changed.failure)
        assertEquals("account_changed", changed.code)
        assertFalse(network.isActive)
        assertNull(network.jar.serialized())
    }

    @Test fun fiveHundredUnicodeIdsResolveInBoundedAuthenticatedBatchesWithStableMissingOrder() {
        val api = api()
        val ids = (0 until 500).map { "界".repeat(76) + it.toString().padStart(4, '0') }
        val missing = setOf(ids[10], ids[401])
        val received = java.util.concurrent.CopyOnWriteArrayList<List<String>>()
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.bodySize > 64 * 1024) return response(JSONObject().put("error", "invalid_input"), 400)
                val values = JSONObject(request.body.clone().readUtf8()).getJSONArray("song_ids")
                val batch = (0 until values.length()).map(values::getString)
                received += batch
                val rows = JSONArray()
                batch.asReversed().filterNot { it in missing }.forEach { rows.put(song(it)) }
                return response(catalog(JSONObject().put("songs", rows).put("missing_ids",
                    JSONArray(batch.filter { it in missing }))))
            }
        }
        assertEquals(ids.filterNot { it in missing }, api.resolveSongs(ids).map { it.id })
        assertEquals(ids, received.flatten())
        assertEquals(3, received.size)
        repeat(received.size) {
            val request = take()
            assertEquals("POST", request.method)
            assertEquals("/api/songs/resolve", request.path)
            assertTrue(request.bodySize <= 60 * 1024)
            assertTrue(JSONObject(request.body.readUtf8()).getJSONArray("song_ids").length() <= 200)
            assertEquals("client-account", request.getHeader("X-FlareTune-Expected-Account"))
            assertEquals("client-api-csrf", request.getHeader("X-CSRF-Token"))
            assertEquals("FlareTune", request.getHeader("X-Requested-With"))
            assertEquals(network.address.origin, request.getHeader("Origin"))
            assertEquals("$SESSION_COOKIE_NAME=$token", request.getHeader("Cookie"))
        }
    }

    @Test fun resolveSongsSkipsEmptyInputAndMissingIdsAndUsesOneAuthenticatedBatch() {
        val api = api()
        val before = server.requestCount
        assertTrue(api.resolveSongs(emptyList()).isEmpty())
        assertEquals(before, server.requestCount)
        server.enqueue(response(catalog(JSONObject().put("songs", JSONArray().put(song("second")).put(song("first")).put(song("unrequested")))
            .put("missing_ids", JSONArray().put("gone")))))
        assertEquals(listOf("first", "second"), api.resolveSongs(listOf("first", "gone", "second", "first")).map { it.id })
        val request = take()
        assertEquals("POST", request.method)
        assertEquals("/api/songs/resolve", request.path)
        assertEquals(listOf("first", "gone", "second"), JSONObject(request.body.readUtf8()).getJSONArray("song_ids").let { a -> (0 until a.length()).map(a::getString) })
        assertEquals("client-api-csrf", request.getHeader("X-CSRF-Token"))
        assertEquals("client-account", request.getHeader("X-FlareTune-Expected-Account"))
        assertEquals(Failure.REQUEST, assertThrows(ApiFailure::class.java) { api.resolveSongs(List(501) { "id-$it" }) }.failure)
    }

    @Test fun malformedModernEnvelopeAndLanguageTypesNeverBecomeFakeEmptySuccess() {
        val api = api()
        server.enqueue(response(catalog(JSONObject().put("playlists", JSONArray()))))
        assertEquals(Failure.INVALID_RESPONSE, assertThrows(ApiFailure::class.java) { api.playlists() }.failure); take()
        server.enqueue(response(songPage(JSONArray().put(song(language = 7)))))
        assertEquals(Failure.INVALID_RESPONSE, assertThrows(ApiFailure::class.java) { api.songs("", 0) }.failure)
    }

    @Test fun legacyMusicFakesStillSupportTheNewFilterOverloads() {
        val expected = SongPage(emptyList(), 50, false)
        val albums = AlbumPage(emptyList(), 20, false)
        val fake = object : MusicApi {
            override fun songs(query: String, offset: Int) = expected
            override fun favorites() = emptyList<Song>()
            override fun albums(offset: Int) = albums
        }
        assertSame(expected, fake.songs("", 0, "zh"))
        assertSame(albums, fake.albums(0, "en"))
    }
}
