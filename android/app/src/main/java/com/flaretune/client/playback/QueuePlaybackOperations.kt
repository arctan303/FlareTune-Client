package com.flaretune.client.playback

import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import com.flaretune.client.data.putSongRelations
import com.flaretune.client.data.readSongRelations
import com.flaretune.client.model.Song
import com.flaretune.client.model.SongRelations
import com.flaretune.client.model.relations
import com.flaretune.client.ui.PlaybackSnapshot
import org.json.JSONObject

internal const val PLAYBACK_PLAY_SINGLE_NEXT = "com.flaretune.client.PLAY_SINGLE_NEXT"

/** Track navigation deliberately does not use Media3's restart-current threshold. */
internal fun previousTrack(player: Player) {
    if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() else player.seekTo(0)
}

internal fun removePlayerQueueItem(player: Player, index: Int) {
    if (index in 0 until player.mediaItemCount) player.removeMediaItem(index)
}

internal fun movePlayerQueueItem(player: Player, fromIndex: Int, toIndex: Int) {
    if (fromIndex != toIndex && fromIndex in 0 until player.mediaItemCount && toIndex in 0 until player.mediaItemCount) {
        // Let the native Timeline preserve the active item and its position.
        player.moveMediaItem(fromIndex, toIndex)
    }
}

internal fun restorePlayerQueue(
    player: Player,
    items: List<MediaItem>,
    currentIndex: Int,
    positionMs: Long,
    shuffle: Boolean,
    repeatMode: Int,
) {
    if (items.isEmpty() || currentIndex !in items.indices || repeatMode !in Player.REPEAT_MODE_OFF..Player.REPEAT_MODE_ALL) return
    // Disable playback before replacing a possibly active timeline. Preparation stays silent.
    player.playWhenReady = false
    player.setMediaItems(items, currentIndex, positionMs.coerceAtLeast(0))
    player.shuffleModeEnabled = shuffle
    player.repeatMode = repeatMode
    player.prepare()
}

internal fun Player.playbackSnapshot(): PlaybackSnapshot {
    val queue = (0 until mediaItemCount).map { getMediaItemAt(it).song() }
    val prevIndex = if (queue.isEmpty()) null else previousMediaItemIndex.takeIf { it in queue.indices } ?: C.INDEX_UNSET
    val nextIndex = if (queue.isEmpty()) null else nextMediaItemIndex.takeIf { it in queue.indices } ?: C.INDEX_UNSET
    return PlaybackSnapshot(
        current = currentMediaItem?.song(), queue = queue,
        currentIndex = if (queue.isEmpty()) -1 else currentMediaItemIndex,
        previousQueueIndex = prevIndex,
        nextQueueIndex = nextIndex,
        playing = isPlaying, buffering = playWhenReady && playbackState == Player.STATE_BUFFERING,
        positionMs = currentPosition.coerceAtLeast(0),
        durationMs = duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0,
        shuffle = shuffleModeEnabled, repeatMode = repeatMode, failed = playerError != null, volume = volume,
    )
}

/** Relocate selected IDs without restarting or replacing the active native timeline. */
internal fun enqueuePlayerSongs(player: Player, items: List<MediaItem>, next: Boolean) {
    val currentId = player.currentMediaItem?.mediaId
    val targets = items.distinctBy { it.mediaId }.filter { it.mediaId != currentId }
    if (targets.isEmpty()) return
    val ids = targets.map { it.mediaId }.toSet()
    for (index in player.mediaItemCount - 1 downTo 0) {
        if (index != player.currentMediaItemIndex && player.getMediaItemAt(index).mediaId in ids)
            player.removeMediaItem(index)
    }
    if (player.mediaItemCount == 0) {
        player.setMediaItems(targets)
        player.playWhenReady = next
        player.prepare()
        if (next) player.play()
        return
    }
    val insertion = if (next) (player.currentMediaItemIndex + 1).coerceIn(0, player.mediaItemCount) else player.mediaItemCount
    player.addMediaItems(insertion, targets)
}

