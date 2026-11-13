package com.flaretune.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.flaretune.client.R
import com.flaretune.client.model.Artist

@Composable
internal fun knownArtistCounts(artist: Artist): String {
    val songs = artist.knownSongCount
    val albums = artist.knownAlbumCount
    return when {
        songs != null && albums != null -> stringResource(R.string.ft_listening_artist_counts, songs, albums)
        songs != null -> stringResource(R.string.ft_listening_tracks_count, songs)
        albums != null -> pluralStringResource(R.plurals.ft_known_album_count, albums, albums)
        else -> ""
    }
}
