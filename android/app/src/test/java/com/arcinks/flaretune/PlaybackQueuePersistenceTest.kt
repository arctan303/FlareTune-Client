package com.arcinks.flaretune

import com.arcinks.flaretune.data.ApiFailure
import com.arcinks.flaretune.data.Failure
import com.arcinks.flaretune.data.HistoryIdentity
import com.arcinks.flaretune.data.HistoryStorage
import com.arcinks.flaretune.data.InMemoryPlaybackQueueStore
import com.arcinks.flaretune.data.JsonPlaybackQueueStore
import com.arcinks.flaretune.data.MAX_PERSISTED_QUEUE_BYTES
import com.arcinks.flaretune.data.MAX_PERSISTED_QUEUE_ENTRIES
import com.arcinks.flaretune.data.PlaybackQueueRepository
import com.arcinks.flaretune.data.PlaybackQueueStore
import com.arcinks.flaretune.data.QueueEntry
import com.arcinks.flaretune.data.SavedPlaybackQueue
import com.arcinks.flaretune.model.ClientNotice
import com.arcinks.flaretune.ui.PlaybackSnapshot
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackQueuePersistenceTest {
    @get:Rule val temporary = TemporaryFolder()
    private val a = HistoryIdentity("https://music.example", "account-A")
    private val b = HistoryIdentity("https://music.example", "account-B")

    @Test fun diskReconstructionPreservesOccurrencesIndexPositionAndModes() {
        val directory = temporary.newFolder("queues")
        val first = JsonPlaybackQueueStore(FileStorage(directory))
        val queue = SavedPlaybackQueue(
            listOf(entry("same"), entry("other"), entry("same")), 2, 90_123, true, 2,
        )
        first.write(HistoryIdentity("MUSIC.EXAMPLE:443/", a.accountId), queue)
        val restored = JsonPlaybackQueueStore(FileStorage(directory)).read(a)
        assertEquals(queue, restored)
        assertEquals(listOf("same", "other", "same"), restored!!.entries.map { it.id })
        assertEquals(2, restored.currentIndex)
        assertEquals("", restored.entries[2].toSong().audioUrl)
        assertNull(restored.entries[2].toSong().coverUrl)
    }

    @Test fun instanceAndAccountKeysAreHashedAndClearingOneCannotClearAnother() {
        val backend = MapStorage()
        val store = JsonPlaybackQueueStore(backend)
        val elsewhere = HistoryIdentity("https://elsewhere.example", a.accountId)
        store.write(a, queue("a"))
        store.write(b, queue("b"))
        store.write(elsewhere, queue("elsewhere"))
        assertEquals(3, backend.values.size)
        backend.values.keys.forEach {
            assertTrue(it.matches(Regex("queue_v1_[a-f0-9]{64}")))
            assertFalse(it.contains(a.accountId))
            assertFalse(it.contains(a.origin))
        }
        store.clear(HistoryIdentity("MUSIC.EXAMPLE/", a.accountId))
        assertNull(store.read(a))
        assertEquals(queue("b"), store.read(b))
        assertEquals(queue("elsewhere"), store.read(elsewhere))
    }

    @Test fun snapshotPersistenceDropsAllMediaCredentialsAndPlaybackIntent() = runTest {
        val backend = MapStorage()
        val store = JsonPlaybackQueueStore(backend)
        val repository = PlaybackQueueRepository(store, backgroundScope, StandardTestDispatcher(testScheduler))
        repository.bind(a)
        assertFalse(repository.loaded.value)
        runCurrent()
        val songs = listOf("same", "second", "same").map { id ->
            entry(id).toSong().copy(
                audioUrl = "https://private.example/audio?cookie=__Host-ft_session_SECRET",
                coverUrl = "https://private.example/art?csrfToken=CSRF_SECRET&resultRef=RESULT_SECRET",
            )
        }
        repository.save(PlaybackSnapshot(
            current = songs[2], queue = songs, currentIndex = 2, positionMs = 9000,
            playing = true, buffering = true, shuffle = true, repeatMode = 1,
        ))
        runCurrent()
        val raw = backend.values.values.single()
        for (forbidden in listOf("audioUrl", "coverUrl", "cookie", "csrfToken", "resultRef",
            "__Host-ft_session_SECRET", "CSRF_SECRET", "RESULT_SECRET", "https://", "playing", "buffering")) {
            assertFalse(forbidden, raw.contains(forbidden))
        }
        assertEquals(setOf("id", "title", "artist", "album", "durationSeconds", "language"),
            JSONObject(raw).getJSONArray("entries").getJSONObject(0).keys().asSequence().toSet())
        assertEquals(listOf("same", "second", "same"), store.read(a)!!.entries.map { it.id })
        assertTrue(repository.loaded.value)
        assertNull(repository.notice.value)
    }

    @Test fun fiveHundredEntriesAreSupportedWithoutTruncationOrDeduplication() {
        val backend = MapStorage()
        val store = JsonPlaybackQueueStore(backend)
        val full = SavedPlaybackQueue(List(500) { entry("duplicate") }, 499, Long.MAX_VALUE, true, 2)
        store.write(a, full)
        assertEquals(full, store.read(a))
        assertEquals(MAX_PERSISTED_QUEUE_ENTRIES, store.read(a)!!.entries.size)
        val committed = backend.values.values.single()
        expectStorage { store.write(a, full.copy(entries = full.entries + entry("extra"))) }
        expectStorage { store.write(a, full.copy(entries = List(500) { entry("$it").copy(title = "界".repeat(1000)) })) }
        assertEquals(committed, backend.values.values.single())
    }

    @Test fun invalidQueuesAndCorruptedJsonFailClosedWithStorageNotice() = runTest {
        val backend = MapStorage()
        val store = JsonPlaybackQueueStore(backend)
        store.write(a, queue("valid"))
        val key = backend.values.keys.single()
        val valid = backend.values.getValue(key)
        for (invalid in listOf(
            queue("invalid").copy(currentIndex = -1),
            queue("invalid").copy(currentIndex = 1),
            queue("invalid").copy(positionMs = -1),
            queue("invalid").copy(repeatMode = 3),
            queue("invalid").copy(entries = listOf(entry(""))),
            queue("invalid").copy(entries = listOf(entry("finite").copy(durationSeconds = Double.NaN))),
            queue("invalid").copy(entries = listOf(entry("finite").copy(durationSeconds = Double.POSITIVE_INFINITY))),
            queue("invalid").copy(entries = listOf(entry("finite").copy(durationSeconds = -1.0))),
            SavedPlaybackQueue(emptyList(), 0),
        )) expectStorage { store.write(a, invalid) }
        assertEquals(valid, backend.values.getValue(key))
        val documents = listOf(
            "{", "[]", JSONObject(valid).put("version", 2).toString(),
            JSONObject(valid).put("version", true).toString(),
            JSONObject(valid).put("currentIndex", "0").toString(),
            JSONObject(valid).put("currentIndex", .5).toString(),
            JSONObject(valid).put("currentIndex", 1).toString(),
            JSONObject(valid).put("positionMs", "1").toString(),
            JSONObject(valid).put("positionMs", 1.5).toString(),
            JSONObject(valid).put("positionMs", -1).toString(),
            JSONObject(valid).put("shuffle", "true").toString(),
            JSONObject(valid).put("repeatMode", 3).toString(),
            JSONObject(valid).put("cookie", "forbidden").toString(),
            JSONObject(valid).apply { getJSONArray("entries").getJSONObject(0).put("audioUrl", "forbidden") }.toString(),
            JSONObject(valid).apply { getJSONArray("entries").getJSONObject(0).put("durationSeconds", "1") }.toString(),
            JSONObject(valid).apply { getJSONArray("entries").getJSONObject(0).put("language", true) }.toString(),
            JSONObject(valid).apply { getJSONArray("entries").getJSONObject(0).remove("artist") }.toString(),
            JSONObject(valid).put("entries", JSONArray().apply {
                repeat(501) { put(JSONObject(valid).getJSONArray("entries").getJSONObject(0)) }
            }).toString(),
            " ".repeat(MAX_PERSISTED_QUEUE_BYTES + 1),
        )
        for (document in documents) {
            backend.values[key] = document
            expectStorage { store.read(a) }
        }
        val repository = PlaybackQueueRepository(store, backgroundScope, StandardTestDispatcher(testScheduler))
        repository.bind(a)
        runCurrent()
        assertNull(repository.state.value)
        assertTrue(repository.loaded.value)
        assertEquals(ClientNotice.STORAGE_FAILED, repository.notice.value)
    }

    @Test fun emptySaveRemovesStorageWithoutRetiringCurrentBinding() = runTest {
        val store = InMemoryPlaybackQueueStore()
        store.write(a, queue("old"))
        val repository = PlaybackQueueRepository(store, backgroundScope, StandardTestDispatcher(testScheduler))
        repository.bind(a)
        runCurrent()
        repository.save(PlaybackSnapshot())
        runCurrent()
        assertNull(repository.state.value)
        assertNull(store.read(a))
        assertTrue(repository.loaded.value)
        repository.save(snapshot(queue("new")))
        runCurrent()
        assertEquals(queue("new"), repository.state.value)
        assertEquals(queue("new"), store.read(a))
    }

    @Test fun reloadRetriesFailedLoadAndSameIdentityBindKeepsLoadedState() = runTest {
        val delegate = InMemoryPlaybackQueueStore()
        delegate.write(a, queue("persisted"))
        var failing = true
        var reads = 0
        val store = object : PlaybackQueueStore by delegate {
            override fun read(identity: HistoryIdentity): SavedPlaybackQueue? {
                reads++
                if (failing) throw IOException("private storage detail")
                return delegate.read(identity)
            }
        }
        val repository = PlaybackQueueRepository(store, backgroundScope, StandardTestDispatcher(testScheduler))
        repository.bind(a)
        assertFalse(repository.loaded.value)
        runCurrent()
        assertTrue(repository.loaded.value)
        assertNull(repository.state.value)
        assertEquals(ClientNotice.STORAGE_FAILED, repository.notice.value)
        failing = false
        repository.reload()
        assertFalse(repository.loaded.value)
        runCurrent()
        assertEquals(queue("persisted"), repository.state.value)
        assertTrue(repository.loaded.value)
        assertNull(repository.notice.value)
        repository.bind(HistoryIdentity("MUSIC.EXAMPLE/", a.accountId))
        runCurrent()
        assertTrue(repository.loaded.value)
        assertEquals(2, reads)
    }

    @Test fun backendAndWriteFailuresNeverClaimSuccessfulPersistence() = runTest {
        val broken = JsonPlaybackQueueStore(object : HistoryStorage {
            override fun read(key: String): String? = throw IOException("private detail")
            override fun write(key: String, value: String): Unit = throw IOException("private detail")
            override fun remove(key: String): Unit = throw IOException("private detail")
        })
        expectStorage { broken.read(a) }
        expectStorage { broken.write(a, queue("new")) }
        expectStorage { broken.clear(a) }
        val delegate = InMemoryPlaybackQueueStore()
        delegate.write(a, queue("committed"))
        var failWrite = true
        val store = object : PlaybackQueueStore by delegate {
            override fun write(identity: HistoryIdentity, queue: SavedPlaybackQueue) {
                if (failWrite) throw ApiFailure(Failure.STORAGE)
                delegate.write(identity, queue)
            }
        }
        val repository = PlaybackQueueRepository(store, backgroundScope, StandardTestDispatcher(testScheduler))
        repository.bind(a)
        runCurrent()
        repository.save(snapshot(queue("failed")))
        runCurrent()
        assertEquals(queue("committed"), repository.state.value)
        assertEquals(queue("committed"), delegate.read(a))
        assertEquals(ClientNotice.STORAGE_FAILED, repository.notice.value)
        failWrite = false
        repository.save(snapshot(queue("saved")))
        runCurrent()
        assertEquals(queue("saved"), repository.state.value)
        assertNull(repository.notice.value)
    }

    @Test fun rapidAtoBtoAExecutesStaleRetirementBeforeLatestSave() = runTest {
        val store = InMemoryPlaybackQueueStore()
        store.write(a, queue("old-a"))
        store.write(b, queue("old-b"))
        val repository = PlaybackQueueRepository(store, backgroundScope, StandardTestDispatcher(testScheduler))
        repository.bind(a)
        runCurrent()
        repository.bind(b)
        repository.bind(a)
        repository.save(snapshot(queue("new-a")))
        runCurrent()
        assertEquals(queue("new-a"), repository.state.value)
        assertEquals(queue("new-a"), store.read(a))
        assertNull(store.read(b))
    }

    @Test fun failedEmptySaveQuarantinesOldQueueUntilRemovalSucceeds() = runTest {
        val delegate = InMemoryPlaybackQueueStore()
        delegate.write(a, queue("old-a"))
        var failClear = true
        val store = object : PlaybackQueueStore by delegate {
            override fun clear(identity: HistoryIdentity) {
                if (failClear) throw ApiFailure(Failure.STORAGE)
                delegate.clear(identity)
            }
        }
        val repository = PlaybackQueueRepository(store, backgroundScope, StandardTestDispatcher(testScheduler))
        repository.bind(a)
        runCurrent()
        repository.save(PlaybackSnapshot())
        runCurrent()
        assertNull(repository.state.value)
        assertEquals(ClientNotice.STORAGE_FAILED, repository.notice.value)
        repository.reload()
        runCurrent()
        assertNull(repository.state.value)
        assertEquals(queue("old-a"), delegate.read(a))
        failClear = false
        repository.reload()
        runCurrent()
        assertNull(repository.state.value)
        assertNull(delegate.read(a))
        assertNull(repository.notice.value)
        repository.save(snapshot(queue("new-a")))
        runCurrent()
        assertEquals(queue("new-a"), repository.state.value)
    }

    @Test fun failedAccountRetirementCannotBeReloadedAfterRebinding() = runTest {
        val delegate = InMemoryPlaybackQueueStore()
        delegate.write(a, queue("old-a"))
        var failClearA = true
        val store = object : PlaybackQueueStore by delegate {
            override fun clear(identity: HistoryIdentity) {
                if (identity == a && failClearA) throw ApiFailure(Failure.STORAGE)
                delegate.clear(identity)
            }
        }
        val repository = PlaybackQueueRepository(store, backgroundScope, StandardTestDispatcher(testScheduler))
        repository.bind(a)
        runCurrent()
        repository.bind(b)
        runCurrent()
        repository.bind(a)
        runCurrent()
        assertNull(repository.state.value)
        assertEquals(ClientNotice.STORAGE_FAILED, repository.notice.value)
        failClearA = false
        repository.reload()
        runCurrent()
        assertNull(repository.state.value)
        assertNull(delegate.read(a))
    }

    @Test fun lateLoadCannotPublishAfterAccountSwitchOrClear() {
        for (clear in listOf(false, true)) {
            val delegate = InMemoryPlaybackQueueStore()
            delegate.write(a, queue("old-a"))
            delegate.write(b, queue("only-b"))
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val erased = CountDownLatch(1)
            val store = object : PlaybackQueueStore by delegate {
                override fun read(identity: HistoryIdentity): SavedPlaybackQueue? {
                    val snapshot = delegate.read(identity)
                    if (identity == a) { entered.countDown(); await(release) }
                    return snapshot
                }
                override fun clear(identity: HistoryIdentity) {
                    delegate.clear(identity)
                    if (identity == a) erased.countDown()
                }
            }
            withLiveRepository(store) { repository ->
                try {
                    repository.bind(a)
                    await(entered)
                    if (clear) repository.clear() else repository.bind(b)
                    assertNull(repository.state.value)
                    assertFalse(repository.loaded.value)
                    release.countDown()
                    if (clear) {
                        await(erased)
                        assertNull(repository.state.value)
                        assertFalse(repository.loaded.value)
                    } else awaitState(repository) { it == queue("only-b") }
                    assertNull(delegate.read(a))
                    assertEquals(queue("only-b"), delegate.read(b))
                } finally { release.countDown() }
            }
        }
    }

    @Test fun realNewSnapshotSupersedesSlowStartupLoadWithoutPublishingOldQueue() {
        val delegate = InMemoryPlaybackQueueStore()
        delegate.write(a, queue("old-a"))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        lateinit var repository: PlaybackQueueRepository
        val store = object : PlaybackQueueStore by delegate {
            override fun read(identity: HistoryIdentity): SavedPlaybackQueue? {
                val snapshot = delegate.read(identity)
                entered.countDown()
                await(release)
                return snapshot
            }
            override fun write(identity: HistoryIdentity, queue: SavedPlaybackQueue) {
                assertNull("old startup data must not publish ahead of a newer real queue", repository.state.value)
                delegate.write(identity, queue)
            }
        }
        withLiveRepository(store) { live ->
            repository = live
            try {
                repository.bind(a)
                await(entered)
                repository.save(snapshot(queue("user-new")))
                release.countDown()
                awaitState(repository) { it == queue("user-new") }
                assertEquals(queue("user-new"), delegate.read(a))
            } finally { release.countDown() }
        }
    }

    @Test fun lateWriteIsErasedBeforeOtherAccountLoadsOrSameAccountRebinds() {
        for (rebindSame in listOf(false, true)) {
            val delegate = InMemoryPlaybackQueueStore()
            delegate.write(a, queue("seed-a"))
            delegate.write(b, queue("only-b"))
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val store = object : PlaybackQueueStore by delegate {
                override fun write(identity: HistoryIdentity, queue: SavedPlaybackQueue) {
                    if (queue.entries.first().id == "late-a") { entered.countDown(); await(release) }
                    delegate.write(identity, queue)
                }
            }
            withLiveRepository(store) { repository ->
                try {
                    repository.bind(a)
                    awaitState(repository) { it == queue("seed-a") }
                    repository.save(snapshot(queue("late-a")))
                    await(entered)
                    if (rebindSame) {
                        repository.clear()
                        repository.save(snapshot(queue("retired-callback")))
                        repository.bind(a)
                        repository.save(snapshot(queue("new-session")))
                    } else repository.bind(b)
                    assertNull(repository.state.value)
                    release.countDown()
                    val expected = if (rebindSame) queue("new-session") else queue("only-b")
                    awaitState(repository) { it == expected }
                    if (rebindSame) assertEquals(expected, delegate.read(a)) else assertNull(delegate.read(a))
                    assertEquals(queue("only-b"), delegate.read(b))
                } finally { release.countDown() }
            }
        }
    }

    private fun entry(id: String) = QueueEntry(id, "Title $id", "Artist", "Album", 180.0, "en")
    private fun queue(id: String) = SavedPlaybackQueue(listOf(entry(id)), 0, 1234, false, 0)
    private fun snapshot(queue: SavedPlaybackQueue): PlaybackSnapshot {
        val songs = queue.entries.map { it.toSong() }
        return PlaybackSnapshot(
            queue = songs, current = songs.getOrNull(queue.currentIndex), currentIndex = queue.currentIndex,
            positionMs = queue.positionMs, shuffle = queue.shuffle, repeatMode = queue.repeatMode,
        )
    }

    private fun expectStorage(block: () -> Unit) {
        val exception = runCatching(block).exceptionOrNull()
        assertTrue(exception is ApiFailure)
        assertEquals(Failure.STORAGE, (exception as ApiFailure).failure)
        assertEquals("STORAGE", exception.message)
    }

    private fun await(latch: CountDownLatch) {
        assertTrue("controlled queue IO did not reach its expected boundary", latch.await(5, TimeUnit.SECONDS))
    }

    private fun awaitState(repository: PlaybackQueueRepository, predicate: (SavedPlaybackQueue?) -> Boolean) {
        runBlocking { withTimeout(5000) { repository.state.first(predicate) } }
    }

    private fun withLiveRepository(store: PlaybackQueueStore, block: (PlaybackQueueRepository) -> Unit) {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try { block(PlaybackQueueRepository(store, scope, dispatcher)) }
        finally { scope.cancel(); dispatcher.close() }
    }

    private class MapStorage : HistoryStorage {
        val values = ConcurrentHashMap<String, String>()
        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
    }

    private class FileStorage(private val directory: File) : HistoryStorage {
        override fun read(key: String): String? = File(directory, key).takeIf { it.exists() }?.readText(Charsets.UTF_8)
        override fun write(key: String, value: String) { File(directory, key).writeText(value, Charsets.UTF_8) }
        override fun remove(key: String) {
            val target = File(directory, key)
            if (target.exists() && !target.delete()) throw IOException("remove failed")
        }
    }
}
