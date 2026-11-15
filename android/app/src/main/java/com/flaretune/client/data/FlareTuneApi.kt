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
    val details: MusicFailureDetails? = null,
) : IOException(failure.name)

class FlareTuneApi(val network: SessionNetwork) : AccountApi {
    private val address get() = network.address
    var session: AuthSession? = null
        private set

    private fun request(path: String, body: JSONObject? = null, lyricOperation: String? = null,
        method: String = if (body == null) "GET" else "POST"): JSONObject = request(address.endpoint(path), body, lyricOperation, method)

    private fun request(url: HttpUrl, body: JSONObject? = null, lyricOperation: String? = null,
        method: String = if (body == null) "GET" else "POST"): JSONObject {
        if (!address.sameOrigin(url)) throw ApiFailure(Failure.REQUEST)
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        session?.let { builder.header("X-FlareTune-Expected-Account", it.account.id) }
        if (body != null) {
            builder.header("Origin", address.origin).header("X-Requested-With", "FlareTune")
            session?.let { builder.header("X-CSRF-Token", it.csrfToken) }
            builder.method(method, body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        }
        return execute(builder.build(), lyricOperation)
    }

    private fun execute(request: Request, lyricOperation: String? = null): JSONObject {
        val url = request.url
        if (!address.sameOrigin(url)) throw ApiFailure(Failure.REQUEST)
        try {
            network.apiClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // Error text is untrusted. Only bounded identifiers and typed current-state facts cross this boundary.
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
                    throw ApiFailure(failure, response.code, code, reason, parseFailureDetails(error))
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
        val username = user.optString("username")
        if (id.isBlank() || csrf.isBlank() || username.isBlank()) invalidResponse()
        val avatarSlot = if (user.has("avatar") && !user.isNull("avatar")) {
            (user.optJSONObject("avatar") ?: invalidResponse()).let(::parseImageSlot)
        } else null
        return AuthSession(
            Account(
                id = id,
                username = username,
                displayName = user.optString("displayName").ifBlank { username },
                avatarUrl = avatarSlot?.url,
                avatarRevision = avatarSlot?.revision ?: 0L,
            ),
            csrf,
            json.optBoolean("mustChangePassword"),
        )
    }

    override fun updateDisplayName(displayName: String): Account {
        val trimmed = displayName.trim()
        if (trimmed.length > 80 || trimmed.any { it.code < 32 || it.code == 127 || it == '\u2028' || it == '\u2029' }) {
            throw ApiFailure(Failure.REQUEST)
        }
        val data = mutation(address.endpoint("/api/account/profile"), "PATCH", JSONObject().put("displayName", trimmed))
        val user = data.optJSONObject("user") ?: invalidResponse()
        val id = requiredString(user, "accountId")
        val username = requiredString(user, "username")
        val returnedName = (user.opt("displayName") as? String)?.trim()?.ifBlank { username } ?: invalidResponse()
        session?.let { currentSession ->
            if (currentSession.account.id == id) {
                session = currentSession.copy(account = currentSession.account.copy(displayName = returnedName))
            }
        }
        return Account(id, username, returnedName)
    }

    override fun changePassword(current: String, replacement: String) {
        request("/api/auth/change-password", JSONObject().put("currentPassword", current).put("newPassword", replacement))
    }

    override fun logout() { request("/api/auth/logout", JSONObject()) }

    override fun songs(query: String, offset: Int): SongPage = songs(query, offset, "all")

    override fun songs(query: String, offset: Int, language: String): SongPage = songs(query, offset, language, "desc")

    override fun songs(query: String, offset: Int, language: String, sort: String): SongPage {
        if (sort !in setOf("asc", "desc")) throw ApiFailure(Failure.REQUEST)
        if (offset !in 0..2000) throw ApiFailure(Failure.REQUEST)
        val filter = languageFilter(language, allowUntagged = true)
        val searching = query.isNotBlank()
        val builder = if (!searching) {
            if (offset % 50 != 0) throw ApiFailure(Failure.REQUEST)
            address.endpoint("/api/songs").newBuilder()
                .addQueryParameter("language", filter ?: ALL_LANGUAGES)
                .addQueryParameter("page", (offset / 50 + 1).toString()).addQueryParameter("limit", "50").addQueryParameter("sort", sort)
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
            .map { it.copy(albumId = album.id, album = it.album.ifBlank { album.title }, coverUrl = it.coverUrl ?: album.coverUrl) }
        return AlbumDetail(album, songs)
    }

    override fun playlists(): List<PlaylistSummary> {
        val array = memberData(request("/api/account/playlists")).optJSONArray("playlists") ?: invalidResponse()
        return (0 until array.length()).map { parsePlaylist(array.optJSONObject(it) ?: invalidResponse()) }
    }

    override fun playlist(id: String): PlaylistDetail {
        checkId(id, 240)
        val data = memberData(request(playlistUrl(id))).optJSONObject("playlist") ?: invalidResponse()
        return parsePlaylistDetail(data).also { if (it.playlist.id != id) invalidResponse() }
    }
    override fun createPlaylist(name: String, description: String, songIds: List<String>, idempotencyKey: String?): PlaylistMutationResult {
        val body = JSONObject().put("name", playlistName(name)).put("description", playlistDescription(description))
            .put("songIds", JSONArray(validSongIds(songIds, 0, 500)))
        if (!idempotencyKey.isNullOrEmpty()) body.put("idempotencyKey", idempotencyKey)
        val result = parsePlaylistMutation(memberData(mutation(address.endpoint("/api/account/playlists"), "POST", body)))
        if (result.shelf == null) invalidResponse()
        return result
    }

    override fun updatePlaylist(id: String, expectedRevision: Long, name: String?, description: String?): PlaylistMutationResult {
        if (name == null && description == null) throw ApiFailure(Failure.REQUEST)
        val body = revisionBody(expectedRevision)
        name?.let { body.put("name", playlistName(it)) }
        description?.let { body.put("description", playlistDescription(it)) }
        return parsePlaylistMutation(memberData(mutation(playlistUrl(id), "PUT", body)), id)
    }

    override fun deletePlaylist(id: String, expectedRevision: Long): PlaylistDeleteResult {
        val data = memberData(mutation(playlistUrl(id), "DELETE", revisionBody(expectedRevision)))
        if (requiredString(data, "outcome") != "applied" || requiredString(data, "deletedPlaylistId") != id) invalidResponse()
        return PlaylistDeleteResult("applied", id, parseShelf(data.optJSONObject("shelf") ?: invalidResponse()))
    }

    override fun replacePlaylistSongs(id: String, songIds: List<String>, expectedRevision: Long, name: String?, description: String?): PlaylistMutationResult {
        val body = revisionBody(expectedRevision).put("songIds", JSONArray(validSongIds(songIds, 0, 500)))
        name?.let { body.put("name", playlistName(it)) }
        description?.let { body.put("description", playlistDescription(it)) }
        return parsePlaylistMutation(memberData(mutation(playlistUrl(id, "songs"), "PUT", body)), id, requireDetail = true)
    }

    override fun removePlaylistSong(id: String, songId: String, expectedRevision: Long): PlaylistMutationResult {
        checkId(songId, 240)
        return parsePlaylistMutation(memberData(mutation(playlistUrl(id, "songs", songId), "DELETE", revisionBody(expectedRevision))), id, requireDetail = true)
    }

    override fun addPlaylistSongs(targets: List<PlaylistTarget>, songIds: List<String>): PlaylistAddResult {
        if (targets.size !in 1..50 || targets.map { it.playlistId }.distinct().size != targets.size) throw ApiFailure(Failure.REQUEST)
        val ids = validSongIds(songIds, 1, 100)
        if (targets.size * ids.size > 500) throw ApiFailure(Failure.REQUEST)
        val targetRows = JSONArray()
        targets.forEach { target ->
            checkId(target.playlistId, 240)
            targetRows.put(revisionBody(target.expectedRevision).put("playlistId", target.playlistId))
        }
        val data = memberData(mutation(address.endpoint("/api/account/playlist-songs"), "POST",
            JSONObject().put("targets", targetRows).put("songIds", JSONArray(ids))))
        val rows = data.optJSONArray("results") ?: invalidResponse()
        val expected = targets.map { it.playlistId }.toSet()
        if (rows.length() != targets.size) invalidResponse()
        val results = (0 until rows.length()).map { index ->
            val row = rows.optJSONObject(index) ?: invalidResponse()
            val id = requiredString(row, "playlistId")
            if (id !in expected) invalidResponse()
            val outcome = requiredString(row, "outcome")
            if (outcome !in setOf("applied", "noop", "failed")) invalidResponse()
            val added = stringIds(row.optJSONArray("addedSongIds") ?: invalidResponse())
            val noop = stringIds(row.optJSONArray("noopSongIds") ?: invalidResponse())
            if ((added + noop).any { it !in ids } || added.any { it in noop } || (outcome != "applied" && added.isNotEmpty())) invalidResponse()
            val error = machineField(row, "error")
            if (outcome == "failed" && error == null) invalidResponse()
            PlaylistTargetResult(id, outcome, added, noop, if (row.has("revision")) nonNegativeLong(row, "revision") else null, error)
        }
        if (results.map { it.playlistId }.distinct().size != results.size) invalidResponse()
        val affected = stringIds(data.optJSONArray("affectedPlaylistIds") ?: invalidResponse())
        if (affected.toSet() != results.filter { it.outcome == "applied" }.map { it.playlistId }.toSet()) invalidResponse()
        val expectedOutcome = if (results.any { it.outcome == "failed" }) {
            if (affected.isEmpty()) "failed" else "partial"
        } else if (affected.isEmpty()) "noop" else "applied"
        if (requiredString(data, "outcome") != expectedOutcome) invalidResponse()
        return PlaylistAddResult(expectedOutcome, results, affected)
    }

    override fun playlistShelf(): PlaylistShelf = parseShelf(memberData(request("/api/account/playlist-shelf"))
        .optJSONObject("shelf") ?: invalidResponse())

    override fun updatePlaylistShelf(items: List<ShelfItem>, expectedRevision: Long): ShelfMutationResult {
        if (items.size !in 1..51 || items.map { it.id }.distinct().size != items.size || items.any { it.kind != "member" }) throw ApiFailure(Failure.REQUEST)
        val rows = JSONArray()
        items.forEach { checkId(it.id, 240); rows.put(JSONObject().put("kind", "member").put("id", it.id)) }
        val data = memberData(mutation(address.endpoint("/api/account/playlist-shelf"), "PUT", revisionBody(expectedRevision).put("items", rows)))
        val outcome = mutationOutcome(data)
        val shelf = parseShelf(data.optJSONObject("shelf") ?: invalidResponse())
        if (shelf.items != items) invalidResponse()
        return ShelfMutationResult(outcome, shelf)
    }

    override fun favoriteMembership(songIds: List<String>): FavoriteMembership {
        // The host has no separate membership endpoint; complete detail retains unplayable members too.
        val favorite = playlists().singleOrNull { it.kind == "favorite" } ?: invalidResponse()
        val detail = playlist(favorite.id)
        if (detail.playlist.kind != "favorite") invalidResponse()
        val requested = songIds.toSet()
        return FavoriteMembership(detail.playlist, detail.entries.map { it.song.id }.filter { it in requested }.toSet())
    }

    private fun playlistUrl(id: String, vararg segments: String): HttpUrl {
        checkId(id, 240)
        return address.endpoint("/api/account/playlists").newBuilder().addPathSegment(id).apply {
            segments.forEach { addPathSegment(it) }
        }.build()
    }

    private fun mutation(url: HttpUrl, method: String, body: JSONObject): JSONObject {
        requireMutationSession()
        if (body.toString().toByteArray(Charsets.UTF_8).size > 64 * 1024) throw ApiFailure(Failure.REQUEST)
        return request(url, body, method = method)
    }

    private fun requireMutationSession() {
        if (!network.isActive) throw ApiFailure(Failure.RETIRED)
        val auth = session ?: throw ApiFailure(Failure.SESSION_EXPIRED)
        if (auth.mustChangePassword) throw ApiFailure(Failure.FORBIDDEN)
    }

    private fun revisionBody(revision: Long): JSONObject {
        if (revision !in 0..9_007_199_254_740_991L) throw ApiFailure(Failure.REQUEST)
        return JSONObject().put("expectedRevision", revision)
    }

    private fun playlistName(value: String): String = value.trim().also {
        if (it.codePointCount(0, it.length) !in 1..40) throw ApiFailure(Failure.REQUEST)
    }
    private fun playlistDescription(value: String): String = value.trim().also {
        if (it.codePointCount(0, it.length) > 300) throw ApiFailure(Failure.REQUEST)
    }
    private fun validSongIds(ids: List<String>, minimum: Int, maximum: Int): List<String> {
        if (ids.size !in minimum..maximum || ids.distinct().size != ids.size) throw ApiFailure(Failure.REQUEST)
        ids.forEach { checkId(it, 240) }
        return ids
    }
    private fun mutationOutcome(data: JSONObject): String = requiredString(data, "outcome").also {
        if (it !in setOf("applied", "noop")) invalidResponse()
    }
    private fun parsePlaylistMutation(data: JSONObject, expectedId: String? = null, requireDetail: Boolean = false): PlaylistMutationResult {
        val outcome = mutationOutcome(data)
        val raw = data.optJSONObject("playlist") ?: invalidResponse()
        val summary = parsePlaylist(raw)
        if (expectedId != null && summary.id != expectedId) invalidResponse()
        val detail = if (raw.has("songs")) parsePlaylistDetail(raw) else null
        if (requireDetail && detail == null) invalidResponse()
        val shelf = if (data.has("shelf")) parseShelf(data.optJSONObject("shelf") ?: invalidResponse()) else null
        return PlaylistMutationResult(summary, detail, shelf, outcome)
    }
    private fun parsePlaylistDetail(data: JSONObject): PlaylistDetail {
        val summary = parsePlaylist(data)
        val rows = data.optJSONArray("songs") ?: invalidResponse()
        val entries = (0 until rows.length()).map { index ->
            val row = rows.optJSONObject(index) ?: invalidResponse()
            PlaylistSong(parseSongMetadata(row), nonNegativeInt(row, "sortOrder", 0), nonNegativeLong(row, "addedAt", 0))
        }
        if (entries.map { it.song.id }.distinct().size != entries.size) invalidResponse()
        return PlaylistDetail(summary, entries.sortedBy { it.sortOrder })
    }
    private fun parseSongMetadata(row: JSONObject): Song {
        val id = requiredString(row, "id")
        val links = songRelations(row, id)
        return Song(id, (row.opt("title") as? String).orEmpty().ifBlank { id },
            (row.opt("artist") as? String).orEmpty(), (row.opt("album") as? String).orEmpty(),
            row.optDouble("duration", 0.0).takeIf { it.isFinite() && it > 0 } ?: 0.0,
            nullableString(row, "audio_url")?.let(address::media).orEmpty(),
            nullableString(row, "cover_url")?.let(address::media), nullableString(row, "language"),
            links.source, links.artists, links.artistId, links.albumId)
    }
    private fun songRelations(row: JSONObject, id: String): SongRelations = try {
        row.readSongRelations(id)
    } catch (_: IllegalArgumentException) { invalidResponse() }
    private fun parseShelf(data: JSONObject): PlaylistShelf {
        val rows = data.optJSONArray("items") ?: invalidResponse()
        if (rows.length() > 51) invalidResponse()
        val items = (0 until rows.length()).map { index ->
            val row = rows.optJSONObject(index) ?: invalidResponse()
            if (requiredString(row, "kind") != "member") invalidResponse()
            ShelfItem(requiredString(row, "id"))
        }
        if (items.map { it.id }.distinct().size != items.size) invalidResponse()
        return PlaylistShelf(nonNegativeLong(data, "revision"), nonNegativeLong(data, "updatedAt", 0), items)
    }
    private fun stringIds(array: JSONArray): List<String> = (0 until array.length()).map { index ->
        (array.opt(index) as? String)?.takeIf { it.isNotBlank() && !it.any { char -> char.code < 32 || char.code == 127 } } ?: invalidResponse()
    }.also { if (it.distinct().size != it.size) invalidResponse() }
    private fun parseFailureDetails(error: JSONObject?): MusicFailureDetails? {
        val data = error?.optJSONObject("data") ?: return null
        // Malformed optional details never replace the original HTTP status/code.
        val playlist = try { data.optJSONObject("playlist")?.let(::parsePlaylist) } catch (_: ApiFailure) { null }
        val shelf = try { data.optJSONObject("shelf")?.let(::parseShelf) } catch (_: ApiFailure) { null }
        val missing = try { data.optJSONArray("missingSongIds")?.let(::stringIds).orEmpty() } catch (_: ApiFailure) { emptyList() }
        return if (playlist == null && shelf == null && missing.isEmpty()) null else MusicFailureDetails(playlist, shelf, missing)
    }
    override fun artists(query: String, offset: Int, language: String): ArtistPage {
        checkOffset(offset)
        val filter = languageFilter(language)
        val url = address.endpoint("/api/artists").newBuilder().addQueryParameter("limit", "20")
            .addQueryParameter("offset", offset.toString()).apply {
                catalogName(query)?.let { addQueryParameter("q", it) }
                filter?.let { addQueryParameter("language", it) }
            }.build()
        val data = catalogData(request(url))
        val rows = data.optJSONArray("artists") ?: invalidResponse()
        val artists = (0 until rows.length()).map { parseArtist(rows.optJSONObject(it) ?: invalidResponse()) }
        val more = data.opt("hasMore") as? Boolean ?: invalidResponse()
        return ArtistPage(artists, offset + 20, more && offset + 20 <= 2000, requiredInt(data, "total"), more && offset + 20 > 2000)
    }

    override fun artist(name: String, offset: Int): ArtistDetail {
        checkOffset(offset)
        val artistName = catalogName(name) ?: throw ApiFailure(Failure.REQUEST)
        val external = validYouTubeArtistId(artistName)
        if (artistName.startsWith("yt:") && !external) throw ApiFailure(Failure.REQUEST)
        val url = address.endpoint("/api/artists").newBuilder().addPathSegment(artistName)
            .addQueryParameter("limit", "50").addQueryParameter("offset", offset.toString()).build()
        val data = catalogData(request(url))
        val artist = parseArtist(data)
        if (external) {
            if (artist.id != artistName) invalidResponse()
        } else if (artist.id != null || artist.name != artistName) invalidResponse()
        val more = data.opt("hasMore") as? Boolean ?: invalidResponse()
        val albums = if (external && data.has("albums")) {
            val rows = data.optJSONArray("albums") ?: invalidResponse()
            (0 until rows.length()).map { parseAlbum(rows.optJSONObject(it) ?: invalidResponse()) }
        } else emptyList()
        return ArtistDetail(artist, parseSongs(data.optJSONArray("songs") ?: invalidResponse()), offset + 50,
            more && offset + 50 <= 2000, more && offset + 50 > 2000, albums)
    }

    override fun browseAlbums(query: String, offset: Int, language: String, artist: String?): AlbumPage {
        checkOffset(offset)
        val filter = languageFilter(language)
        val url = address.endpoint("/api/albums").newBuilder().addQueryParameter("limit", "20")
            .addQueryParameter("offset", offset.toString()).apply {
                catalogName(query)?.let { addQueryParameter("q", it) }
                filter?.let { addQueryParameter("language", it) }
                artist?.let { catalogName(it)?.let { value -> addQueryParameter("artist", value) } }
            }.build()
        val data = catalogData(request(url))
        val rows = data.optJSONArray("albums") ?: invalidResponse()
        val albums = (0 until rows.length()).map { parseAlbum(rows.optJSONObject(it) ?: invalidResponse()) }
        val more = data.opt("hasMore") as? Boolean ?: invalidResponse()
        return AlbumPage(albums, offset + 20, more && offset + 20 <= 2000, more && offset + 20 > 2000)
    }

    override fun artistPhotos(name: String): ArtistPhotos {
        val clean = catalogName(name) ?: throw ApiFailure(Failure.REQUEST)
        if (clean.length > 200) throw ApiFailure(Failure.REQUEST)
        val result = request(address.endpoint("/api/artist-photo").newBuilder().addQueryParameter("name", clean).build())
        if ((result.opt("code") as? Number)?.toDouble() != 0.0) invalidResponse()
        val data = result.optJSONObject("data") ?: invalidResponse()
        if (requiredString(data, "artist") != clean) invalidResponse()
        val names = data.optJSONArray("artistNames") ?: invalidResponse()
        val rows = data.optJSONArray("photos") ?: invalidResponse()
        if (names.length() > 8 || rows.length() > 12) invalidResponse()
        val photos = (0 until rows.length()).map { index ->
            val row = rows.optJSONObject(index) ?: invalidResponse()
            val url = artistPhotoUrl(requiredString(row, "url")) ?: invalidResponse()
            fun dimension(field: String): Int? = if (row.has(field)) requiredInt(row, field).also { if (it > 100_000) invalidResponse() } else null
            ArtistPhoto(url, nullableString(row, "artistName"), nullableString(row, "type"), nullableString(row, "label"),
                nullableString(row, "source"), nullableString(row, "sourceName"), dimension("width"), dimension("height"),
                row.opt("isRealArtist") as? Boolean, row.opt("is169") as? Boolean)
        }
        return ArtistPhotos(clean, stringIds(names), nullableString(data, "primary")?.let(::artistPhotoUrl), photos)
    }
    private fun artistPhotoUrl(raw: String): String? {
        if (raw.isBlank()) return null
        val url = try { address.endpoint(raw) } catch (_: IllegalArgumentException) { invalidResponse() }
        if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty() || (!raw.startsWith('/') && !raw.startsWith("https://", true)) || raw.startsWith("//")) invalidResponse()
        return url.toString()
    }
    override fun languageCounts(): Map<String, Int> = languageCounts(includeUntagged = false)

    override fun languageCounts(includeUntagged: Boolean): Map<String, Int> {
        val builder = address.endpoint("/api/songs").newBuilder()
            .addQueryParameter("counts", "language")
        if (includeUntagged) builder.addQueryParameter("includeUntagged", "1")
        val data = catalogData(request(builder.build()))
        return data.keys().asSequence().associateWith { requiredInt(data, it) }
    }

    override fun randomSongs(limit: Int, exclude: List<String>): List<Song> {
        if (limit !in 1..50) throw ApiFailure(Failure.REQUEST)
        val url = address.endpoint("/api/songs/random").newBuilder().addQueryParameter("limit", limit.toString())
            .apply { commaExclusions(exclude, 80)?.let { addQueryParameter("exclude", it) } }.build()
        return parseSongs(catalogData(request(url)).optJSONArray("songs") ?: invalidResponse())
    }

    override fun spotlightArtist(exclude: List<String>): SpotlightArtist? {
        val url = address.endpoint("/api/songs/spotlight-artist").newBuilder()
            .apply { commaExclusions(exclude, 100)?.let { addQueryParameter("exclude", it) } }.build()
        val result = request(url)
        if ((result.opt("code") as? Number)?.toDouble() != 200.0) invalidResponse()
        if (result.isNull("data")) return null
        val data = result.optJSONObject("data") ?: invalidResponse()
        return SpotlightArtist(requiredString(data, "artist"), requiredInt(data, "songCount"),
            parseSongs(data.optJSONArray("songs") ?: invalidResponse()), nullableString(data, "photoUrl")?.let(address::media),
            nullableString(data, "coverUrl")?.let(address::media))
    }

    override fun playStats(limit: Int, summaryOnly: Boolean): PlayStats = playStats(limit, summaryOnly, null)

    override fun playStats(limit: Int, summaryOnly: Boolean, days: Int?): PlayStats {
        if (limit !in 1..50) throw ApiFailure(Failure.REQUEST)
        if (days != null && days !in 1..30) throw ApiFailure(Failure.REQUEST)
        val url = address.endpoint("/api/account/play-stats").newBuilder().addQueryParameter("limit", limit.toString())
            .apply {
                if (summaryOnly) addQueryParameter("view", "summary")
                if (days != null) addQueryParameter("days", days.toString())
            }.build()
        val data = memberData(request(url))
        val rows = data.optJSONArray("songs") ?: invalidResponse()
        val songs = (0 until rows.length()).map { index ->
            val row = rows.optJSONObject(index) ?: invalidResponse()
            RankedSong(parseSongMetadata(row), nonNegativeLong(row, "play_count"), nonNegativeLong(row, "last_played_at"))
        }
        // Old hosts may silently ignore ?days. Never label their all-time counts as recent.
        val window = days?.let { requestedDays ->
            val range = data.optJSONObject("window") ?: invalidResponse()
            val echoedDays = requiredInt(range, "days")
            val from = nonNegativeLong(range, "from")
            val to = nonNegativeLong(range, "to")
            if (echoedDays != requestedDays || to - from != requestedDays * 86_400_000L) invalidResponse()
            PlayStatsWindow(echoedDays, from, to)
        }
        if (summaryOnly) {
            if (data.opt("view") != "summary") invalidResponse()
            return PlayStats(songs, summaryOnly = true, window = window)
        }
        val counts = data.optJSONObject("playCounts") ?: invalidResponse()
        return PlayStats(songs, counts.keys().asSequence().associateWith { nonNegativeLong(counts, it) },
            nonNegativeLong(data, "totalPlays"), requiredInt(data, "totalUniqueSongs"), window = window)
    }

    override fun submitPlayEvents(events: List<PlayEvent>): PlayEventReceipt {
        if (events.isEmpty()) return PlayEventReceipt(0, emptyList())
        if (events.size > 25) throw ApiFailure(Failure.REQUEST)
        val rows = JSONArray()
        events.forEach { event ->
            if (!event.eventId.matches(Regex("[A-Za-z0-9_-]{1,128}")) || event.playedAt !in 1..9_007_199_254_740_991L) throw ApiFailure(Failure.REQUEST)
            checkId(event.songId, 256)
            rows.put(JSONObject().put("event_id", event.eventId).put("song_id", event.songId).put("played_at", event.playedAt))
        }
        val data = memberData(mutation(address.endpoint("/api/account/play-stats"), "POST", JSONObject().put("events", rows)))
        val accepted = stringIds(data.optJSONArray("acceptedEventIds") ?: invalidResponse())
        val recorded = requiredInt(data, "recorded")
        val submitted = events.map { it.eventId }.toSet()
        if (accepted.any { it !in submitted } || recorded > submitted.size) invalidResponse()
        return PlayEventReceipt(recorded, accepted)
    }

    override fun imageSlot(purpose: String, targetId: String): ImageSlot {
        if (purpose !in setOf("avatar", "playlist") || (purpose == "avatar" && targetId != "avatar")) throw ApiFailure(Failure.REQUEST)
        checkImageId(targetId)
        val url = address.endpoint("/api/account/images/slots").newBuilder().addPathSegment(purpose).addPathSegment(targetId).build()
        return parseImageSlot(request(url))
    }

    override fun uploadPlaylistCover(bytes: ByteArray): UserImage = uploadUserImage("playlist", 1024, bytes)

    override fun setPlaylistCover(id: String, imageId: String?, revision: Long): ImageSlot =
        setImageSlot("playlist", id, imageId, revision)

    override fun uploadAvatar(bytes: ByteArray): UserImage = uploadUserImage("avatar", 512, bytes)

    override fun setAvatar(imageId: String?, revision: Long): ImageSlot =
        setImageSlot("avatar", "avatar", imageId, revision)

    private fun uploadUserImage(purpose: String, expectedDimension: Int, bytes: ByteArray): UserImage {
        requireMutationSession()
        if (bytes.isEmpty() || bytes.size > 5 * 1024 * 1024) throw ApiFailure(Failure.REQUEST)
        val auth = session ?: throw ApiFailure(Failure.SESSION_EXPIRED)
        val url = address.endpoint("/api/account/images/upload").newBuilder().addPathSegment(purpose).build()
        val builder = Request.Builder().url(url)
            .header("Accept", "application/json").header("Origin", address.origin).header("X-Requested-With", "FlareTune")
            .header("X-FlareTune-Expected-Account", auth.account.id).header("X-CSRF-Token", auth.csrfToken)
            .post(bytes.copyOf().toRequestBody("image/webp".toMediaType()))
        val data = execute(builder.build())
        val id = requiredString(data, "id").also { if (!it.matches(Regex("[A-Za-z0-9_-]{1,160}"))) invalidResponse() }
        val width = requiredInt(data, "width")
        val height = requiredInt(data, "height")
        val size = requiredInt(data, "byteSize")
        if (width != expectedDimension || height != expectedDimension || size !in 1..5 * 1024 * 1024 || size != bytes.size) invalidResponse()
        return UserImage(id, privateImageUrl(nullableString(data, "url"), id) ?: invalidResponse(), width, height, size)
    }

    private fun setImageSlot(purpose: String, targetId: String, imageId: String?, revision: Long): ImageSlot {
        checkImageId(targetId)
        imageId?.let(::checkImageId)
        revisionBody(revision)
        val url = address.endpoint("/api/account/images/slots").newBuilder()
            .addPathSegment(purpose).addPathSegment(targetId).build()
        val data = mutation(url, "PUT", JSONObject().put("imageId", imageId ?: JSONObject.NULL).put("revision", revision))
        return parseImageSlot(data).also { if (it.imageId != imageId) invalidResponse() }
    }

    override fun discardUserImage(id: String) {
        checkImageId(id)
        val url = address.endpoint("/api/account/images").newBuilder().addPathSegment(id).build()
        if (mutation(url, "DELETE", JSONObject()).opt("ok") != true) invalidResponse()
    }

    private fun checkImageId(id: String) {
        if (!id.matches(Regex("[A-Za-z0-9_-]{1,160}"))) throw ApiFailure(Failure.REQUEST)
    }
    private fun parseImageSlot(data: JSONObject): ImageSlot {
        val id = nullableString(data, "imageId")
        if (id != null && !id.matches(Regex("[A-Za-z0-9_-]{1,160}"))) invalidResponse()
        return ImageSlot(id, privateImageUrl(nullableString(data, "url"), id), nonNegativeLong(data, "revision"))
    }
    private fun privateImageUrl(raw: String?, id: String?): String? {
        if (id == null) {
            if (raw != null) invalidResponse()
            return null
        }
        if (raw.isNullOrBlank()) invalidResponse()
        val url = try { address.endpoint(raw) } catch (_: IllegalArgumentException) { invalidResponse() }
        if (!address.sameOrigin(url) || url.encodedPath != "/api/account/images/$id" || url.query != null || url.fragment != null ||
            url.username.isNotEmpty() || url.password.isNotEmpty()) invalidResponse()
        return url.toString()
    }
    private fun validYouTubeArtistId(id: String) = id.matches(Regex("yt:artist:UC[A-Za-z0-9_-]{1,126}"))
    private fun validYouTubeAlbumId(id: String) = id.matches(Regex("yt:album:MPREb_[A-Za-z0-9_-]{1,122}"))
    private fun nullableCount(data: JSONObject, field: String): Int? =
        if (!data.has(field) || data.isNull(field)) null else requiredInt(data, field)

    private fun parseArtist(data: JSONObject): Artist {
        val id = nullableString(data, "id")
        val source = nullableString(data, "source")
        val external = id?.startsWith("yt:") == true || source == "youtube"
        if (external && (id == null || !validYouTubeArtistId(id) || (source != null && source != "youtube"))) invalidResponse()
        val songCount = if (external) nullableCount(data, "songCount") else requiredInt(data, "songCount")
        val albumCount = if (external) nullableCount(data, "albumCount") else nonNegativeInt(data, "albumCount", 0)
        return Artist(requiredString(data, "name"), songCount ?: 0, albumCount ?: 0,
            nullableString(data, "photoUrl")?.let(address::media), nullableString(data, "coverUrl")?.let(address::media),
            id = id.takeIf { external }, knownSongCount = songCount, knownAlbumCount = albumCount)
    }
    private fun checkOffset(offset: Int) { if (offset !in 0..2000) throw ApiFailure(Failure.REQUEST) }
    private fun catalogName(raw: String): String? = raw.trim().takeIf { it.isNotEmpty() }?.also {
        if (it.length > 300 || it.any { char -> char.code < 32 || char.code == 127 }) throw ApiFailure(Failure.REQUEST)
    }
    private fun commaExclusions(items: List<String>, maximumLength: Int): String? {
        if (items.isEmpty()) return null
        if (items.size > 20) throw ApiFailure(Failure.REQUEST)
        val value = items.joinToString(",") { item -> item.trim().also {
            if (it.isEmpty() || it.length > maximumLength || it.any { char -> char.code < 32 || char.code == 127 }) throw ApiFailure(Failure.REQUEST)
        } }
        if (value.length > 1000 || value.split(',').size > 20 || value.split(',').any { it.isBlank() }) throw ApiFailure(Failure.REQUEST)
        return value
    }
    private fun requiredInt(json: JSONObject, field: String): Int = nonNegativeLong(json, field)
        .also { if (it > Int.MAX_VALUE) invalidResponse() }.toInt()
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
    private fun languageFilter(language: String, allowUntagged: Boolean = false): String? {
        if (language == "all") return null
        if (allowUntagged && language == "untagged") return language
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
            nonNegativeLong(json, "createdAt", 0), nonNegativeLong(json, "updatedAt", 0), coverRevision = nonNegativeLong(json, "coverRevision", 0))
    }

    private fun validAlbumId(id: String): Boolean = validYouTubeAlbumId(id) || (id.length in 1..3200 && id.matches(Regex("[A-Za-z0-9_-]+")))

    private fun parseAlbum(json: JSONObject): Album {
        val id = requiredString(json, "id")
        val title = requiredString(json, "title").trim()
        val source = nullableString(json, "source")
        val external = id.startsWith("yt:") || source == "youtube"
        if (external && (!validYouTubeAlbumId(id) || (source != null && source != "youtube"))) invalidResponse()
        val artist = if (external) nullableString(json, "artist")?.trim().orEmpty() else requiredString(json, "artist").trim()
        if (!validAlbumId(id) || title.isBlank() || (!external && artist.isBlank())) invalidResponse()
        val count = if (external) nullableCount(json, "trackCount") ?: nullableCount(json, "songCount")
            else if (json.has("trackCount")) requiredInt(json, "trackCount") else null
        return Album(id, title, artist, nullableString(json, "coverUrl")?.let(address::media), count ?: 0, knownTrackCount = count)
    }

    private fun parseSongs(array: JSONArray): List<Song> = (0 until array.length()).mapNotNull { index ->
        array.optJSONObject(index)?.let(::parseSong)
    }.distinctBy { it.id }

    private fun parseSong(row: JSONObject): Song? {
        val id = row.opt("id") as? String ?: return null
        val audio = nullableString(row, "audio_url")?.let(address::media) ?: return null
        if (id.isBlank()) return null
        val links = songRelations(row, id)
        return Song(id, (row.opt("title") as? String).orEmpty().ifBlank { id }, (row.opt("artist") as? String).orEmpty(),
            (row.opt("album") as? String).orEmpty(), row.optDouble("duration", 0.0).takeIf { it.isFinite() && it > 0 } ?: 0.0,
            audio, nullableString(row, "cover_url")?.let(address::media), nullableString(row, "language"),
            links.source, links.artists, links.artistId, links.albumId)
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
        return LyricDocument(lines, false, ref, translationState, supported, sourceId, mode,
            translationLanguage = nullableString(translation, "language"),
            translationTargetLanguage = nullableString(translation, "targetLanguage"))
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
