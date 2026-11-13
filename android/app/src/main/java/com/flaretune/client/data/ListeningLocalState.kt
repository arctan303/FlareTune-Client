package com.flaretune.client.data

import android.content.Context
import com.flaretune.client.model.Song
import com.flaretune.client.model.SpotlightArtist
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Account-owned search entities contain display metadata, never live media or lyric references. */
data class SearchEntity(
    val type: String, val id: String, val name: String, val artist: String = "", val album: String = "",
    val durationSeconds: Double = 0.0, val language: String? = null,
) {
    val title: String get() = name
    fun toSong(): Song = Song(id, name, artist, album, durationSeconds, "", language = language)
    companion object {
        fun fromSong(song: Song) = SearchEntity("song", song.id, song.title, song.artist, song.album, song.durationSeconds, song.language)
        fun fromArtist(name: String, id: String? = null) = SearchEntity("artist", id ?: name, name)
    }
}
data class SearchHistorySnapshot(val terms: List<String> = emptyList(), val entities: List<SearchEntity> = emptyList())

class SearchHistoryStore(private val storage: HistoryStorage) {
    @Synchronized fun load(identity: HistoryIdentity): SearchHistorySnapshot = localStateOperation {
        val raw = storage.read(listeningStorageKey("search_v1", identity)) ?: return@localStateOperation SearchHistorySnapshot()
        val data = boundedLocalJson(raw)
        exactKeys(data, setOf("version", "terms", "entities"))
        if (data.opt("version") != 1) localStateFailure()
        val terms = data.optJSONArray("terms") ?: localStateFailure()
        val entities = data.optJSONArray("entities") ?: localStateFailure()
        if (terms.length() > 10 || entities.length() > 10) localStateFailure()
        SearchHistorySnapshot((0 until terms.length()).map { searchTerm(terms.opt(it) as? String ?: localStateFailure()) },
            (0 until entities.length()).map { index ->
                val row = entities.optJSONObject(index) ?: localStateFailure()
                exactKeys(row, setOf("type", "id", "name", "artist", "album", "durationSeconds", "language"))
                validateEntity(SearchEntity(localString(row, "type"), localString(row, "id"), localString(row, "name"),
                    localString(row, "artist"), localString(row, "album"), localDuration(row), localLanguage(row)))
            })
    }
    @Synchronized fun addTerm(identity: HistoryIdentity, term: String): SearchHistorySnapshot {
        val clean = searchTerm(term)
        val old = load(identity)
        return save(identity, old.copy(terms = (listOf(clean) + old.terms.filterNot { it.equals(clean, true) }).take(10)))
    }
    @Synchronized fun removeTerm(identity: HistoryIdentity, term: String): SearchHistorySnapshot = load(identity).let { old ->
        save(identity, old.copy(terms = old.terms.filterNot { it.equals(term.trim(), true) }))
    }
    @Synchronized fun clearTerms(identity: HistoryIdentity): SearchHistorySnapshot = save(identity, load(identity).copy(terms = emptyList()))
    @Synchronized fun addEntity(identity: HistoryIdentity, entity: SearchEntity): SearchHistorySnapshot {
        val clean = validateEntity(entity)
        val old = load(identity)
        return save(identity, old.copy(entities = (listOf(clean) + old.entities.filterNot { sameEntity(it, clean) }).take(10)))
    }
    @Synchronized fun removeEntity(identity: HistoryIdentity, entity: SearchEntity): SearchHistorySnapshot = load(identity).let { old ->
        save(identity, old.copy(entities = old.entities.filterNot { sameEntity(it, entity) }))
    }
    @Synchronized fun clearEntities(identity: HistoryIdentity): SearchHistorySnapshot = save(identity, load(identity).copy(entities = emptyList()))
    private fun save(identity: HistoryIdentity, snapshot: SearchHistorySnapshot): SearchHistorySnapshot = localStateOperation {
        val data = JSONObject().put("version", 1).put("terms", JSONArray(snapshot.terms)).put("entities", JSONArray(snapshot.entities.map {
            JSONObject().put("type", it.type).put("id", it.id).put("name", it.name).put("artist", it.artist).put("album", it.album)
                .put("durationSeconds", it.durationSeconds).put("language", it.language ?: JSONObject.NULL)
        }))
        writeLocalJson(storage, listeningStorageKey("search_v1", identity), data)
        snapshot
    }
    private fun sameEntity(left: SearchEntity, right: SearchEntity): Boolean = left.type == right.type &&
        if (left.type == "artist" && !left.id.startsWith("yt:artist:") && !right.id.startsWith("yt:artist:"))
            left.id.trim().lowercase(Locale.ROOT) == right.id.trim().lowercase(Locale.ROOT) else left.id == right.id
    private fun searchTerm(value: String): String = value.trim().also { if (it.isEmpty() || it.length > 200 || it.any { c -> c.code < 32 || c.code == 127 }) localStateFailure() }
    private fun validateEntity(entity: SearchEntity): SearchEntity = entity.also {
        if (it.type !in setOf("song", "artist") || it.id.isBlank() || it.id.length > (if (it.type == "artist") 300 else 256) ||
            listOf(it.id, it.name, it.artist, it.album).any { field -> field.length > 2000 || field.any { c -> c.code < 32 || c.code == 127 } } ||
            it.name.isBlank() || !it.durationSeconds.isFinite() || it.durationSeconds < 0) localStateFailure()
    }
}

