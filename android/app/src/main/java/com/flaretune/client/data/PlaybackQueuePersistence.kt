package com.flaretune.client.data

import android.content.Context
import android.content.SharedPreferences
import com.flaretune.client.model.ClientNotice
import com.flaretune.client.model.Song
import com.flaretune.client.model.SongRelations
import com.flaretune.client.model.relations
import com.flaretune.client.ui.PlaybackSnapshot
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

const val MAX_PERSISTED_QUEUE_ENTRIES = 500
const val MAX_PERSISTED_QUEUE_BYTES = 512 * 1024

/** Stable metadata only. Replaying an entry requires fresh resolution in the active session. */
data class QueueEntry(
    val id: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val durationSeconds: Double = 0.0,
    val language: String? = null,
    val relations: SongRelations = SongRelations(),
) {
    fun toSong(): Song = Song(
        id = id, title = title, artist = artist, album = album,
        durationSeconds = durationSeconds, language = language, audioUrl = "",
        source = relations.source, artists = relations.artists, artistId = relations.artistId, albumId = relations.albumId,
    )
}

/** Queue order and duplicate occurrences are intentional; playback always restores paused. */
data class SavedPlaybackQueue(
    val entries: List<QueueEntry>,
    val currentIndex: Int,
    val positionMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = 0,
)

interface PlaybackQueueStore {
    fun read(identity: HistoryIdentity): SavedPlaybackQueue?
    fun write(identity: HistoryIdentity, queue: SavedPlaybackQueue)
    fun clear(identity: HistoryIdentity)
}

class JsonPlaybackQueueStore(private val storage: HistoryStorage) : PlaybackQueueStore {
    override fun read(identity: HistoryIdentity): SavedPlaybackQueue? = queueStorageOperation {
        val raw = storage.read(key(identity)) ?: return@queueStorageOperation null
        if (raw.length > MAX_PERSISTED_QUEUE_BYTES ||
            raw.toByteArray(Charsets.UTF_8).size > MAX_PERSISTED_QUEUE_BYTES) queueStorageFailure()
        val document = JSONObject(raw)
        if (document.keys().asSequence().toSet() !=
            setOf("version", "entries", "currentIndex", "positionMs", "shuffle", "repeatMode") ||
            document.opt("version") !is Int || document.opt("version") != 1) queueStorageFailure()
        val rows = document.opt("entries") as? JSONArray ?: queueStorageFailure()
        if (rows.length() > MAX_PERSISTED_QUEUE_ENTRIES) queueStorageFailure()
        val entries = (0 until rows.length()).map { index ->
            val row = rows.opt(index) as? JSONObject ?: queueStorageFailure()
            val allowed = setOf("id", "title", "artist", "album", "durationSeconds", "language") + SONG_RELATION_KEYS
            if (row.keys().asSequence().any { it !in allowed }) queueStorageFailure()
            val language = when (val value = row.opt("language")) {
                null, JSONObject.NULL -> null
                is String -> value
                else -> queueStorageFailure()
            }
            QueueEntry(
                id = row.opt("id") as? String ?: queueStorageFailure(),
                title = row.opt("title") as? String ?: queueStorageFailure(),
                artist = row.opt("artist") as? String ?: queueStorageFailure(),
                album = row.opt("album") as? String ?: queueStorageFailure(),
                durationSeconds = (row.opt("durationSeconds") as? Number)?.toDouble() ?: queueStorageFailure(),
                language = language, relations = row.readSongRelations(row.opt("id") as? String ?: queueStorageFailure()),
            )
        }
        val currentIndex = document.queueLong("currentIndex")
        val repeatMode = document.queueLong("repeatMode")
        if (currentIndex !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() ||
            repeatMode !in 0L..2L) queueStorageFailure()
        validateQueue(SavedPlaybackQueue(
            entries = entries, currentIndex = currentIndex.toInt(),
            positionMs = document.queueLong("positionMs"),
            shuffle = document.opt("shuffle") as? Boolean ?: queueStorageFailure(),
            repeatMode = repeatMode.toInt(),
        )).takeIf { it.entries.isNotEmpty() }
    }

    override fun write(identity: HistoryIdentity, queue: SavedPlaybackQueue) = queueStorageOperation {
        val safe = validateQueue(queue)
        if (safe.entries.isEmpty()) {
            storage.remove(key(identity))
        } else {
            val rows = JSONArray()
            safe.entries.forEach { entry ->
                rows.put(JSONObject().put("id", entry.id).put("title", entry.title)
                    .put("artist", entry.artist).put("album", entry.album)
                    .put("durationSeconds", entry.durationSeconds)
                    .put("language", entry.language ?: JSONObject.NULL).putSongRelations(entry.id, entry.relations))
            }
            val encoded = JSONObject().put("version", 1).put("entries", rows)
                .put("currentIndex", safe.currentIndex).put("positionMs", safe.positionMs)
                .put("shuffle", safe.shuffle).put("repeatMode", safe.repeatMode).toString()
            if (encoded.toByteArray(Charsets.UTF_8).size > MAX_PERSISTED_QUEUE_BYTES) queueStorageFailure()
            storage.write(key(identity), encoded)
        }
    }

