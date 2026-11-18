package com.arcinks.flaretune.data

import androidx.lifecycle.SavedStateHandle
import com.arcinks.flaretune.model.Album
import com.arcinks.flaretune.model.Artist
import com.arcinks.flaretune.model.PlaylistSummary
import com.arcinks.flaretune.ui.ClientUiState
import com.arcinks.flaretune.ui.LibraryTab
import com.arcinks.flaretune.ui.MainDestination
import java.security.MessageDigest

/** Android instance state contains routing values, never credentials or media URLs. */
internal class ClientNavigationState(private val handle: SavedStateHandle?) {
    data class Route(val destination: MainDestination, val tab: LibraryTab, val query: String,
        val songLanguage: String, val albumLanguage: String, val sort: String,
        val albumId: String?, val playlistId: String?, val artist: String?, val artistId: String?) {
        val hasDetail get() = albumId != null || playlistId != null || artist != null
        fun applyTo(state: ClientUiState) = state.copy(destination = destination, tab = tab, query = query,
            songLanguage = songLanguage, albumLanguage = albumLanguage,
            discoverLanguage = if (songLanguage == albumLanguage) songLanguage else "all",
            songSort = sort,
            selectedAlbum = albumId?.let { Album(it, "", "", knownTrackCount = null) }, albumLoading = albumId != null,
            selectedPlaylist = playlistId?.let { PlaylistSummary(it, "", "playlist") }, playlistLoading = playlistId != null,
            selectedArtist = artist?.let { Artist(it, id = artistId, knownSongCount = null, knownAlbumCount = null) }, artistLoading = artist != null)
    }
    fun restore(connection: ActiveConnection): Route? {
        val raw = handle?.get<Any>(KEY) as? ArrayList<*> ?: return null
        if (raw.size != 11 || raw.any { it !is String } || raw[0] != identity(connection)) { clear(); return null }
        val values = raw.filterIsInstance<String>()
        val destination = MainDestination.entries.find { it.name == values[1] }
        val tab = LibraryTab.entries.find { it.name == values[2] }
        if (destination == null || tab == null || values.withIndex().any { (i, value) -> value.length > limits[i] || value.any { it.code < 32 || it.code == 127 } }) {
            clear(); return null
        }
        fun id(index: Int) = values[index].takeIf { it.isNotBlank() }
        // Only one detail may be restored, using the same precedence as the shell.
        val artist = id(9)
        val album = id(7).takeIf { artist == null }
        val playlist = id(8).takeIf { artist == null && album == null }
        return Route(destination, tab, values[3], values[4].takeIf { it in languages } ?: "all",
            values[5].takeIf { it in languages } ?: "all", values[6].takeIf { it == "asc" } ?: "desc", album, playlist, artist, id(10).takeIf { artist != null })
    }
    fun save(connection: ActiveConnection, state: ClientUiState) {
        val target = handle ?: return
        val values = arrayListOf(identity(connection), state.destination.name, state.tab.name, state.query.take(200),
            state.songLanguage, state.albumLanguage, state.songSort, state.selectedAlbum?.id?.takeIf { it.length <= 3200 }.orEmpty(),
            state.selectedPlaylist?.id?.takeIf { it.length <= 256 }.orEmpty(), state.selectedArtist?.name?.takeIf { it.length <= 300 }.orEmpty(),
            state.selectedArtist?.id?.takeIf { it.length <= 256 }.orEmpty())
        if (target.get<Any>(KEY) != values) target[KEY] = values
    }
    fun clear() { handle?.remove<ArrayList<String>>(KEY) }
    companion object {
        const val KEY = "client_navigation_v1"
        private val limits = listOf(64, 16, 16, 200, 24, 24, 4, 3200, 256, 300, 256)
        fun identity(connection: ActiveConnection): String = MessageDigest.getInstance("SHA-256")
            .digest((connection.network.address.origin + "\u0000" + connection.auth.account.id).toByteArray(Charsets.UTF_8))
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        private val languages = setOf("all", "zh", "en", "ja", "ko", "instrumental", "ru", "es", "fr", "de", "sv", "vi", "yue", "it", "th", "pt", "other")
    }
}
