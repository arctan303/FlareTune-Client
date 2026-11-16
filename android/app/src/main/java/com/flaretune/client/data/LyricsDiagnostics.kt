package com.flaretune.client.data

import org.json.JSONObject

/** Opt-in structural diagnostics. No request identity, content, URLs or opaque references. */
internal object LyricsDiagnostics {
    private val lock = Any()
    @Volatile private var sink: ((String) -> Unit)? = null
    private val operations = setOf("read", "translation")
    // Closed regex vocabulary prevents an identifier-shaped credential from being logged as an error code.
    private val codes = Regex("(?:LYRIC_RESULT_INVALID|LYRIC_RESULT_RETIRED|LYRIC_RESULT_CHANGED|LYRIC_RESULT_UNAVAILABLE|LYRIC_SERVICE_UNAVAILABLE|LYRICS_INVOCATION_BUDGET_EXHAUSTED|LYRIC_SOURCE_UNAVAILABLE|LYRIC_SOURCE_FAILED|LYRIC_SONG_DELETED|PLUGIN_SERVICE_RETIRED|PLUGIN_SERVICE_CONTEXT_MISMATCH|PLUGIN_NOT_INSTALLED|PLUGIN_UNAVAILABLE|PLUGIN_NOT_FOUND|PLUGIN_BUILD_MISMATCH|PLUGIN_OPERATION_RETIRED|authentication_required|forbidden|csrf_rejected|account_changed|invalid_input|invalid_setting|method_not_allowed|revision_conflict|service_unavailable|source_unavailable|phase_not_ready|not_found)")
    private val reasons = Regex("(?:lyrics_not_found|song_not_found|source_unavailable|instrumental)")

    fun install(sink: ((String) -> Unit)?) { synchronized(lock) { this.sink = sink } }

    fun http(operation: String, status: Int, code: String? = null, reason: String? = null) {
        if (sink == null || operation !in operations || status !in 100..599) return
        safely {
            JSONObject().put("event", "http").put("operation", operation).put("status", status).apply {
                code?.let { put("code", if (codes.matches(it)) it else "other") }
                reason?.let { put("reason", if (reasons.matches(it)) it else "other") }
            }
        }
    }

    /** Accepts the full response envelope; only the data object's structural facts are projected. */
    fun parsed(operation: String, payload: JSONObject, failed: Boolean) {
        if (sink == null || operation !in operations) return
        safely {
            val data = payload.optJSONObject("data") ?: payload
            val lines = data.optJSONArray("lines")
            val translation = data.optJSONObject("translation")
            val sourceId = data.opt("sourceId") as? String
            JSONObject().put("event", "parsed").put("operation", operation).put("failed", failed)
                .put("version", when (val version = data.opt("version")) {
                    null, JSONObject.NULL -> "missing"
                    is Number -> when (version.toDouble()) { 1.0 -> "1"; 2.0 -> "2"; else -> "other" }
                    else -> "invalid"
                })
                .put("format", category(data.opt("format"), setOf("krc", "lyricsfile", "lrc", "plain")))
                .put("syncMode", category(data.opt("syncMode"), setOf("word", "line", "none")))
                .put("sourceId", sourceId?.takeIf { it in setOf("file", "lyrics-workbench", "youtube-music") } ?: "other")
                .put("lineCount", lines?.length() ?: -1)
                .put("blankTextCount", if (lines == null) -1 else (0 until lines.length()).count {
                    (lines.optJSONObject(it)?.opt("text") as? String).isNullOrBlank()
                })
                .put("refLength", (data.opt("resultRef") as? String)?.length ?: 0)
                .put("hasSourceId", !sourceId.isNullOrBlank())
                .put("hasTranslation", translation != null)
                .put("translationSupported", (translation?.opt("supported") as? Boolean) ?: JSONObject.NULL)
                .put("translationState", category(translation?.opt("state"), setOf("idle", "pending", "ready", "failed", "unavailable", "not_needed")))
                .put("translationLineCount", translation?.optJSONArray("lines")?.length() ?: -1)
        }
    }

    private fun category(value: Any?, allowed: Set<String>): String = when (value) {
        null, JSONObject.NULL -> "missing"
        is String -> if (value in allowed) value else "other"
        else -> "invalid"
    }

    private fun safely(summary: () -> JSONObject) {
        // Diagnostic construction and user-provided sinks must never change API behavior.
        try {
            val line = summary().toString()
            synchronized(lock) { try { sink?.invoke(line) } catch (_: Throwable) { } }
        } catch (_: Throwable) { }
    }
}
