package com.flaretune.client

import com.flaretune.client.data.ApiFailure
import com.flaretune.client.data.Failure
import com.flaretune.client.data.HistoryEntry
import com.flaretune.client.data.HistoryIdentity
import com.flaretune.client.data.HistoryStorage
import com.flaretune.client.data.InMemoryPlaybackHistoryStore
import com.flaretune.client.data.JsonPlaybackHistoryStore
import com.flaretune.client.data.MAX_HISTORY_BYTES
import com.flaretune.client.data.MAX_HISTORY_ENTRIES
import com.flaretune.client.data.PlaybackHistoryRepository
import com.flaretune.client.data.PlaybackHistoryStore
import com.flaretune.client.model.ClientNotice
import com.flaretune.client.model.Song
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
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
class PlaybackHistoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val a = HistoryIdentity("https://music.example", "account-A")
    private val b = HistoryIdentity("https://music.example", "account-B")

    @Test fun diskBackendSurvivesStoreReconstructionAndIdentityKeysAreIsolated() {
        val directory = temporary.newFolder("history")
        val first = JsonPlaybackHistoryStore(FileStorage(directory))
        val otherOrigin = HistoryIdentity("https://elsewhere.example", a.accountId)
        first.write(HistoryIdentity("MUSIC.EXAMPLE:443/", a.accountId), listOf(entry("a", 1)))
        first.write(b, listOf(entry("b", 2)))
        first.write(otherOrigin, listOf(entry("elsewhere", 3)))
        val rebuilt = JsonPlaybackHistoryStore(FileStorage(directory))
        assertEquals(listOf(entry("a", 1)), rebuilt.read(a))
        assertEquals(listOf(entry("b", 2)), rebuilt.read(b))
        assertEquals(listOf(entry("elsewhere", 3)), rebuilt.read(otherOrigin))
        assertEquals(3, directory.listFiles()!!.size)
        directory.listFiles()!!.forEach {
            assertTrue(it.name.matches(Regex("history_v1_[a-f0-9]{64}")))
            assertFalse(it.name.contains(a.origin))
            assertFalse(it.name.contains(a.accountId))
        }
        rebuilt.clear(a)
        assertTrue(JsonPlaybackHistoryStore(FileStorage(directory)).read(a).isEmpty())
        assertEquals(1, rebuilt.read(b).size)
        assertEquals(1, rebuilt.read(otherOrigin).size)
    }

    @Test fun onlyMetadataPersistsAndRehydrationRequiresFreshMediaResolution() = runTest {
        val backend = MapStorage()
        val repository = PlaybackHistoryRepository(JsonPlaybackHistoryStore(backend), backgroundScope,
            StandardTestDispatcher(testScheduler))
        repository.bind(a)
        runCurrent()
        repository.record(song("safe").copy(
            audioUrl = "https://private.example/audio?cookie=__Host-ft_session_SECRET",
            coverUrl = "https://private.example/cover?csrfToken=CSRF_SECRET&resultRef=RESULT_SECRET",
            language = "zh",
        ), 123)
        runCurrent()
        val encoded = backend.values.values.single()
        for (forbidden in listOf("audioUrl", "coverUrl", "cookie", "csrfToken", "resultRef",
            "__Host-ft_session_SECRET", "CSRF_SECRET", "RESULT_SECRET", "https://")) {
            assertFalse(forbidden, encoded.contains(forbidden))
        }
        val persisted = JsonPlaybackHistoryStore(backend).read(a).single()
        assertEquals("zh", persisted.language)
        val restored = persisted.toSong()
        assertEquals("", restored.audioUrl)
        assertNull(restored.coverUrl)
        assertEquals("safe", restored.id)
        assertEquals(123, persisted.playedAt)
        assertEquals(setOf("id", "title", "artist", "album", "durationSeconds", "language", "playedAt"),
            JSONObject(encoded).getJSONArray("entries").getJSONObject(0).keys().asSequence().toSet())
    }

    @Test fun historiesAreNewestFirstUniqueAndCappedAtTwoHundred() = runTest {
        val backend = MapStorage()
        val store = JsonPlaybackHistoryStore(backend)
        store.write(a, (0..250).map { entry("song-$it", it.toLong()) } + entry("song-10", 1000))
        val loaded = store.read(a)
        assertEquals(MAX_HISTORY_ENTRIES, loaded.size)
        assertEquals("song-10", loaded.first().id)
        assertEquals(200, loaded.map { it.id }.toSet().size)
        assertTrue(loaded.zipWithNext().all { (newer, older) -> newer.playedAt >= older.playedAt })
        val repository = PlaybackHistoryRepository(store, backgroundScope, StandardTestDispatcher(testScheduler))
        repository.bind(a)
        runCurrent()
        repository.record(song("song-250"), 2000)
        repository.record(song("song-250"), 2001)
        runCurrent()
        assertEquals(MAX_HISTORY_ENTRIES, repository.state.value.size)
        assertEquals(1, repository.state.value.count { it.id == "song-250" })
        assertEquals(2001, repository.state.value.first().playedAt)
        assertEquals(repository.state.value, JsonPlaybackHistoryStore(backend).read(a))
    }

    @Test fun corruptUnknownOrOversizedJsonFailsAsStorageWithoutLeakingIntoState() = runTest {
        val backend = MapStorage()
        val store = JsonPlaybackHistoryStore(backend)
        store.write(a, listOf(entry("valid", 1)))
        val key = backend.values.keys.single()
        val valid = backend.values.getValue(key)
        val badDocuments = listOf(
            "{", "[]", """{"version":2,"entries":[]}""",
            """{"version":true,"entries":[]}""",
            """{"version":1,"entries":{}}""",
            """{"version":1,"entries":[],"cookie":"forbidden"}""",
            JSONObject(valid).apply { getJSONArray("entries").getJSONObject(0).put("playedAt", "1") }.toString(),
            JSONObject(valid).apply { getJSONArray("entries").getJSONObject(0).put("playedAt", 1.5) }.toString(),
            JSONObject(valid).apply { getJSONArray("entries").getJSONObject(0).put("durationSeconds", "1") }.toString(),
            JSONObject(valid).apply { getJSONArray("entries").getJSONObject(0).put("title", 1) }.toString(),
            JSONObject(valid).apply { getJSONArray("entries").getJSONObject(0).put("language", true) }.toString(),
            JSONObject(valid).apply { getJSONArray("entries").getJSONObject(0).put("audioUrl", "secret") }.toString(),
            JSONObject(valid).apply { getJSONArray("entries").getJSONObject(0).put("id", "") }.toString(),
            JSONObject().put("version", 1).put("entries", JSONArray().apply {
                repeat(201) { put(JSONObject(valid).getJSONArray("entries").getJSONObject(0)) }
            }).toString(),
            " ".repeat(MAX_HISTORY_BYTES + 1),
        )
        badDocuments.forEach {
            backend.values[key] = it
            expectStorage { store.read(a) }
        }
        val repository = PlaybackHistoryRepository(store, backgroundScope, StandardTestDispatcher(testScheduler))
        repository.bind(a)
        runCurrent()
        assertTrue(repository.state.value.isEmpty())
        assertEquals(ClientNotice.STORAGE_FAILED, repository.notice.value)
        backend.values[key] = valid
        expectStorage { store.write(a, listOf(entry("nan", 1).copy(durationSeconds = Double.NaN))) }
        expectStorage { store.write(a, listOf(entry("negative", -1))) }
        expectStorage { store.write(a, (0 until 200).map { entry("$it", it.toLong()).copy(title = "界".repeat(2000)) }) }
        assertEquals(valid, backend.values.getValue(key))
    }

    @Test fun backendReadWriteAndRemoveFailuresHaveOneSafeExceptionType() {
        val backend = object : HistoryStorage {
            override fun read(key: String): String? = throw IOException("private backend detail")
            override fun write(key: String, value: String): Unit = throw IOException("private backend detail")
            override fun remove(key: String): Unit = throw IOException("private backend detail")
        }
        val store = JsonPlaybackHistoryStore(backend)
        expectStorage { store.read(a) }
        expectStorage { store.write(a, listOf(entry("song", 1))) }
        expectStorage { store.clear(a) }
    }

    @Test fun sameIdentityPreservesStateButRapidIdentityChangesAlwaysRetireOldData() = runTest {
        val store = InMemoryPlaybackHistoryStore()
        store.write(a, listOf(entry("old-a", 1)))
        store.write(b, listOf(entry("old-b", 1)))
        val repository = PlaybackHistoryRepository(store, backgroundScope, StandardTestDispatcher(testScheduler))
        repository.bind(a)
        runCurrent()
        repository.bind(HistoryIdentity("MUSIC.EXAMPLE/", a.accountId))
        assertEquals("old-a", repository.state.value.single().id)
        repository.bind(b)
        assertTrue(repository.state.value.isEmpty())
        repository.bind(a)
        repository.record(song("new-a"), 3)
        runCurrent()
        assertEquals(listOf("new-a"), repository.state.value.map { it.id })
        assertEquals(listOf("new-a"), store.read(a).map { it.id })
        assertTrue(store.read(b).isEmpty())
    }

    @Test fun failedWriteKeepsCommittedStateAndExposesStorageNotice() = runTest {
        val delegate = InMemoryPlaybackHistoryStore()
        delegate.write(a, listOf(entry("committed", 1)))
        var failWrite = true
        val store = object : PlaybackHistoryStore by delegate {
            override fun write(identity: HistoryIdentity, entries: List<HistoryEntry>) {
                if (failWrite) throw ApiFailure(Failure.STORAGE)
                delegate.write(identity, entries)
            }
        }
        val repository = PlaybackHistoryRepository(store, backgroundScope, StandardTestDispatcher(testScheduler))
        repository.bind(a)
        runCurrent()
        repository.record(song("not-persisted"), 2)
        runCurrent()
        assertEquals(listOf("committed"), repository.state.value.map { it.id })
        assertEquals(ClientNotice.STORAGE_FAILED, repository.notice.value)
        assertEquals(listOf("committed"), delegate.read(a).map { it.id })
        failWrite = false
        repository.record(song("persisted"), 3)
        runCurrent()
        assertEquals(listOf("persisted", "committed"), repository.state.value.map { it.id })
        assertNull(repository.notice.value)
    }

    @Test fun failedRetirementQuarantinesIdentityUntilClearRetrySucceeds() = runTest {
        val delegate = InMemoryPlaybackHistoryStore()
        delegate.write(a, listOf(entry("old-a", 1)))
        var failClear = true
        val store = object : PlaybackHistoryStore by delegate {
            override fun clear(identity: HistoryIdentity) {
                if (identity == a && failClear) throw ApiFailure(Failure.STORAGE)
                delegate.clear(identity)
            }
        }
        val repository = PlaybackHistoryRepository(store, backgroundScope, StandardTestDispatcher(testScheduler))
        repository.bind(a)
        runCurrent()
        repository.bind(b)
        runCurrent()
        repository.bind(a)
        runCurrent()
        repository.record(song("blocked"), 2)
        runCurrent()
        assertTrue(repository.state.value.isEmpty())
        assertEquals(ClientNotice.STORAGE_FAILED, repository.notice.value)
        assertEquals("old-a", delegate.read(a).single().id)
        failClear = false
        repository.record(song("new-a"), 3)
        runCurrent()
        assertEquals(listOf("new-a"), repository.state.value.map { it.id })
        assertEquals(listOf("new-a"), delegate.read(a).map { it.id })
        assertNull(repository.notice.value)
    }

    @Test fun lateLoadAfterBindCannotPublishOldAccountOrLeaveOldRecords() {
        val delegate = InMemoryPlaybackHistoryStore()
        delegate.write(a, listOf(entry("old-a", 1)))
        delegate.write(b, listOf(entry("only-b", 2)))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val store = object : PlaybackHistoryStore by delegate {
            override fun read(identity: HistoryIdentity): List<HistoryEntry> {
                val snapshot = delegate.read(identity)
                if (identity == a) { entered.countDown(); await(release) }
                return snapshot
            }
        }
        withLiveRepository(store) { repository ->
            try {
                repository.bind(a)
                await(entered)
                repository.bind(b)
                assertTrue(repository.state.value.isEmpty())
                release.countDown()
                awaitState(repository) { it.singleOrNull()?.id == "only-b" }
                assertTrue(delegate.read(a).isEmpty())
            } finally { release.countDown() }
        }
    }

    @Test fun lateLoadAfterClearCannotReappearAndRecordingRequiresRebind() {
        val delegate = InMemoryPlaybackHistoryStore()
        delegate.write(a, listOf(entry("old-a", 1)))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cleared = CountDownLatch(1)
        val store = object : PlaybackHistoryStore by delegate {
            override fun read(identity: HistoryIdentity): List<HistoryEntry> {
                val snapshot = delegate.read(identity)
                entered.countDown()
                await(release)
                return snapshot
            }
            override fun clear(identity: HistoryIdentity) { delegate.clear(identity); cleared.countDown() }
        }
        withLiveRepository(store) { repository ->
            try {
                repository.bind(a)
                await(entered)
                repository.clear()
                repository.record(song("retired-callback"), 2)
                assertTrue(repository.state.value.isEmpty())
                release.countDown()
                await(cleared)
                assertTrue(repository.state.value.isEmpty())
                assertTrue(delegate.read(a).isEmpty())
            } finally { release.countDown() }
        }
    }

    @Test fun lateWriteAfterAccountSwitchIsFinallyErasedBeforeNewAccountLoads() {
        val delegate = InMemoryPlaybackHistoryStore()
        delegate.write(a, listOf(entry("seed-a", 1)))
        delegate.write(b, listOf(entry("only-b", 2)))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val store = object : PlaybackHistoryStore by delegate {
            override fun write(identity: HistoryIdentity, entries: List<HistoryEntry>) {
                if (identity == a) { entered.countDown(); await(release) }
                delegate.write(identity, entries)
            }
        }
        withLiveRepository(store) { repository ->
            try {
                repository.bind(a)
                awaitState(repository) { it.singleOrNull()?.id == "seed-a" }
                repository.record(song("late-a"), 3)
                await(entered)
                repository.bind(b)
                assertTrue(repository.state.value.isEmpty())
                release.countDown()
                awaitState(repository) { it.singleOrNull()?.id == "only-b" }
                assertTrue(delegate.read(a).isEmpty())
                assertEquals(listOf("only-b"), delegate.read(b).map { it.id })
            } finally { release.countDown() }
        }
    }

    @Test fun clearAndRebindWaitBehindLateWriteAndNeverReloadRetiredHistory() {
        val delegate = InMemoryPlaybackHistoryStore()
        delegate.write(a, listOf(entry("seed-a", 1)))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val reloaded = CountDownLatch(1)
        var reads = 0
        val store = object : PlaybackHistoryStore by delegate {
            override fun read(identity: HistoryIdentity): List<HistoryEntry> {
                val snapshot = delegate.read(identity)
                reads++
                if (reads == 2) reloaded.countDown()
                return snapshot
            }
            override fun write(identity: HistoryIdentity, entries: List<HistoryEntry>) {
                if (entries.any { it.id == "late-a" }) { entered.countDown(); await(release) }
                delegate.write(identity, entries)
            }
        }
        withLiveRepository(store) { repository ->
            try {
                repository.bind(a)
                awaitState(repository) { it.singleOrNull()?.id == "seed-a" }
                repository.record(song("late-a"), 2)
                await(entered)
                repository.clear()
                repository.record(song("ignored-after-clear"), 3)
                repository.bind(a)
                repository.record(song("new-session"), 4)
                assertTrue(repository.state.value.isEmpty())
                release.countDown()
                await(reloaded)
                awaitState(repository) { it.singleOrNull()?.id == "new-session" }
                assertEquals(listOf("new-session"), delegate.read(a).map { it.id })
            } finally { release.countDown() }
        }
    }


    @Test fun userRemoveAndClearRemainDurableAndKeepIdentityBoundForFuturePlayback() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val applicationScope = CoroutineScope(SupervisorJob() + dispatcher)
        val store = InMemoryPlaybackHistoryStore()
        val repository = PlaybackHistoryRepository(store, applicationScope, dispatcher)
        try {
            repository.bind(a)
            repository.record(Song("one", "One", audioUrl = "private"), 10)
            repository.record(Song("two", "Two", audioUrl = "private"), 20)
            runCurrent()
            val removed = async { repository.remove("one") }
            runCurrent()
            assertTrue(removed.await())
            assertEquals(listOf("two"), repository.state.value.map { it.id })
            assertEquals(listOf("two"), store.read(a).map { it.id })
            val cleared = async { repository.clearEntries() }
            runCurrent()
            assertTrue(cleared.await())
            assertTrue(store.read(a).isEmpty())
            repository.record(Song("three", "Three", audioUrl = "private"), 30)
            runCurrent()
            assertEquals(listOf("three"), store.read(a).map { it.id })
        } finally { applicationScope.cancel() }
    }

    @Test fun failedUserHistoryEditKeepsLastDurableStateAndReportsFailure() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val applicationScope = CoroutineScope(SupervisorJob() + dispatcher)
        val delegate = InMemoryPlaybackHistoryStore()
        delegate.write(a, listOf(entry("kept", 1)))
        val store = object : PlaybackHistoryStore by delegate {
            override fun clear(identity: HistoryIdentity) { throw ApiFailure(Failure.STORAGE) }
        }
        val repository = PlaybackHistoryRepository(store, applicationScope, dispatcher)
        try {
            repository.bind(a); runCurrent()
            val result = async { repository.clearEntries() }
            runCurrent()
            assertFalse(result.await())
            assertEquals(listOf("kept"), repository.state.value.map { it.id })
            assertEquals(ClientNotice.STORAGE_FAILED, repository.notice.value)
            assertEquals(listOf("kept"), delegate.read(a).map { it.id })
        } finally { applicationScope.cancel() }
    }

    private fun entry(id: String, time: Long) = HistoryEntry(id, "Title $id", "Artist", "Album", 180.0, playedAt = time)
    private fun song(id: String) = Song(id, "Title $id", "Artist", "Album", 180.0, "https://audio.example/$id")

    private fun expectStorage(block: () -> Unit) {
        val exception = runCatching(block).exceptionOrNull()
        assertTrue(exception is ApiFailure)
        assertEquals(Failure.STORAGE, (exception as ApiFailure).failure)
        assertEquals("STORAGE", exception.message)
    }

    private fun await(latch: CountDownLatch) {
        assertTrue("controlled IO did not reach its expected boundary", latch.await(5, TimeUnit.SECONDS))
    }

    private fun awaitState(repository: PlaybackHistoryRepository, predicate: (List<HistoryEntry>) -> Boolean) {
        runBlocking { withTimeout(5000) { repository.state.first(predicate) } }
    }

    private fun withLiveRepository(store: PlaybackHistoryStore, block: (PlaybackHistoryRepository) -> Unit) {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try { block(PlaybackHistoryRepository(store, scope, dispatcher)) }
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
