package com.flaretune.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import com.flaretune.client.model.*
import com.flaretune.client.data.AppThemeMode
import com.flaretune.client.data.AppUpdateError
import com.flaretune.client.data.AppUpdateRelease
import com.flaretune.client.data.BluetoothLyricsMode
import com.flaretune.client.data.PlayedCollectionType
import com.flaretune.client.data.SearchEntity
import com.flaretune.client.data.SavedAccountSummary
import com.flaretune.client.playback.SleepTimerState
import kotlinx.coroutines.flow.StateFlow

enum class AppUpdateStatus {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    NO_RELEASE_FOUND,
    AVAILABLE,
    DOWNLOADING,
    READY_TO_INSTALL,
    INSTALL_PERMISSION_REQUIRED,
    FAILED,
}

data class AppUpdateState(
    val currentVersion: String = "",
    val status: AppUpdateStatus = AppUpdateStatus.IDLE,
    val includePrereleases: Boolean = false,
    val dialogVisible: Boolean = false,
    val availableRelease: AppUpdateRelease? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val downloadedApkPath: String? = null,
    val errorKind: AppUpdateError? = null,
)

enum class ImageCacheClearResult { CLEARING, CLEARED, FAILED, UNAVAILABLE }

enum class ClientScreen { RESTORING, CONNECT, CHANGE_PASSWORD, LIBRARY }
enum class ConnectStep { INSTANCE, CREDENTIALS }
enum class LibraryTab { CATALOG, FAVORITES, ALBUMS }
enum class MainDestination { HOME, DISCOVER, SEARCH, LIBRARY }

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
    val previousQueueIndex: Int? = null,
    val nextQueueIndex: Int? = null,
)

data class ClientUiState(
    val screen: ClientScreen = ClientScreen.RESTORING,
    val instanceUrl: String = "",
    val username: String = "",
    val accountName: String = "",
    val avatarUrl: String? = null,
    val avatarRevision: Long = 0L,
    val profileBusy: Boolean = false,
    val profileNotice: ClientNotice? = null,
    val savedAccounts: List<SavedAccountSummary> = emptyList(),
    val addingAccount: Boolean = false,
    val splashNonce: Int = 0,
    val keepScreenOnEnabled: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val bluetoothLyricsMode: BluetoothLyricsMode = BluetoothLyricsMode.OFF,
    val imageCacheClearResult: ImageCacheClearResult? = null,
    val busy: Boolean = false,
    val notice: ClientNotice? = null,
    val tab: LibraryTab = LibraryTab.CATALOG,
    val query: String = "",
    val songs: List<Song> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val destination: MainDestination = MainDestination.HOME,
    val navigationIdentity: String? = null,
    val playbackReady: Boolean = true,
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
    val discoverLanguage: String = "all",
    val favoriteSongIds: Set<String> = emptySet(),
    val selectedPlaylistEntries: List<PlaylistSong> = emptyList(),
    val playlistShelf: PlaylistShelf? = null,
    val libraryMutationBusy: Boolean = false,
    val libraryMutationNotice: ClientNotice? = null,
    val libraryMutationConflict: Boolean = false,
    val libraryMutationResults: List<PlaylistTargetResult> = emptyList(),
    val libraryMutationVersion: Long = 0,
    val pickerSongs: List<Song> = emptyList(),
    val pickerQuery: String = "",
    val pickerLoading: Boolean = false,
    val pickerLoadingMore: Boolean = false,
    val pickerHasMore: Boolean = false,
    val pickerNotice: ClientNotice? = null,
    val artists: List<Artist> = emptyList(),
    val artistQuery: String = "",
    val artistsLoading: Boolean = false,
    val artistsLoadingMore: Boolean = false,
    val artistsHasMore: Boolean = false,
    val artistsNotice: ClientNotice? = null,
    val selectedArtist: Artist? = null,
    val artistSongs: List<Song> = emptyList(),
    val artistLoading: Boolean = false,
    val artistLoadingMore: Boolean = false,
    val artistHasMore: Boolean = false,
    val artistNotice: ClientNotice? = null,
    val playStats: PlayStats? = null,
    val statsLoading: Boolean = false,
    val statsNotice: ClientNotice? = null,
    val languageCounts: Map<String, Int> = emptyMap(),
    val playHistoryPlayedAt: Map<String, Long> = emptyMap(),
    val songSort: String = "desc",
    val searchAlbums: List<Album> = emptyList(),
    val albumSearchQuery: String = "",
    val albumSearchLoading: Boolean = false,
    val albumSearchLoadingMore: Boolean = false,
    val albumSearchHasMore: Boolean = false,
    val albumSearchNotice: ClientNotice? = null,
    val artistAlbums: List<Album> = emptyList(),
    val artistAlbumsLoading: Boolean = false,
    val artistAlbumsLoadingMore: Boolean = false,
    val artistAlbumsHasMore: Boolean = false,
    val artistAlbumsNotice: ClientNotice? = null,
    val discoverySongs: List<Song> = emptyList(),
    val homeLibrarySongs: List<Song> = emptyList(),
    val homeLibraryLoading: Boolean = false,
    val playlistSortMode: PlaylistSortMode = PlaylistSortMode.CUSTOM,
    val playlistSongSort: PlaylistSongSortMode = PlaylistSongSortMode.DEFAULT,
    val continueListeningSongs: List<Song> = emptyList(),
    val homeHistoryPreviewSong: Song? = null,
    val spotlightArtist: SpotlightArtist? = null,
    val discoveryLoading: Boolean = false,
    val discoveryNotice: ClientNotice? = null,
    val recentSearches: List<SearchEntity> = emptyList(),
    val recentSearchTerms: List<String> = emptyList(),
    val searchHistoryNotice: ClientNotice? = null,
    val visitedArtists: List<VisitedArtist> = emptyList(),
    val playedCollections: List<PlayedCollection> = emptyList(),
    val queueSnapshots: List<QueueSnapshot> = emptyList(),
    val playbackFeedback: PlaybackFeedbackEvent? = null,
    val connectStep: ConnectStep = ConnectStep.INSTANCE,
    val googleLoginEnabled: Boolean = false,
    val googleNativeLoginEnabled: Boolean = false,
    val googleAuthLaunchUrl: String? = null,
    val appUpdate: AppUpdateState = AppUpdateState(),

) {
    val activeLyricIndex: Int get() = lyricState.activeLyricIndex
}

