package com.flaretune.client.model

/** Host listening DTOs. Revisions, image-slot revisions and server acknowledgements stay distinct. */
data class ShelfItem(val id: String, val kind: String = "member")
data class PlaylistShelf(val revision: Long, val updatedAt: Long = 0, val items: List<ShelfItem> = emptyList())
data class PlaylistTarget(val playlistId: String, val expectedRevision: Long)
data class PlaylistMutationResult(
    val playlist: PlaylistSummary,
    val detail: PlaylistDetail? = null,
    val shelf: PlaylistShelf? = null,
    val outcome: String = "applied",
)
data class PlaylistDeleteResult(val outcome: String, val deletedPlaylistId: String, val shelf: PlaylistShelf)
data class PlaylistTargetResult(
    val playlistId: String,
    val outcome: String,
    val addedSongIds: List<String> = emptyList(),
    val noopSongIds: List<String> = emptyList(),
    val revision: Long? = null,
    val error: String? = null,
)
data class PlaylistAddResult(val outcome: String, val results: List<PlaylistTargetResult>, val affectedPlaylistIds: List<String>)
data class ShelfMutationResult(val outcome: String, val shelf: PlaylistShelf)
data class MusicFailureDetails(
    val playlist: PlaylistSummary? = null,
    val shelf: PlaylistShelf? = null,
    val missingSongIds: List<String> = emptyList(),
)
data class FavoriteMembership(val favorite: PlaylistSummary, val songIds: Set<String>)

data class Artist(
    val name: String,
    val songCount: Int = 0,
    val albumCount: Int = 0,
    val photoUrl: String? = null,
    val coverUrl: String? = null,
    val id: String? = null,
    // Legacy Int fields remain source-compatible; render these nullable facts.
    val knownSongCount: Int? = songCount,
    val knownAlbumCount: Int? = albumCount,
)
data class ArtistPage(val artists: List<Artist>, val nextOffset: Int, val hasMore: Boolean, val total: Int = 0, val capped: Boolean = false)
data class ArtistDetail(val artist: Artist, val songs: List<Song>, val nextOffset: Int, val hasMore: Boolean, val capped: Boolean = false, val albums: List<Album> = emptyList())
data class SpotlightArtist(val artist: String, val songCount: Int, val songs: List<Song>, val photoUrl: String? = null, val coverUrl: String? = null)
data class RankedSong(val song: Song, val playCount: Long, val lastPlayedAt: Long)
data class PlayStats(
    val songs: List<RankedSong> = emptyList(),
    val playCounts: Map<String, Long>? = null,
    val totalPlays: Long? = null,
    val totalUniqueSongs: Int? = null,
    val summaryOnly: Boolean = false,
)
data class PlayEvent(val eventId: String, val songId: String, val playedAt: Long)
data class PlayEventReceipt(val recorded: Int, val acceptedEventIds: List<String>)

data class UserImage(val id: String, val url: String, val width: Int, val height: Int, val byteSize: Int)
data class ImageSlot(val imageId: String? = null, val url: String? = null, val revision: Long = 0)

data class ArtistPhoto(val url: String, val artistName: String? = null, val type: String? = null, val label: String? = null,
    val source: String? = null, val sourceName: String? = null, val width: Int? = null, val height: Int? = null,
    val isRealArtist: Boolean? = null, val is169: Boolean? = null)
data class ArtistPhotos(val artist: String, val artistNames: List<String>, val primary: String?, val photos: List<ArtistPhoto>)
