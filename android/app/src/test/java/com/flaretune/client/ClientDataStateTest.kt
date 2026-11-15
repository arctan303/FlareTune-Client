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
class ClientDataStateTest {
    @Test fun reusedApplicationHistoryIsVisibleImmediatelyInASecondViewModel() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val historyScope = CoroutineScope(SupervisorJob() + dispatcher)
        val store = InMemoryPlaybackHistoryStore()
        val key = HistoryIdentity("https://music.example", "data-account")
        store.write(key, listOf(HistoryEntry("historic", "Old title", "", "", 10.0, null, 100)))
        val repository = PlaybackHistoryRepository(store, historyScope, dispatcher)
        val first = DataFixture(this, repository)
        advanceUntilIdle()
        assertEquals(1, first.model.state.value.playHistory.size)
        first.close()
        val second = DataFixture(this, repository)
        try {
            advanceUntilIdle()
            assertEquals(listOf("historic"), second.model.state.value.playHistory.map { it.id })
        } finally { second.close(); historyScope.cancel() }
    }

    @Test fun passivePlaylistRefreshCannotCancelAnExplicitCompleteFavoritesPlay() = runTest {
        val fixture = DataFixture(this)
        try {
            advanceUntilIdle()
            fixture.api.onPlaylists = {
                fixture.api.onPlaylists = null
                fixture.model.playFavorites()
            }
            fixture.model.refreshPlaylists()
            advanceUntilIdle()
            assertEquals(80, fixture.player.state.value.queue.size)
            assertTrue(fixture.player.state.value.playing)
        } finally { fixture.close() }
    }

    @Test fun mixedLongAndShortHistoryIdsAreResolvedThroughTheirSupportedEndpoints() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val historyScope = CoroutineScope(SupervisorJob() + dispatcher)
        val store = InMemoryPlaybackHistoryStore()
        val key = HistoryIdentity("https://music.example", "data-account")
        val longId = "x".repeat(81)
        store.write(key, listOf(HistoryEntry("short", "Short", "", "", 1.0, null, 100),
            HistoryEntry(longId, "Long", "", "", 1.0, null, 99)))
        val fixture = DataFixture(this, PlaybackHistoryRepository(store, historyScope, dispatcher))
        try {
            advanceUntilIdle()
            assertEquals(listOf("short"), fixture.api.lastResolveIds)
            assertEquals(listOf(longId), fixture.api.singleSongIds)
            assertEquals(2, fixture.model.state.value.playHistory.size)
            assertTrue(fixture.model.state.value.playHistory.all { it.audioUrl.isNotBlank() })
            fixture.model.playHistorySong(fixture.model.state.value.playHistory.last())
            advanceUntilIdle()
            assertEquals(listOf(longId, longId), fixture.api.singleSongIds)
        } finally { fixture.close(); historyScope.cancel() }
    }

    @Test fun aFullQueueAndOnlyOneActuallyPlayedTrackProduceOneHistoryEntry() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val historyScope = CoroutineScope(SupervisorJob() + dispatcher)
        val repository = PlaybackHistoryRepository(InMemoryPlaybackHistoryStore(), historyScope, dispatcher)
        val fixture = DataFixture(this, repository)
        try {
            advanceUntilIdle()
            val queue = (0 until 30).map { dataSong("queue-" + it) }
            val tracker = com.flaretune.client.playback.PlayedTrackTracker { repository.record(it, 100) }
            fixture.player.play(queue, 0)
            tracker.observe(queue[0], true)
            repeat(3) {
                tracker.observe(queue[0], false)
                tracker.observe(queue[0], true)
            }
            advanceUntilIdle()
            assertEquals(30, fixture.model.state.value.playback.queue.size)
            assertEquals(listOf("queue-0"), fixture.model.state.value.playHistory.map { it.id })
            assertEquals(listOf("queue-0"), repository.state.value.map { it.id })
        } finally { fixture.close(); historyScope.cancel() }
    }

    @Test fun queueSaveFailureReachesUiAndRecoveryPreservesOnlyPartialRestoreWarning() = runTest {
        for (hasMissingTrack in listOf(false, true)) {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val queueScope = CoroutineScope(SupervisorJob() + dispatcher)
            val delegate = InMemoryPlaybackQueueStore()
            val key = HistoryIdentity("https://music.example", "data-account")
            delegate.write(key, SavedPlaybackQueue(listOf(QueueEntry("a", "A"), QueueEntry("b", "B")), 0))
            var failing = false
            val store = object : PlaybackQueueStore by delegate {
                override fun write(identity: HistoryIdentity, queue: SavedPlaybackQueue) {
                    if (failing) throw ApiFailure(Failure.STORAGE)
                    delegate.write(identity, queue)
                }
            }
            val repository = PlaybackQueueRepository(store, queueScope, dispatcher)
            val fixture = DataFixture(this, queue = repository)
            if (hasMissingTrack) fixture.api.missingIds += "b"
            try {
                advanceUntilIdle()
                assertEquals(if (hasMissingTrack) ClientNotice.REQUEST_FAILED else null, fixture.model.state.value.queueNotice)
                failing = true
                repository.save(fixture.player.state.value)
                advanceUntilIdle()
                assertEquals(ClientNotice.STORAGE_FAILED, fixture.model.state.value.queueNotice)
                failing = false
                repository.save(fixture.player.state.value)
                advanceUntilIdle()
                assertNull(repository.notice.value)
                assertEquals(if (hasMissingTrack) ClientNotice.REQUEST_FAILED else null, fixture.model.state.value.queueNotice)
            } finally { fixture.close(); queueScope.cancel() }
        }
    }

    @Test fun aRecreatedViewModelConsumesAnExistingApplicationQueueStorageNotice() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val queueScope = CoroutineScope(SupervisorJob() + dispatcher)
        val delegate = InMemoryPlaybackQueueStore()
        val store = object : PlaybackQueueStore by delegate {
            override fun write(identity: HistoryIdentity, queue: SavedPlaybackQueue) { throw ApiFailure(Failure.STORAGE) }
        }
        val repository = PlaybackQueueRepository(store, queueScope, dispatcher)
        val first = DataFixture(this, queue = repository)
        var second: DataFixture? = null
        try {
            advanceUntilIdle()
            repository.save(PlaybackSnapshot(queue = listOf(dataSong("track")), currentIndex = 0))
            advanceUntilIdle()
            assertEquals(ClientNotice.STORAGE_FAILED, first.model.state.value.queueNotice)
            first.close()
            second = DataFixture(this, queue = repository)
            advanceUntilIdle()
            assertEquals(ClientNotice.STORAGE_FAILED, second.model.state.value.queueNotice)
        } finally { second?.close(); queueScope.cancel() }
    }

    @Test fun logoutRejectsALateQueueStorageFailureFromRetiredIdentity() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val queueScope = CoroutineScope(SupervisorJob() + dispatcher)
        val delegate = InMemoryPlaybackQueueStore()
        lateinit var fixture: DataFixture
        val store = object : PlaybackQueueStore by delegate {
            override fun write(identity: HistoryIdentity, queue: SavedPlaybackQueue) {
                fixture.model.logout()
                throw ApiFailure(Failure.STORAGE)
            }
        }
        val repository = PlaybackQueueRepository(store, queueScope, dispatcher)
        fixture = DataFixture(this, queue = repository)
        try {
            advanceUntilIdle()
            repository.save(PlaybackSnapshot(queue = listOf(dataSong("retired")), currentIndex = 0))
            advanceUntilIdle()
            // Retirement now preserves disk; a late write failure still cannot reach CONNECT.
            assertNull(repository.notice.value)
            assertEquals(ClientScreen.CONNECT, fixture.model.state.value.screen)
            assertNull(fixture.model.state.value.queueNotice)
        } finally { fixture.close(); queueScope.cancel() }
    }

    @Test fun coldQueueRestoreKeepsDuplicatesPositionAndModesButDoesNotStartPlayback() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val queueScope = CoroutineScope(SupervisorJob() + dispatcher)
        val store = InMemoryPlaybackQueueStore()
        val key = HistoryIdentity("https://music.example", "data-account")
        store.write(key, SavedPlaybackQueue(listOf(QueueEntry("a", "A"), QueueEntry("b", "B"), QueueEntry("a", "A")),
            currentIndex = 2, positionMs = 4500, shuffle = true, repeatMode = 2))
        val fixture = DataFixture(this, queue = PlaybackQueueRepository(store, queueScope, dispatcher))
        try {
            advanceUntilIdle()
            val playback = fixture.model.state.value.playback
            assertEquals(listOf("a", "b", "a"), playback.queue.map { it.id })
            assertEquals(2, playback.currentIndex)
            assertEquals(4500L, playback.positionMs)
            assertTrue(playback.shuffle)
            assertEquals(2, playback.repeatMode)
            assertFalse(playback.playing)
            assertEquals(listOf("a", "b"), fixture.api.lastResolveIds)
            assertTrue(playback.queue.all { it.audioUrl.isNotBlank() })
            assertFalse(fixture.model.state.value.queueRestoring)
        } finally { fixture.close(); queueScope.cancel() }
    }

    @Test fun savedQueueDropsMissingCurrentTrackAndStartsNearestFollowingTrackAtZero() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val queueScope = CoroutineScope(SupervisorJob() + dispatcher)
        val store = InMemoryPlaybackQueueStore()
        store.write(HistoryIdentity("https://music.example", "data-account"),
            SavedPlaybackQueue(listOf(QueueEntry("a", "A"), QueueEntry("b", "B"), QueueEntry("c", "C")),
                currentIndex = 1, positionMs = 8000))
        val fixture = DataFixture(this, queue = PlaybackQueueRepository(store, queueScope, dispatcher))
        fixture.api.missingIds += "b"
        try {
            advanceUntilIdle()
            val playback = fixture.model.state.value.playback
            assertEquals(listOf("a", "c"), playback.queue.map { it.id })
            assertEquals("c", playback.current!!.id)
            assertEquals(0L, playback.positionMs)
            assertFalse(playback.playing)
        } finally { fixture.close(); queueScope.cancel() }
    }

    @Test fun explicitUserPlaybackSupersedesTheColdRestoreBeforeItsReadCompletes() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val queueScope = CoroutineScope(SupervisorJob() + dispatcher)
        val store = InMemoryPlaybackQueueStore()
        store.write(HistoryIdentity("https://music.example", "data-account"),
            SavedPlaybackQueue(listOf(QueueEntry("old", "Old")), 0))
        val fixture = DataFixture(this, queue = PlaybackQueueRepository(store, queueScope, dispatcher))
        fixture.api.onResolve = {
            fixture.api.onResolve = null
            fixture.model.playFavorites()
        }
        try {
            advanceUntilIdle()
            assertEquals("favorite-0", fixture.player.state.value.current!!.id)
            assertTrue(fixture.player.state.value.playing)
            assertEquals(0, fixture.player.restoreCalls)
            assertFalse(fixture.model.state.value.queueRestoring)
        } finally { fixture.close(); queueScope.cancel() }
    }

    @Test fun logoutDuringQueueRestorePreservesDiskAndCannotRepopulatePlayer() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val queueScope = CoroutineScope(SupervisorJob() + dispatcher)
        val store = InMemoryPlaybackQueueStore()
        val key = HistoryIdentity("https://music.example", "data-account")
        store.write(key, SavedPlaybackQueue(listOf(QueueEntry("old", "Old")), 0))
        val fixture = DataFixture(this, queue = PlaybackQueueRepository(store, queueScope, dispatcher))
        fixture.api.onResolve = { fixture.model.logout() }
        try {
            advanceUntilIdle()
            assertEquals(ClientScreen.CONNECT, fixture.model.state.value.screen)
            assertTrue(fixture.player.state.value.queue.isEmpty())
            assertEquals(listOf("old"), store.read(key)!!.entries.map { it.id })
        } finally { fixture.close(); queueScope.cancel() }
    }

    @Test fun queueEditActionsPreserveTimelineOwnershipAndIgnoreOutOfBoundsRequests() = runTest {
        val fixture = DataFixture(this)
        try {
            advanceUntilIdle()
            fixture.model.playFavorites()
            advanceUntilIdle()
            fixture.model.moveQueueItem(0, 2)
            advanceUntilIdle()
            assertEquals("favorite-0", fixture.player.state.value.current!!.id)
            assertEquals(2, fixture.player.state.value.currentIndex)
            fixture.model.removeQueueItem(2)
            advanceUntilIdle()
            assertEquals(79, fixture.player.state.value.queue.size)
            assertEquals("favorite-3", fixture.player.state.value.current!!.id)
            fixture.model.removeQueueItem(-1)
            fixture.model.moveQueueItem(0, 1000)
            assertEquals(79, fixture.player.state.value.queue.size)
        } finally { fixture.close() }
    }
    @Test fun togglingAnExistingTranslationOnlyChangesDisplayAndNeverRequestsGeneration() = runTest {
        val fixture = DataFixture(this)
        fixture.api.translationDocument = LyricDocument(
            lines = listOf(LyricLine("Original", "译文", 0.0)),
            resultRef = "opaque-ref", translationState = LyricTranslationStatus.READY,
            translationSupported = true, syncMode = "line")
        try {
            advanceUntilIdle()
            fixture.player.play(listOf(dataSong("translated")), 0)
            advanceUntilIdle()
            assertFalse(fixture.model.state.value.translationEnabled)
            fixture.model.toggleTranslation()
            assertTrue(fixture.model.state.value.translationEnabled)
            assertEquals("译文", fixture.model.state.value.lyricState.lines.single().translation)
            fixture.model.toggleTranslation()
            assertFalse(fixture.model.state.value.translationEnabled)
            assertEquals(0, fixture.api.translationRequests)
        } finally { fixture.close() }
    }
    @Test fun homeLoadsModernPlaylistsAndLatestCoverAndFavoritesPlayAlwaysFetchesTheCompleteDetail() = runTest {
        val fixture = DataFixture(this)
        try {
            advanceUntilIdle()
            val state = fixture.model.state.value
            assertEquals(listOf("real-favorite", "regular"), state.playlists.map { it.id })
            assertEquals(80, state.favoriteSongs.size)
            assertEquals("https://music.example/media/newest.jpg", state.latestFavoriteCoverUrl)
            assertNotEquals(state.favoriteSongs.first().coverUrl, state.latestFavoriteCoverUrl)
            fixture.api.entries = fixture.api.entries + PlaylistSong(
                dataSong("added-after-preview"), sortOrder = 80, addedAt = 1001)
            fixture.model.playFavorites()
            advanceUntilIdle()
            assertEquals(81, fixture.player.state.value.queue.size)
            assertEquals("added-after-preview", fixture.player.state.value.queue.last().id)
        } finally { fixture.close() }
    }

    @Test fun playlistDetailsExposeAllSongsAndCloseRejectsTheCancelledResult() = runTest {
        val fixture = DataFixture(this)
        try {
            advanceUntilIdle()
            fixture.model.openPlaylist(fixture.model.state.value.playlists.last())
            assertTrue(fixture.model.state.value.playlistLoading)
            advanceUntilIdle()
            assertEquals(80, fixture.model.state.value.playlistSongs.size)
            fixture.api.onPlaylist = { fixture.model.closePlaylist() }
            fixture.model.refreshPlaylist()
            advanceUntilIdle()
            assertNull(fixture.model.state.value.selectedPlaylist)
            assertTrue(fixture.model.state.value.playlistSongs.isEmpty())
            assertFalse(fixture.model.state.value.playlistLoading)
        } finally { fixture.close() }
    }

    @Test fun categoriesKeepIndependentCursorsAndSupersededLanguagesCannotReplaceCurrentResults() = runTest {
        val fixture = DataFixture(this)
        try {
            advanceUntilIdle()
            fixture.model.loadMore()
            advanceUntilIdle()
            assertEquals(50, fixture.api.songRequests.last().second)
            fixture.api.onSongs = { language ->
                if (language == "zh") fixture.model.selectSongLanguage("ja")
            }
            fixture.model.selectSongLanguage("zh")
            advanceUntilIdle()
            assertEquals("ja", fixture.model.state.value.songLanguage)
            assertEquals("ja-0", fixture.model.state.value.songs.single().id)
            assertEquals("ja" to 0, fixture.api.songRequests.last())
            fixture.model.selectAlbumLanguage("en")
            advanceUntilIdle()
            assertEquals("en", fixture.model.state.value.albumLanguage)
            assertEquals("ja", fixture.model.state.value.songLanguage)
            assertEquals("en" to 0, fixture.api.albumRequests.last())
            fixture.model.selectSongLanguage("all")
            advanceUntilIdle()
            assertEquals("all" to 0, fixture.api.songRequests.last())
        } finally { fixture.close() }
    }

    @Test fun logoutDuringFavoritePlaybackReadClearsAllNewPrivateStateAndCannotStartTheOldQueue() = runTest {
        val fixture = DataFixture(this)
        try {
            advanceUntilIdle()
            fixture.model.openPlaylist(fixture.model.state.value.playlists.last())
            advanceUntilIdle()
            fixture.api.onPlaylist = { fixture.model.logout() }
            fixture.model.playFavorites()
            advanceUntilIdle()
            val state = fixture.model.state.value
            assertEquals(ClientScreen.CONNECT, state.screen)
            assertTrue(state.playlists.isEmpty())
            assertTrue(state.playlistSongs.isEmpty())
            assertTrue(state.favoriteSongs.isEmpty())
            assertTrue(state.playHistory.isEmpty())
            assertEquals(LyricStatus.IDLE, state.lyricState.status)
            assertTrue(fixture.player.state.value.queue.isEmpty())
        } finally { fixture.close() }
    }

    @Test fun playbackDrivesUnifiedLyricsAndPositionWhileExplicitInstrumentalMakesNoRequest() = runTest {
        val fixture = DataFixture(this)
        try {
            advanceUntilIdle()
            fixture.player.state.value = PlaybackSnapshot(current = dataSong("instrumental").copy(language = "instrumental"))
            advanceUntilIdle()
            assertEquals(0, fixture.api.lyricRequests)
            assertEquals(LyricStatus.INSTRUMENTAL, fixture.model.state.value.lyricState.status)
            assertEquals("纯音乐，请欣赏", fixture.model.state.value.lyricState.lines.single().text)
            fixture.player.state.value = PlaybackSnapshot(current = dataSong("unknown").copy(language = ""), positionMs = 2500)
            advanceUntilIdle()
            assertEquals(1, fixture.api.lyricRequests)
            assertEquals(LyricStatus.READY, fixture.model.state.value.lyricState.status)
            assertEquals(1, fixture.model.state.value.activeLyricIndex)
            fixture.player.state.value = fixture.player.state.value.copy(positionMs = 500)
            advanceUntilIdle()
            assertEquals(-1, fixture.model.state.value.activeLyricIndex)
            assertEquals(1, fixture.api.lyricRequests)
        } finally { fixture.close() }
    }

    @Test fun deviceHistoryIsHydratedOnceAndReplayUsesFreshMediaAndLogoutDetachesPersistentAccountData() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val historyScope = CoroutineScope(SupervisorJob() + dispatcher)
        val store = InMemoryPlaybackHistoryStore()
        val key = HistoryIdentity("https://music.example", "data-account")
        store.write(key, listOf(HistoryEntry("historic", "Old title", "", "", 10.0, null, 100)))
        val repository = PlaybackHistoryRepository(store, historyScope, dispatcher)
        val fixture = DataFixture(this, repository)
        try {
            advanceUntilIdle()
            assertEquals(listOf("historic"), fixture.model.state.value.playHistory.map { it.id })
            assertEquals(1, fixture.api.resolveRequests)
            assertEquals("https://music.example/media/resolved.mp3", fixture.model.state.value.playHistory.single().audioUrl)
            fixture.model.playHistorySong(fixture.model.state.value.playHistory.single())
            advanceUntilIdle()
            assertEquals(1, fixture.api.singleSongRequests)
            assertEquals("https://music.example/media/fresh.mp3", fixture.player.state.value.current!!.audioUrl)
            fixture.model.logout()
            advanceUntilIdle()
            assertTrue(fixture.model.state.value.playHistory.isEmpty())
            assertTrue(repository.state.value.isEmpty())
            assertEquals(listOf("historic"), store.read(key).map { it.id })
        } finally { fixture.close(); historyScope.cancel() }
    }

    @Test fun playSingleAsNextAndRecentSearchSongInsertAfterCurrentWithoutReplacingQueue() = runTest {
        val fixture = DataFixture(this)
        try {
            advanceUntilIdle()
            val s1 = dataSong("s1")
            val s2 = dataSong("s2")
            val s3 = dataSong("s3")
            val s4 = dataSong("s4")

            // Empty queue -> playSingleAsNext starts [s1].
            fixture.model.playSingleAsNext(s1)
            advanceUntilIdle()
            assertEquals(listOf("s1"), fixture.player.state.value.queue.map { it.id })
            assertEquals(0, fixture.player.state.value.currentIndex)

            // Non-empty queue [s1, s2, s3] at index 0 -> inserts s4 at index 1 and switches playback to s4.
            fixture.model.playSongs(listOf(s1, s2, s3), 0)
            advanceUntilIdle()
            fixture.model.playSingleAsNext(s4)
            advanceUntilIdle()
            assertEquals(listOf("s1", "s4", "s2", "s3"), fixture.player.state.value.queue.map { it.id })
            assertEquals("s4", fixture.player.state.value.current?.id)
            assertEquals(1, fixture.player.state.value.currentIndex)

            // Pre-search recent song card resolves/plays via playSingleAsNext without wiping queue.
            fixture.model.openRecentSearch(SearchEntity.fromSong(dataSong("recent-x")))
            advanceUntilIdle()
            assertEquals(listOf("s1", "s4", "recent-x", "s2", "s3"), fixture.player.state.value.queue.map { it.id })
            assertEquals("recent-x", fixture.player.state.value.current?.id)
            assertEquals(2, fixture.player.state.value.currentIndex)
            assertTrue(fixture.player.singleAsNextCalls.contains("recent-x"))
        } finally { fixture.close() }
    }

    @Test fun historyExtrasTracksVisitedArtistsPlayedCollectionsAndRestoresQueueSnapshots() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val extrasScope = CoroutineScope(SupervisorJob() + dispatcher)
        val store = JsonListeningHistoryExtrasStore(TestExtrasStorage())
        val extras = ListeningHistoryExtrasRepository(store, extrasScope, dispatcher)
        val fixture = DataFixture(this, historyExtras = extras)
        try {
            advanceUntilIdle()

            // 1. Opening an artist loads detail and records a visited artist.
            fixture.model.openArtist(Artist(name = "Artist One", id = "artist-1"))
            advanceUntilIdle()
            assertEquals(listOf("Artist One"), fixture.model.state.value.visitedArtists.map { it.name })
            assertEquals(listOf("artist-1"), fixture.model.state.value.visitedArtists.map { it.id })

            // 2. Playing a playlist records PlayedCollectionType.PLAYLIST and loads >= 2 songs.
            fixture.model.playPlaylist(PlaylistSummary("regular", "Regular", "regular", songCount = 80))
            advanceUntilIdle()
            assertEquals(listOf("regular"), fixture.model.state.value.playedCollections.map { it.id })
            assertEquals(listOf(PlayedCollectionType.PLAYLIST), fixture.model.state.value.playedCollections.map { it.type })
            assertTrue(fixture.player.state.value.queue.size >= 2)

            // 3. Replacing the multi-song queue with a new queue auto-captures the old queue snapshot.
            val queueA = listOf(dataSong("qa-1"), dataSong("qa-2"))
            fixture.model.playSongs(queueA, 0)
            advanceUntilIdle()
            assertEquals(1, fixture.model.state.value.queueSnapshots.size)
            val savedRegularSnap = fixture.model.state.value.queueSnapshots.first()
            assertEquals(80, savedRegularSnap.songs.size)

            // Playing queueB captures queueA.
            val queueB = listOf(dataSong("qb-1"), dataSong("qb-2"))
            fixture.model.playSongs(queueB, 1)
            advanceUntilIdle()
            assertEquals(2, fixture.model.state.value.queueSnapshots.size)
            assertEquals(listOf("qa-1", "qa-2"), fixture.model.state.value.queueSnapshots.first().songs.map { it.id })

            // Playing queueC captures queueB; also verify identical replacement skips duplicate capture.
            val queueC = listOf(dataSong("qc-1"), dataSong("qc-2"))
            fixture.model.playSongs(queueC, 0)
            advanceUntilIdle()
            assertEquals(3, fixture.model.state.value.queueSnapshots.size)

            // Restoring queueA snapshot resolves via api.resolveSongs and preserves current queueC snapshot.
            val targetSnapId = fixture.model.state.value.queueSnapshots[1].id // queueA
            fixture.model.restoreQueueSnapshot(targetSnapId)
            advanceUntilIdle()
            assertEquals(listOf("qa-1", "qa-2"), fixture.player.state.value.queue.map { it.id })
            assertEquals(0, fixture.player.state.value.currentIndex)
            assertEquals(4, fixture.model.state.value.queueSnapshots.size)

            // Single-item remove & clear actions work across all three extra categories.
            val artistKey = fixture.model.state.value.visitedArtists.single().key
            fixture.model.removeVisitedArtist(artistKey)
            advanceUntilIdle()
            assertTrue(fixture.model.state.value.visitedArtists.isEmpty())

            val colKey = fixture.model.state.value.playedCollections.single().key
            fixture.model.removePlayedCollection(colKey)
            advanceUntilIdle()
            assertTrue(fixture.model.state.value.playedCollections.isEmpty())

            val headSnapId = fixture.model.state.value.queueSnapshots.first().id
            fixture.model.removeQueueSnapshot(headSnapId)
            advanceUntilIdle()
            assertEquals(3, fixture.model.state.value.queueSnapshots.size)

            fixture.model.clearQueueSnapshots()
            advanceUntilIdle()
            assertTrue(fixture.model.state.value.queueSnapshots.isEmpty())
        } finally { fixture.close(); extrasScope.cancel() }
    }

    @Test fun searchRootGatingPreventsOverlayAndDetailScreensFromPollutingRecentSearches() = runTest {
        val searchStore = SearchHistoryStore(TestExtrasStorage())
        val identity = HistoryIdentity("https://music.example", "data-account")
        val fixture = DataFixture(this, searchHistoryStore = searchStore)
        try {
            advanceUntilIdle()
            val historySong = dataSong("from-history")
            val albumSong1 = dataSong("alb-track-1")
            val albumSong2 = dataSong("alb-track-2")
            val homeNonSearchSong = dataSong("non-search-song")
            val searchSong = dataSong("from-search")

            fixture.model.navigate(MainDestination.SEARCH)
            fixture.model.setSecondaryOverlayActive(true)
            fixture.model.playSingleAsNext(historySong, fromSearch = false)
            fixture.model.openArtist(Artist("Overlay Artist", id = "artist-overlay"))
            advanceUntilIdle()
            assertTrue(searchStore.load(identity).entities.isEmpty())

            // Closing overlay but opening album detail on top of Search still must not pollute recent searches.
            fixture.model.closeArtist()
            fixture.model.setSecondaryOverlayActive(false)
            fixture.model.openAlbum(Album("alb-overlay", "Album Overlay", "Artist One", trackCount = 2))
            advanceUntilIdle()
            fixture.model.playSongs(listOf(albumSong1, albumSong2), 0)
            fixture.model.openArtist(Artist("Album Detail Artist", id = "artist-album"))
            advanceUntilIdle()
            assertTrue(searchStore.load(identity).entities.isEmpty())

            // Explicit non-search single play even on SEARCH destination does not record search history.
            fixture.model.closeArtist()
            fixture.model.closeAlbum()
            fixture.model.playSingleAsNext(homeNonSearchSong, fromSearch = false)
            advanceUntilIdle()
            assertTrue(searchStore.load(identity).entities.isEmpty())

            // Returning to Search root records explicit search song / artist selections.
            fixture.model.playSingleAsNext(searchSong, fromSearch = true)
            advanceUntilIdle()
            fixture.model.openArtist(Artist("Search Artist", id = "artist-search"))
            advanceUntilIdle()
            val recents = searchStore.load(identity).entities
            assertEquals(2, recents.size)
            assertEquals(listOf("artist" to "artist-search", "song" to "from-search"), recents.map { it.type to it.id })
        } finally { fixture.close() }
    }

    @Test fun captureQueueSnapshotRemapsCurrentIndexWhenInvalidEntriesAreFiltered() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val extrasScope = CoroutineScope(SupervisorJob() + dispatcher)
        val store = JsonListeningHistoryExtrasStore(TestExtrasStorage())
        val repo = ListeningHistoryExtrasRepository(store, extrasScope, dispatcher)
        val fixture = DataFixture(this, historyExtras = repo)
        try {
            advanceUntilIdle()
            val invalid0 = Song("", "Blank 0", audioUrl = "https://music.example/media/b0.mp3")
            val invalid2 = Song("   ", "Blank 2", audioUrl = "https://music.example/media/b2.mp3")
            val good1 = dataSong("good-1")
            val good2 = dataSong("good-2")
            val good3 = dataSong("good-3")
            val replacement = listOf(dataSong("rep-1"), dataSong("rep-2"))

            // Preceding invalid entry shifts surviving good2 from index 2 -> remapped index 1.
            fixture.player.state.value = PlaybackSnapshot(
                current = good2,
                queue = listOf(invalid0, good1, good2, good3),
                currentIndex = 2,
                positionMs = 4321L,
            )
            fixture.model.playSongs(replacement, 0)
            advanceUntilIdle()

            val firstSnap = fixture.model.state.value.queueSnapshots.first()
            assertEquals(listOf("good-1", "good-2", "good-3"), firstSnap.songs.map { it.id })
            assertEquals(1, firstSnap.currentIndex)

            // Trailing invalid entry at currentIndex clamps cleanly to last surviving item (good2 -> index 1).
            fixture.player.state.value = PlaybackSnapshot(
                current = invalid2,
                queue = listOf(good1, good2, invalid2),
                currentIndex = 2,
                positionMs = 999L,
            )
            fixture.model.playSongs(listOf(dataSong("rep-3")), 0)
            advanceUntilIdle()

            val secondSnap = fixture.model.state.value.queueSnapshots.first()
            assertEquals(listOf("good-1", "good-2"), secondSnap.songs.map { it.id })
            assertEquals(1, secondSnap.currentIndex)
        } finally { fixture.close(); extrasScope.cancel() }
    }

    @Test fun unavailableHistoryTrackAndPartialOrEmptySnapshotRestoreEmitAccurateFeedback() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val extraScope = CoroutineScope(SupervisorJob() + dispatcher)
        val historyRepo = PlaybackHistoryRepository(InMemoryPlaybackHistoryStore(), extraScope, dispatcher)
        val store = JsonListeningHistoryExtrasStore(TestExtrasStorage())
        val repo = ListeningHistoryExtrasRepository(store, extraScope, dispatcher)
        val fixture = DataFixture(this, history = historyRepo, historyExtras = repo)
        try {
            advanceUntilIdle()
            val missingSong = dataSong("dead-track")
            // Seed missingSong into playback history repository, then mark it unavailable on server.
            historyRepo.record(missingSong, 100)
            advanceUntilIdle()
            fixture.api.missingIds += "dead-track"

            // Unplayable history track emits TRACK_UNAVAILABLE + PLAYBACK_FAILED without invoking gateway.
            fixture.model.playHistorySong(missingSong)
            advanceUntilIdle()
            assertTrue(fixture.player.singleAsNextCalls.isEmpty())
            assertEquals(PlaybackFeedbackKind.TRACK_UNAVAILABLE, fixture.model.state.value.playbackFeedback?.kind)
            assertEquals(ClientNotice.PLAYBACK_FAILED, fixture.model.state.value.historyNotice)
            fixture.model.dismissPlaybackFeedback(fixture.model.state.value.playbackFeedback!!.id)

            // Prepare a 3-track snapshot by replacing an active 3-track queue.
            val q1 = dataSong("q1")
            val q2 = dataSong("q2")
            val q3 = dataSong("q3")
            fixture.player.state.value = PlaybackSnapshot(
                current = q2,
                queue = listOf(q1, q2, q3),
                currentIndex = 1,
                positionMs = 2000L,
            )
            fixture.model.playSongs(listOf(dataSong("other")), 0)
            advanceUntilIdle()
            val snapshotId = fixture.model.state.value.queueSnapshots.first().id

            // Case A: All entries unplayable -> QUEUE_RESTORE_UNAVAILABLE, active queue untouched.
            fixture.api.missingIds.clear()
            fixture.api.missingIds.addAll(listOf("q1", "q2", "q3"))
            fixture.model.restoreQueueSnapshot(snapshotId)
            advanceUntilIdle()
            assertEquals(listOf("other"), fixture.player.state.value.queue.map { it.id })
            assertEquals(PlaybackFeedbackKind.QUEUE_RESTORE_UNAVAILABLE, fixture.model.state.value.playbackFeedback?.kind)
            assertEquals(ClientNotice.SOME_SOURCES_UNAVAILABLE, fixture.model.state.value.historyNotice)
            fixture.model.dismissPlaybackFeedback(fixture.model.state.value.playbackFeedback!!.id)

            // Case B: 1 of 3 entries unplayable -> plays 2 surviving entries at remapped index + QUEUE_RESTORED_PARTIAL.
            fixture.api.missingIds.clear()
            fixture.api.missingIds += "q1"
            fixture.model.restoreQueueSnapshot(snapshotId)
            advanceUntilIdle()
            assertEquals(listOf("q2", "q3"), fixture.player.state.value.queue.map { it.id })
            assertEquals("q2", fixture.player.state.value.current?.id)
            assertTrue(fixture.player.state.value.playing)
            val partial = fixture.model.state.value.playbackFeedback
            assertEquals(PlaybackFeedbackKind.QUEUE_RESTORED_PARTIAL, partial?.kind)
            assertEquals(1, partial?.omittedCount)
            assertEquals(ClientNotice.SOME_SOURCES_UNAVAILABLE, fixture.model.state.value.historyNotice)
        } finally { fixture.close(); extraScope.cancel() }
    }
}