    override fun clear(identity: HistoryIdentity) = queueStorageOperation { storage.remove(key(identity)) }

    private fun key(identity: HistoryIdentity): String {
        val normalized = identity.normalized()
        val payload = "FlareTune.Queue.v1|${normalized.origin.length}:${normalized.origin}|${normalized.accountId.length}:${normalized.accountId}"
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return "queue_v1_" + digest.joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}

private class QueuePreferences(private val preferences: SharedPreferences) : HistoryStorage {
    override fun read(key: String): String? = preferences.getString(key, null)
    override fun write(key: String, value: String) {
        if (!preferences.edit().putString(key, value).commit()) queueStorageFailure()
    }
    override fun remove(key: String) {
        if (!preferences.edit().remove(key).commit()) queueStorageFailure()
    }
}

class AndroidPlaybackQueueStore(context: Context) : PlaybackQueueStore by JsonPlaybackQueueStore(
    QueuePreferences(queueStorageOperation {
        context.applicationContext.getSharedPreferences("playback_queue_v1", Context.MODE_PRIVATE)
    }),
)

private class QueueMemoryStorage : HistoryStorage {
    private val values = mutableMapOf<String, String>()
    override fun read(key: String): String? = synchronized(values) { values[key] }
    override fun write(key: String, value: String) { synchronized(values) { values[key] = value } }
    override fun remove(key: String) { synchronized(values) { values.remove(key) } }
}

class InMemoryPlaybackQueueStore : PlaybackQueueStore by JsonPlaybackQueueStore(QueueMemoryStorage())

/**
 * Application-scoped serial IO. clear retires the binding immediately; it must be
 * allowed to finish before the supplied scope ends. No playing/buffering intent is
 * persisted, and this repository never touches a media controller or credentials.
 */
class PlaybackQueueRepository(
    private val store: PlaybackQueueStore,
    scope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val lock = Any()
    private var identity: HistoryIdentity? = null
    private var generation = 0L
    private val mutableState = MutableStateFlow<SavedPlaybackQueue?>(null)
    private val mutableLoaded = MutableStateFlow(false)
    private val mutableNotice = MutableStateFlow<ClientNotice?>(null)
    val state: StateFlow<SavedPlaybackQueue?> = mutableState.asStateFlow()
    /** True means the current load/save attempt finished, including a reported failure. */
    val loaded: StateFlow<Boolean> = mutableLoaded.asStateFlow()
    val notice: StateFlow<ClientNotice?> = mutableNotice.asStateFlow()
    private val commands = Channel<Command>(Channel.UNLIMITED)
    // Actor-owned quarantine: failed retirement must not revive on a later bind/reload.
    private val pendingClears = linkedSetOf<HistoryIdentity>()

    private sealed interface Command {
        val generation: Long
        data class Load(
            override val generation: Long,
            val identity: HistoryIdentity,
            val previous: HistoryIdentity?,
        ) : Command
        data class Save(
            override val generation: Long,
            val identity: HistoryIdentity,
            val queue: SavedPlaybackQueue?,
        ) : Command
        data class Clear(
            override val generation: Long,
            val previous: HistoryIdentity?,
        ) : Command
    }

    private val worker = scope.launch(ioDispatcher) {
        for (command in commands) when (command) {
            is Command.Load -> load(command)
            is Command.Save -> persist(command)
            is Command.Clear -> retire(command)
        }
    }

    fun bind(identity: HistoryIdentity) {
        val normalized = identity.normalized()
        synchronized(lock) {
            if (this.identity == normalized) return
            val previous = this.identity
            this.identity = normalized
            generation++
            mutableState.value = null
            mutableLoaded.value = false
            mutableNotice.value = null
            enqueue(Command.Load(generation, normalized, previous))
        }
    }

    /** The service must suppress its initial empty player snapshot until real queue data exists. */
    fun save(snapshot: PlaybackSnapshot) {
        synchronized(lock) {
            val current = identity ?: return
            val queue = snapshot.queue.takeIf { it.isNotEmpty() }?.let { songs ->
                SavedPlaybackQueue(
                    entries = songs.map { song ->
                        QueueEntry(song.id, song.title, song.artist, song.album, song.durationSeconds, song.language, song.relations)
                    },
                    currentIndex = snapshot.currentIndex, positionMs = snapshot.positionMs,
                    shuffle = snapshot.shuffle, repeatMode = snapshot.repeatMode,
                )
            }
            // A complete new snapshot supersedes an in-flight load or earlier save
            // publication. IO remains ordered, including all mandatory deletions.
            generation++
            enqueue(Command.Save(generation, current, queue))
        }
    }

    fun reload() {
        synchronized(lock) {
            val current = identity ?: return
            generation++
            mutableState.value = null
            mutableLoaded.value = false
            mutableNotice.value = null
            enqueue(Command.Load(generation, current, null))
        }
    }

    fun clear() {
        synchronized(lock) {
            val previous = identity
            identity = null
            generation++
            mutableState.value = null
            mutableLoaded.value = false
            mutableNotice.value = null
            enqueue(Command.Clear(generation, previous))
        }
    }

    private fun enqueue(command: Command) {
        if (!worker.isActive || commands.trySend(command).isFailure) {
            mutableNotice.value = ClientNotice.STORAGE_FAILED
            if (identity != null) mutableLoaded.value = true
        }
    }

    private fun current(ticket: Long, expected: HistoryIdentity?): Boolean = synchronized(lock) {
        generation == ticket && identity == expected
    }

    private fun publish(
        ticket: Long,
        expected: HistoryIdentity?,
        queue: SavedPlaybackQueue?,
        failed: Boolean,
        updateState: Boolean = true,
    ) {
        synchronized(lock) {
            if (generation != ticket || identity != expected) return
            if (updateState) mutableState.value = queue
            mutableNotice.value = if (failed) ClientNotice.STORAGE_FAILED else null
            mutableLoaded.value = expected != null
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

    private fun load(command: Command.Load) {
        command.previous?.let(pendingClears::add)
        val cleared = flushClears()
        if (!current(command.generation, command.identity)) return
        if (command.identity in pendingClears) {
            publish(command.generation, command.identity, null, failed = true)
            return
        }
        try {
            val queue = store.read(command.identity)?.let(::validateQueue)?.takeIf { it.entries.isNotEmpty() }
            publish(command.generation, command.identity, queue, failed = !cleared)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            publish(command.generation, command.identity, null, failed = true)
        }
    }

    private fun persist(command: Command.Save) {
        // An explicit queue clear is mandatory even when a later snapshot or bind
        // already superseded its UI generation.
        if (command.queue == null) pendingClears.add(command.identity)
        if (command.queue != null && !current(command.generation, command.identity)) return
        val cleared = flushClears()
        if (!current(command.generation, command.identity)) return
        if (command.identity in pendingClears) {
            publish(command.generation, command.identity, null, failed = true)
            return
        }
        if (command.queue == null) {
            publish(command.generation, command.identity, null, failed = !cleared)
            return
        }
        try {
            val safe = validateQueue(command.queue)
            store.write(command.identity, safe)
            publish(command.generation, command.identity, safe, failed = !cleared)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // Keep the last committed state rather than pretending the new snapshot persisted.
            publish(command.generation, command.identity, null, failed = true, updateState = false)
        }
    }

    private fun retire(command: Command.Clear) {
        command.previous?.let(pendingClears::add)
        val cleared = flushClears()
        publish(command.generation, null, null, failed = !cleared)
    }
}

private fun validateQueue(queue: SavedPlaybackQueue): SavedPlaybackQueue {
    if (queue.entries.size > MAX_PERSISTED_QUEUE_ENTRIES || queue.positionMs < 0 ||
        queue.repeatMode !in 0..2 ||
        (queue.entries.isEmpty() && queue.currentIndex != -1) ||
        (queue.entries.isNotEmpty() && queue.currentIndex !in queue.entries.indices)) queueStorageFailure()
    queue.entries.forEach { entry ->
        if (entry.id.isBlank() || entry.id.length > 2048 || entry.title.length > 4096 ||
            entry.artist.length > 4096 || entry.album.length > 4096 ||
            (entry.language != null && entry.language.length > 128) ||
            !entry.durationSeconds.isFinite() || entry.durationSeconds < 0 || !validSongRelations(entry.id, entry.relations)) queueStorageFailure()
    }
    return queue.copy(entries = queue.entries.toList())
}

private fun JSONObject.queueLong(name: String): Long = when (val value = opt(name)) {
    is Int -> value.toLong()
    is Long -> value
    else -> queueStorageFailure()
}

private fun queueStorageFailure(): Nothing = throw ApiFailure(Failure.STORAGE)

private inline fun <T> queueStorageOperation(block: () -> T): T = try {
    block()
} catch (error: Exception) {
    if (error is CancellationException) throw error
    throw ApiFailure(Failure.STORAGE)
}
