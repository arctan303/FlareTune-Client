package com.flaretune.client.ui

import com.flaretune.client.model.ClientNotice
import com.flaretune.client.model.Album
import com.flaretune.client.model.Song
import com.flaretune.client.model.PlaylistSummary
import com.flaretune.client.model.LyricState

enum class ClientScreen { RESTORING, CONNECT, CHANGE_PASSWORD, LIBRARY }
enum class LibraryTab { CATALOG, FAVORITES, ALBUMS }
enum class MainDestination { HOME, SEARCH, LIBRARY }

data class PlaybackSnapshot(
    val current: Song? = null,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = 0,
    val failed: Boolean = false,
    val volume: Float = 1f,
)

data class ClientUiState(
    val screen: ClientScreen = ClientScreen.RESTORING,
    val instanceUrl: String = "",
    val username: String = "",
    val accountName: String = "",
    val busy: Boolean = false,
    val notice: ClientNotice? = null,
    val tab: LibraryTab = LibraryTab.CATALOG,
    val query: String = "",
    val songs: List<Song> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val destination: MainDestination = MainDestination.HOME,
    val albums: List<Album> = emptyList(),
    val albumsLoading: Boolean = false,
    val albumsLoadingMore: Boolean = false,
    val albumsHasMore: Boolean = false,
    val albumsNotice: ClientNotice? = null,
    val selectedAlbum: Album? = null,
    val albumSongs: List<Song> = emptyList(),
    val albumLoading: Boolean = false,
    val albumNotice: ClientNotice? = null,
    val playback: PlaybackSnapshot = PlaybackSnapshot(),
    val lyricState: LyricState = LyricState(),
    val translationEnabled: Boolean = false,
    val translationNotice: ClientNotice? = null,
    val playlists: List<PlaylistSummary> = emptyList(),
    val playlistsLoading: Boolean = false,
    val playlistsNotice: ClientNotice? = null,
    val selectedPlaylist: PlaylistSummary? = null,
    val playlistSongs: List<Song> = emptyList(),
    val playlistLoading: Boolean = false,
    val playlistNotice: ClientNotice? = null,
    val favoriteSongs: List<Song> = emptyList(),
    val latestFavoriteCoverUrl: String? = null,
    val favoritesLoading: Boolean = false,
    val favoritesNotice: ClientNotice? = null,
    val playHistory: List<Song> = emptyList(),
    val historyNotice: ClientNotice? = null,
    val queueRestoring: Boolean = false,
    val queueNotice: ClientNotice? = null,
    val songLanguage: String = "all",
    val albumLanguage: String = "all",
) {
    val activeLyricIndex: Int get() = lyricState.activeLyricIndex
}

interface ClientActions {
    fun navigate(destination: MainDestination)
    fun loadMoreAlbums()
    fun openAlbum(album: Album)
    fun closeAlbum()
    fun setVolume(volume: Float)
    fun login(instanceUrl: String, username: String, password: String)
    fun changePassword(currentPassword: String, newPassword: String)
    fun logout()
    fun search(query: String)
    fun selectTab(tab: LibraryTab)
    fun refresh()
    fun loadMore()
    fun playSong(song: Song)
    fun togglePlay()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun cycleRepeat()
    fun selectQueueItem(index: Int)
    fun retryPlayback()

    fun removeQueueItem(index: Int) {}
    fun moveQueueItem(fromIndex: Int, toIndex: Int) {}
    fun retryQueueRestore() {}
    fun toggleTranslation() {}
    fun setTranslationEnabled(enabled: Boolean) {}
    fun retryLyrics() {}
    fun requestLyricTranslation() {}
    fun refreshPlaylists() {}
    fun openPlaylist(playlist: PlaylistSummary) {}
    fun closePlaylist() {}
    fun refreshPlaylist() {}
    fun refreshFavorites() {}
    fun playFavorites() {}
    fun playHistorySong(song: Song) { playSong(song) }
    fun selectSongLanguage(language: String) {}
    fun selectAlbumLanguage(language: String) {}
}