private class TestExtrasStorage : HistoryStorage {
    private val map = linkedMapOf<String, String>()
    override fun read(key: String): String? = map[key]
    override fun write(key: String, value: String) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
}

private fun dataSong(id: String) = Song(id, id, audioUrl = "https://music.example/media/" + id + ".mp3",
    coverUrl = "https://music.example/media/" + id + ".jpg")

@OptIn(ExperimentalCoroutinesApi::class)
private class DataFixture(
    scope: TestScope,
    history: PlaybackHistoryRepository? = null,
    queue: PlaybackQueueRepository? = null,
    historyExtras: ListeningHistoryExtrasRepository? = null,
    searchHistoryStore: SearchHistoryStore? = null,
) {
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    val api = DataApi()
    val player = DataPlayer()
    private val connections = DataConnections(ActiveConnection(
        SessionNetwork(InstanceAddress.parse("https://music.example")), api,
        AuthSession(Account("data-account", "listener", "Listener"), "csrf-data", false)))
    val model: ClientViewModel
    init {
        Dispatchers.setMain(dispatcher)
        model = ClientViewModel(
            connections = connections,
            player = player,
            ioDispatcher = dispatcher,
            history = history,
            queueRepository = queue,
            searchHistoryStore = searchHistoryStore,
            historyExtras = historyExtras,
        )
    }
    fun close() { model.viewModelScope.cancel(); Dispatchers.resetMain() }
}

