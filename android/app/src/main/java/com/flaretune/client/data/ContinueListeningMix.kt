package com.flaretune.client.data

import com.flaretune.client.model.RankedSong
import com.flaretune.client.model.Song
import com.flaretune.client.model.isYouTube
import kotlin.math.ln
import kotlin.random.Random

/** Display names are not identities: separate recordings/editions keep their stable IDs. */
private fun Song.mixIdentity() = (if (isYouTube) "youtube" else source?.takeIf { it.isNotBlank() } ?: "local") to id

/** Weighted sampling without replacement; count growth is dampened so older hits cannot monopolize. */
private fun <T> weightedOrder(values: List<T>, random: Random, weight: (T, Int) -> Double): List<T> =
    values.mapIndexed { index, value ->
        value to -ln(1.0 - random.nextDouble()) / weight(value, index).coerceAtLeast(0.001)
    }.sortedBy { it.second }.map { it.first }

internal fun buildContinueListeningMix(
    recentStats: List<RankedSong>,
    allTimeStats: List<RankedSong>,
    favorites: List<Song>,
    limit: Int = 12,
    random: Random = Random.Default,
    recentSearchSongs: List<Song> = emptyList(),
    recentDayStats: List<RankedSong> = emptyList(),
): List<Song> {
    if (limit <= 0) return emptyList()
    fun rankedPool(rows: List<RankedSong>, minimum: Long): List<Song> {
        val valid = rows.filter { it.playCount >= minimum && it.song.canPlay && it.song.id.isNotBlank() }
            .sortedByDescending { it.playCount }.distinctBy { it.song.mixIdentity() }
        return weightedOrder(valid, random) { row, _ -> 1.0 + ln(1.0 + row.playCount.toDouble()) }.map { it.song }
    }
    val recent = (rankedPool(recentDayStats, 2) + rankedPool(recentStats, 2)).distinctBy { it.mixIdentity() }
    val liked = favorites.filter { it.canPlay && it.id.isNotBlank() }.distinctBy { it.mixIdentity() }.shuffled(random)
    val frequent = rankedPool(allTimeStats, 1)
    val searches = recentSearchSongs.filter { it.canPlay && it.id.isNotBlank() }.distinctBy { it.mixIdentity() }
    val search = weightedOrder(searches, random) { _, index -> (searches.size - index).toDouble() }
    val pools = listOf(recent, liked, frequent, search)
    val weights = listOf(4, 3, 3, 2)
    val quotas = weights.map { (limit.toLong() * it / 12).toInt() }.toMutableList()
    val remainders = weights.indices.sortedWith(compareByDescending<Int> { limit.toLong() * weights[it] % 12 }.thenBy { it })
    repeat(limit - quotas.sum()) { quotas[remainders[it]]++ }
    val positions = IntArray(pools.size)
    val picked = mutableListOf<Song>()
    val seen = mutableSetOf<Pair<String, String>>()
    fun takeNext(bucket: Int): Boolean {
        while (positions[bucket] < pools[bucket].size) {
            val song = pools[bucket][positions[bucket]++]
            if (seen.add(song.mixIdentity())) { picked += song; return true }
        }
        return false
    }
    // Duplicates never consume a bucket's quota: advance through its remaining candidates.
    pools.indices.forEach { bucket -> repeat(quotas[bucket]) { if (picked.size < limit) takeNext(bucket) } }
    // Exhausted buckets release their places. Stop when every unique candidate is exhausted.
    while (picked.size < limit) {
        var added = false
        for (bucket in pools.indices) if (picked.size < limit && takeNext(bucket)) added = true
        if (!added) break
    }
    return picked.shuffled(random)
}