data class CachedSpotlight(val artist: String, val songCount: Int, val songs: List<QueueEntry>)
data class DiscoveryCacheSnapshot(val date: String, val songs: List<QueueEntry> = emptyList(), val spotlight: CachedSpotlight? = null, val dailyLoaded: Boolean = false, val spotlightLoaded: Boolean = false)

/** Disk is metadata-only; resolve every restored song in the current connection before using it. */
class DiscoveryCache(private val storage: HistoryStorage) {
    private val refreshedAt = mutableMapOf<String, Long>()
    @Synchronized fun load(identity: HistoryIdentity, day: String? = null): DiscoveryCacheSnapshot? = localStateOperation {
        val raw = storage.read(listeningStorageKey("discovery_v1", identity)) ?: return@localStateOperation null
        val data = boundedLocalJson(raw)
        exactKeys(data, setOf("version", "date", "songs", "spotlight", "dailyLoaded", "spotlightLoaded"))
        if (data.opt("version") != 1) localStateFailure()
        val date = localString(data, "date").also(::validateDay)
        val songs = entries(data.optJSONArray("songs") ?: localStateFailure())
        val spotlight = if (data.isNull("spotlight")) null else {
            val row = data.optJSONObject("spotlight") ?: localStateFailure()
            exactKeys(row, setOf("artist", "songCount", "songs"))
            val count = localLong(row, "songCount")
            if (count > Int.MAX_VALUE) localStateFailure()
            CachedSpotlight(localString(row, "artist"), count.toInt(), entries(row.optJSONArray("songs") ?: localStateFailure()))
        }
        val dailyLoaded = data.opt("dailyLoaded") as? Boolean ?: localStateFailure()
        val spotlightLoaded = data.opt("spotlightLoaded") as? Boolean ?: localStateFailure()
        if (day != null && date != day) null else DiscoveryCacheSnapshot(date, songs, spotlight, dailyLoaded, spotlightLoaded)
    }
    @Synchronized fun saveDaily(identity: HistoryIdentity, day: String, songs: List<Song>): DiscoveryCacheSnapshot {
        validateDay(day)
        if (songs.size > 20) localStateFailure()
        val current = load(identity, day) ?: DiscoveryCacheSnapshot(day)
        return save(identity, current.copy(songs = songs.map(::metadataEntry), dailyLoaded = true))
    }
    @Synchronized fun saveSpotlight(identity: HistoryIdentity, day: String, spotlight: SpotlightArtist?): DiscoveryCacheSnapshot {
        validateDay(day)
        val current = load(identity, day) ?: DiscoveryCacheSnapshot(day)
        val cached = spotlight?.let { CachedSpotlight(it.artist, it.songCount, it.songs.map(::metadataEntry)) }
        return save(identity, current.copy(spotlight = cached, spotlightLoaded = true))
    }
    @Synchronized fun remainingCooldownMs(identity: HistoryIdentity, kind: String, now: Long = System.currentTimeMillis()): Long {
        val cooldown = cooldown(kind)
        val elapsed = now - (refreshedAt[listeningStorageKey("cooldown_$kind", identity)] ?: return 0)
        return (cooldown - elapsed).coerceIn(0, cooldown)
    }
    @Synchronized fun markRefreshed(identity: HistoryIdentity, kind: String, now: Long = System.currentTimeMillis()) {
        cooldown(kind)
        refreshedAt[listeningStorageKey("cooldown_$kind", identity)] = now
    }
    @Synchronized fun clear(identity: HistoryIdentity) = localStateOperation { storage.remove(listeningStorageKey("discovery_v1", identity)) }
    private fun cooldown(kind: String): Long = when (kind) { "daily" -> 2000; "spotlight" -> 600; else -> localStateFailure() }
    private fun validateDay(day: String) { if (!day.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) localStateFailure() }
    private fun save(identity: HistoryIdentity, value: DiscoveryCacheSnapshot): DiscoveryCacheSnapshot = localStateOperation {
        if (value.songs.size > 20 || (value.spotlight?.songs?.size ?: 0) > 500) localStateFailure()
        val data = JSONObject().put("version", 1).put("date", value.date).put("songs", encodeEntries(value.songs)).put("dailyLoaded", value.dailyLoaded).put("spotlightLoaded", value.spotlightLoaded)
            .put("spotlight", value.spotlight?.let { JSONObject().put("artist", it.artist).put("songCount", it.songCount).put("songs", encodeEntries(it.songs)) } ?: JSONObject.NULL)
        writeLocalJson(storage, listeningStorageKey("discovery_v1", identity), data)
        value
    }
    private fun encodeEntries(entries: List<QueueEntry>) = JSONArray(entries.map { entry ->
        JSONObject().put("id", entry.id).put("title", entry.title).put("artist", entry.artist).put("album", entry.album)
            .put("durationSeconds", entry.durationSeconds).put("language", entry.language ?: JSONObject.NULL)
    })
    private fun entries(rows: JSONArray): List<QueueEntry> {
        if (rows.length() > 500) localStateFailure()
        return (0 until rows.length()).map { index ->
            val row = rows.optJSONObject(index) ?: localStateFailure()
            exactKeys(row, setOf("id", "title", "artist", "album", "durationSeconds", "language"))
            metadataEntry(Song(localString(row, "id"), localString(row, "title"), localString(row, "artist"), localString(row, "album"),
                localDuration(row), "", language = localLanguage(row)))
        }
    }
}

