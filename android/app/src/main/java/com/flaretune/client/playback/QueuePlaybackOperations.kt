package com.flaretune.client.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.flaretune.client.ui.PlaybackSnapshot

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
    return PlaybackSnapshot(
        current = currentMediaItem?.song(), queue = queue,
        currentIndex = if (queue.isEmpty()) -1 else currentMediaItemIndex,
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
