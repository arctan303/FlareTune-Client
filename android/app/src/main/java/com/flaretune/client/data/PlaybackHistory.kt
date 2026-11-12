package com.flaretune.client.data

import android.content.Context
import android.content.SharedPreferences
import com.flaretune.client.model.ClientNotice
import com.flaretune.client.model.Song
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

const val MAX_HISTORY_ENTRIES = 200
const val MAX_HISTORY_BYTES = 512 * 1024

data class HistoryIdentity(val origin: String, val accountId: String) {
    /** All repository comparisons and disk keys use this canonical identity. */
    fun normalized(): HistoryIdentity {
        val canonical = InstanceAddress.parse(origin).origin
        if (!accountId.matches(Regex("[A-Za-z0-9_-]{1,128}"))) throw ApiFailure(Failure.STORAGE)
        return if (canonical == origin) this else copy(origin = canonical)
    }
}

/** Metadata only: transient media URLs and authentication never enter this type. */
data class HistoryEntry(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Double,
    val language: String? = null,
    val playedAt: Long,
) {
    /** The caller must resolve current media through the active account before playing. */
    fun toSong(): Song = Song(
        id = id, title = title, artist = artist, album = album,
        durationSeconds = durationSeconds, language = language, audioUrl = "",
    )
}

interface PlaybackHistoryStore {
    fun read(identity: HistoryIdentity): List<HistoryEntry>
    fun write(identity: HistoryIdentity, entries: List<HistoryEntry>)
    fun clear(identity: HistoryIdentity)
}

/** Implementations must complete writes/removals before returning, or throw. */
interface HistoryStorage {
    fun read(key: String): String?
    fun write(key: String, value: String)
    fun remove(key: String)
}

/** Versioned, size-bounded codec, independently testable without Android preferences. */
class JsonPlaybackHistoryStore(private val storage: HistoryStorage) : PlaybackHistoryStore {
    override fun read(identity: HistoryIdentity): List<HistoryEntry> = storageOperation {
        val raw = storage.read(key(identity)) ?: return@storageOperation emptyList()
        if (raw.length > MAX_HISTORY_BYTES || raw.toByteArray(Charsets.UTF_8).size > MAX_HISTORY_BYTES) failStorage()
        val document = JSONObject(raw)
        if (document.keys().asSequence().toSet() != setOf("version", "entries") ||
            document.opt("version") !is Int || document.opt("version") != 1) failStorage()
        val rows = document.opt("entries") as? JSONArray ?: failStorage()
        if (rows.length() > MAX_HISTORY_ENTRIES) failStorage()
        canonicalHistory((0 until rows.length()).map { index ->
            val row = rows.opt(index) as? JSONObject ?: failStorage()
            val allowed = setOf("id", "title", "artist", "album", "durationSeconds", "language", "playedAt")
            if (row.keys().asSequence().any { it !in allowed }) failStorage()
            val language = when (val value = row.opt("language")) {
                null, JSONObject.NULL -> null
                is String -> value
                else -> failStorage()
            }
            val duration = (row.opt("durationSeconds") as? Number)?.toDouble() ?: failStorage()
            val playedAt = when (val value = row.opt("playedAt")) {
                is Int -> value.toLong()
                is Long -> value
                else -> failStorage()
            }
            HistoryEntry(
                id = row.opt("id") as? String ?: failStorage(),
                title = row.opt("title") as? String ?: failStorage(),
                artist = row.opt("artist") as? String ?: failStorage(),
                album = row.opt("album") as? String ?: failStorage(),
                durationSeconds = duration, language = language, playedAt = playedAt,
            )
        })
    }

    override fun write(identity: HistoryIdentity, entries: List<HistoryEntry>) = storageOperation {
        val rows = JSONArray()
        canonicalHistory(entries).forEach { entry ->
            rows.put(JSONObject()
                .put("id", entry.id).put("title", entry.title)
                .put("artist", entry.artist).put("album", entry.album)
                .put("durationSeconds", entry.durationSeconds)
                .put("language", entry.language ?: JSONObject.NULL)
                .put("playedAt", entry.playedAt))
        }
        val encoded = JSONObject().put("version", 1).put("entries", rows).toString()
        if (encoded.toByteArray(Charsets.UTF_8).size > MAX_HISTORY_BYTES) failStorage()
        storage.write(key(identity), encoded)
    }

