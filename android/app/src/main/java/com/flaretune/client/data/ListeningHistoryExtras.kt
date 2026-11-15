package com.flaretune.client.data

import com.flaretune.client.model.ClientNotice
import com.flaretune.client.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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

const val MAX_VISITED_ARTISTS = 40
const val MAX_PLAYED_COLLECTIONS = 40
const val MAX_QUEUE_SNAPSHOTS = 18
private const val MAX_SNAPSHOT_JSON_BYTES = 450 * 1024

enum class PlayedCollectionType { ALBUM, PLAYLIST }

/** Safe metadata-only visited artist entry; transient cover/media URLs are never persisted. */
data class VisitedArtistEntry(
    val id: String? = null,
    val name: String,
    val source: String = "local",
    val visitedAtMs: Long,
) {
    fun canonicalKey(): String {
        val cleanId = id?.trim()?.takeIf(String::isNotBlank)
        return if (cleanId != null) "id:$cleanId"
        else "name:${source.trim().lowercase()}:${name.trim().lowercase()}"
    }
}

/** Safe metadata-only played album or playlist entry; transient cover/media URLs are never persisted. */
data class PlayedCollectionEntry(
    val type: PlayedCollectionType,
    val id: String,
    val title: String,
    val subtitle: String = "",
    val trackCount: Int = 0,
    val source: String = "local",
    val kind: String = "regular",
    val playedAtMs: Long,
) {
    fun canonicalKey(): String = "${type.name}:$id"
}

/** Auto-captured metadata snapshot of a playback queue before replacement. */
data class QueueSnapshotEntry(
    val id: String,
    val capturedAtMs: Long,
    val sourceLabel: String = "",
    val sourceType: String = "",
    val entries: List<QueueEntry>,
    val currentIndex: Int,
) {
    fun signature(): Pair<List<String>, Int> = entries.map { it.id } to currentIndex
}

data class ListeningHistoryExtrasSnapshot(
    val artists: List<VisitedArtistEntry> = emptyList(),
    val collections: List<PlayedCollectionEntry> = emptyList(),
    val queueSnapshots: List<QueueSnapshotEntry> = emptyList(),
)

interface ListeningHistoryExtrasStore {
    fun read(identity: HistoryIdentity): ListeningHistoryExtrasSnapshot
    fun write(identity: HistoryIdentity, snapshot: ListeningHistoryExtrasSnapshot)
    fun clear(identity: HistoryIdentity)
}

/** Versioned, bounded per-account codec reusing [HistoryStorage]; never persists URLs or auth tokens. */
class JsonListeningHistoryExtrasStore(private val storage: HistoryStorage) : ListeningHistoryExtrasStore {
    override fun read(identity: HistoryIdentity): ListeningHistoryExtrasSnapshot = localStateOperation {
        ListeningHistoryExtrasSnapshot(
            artists = readArtists(identity),
            collections = readCollections(identity),
            queueSnapshots = readQueueSnapshots(identity),
        )
    }

    override fun write(identity: HistoryIdentity, snapshot: ListeningHistoryExtrasSnapshot) = localStateOperation {
        val canonical = canonicalExtras(snapshot)
        writeArtists(identity, canonical.artists)
        writeCollections(identity, canonical.collections)
        writeQueueSnapshots(identity, canonical.queueSnapshots)
    }

    override fun clear(identity: HistoryIdentity) = localStateOperation {
        storage.remove(listeningStorageKey(ARTISTS_PREFIX, identity))
        storage.remove(listeningStorageKey(COLLECTIONS_PREFIX, identity))
        storage.remove(listeningStorageKey(SNAPSHOTS_PREFIX, identity))
    }

