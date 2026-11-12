package com.flaretune.client.data

import com.flaretune.client.model.*

interface MusicApi {
    fun songs(query: String, offset: Int): SongPage
    fun songs(query: String, offset: Int, language: String): SongPage = songs(query, offset)
    fun songs(query: String, offset: Int, language: String, sort: String): SongPage = songs(query, offset, language)
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

    fun createPlaylist(name: String, description: String = "", songIds: List<String> = emptyList(), idempotencyKey: String? = null): PlaylistMutationResult = throw ApiFailure(Failure.REQUEST)
    fun updatePlaylist(id: String, expectedRevision: Long, name: String? = null, description: String? = null): PlaylistMutationResult = throw ApiFailure(Failure.REQUEST)
    fun deletePlaylist(id: String, expectedRevision: Long): PlaylistDeleteResult = throw ApiFailure(Failure.REQUEST)
    fun replacePlaylistSongs(id: String, songIds: List<String>, expectedRevision: Long, name: String? = null, description: String? = null): PlaylistMutationResult = throw ApiFailure(Failure.REQUEST)
    fun removePlaylistSong(id: String, songId: String, expectedRevision: Long): PlaylistMutationResult = throw ApiFailure(Failure.REQUEST)
    fun addPlaylistSongs(targets: List<PlaylistTarget>, songIds: List<String>): PlaylistAddResult = throw ApiFailure(Failure.REQUEST)
    fun playlistShelf(): PlaylistShelf = throw ApiFailure(Failure.REQUEST)
    fun updatePlaylistShelf(items: List<ShelfItem>, expectedRevision: Long): ShelfMutationResult = throw ApiFailure(Failure.REQUEST)
    /** Client composition of the modern index and complete favorite detail, not a new server route. */
    fun favoriteMembership(songIds: List<String>): FavoriteMembership = throw ApiFailure(Failure.REQUEST)

    fun artists(query: String = "", offset: Int = 0, language: String = "all"): ArtistPage = throw ApiFailure(Failure.REQUEST)
    fun artist(name: String, offset: Int = 0): ArtistDetail = throw ApiFailure(Failure.REQUEST)
    fun browseAlbums(query: String = "", offset: Int = 0, language: String = "all", artist: String? = null): AlbumPage = throw ApiFailure(Failure.REQUEST)
    fun artistPhotos(name: String): ArtistPhotos = throw ApiFailure(Failure.REQUEST)
    fun languageCounts(): Map<String, Int> = throw ApiFailure(Failure.REQUEST)
    fun randomSongs(limit: Int = 20, exclude: List<String> = emptyList()): List<Song> = throw ApiFailure(Failure.REQUEST)
    fun spotlightArtist(exclude: List<String> = emptyList()): SpotlightArtist? = throw ApiFailure(Failure.REQUEST)
    fun playStats(limit: Int = 20, summaryOnly: Boolean = false): PlayStats = throw ApiFailure(Failure.REQUEST)
    fun submitPlayEvents(events: List<PlayEvent>): PlayEventReceipt = throw ApiFailure(Failure.REQUEST)

    fun imageSlot(purpose: String, targetId: String): ImageSlot = throw ApiFailure(Failure.REQUEST)
    fun uploadPlaylistCover(bytes: ByteArray): UserImage = throw ApiFailure(Failure.REQUEST)
    fun setPlaylistCover(id: String, imageId: String?, revision: Long): ImageSlot = throw ApiFailure(Failure.REQUEST)
    fun discardUserImage(id: String): Unit = throw ApiFailure(Failure.REQUEST)
}

interface AccountApi : MusicApi {
    fun changePassword(current: String, replacement: String)
    fun logout()
}
