package com.arcinks.flaretune

import com.arcinks.flaretune.data.buildContinueListeningMix
import com.arcinks.flaretune.model.RankedSong
import com.arcinks.flaretune.model.Song
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ContinueListeningMixTest {
    private fun song(id: String, title: String = id, source: String? = null) = Song(
        id = id, title = title, artist = "Fixture artist",
        audioUrl = "https://music.example/media/$id.mp3", source = source,
    )
    private fun ranked(id: String, count: Long = 3L) = RankedSong(song(id), count, 1_800_000_000_000L)

    @Test fun aFullDisjointPoolHonorsTheFourThreeThreeTwoMix() {
        val mixed = buildContinueListeningMix(
            recentStats = (0 until 30).map { ranked("recent-$it") },
            allTimeStats = (0 until 30).map { ranked("frequent-$it", 20L) },
            favorites = (0 until 30).map { song("favorite-$it") },
            recentSearchSongs = (0 until 10).map { song("search-$it") }, random = Random(81),
        )
        assertEquals(12, mixed.size)
        assertEquals(4, mixed.count { it.id.startsWith("recent-") })
        assertEquals(3, mixed.count { it.id.startsWith("favorite-") })
        assertEquals(3, mixed.count { it.id.startsWith("frequent-") })
        assertEquals(2, mixed.count { it.id.startsWith("search-") })
    }

    @Test fun sharedCandidatesDoNotPreventFillingFromTheUnusedReserve() {
        val shared = (0 until 8).map { song("shared-$it") }
        val reserves = (0 until 18).map { song("reserve-$it") }
        val mixed = buildContinueListeningMix(
            recentStats = shared.map { RankedSong(it, 5, 100) },
            allTimeStats = (shared + reserves).map { RankedSong(it, 40, 100) }, favorites = shared,
            recentSearchSongs = shared, random = Random(19),
        )
        assertEquals(12, mixed.size)
        assertEquals(12, mixed.map { it.id }.distinct().size)
        assertTrue(mixed.any { it.id.startsWith("reserve-") })
        assertTrue(mixed.all { candidate -> candidate.id in (shared + reserves).map { it.id } })
    }

    @Test fun oneAvailableSourceCanTakeTheUnusedSlots() {
        val favorites = (0 until 25).map { song("favorite-$it") }
        val mixed = buildContinueListeningMix(
            recentStats = emptyList(), allTimeStats = emptyList(), favorites = favorites, random = Random(12),
        )
        assertEquals(12, mixed.size)
        assertEquals(12, mixed.map { it.id }.distinct().size)
        assertTrue(mixed.all { it in favorites })
    }

    @Test fun exhaustedPoolsReturnTheirRealUniqueSizeInsteadOfRepeatingSongs() {
        val available = listOf(song("a"), song("b"), song("c"))
        val mixed = buildContinueListeningMix(
            recentStats = available.map { RankedSong(it, 4, 100) },
            allTimeStats = available.reversed().map { RankedSong(it, 18, 100) }, favorites = available,
            recentSearchSongs = available + available, random = Random(9),
        )
        assertEquals(setOf("a", "b", "c"), mixed.map { it.id }.toSet())
        assertEquals(3, mixed.size)
    }

    @Test fun equalTitlesDoNotMergeDifferentStableSongVersions() {
        val original = song("original", title = "Same title")
        val remaster = song("remaster", title = "Same title")
        val live = song("live", title = "Same title")
        val mixed = buildContinueListeningMix(
            recentStats = listOf(RankedSong(original, 4, 100)),
            allTimeStats = listOf(RankedSong(remaster, 10, 100)),
            favorites = listOf(original, live), random = Random(41),
        )
        assertEquals(setOf("original", "remaster", "live"), mixed.map { it.id }.toSet())
        assertEquals(3, mixed.size)
    }

    @Test fun absentAndExplicitSourceMetadataDoNotDuplicateTheSameReference() {
        val local = song("local-one")
        val youtube = song("yt:track:abcdefghijk")
        val mixed = buildContinueListeningMix(
            recentStats = emptyList(),
            allTimeStats = listOf(RankedSong(local, 4, 100), RankedSong(youtube, 3, 100)),
            favorites = listOf(local.copy(source = "local"), youtube.copy(source = "youtube")),
            recentSearchSongs = listOf(local.copy(source = ""), youtube), random = Random(17),
        )
        assertEquals(setOf(local.id, youtube.id), mixed.map { it.id }.toSet())
        assertEquals(2, mixed.size)
    }

    @Test fun sourceOwnedIdsAreNotCollapsedAcrossDifferentProviders() {
        val mixed = buildContinueListeningMix(
            recentStats = emptyList(), allTimeStats = emptyList(),
            favorites = listOf(song("same-reference", source = "local"), song("same-reference", source = "youtube")),
            random = Random(11),
        )
        assertEquals(2, mixed.size)
        assertEquals(setOf("local", "youtube"), mixed.map { it.source }.toSet())
    }

    @Test fun unavailableSongsAndSingleRecentPlaysDoNotInventRepeatCandidates() {
        val unavailable = song("gone").copy(audioUrl = "")
        val single = ranked("heard-once", count = 1)
        val mixed = buildContinueListeningMix(
            recentStats = listOf(RankedSong(unavailable, 99, 100), single), recentDayStats = listOf(single),
            allTimeStats = listOf(RankedSong(unavailable, 999, 100)),
            favorites = listOf(unavailable, song("playable")), recentSearchSongs = listOf(unavailable), random = Random(6),
        )
        assertEquals(listOf("playable"), mixed.map { it.id })
        assertTrue(mixed.all { it.canPlay })
    }

    @Test fun theLastDayRepeatPoolGetsPriorityWithinTheRecentQuota() {
        val today = (0 until 4).map { ranked("today-$it", 2L) }
        val mixed = buildContinueListeningMix(
            recentDayStats = today, recentStats = (0 until 30).map { ranked("week-$it", 20L) },
            allTimeStats = (0 until 20).map { ranked("frequent-$it", 30L) },
            favorites = (0 until 20).map { song("favorite-$it") },
            recentSearchSongs = (0 until 10).map { song("search-$it") }, random = Random(29),
        )
        assertEquals(today.map { it.song.id }.toSet(), mixed.filter { it.id.startsWith("today-") }.map { it.id }.toSet())
        assertFalse(mixed.any { it.id.startsWith("week-") })
    }

    @Test fun arbitraryLimitsAreRespectedWithoutDroppingAvailableUniqueCandidates() {
        val favorites = (0 until 20).map { song("favorite-$it") }
        for (limit in listOf(1, 2, 3, 5, 7, 11, 13)) {
            val mixed = buildContinueListeningMix(
                recentStats = emptyList(), allTimeStats = emptyList(), favorites = favorites,
                limit = limit, random = Random(limit),
            )
            assertEquals("limit=$limit", limit, mixed.size)
            assertEquals("limit=$limit", limit, mixed.map { it.id }.distinct().size)
        }
        for (limit in listOf(0, -1)) {
            assertTrue(buildContinueListeningMix(
                recentStats = emptyList(), allTimeStats = emptyList(), favorites = favorites, limit = limit,
            ).isEmpty())
        }
    }

    @Test fun accumulatedCountsBiasSelectionWithoutFixingTheSameWinnerEveryRefresh() {
        var frequentWins = 0
        repeat(400) { seed ->
            val chosen = buildContinueListeningMix(
                recentStats = emptyList(), allTimeStats = listOf(ranked("often", 9), ranked("sometimes", 1)),
                favorites = emptyList(), limit = 1, random = Random(seed),
            ).single()
            if (chosen.id == "often") frequentWins++
        }
        assertTrue("More often played song should be favored ($frequentWins/400)", frequentWins > 230)
        assertTrue("Selection should not become a fixed top-one list", frequentWins < 400)
    }
}