    private fun readArtists(identity: HistoryIdentity): List<VisitedArtistEntry> {
        val raw = storage.read(listeningStorageKey(ARTISTS_PREFIX, identity)) ?: return emptyList()
        val doc = boundedLocalJson(raw)
        exactKeys(doc, setOf("version", "artists"))
        if (doc.opt("version") != 1) localStateFailure()
        val rows = doc.opt("artists") as? JSONArray ?: localStateFailure()
        if (rows.length() > MAX_VISITED_ARTISTS) localStateFailure()
        return canonicalVisitedArtists((0 until rows.length()).map { index ->
            val row = rows.optJSONObject(index) ?: localStateFailure()
            exactKeys(row, setOf("id", "name", "source", "visitedAtMs"))
            val artistId = if (row.isNull("id")) null else localString(row, "id").takeIf { it.isNotBlank() }
            VisitedArtistEntry(
                id = artistId,
                name = localString(row, "name"),
                source = localString(row, "source"),
                visitedAtMs = localLong(row, "visitedAtMs"),
            )
        })
    }

    private fun writeArtists(identity: HistoryIdentity, artists: List<VisitedArtistEntry>) {
        val key = listeningStorageKey(ARTISTS_PREFIX, identity)
        if (artists.isEmpty()) {
            storage.remove(key)
            return
        }
        val rows = JSONArray()
        for (artist in artists.take(MAX_VISITED_ARTISTS)) {
            rows.put(
                JSONObject()
                    .put("id", artist.id ?: JSONObject.NULL)
                    .put("name", cleanText(artist.name, 400))
                    .put("source", cleanText(artist.source, 64))
                    .put("visitedAtMs", artist.visitedAtMs.coerceAtLeast(0L)),
            )
        }
        writeLocalJson(storage, key, JSONObject().put("version", 1).put("artists", rows))
    }

    private fun readCollections(identity: HistoryIdentity): List<PlayedCollectionEntry> {
        val raw = storage.read(listeningStorageKey(COLLECTIONS_PREFIX, identity)) ?: return emptyList()
        val doc = boundedLocalJson(raw)
        exactKeys(doc, setOf("version", "collections"))
        if (doc.opt("version") != 1) localStateFailure()
        val rows = doc.opt("collections") as? JSONArray ?: localStateFailure()
        if (rows.length() > MAX_PLAYED_COLLECTIONS) localStateFailure()
        return canonicalPlayedCollections((0 until rows.length()).map { index ->
            val row = rows.optJSONObject(index) ?: localStateFailure()
            exactKeys(row, setOf("type", "id", "title", "subtitle", "trackCount", "source", "kind", "playedAtMs"))
            val typeName = localString(row, "type")
            val type = PlayedCollectionType.entries.find { it.name == typeName } ?: localStateFailure()
            val trackCount = (row.opt("trackCount") as? Number)?.toInt()?.takeIf { it >= 0 } ?: localStateFailure()
            PlayedCollectionEntry(
                type = type,
                id = localString(row, "id"),
                title = localString(row, "title"),
                subtitle = localString(row, "subtitle"),
                trackCount = trackCount,
                source = localString(row, "source"),
                kind = localString(row, "kind"),
                playedAtMs = localLong(row, "playedAtMs"),
            )
        })
    }

    private fun writeCollections(identity: HistoryIdentity, collections: List<PlayedCollectionEntry>) {
        val key = listeningStorageKey(COLLECTIONS_PREFIX, identity)
        if (collections.isEmpty()) {
            storage.remove(key)
            return
        }
        val rows = JSONArray()
        for (item in collections.take(MAX_PLAYED_COLLECTIONS)) {
            rows.put(
                JSONObject()
                    .put("type", item.type.name)
                    .put("id", cleanText(item.id, 256))
                    .put("title", cleanText(item.title, 400))
                    .put("subtitle", cleanText(item.subtitle, 400))
                    .put("trackCount", item.trackCount.coerceAtLeast(0))
                    .put("source", cleanText(item.source, 64))
                    .put("kind", cleanText(item.kind, 64))
                    .put("playedAtMs", item.playedAtMs.coerceAtLeast(0L)),
            )
        }
        writeLocalJson(storage, key, JSONObject().put("version", 1).put("collections", rows))
    }