enum class PlaylistSortMode { CUSTOM, RECENTLY_UPDATED, NAME, TRACK_COUNT }

enum class PlaylistSongSortMode { DEFAULT, TITLE, ARTIST, DURATION, REVERSED }

enum class PlaybackFeedbackKind {
    PLAYED_SINGLE_NEXT,
    PLAYED_BATCH_NEXT,
    ENQUEUED_BATCH,
    FAVORITED_BATCH,
    QUEUE_RESTORED,
    QUEUE_RESTORED_PARTIAL,
    TRACK_UNAVAILABLE,
    QUEUE_RESTORE_UNAVAILABLE,
}

data class PlaybackFeedbackEvent(
    val id: Long,
    val kind: PlaybackFeedbackKind,
    val omittedCount: Int = 0,
)

data class VisitedArtist(
    val id: String? = null,
    val name: String,
    val source: String = "local",
    val visitedAtMs: Long,
    val coverUrl: String? = null,
) {
    val key: String
        get() {
            val cleanId = id?.trim()?.takeIf(String::isNotBlank)
            return if (cleanId != null) "id:$cleanId"
            else "name:${source.trim().lowercase()}:${name.trim().lowercase()}"
        }

    fun toArtist(): Artist = Artist(name = name, coverUrl = coverUrl, id = id)
}

data class PlayedCollection(
    val type: PlayedCollectionType,
    val id: String,
    val title: String,
    val subtitle: String = "",
    val trackCount: Int = 0,
    val source: String = "local",
    val kind: String = "regular",
    val playedAtMs: Long,
    val coverUrl: String? = null,
    val previewCovers: List<String> = emptyList(),
) {
    val key: String get() = "${type.name}:$id"
}

data class QueueSnapshot(
    val id: String,
    val capturedAtMs: Long,
    val sourceLabel: String = "",
    val sourceType: String = "",
    val songs: List<Song>,
    val currentIndex: Int,
) {
    val currentSong: Song? get() = songs.getOrNull(currentIndex)
}

interface ClientActions {
    fun navigate(destination: MainDestination)
    fun loadMoreAlbums()
    fun openAlbum(album: Album)
    fun closeAlbum()
    fun setVolume(volume: Float)
    fun checkInstance(instanceUrl: String) {}
    fun editInstanceAddress() {}
    fun startGoogleLogin() {}
    fun consumeGoogleAuthLaunch() {}
    fun reportGoogleAuthLaunchFailed() {}
    fun login(instanceUrl: String, username: String, password: String)
    fun changePassword(currentPassword: String, newPassword: String)
    fun beginAddAccount() {}
    fun cancelAddAccount() {}
    fun switchAccount(accountKey: String) {}
    fun removeSavedAccount(accountKey: String) {}
    fun setSleepTimer(minutes: Int) {}
    fun setSleepTimerEndOfTrack() {}
    fun setSleepTimerFinishCurrentTrack(enabled: Boolean) {}
    fun cancelSleepTimer() {}
    fun setKeepScreenOnEnabled(enabled: Boolean) {}
    fun setThemeMode(mode: AppThemeMode) {}
    fun setBluetoothLyricsMode(mode: BluetoothLyricsMode) {}
    fun clearImageCache() {}
    fun setIncludePrereleaseUpdates(enabled: Boolean) {}
    fun checkForAppUpdate() {}
    fun openAppUpdateDialog() {}
    fun dismissAppUpdateDialog() {}
    fun startAppUpdateDownload() {}
    fun cancelAppUpdateDownload() {}
    fun installDownloadedAppUpdate() {}
    fun updateDisplayName(displayName: String) {}
    fun setAccountAvatar(sourceUri: String) {}
    fun clearAccountAvatar() {}
    fun dismissProfileNotice() {}
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
    fun setPlaybackMode(
        shuffle: Boolean,
        repeatMode: Int,
        currentShuffle: Boolean = false,
        currentRepeatMode: Int = 0,
    ) {
        if (currentShuffle != shuffle) toggleShuffle()
        var mode = currentRepeatMode.coerceIn(0, 2)
        val target = repeatMode.coerceIn(0, 2)
        var steps = 0
        while (mode != target && steps++ < 3) {
            cycleRepeat()
            mode = (mode + 1) % 3
        }
    }
    fun selectQueueItem(index: Int)
    fun retryPlayback()

