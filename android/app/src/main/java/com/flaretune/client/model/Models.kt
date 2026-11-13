package com.flaretune.client.model

data class Song(
    val id: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val durationSeconds: Double = 0.0,
    val audioUrl: String,
    val coverUrl: String? = null,
    val language: String? = null,
) {
    /** Membership metadata can exist after a media source disappears. */
    val canPlay: Boolean get() = audioUrl.isNotBlank()
}

data class SongPage(val songs: List<Song>, val nextOffset: Int, val hasMore: Boolean, val partialSources: Boolean = false, val capped: Boolean = false)
data class Account(val id: String, val username: String, val displayName: String)
data class AuthSession(val account: Account, val csrfToken: String, val mustChangePassword: Boolean)
enum class ClientNotice {
    INVALID_URL, NOT_READY, NETWORK, INVALID_LOGIN, SESSION_EXPIRED, PASSWORD_CHANGED,
    REQUEST_FAILED, RATE_LIMITED, INVALID_RESPONSE, STORAGE_FAILED, PAGE_LIMIT,
    PLAYBACK_FAILED, SOME_SOURCES_UNAVAILABLE, FORBIDDEN,
    PLAYLIST_LIMIT, PLAYLIST_SONG_LIMIT, IMAGE_UPLOAD_LIMIT, IMAGE_TOO_LARGE
}


data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val coverUrl: String? = null,
    val trackCount: Int = 0,
    val knownTrackCount: Int? = trackCount,
)

data class AlbumPage(
    val albums: List<Album>,
    val nextOffset: Int,
    val hasMore: Boolean,
    val capped: Boolean = false,
)

data class AlbumDetail(val album: Album, val songs: List<Song>)