    private fun readQueueSnapshots(identity: HistoryIdentity): List<QueueSnapshotEntry> {
        val raw = storage.read(listeningStorageKey(SNAPSHOTS_PREFIX, identity)) ?: return emptyList()
        val doc = boundedLocalJson(raw)
        exactKeys(doc, setOf("version", "snapshots"))
        if (doc.opt("version") != 1) localStateFailure()
        val rows = doc.opt("snapshots") as? JSONArray ?: localStateFailure()
        if (rows.length() > MAX_QUEUE_SNAPSHOTS) localStateFailure()
        return canonicalQueueSnapshots((0 until rows.length()).map { index ->
            val row = rows.optJSONObject(index) ?: localStateFailure()
            exactKeys(row, setOf("id", "capturedAtMs", "sourceLabel", "sourceType", "currentIndex", "entries"))
            val entryRows = row.opt("entries") as? JSONArray ?: localStateFailure()
            if (entryRows.length() > 500) localStateFailure()
            val entries = (0 until entryRows.length()).map { itemIndex ->
                val item = entryRows.optJSONObject(itemIndex) ?: localStateFailure()
                relationKeys(item, setOf("id", "title", "artist", "album", "durationSeconds", "language"))
                val songId = localString(item, "id")
                val links = item.readSongRelations(songId)
                metadataEntry(
                    Song(
                        id = songId,
                        title = localString(item, "title"),
                        artist = localString(item, "artist"),
                        album = localString(item, "album"),
                        durationSeconds = localDuration(item),
                        audioUrl = "",
                        language = localLanguage(item),
                        source = links.source,
                        artists = links.artists,
                        artistId = links.artistId,
                        albumId = links.albumId,
                    ),
                )
            }
            val currentIndex = (row.opt("currentIndex") as? Number)?.toInt() ?: localStateFailure()
            QueueSnapshotEntry(
                id = localString(row, "id"),
                capturedAtMs = localLong(row, "capturedAtMs"),
                sourceLabel = localString(row, "sourceLabel"),
                sourceType = localString(row, "sourceType"),
                entries = entries,
                currentIndex = currentIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0)),
            )
        })
    }

    private fun writeQueueSnapshots(identity: HistoryIdentity, snapshots: List<QueueSnapshotEntry>) {
        val key = listeningStorageKey(SNAPSHOTS_PREFIX, identity)
        if (snapshots.isEmpty()) {
            storage.remove(key)
            return
        }
        var kept = snapshots.take(MAX_QUEUE_SNAPSHOTS)
        while (kept.isNotEmpty()) {
            val payload = encodeSnapshotsJson(kept)
            if (payload.toString().toByteArray(Charsets.UTF_8).size <= MAX_SNAPSHOT_JSON_BYTES || kept.size == 1) {
                writeLocalJson(storage, key, payload)
                return
            }
            kept = kept.dropLast(1)
        }
    }

    private fun encodeSnapshotsJson(snapshots: List<QueueSnapshotEntry>): JSONObject {
        val rows = JSONArray()
        for (snapshot in snapshots) {
            val entries = JSONArray()
            for (entry in snapshot.entries.take(500)) {
                entries.put(
                    JSONObject()
                        .put("id", entry.id)
                        .put("title", cleanText(entry.title, 2000))
                        .put("artist", cleanText(entry.artist, 2000))
                        .put("album", cleanText(entry.album, 2000))
                        .put("durationSeconds", entry.durationSeconds)
                        .put("language", entry.language ?: JSONObject.NULL)
                        .putSongRelations(entry.id, entry.relations),
                )
            }
            rows.put(
                JSONObject()
                    .put("id", cleanText(snapshot.id, 128))
                    .put("capturedAtMs", snapshot.capturedAtMs.coerceAtLeast(0L))
                    .put("sourceLabel", cleanText(snapshot.sourceLabel, 400))
                    .put("sourceType", cleanText(snapshot.sourceType, 64))
                    .put("currentIndex", snapshot.currentIndex.coerceAtLeast(0))
                    .put("entries", entries),
            )
        }
        return JSONObject().put("version", 1).put("snapshots", rows)
    }

    private fun cleanText(value: String, maxLen: Int): String =
        value.replace(Regex("[\\u0000-\\u001F\\u007F]"), " ").trim().take(maxLen)

    companion object {
        private const val ARTISTS_PREFIX = "listening_artists"
        private const val COLLECTIONS_PREFIX = "listening_collections"
        private const val SNAPSHOTS_PREFIX = "listening_queue_snapshots"
    }
}