    fun dismissLibraryMutationNotice() {}
    fun toggleFavorite(song: Song) {}
    fun createPlaylist(name: String, description: String = "", songIds: List<String> = emptyList()) {}
    fun updatePlaylist(playlist: PlaylistSummary, name: String, description: String) {}
    fun deletePlaylist(playlist: PlaylistSummary) {}
    fun addPlaylistSongs(playlistIds: List<String>, songIds: List<String>) {}
    fun removePlaylistSongs(songIds: List<String>) {}
    fun replacePlaylistOrder(songIds: List<String>) {}
    fun movePlaylistShelf(fromIndex: Int, toIndex: Int) {}
    fun replacePlaylistShelfOrder(ids: List<String>) {}
    fun selectPlaylistSort(mode: PlaylistSortMode) {}
    fun selectPlaylistSongSort(mode: PlaylistSongSortMode) {}
    fun addFavorites(songs: List<Song>) {}
    fun playSongs(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {}
    fun playSingleAsNext(song: Song, fromSearch: Boolean = false) {}
    fun setSecondaryOverlayActive(active: Boolean) {}
    fun dismissPlaybackFeedback(eventId: Long) {}
    fun playPlaylist(playlist: PlaylistSummary, shuffle: Boolean = false) {}
    fun enqueuePlaylist(playlist: PlaylistSummary) {}
    fun playNext(song: Song) {}
    fun playNextSongs(songs: List<Song>) {}
    fun enqueue(songs: List<Song>) {}
    fun clearQueue() {}
    fun removeHistory(songId: String) {}
    fun clearHistory() {}
    fun removeVisitedArtist(artistKey: String) {}
    fun clearVisitedArtists() {}
    fun removePlayedCollection(collectionKey: String) {}
    fun clearPlayedCollections() {}
    fun playPlayedCollection(collection: PlayedCollection) {}
    fun openPlayedCollection(collection: PlayedCollection) {}
    fun restoreQueueSnapshot(snapshotId: String) {}
    fun removeQueueSnapshot(snapshotId: String) {}
    fun clearQueueSnapshots() {}
    fun setPlaylistCover(playlist: PlaylistSummary, sourceUri: String) {}
    fun clearPlaylistCover(playlist: PlaylistSummary) {}
    fun searchPlaylistSongs(query: String) {}
    fun loadMorePlaylistSongs() {}
    fun closePlaylistSongPicker() {}
    fun refreshArtists(query: String = "") {}
    fun loadMoreArtists() {}
    fun openArtist(artist: Artist) {}
    fun closeArtist() {}
    fun loadMoreArtistSongs() {}
    fun refreshPlayStats() {}
    fun searchAlbums(query: String) {}
    fun loadMoreSearchAlbums() {}
    fun loadMoreArtistAlbums() {}
    fun selectSongSort(sort: String) {}
    fun refreshDiscovery() {}
    fun openRecentSearch(entry: SearchEntity) {}
    fun removeRecentSearch(entry: SearchEntity) {}
    fun clearRecentSearches() {}
    fun removeRecentSearchTerm(term: String) {}
    fun clearRecentSearchTerms() {}
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
    fun selectDiscoverLanguage(language: String) {}
}

internal val LocalPlaybackPositionMs = staticCompositionLocalOf<StateFlow<Long>?> { null }

@Composable
internal fun rememberPlaybackPositionMsState(fallbackPositionMs: Long): State<Long> {
    val flow = LocalPlaybackPositionMs.current
    return flow?.collectAsState() ?: rememberUpdatedState(fallbackPositionMs)
}

internal val LocalSleepTimer = staticCompositionLocalOf<StateFlow<SleepTimerState>?> { null }

@Composable
internal fun rememberSleepTimerState(): State<SleepTimerState> {
    val flow = LocalSleepTimer.current
    return flow?.collectAsState() ?: rememberUpdatedState(SleepTimerState())
}