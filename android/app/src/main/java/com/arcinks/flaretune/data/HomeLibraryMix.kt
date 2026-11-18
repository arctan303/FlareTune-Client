package com.arcinks.flaretune.data

import com.arcinks.flaretune.model.Song
import kotlin.math.floor

internal const val HOME_LIBRARY_LIMIT = 16

/**
 * Allocates up to [limit] track slots across non-empty language buckets using
 * the Hamilton (largest-remainder) method.
 *
 * - If non-empty language count > [limit], selects the top [limit] languages by count
 *   (deterministic tie-break by language code) and gives each 1 slot.
 * - If non-empty language count <= [limit], guarantees 1 slot per non-empty language and
 *   distributes remaining slots proportionally by largest remainder, capped by each bucket's
 *   available song count so small libraries can still fill up to [limit] from larger buckets.
 */
internal fun allocateHomeLibraryQuotas(
    counts: Map<String, Int>,
    limit: Int = HOME_LIBRARY_LIMIT,
): Map<String, Int> {
    if (limit <= 0 || counts.isEmpty()) return emptyMap()
    val nonEmpty = counts.entries
        .mapNotNull { (lang, count) ->
            val key = lang.trim()
            if (key.isBlank() || count <= 0) null else key to count
        }
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
    if (nonEmpty.isEmpty()) return emptyMap()

    if (nonEmpty.size >= limit) {
        return nonEmpty.take(limit).associate { it.first to 1 }
    }

    val quotas = linkedMapOf<String, Int>()
    val remainingCap = linkedMapOf<String, Int>()
    nonEmpty.forEach { (lang, count) ->
        quotas[lang] = 1
        remainingCap[lang] = (count - 1).coerceAtLeast(0)
    }

    var remainingSlots = minOf(limit - nonEmpty.size, remainingCap.values.sum())
    while (remainingSlots > 0) {
        val eligible = nonEmpty.filter { (lang, _) -> (remainingCap[lang] ?: 0) > 0 }
        if (eligible.isEmpty()) break
        val weightSum = eligible.sumOf { it.second.toDouble() }
        if (weightSum <= 0.0) break

        val remainders = mutableListOf<Triple<String, Double, Int>>()
        var distributedThisPass = 0
        eligible.forEach { (lang, count) ->
            val cap = remainingCap[lang] ?: 0
            val exact = remainingSlots * (count.toDouble() / weightSum)
            val floored = floor(exact).toInt().coerceAtMost(cap)
            if (floored > 0) {
                quotas[lang] = (quotas[lang] ?: 0) + floored
                remainingCap[lang] = cap - floored
                distributedThisPass += floored
            }
            if ((remainingCap[lang] ?: 0) > 0) {
                remainders += Triple(lang, exact - floored, count)
            }
        }

        remainingSlots -= distributedThisPass
        if (remainingSlots <= 0) break

        val orderedRemainders = remainders.sortedWith(
            compareByDescending<Triple<String, Double, Int>> { it.second }
                .thenByDescending { it.third }
                .thenBy { it.first },
        )
        var addedRemainder = false
        for ((lang, _, _) in orderedRemainders) {
            if (remainingSlots <= 0) break
            val cap = remainingCap[lang] ?: 0
            if (cap > 0) {
                quotas[lang] = (quotas[lang] ?: 0) + 1
                remainingCap[lang] = cap - 1
                remainingSlots--
                addedRemainder = true
            }
        }
        if (!addedRemainder && distributedThisPass == 0) break
    }
    return quotas
}

/**
 * Interleaves per-language latest track lists proportionally according to [quotas]
 * up to [limit] distinct songs, topping up from [fallbackSongs] if any language bucket
 * returned fewer playable songs than its allocated quota.
 */
internal fun interleaveProportionalTracks(
    tracksByLanguage: Map<String, List<Song>>,
    quotas: Map<String, Int>,
    fallbackSongs: List<Song> = emptyList(),
    limit: Int = HOME_LIBRARY_LIMIT,
): List<Song> {
    if (limit <= 0) return emptyList()
    val result = ArrayList<Song>(limit)
    val seenIds = HashSet<String>(limit * 2)
    val buckets = quotas.entries.mapNotNull { (lang, quota) ->
        if (quota <= 0) null
        else {
            val songs = tracksByLanguage[lang].orEmpty()
            if (songs.isEmpty()) null else MutableBucket(lang, quota, songs)
        }
    }

    while (result.size < limit) {
        val candidates = buckets.filter { it.emitted < it.quota && it.nextIndex < it.songs.size }
        if (candidates.isEmpty()) break
        val chosen = candidates.minWith(
            compareBy<MutableBucket> { it.emitted.toDouble() / it.quota.toDouble() }
                .thenByDescending { it.quota }
                .thenBy { it.language },
        )
        var picked: Song? = null
        while (chosen.nextIndex < chosen.songs.size) {
            val candidate = chosen.songs[chosen.nextIndex++]
            if (seenIds.add(candidate.id)) {
                picked = candidate
                break
            }
        }
        if (picked != null) {
            chosen.emitted++
            result += picked
        }
    }

    if (result.size < limit) {
        // Fill remaining capacity from leftover fetched bucket tracks first (maintaining language diversity),
        // then from explicit fallback songs.
        var progressed = true
        while (result.size < limit && progressed) {
            progressed = false
            for (bucket in buckets) {
                if (result.size >= limit) break
                while (bucket.nextIndex < bucket.songs.size) {
                    val candidate = bucket.songs[bucket.nextIndex++]
                    if (seenIds.add(candidate.id)) {
                        result += candidate
                        progressed = true
                        break
                    }
                }
            }
        }
        for (song in fallbackSongs) {
            if (result.size >= limit) break
            if (seenIds.add(song.id)) {
                result += song
            }
        }
    }

    return result
}

private class MutableBucket(
    val language: String,
    val quota: Int,
    val songs: List<Song>,
    var nextIndex: Int = 0,
    var emitted: Int = 0,
)
