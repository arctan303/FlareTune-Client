package com.flaretune.client.playback

import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer.State
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Keeps the system's standard previous/next controls present at music queue boundaries.
 *
 * State and seeks use the same forwarding player, so MediaSession observes the same commands its
 * listeners receive. Queue order, shuffle/repeat and controller authorization remain unchanged.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
internal class PersistentTransportPlayer(player: Player) : ForwardingSimpleBasePlayer(player) {
    override fun getState(): State {
        val state = super.getState()
        val delegate = getPlayer()
        val commands = state.availableCommands.buildUpon()
        if (state.timeline.isEmpty) {
            commands.remove(Player.COMMAND_SEEK_TO_PREVIOUS).remove(Player.COMMAND_SEEK_TO_NEXT)
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM).remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        } else {
            val index = state.currentMediaItemIndex.takeIf { it in 0 until state.timeline.windowCount } ?: 0
            val window = state.timeline.getWindow(index, Timeline.Window())
            // A newly selected finite audio item initially has a non-seekable placeholder timeline.
            // Exo still allows an indexed seek, so keep transport stable while its source resolves.
            val pendingFiniteAudio = window.isPlaceholder &&
                state.availableCommands.contains(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
            if (!window.isLive && !delegate.isPlayingAd && (pendingFiniteAudio ||
                    state.availableCommands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))) {
                // Advertise standard actions without inventing a previous/next item index.
                commands.add(Player.COMMAND_SEEK_TO_PREVIOUS).add(Player.COMMAND_SEEK_TO_NEXT)
            }
        }
        return state.buildUpon().setAvailableCommands(commands.build()).build()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val delegate = getPlayer()
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                if (!delegate.currentTimeline.isEmpty && !delegate.isPlayingAd) {
                    if (delegate.hasPreviousMediaItem() || delegate.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
                        previousTrack(delegate)
                    } else if (delegate.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM)) {
                        delegate.seekTo(delegate.currentMediaItemIndex, 0)
                    }
                }
            }
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                // Native navigation already applies shuffle and repeat-all. At the end, keep the
                // item/position/mode intact instead of silently wrapping or enabling repeat.
                if (!delegate.isPlayingAd && delegate.hasNextMediaItem()) delegate.seekToNextMediaItem()
            }
            else -> return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
        }
        return Futures.immediateVoidFuture()
    }
}