    override fun clear(identity: HistoryIdentity) = storageOperation { storage.remove(key(identity)) }

    private fun key(identity: HistoryIdentity): String {
        val normalized = identity.normalized()
        val payload = "FlareTune.History.v1|${normalized.origin.length}:${normalized.origin}|${normalized.accountId.length}:${normalized.accountId}"
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return "history_v1_" + digest.joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}

private class PreferenceHistoryStorage(private val preferences: SharedPreferences) : HistoryStorage {
    override fun read(key: String): String? = preferences.getString(key, null)
    override fun write(key: String, value: String) {
        if (!preferences.edit().putString(key, value).commit()) failStorage()
    }
    override fun remove(key: String) {
        if (!preferences.edit().remove(key).commit()) failStorage()
    }
}

class AndroidPlaybackHistoryStore(context: Context) : PlaybackHistoryStore by JsonPlaybackHistoryStore(
    PreferenceHistoryStorage(storageOperation { context.applicationContext.getSharedPreferences("playback_history_v1", Context.MODE_PRIVATE) }),
)

private class MemoryHistoryStorage : HistoryStorage {
    private val values = mutableMapOf<String, String>()
    override fun read(key: String): String? = synchronized(values) { values[key] }
    override fun write(key: String, value: String) { synchronized(values) { values[key] = value } }
    override fun remove(key: String) { synchronized(values) { values.remove(key) } }
}

/** An isolated default for JVM/ViewModel tests; production injects the Android store. */
class InMemoryPlaybackHistoryStore : PlaybackHistoryStore by JsonPlaybackHistoryStore(MemoryHistoryStorage())

/**
 * Use an application-lived scope, not a ViewModel scope. It must remain alive until
 * retirement IO finishes. One actor orders every load/write/clear; generations fence
 * synchronous state, while mandatory old-identity clears run even for stale commands.
 */
class PlaybackHistoryRepository(
    private val store: PlaybackHistoryStore,
    scope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val lock = Any()
    private var identity: HistoryIdentity? = null
    private var generation = 0L
    private val mutableState = MutableStateFlow<List<HistoryEntry>>(emptyList())
    private val mutableNotice = MutableStateFlow<ClientNotice?>(null)
    val state: StateFlow<List<HistoryEntry>> = mutableState.asStateFlow()
    val notice: StateFlow<ClientNotice?> = mutableNotice.asStateFlow()
    private val commands = Channel<Command>(Channel.UNLIMITED)

    // Actor-owned fields; a failed clear quarantines that identity until retry succeeds.
    private val pendingClears = linkedSetOf<HistoryIdentity>()
    private var loadedGeneration = -1L
    private var loadedEntries: List<HistoryEntry> = emptyList()

    private sealed interface Command {
        val generation: Long
        data class Bind(
            override val generation: Long,
            val identity: HistoryIdentity,
            val previous: HistoryIdentity?,
        ) : Command
        data class Record(
            override val generation: Long,
            val identity: HistoryIdentity,
            val entry: HistoryEntry,
        ) : Command
        data class Clear(
            override val generation: Long,
            val previous: HistoryIdentity?,
        ) : Command
    }

    private val worker = scope.launch(ioDispatcher) {
        for (command in commands) {
            when (command) {
                is Command.Bind -> load(command)
                is Command.Record -> persist(command)
                is Command.Clear -> retire(command)
            }
        }
    }

    fun bind(identity: HistoryIdentity) {
        val normalized = identity.normalized()
        synchronized(lock) {
            if (this.identity == normalized) return
            val previous = this.identity
            this.identity = normalized
            generation++
            mutableState.value = emptyList()
            mutableNotice.value = null
            enqueue(Command.Bind(generation, normalized, previous))
        }
    }

    fun record(song: Song, playedAt: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            val current = identity ?: return
            enqueue(Command.Record(generation, current, HistoryEntry(
                id = song.id, title = song.title, artist = song.artist, album = song.album,
                durationSeconds = song.durationSeconds, language = song.language, playedAt = playedAt,
            )))
        }
    }

