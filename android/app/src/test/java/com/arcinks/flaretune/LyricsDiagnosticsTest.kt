package com.arcinks.flaretune

import com.arcinks.flaretune.data.ApiFailure
import com.arcinks.flaretune.data.Failure
import com.arcinks.flaretune.data.FlareTuneApi
import com.arcinks.flaretune.data.InstanceAddress
import com.arcinks.flaretune.data.LyricsDiagnostics
import com.arcinks.flaretune.data.SessionNetwork
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Plain JVM tests: all emitted strings are structural, never lyric/request content. */
class LyricsDiagnosticsTest {
    @After fun reset() { LyricsDiagnostics.install(null) }

    @Test fun payloadCredentialsLyricsUrlsAndReferencesCannotReachTheSink() {
        val events = mutableListOf<String>()
        LyricsDiagnostics.install { events += it }
        val data = JSONObject().put("version", 2).put("format", "krc").put("syncMode", "word")
            .put("sourceId", "https://private.invalid/SECRET_URL?token=TOKEN_CANARY")
            .put("songId", "SONG_ID_CANARY").put("title", "SONG_TITLE_CANARY").put("source", "SOURCE_SECRET_CANARY")
            .put("resultRef", "RESULT_REF_CANARY_WITH_COOKIE").put("csrfToken", "CSRF_CANARY")
            .put("cookie", "__Host-ft_session_COOKIE_CANARY").put("secret", "SERVER_SECRET_CANARY")
            .put("lrc", "LRC_TEXT_CANARY").put("lines", JSONArray()
                .put(JSONObject().put("text", "LYRIC_TEXT_CANARY").put("tlyric", "TRANSLATED_TEXT_CANARY"))
                .put(JSONObject().put("text", " ")).put(JSONObject().put("private", "MISSING_TEXT_CANARY")))
            .put("translation", JSONObject().put("supported", true).put("state", "ready")
                .put("lines", JSONArray().put("TRANSLATION_BODY_CANARY").put("").put("")))
        LyricsDiagnostics.parsed("read", JSONObject().put("code", 200).put("data", data), failed = true)
        LyricsDiagnostics.http("read", 404, "LYRIC_RESULT_INVALID", "lyrics_not_found")
        assertEquals(2, events.size)
        val combined = events.joinToString()
        for (canary in listOf("SECRET_URL", "TOKEN_CANARY", "SONG_ID_CANARY", "SONG_TITLE_CANARY", "SOURCE_SECRET_CANARY",
            "RESULT_REF_CANARY", "CSRF_CANARY", "COOKIE_CANARY", "SERVER_SECRET_CANARY", "LRC_TEXT_CANARY",
            "LYRIC_TEXT_CANARY", "TRANSLATED_TEXT_CANARY", "TRANSLATION_BODY_CANARY", "MISSING_TEXT_CANARY", "https://")) {
            assertFalse(canary, combined.contains(canary))
        }
        val summary = JSONObject(events.first())
        assertEquals("parsed", summary.getString("event"))
        assertEquals("read", summary.getString("operation"))
        assertTrue(summary.getBoolean("failed"))
        assertEquals("2", summary.getString("version"))
        assertEquals("krc", summary.getString("format"))
        assertEquals("word", summary.getString("syncMode"))
        assertEquals("other", summary.getString("sourceId"))
        assertEquals(3, summary.getInt("lineCount"))
        assertEquals(2, summary.getInt("blankTextCount"))
        assertEquals("RESULT_REF_CANARY_WITH_COOKIE".length, summary.getInt("refLength"))
        assertEquals(3, summary.getInt("translationLineCount"))
        assertEquals(setOf("event", "operation", "failed", "version", "format", "syncMode", "sourceId", "lineCount",
            "blankTextCount", "refLength", "hasSourceId", "hasTranslation", "translationSupported", "translationState", "translationLineCount"), summary.keys().asSequence().toSet())
    }

    @Test fun unknownEnumsAndIdentifierShapedSecretsAreClassifiedInsteadOfEchoed() {
        val events = mutableListOf<String>()
        LyricsDiagnostics.install { events += it }
        LyricsDiagnostics.http("translation", 503, "ALPHANUMERIC_SECRET_CANARY", "https://secret.invalid/private")
        LyricsDiagnostics.parsed("translation", JSONObject().put("version", "VERSION_SECRET_CANARY")
            .put("format", "FORMAT_SECRET_CANARY").put("syncMode", JSONObject().put("secret", "SYNC_SECRET_CANARY"))
            .put("translation", JSONObject().put("supported", "SUPPORTED_SECRET_CANARY").put("state", "STATE_SECRET_CANARY")), false)
        assertEquals("other", JSONObject(events[0]).getString("code"))
        assertEquals("other", JSONObject(events[0]).getString("reason"))
        val shape = JSONObject(events[1])
        assertEquals("invalid", shape.getString("version"))
        assertEquals("other", shape.getString("format"))
        assertEquals("invalid", shape.getString("syncMode"))
        assertEquals("other", shape.getString("translationState"))
        assertTrue(shape.isNull("translationSupported"))
        assertEquals(-1, shape.getInt("lineCount"))
        assertFalse(events.joinToString().contains("CANARY"))
        LyricsDiagnostics.http("https://secret.invalid/OPERATION_CANARY", 404)
        LyricsDiagnostics.parsed("OPERATION_CANARY", valid(), true)
        LyricsDiagnostics.http("read", -1)
        assertEquals(2, events.size)
    }

