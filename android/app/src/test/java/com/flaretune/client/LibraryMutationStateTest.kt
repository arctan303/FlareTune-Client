package com.flaretune.client

import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.flaretune.client.data.*
import com.flaretune.client.model.*
import com.flaretune.client.playback.PlaybackGateway
import com.flaretune.client.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryMutationStateTest {
    @Test fun fullMembershipKeepsUnplayableEntriesWhenReorderedAndRemovedButPlaybackFiltersThem() = runTest {
        val f = LibraryFixture(this)
        try {
            advanceUntilIdle()
            f.model.openPlaylist(f.api.regular)
            advanceUntilIdle()
            assertEquals(listOf("a", "missing", "b"), f.model.state.value.selectedPlaylistEntries.map { it.song.id })
            assertEquals(listOf("a", "b"), f.model.state.value.playlistSongs.map { it.id })
            assertFalse(f.model.state.value.selectedPlaylistEntries[1].canPlay)
            f.model.replacePlaylistOrder(listOf("b", "missing", "a"))
            advanceUntilIdle()
            assertEquals(listOf("b", "missing", "a"), f.api.replacements.last())
            assertEquals(1L, f.model.state.value.libraryMutationVersion)
            f.model.removePlaylistSongs(listOf("b"))
            advanceUntilIdle()
            assertEquals(listOf("missing", "a"), f.api.replacements.last())
            f.model.playPlaylist(f.api.regular)
            advanceUntilIdle()
            assertEquals(listOf("a"), f.player.state.value.queue.map { it.id })
        } finally { f.close() }
    }

    @Test fun conflictRefreshesCompleteMembersWithoutAutomaticallyReplayingOldDraft() = runTest {
        val f = LibraryFixture(this)
        try {
            advanceUntilIdle(); f.model.openPlaylist(f.api.regular); advanceUntilIdle()
            f.api.conflict = true
            f.model.replacePlaylistOrder(listOf("b", "a", "missing"))
            advanceUntilIdle()
            assertTrue(f.model.state.value.libraryMutationConflict)
            assertEquals(0L, f.model.state.value.libraryMutationVersion)
            assertEquals(1, f.api.replacements.size)
            f.model.replacePlaylistOrder(listOf("a", "b", "missing"))
            advanceUntilIdle()
            assertEquals(1, f.api.replacements.size)
            f.model.dismissLibraryMutationNotice()
            f.api.conflict = false
            f.model.replacePlaylistOrder(f.model.state.value.selectedPlaylistEntries.map { it.song.id })
            advanceUntilIdle()
            assertEquals(2, f.api.replacements.size)
        } finally { f.close() }
    }

    @Test fun partialMultiTargetAddKeepsDraftOpenAndPublishesPerTargetResults() = runTest {
        val f = LibraryFixture(this)
        try {
            advanceUntilIdle()
            f.api.partial = true
            f.model.addPlaylistSongs(listOf(f.api.regular.id, f.api.favorite.id), listOf("new"))
            advanceUntilIdle()
            assertEquals(0L, f.model.state.value.libraryMutationVersion)
            assertEquals(ClientNotice.REQUEST_FAILED, f.model.state.value.libraryMutationNotice)
            assertEquals(listOf("applied", "failed"), f.model.state.value.libraryMutationResults.map { it.outcome })
            assertFalse(f.model.state.value.libraryMutationBusy)
        } finally { f.close() }
    }

    @Test fun createRetryReusesIdempotencyKeyAndDuplicateSubmitWhileBusyIsIgnored() = runTest {
        val f = LibraryFixture(this)
        try {
            advanceUntilIdle()
            f.api.createFailures = 1
            f.model.createPlaylist("Trip")
            f.model.createPlaylist("Trip")
            advanceUntilIdle()
            assertEquals(1, f.api.createKeys.size)
            assertEquals(0L, f.model.state.value.libraryMutationVersion)
            f.model.createPlaylist("Trip"); advanceUntilIdle()
            assertEquals(2, f.api.createKeys.size)
            assertEquals(f.api.createKeys[0], f.api.createKeys[1])
            assertEquals(1L, f.model.state.value.libraryMutationVersion)
            f.model.createPlaylist("Other"); advanceUntilIdle()
            assertNotEquals(f.api.createKeys[1], f.api.createKeys[2])
        } finally { f.close() }
    }

    @Test fun retiredIdentityDoesNotReceiveLateMutationOrSuccessVersion() = runTest {
        val f = LibraryFixture(this)
        try {
            advanceUntilIdle()
            f.api.onUpdate = { f.model.logout() }
            f.model.updatePlaylist(f.api.regular, "Changed", "")
            advanceUntilIdle()
            assertEquals(ClientScreen.CONNECT, f.model.state.value.screen)
            assertTrue(f.model.state.value.playlists.isEmpty())
            assertEquals(0L, f.model.state.value.libraryMutationVersion)
            assertFalse(f.model.state.value.libraryMutationBusy)
        } finally { f.close() }
    }

    @Test fun favoriteMembershipUpdatesAcrossPagesAndFavoriteMetadataCannotBeDeletedOrRenamed() = runTest {
        val f = LibraryFixture(this)
        try {
            advanceUntilIdle()
            assertEquals(setOf("starred"), f.model.state.value.favoriteSongIds)
            f.model.toggleFavorite(librarySong("new")); advanceUntilIdle()
            assertEquals(setOf("starred", "new"), f.model.state.value.favoriteSongIds)
            f.model.toggleFavorite(librarySong("starred")); advanceUntilIdle()
            assertEquals(setOf("new"), f.model.state.value.favoriteSongIds)
            val version = f.model.state.value.libraryMutationVersion
            f.model.deletePlaylist(f.api.favorite)
            f.model.updatePlaylist(f.api.favorite, "Wrong", "Wrong")
            advanceUntilIdle()
            assertEquals(version, f.model.state.value.libraryMutationVersion)
            assertEquals(0, f.api.metadataWrites)
            assertEquals(0, f.api.deletes)
        } finally { f.close() }
    }

    @Test fun shelfReplaceIsAnExactPermutationAndUsesItsOwnRevision() = runTest {
        val f = LibraryFixture(this)
        try {
            advanceUntilIdle()
            f.model.replacePlaylistShelfOrder(listOf(f.api.regular.id))
            advanceUntilIdle()
            assertNull(f.api.savedShelf)
            f.model.replacePlaylistShelfOrder(listOf(f.api.regular.id, f.api.favorite.id))
            advanceUntilIdle()
            assertEquals(15L, f.api.shelfExpectedRevision)
            assertEquals(listOf("regular", "fav"), f.api.savedShelf?.map { it.id })
        } finally { f.close() }
    }

    @Test fun playlistPickerDoesNotChangeGlobalTabQueryOrDetailAndHasIndependentPagination() = runTest {
        val f = LibraryFixture(this)
        try {
            advanceUntilIdle()
            f.model.selectTab(LibraryTab.FAVORITES)
            f.model.search("global")
            advanceUntilIdle()
            f.model.openPlaylist(f.api.regular); advanceUntilIdle()
            f.model.searchPlaylistSongs("picker"); advanceUntilIdle()
            assertEquals(LibraryTab.FAVORITES, f.model.state.value.tab)
            assertEquals("global", f.model.state.value.query)
            assertEquals("regular", f.model.state.value.selectedPlaylist?.id)
            assertEquals(listOf(Triple("picker", 0, "all")), f.api.songReads.filter { it.first == "picker" })
            f.model.loadMorePlaylistSongs(); advanceUntilIdle()
            assertEquals(2, f.model.state.value.pickerSongs.size)
            assertFalse(f.model.state.value.pickerHasMore)
            f.model.closePlaylistSongPicker()
            assertTrue(f.model.state.value.pickerSongs.isEmpty())
            assertEquals("regular", f.model.state.value.selectedPlaylist?.id)
        } finally { f.close() }
    }

    @Test fun artistSongsAndAlbumsPageIndependentlyAndSearchUsesRealAlbumEndpoint() = runTest {
        val f = LibraryFixture(this)
        try {
            advanceUntilIdle()
            f.model.search("needle"); advanceUntilIdle()
            assertTrue(f.api.albumReads.any { it.first == "needle" && it.third == null })
            assertEquals("needle", f.model.state.value.albumSearchQuery)
            f.model.openArtist(Artist("Singer")); advanceUntilIdle()
            assertEquals(listOf("artist-0"), f.model.state.value.artistSongs.map { it.id })
            f.model.loadMoreArtistSongs(); f.model.loadMoreArtistAlbums(); advanceUntilIdle()
            assertEquals(listOf("artist-0", "artist-50"), f.model.state.value.artistSongs.map { it.id })
            assertEquals(2, f.model.state.value.artistAlbums.size)
            assertFalse(f.model.state.value.artistHasMore)
            val album = f.model.state.value.artistAlbums.last()
            f.model.openAlbum(album); advanceUntilIdle()
            assertEquals(album.id, f.model.state.value.selectedAlbum?.id)
            assertNull(f.model.state.value.selectedArtist)
        } finally { f.close() }
    }

    @Test fun coverFailureAfterUploadDiscardsDraftAndUsesSlotRevisionInsteadOfPlaylistRevision() = runTest {
        val f = LibraryFixture(this)
        try {
            advanceUntilIdle()
            f.api.failCover = true
            f.model.setPlaylistCover(f.api.regular, "content://local/cover")
            advanceUntilIdle()
            assertEquals(listOf("slot", "process", "upload", "apply:44", "discard:draft"), f.api.coverCalls)
            assertEquals(0L, f.model.state.value.libraryMutationVersion)
            assertTrue(f.model.state.value.libraryMutationConflict)
            f.model.dismissLibraryMutationNotice()
            f.api.failCover = false
            f.model.setPlaylistCover(f.api.regular, "content://local/cover"); advanceUntilIdle()
            assertEquals(1L, f.model.state.value.libraryMutationVersion)
            assertEquals(1, f.api.coverCalls.count { it.startsWith("discard:") })
        } finally { f.close() }
    }


    @Test fun recentSearchEntitiesAndTermsAreAccountOwnedMetadataAndSwitchingCannotPublishOldList() = runTest {
        val storage = LibraryMemoryStorage()
        val f = LibraryFixture(this, storage)
        try {
            advanceUntilIdle()
            f.model.navigate(MainDestination.SEARCH)
            f.model.search("private query"); advanceUntilIdle()
            f.model.playSongs(listOf(librarySong("searched"))); advanceUntilIdle()
            assertTrue(f.model.state.value.recentSearchTerms.isEmpty())
            assertTrue(f.model.state.value.recentSearches.isEmpty())
            f.model.refresh(); advanceUntilIdle()
            assertEquals(listOf("private query"), f.model.state.value.recentSearchTerms)
            assertEquals("searched", f.model.state.value.recentSearches.first().id)
            f.model.removeRecentSearchTerm("private query"); advanceUntilIdle()
            assertTrue(f.model.state.value.recentSearchTerms.isEmpty())
            f.model.logout(); advanceUntilIdle()
            val other = ActiveConnection(SessionNetwork(InstanceAddress.parse("https://music.example")), f.api,
                AuthSession(Account("other-account", "other", "Other"), "other-csrf", false))
            f.connections.current = other
            f.model.login("https://music.example", "other", "password")
            advanceUntilIdle()
            assertTrue(f.model.state.value.recentSearches.isEmpty())
            assertTrue(f.model.state.value.recentSearchTerms.isEmpty())
            for (raw in storage.values.values) {
                assertFalse(raw.contains("https://"))
                assertFalse(raw.contains("csrf"))
                assertFalse(raw.contains("audioUrl"))
                assertFalse(raw.contains("coverUrl"))
            }
        } finally { f.close() }
    }

    @Test fun searchRecentSnapshotStaysLockedAcrossQuickPlaybackAndNewSearchesUntilRefresh() = runTest {
        val storage = LibraryMemoryStorage()
        val seedStore = SearchHistoryStore(storage)
        val key = HistoryIdentity("https://music.example", "library-account")
        seedStore.addTerm(key, "second term")
        seedStore.addTerm(key, "first term")
        seedStore.addEntity(key, SearchEntity.fromSong(librarySong("song-two")))
        seedStore.addEntity(key, SearchEntity.fromSong(librarySong("song-one")))
        val f = LibraryFixture(this, storage)
        try {
            advanceUntilIdle()
            f.model.navigate(MainDestination.SEARCH)
            assertEquals(listOf("first term", "second term"), f.model.state.value.recentSearchTerms)
            assertEquals(listOf("song-one", "song-two"), f.model.state.value.recentSearches.map { it.id })

            // Quick-playing a song from recent searches never reorders the visible or persisted list.
            val secondEntity = f.model.state.value.recentSearches[1]
            f.model.openRecentSearch(secondEntity); advanceUntilIdle()
            assertEquals("song-two", f.player.state.value.current?.id)
            assertEquals(listOf("song-one", "song-two"), f.model.state.value.recentSearches.map { it.id })

            // New searches and search-result plays record on disk without mutating the locked UI snapshot.
            f.model.search("third term"); advanceUntilIdle()
            f.model.playSongs(listOf(librarySong("song-three"))); advanceUntilIdle()
            assertEquals(listOf("first term", "second term"), f.model.state.value.recentSearchTerms)
            assertEquals(listOf("song-one", "song-two"), f.model.state.value.recentSearches.map { it.id })

            // Explicit removal updates the visible snapshot immediately without surfacing unrefreshed writes.
            f.model.removeRecentSearchTerm("first term"); advanceUntilIdle()
            assertEquals(listOf("second term"), f.model.state.value.recentSearchTerms)

            // Manual refresh unlocks and syncs the latest recorded snapshot (and quick-play did not reorder song-two ahead of song-one).
            f.model.refresh(); advanceUntilIdle()
            assertEquals(listOf("third term", "second term"), f.model.state.value.recentSearchTerms)
            assertEquals(listOf("song-three", "song-one", "song-two"), f.model.state.value.recentSearches.map { it.id })
        } finally { f.close() }
    }

    @Test fun discoveryReusesDailyMetadataCacheResolvesMediaAndAppliesCooldownAndExclusions() = runTest {
        val storage = LibraryMemoryStorage()
        val first = LibraryFixture(this, storage)
        try {
            advanceUntilIdle()
            assertEquals(1, first.api.randomRequests)
            assertEquals(1, first.api.spotlightRequests)
            first.model.refreshDiscovery(); advanceUntilIdle()
            assertEquals(1, first.api.randomRequests)
            first.cache!!.markRefreshed(HistoryIdentity("https://music.example", "library-account"), "daily", System.currentTimeMillis() - 3000)
            first.cache.markRefreshed(HistoryIdentity("https://music.example", "library-account"), "spotlight", System.currentTimeMillis() - 3000)
            first.model.refreshDiscovery(); advanceUntilIdle()
            assertEquals(2, first.api.randomRequests)
            assertEquals(listOf("random"), first.api.randomExcludes.last())
            assertTrue("Spotlight" in first.api.spotlightExcludes.last())
            first.close()
            val second = LibraryFixture(this, storage)
            try {
                advanceUntilIdle()
                assertEquals(0, second.api.randomRequests)
                assertEquals(0, second.api.spotlightRequests)
                assertEquals(listOf("random"), second.model.state.value.discoverySongs.map { it.id })
                assertTrue(second.model.state.value.discoverySongs.all { it.canPlay })
            } finally { second.close() }
        } finally { first.close() }
    }

    @Test fun explicitCollectionPlaybackOwnsItsContextAndLanguageSortDoesNotAlterMembership() = runTest {
        val f = LibraryFixture(this)
        try {
            advanceUntilIdle()
            val songs = listOf(librarySong("outside-a"), librarySong("outside-b"), librarySong("outside-a"))
            f.model.playSongs(songs, 1)
            advanceUntilIdle()
            assertEquals(songs, f.player.state.value.queue)
            assertEquals(1, f.player.state.value.currentIndex)
            f.model.selectSongSort("asc"); advanceUntilIdle()
            assertEquals("asc", f.api.songSorts.last())
            assertEquals(songs, f.player.state.value.queue)
            assertTrue(f.api.replacements.isEmpty())
        } finally { f.close() }
    }


    @Test fun detailLoadingCannotBeMistakenForACompleteEmptyPlaylistDuringMemberEdit() = runTest {
        val f = LibraryFixture(this)
        try {
            advanceUntilIdle()
            f.api.onDetail = { f.model.removePlaylistSongs(listOf("a")) }
            f.model.openPlaylist(f.api.regular); advanceUntilIdle()
            assertTrue(f.api.replacements.isEmpty())
            assertEquals(3, f.model.state.value.selectedPlaylistEntries.size)
        } finally { f.close() }
    }

    @Test fun accountRetirementAfterCoverUploadDoesNotApplyDraftToNewSession() = runTest {
        val f = LibraryFixture(this)
        try {
            advanceUntilIdle()
            f.api.onUpload = { f.model.logout() }
            f.model.setPlaylistCover(f.api.regular, "content://local/cover")
            advanceUntilIdle()
            assertEquals(ClientScreen.CONNECT, f.model.state.value.screen)
            assertTrue(f.api.coverCalls.contains("discard:draft"))
            assertFalse(f.api.coverCalls.any { it.startsWith("apply:") })
            assertEquals(0L, f.model.state.value.libraryMutationVersion)
        } finally { f.close() }
    }


    @Test fun knownCapacityErrorsGiveActionableSafeNoticesWithoutPretendingToBeRevisionConflicts() = runTest {
        for ((code, expected) in listOf("PLAYLIST_LIMIT_REACHED" to ClientNotice.PLAYLIST_LIMIT,
            "PLAYLIST_SONG_LIMIT_REACHED" to ClientNotice.PLAYLIST_SONG_LIMIT,
            "image_upload_limit" to ClientNotice.IMAGE_UPLOAD_LIMIT, "image_too_large" to ClientNotice.IMAGE_TOO_LARGE)) {
            val f = LibraryFixture(this)
            try {
                advanceUntilIdle()
                f.api.createError = ApiFailure(Failure.REQUEST, if (code.startsWith("PLAYLIST")) 409 else 429, code)
                f.model.createPlaylist("Capacity")
                advanceUntilIdle()
                assertEquals(expected, f.model.state.value.libraryMutationNotice)
                assertFalse(f.model.state.value.libraryMutationConflict)
                assertEquals(0L, f.model.state.value.libraryMutationVersion)
            } finally { f.close() }
        }
        val f = LibraryFixture(this)
        try {
            advanceUntilIdle(); f.api.partial = true; f.api.partialCode = "PLAYLIST_SONG_LIMIT_REACHED"
            f.model.addPlaylistSongs(listOf("regular", "fav"), listOf("new")); advanceUntilIdle()
            assertEquals(ClientNotice.PLAYLIST_SONG_LIMIT, f.model.state.value.libraryMutationNotice)
            assertFalse(f.model.state.value.libraryMutationConflict)
            assertEquals(0L, f.model.state.value.libraryMutationVersion)
        } finally { f.close() }
    }

    @Test fun playerGainRestoresImmediatelyAndRapidChangesOnlyPersistTheLastDeviceValue() = runTest {
        val preferences = object : TranslationPreferences {
            var gain = .35f
            val writes = mutableListOf<Float>()
            override fun read() = false
            override fun write(enabled: Boolean) = Unit
            override fun readVolume() = gain
            override fun writeVolume(volume: Float) { gain = volume; writes += volume }
        }
        val f = LibraryFixture(this, preferences = preferences)
        try {
            advanceUntilIdle()
            assertEquals(.35f, f.player.state.value.volume, .0001f)
            f.model.setVolume(.2f); f.model.setVolume(.6f); f.model.setVolume(.8f)
            assertEquals(.8f, f.player.state.value.volume, .0001f)
            assertTrue(preferences.writes.isEmpty())
            advanceUntilIdle()
            assertEquals(listOf(.8f), preferences.writes)
            assertEquals(.8f, preferences.gain, .0001f)
            f.model.setVolume(Float.NaN); advanceUntilIdle()
            assertEquals(listOf(.8f), preferences.writes)
        } finally { f.close() }
    }

    @Test fun clearQueueCancelsPendingRestoreWithoutRetiringConnection() = runTest {
        val f = LibraryFixture(this)
        try {
            advanceUntilIdle()
            f.player.play(listOf(librarySong("a")), 0)
            f.model.clearQueue(); advanceUntilIdle()
            assertEquals(1, f.player.clears)
            assertTrue(f.player.state.value.queue.isEmpty())
            assertEquals(ClientScreen.LIBRARY, f.model.state.value.screen)
            assertTrue(f.connections.current!!.network.isActive)
        } finally { f.close() }
    }
}

