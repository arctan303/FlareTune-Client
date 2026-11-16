package com.flaretune.client.data

import com.flaretune.client.model.*
import org.json.JSONArray
import org.json.JSONObject

internal val SONG_RELATION_KEYS = setOf("source", "artists", "artistId", "albumId")

/** The four whitelisted relation fields carry no playback/image URLs or credentials. */
internal fun JSONObject.readSongRelations(songId: String): SongRelations {
    fun text(row: JSONObject, key: String): String? = when (val value = row.opt(key)) {
        null, JSONObject.NULL -> null
        is String -> value
        else -> throw IllegalArgumentException("Invalid relation value")
    }
    val source = text(this, "source")
    val external = source == "youtube" || songId.startsWith("yt:")
    fun artistId(value: String?): String? {
        if (value == null) return null
        val normalized = if (external && value.startsWith("UC")) "yt:artist:" + value else value
        require(external && validYouTubeArtistReference(normalized)) { "Invalid artist reference" }
        return normalized
    }
    val rows = when (val value = opt("artists")) {
        null, JSONObject.NULL -> null
        is JSONArray -> value
        else -> throw IllegalArgumentException("Invalid artist references")
    }
    require(rows == null || rows.length() <= 50) { "Too many artist references" }
    val artists = if (rows == null) emptyList() else (0 until rows.length()).map { index ->
        val row = rows.opt(index) as? JSONObject ?: throw IllegalArgumentException("Invalid artist reference")
        val name = text(row, "name") ?: throw IllegalArgumentException("Missing artist name")
        // Local artist routes are names, while external routes use the actual opaque ID.
        SongArtist(if (external) artistId(text(row, "id")) else null, name)
    }.distinctBy { it.id to it.name }
    val result = SongRelations(source, artists, artistId(text(this, "artistId")), text(this, "albumId"))
    require(validSongRelations(songId, result)) { "Invalid relation metadata" }
    return result
}

internal fun validSongRelations(songId: String, relations: SongRelations): Boolean {
    fun validName(name: String) = name.isNotBlank() && name.length <= 300 && name.none { it.code < 32 || it.code == 127 }
    if (relations.source != null && !relations.source.matches(Regex("[A-Za-z0-9_-]{1,64}"))) return false
    val external = relations.source == "youtube" || songId.startsWith("yt:")
    if (relations.artists.size > 50 || relations.artists.any { !validName(it.name) ||
            (it.id != null && (!external || !validYouTubeArtistReference(it.id))) }) return false
    if (relations.artistId != null && (!external || !validYouTubeArtistReference(relations.artistId))) return false
    return relations.albumId == null || if (external) validYouTubeAlbumReference(relations.albumId) else validLocalAlbumReference(relations.albumId)
}

internal fun JSONObject.putSongRelations(songId: String, relations: SongRelations): JSONObject = apply {
    require(validSongRelations(songId, relations)) { "Invalid relation metadata" }
    relations.source?.let { put("source", it) }
    if (relations.artists.isNotEmpty()) put("artists", JSONArray(relations.artists.map {
        JSONObject().put("id", it.id ?: JSONObject.NULL).put("name", it.name)
    }))
    relations.artistId?.let { put("artistId", it) }
    relations.albumId?.let { put("albumId", it) }
}