/** Build a post-insertion shuffle permutation placing [insertionIndex] immediately after [currentIndex]. */
internal fun buildShuffleOrderAfterPlayNext(player: Player, currentIndex: Int, insertionIndex: Int): IntArray? {
    val count = player.mediaItemCount
    if (count <= 0) return null
    val timeline = player.currentTimeline
    val oldOrder = ArrayList<Int>(count)
    val seen = HashSet<Int>(count)
    if (!timeline.isEmpty && timeline.windowCount == count) {
        var cursor = timeline.getFirstWindowIndex(true)
        while (cursor != C.INDEX_UNSET && oldOrder.size < count && seen.add(cursor)) {
            oldOrder += cursor
            cursor = timeline.getNextWindowIndex(cursor, Player.REPEAT_MODE_OFF, true)
        }
    }
    for (idx in 0 until count) {
        if (seen.add(idx)) oldOrder += idx
    }
    val shifted = oldOrder.mapTo(ArrayList(count + 1)) { if (it >= insertionIndex) it + 1 else it }
    val pos = shifted.indexOf(currentIndex)
    if (pos >= 0) shifted.add(pos + 1, insertionIndex) else shifted.add(insertionIndex)
    return shifted.toIntArray()
}

internal fun Song.toSingleNextBundle(): Bundle = Bundle().apply {
    putString("id", id)
    putString("title", title)
    putString("artist", artist)
    putString(EXTRA_ORIGINAL_TITLE, title)
    putString(EXTRA_ORIGINAL_ARTIST, artist)
    putString("album", album)
    putDouble("duration_seconds", durationSeconds)
    putString("audio_url", audioUrl)
    putString("cover_url", coverUrl)
    putString("language", language)
    putString("song_relations_v1", JSONObject().putSongRelations(id, relations).toString())
}

internal fun Bundle.toSingleNextSong(): Song? {
    val id = getString("id")?.takeIf(String::isNotBlank) ?: return null
    val audioUrl = getString("audio_url")?.takeIf(String::isNotBlank) ?: return null
    val links = try {
        getString("song_relations_v1")?.takeIf { it.length <= 64 * 1024 }
            ?.let { JSONObject(it).readSongRelations(id) } ?: SongRelations()
    } catch (_: Exception) {
        SongRelations()
    }
    val rawTitle = if (containsKey(EXTRA_ORIGINAL_TITLE)) {
        getString(EXTRA_ORIGINAL_TITLE)?.takeIf { it.isNotEmpty() } ?: getString("title") ?: id
    } else {
        getString("title") ?: id
    }
    val rawArtist = if (containsKey(EXTRA_ORIGINAL_ARTIST)) {
        getString(EXTRA_ORIGINAL_ARTIST) ?: ""
    } else {
        getString("artist") ?: ""
    }
    return Song(
        id = id,
        title = rawTitle,
        artist = rawArtist,
        album = getString("album") ?: "",
        durationSeconds = getDouble("duration_seconds", 0.0),
        audioUrl = audioUrl,
        coverUrl = getString("cover_url"),
        language = getString("language"),
        source = links.source,
        artists = links.artists,
        artistId = links.artistId,
        albumId = links.albumId,
    )
}

/** Insert a single track right after the active item and play it without disturbing existing queue entries. */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
internal fun playSingleAsNext(player: Player, item: MediaItem): Boolean {
    val count = player.mediaItemCount
    val currentIndex = player.currentMediaItemIndex
    if (count == 0 || currentIndex !in 0 until count) {
        player.setMediaItems(listOf(item), 0, 0L)
        player.prepare()
        player.play()
        return true
    }
    val insertion = (currentIndex + 1).coerceIn(0, count)
    val exo = (player as? PersistentTransportPlayer)?.delegateExoPlayer ?: (player as? ExoPlayer)
    val nextShuffleOrder = if (exo != null && player.shuffleModeEnabled) {
        buildShuffleOrderAfterPlayNext(player, currentIndex, insertion)
    } else {
        null
    }
    player.addMediaItem(insertion, item)
    if (nextShuffleOrder != null) {
        exo?.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(nextShuffleOrder, System.nanoTime()))
    }
    player.seekToDefaultPosition(insertion)
    if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
        player.prepare()
    }
    player.play()
    return true
}