private class DataApi : AccountApi {
    val favorite = PlaylistSummary("real-favorite", "Favorite", "favorite", songCount = 80, previewCovers = listOf("/one"))
    private val regular = PlaylistSummary("regular", "Regular", "regular", songCount = 80)
    var entries = (0 until 80).map { index ->
        PlaylistSong(dataSong("favorite-" + index).copy(coverUrl =
            if (index == 10) "https://music.example/media/newest.jpg" else "https://music.example/media/old.jpg"),
            index, if (index == 10) 1000 else index.toLong())
    }
    val songRequests = mutableListOf<Pair<String, Int>>()
    val albumRequests = mutableListOf<Pair<String, Int>>()
    var onSongs: ((String) -> Unit)? = null
    var onPlaylist: (() -> Unit)? = null
    var onPlaylists: (() -> Unit)? = null
    var onResolve: (() -> Unit)? = null
    var lastResolveIds = emptyList<String>()
    val singleSongIds = mutableListOf<String>()
    val missingIds = mutableSetOf<String>()
    var translationDocument: LyricDocument? = null
    var translationRequests = 0
    var lyricRequests = 0
    var resolveRequests = 0
    var singleSongRequests = 0
    override fun songs(query: String, offset: Int) = songs(query, offset, "all")
    override fun songs(query: String, offset: Int, language: String): SongPage {
        songRequests += language to offset
        onSongs?.invoke(language)
        return SongPage(listOf(dataSong(language + "-" + offset)), offset + 50, offset == 0)
    }
    override fun albums(offset: Int) = albums(offset, "all")
    override fun albums(offset: Int, language: String): AlbumPage {
        albumRequests += language to offset
        return AlbumPage(emptyList(), offset + 20, false)
    }
    override fun favorites() = entries.map { it.song }
    override fun playlists(): List<PlaylistSummary> { onPlaylists?.invoke(); return listOf(favorite, regular) }
    override fun playlist(id: String): PlaylistDetail {
        val snapshot = entries
        onPlaylist?.invoke()
        return PlaylistDetail(if (id == favorite.id) favorite else regular, snapshot)
    }
    override fun lyrics(songId: String, resultRef: String?): LyricDocument {
        lyricRequests++
        translationDocument?.let { return it }
        return LyricDocument(lines = listOf(LyricLine("first", timeSeconds = 1.0),
            LyricLine("second", timeSeconds = 2.0)), syncMode = "line")
    }
    override fun translateLyrics(resultRef: String): LyricDocument { translationRequests++; return translationDocument ?: LyricDocument() }
    override fun resolveSongs(ids: List<String>): List<Song> {
        resolveRequests++
        if (ids.any { it.length > 80 }) throw ApiFailure(Failure.REQUEST)
        lastResolveIds = ids
        onResolve?.invoke()
        return ids.filterNot { it in missingIds }.map { dataSong(it).copy(audioUrl = "https://music.example/media/resolved.mp3") }
    }
    override fun song(id: String): Song {
        singleSongRequests++
        singleSongIds += id
        return if (id in missingIds) dataSong(id).copy(audioUrl = "")
        else dataSong(id).copy(audioUrl = "https://music.example/media/fresh.mp3")
    }
    override fun artist(name: String, offset: Int): ArtistDetail =
        ArtistDetail(
            Artist(
                name = if (name == "artist-1") "Artist One" else name,
                id = if (name == "artist-1") "artist-1" else null,
                photoUrl = "https://music.example/media/artist.jpg",
            ),
            listOf(dataSong("artist-track-1")),
            50,
            false,
        )
    override fun album(id: String): AlbumDetail =
        AlbumDetail(Album(id, "Album $id", "Artist One", trackCount = 2),
            listOf(dataSong("$id-1"), dataSong("$id-2")))
    override fun changePassword(current: String, replacement: String) = Unit
    override fun logout() = Unit
}