private fun librarySong(id: String) = Song(id, id, artist = "Singer", durationSeconds = 60.0, audioUrl = "https://music.example/media/$id")

@OptIn(ExperimentalCoroutinesApi::class)
private class LibraryFixture(scope: TestScope, val storage: HistoryStorage? = null, preferences: TranslationPreferences? = null) {
    val dispatcher = StandardTestDispatcher(scope.testScheduler)
    val api = LibraryApi()
    val cache = storage?.let(::DiscoveryCache)
    val player = LibraryPlayer()
    val connections = LibraryConnections(ActiveConnection(SessionNetwork(InstanceAddress.parse("https://music.example")), api,
        AuthSession(Account("library-account", "listener", "Listener"), "csrf-fixture", false)))
    val model: ClientViewModel
    init {
        Dispatchers.setMain(dispatcher)
        model = ClientViewModel(connections, player, dispatcher, translationPreferences = preferences, coverImageProcessor = CoverImageProcessor {
            api.coverCalls += "process"
            byteArrayOf(1, 2, 3)
        }, searchHistoryStore = storage?.let(::SearchHistoryStore), discoveryCache = cache)
    }
    fun close() { model.viewModelScope.cancel(); Dispatchers.resetMain() }
}

private class LibraryApi : AccountApi {
    var regular = PlaylistSummary("regular", "Regular", "regular", revision = 7)
    var favorite = PlaylistSummary("fav", "Favorite", "favorite", revision = 3)
    var entries = listOf(PlaylistSong(librarySong("a")), PlaylistSong(librarySong("missing").copy(audioUrl = "")),
        PlaylistSong(librarySong("b")))
    var favorites = listOf(PlaylistSong(librarySong("starred")))
    var conflict = false
    var partial = false
    var createFailures = 0
    var createError: ApiFailure? = null
    var partialCode = "REVISION_CONFLICT"
    var onUpdate: (() -> Unit)? = null
    var onDetail: (() -> Unit)? = null
    var onUpload: (() -> Unit)? = null
    var metadataWrites = 0
    var deletes = 0
    val createKeys = mutableListOf<String?>()
    val replacements = mutableListOf<List<String>>()
    var savedShelf: List<ShelfItem>? = null
    var shelfExpectedRevision: Long? = null
    val songReads = mutableListOf<Triple<String, Int, String>>()
    val albumReads = mutableListOf<Triple<String, Int, String?>>()
    val coverCalls = mutableListOf<String>()
    var failCover = false
    var randomRequests = 0
    var spotlightRequests = 0
    val randomExcludes = mutableListOf<List<String>>()
    val spotlightExcludes = mutableListOf<List<String>>()
    val songSorts = mutableListOf<String>()
    override fun randomSongs(limit: Int, exclude: List<String>): List<Song> {
        randomRequests++; randomExcludes += exclude
        return listOf(librarySong("random"))
    }
    override fun spotlightArtist(exclude: List<String>): SpotlightArtist {
        spotlightRequests++; spotlightExcludes += exclude
        return SpotlightArtist("Spotlight", 1, listOf(librarySong("spotlight-song")))
    }
    override fun songs(query: String, offset: Int, language: String, sort: String): SongPage {
        songSorts += sort
        return songs(query, offset, language)
    }
    override fun songs(query: String, offset: Int) = songs(query, offset, "all")
    override fun songs(query: String, offset: Int, language: String): SongPage {
        songReads += Triple(query, offset, language)
        return SongPage(listOf(librarySong("$query-$offset")), offset + 50, offset == 0)
    }
    override fun favorites() = favorites.map { it.song }
    override fun playlists() = listOf(favorite, regular)
    override fun playlist(id: String): PlaylistDetail { onDetail?.invoke(); return if (id == favorite.id) PlaylistDetail(favorite, favorites) else PlaylistDetail(regular, entries) }
    override fun playlistShelf() = PlaylistShelf(15, items = listOf(ShelfItem(favorite.id), ShelfItem(regular.id)))
    override fun createPlaylist(name: String, description: String, songIds: List<String>, idempotencyKey: String?): PlaylistMutationResult {
        createKeys += idempotencyKey
        createError?.let { throw it }
        if (createFailures-- > 0) throw ApiFailure(Failure.NETWORK)
        return PlaylistMutationResult(regular.copy(name = name))
    }
    override fun updatePlaylist(id: String, expectedRevision: Long, name: String?, description: String?): PlaylistMutationResult {
        metadataWrites++; onUpdate?.invoke()
        regular = regular.copy(name = name ?: regular.name, revision = regular.revision + 1)
        return PlaylistMutationResult(regular)
    }
    override fun deletePlaylist(id: String, expectedRevision: Long): PlaylistDeleteResult {
        deletes++
        return PlaylistDeleteResult("applied", id, playlistShelf())
    }
    override fun replacePlaylistSongs(id: String, songIds: List<String>, expectedRevision: Long, name: String?, description: String?): PlaylistMutationResult {
        replacements += songIds.toList()
        if (conflict) throw ApiFailure(Failure.REQUEST, 409, "REVISION_CONFLICT", details = MusicFailureDetails(playlist = regular))
        assertEquals(regular.revision, expectedRevision)
        val all = entries.associateBy { it.song.id }
        entries = songIds.map { all.getValue(it) }
        regular = regular.copy(revision = regular.revision + 1)
        return PlaylistMutationResult(regular, PlaylistDetail(regular, entries))
    }
    override fun removePlaylistSong(id: String, songId: String, expectedRevision: Long): PlaylistMutationResult {
        favorites = favorites.filterNot { it.song.id == songId }
        favorite = favorite.copy(revision = favorite.revision + 1)
        return PlaylistMutationResult(favorite, PlaylistDetail(favorite, favorites))
    }
    override fun addPlaylistSongs(targets: List<PlaylistTarget>, songIds: List<String>): PlaylistAddResult {
        if (partial) return PlaylistAddResult("partial", listOf(
            PlaylistTargetResult(targets[0].playlistId, "applied", addedSongIds = songIds),
            PlaylistTargetResult(targets[1].playlistId, "failed", error = partialCode)), listOf(targets[0].playlistId))
        favorites = (favorites + songIds.map { PlaylistSong(librarySong(it)) }).distinctBy { it.song.id }
        favorite = favorite.copy(revision = favorite.revision + 1)
        return PlaylistAddResult("applied", targets.map { PlaylistTargetResult(it.playlistId, "applied", addedSongIds = songIds) },
            targets.map { it.playlistId })
    }
    override fun updatePlaylistShelf(items: List<ShelfItem>, expectedRevision: Long): ShelfMutationResult {
        savedShelf = items; shelfExpectedRevision = expectedRevision
        return ShelfMutationResult("applied", PlaylistShelf(expectedRevision + 1, items = items))
    }
    override fun albums(offset: Int, language: String) = AlbumPage(emptyList(), 0, false)
    override fun browseAlbums(query: String, offset: Int, language: String, artist: String?): AlbumPage {
        albumReads += Triple(query, offset, artist)
        return AlbumPage(listOf(Album("album-$query-$artist-$offset", "Album", "Singer")), offset + 20, offset == 0)
    }
    override fun album(id: String) = AlbumDetail(Album(id, "Album", "Singer"), listOf(librarySong("album-song")))
    override fun artists(query: String, offset: Int, language: String) = ArtistPage(listOf(Artist("Singer")), 50, false)
    override fun artist(name: String, offset: Int) = ArtistDetail(Artist(name), listOf(librarySong("artist-$offset")), offset + 50, offset == 0)
    override fun song(id: String) = librarySong(id)
    override fun resolveSongs(ids: List<String>) = ids.map(::librarySong)
    override fun imageSlot(purpose: String, targetId: String): ImageSlot { coverCalls += "slot"; return ImageSlot(revision = 44) }
    override fun uploadPlaylistCover(bytes: ByteArray): UserImage { coverCalls += "upload"; onUpload?.invoke(); return UserImage("draft", "https://music.example/api/account/images/draft", 1024, 1024, bytes.size) }
    override fun setPlaylistCover(id: String, imageId: String?, revision: Long): ImageSlot {
        coverCalls += "apply:$revision"
        if (failCover) throw ApiFailure(Failure.REQUEST, 409, "revision_conflict")
        return ImageSlot(imageId, "https://music.example/api/account/images/draft", revision + 1)
    }
    override fun discardUserImage(id: String) { coverCalls += "discard:$id" }
    override fun changePassword(current: String, replacement: String) = Unit
    override fun logout() = Unit
}

