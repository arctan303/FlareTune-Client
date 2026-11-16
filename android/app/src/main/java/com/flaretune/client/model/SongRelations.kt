package com.flaretune.client.model

/** A source-owned entity reference, not a display-name identity. */
data class SongArtist(val id: String? = null, val name: String)

/** Safe metadata only; suitable for queue/history snapshots without signed URLs. */
data class SongRelations(
    val source: String? = null,
    val artists: List<SongArtist> = emptyList(),
    val artistId: String? = null,
    val albumId: String? = null,
)

val Song.relations: SongRelations get() = SongRelations(source, artists, artistId, albumId)
val Song.isYouTube: Boolean get() = source == "youtube" || id.startsWith("yt:")

/** Route only the supplied external references. Missing external IDs never mean a local name. */
fun Song.relatedArtists(): List<Artist> {
    if (isYouTube) {
        val named = artists.mapNotNull { reference ->
            val id = reference.id?.takeIf(::validYouTubeArtistReference) ?: return@mapNotNull null
            Artist(reference.name, coverUrl = coverUrl, id = id, knownSongCount = null, knownAlbumCount = null)
        }.distinctBy { it.id }
        if (named.isNotEmpty()) return named
        val fallback = artistId?.takeIf(::validYouTubeArtistReference) ?: return emptyList()
        val name = artists.firstOrNull()?.name ?: splitSongArtistNames(artist).firstOrNull() ?: return emptyList()
        return listOf(Artist(name, coverUrl = coverUrl, id = fallback, knownSongCount = null, knownAlbumCount = null))
    }
    val names = artists.map { it.name }.ifEmpty { splitSongArtistNames(artist) }
    return names.filter { it.isNotBlank() }.distinct().map {
        Artist(it, coverUrl = coverUrl, knownSongCount = null, knownAlbumCount = null)
    }
}

fun Song.relatedAlbum(): Album? {
    val target = albumId ?: return null
    if (isYouTube && !validYouTubeAlbumReference(target)) return null
    if (!isYouTube && !validLocalAlbumReference(target)) return null
    return Album(target, album, artist, coverUrl, knownTrackCount = null)
}

/** Fresh media remains authoritative. Only a same-song reference can fill omitted relationships. */
fun mergeSongRelations(fresh: Song, previous: Song, preferPrevious: Boolean = false): Song {
    if (fresh.id != previous.id || fresh.isYouTube != previous.isYouTube) return fresh
    val preferred = if (preferPrevious) previous else fresh
    val fallback = if (preferPrevious) fresh else previous
    return fresh.copy(source = preferred.source ?: fallback.source,
        artists = preferred.artists.ifEmpty { fallback.artists },
        artistId = preferred.artistId ?: fallback.artistId,
        albumId = preferred.albumId ?: fallback.albumId)
}

internal fun validYouTubeArtistReference(id: String): Boolean = id.matches(Regex("yt:artist:UC[A-Za-z0-9_-]{1,126}"))
internal fun validYouTubeAlbumReference(id: String): Boolean = id.matches(Regex("yt:album:MPREb_[A-Za-z0-9_-]{1,122}"))
internal fun validLocalAlbumReference(id: String): Boolean = id.length in 1..3200 && id.matches(Regex("[A-Za-z0-9_-]+"))

private fun splitSongArtistNames(raw: String): List<String> = raw
    .replace(Regex("\\s+(feat\\.?|ft\\.?|featuring|with|vs\\.?|v\\.s\\.?|x)\\s+", RegexOption.IGNORE_CASE), " / ")
    .replace(Regex("[&,;、•·\\\\|]"), " / ").split('/')
    .map(String::trim).filter { Regex("[\\p{L}\\p{N}]").containsMatchIn(it) }.distinct()
