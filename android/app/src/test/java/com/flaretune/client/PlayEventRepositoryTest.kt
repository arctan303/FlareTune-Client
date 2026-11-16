package com.flaretune.client

import com.flaretune.client.data.*
import com.flaretune.client.model.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayEventRepositoryTest {
    private val a = HistoryIdentity("https://music.example", "account-A")
    private val b = HistoryIdentity("https://music.example", "account-B")
    private fun song(id: String = "song") = Song(id, "Song", audioUrl = "https://private.invalid/audio?token=SECRET_URL", coverUrl = "https://private.invalid/cover?csrf=SECRET_CSRF")

    @Test fun failuresRetryTheSameDurableEventIdAndOnlyServerAcknowledgementRemovesIt() = runTest {
        val backend = Memory(); val store = JsonPlayEventStore(backend)
        val api = Api()
        api.reply = { events -> if (api.calls.size == 1) throw ApiFailure(Failure.NETWORK) else PlayEventReceipt(0, events.map { it.eventId }) }
        val connection = connection("account-A", api)
        val repository = PlayEventRepository(store, backgroundScope, StandardTestDispatcher(testScheduler), { testScheduler.currentTime + 1000 }, { "stable_event" })
        try {
            repository.bind(a, connection) { true }; runCurrent()
            repository.record(song()); runCurrent()
            assertEquals("stable_event", store.read(a).single().eventId)
            assertFalse(backend.values.values.joinToString().contains("SECRET"))
            repository.flush(); runCurrent()
            assertEquals(1, repository.pending.value.size)
            assertEquals(ClientNotice.NETWORK, repository.notice.value)
            advanceTimeBy(30_000); runCurrent()
            assertEquals(listOf("stable_event", "stable_event"), api.calls.map { it.single().eventId })
            assertTrue(repository.pending.value.isEmpty())
            assertTrue(store.read(a).isEmpty())
        } finally { repository.clear(); connection.network.retire() }
    }

    @Test fun batchesNeverExceedTwentyFiveAndPartialForeignAckCannotStarveOrDeleteLaterEvents() = runTest {
        val store = JsonPlayEventStore(Memory())
        val initial = (0 until 26).map { PlayEvent("e_$it", "song-$it", 1000L + it) }
        store.write(a, initial)
        val api = Api()
        api.reply = { events -> if (api.calls.size == 1) PlayEventReceipt(1, listOf(events.first().eventId, "e_25")) else PlayEventReceipt(0, emptyList()) }
        val connection = connection("account-A", api)
        val repository = PlayEventRepository(store, backgroundScope, StandardTestDispatcher(testScheduler), { 2000 }, { "unused" })
        try {
            repository.bind(a, connection) { true }; runCurrent()
            assertEquals(listOf(25, 1), api.calls.map { it.size })
            assertEquals("e_25", api.calls.last().single().eventId)
            assertEquals(25, repository.pending.value.size)
            assertTrue(repository.pending.value.any { it.eventId == "e_25" })
            assertFalse(repository.pending.value.any { it.eventId == "e_0" })
        } finally { repository.clear(); connection.network.retire() }
    }

    @Test fun retirementPreservesOwnerPendingForLaterLoginAndNeverLoadsItIntoAnotherAccount() = runTest {
        val backend = Memory(); val store = JsonPlayEventStore(backend)
        val api = Api(); val ca = connection("account-A", api); val cb = connection("account-B", Api())
        val repository = PlayEventRepository(store, backgroundScope, StandardTestDispatcher(testScheduler), { 1000 }, { "owned_event" })
        try {
            repository.bind(a, ca) { true }; runCurrent(); repository.record(song()); runCurrent()
            repository.clear(); runCurrent()
            assertTrue(repository.pending.value.isEmpty()); assertEquals(1, store.read(a).size)
            repository.bind(b, cb) { true }; runCurrent()
            assertTrue(repository.pending.value.isEmpty())
            repository.bind(a, ca) { true }; runCurrent()
            assertEquals("owned_event", repository.pending.value.single().eventId)
            assertTrue(backend.values.keys.all { it.matches(Regex("play_events_v1_[a-f0-9]{64}")) })
            assertEquals(0, api.calls.size)
        } finally { repository.clear(); ca.network.retire(); cb.network.retire() }
    }

    @Test fun unreadableDiskIsNotOverwrittenAndNoBatchIsSentUntilOwnerStateCanBeRead() = runTest {
        val backend = Memory()
        val key = listeningStorageKey("play_events_v1", a)
        backend.values[key] = "corrupt_PRIVATE_COOKIE_CANARY"
        val api = Api(); val connection = connection("account-A", api)
        val repository = PlayEventRepository(JsonPlayEventStore(backend), backgroundScope, StandardTestDispatcher(testScheduler), { 1000 }, { "pending_event" })
        try {
            repository.bind(a, connection) { true }; runCurrent()
            repository.record(song()); runCurrent(); repository.flush(); runCurrent()
            assertEquals(ClientNotice.STORAGE_FAILED, repository.notice.value)
            assertEquals("corrupt_PRIVATE_COOKIE_CANARY", backend.values[key])
            assertTrue(api.calls.isEmpty())
            backend.values.remove(key)
            repository.flush(); runCurrent()
            assertEquals("pending_event", api.calls.single().single().eventId)
            assertTrue(repository.pending.value.isEmpty())
        } finally { repository.clear(); connection.network.retire() }
    }

    @Test fun delayedOldNetworkAckCannotMutateTheNextAccountOrEraseOldUnconfirmedDisk() = runBlocking {
        val store = JsonPlayEventStore(Memory())
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val completed = CountDownLatch(1)
        val apiA = Api().apply { reply = { events ->
            entered.countDown()
            try { check(release.await(5, TimeUnit.SECONDS)); PlayEventReceipt(events.size, events.map { it.eventId }) }
            finally { completed.countDown() }
        } }
        val ca = connection("account-A", apiA); val cb = connection("account-B", Api())
        val active = AtomicReference(ca)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = PlayEventRepository(store, scope, Dispatchers.IO)
        try {
            repository.bind(a, ca) { active.get() === ca }; repository.record(song("a-song"))
            withTimeout(5000) { repository.pending.first { it.any { event -> event.songId == "a-song" } } }
            repository.flush()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            active.set(cb); repository.bind(b, cb) { active.get() === cb }; repository.record(song("b-song"))
            withTimeout(5000) { repository.pending.first { it.size == 1 && it.single().songId == "b-song" } }
            release.countDown(); assertTrue(completed.await(5, TimeUnit.SECONDS))
            withTimeout(5000) { while (store.read(b).none { it.songId == "b-song" }) delay(10) }
            delay(50)
            assertEquals("b-song", repository.pending.value.single().songId)
            assertEquals("a-song", store.read(a).single().songId)
        } finally { release.countDown(); repository.clear(); scope.cancel(); ca.network.retire(); cb.network.retire() }
    }

    @Test fun eventCodecRejectsUnknownFieldsAndInvalidIdsWithoutReplacingExistingBytes() {
        val backend = Memory(); val store = JsonPlayEventStore(backend)
        store.write(a, listOf(PlayEvent("event", "song", 1000)))
        val key = backend.values.keys.single(); val valid = backend.values.getValue(key)
        backend.values[key] = JSONObject(valid).put("cookie", "PRIVATE_CANARY").toString()
        assertEquals(Failure.STORAGE, assertThrows(ApiFailure::class.java) { store.read(a) }.failure)
        backend.values[key] = valid
        assertEquals(Failure.STORAGE, assertThrows(ApiFailure::class.java) { store.write(a, listOf(PlayEvent("bad!", "song", 1000))) }.failure)
        assertEquals(valid, backend.values[key])
    }

    private class Memory : HistoryStorage {
        val values = ConcurrentHashMap<String, String>()
        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
    }
    private class Api : AccountApi {
        val calls = CopyOnWriteArrayList<List<PlayEvent>>()
        var reply: (List<PlayEvent>) -> PlayEventReceipt = { PlayEventReceipt(it.size, it.map { event -> event.eventId }) }
        override fun submitPlayEvents(events: List<PlayEvent>): PlayEventReceipt { calls += events.toList(); return reply(events) }
        override fun songs(query: String, offset: Int) = SongPage(emptyList(), offset, false)
        override fun favorites() = emptyList<Song>()
        override fun changePassword(current: String, replacement: String) {}
        override fun logout() {}
    }
    private fun connection(id: String, api: AccountApi): ActiveConnection = ActiveConnection(
        SessionNetwork(InstanceAddress.parse("https://music.example")), api,
        AuthSession(Account(id, "listener", "Listener"), "csrf-fixture", false),
    )
}