    @Test fun uninstallDisablesAllFurtherEmissionAndInstrumentalSummaryHasOnlyShape() {
        val events = mutableListOf<String>()
        LyricsDiagnostics.install { events += it }
        LyricsDiagnostics.parsed("read", JSONObject().put("data", JSONObject().put("version", 2)
            .put("source", JSONObject.NULL).put("format", JSONObject.NULL).put("syncMode", "none")
            .put("lines", JSONArray()).put("translation", JSONObject().put("supported", false).put("state", "not_needed"))), false)
        val shape = JSONObject(events.single())
        assertEquals(0, shape.getInt("lineCount"))
        assertEquals(0, shape.getInt("blankTextCount"))
        assertEquals(0, shape.getInt("refLength"))
        assertFalse(shape.getBoolean("hasSourceId"))
        assertEquals("not_needed", shape.getString("translationState"))
        LyricsDiagnostics.install(null)
        LyricsDiagnostics.http("read", 500)
        LyricsDiagnostics.parsed("read", valid(), true)
        assertEquals(1, events.size)
    }

    @Test fun concurrentEmittersUseOneSerializedSinkAndNeverLeakRawValues() {
        val count = AtomicInteger()
        val inside = AtomicInteger()
        val overlap = AtomicBoolean()
        LyricsDiagnostics.install { event ->
            if (inside.incrementAndGet() != 1) overlap.set(true)
            try {
                assertEquals("read", JSONObject(event).getString("operation"))
                count.incrementAndGet()
                Thread.yield()
            } finally { inside.decrementAndGet() }
        }
        val executor = Executors.newFixedThreadPool(4)
        try {
            val tasks = (0 until 4).map { executor.submit { repeat(40) { LyricsDiagnostics.http("read", 200) } } }
            tasks.forEach { it.get(5, TimeUnit.SECONDS) }
            assertEquals(160, count.get())
            assertFalse(overlap.get())
        } finally { executor.shutdownNow() }
    }

    @Test fun throwingSinkCannotChangeSuccessfulFailedParseOrHttpFailureApiResults() = withApi { api, server ->
        LyricsDiagnostics.install { throw AssertionError("DIAGNOSTIC_SINK_SECRET_CANARY") }
        server.enqueue(response(valid()))
        assertEquals("LYRIC_BODY_CANARY", api.lyrics("fixture-song").lines.single().text)
        server.enqueue(response(valid().apply { getJSONObject("data").put("version", "2") }))
        assertEquals(Failure.INVALID_RESPONSE, assertThrows(ApiFailure::class.java) { api.lyrics("fixture-song") }.failure)
        server.enqueue(response(JSONObject().put("code", 404).put("reason", "lyrics_not_found"), 404))
        val missing = assertThrows(ApiFailure::class.java) { api.lyrics("fixture-song") }
        assertEquals(Failure.REQUEST, missing.failure)
        assertEquals(404, missing.httpStatus)
        assertEquals("lyrics_not_found", missing.reason)
    }

    @Test fun actualHttp200ParseFailureEmitsAStructureSummaryWithoutPayloadText() = withApi { api, server ->
        val events = mutableListOf<String>()
        LyricsDiagnostics.install { events += it }
        server.enqueue(response(valid().apply { getJSONObject("data").remove("sourceId") }))
        assertEquals(Failure.INVALID_RESPONSE, assertThrows(ApiFailure::class.java) { api.lyrics("fixture-song") }.failure)
        val http = JSONObject(events.first())
        assertEquals(200, http.getInt("status"))
        val parsed = JSONObject(events.last())
        assertTrue(parsed.getBoolean("failed"))
        assertFalse(parsed.getBoolean("hasSourceId"))
        assertEquals(1, parsed.getInt("lineCount"))
        assertFalse(events.joinToString().contains("CANARY"))
        assertFalse(events.joinToString().contains("fixture-song"))
    }

    private fun valid() = JSONObject().put("code", 200).put("data", JSONObject().put("version", 2)
        .put("source", "manual").put("format", "lrc").put("syncMode", "line").put("sourceId", "file")
        .put("resultRef", "RESULT_REF_CANARY").put("lines", JSONArray().put(JSONObject().put("text", "LYRIC_BODY_CANARY").put("time", 1.25)))
        .put("translation", JSONObject().put("supported", false).put("state", "unavailable")))
    private fun response(body: JSONObject, status: Int = 200) = MockResponse().setResponseCode(status)
        .setHeader("Content-Type", "application/json").setBody(body.toString())

    private fun withApi(block: (FlareTuneApi, MockWebServer) -> Unit) {
        val certificate = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1").addSubjectAlternativeName("::1").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
        val base = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
        val network = SessionNetwork(InstanceAddress.parse(server.url("/").toString()), baseClient = base)
        try { block(FlareTuneApi(network), server) }
        finally {
            network.retire()
            server.shutdown()
            base.dispatcher.executorService.shutdownNow()
            base.connectionPool.evictAll()
        }
    }
}