private class DataConnections(override var current: ActiveConnection?) : Connections {
    override val invalidations = MutableSharedFlow<ClientNotice>(extraBufferCapacity = 1)
    override val lastOrigin = "https://music.example"
    override suspend fun restore() = current
    override suspend fun login(address: String, username: String, password: String) = current!!
    override suspend fun changePassword(current: String, replacement: String) { this.current = null }
    override suspend fun logout() { current?.network?.retire(); current = null }
}

private class DataPlayer : PlaybackGateway {
    override val state = MutableStateFlow(PlaybackSnapshot())
    override val imageLoader: ImageLoader? = null
    override suspend fun connect(connection: ActiveConnection) = Unit
    val singleAsNextCalls = mutableListOf<String>()
    override fun play(songs: List<Song>, index: Int) {
        state.value = PlaybackSnapshot(current = songs[index], queue = songs, currentIndex = index, playing = true)
    }
    override fun playSingleAsNext(song: Song): Boolean {
        singleAsNextCalls += song.id
        val snap = state.value
        if (snap.queue.isEmpty() || snap.currentIndex !in snap.queue.indices) {
            play(listOf(song), 0)
            return true
        }
        val insertIndex = snap.currentIndex + 1
        val nextQueue = snap.queue.toMutableList().apply { add(insertIndex, song) }
        state.value = snap.copy(current = song, queue = nextQueue, currentIndex = insertIndex, playing = true)
        return true
    }
    var restoreCalls = 0
    override fun restoreQueue(songs: List<Song>, currentIndex: Int, positionMs: Long, shuffle: Boolean, repeatMode: Int) {
        restoreCalls++
        state.value = PlaybackSnapshot(current = songs[currentIndex], queue = songs, currentIndex = currentIndex,
            positionMs = positionMs, shuffle = shuffle, repeatMode = repeatMode, playing = false)
    }
    override fun removeQueueItem(index: Int) {
        val old = state.value
        val list = old.queue.toMutableList()
        list.removeAt(index)
        val target = when {
            list.isEmpty() -> -1
            index < old.currentIndex -> old.currentIndex - 1
            else -> old.currentIndex.coerceAtMost(list.lastIndex)
        }
        state.value = old.copy(queue = list, currentIndex = target, current = list.getOrNull(target))
    }
    override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val old = state.value
        val list = old.queue.toMutableList()
        list.add(toIndex, list.removeAt(fromIndex))
        state.value = old.copy(queue = list, currentIndex = list.indexOfFirst { it.id == old.current?.id })
    }
    override fun stopAndClear() { state.value = PlaybackSnapshot() }
    override fun setVolume(volume: Float) = Unit
    override fun togglePlay() = Unit
    override fun next() = Unit
    override fun previous() = Unit
    override fun seekTo(positionMs: Long) = Unit
    override fun toggleShuffle() = Unit
    override fun cycleRepeat() = Unit
    override fun select(index: Int) = Unit
    override fun retry() = Unit
    override fun release() = Unit
}