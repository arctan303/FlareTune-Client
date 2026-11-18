package com.arcinks.flaretune.data

import com.arcinks.flaretune.model.ClientNotice
import com.arcinks.flaretune.model.PlayEvent
import com.arcinks.flaretune.model.PlayEventReceipt
import com.arcinks.flaretune.model.Song
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

interface PlayEventStore {
    fun read(identity: HistoryIdentity): List<PlayEvent>
    fun write(identity: HistoryIdentity, events: List<PlayEvent>)
}

/** Event-only owner-keyed persistence, with no metadata URLs, cookies or result references. */
class JsonPlayEventStore(private val storage: HistoryStorage) : PlayEventStore {
    override fun read(identity: HistoryIdentity): List<PlayEvent> = localStateOperation {
        val raw = storage.read(listeningStorageKey("play_events_v1", identity)) ?: return@localStateOperation emptyList()
        if (raw.toByteArray(Charsets.UTF_8).size > 2 * 1024 * 1024) localStateFailure()
        val data = JSONObject(raw)
        exactKeys(data, setOf("version", "events"))
        if (data.opt("version") != 1) localStateFailure()
        val rows = data.optJSONArray("events") ?: localStateFailure()
        if (rows.length() > 10_000) localStateFailure()
        val events = (0 until rows.length()).map { index ->
            val row = rows.optJSONObject(index) ?: localStateFailure()
            exactKeys(row, setOf("event_id", "song_id", "played_at"))
            validPlayEvent(PlayEvent(localString(row, "event_id"), localString(row, "song_id"), localLong(row, "played_at")))
        }
        if (events.map { it.eventId }.distinct().size != events.size) localStateFailure()
        events.sortedWith(compareBy<PlayEvent> { it.playedAt }.thenBy { it.eventId })
    }
    override fun write(identity: HistoryIdentity, events: List<PlayEvent>) = localStateOperation {
        if (events.size > 10_000 || events.map { it.eventId }.distinct().size != events.size) localStateFailure()
        val key = listeningStorageKey("play_events_v1", identity)
        if (events.isEmpty()) { storage.remove(key); return@localStateOperation }
        val rows = JSONArray(events.map { validPlayEvent(it); JSONObject().put("event_id", it.eventId).put("song_id", it.songId).put("played_at", it.playedAt) })
        val raw = JSONObject().put("version", 1).put("events", rows).toString()
        if (raw.toByteArray(Charsets.UTF_8).size > 2 * 1024 * 1024) localStateFailure()
        storage.write(key, raw)
    }
}

private fun validPlayEvent(event: PlayEvent): PlayEvent = event.also {
    if (!it.eventId.matches(Regex("[A-Za-z0-9_-]{1,128}")) || it.songId.isBlank() || it.songId.length > 256 ||
        it.songId.any { c -> c.code < 32 || c.code == 127 } || it.playedAt !in 1..9_007_199_254_740_991L) localStateFailure()
}

