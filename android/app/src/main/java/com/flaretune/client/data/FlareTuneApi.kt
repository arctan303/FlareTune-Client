package com.flaretune.client.data

import com.flaretune.client.model.*
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

// Machine-readable server details are retained without exposing arbitrary upstream messages.
enum class Failure { INVALID_URL, NOT_READY, NETWORK, INVALID_LOGIN, SESSION_EXPIRED, RATE_LIMITED, INVALID_RESPONSE, STORAGE, REQUEST, FORBIDDEN, RETIRED }
class ApiFailure(
    val failure: Failure,
    val httpStatus: Int? = null,
    val code: String? = null,
    val reason: String? = null,
) : IOException(failure.name)

interface MusicApi {
    fun songs(query: String, offset: Int): SongPage
    fun songs(query: String, offset: Int, language: String): SongPage = songs(query, offset)
    fun favorites(): List<Song>
    fun albums(offset: Int = 0): AlbumPage = throw ApiFailure(Failure.REQUEST)
    fun albums(offset: Int, language: String): AlbumPage = albums(offset)
    fun album(id: String): AlbumDetail = throw ApiFailure(Failure.REQUEST)
    fun playlists(): List<PlaylistSummary> = throw ApiFailure(Failure.REQUEST)
    fun playlist(id: String): PlaylistDetail = throw ApiFailure(Failure.REQUEST)
    fun song(id: String): Song = throw ApiFailure(Failure.REQUEST)
    fun resolveSongs(ids: List<String>): List<Song> = ids.map { song(it) }
    fun lyrics(songId: String, resultRef: String? = null): LyricDocument = throw ApiFailure(Failure.REQUEST)
    fun translateLyrics(resultRef: String): LyricDocument = throw ApiFailure(Failure.REQUEST)
}

interface AccountApi : MusicApi {
    fun changePassword(current: String, replacement: String)
    fun logout()
}

class FlareTuneApi(val network: SessionNetwork) : AccountApi {
    private val address get() = network.address
    var session: AuthSession? = null
        private set

    private fun request(path: String, body: JSONObject? = null, lyricOperation: String? = null): JSONObject = request(address.endpoint(path), body, lyricOperation)