internal fun canonicalVisitedArtists(entries: List<VisitedArtistEntry>): List<VisitedArtistEntry> {
    val result = mutableListOf<VisitedArtistEntry>()
    val seenKeys = HashSet<String>()
    val seenNameSources = HashSet<String>()
    for (entry in entries.filter { it.name.isNotBlank() }.sortedByDescending { it.visitedAtMs }) {
        val key = entry.canonicalKey()
        val nameSource = "${entry.source.trim().lowercase()}:${entry.name.trim().lowercase()}"
        if (!seenKeys.add(key)) continue
        if (entry.id.isNullOrBlank() && nameSource in seenNameSources) continue
        if (!entry.id.isNullOrBlank() && nameSource in seenNameSources) {
            result.removeAll { it.id.isNullOrBlank() && "${it.source.trim().lowercase()}:${it.name.trim().lowercase()}" == nameSource }
        }
        seenNameSources.add(nameSource)
        result += entry
        if (result.size >= MAX_VISITED_ARTISTS) break
    }
    return result
}

internal fun canonicalPlayedCollections(entries: List<PlayedCollectionEntry>): List<PlayedCollectionEntry> =
    entries
        .filter { it.id.isNotBlank() && it.title.isNotBlank() }
        .sortedByDescending { it.playedAtMs }
        .distinctBy { it.canonicalKey() }
        .take(MAX_PLAYED_COLLECTIONS)

internal fun canonicalQueueSnapshots(entries: List<QueueSnapshotEntry>): List<QueueSnapshotEntry> {
    val sorted = entries
        .filter { it.id.isNotBlank() && it.entries.count { entry -> entry.id.isNotBlank() } >= 2 }
        .sortedByDescending { it.capturedAtMs }
    val result = mutableListOf<QueueSnapshotEntry>()
    val seenIds = HashSet<String>()
    for (item in sorted) {
        if (!seenIds.add(item.id)) continue
        val clampedIndex = item.currentIndex.coerceIn(0, item.entries.lastIndex)
        val normalized = if (clampedIndex == item.currentIndex) item else item.copy(currentIndex = clampedIndex)
        val prev = result.lastOrNull()
        if (prev != null && prev.signature() == normalized.signature()) continue
        result += normalized
        if (result.size >= MAX_QUEUE_SNAPSHOTS) break
    }
    return result
}

internal fun canonicalExtras(snapshot: ListeningHistoryExtrasSnapshot) = ListeningHistoryExtrasSnapshot(
    artists = canonicalVisitedArtists(snapshot.artists),
    collections = canonicalPlayedCollections(snapshot.collections),
    queueSnapshots = canonicalQueueSnapshots(snapshot.queueSnapshots),
)

