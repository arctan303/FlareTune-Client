package com.arcinks.flaretune

import com.arcinks.flaretune.data.*
import com.arcinks.flaretune.model.Song
import com.arcinks.flaretune.ui.PlaybackSnapshot
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountLocalDataIsolationTest {
    private val a = HistoryIdentity("https://music.example", "A")
    private val b = HistoryIdentity("https://music.example", "B")
    private val otherInstance = HistoryIdentity("https://other.example", "A")

    @Test fun detachRetainsEachAccountsHistoryAndQueueAndRebindRestoresOnlyTarget() = runTest {
        val historyStore = InMemoryPlaybackHistoryStore()
        val queueStore = AccountQueueStore()
        val h = PlaybackHistoryRepository(historyStore, backgroundScope, StandardTestDispatcher(testScheduler))
        val q = PlaybackQueueRepository(queueStore, backgroundScope, StandardTestDispatcher(testScheduler))
        for ((key, id) in listOf(a to "a", b to "b", otherInstance to "remote-a")) {
            historyStore.write(key, listOf(entry(id)))
            queueStore.write(key, queue(id))
        }
        for ((key, id) in listOf(a to "a", b to "b", otherInstance to "remote-a", a to "a")) {
            h.bind(key); q.bind(key); runCurrent()
            assertEquals(listOf(id), h.state.value.map { it.id })
            assertEquals(listOf(id), q.state.value!!.entries.map { it.id })
            h.detach(); q.detach()
            assertTrue(h.state.value.isEmpty())
            assertNull(q.state.value)
            assertFalse(q.loaded.value)
            // Teardown events from the old Player have no repository authority.
            h.record(song("late"))
            q.save(PlaybackSnapshot())
            runCurrent()
            assertEquals(listOf(id), historyStore.read(key).map { it.id })
            assertEquals(listOf(id), queueStore.read(key)!!.entries.map { it.id })
        }
    }

    @Test fun lateHistoryReadCannotPublishOldAccountAfterDetachEvenForAtoBtoA() = runTest {
        val backing = InMemoryPlaybackHistoryStore()
        backing.write(a, listOf(entry("a")))
        backing.write(b, listOf(entry("b")))
        lateinit var repository: PlaybackHistoryRepository
        var firstRead = true
        val store = object : PlaybackHistoryStore by backing {
            override fun read(identity: HistoryIdentity): List<HistoryEntry> {
                val result = backing.read(identity)
                if (firstRead) {
                    firstRead = false
                    repository.detach()
                    repository.bind(b)
                }
                return result
            }
        }
        repository = PlaybackHistoryRepository(store, backgroundScope, StandardTestDispatcher(testScheduler))
        val published = mutableListOf<List<String>>()
        backgroundScope.launchCollector(repository, published)
        repository.bind(a); runCurrent()
        assertEquals(listOf("b"), repository.state.value.map { it.id })
        assertFalse(published.contains(listOf("a")))
        repository.detach(); repository.bind(a); runCurrent()
        assertEquals(listOf("a"), repository.state.value.map { it.id })
        assertEquals(listOf("b"), backing.read(b).map { it.id })
    }

    @Test fun lateQueueReadAndQueuedOldSaveCannotOverwriteNewAccountOrClearOldSnapshot() = runTest {
        val backing = AccountQueueStore()
        backing.write(a, queue("a"))
        backing.write(b, queue("b"))
        lateinit var repository: PlaybackQueueRepository
        var firstRead = true
        val store = object : PlaybackQueueStore by backing {
            override fun read(identity: HistoryIdentity): SavedPlaybackQueue? {
                val result = backing.read(identity)
                if (firstRead) {
                    firstRead = false
                    repository.detach()
                    repository.bind(b)
                }
                return result
            }
        }
        repository = PlaybackQueueRepository(store, backgroundScope, StandardTestDispatcher(testScheduler))
        repository.bind(a); runCurrent()
        assertEquals("b", repository.state.value!!.entries.single().id)
        repository.save(PlaybackSnapshot(current = song("late-b"), queue = listOf(song("late-b")), currentIndex = 0))
        repository.detach(); repository.bind(a); runCurrent()
        assertEquals("a", repository.state.value!!.entries.single().id)
        assertEquals("b", backing.read(b)!!.entries.single().id)
        assertEquals("a", backing.read(a)!!.entries.single().id)
    }

    @Test fun jsonListeningHistoryExtrasStorePersistsMetadataOnlyAndEnforcesCaps() {
        val backing = InMemoryHistoryStorage()
        val store = JsonListeningHistoryExtrasStore(backing)
        val artists = (1..45).map { idx ->
            VisitedArtistEntry(
                id = "artist-$idx",
                name = "Artist $idx",
                source = if (idx % 2 == 0) "local" else "youtube",
                visitedAtMs = idx * 1000L,
            )
        } + VisitedArtistEntry(id = null, name = "Artist 45", source = "youtube", visitedAtMs = 999L)
        val collections = (1..45).map { idx ->
            PlayedCollectionEntry(
                type = if (idx % 2 == 0) PlayedCollectionType.ALBUM else PlayedCollectionType.PLAYLIST,
                id = "col-$idx",
                title = "Collection $idx",
                subtitle = "Sub $idx",
                trackCount = idx,
                source = "local",
                kind = "regular",
                playedAtMs = idx * 1000L,
            )
        }
        val snapshots = (1..22).map { idx ->
            QueueSnapshotEntry(
                id = "snap-$idx",
                capturedAtMs = idx * 1000L,
                sourceLabel = "Playlist $idx",
                sourceType = "playlist",
                entries = listOf(QueueEntry("s-${idx}-1", "Song 1"), QueueEntry("s-${idx}-2", "Song 2")),
                currentIndex = 1,
            )
        } + QueueSnapshotEntry(
            id = "too-small",
            capturedAtMs = 999_999L,
            sourceLabel = "Single",
            sourceType = "song",
            entries = listOf(QueueEntry("only-one", "Only")),
            currentIndex = 0,
        )

        store.write(a, ListeningHistoryExtrasSnapshot(artists, collections, snapshots))
        val restored = store.read(a)
        assertEquals(MAX_VISITED_ARTISTS, restored.artists.size)
        assertEquals("artist-45", restored.artists.first().id)
        assertEquals(MAX_PLAYED_COLLECTIONS, restored.collections.size)
        assertEquals("col-45", restored.collections.first().id)
        assertEquals(MAX_QUEUE_SNAPSHOTS, restored.queueSnapshots.size)
        assertEquals("snap-22", restored.queueSnapshots.first().id)
        assertFalse(restored.queueSnapshots.any { it.id == "too-small" })

        val rawPayloads = backing.allValues().joinToString("\n")
        for (forbidden in listOf("coverUrl", "audioUrl", "resultRef", "cookie", "csrf", "http://", "https://")) {
            assertFalse("Unexpected $forbidden in persisted JSON", rawPayloads.contains(forbidden, ignoreCase = true))
        }

        store.clear(a)
        val cleared = store.read(a)
        assertTrue(cleared.artists.isEmpty())
        assertTrue(cleared.collections.isEmpty())
        assertTrue(cleared.queueSnapshots.isEmpty())
    }

    @Test fun listeningHistoryExtrasRepositoryIsolatesAccountsAcrossSwitchesAndAccountDeletion() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = JsonListeningHistoryExtrasStore(InMemoryHistoryStorage())
        val repo = ListeningHistoryExtrasRepository(store, backgroundScope, dispatcher)

        repo.bind(a); runCurrent()
        repo.recordArtist(VisitedArtistEntry(id = "art-a", name = "Artist A", source = "local", visitedAtMs = 100L))
        repo.recordCollection(PlayedCollectionEntry(PlayedCollectionType.ALBUM, "alb-a", "Album A", playedAtMs = 100L))
        repo.recordQueueSnapshot(QueueSnapshotEntry("q-a", 100L, "Queue A", "playlist", listOf(QueueEntry("a1", "A1"), QueueEntry("a2", "A2")), 0))
        runCurrent()

        repo.bind(b); runCurrent()
        assertTrue(repo.state.value.artists.isEmpty())
        assertTrue(repo.state.value.collections.isEmpty())
        assertTrue(repo.state.value.queueSnapshots.isEmpty())
        repo.recordArtist(VisitedArtistEntry(id = "art-b", name = "Artist B", source = "local", visitedAtMs = 200L))
        runCurrent()

        repo.bind(otherInstance); runCurrent()
        repo.recordCollection(PlayedCollectionEntry(PlayedCollectionType.PLAYLIST, "pl-other", "Other", playedAtMs = 300L))
        runCurrent()

        repo.bind(a); runCurrent()
        assertEquals(listOf("art-a"), repo.state.value.artists.map { it.id })
        assertEquals(listOf("alb-a"), repo.state.value.collections.map { it.id })
        assertEquals(listOf("q-a"), repo.state.value.queueSnapshots.map { it.id })

        repo.clearAccount(a); runCurrent()
        assertTrue(repo.state.value.artists.isEmpty())
        assertTrue(repo.state.value.collections.isEmpty())
        assertTrue(repo.state.value.queueSnapshots.isEmpty())

        repo.bind(b); runCurrent()
        assertEquals(listOf("art-b"), repo.state.value.artists.map { it.id })

        repo.bind(otherInstance); runCurrent()
        assertEquals(listOf("pl-other"), repo.state.value.collections.map { it.id })
    }

    @Test fun jsonPlaylistPreferencesStoreIsolatesAndClearsByAccountIdentity() {
        val backing = InMemoryHistoryStorage()
        val store = JsonPlaylistPreferencesStore(backing)
        store.writeSortMode(a, com.arcinks.flaretune.ui.PlaylistSortMode.NAME)
        store.writeSortMode(b, com.arcinks.flaretune.ui.PlaylistSortMode.TRACK_COUNT)
        store.writeSortMode(otherInstance, com.arcinks.flaretune.ui.PlaylistSortMode.RECENTLY_UPDATED)

        assertEquals(com.arcinks.flaretune.ui.PlaylistSortMode.NAME, store.readSortMode(a))
        assertEquals(com.arcinks.flaretune.ui.PlaylistSortMode.TRACK_COUNT, store.readSortMode(b))
        assertEquals(com.arcinks.flaretune.ui.PlaylistSortMode.RECENTLY_UPDATED, store.readSortMode(otherInstance))

        store.clear(a)
        assertEquals(com.arcinks.flaretune.ui.PlaylistSortMode.CUSTOM, store.readSortMode(a))
        assertEquals(com.arcinks.flaretune.ui.PlaylistSortMode.TRACK_COUNT, store.readSortMode(b))
        assertEquals(com.arcinks.flaretune.ui.PlaylistSortMode.RECENTLY_UPDATED, store.readSortMode(otherInstance))
    }

    private fun entry(id: String) = HistoryEntry(id, id, "", "", 10.0, null, 100)
    private fun queue(id: String) = SavedPlaybackQueue(listOf(QueueEntry(id, id)), 0, 500)
    private fun song(id: String) = Song(id, id, audioUrl = "https://music.example/media/$id")
}

private class InMemoryHistoryStorage : HistoryStorage {
    private val map = linkedMapOf<String, String>()
    override fun read(key: String): String? = map[key]
    override fun write(key: String, value: String) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
    fun allValues(): Collection<String> = map.values
}

private fun kotlinx.coroutines.CoroutineScope.launchCollector(
    repository: PlaybackHistoryRepository, published: MutableList<List<String>>,
) = kotlinx.coroutines.CoroutineScope(coroutineContext).launch {
    repository.state.collect { published += it.map { entry -> entry.id } }
}

private class AccountQueueStore : PlaybackQueueStore {
    private val data = mutableMapOf<HistoryIdentity, SavedPlaybackQueue>()
    override fun read(identity: HistoryIdentity) = data[identity.normalized()]
    override fun write(identity: HistoryIdentity, queue: SavedPlaybackQueue) { data[identity.normalized()] = queue }
    override fun clear(identity: HistoryIdentity) { data.remove(identity.normalized()) }
}