    private fun request(url: HttpUrl, body: JSONObject? = null, lyricOperation: String? = null): JSONObject {
        if (!address.sameOrigin(url)) throw ApiFailure(Failure.REQUEST)
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        session?.let { builder.header("X-FlareTune-Expected-Account", it.account.id) }
        if (body != null) {
            builder.header("Origin", address.origin).header("X-Requested-With", "FlareTune")
            session?.let { builder.header("X-CSRF-Token", it.csrfToken) }
            builder.post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        }
        try {
            network.apiClient.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    // Error text is untrusted. Only bounded JSON identifier fields cross this boundary.
                    val source = response.body?.source()
                    source?.request(64L * 1024 + 1)
                    val error = if (source != null && source.buffer.size <= 64L * 1024) {
                        try { JSONObject(source.buffer.readUtf8()) } catch (_: Exception) { null }
                    } else null
                    val code = machineField(error, "error") ?: machineField(error, "code")
                    val reason = machineField(error, "reason")
                    val accountChanged = response.code == 409 && code == "account_changed"
                    if (accountChanged) network.retire()
                    val failure = when {
                        accountChanged -> Failure.SESSION_EXPIRED
                        response.code == 401 -> if (url.encodedPath == "/api/auth/login") Failure.INVALID_LOGIN else Failure.SESSION_EXPIRED
                        response.code == 403 -> Failure.FORBIDDEN
                        response.code == 429 -> Failure.RATE_LIMITED
                        response.code == 503 -> Failure.NOT_READY
                        else -> Failure.REQUEST
                    }
                    lyricOperation?.let { LyricsDiagnostics.http(it, response.code, code, reason) }
                    throw ApiFailure(failure, response.code, code, reason)
                }
                lyricOperation?.let { LyricsDiagnostics.http(it, response.code) }
                val source = response.body?.source() ?: invalidResponse()
                source.request(8L * 1024 * 1024 + 1)
                if (source.buffer.size > 8L * 1024 * 1024) invalidResponse()
                val bytes = source.buffer.readByteArray()
                return try { JSONObject(bytes.toString(Charsets.UTF_8)) }
                catch (_: Exception) { invalidResponse() }
            }
        } catch (e: ApiFailure) { throw e }
        catch (_: IOException) { throw ApiFailure(if (network.isActive) Failure.NETWORK else Failure.RETIRED) }
    }

    private fun machineField(json: JSONObject?, field: String): String? = (json?.opt(field) as? String)
        ?.takeIf { it.matches(Regex("[A-Za-z0-9_]{1,100}")) }

    fun checkReady() {
        if (request("/api/instance/status").optString("state") != "ready") throw ApiFailure(Failure.NOT_READY)
    }

    fun login(username: String, password: String): AuthSession {
        val result = request("/api/auth/login", JSONObject().put("username", username.trim()).put("password", password))
        return parseSession(result).also { session = it }
    }

    fun restore(): AuthSession = parseSession(request("/api/auth/session")).also { session = it }

    private fun parseSession(json: JSONObject): AuthSession {
        if (!json.optBoolean("authenticated")) throw ApiFailure(Failure.SESSION_EXPIRED)
        val user = json.optJSONObject("user") ?: invalidResponse()
        val id = user.optString("accountId")
        val csrf = json.optString("csrfToken")
        if (id.isBlank() || csrf.isBlank() || user.optString("username").isBlank()) invalidResponse()
        return AuthSession(Account(id, user.getString("username"), user.optString("displayName").ifBlank { user.getString("username") }),
            csrf, json.optBoolean("mustChangePassword"))
    }

    override fun changePassword(current: String, replacement: String) {
        request("/api/auth/change-password", JSONObject().put("currentPassword", current).put("newPassword", replacement))
    }

    override fun logout() { request("/api/auth/logout", JSONObject()) }

    override fun songs(query: String, offset: Int): SongPage = songs(query, offset, "all")

    override fun songs(query: String, offset: Int, language: String): SongPage {
        if (offset !in 0..2000) throw ApiFailure(Failure.REQUEST)
        val filter = languageFilter(language)
        val searching = query.isNotBlank()
        val builder = if (!searching) {
            if (offset % 50 != 0) throw ApiFailure(Failure.REQUEST)
            address.endpoint("/api/songs").newBuilder()
                .addQueryParameter("language", filter ?: ALL_LANGUAGES)
                .addQueryParameter("page", (offset / 50 + 1).toString()).addQueryParameter("limit", "50")
        } else address.endpoint("/api/songs/search").newBuilder()
            .addQueryParameter("q", query.trim().take(200)).addQueryParameter("limit", "50")
            .addQueryParameter("offset", offset.toString()).also { if (filter != null) it.addQueryParameter("language", filter) }
        val data = catalogData(request(builder.build()))
        val array = data.optJSONArray("songs") ?: invalidResponse()
        val serverHasMore = data.opt("hasMore") as? Boolean ?: (array.length() == 50)
        val next = if (data.has("nextOffset") && !data.isNull("nextOffset")) {
            nonNegativeInt(data, "nextOffset", offset + 50)
        } else offset + 50
        if (serverHasMore && next <= offset) invalidResponse()
        if (searching && data.has("nextOffset") && data.isNull("nextOffset") && serverHasMore) invalidResponse()
        val capped = data.opt("capped") as? Boolean ?: false
        return SongPage(parseSongs(array), next, serverHasMore && next <= 2000 && !capped,
            (data.optJSONArray("sourceWarnings")?.length() ?: 0) > 0, capped || (serverHasMore && next > 2000))
    }

    override fun favorites(): List<Song> {
        val data = catalogData(request("/api/playlists/favorite"))
        return parseSongs(data.optJSONArray("songs") ?: invalidResponse())
    }

    override fun albums(offset: Int): AlbumPage = albums(offset, "all")

    override fun albums(offset: Int, language: String): AlbumPage {
        if (offset !in 0..2000) throw ApiFailure(Failure.REQUEST)
        val filter = languageFilter(language)
        val url = address.endpoint("/api/albums").newBuilder().addQueryParameter("limit", "20")
            .addQueryParameter("offset", offset.toString()).also { if (filter != null) it.addQueryParameter("language", filter) }.build()
        val data = catalogData(request(url))
        val rows = data.optJSONArray("albums") ?: invalidResponse()
        val albums = (0 until rows.length()).map { parseAlbum(rows.optJSONObject(it) ?: invalidResponse()) }.distinctBy { it.id }
        val next = offset + 20
        val serverHasMore = data.opt("hasMore") as? Boolean ?: invalidResponse()
        return AlbumPage(albums, next, serverHasMore && next <= 2000, serverHasMore && next > 2000)
    }

    override fun album(id: String): AlbumDetail {
        if (!validAlbumId(id)) throw ApiFailure(Failure.REQUEST)
        val data = catalogData(request(address.endpoint("/api/albums").newBuilder().addPathSegment(id).build()))
        val album = parseAlbum(data)
        if (album.id != id) invalidResponse()
        val songs = parseSongs(data.optJSONArray("songs") ?: invalidResponse())
            .map { if (it.coverUrl == null) it.copy(coverUrl = album.coverUrl) else it }
        return AlbumDetail(album, songs)
    }

    override fun playlists(): List<PlaylistSummary> {
        val array = memberData(request("/api/account/playlists")).optJSONArray("playlists") ?: invalidResponse()
        return (0 until array.length()).map { parsePlaylist(array.optJSONObject(it) ?: invalidResponse()) }
    }

    override fun playlist(id: String): PlaylistDetail {
        checkId(id, 240)
        val data = memberData(request(address.endpoint("/api/account/playlists").newBuilder().addPathSegment(id).build()))
            .optJSONObject("playlist") ?: invalidResponse()
        val playlist = parsePlaylist(data)
        if (playlist.id != id) invalidResponse()
        val songs = data.optJSONArray("songs") ?: invalidResponse()
        val entries = (0 until songs.length()).mapNotNull { index ->
            val row = songs.optJSONObject(index) ?: invalidResponse()
            parseSong(row)?.let { PlaylistSong(it, nonNegativeInt(row, "sortOrder", 0), nonNegativeLong(row, "addedAt", 0)) }
        }.distinctBy { it.song.id }.sortedBy { it.sortOrder }
        return PlaylistDetail(playlist, entries)
    }

    override fun song(id: String): Song {
        checkId(id, 256)
        val song = parseSong(catalogData(request(address.endpoint("/api/songs").newBuilder().addPathSegment(id).build()))) ?: invalidResponse()
        if (song.id != id) invalidResponse()
        return song
    }

    override fun resolveSongs(ids: List<String>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        if (ids.size > 500) throw ApiFailure(Failure.REQUEST)
        ids.forEach { checkId(it, 80) }
        val requested = ids.distinct()
        val batches = mutableListOf<List<String>>()
        var batch = mutableListOf<String>()
        fun body(values: List<String>) = JSONObject().put("song_ids", JSONArray(values))
        requested.forEach { id ->
            val candidate = batch + id
            // The host accepts 64KiB of UTF-8, not 500 arbitrarily large IDs.
            if (batch.isNotEmpty() && (candidate.size > 200 ||
                    body(candidate).toString().toByteArray(Charsets.UTF_8).size > 60 * 1024)) {
                batches += batch.toList()
                batch = mutableListOf()
            }
            batch += id
        }
        if (batch.isNotEmpty()) batches += batch.toList()
        val songs = mutableMapOf<String, Song>()
        batches.forEach { values ->
            val payload = body(values)
            if (payload.toString().toByteArray(Charsets.UTF_8).size > 60 * 1024) throw ApiFailure(Failure.REQUEST)
            // Each request goes through the same origin/account/CSRF/session guards.
            val data = catalogData(request("/api/songs/resolve", payload))
            val allowed = values.toSet()
            parseSongs(data.optJSONArray("songs") ?: invalidResponse()).filter { it.id in allowed }
                .forEach { songs[it.id] = it }
        }
        return requested.mapNotNull(songs::get)
    }

    override fun lyrics(songId: String, resultRef: String?): LyricDocument {
        checkId(songId, 128)
        if (songId.any { it == '/' || it == '\\' }) throw ApiFailure(Failure.REQUEST)
        val url = address.endpoint("/api/lyrics").newBuilder().addQueryParameter("songId", songId)
            .also { if (resultRef != null) { checkRef(resultRef); it.addQueryParameter("resultRef", resultRef) } }.build()
        return lyricPayload("read") { request(url, lyricOperation = "read") }
    }

    override fun translateLyrics(resultRef: String): LyricDocument {
        checkRef(resultRef)
        return lyricPayload("translation") { request("/api/lyrics/translation", JSONObject().put("resultRef", resultRef), "translation") }
    }

    private fun lyricPayload(operation: String, load: () -> JSONObject): LyricDocument {
        var payload: JSONObject? = null
        try {
            payload = load()
            val document = parseLyrics(catalogData(payload))
            LyricsDiagnostics.parsed(operation, payload, failed = false)
            return document
        } catch (error: ApiFailure) {
            payload?.let { LyricsDiagnostics.parsed(operation, it, failed = true) }
            throw error
        }
    }
    private fun languageFilter(language: String): String? {
        if (language == "all") return null
        if (language !in ALL_LANGUAGES.split(',')) throw ApiFailure(Failure.REQUEST)
        return language
    }

    private fun catalogData(json: JSONObject): JSONObject {
        if ((json.opt("code") as? Number)?.toDouble() != 200.0) invalidResponse()
        return json.optJSONObject("data") ?: invalidResponse()
    }

    private fun memberData(json: JSONObject): JSONObject {
        if (json.opt("ok") != true) invalidResponse()
        return json.optJSONObject("data") ?: invalidResponse()
    }

    private fun parsePlaylist(json: JSONObject): PlaylistSummary {
        val id = requiredString(json, "id")
        val kind = requiredString(json, "kind")
        if (id.length > 240 || kind !in setOf("favorite", "regular")) invalidResponse()
        val covers = json.optJSONArray("previewCovers") ?: JSONArray()
        return PlaylistSummary(id, requiredString(json, "name"), kind, nullableString(json, "description") ?: "",
            nonNegativeInt(json, "songCount", 0), nonNegativeLong(json, "revision"),
            (0 until covers.length()).mapNotNull { index ->
                val raw = covers.opt(index) as? String ?: invalidResponse()
                address.media(raw)
            }, nullableString(json, "customCoverUrl")?.let(address::media),
            nonNegativeLong(json, "createdAt", 0), nonNegativeLong(json, "updatedAt", 0))
    }

    private fun validAlbumId(id: String): Boolean = id.length in 1..3200 && id.matches(Regex("[A-Za-z0-9_-]+"))

    private fun parseAlbum(json: JSONObject): Album {
        val id = requiredString(json, "id")
        val title = requiredString(json, "title").trim()
        val artist = requiredString(json, "artist").trim()
        if (!validAlbumId(id) || title.isBlank() || artist.isBlank()) invalidResponse()
        return Album(id, title, artist, nullableString(json, "coverUrl")?.let(address::media), nonNegativeInt(json, "trackCount", 0))
    }

    private fun parseSongs(array: JSONArray): List<Song> = (0 until array.length()).mapNotNull { index ->
        array.optJSONObject(index)?.let(::parseSong)
    }.distinctBy { it.id }

    private fun parseSong(row: JSONObject): Song? {
        val id = row.opt("id") as? String ?: return null
        val audio = nullableString(row, "audio_url")?.let(address::media) ?: return null
        if (id.isBlank()) return null
        return Song(id, (row.opt("title") as? String).orEmpty().ifBlank { id }, (row.opt("artist") as? String).orEmpty(),
            (row.opt("album") as? String).orEmpty(), row.optDouble("duration", 0.0).takeIf { it.isFinite() && it > 0 } ?: 0.0,
            audio, nullableString(row, "cover_url")?.let(address::media), nullableString(row, "language"))
    }

    private fun parseLyrics(data: JSONObject): LyricDocument {
        if ((data.opt("version") as? Number)?.toDouble() != 2.0) invalidResponse()
        val rows = data.optJSONArray("lines") ?: invalidResponse()
        if (data.optString("status") == "not_needed" && data.optString("reason") == "instrumental") {
            val translation = data.optJSONObject("translation") ?: invalidResponse()
            if (rows.length() != 0 || data.opt("syncMode") != "none" || !data.isNull("source") || !data.isNull("format") ||
                translation.opt("supported") != false || translation.opt("state") != "not_needed") invalidResponse()
            return LyricDocument(instrumental = true, translationState = LyricTranslationStatus.NOT_NEEDED)
        }
        if (rows.length() !in 1..2000) invalidResponse()
        if (requiredString(data, "format") !in setOf("krc", "lyricsfile", "lrc", "plain")) invalidResponse()
        requiredString(data, "source")
        val requestedMode = requiredString(data, "syncMode")
        if (requestedMode !in setOf("word", "line", "none")) invalidResponse()
        val ref = requiredString(data, "resultRef").also { if (it.length > 8500) invalidResponse() }
        val sourceId = requiredString(data, "sourceId")
        val translation = data.optJSONObject("translation") ?: invalidResponse()
        val supported = translation.opt("supported") as? Boolean ?: invalidResponse()
        val translationState = when (requiredString(translation, "state")) {
            "idle" -> LyricTranslationStatus.IDLE
            "pending" -> LyricTranslationStatus.PENDING
            "ready" -> LyricTranslationStatus.READY
            "failed" -> LyricTranslationStatus.FAILED
            "unavailable" -> LyricTranslationStatus.UNAVAILABLE
            "not_needed" -> LyricTranslationStatus.NOT_NEEDED
            else -> invalidResponse()
        }
        if (data.has("translationState") && data.opt("translationState") != translation.getString("state")) invalidResponse()
        if (data.has("translationAvailable") && data.opt("translationAvailable") != (translationState == LyricTranslationStatus.READY)) invalidResponse()
        val paired = if (translationState == LyricTranslationStatus.READY) {
            if (!supported) invalidResponse()
            val values = translation.optJSONArray("lines") ?: invalidResponse()
            if (values.length() != rows.length()) invalidResponse()
            (0 until values.length()).map { index ->
                (values.opt(index) as? String)?.takeIf { it.length <= 4000 && !it.contains('\n') && !it.contains('\r') } ?: invalidResponse()
            }.also { if (it.none(String::isNotBlank)) invalidResponse() }
        } else null
        var badWords = false
        var totalWords = 0
        val lines = (0 until rows.length()).map { index ->
            val row = rows.optJSONObject(index) ?: invalidResponse()
            val text = requiredString(row, "text").also { if (it.length > 4000) invalidResponse() }
            val time = lyricTime(row.opt("time"))
            val end = lyricTime(row.opt("endTime"))?.takeIf { time != null && it >= time }
            val rawWords = row.optJSONArray("words")
            totalWords += rawWords?.length() ?: 0
            if (totalWords > 20000) invalidResponse()
            val words = parseWords(rawWords, text, time, end)
            if (row.has("words") && !row.isNull("words") && (rawWords == null || (rawWords.length() > 0 && words.isEmpty()))) badWords = true
            val translated = if (paired != null) paired[index] else nullableString(row, "tlyric")
            LyricLine(text, translated?.takeIf(String::isNotBlank), time, end, words)
        }
        val hasTimes = lines.any { it.timeSeconds != null }
        val mode = when {
            requestedMode == "none" || !hasTimes -> "none"
            requestedMode == "word" && !badWords && lines.any { it.words.isNotEmpty() } -> "word"
            else -> "line"
        }
        return LyricDocument(lines, false, ref, translationState, supported, sourceId, mode)
    }

    private fun parseWords(rows: JSONArray?, text: String, time: Double?, end: Double?): List<LyricWord> {
        if (rows == null || rows.length() == 0 || time == null) return emptyList()
        val words = (0 until rows.length()).map { index ->
            val row = rows.optJSONObject(index) ?: return emptyList()
            val token = row.opt("text") as? String ?: return emptyList()
            val start = lyricTime(row.opt("startTime")) ?: return emptyList()
            val finish = lyricTime(row.opt("endTime")) ?: return emptyList()
            if (token.isEmpty() || token.length > 500 || start < time || finish < start || (end != null && finish > end + 0.002)) return emptyList()
            LyricWord(token, start, finish)
        }
        if (words.joinToString("") { it.text } != text) return emptyList()
        if (words.zipWithNext().any { (left, right) -> right.startTimeSeconds < left.startTimeSeconds || right.startTimeSeconds + 0.002 < left.endTimeSeconds }) return emptyList()
        return words
    }

    private fun lyricTime(raw: Any?): Double? = (raw as? Number)?.toDouble()?.takeIf { it.isFinite() && it in 0.0..86400.0 }
    private fun requiredString(json: JSONObject, field: String): String = (json.opt(field) as? String)?.takeIf(String::isNotBlank) ?: invalidResponse()
    private fun nullableString(json: JSONObject, field: String): String? = when {
        !json.has(field) || json.isNull(field) -> null
        else -> json.opt(field) as? String ?: invalidResponse()
    }
    private fun nonNegativeLong(json: JSONObject, field: String, fallback: Long? = null): Long {
        if (!json.has(field)) return fallback ?: invalidResponse()
        val number = json.opt(field) as? Number ?: invalidResponse()
        val value = number.toDouble()
        if (!value.isFinite() || value < 0 || value > 9_007_199_254_740_991.0 || value != number.toLong().toDouble()) invalidResponse()
        return number.toLong()
    }
    private fun nonNegativeInt(json: JSONObject, field: String, fallback: Int): Int = nonNegativeLong(json, field, fallback.toLong())
        .also { if (it > Int.MAX_VALUE) invalidResponse() }.toInt()
    private fun checkId(id: String, maximum: Int) {
        if (id.isBlank() || id.length > maximum || id != id.trim() || id in setOf(".", "..") || id.any { it.code < 32 || it.code == 127 }) throw ApiFailure(Failure.REQUEST)
    }
    private fun checkRef(ref: String) {
        if (ref.isBlank() || ref.length > 8500 || ref.any { it.code < 32 || it.code == 127 }) throw ApiFailure(Failure.REQUEST)
    }
    private fun invalidResponse(): Nothing = throw ApiFailure(Failure.INVALID_RESPONSE)

    private companion object {
        const val ALL_LANGUAGES = "zh,en,ja,ko,instrumental,ru,es,fr,de,sv,vi,yue,it,th,pt,other"
    }
}