/** Per-account serialized writer matching PlaybackHistoryRepository isolation semantics. */
class ListeningHistoryExtrasRepository(
    private val store: ListeningHistoryExtrasStore,
    scope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val lock = Any()
    private var identity: HistoryIdentity? = null
    private var generation = 0L
    private val mutableState = MutableStateFlow(ListeningHistoryExtrasSnapshot())
    private val mutableNotice = MutableStateFlow<ClientNotice?>(null)
    val state: StateFlow<ListeningHistoryExtrasSnapshot> = mutableState.asStateFlow()
    val notice: StateFlow<ClientNotice?> = mutableNotice.asStateFlow()
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val pendingClears = linkedSetOf<HistoryIdentity>()
    private var loadedGeneration = -1L
    private var loadedSnapshot = ListeningHistoryExtrasSnapshot()

    private sealed interface Command {
        val generation: Long
        data class Bind(
            override val generation: Long,
            val identity: HistoryIdentity,
            val previous: HistoryIdentity?,
        ) : Command
        data class Mutate(
            override val generation: Long,
            val identity: HistoryIdentity,
            val completion: CompletableDeferred<Boolean>? = null,
            val transform: (ListeningHistoryExtrasSnapshot) -> ListeningHistoryExtrasSnapshot,
        ) : Command
        data class Clear(
            override val generation: Long,
            val previous: HistoryIdentity?,
        ) : Command
        data class ClearTarget(
            override val generation: Long,
            val target: HistoryIdentity,
        ) : Command
    }

    private val worker = scope.launch(ioDispatcher) {
        for (command in commands) {
            when (command) {
                is Command.Bind -> handleBind(command)
                is Command.Mutate -> handleMutate(command)
                is Command.Clear -> handleClear(command)
                is Command.ClearTarget -> handleClearTarget(command)
            }
        }
    }

    fun bind(identity: HistoryIdentity) {
        val normalized = identity.normalized()
        synchronized(lock) {
            if (this.identity == normalized) return
            this.identity = normalized
            generation++
            mutableState.value = ListeningHistoryExtrasSnapshot()
            mutableNotice.value = null
            enqueue(Command.Bind(generation, normalized, previous = null))
        }
    }

    fun recordArtist(entry: VisitedArtistEntry) {
        if (entry.name.isBlank()) return
        mutateAsync { current ->
            val merged = listOf(entry) + current.artists.filterNot { existing ->
                val sameId = !entry.id.isNullOrBlank() && existing.id == entry.id
                val sameSourceName = existing.source.equals(entry.source, ignoreCase = true) &&
                    existing.name.equals(entry.name, ignoreCase = true) &&
                    (entry.id.isNullOrBlank() || existing.id.isNullOrBlank())
                sameId || sameSourceName
            }
            current.copy(artists = canonicalVisitedArtists(merged))
        }
    }

    fun recordCollection(entry: PlayedCollectionEntry) {
        if (entry.id.isBlank() || entry.title.isBlank()) return
        mutateAsync { current ->
            val merged = listOf(entry) + current.collections.filterNot { it.canonicalKey() == entry.canonicalKey() }
            current.copy(collections = canonicalPlayedCollections(merged))
        }
    }

    fun recordQueueSnapshot(entry: QueueSnapshotEntry) {
        if (entry.id.isBlank() || entry.entries.count { it.id.isNotBlank() } < 2) return
        mutateAsync { current ->
            val head = current.queueSnapshots.firstOrNull()
            val clamped = entry.copy(currentIndex = entry.currentIndex.coerceIn(0, entry.entries.lastIndex))
            val nextList = if (head != null && head.signature() == clamped.signature()) {
                listOf(clamped.copy(id = head.id)) + current.queueSnapshots.drop(1)
            } else {
                listOf(clamped) + current.queueSnapshots
            }
            current.copy(queueSnapshots = canonicalQueueSnapshots(nextList))
        }
    }

    suspend fun removeArtist(artistKey: String): Boolean = mutateSuspend { current ->
        current.copy(artists = current.artists.filterNot { it.canonicalKey() == artistKey })
    }

    suspend fun clearArtists(): Boolean = mutateSuspend { current ->
        current.copy(artists = emptyList())
    }

    suspend fun removeCollection(collectionKey: String): Boolean = mutateSuspend { current ->
        current.copy(collections = current.collections.filterNot { it.canonicalKey() == collectionKey })
    }

    suspend fun clearCollections(): Boolean = mutateSuspend { current ->
        current.copy(collections = emptyList())
    }

    suspend fun removeQueueSnapshot(snapshotId: String): Boolean = mutateSuspend { current ->
        current.copy(queueSnapshots = current.queueSnapshots.filterNot { it.id == snapshotId })
    }

    suspend fun clearQueueSnapshots(): Boolean = mutateSuspend { current ->
        current.copy(queueSnapshots = emptyList())
    }

    /** Account switch: drop in-memory ownership without erasing this account's local records. */
    fun detach() {
        synchronized(lock) {
            identity = null
            generation++
            mutableState.value = ListeningHistoryExtrasSnapshot()
            mutableNotice.value = null
            enqueue(Command.Clear(generation, null))
        }
    }

    /** Clear the active identity's stored data and revoke binding. */
    fun clear() {
        synchronized(lock) {
            val previous = identity
            identity = null
            generation++
            mutableState.value = ListeningHistoryExtrasSnapshot()
            mutableNotice.value = null
            enqueue(Command.Clear(generation, previous))
        }
    }

    /** Delete local extras for a removed account; clears in-memory state if it matches active identity. */
    fun clearAccount(target: HistoryIdentity) {
        val normalized = runCatching { target.normalized() }.getOrNull() ?: return
        synchronized(lock) {
            if (identity == normalized) {
                identity = null
                generation++
                mutableState.value = ListeningHistoryExtrasSnapshot()
                mutableNotice.value = null
            }
            enqueue(Command.ClearTarget(generation, normalized))
        }
    }

    private fun mutateAsync(transform: (ListeningHistoryExtrasSnapshot) -> ListeningHistoryExtrasSnapshot) {
        synchronized(lock) {
            val current = identity ?: return
            enqueue(Command.Mutate(generation, current, null, transform))
        }
    }

    private suspend fun mutateSuspend(transform: (ListeningHistoryExtrasSnapshot) -> ListeningHistoryExtrasSnapshot): Boolean {
        val completion = CompletableDeferred<Boolean>()
        synchronized(lock) {
            val current = identity ?: return false
            if (!worker.isActive || commands.trySend(Command.Mutate(generation, current, completion, transform)).isFailure) {
                completion.complete(false)
            }
        }
        return completion.await()
    }

    private fun enqueue(command: Command) {
        if (!worker.isActive || commands.trySend(command).isFailure) {
            mutableNotice.value = ClientNotice.STORAGE_FAILED
        }
    }

    private fun isCurrent(ticket: Long, expected: HistoryIdentity?): Boolean = synchronized(lock) {
        generation == ticket && identity == expected
    }

    private fun publish(ticket: Long, expected: HistoryIdentity?, snapshot: ListeningHistoryExtrasSnapshot?, failed: Boolean) {
        synchronized(lock) {
            if (generation != ticket || identity != expected) return
            if (snapshot != null) mutableState.value = snapshot
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

    private fun handleBind(command: Command.Bind) {
        command.previous?.let(pendingClears::add)
        flushClears()
        if (!isCurrent(command.generation, command.identity)) return
        loadedGeneration = command.generation
        loadedSnapshot = ListeningHistoryExtrasSnapshot()
        if (command.identity in pendingClears) {
            publish(command.generation, command.identity, ListeningHistoryExtrasSnapshot(), failed = true)
            return
        }
        try {
            val loaded = canonicalExtras(store.read(command.identity))
            if (!isCurrent(command.generation, command.identity)) return
            loadedSnapshot = loaded
            publish(command.generation, command.identity, loaded, failed = false)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (isCurrent(command.generation, command.identity)) {
                publish(command.generation, command.identity, ListeningHistoryExtrasSnapshot(), failed = true)
            }
        }
    }

    private fun handleMutate(command: Command.Mutate) {
        if (!isCurrent(command.generation, command.identity) || loadedGeneration != command.generation) {
            command.completion?.complete(false)
            return
        }
        val base = if (command.identity in pendingClears) ListeningHistoryExtrasSnapshot() else loadedSnapshot
        val updated = canonicalExtras(command.transform(base))
        if (updated == loadedSnapshot && command.identity !in pendingClears) {
            command.completion?.complete(true)
            return
        }
        try {
            store.write(command.identity, updated)
            pendingClears.remove(command.identity)
            loadedSnapshot = updated
            if (isCurrent(command.generation, command.identity)) {
                publish(command.generation, command.identity, updated, failed = false)
            }
            command.completion?.complete(true)
        } catch (error: Exception) {
            if (error is CancellationException) {
                command.completion?.complete(false)
                throw error
            }
            if (isCurrent(command.generation, command.identity)) {
                publish(command.generation, command.identity, null, failed = true)
            }
            command.completion?.complete(false)
        }
    }

    private fun handleClear(command: Command.Clear) {
        loadedGeneration = -1L
        loadedSnapshot = ListeningHistoryExtrasSnapshot()
        command.previous?.let(pendingClears::add)
        flushClears()
    }

    private fun handleClearTarget(command: Command.ClearTarget) {
        if (command.target == synchronized(lock) { identity }) {
            loadedGeneration = -1L
            loadedSnapshot = ListeningHistoryExtrasSnapshot()
        }
        pendingClears.add(command.target)
        flushClears()
    }
}