private class LibraryConnections(override var current: ActiveConnection?) : Connections {
    override val invalidations = MutableSharedFlow<ClientNotice>(extraBufferCapacity = 1)
    override val lastOrigin = "https://music.example"
    override suspend fun restore() = current
    override suspend fun login(address: String, username: String, password: String) = current!!
    override suspend fun changePassword(current: String, replacement: String) = Unit
    override suspend fun logout() { current?.network?.retire(); current = null }
}
private class LibraryPlayer : PlaybackGateway {
    override val state = MutableStateFlow(PlaybackSnapshot())
    override val imageLoader: ImageLoader? = null
    var clears = 0
    override suspend fun connect(connection: ActiveConnection) = Unit
    override fun play(songs: List<Song>, index: Int) { state.value = PlaybackSnapshot(songs[index], songs, index, playing = true) }
    override fun clearQueue() { clears++; state.value = PlaybackSnapshot() }
    override fun setVolume(volume: Float) { state.value = state.value.copy(volume = volume) }
    override fun togglePlay() = Unit
    override fun next() = Unit
    override fun previous() = Unit
    override fun seekTo(positionMs: Long) = Unit
    override fun toggleShuffle() = Unit
    override fun cycleRepeat() = Unit
    override fun select(index: Int) = Unit
    override fun retry() = Unit
    override fun stopAndClear() { state.value = PlaybackSnapshot() }
    override fun release() = Unit
}

private class LibraryMemoryStorage : HistoryStorage {
    val values = mutableMapOf<String, String>()
    override fun read(key: String): String? = synchronized(values) { values[key] }
    override fun write(key: String, value: String) { synchronized(values) { values[key] = value } }
    override fun remove(key: String) { synchronized(values) { values.remove(key) } }
}