    /** Retires the binding immediately. A later bind is required to resume recording. */
    fun clear() {
        synchronized(lock) {
            val previous = identity
            identity = null
            generation++
            mutableState.value = emptyList()
            mutableNotice.value = null
            enqueue(Command.Clear(generation, previous))
        }
    }

    private fun enqueue(command: Command) {
        if (!worker.isActive || commands.trySend(command).isFailure) {
            mutableNotice.value = ClientNotice.STORAGE_FAILED
        }
    }

    private fun current(ticket: Long, expected: HistoryIdentity?): Boolean = synchronized(lock) {
        generation == ticket && identity == expected
    }

    private fun publish(ticket: Long, expected: HistoryIdentity?, entries: List<HistoryEntry>?, failed: Boolean) {
        synchronized(lock) {
            if (generation != ticket || identity != expected) return
            if (entries != null) mutableState.value = entries.toList()
            mutableNotice.value = if (failed) ClientNotice.STORAGE_FAILED else null
        }
    }

    private fun flushClears(): Boolean {
        for (previous in pendingClears.toList()) {
            try {
                store.clear(previous)
                pendingClears.remove(previous)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
            }
        }
        return pendingClears.isEmpty()
    }

    private fun load(command: Command.Bind) {
        command.previous?.let(pendingClears::add)
        val cleared = flushClears()
        if (!current(command.generation, command.identity)) return
        loadedGeneration = command.generation
        loadedEntries = emptyList()
        if (command.identity in pendingClears) {
            publish(command.generation, command.identity, emptyList(), failed = true)
            return
        }
        try {
            val entries = canonicalHistory(store.read(command.identity))
            if (!current(command.generation, command.identity)) return
            loadedEntries = entries
            publish(command.generation, command.identity, entries, failed = !cleared)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            publish(command.generation, command.identity, emptyList(), failed = true)
        }
    }

    private fun persist(command: Command.Record) {
        if (!current(command.generation, command.identity)) return
        val cleared = flushClears()
        if (!current(command.generation, command.identity)) return
        if (command.identity in pendingClears || loadedGeneration != command.generation) {
            publish(command.generation, command.identity, null, failed = true)
            return
        }
        try {
            val next = canonicalHistory(
                listOf(command.entry) + loadedEntries.filterNot { it.id == command.entry.id },
            )
            // The identity may retire during this synchronous IO. The queued clear
            // runs after it; the generation check below suppresses any stale publish.
            store.write(command.identity, next)
            if (!current(command.generation, command.identity)) return
            loadedEntries = next
            publish(command.generation, command.identity, next, failed = !cleared)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            publish(command.generation, command.identity, null, failed = true)
        }
    }

    private fun retire(command: Command.Clear) {
        command.previous?.let(pendingClears::add)
        val cleared = flushClears()
        if (current(command.generation, null)) {
            loadedGeneration = -1
            loadedEntries = emptyList()
            publish(command.generation, null, emptyList(), failed = !cleared)
        }
    }
}

private fun canonicalHistory(entries: List<HistoryEntry>): List<HistoryEntry> {
    entries.forEach { entry ->
        if (entry.id.isBlank() || entry.id.length > 2048 || entry.title.length > 4096 ||
            entry.artist.length > 4096 || entry.album.length > 4096 ||
            (entry.language != null && entry.language.length > 128) ||
            !entry.durationSeconds.isFinite() || entry.durationSeconds < 0 || entry.playedAt < 0) failStorage()
    }
    return entries.sortedByDescending { it.playedAt }.distinctBy { it.id }.take(MAX_HISTORY_ENTRIES).toList()
}

private fun failStorage(): Nothing = throw ApiFailure(Failure.STORAGE)

private inline fun <T> storageOperation(block: () -> T): T = try {
    block()
} catch (error: Exception) {
    if (error is CancellationException) throw error
    throw ApiFailure(Failure.STORAGE)
}