class AndroidListeningLocalStorage(context: Context) : HistoryStorage {
    private val preferences = context.getSharedPreferences("listening_local", Context.MODE_PRIVATE)
    override fun read(key: String): String? = preferences.getString(key, null)
    override fun write(key: String, value: String) { if (!preferences.edit().putString(key, value).commit()) throw IOException("Local state unavailable") }
    override fun remove(key: String) { if (!preferences.edit().remove(key).commit()) throw IOException("Local state unavailable") }
}
internal fun listeningStorageKey(prefix: String, identity: HistoryIdentity): String {
    val value = identity.normalized()
    val digest = MessageDigest.getInstance("SHA-256").digest((value.origin + "\u0000" + value.accountId).toByteArray(Charsets.UTF_8))
    return prefix + "_" + digest.joinToString("") { "%02x".format(it) }
}
internal fun metadataEntry(song: Song): QueueEntry {
    if (song.id.isBlank() || song.id.length > 256 || listOf(song.id, song.title, song.artist, song.album).any { it.length > 2000 || it.any { c -> c.code < 32 || c.code == 127 } } ||
        !song.durationSeconds.isFinite() || song.durationSeconds < 0) localStateFailure()
    return QueueEntry(song.id, song.title, song.artist, song.album, song.durationSeconds, song.language)
}
internal fun boundedLocalJson(raw: String): JSONObject {
    if (raw.toByteArray(Charsets.UTF_8).size > 512 * 1024) localStateFailure()
    return JSONObject(raw)
}
internal fun exactKeys(data: JSONObject, keys: Set<String>) { if (data.keys().asSequence().toSet() != keys) localStateFailure() }
internal fun localString(data: JSONObject, field: String): String = data.opt(field) as? String ?: localStateFailure()
internal fun localLanguage(data: JSONObject): String? = if (data.isNull("language")) null else data.opt("language") as? String ?: localStateFailure()
internal fun localDuration(data: JSONObject): Double = (data.opt("durationSeconds") as? Number)?.toDouble()?.takeIf { it.isFinite() && it >= 0 } ?: localStateFailure()
internal fun localLong(data: JSONObject, field: String): Long {
    val number = data.opt(field) as? Number ?: localStateFailure()
    val value = number.toDouble()
    if (!value.isFinite() || value < 0 || value > 9_007_199_254_740_991.0 || value != number.toLong().toDouble()) localStateFailure()
    return number.toLong()
}
internal fun writeLocalJson(storage: HistoryStorage, key: String, data: JSONObject) {
    val raw = data.toString()
    if (raw.toByteArray(Charsets.UTF_8).size > 512 * 1024) localStateFailure()
    storage.write(key, raw)
}
internal fun localStateFailure(): Nothing = throw ApiFailure(Failure.STORAGE)
internal inline fun <T> localStateOperation(work: () -> T): T = try { work() } catch (error: ApiFailure) { throw error }
    catch (_: Exception) { throw ApiFailure(Failure.STORAGE) }
