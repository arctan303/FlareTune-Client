package com.arcinks.flaretune

import com.arcinks.flaretune.data.*
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

class ContinueStatsApiTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var network: SessionNetwork
    private lateinit var api: FlareTuneApi
    private val token = "S".repeat(43)
    private val now = 1_800_000_000_000L
    private val dayMs = 86_400_000L

    @Before fun setup() {
        val certificate = HeldCertificate.Builder().commonName("localhost")
            .addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1")
            .addSubjectAlternativeName("::1").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
        client = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
        network = SessionNetwork(InstanceAddress.parse(server.url("/").toString()), baseClient = client)
        server.enqueue(response(JSONObject().put("authenticated", true).put("mustChangePassword", false)
            .put("csrfToken", "continue-csrf").put("user", JSONObject().put("accountId", "continue-account")
                .put("username", "listener").put("displayName", "Listener")))
            .setHeader("Set-Cookie", "$SESSION_COOKIE_NAME=$token; Path=/; Secure; HttpOnly; Max-Age=3600"))
        api = FlareTuneApi(network)
        api.login("listener", "not-saved")
        take()
    }

    @After fun teardown() {
        network.retire()
        server.shutdown()
        client.dispatcher.executorService.shutdownNow()
        client.connectionPool.evictAll()
    }

    private fun response(body: JSONObject) = MockResponse().setResponseCode(200)
        .setHeader("Content-Type", "application/json").setBody(body.toString())
    private fun take(): RecordedRequest = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS))
    private fun window(days: Int) = JSONObject().put("days", days).put("from", now - days * dayMs).put("to", now)
    private fun row(id: String = "track", count: Long = 3) = JSONObject()
        .put("id", id).put("title", "Song $id").put("artist", "Artist").put("album", "Album")
        .put("duration", 180).put("audio_url", "/media/audio/$id.flac")
        .put("cover_url", "/media/cover/$id.jpg").put("play_count", count).put("last_played_at", now - 10_000)

    private fun data(days: Int?, summary: Boolean = true, count: Long = 3): JSONObject {
        val body = JSONObject().put("songs", JSONArray().put(row(count = count))).put("topAlbums", JSONArray())
        if (summary) body.put("view", "summary")
        else body.put("playCounts", JSONObject().put("track", count)).put("totalPlays", count).put("totalUniqueSongs", 1)
        if (days != null) body.put("window", window(days))
        return body
    }
    private fun enqueue(data: JSONObject) {
        server.enqueue(response(JSONObject().put("ok", true).put("data", data)))
    }
    private fun assertFailure(expected: Failure, action: () -> Unit) {
        try {
            action()
            fail("Expected $expected")
        } catch (error: ApiFailure) {
            assertEquals(expected, error.failure)
        }
    }

    @Test fun oneDayAndSevenDayQueriesRetainTheirDistinctWindowAndCounts() {
        for ((days, count) in listOf(1 to 2L, 7 to 9L)) {
            enqueue(data(days, count = count))
            val result = api.playStats(limit = 30, summaryOnly = true, days = days)
            val request = take()
            assertEquals("GET", request.method)
            assertEquals("/api/account/play-stats", request.requestUrl!!.encodedPath)
            assertEquals(days.toString(), request.requestUrl!!.queryParameter("days"))
            assertEquals("30", request.requestUrl!!.queryParameter("limit"))
            assertEquals("summary", request.requestUrl!!.queryParameter("view"))
            assertEquals("continue-account", request.getHeader("X-FlareTune-Expected-Account"))
            assertEquals("$SESSION_COOKIE_NAME=$token", request.getHeader("Cookie"))
            assertEquals(0L, request.bodySize)
            assertTrue(result.summaryOnly)
            assertEquals(count, result.songs.single().playCount)
            assertEquals(days, result.window!!.days)
            assertEquals(now - days * dayMs, result.window!!.fromMs)
            assertEquals(now, result.window!!.toMs)
            assertNull(result.playCounts)
        }
    }

    @Test fun aWindowedFullResponseKeepsItsOwnTotalsAndDoesNotRequireSummaryView() {
        enqueue(data(7, summary = false, count = 8))
        val result = api.playStats(limit = 50, summaryOnly = false, days = 7)
        val request = take()
        assertNull(request.requestUrl!!.queryParameter("view"))
        assertEquals("7", request.requestUrl!!.queryParameter("days"))
        assertFalse(result.summaryOnly)
        assertEquals(7, result.window!!.days)
        assertEquals(mapOf("track" to 8L), result.playCounts)
        assertEquals(8L, result.totalPlays)
        assertEquals(1, result.totalUniqueSongs)
    }

    @Test fun cumulativeResponsesStayCompatibleWithoutAWindowObject() {
        enqueue(data(null, summary = false, count = 84))
        val result = api.playStats(limit = 20, summaryOnly = false)
        val request = take()
        assertNull(request.requestUrl!!.queryParameter("days"))
        assertNull(result.window)
        assertEquals(84L, result.songs.single().playCount)
        assertEquals(84L, result.totalPlays)
        enqueue(data(null))
        val summary = api.playStats(limit = 20, summaryOnly = true)
        assertNull(summary.window)
        assertTrue(summary.summaryOnly)
        assertNull(take().requestUrl!!.queryParameter("days"))
    }

    @Test fun aLegacyServerIgnoringDaysCannotMasqueradeAsRecentStatistics() {
        enqueue(data(null))
        assertFailure(Failure.INVALID_RESPONSE) { api.playStats(limit = 30, summaryOnly = true, days = 7) }
        take()
        assertTrue("An unsupported window must not retire the account session", network.isActive)
    }

    @Test fun mismatchedIncompleteAndMalformedWindowsAreRejected() {
        val invalidWindows = listOf<Any>(
            JSONObject.NULL, "7", window(1), window(7).put("days", 7.5), window(7).put("days", "7"),
            window(7).apply { remove("from") }, window(7).apply { remove("to") },
            window(7).put("from", now), window(7).put("from", now + 1),
            window(7).put("from", now - 7 * dayMs + 1), window(7).put("to", "not-a-timestamp"),
        )
        for (invalid in invalidWindows) {
            enqueue(data(7).put("window", invalid))
            assertFailure(Failure.INVALID_RESPONSE) { api.playStats(limit = 30, summaryOnly = true, days = 7) }
            take()
            assertTrue(network.isActive)
        }
    }

    @Test fun invalidWindowRequestsAreRejectedBeforeNetworkIo() {
        val initialRequests = server.requestCount
        for (days in listOf(-1, 0, 31, Int.MAX_VALUE)) {
            assertFailure(Failure.REQUEST) { api.playStats(limit = 30, summaryOnly = true, days = days) }
        }
        assertEquals(initialRequests, server.requestCount)
    }
}