/** Serial disk actor plus separate network jobs: a slow upload never delays recording the next play. */
class PlayEventRepository(
    private val store: PlayEventStore,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
    private val createId: () -> String = { "play_" + UUID.randomUUID().toString().replace('-', '_') },
) {
    private val lock = Any()
    private var generation = 0L
    private data class Binding(val generation: Long, val identity: HistoryIdentity, val connection: ActiveConnection,
        val isCurrent: () -> Boolean, var flushing: Boolean = false, var failures: Int = 0)
    private var binding: Binding? = null
    private val queues = mutableMapOf<HistoryIdentity, LinkedHashMap<String, PlayEvent>>()
    private val loaded = mutableSetOf<HistoryIdentity>()
    private var timer: Job? = null
    private var uploadJob: Job? = null
    private val mutablePending = MutableStateFlow<List<PlayEvent>>(emptyList())
    private val mutableNotice = MutableStateFlow<ClientNotice?>(null)
    private val mutableUploading = MutableStateFlow(false)
    private val mutableAcknowledgedAt = MutableStateFlow(0L)
    val pending: StateFlow<List<PlayEvent>> = mutablePending.asStateFlow()
    val notice: StateFlow<ClientNotice?> = mutableNotice.asStateFlow()
    val uploading: StateFlow<Boolean> = mutableUploading.asStateFlow()
    val lastAcknowledgedAt: StateFlow<Long> = mutableAcknowledgedAt.asStateFlow()
    private sealed interface Command {
        data class Load(val binding: Binding) : Command
        data class Persist(val identity: HistoryIdentity) : Command
        data class Flush(val binding: Binding, val attempted: Set<String> = emptySet()) : Command
        data class Ack(val binding: Binding, val submitted: List<PlayEvent>, val receipt: PlayEventReceipt, val attempted: Set<String>) : Command
        data class Failed(val binding: Binding, val error: Exception) : Command
    }
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val worker = scope.launch(ioDispatcher) {
        for (command in commands) when (command) {
            is Command.Load -> if (current(command.binding) && ensureLoaded(command.binding.identity)) {
                publish(command.binding.identity)
                schedule(command.binding)
            }
            is Command.Persist -> {
                persist(command.identity)
                synchronized(lock) { binding?.takeIf { it.identity == command.identity } }?.let(::schedule)
            }
            is Command.Flush -> startUpload(command.binding, command.attempted)
            is Command.Ack -> acknowledge(command)
            is Command.Failed -> failed(command)
        }
    }

    fun bind(identity: HistoryIdentity, connection: ActiveConnection, isCurrent: () -> Boolean) {
        val owner = identity.normalized()
        if (owner != HistoryIdentity(connection.network.address.origin, connection.auth.account.id).normalized()) throw ApiFailure(Failure.REQUEST)
        synchronized(lock) {
            if (binding?.connection?.network === connection.network && binding?.identity == owner) return
            generation++
            timer?.cancel(); uploadJob?.cancel()
            val next = Binding(generation, owner, connection, isCurrent)
            binding = next
            mutablePending.value = emptyList(); mutableNotice.value = null; mutableUploading.value = false; mutableAcknowledgedAt.value = 0
            send(Command.Load(next))
        }
    }

    fun record(song: Song) {
        synchronized(lock) {
            val owner = binding?.takeIf(::current) ?: return
            val queue = queues.getOrPut(owner.identity) { linkedMapOf() }
            if (queue.size >= 10_000) { mutableNotice.value = ClientNotice.RATE_LIMITED; return }
            val event = try { validPlayEvent(PlayEvent(createId(), song.id, now())) }
                catch (_: ApiFailure) { mutableNotice.value = ClientNotice.STORAGE_FAILED; return }
            queue[event.eventId] = event
            mutablePending.value = sorted(owner.identity)
            send(Command.Persist(owner.identity))
        }
    }

    fun flush() { synchronized(lock) { binding?.takeIf(::current)?.let { send(Command.Flush(it)) } } }

    /** Retires network authority and UI immediately; unacknowledged owner-keyed events remain retryable. */
    fun clear() {
        synchronized(lock) {
            generation++
            binding = null
            timer?.cancel(); timer = null
            uploadJob?.cancel(); uploadJob = null
            mutablePending.value = emptyList(); mutableNotice.value = null; mutableUploading.value = false; mutableAcknowledgedAt.value = 0
        }
    }

    private fun send(command: Command) { if (!worker.isActive || commands.trySend(command).isFailure) mutableNotice.value = ClientNotice.STORAGE_FAILED }
    private fun current(value: Binding): Boolean = synchronized(lock) {
        binding === value && value.generation == generation && value.connection.network.isActive &&
            !value.connection.auth.mustChangePassword && try { value.isCurrent() } catch (_: Exception) { false }
    }
    private fun sorted(identity: HistoryIdentity): List<PlayEvent> = queues[identity]?.values.orEmpty()
        .sortedWith(compareBy<PlayEvent> { it.playedAt }.thenBy { it.eventId })
    private fun publish(identity: HistoryIdentity, error: ClientNotice? = null) {
        synchronized(lock) { binding?.takeIf { it.identity == identity && current(it) }?.let {
            mutablePending.value = sorted(identity)
            mutableNotice.value = error
        } }
    }
    private fun ensureLoaded(identity: HistoryIdentity): Boolean {
        if (identity in loaded) return true
        return try {
            val persisted = store.read(identity)
            synchronized(lock) {
                val queue = queues.getOrPut(identity) { linkedMapOf() }
                persisted.forEach { event -> if (!queue.containsKey(event.eventId)) queue[event.eventId] = event }
                loaded += identity
            }
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            publish(identity, ClientNotice.STORAGE_FAILED)
            false
        }
    }
    private fun persist(identity: HistoryIdentity): Boolean {
        if (!ensureLoaded(identity)) return false
        return try {
            val snapshot = synchronized(lock) { sorted(identity) }
            store.write(identity, snapshot)
            publish(identity)
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            publish(identity, ClientNotice.STORAGE_FAILED)
            false
        }
    }
    private fun schedule(value: Binding, retryMs: Long? = null) {
        if (!current(value)) return
        val snapshot = synchronized(lock) { sorted(value.identity) }
        if (snapshot.isEmpty()) { timer?.cancel(); timer = null; return }
        if (value.flushing) return
        timer?.cancel()
        val wait = retryMs ?: if (snapshot.size >= 10) 0L else (600_000 - (now() - snapshot.first().playedAt)).coerceIn(0, 600_000)
        timer = scope.launch { delay(wait); if (current(value)) send(Command.Flush(value)) }
    }
    private fun startUpload(value: Binding, attempted: Set<String>) {
        if (!current(value) || value.flushing || !ensureLoaded(value.identity)) return
        val batch = synchronized(lock) { sorted(value.identity).filterNot { it.eventId in attempted }.take(25) }
        if (batch.isEmpty()) { schedule(value, 30_000); return }
        synchronized(lock) {
            if (!current(value)) return
            value.flushing = true
            mutableUploading.value = true
        }
        val tried = attempted + batch.map { it.eventId }
        uploadJob = scope.launch(ioDispatcher) {
            try {
                if (!current(value)) return@launch
                val receipt = value.connection.api.submitPlayEvents(batch)
                send(Command.Ack(value, batch, receipt, tried))
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                send(Command.Failed(value, error))
            }
        }
    }
    private fun acknowledge(command: Command.Ack) {
        val value = command.binding
        if (!current(value)) return
        val submitted = command.submitted.map { it.eventId }.toSet()
        val accepted = command.receipt.acceptedEventIds.filter { it in submitted }.toSet()
        synchronized(lock) {
            if (!current(value)) return
            accepted.forEach { queues[value.identity]?.remove(it) }
            value.flushing = false; value.failures = 0
            mutableUploading.value = false
            if (accepted.isNotEmpty()) mutableAcknowledgedAt.value = now()
        }
        persist(value.identity)
        val more = synchronized(lock) { sorted(value.identity).any { it.eventId !in command.attempted } }
        if (more) send(Command.Flush(value, command.attempted)) else schedule(value, 30_000)
    }
    private fun failed(command: Command.Failed) {
        val value = command.binding
        if (!current(value)) return
        val notice = when ((command.error as? ApiFailure)?.failure) {
            Failure.RATE_LIMITED -> ClientNotice.RATE_LIMITED
            Failure.SESSION_EXPIRED, Failure.RETIRED -> ClientNotice.SESSION_EXPIRED
            Failure.FORBIDDEN -> ClientNotice.FORBIDDEN
            else -> ClientNotice.NETWORK
        }
        synchronized(lock) { value.flushing = false; value.failures = (value.failures + 1).coerceAtMost(5); mutableUploading.value = false }
        publish(value.identity, notice)
        schedule(value, (30_000L * (1L shl (value.failures - 1))).coerceAtMost(300_000))
    }
}
