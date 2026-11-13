package com.flaretune.client.playback

import android.content.Context
import android.os.Bundle
import android.os.Process
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.flaretune.client.R
import com.flaretune.client.data.ActiveConnection
import com.flaretune.client.data.SystemMediaFavoriteApi
import com.flaretune.client.data.SystemMediaFavoriteChanges
import com.flaretune.client.data.SystemMediaFavoriteResult
import com.flaretune.client.data.SystemMediaFavorites
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal const val SYSTEM_MEDIA_CYCLE_ORDER = "com.flaretune.client.CYCLE_PLAYBACK_ORDER"

internal enum class SystemMediaOrder { SEQUENTIAL, REPEAT_ALL, REPEAT_ONE, SHUFFLE }

internal fun systemMediaOrder(shuffle: Boolean, repeatMode: Int): SystemMediaOrder = when {
    shuffle -> SystemMediaOrder.SHUFFLE
    repeatMode == Player.REPEAT_MODE_ONE -> SystemMediaOrder.REPEAT_ONE
    repeatMode == Player.REPEAT_MODE_ALL -> SystemMediaOrder.REPEAT_ALL
    else -> SystemMediaOrder.SEQUENTIAL
}

internal fun cycleSystemMediaOrder(player: Player) {
    when (systemMediaOrder(player.shuffleModeEnabled, player.repeatMode)) {
        SystemMediaOrder.SEQUENTIAL -> { player.shuffleModeEnabled = false; player.repeatMode = Player.REPEAT_MODE_ALL }
        SystemMediaOrder.REPEAT_ALL -> { player.shuffleModeEnabled = false; player.repeatMode = Player.REPEAT_MODE_ONE }
        SystemMediaOrder.REPEAT_ONE -> { player.shuffleModeEnabled = true; player.repeatMode = Player.REPEAT_MODE_OFF }
        SystemMediaOrder.SHUFFLE -> { player.shuffleModeEnabled = false; player.repeatMode = Player.REPEAT_MODE_OFF }
    }
}

internal fun mayUseSystemMediaCommands(appUid: Int, uid: Int,
    trusted: Boolean, packageVerified: Boolean, packageName: String): Boolean =
    uid == appUid || (trusted &&
        (uid == Process.SYSTEM_UID || (packageVerified && packageName == "com.android.systemui")))

/** Adds two optional system actions without replacing Media3's standard transport commands. */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
internal class SystemMediaControls(
    private val context: Context,
    private val connection: ActiveConnection,
    private val scope: CoroutineScope,
    private val changes: SystemMediaFavoriteChanges,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val isCurrent: () -> Boolean,
) : MediaSession.Callback {
    private val orderCommand = SessionCommand(SYSTEM_MEDIA_CYCLE_ORDER, Bundle.EMPTY)
    private val favorites = SystemMediaFavorites(scope, SystemMediaFavoriteApi(connection, ioDispatcher), ::current) {
        changes.publish(connection, it)
    }
    private var session: MediaSession? = null
    private val jobs = mutableListOf<Job>()
    private var closed = false

    val sessionExtras: Bundle get() = Bundle().apply {
        // Never let a custom favorite/order button replace an unavailable first/last skip slot.
        putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_PREV, true)
        putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_NEXT, true)
    }

    private fun current() = !closed && connection.network.isActive && !connection.auth.mustChangePassword && isCurrent()

    // Media3's notification-controller hint alone is not caller authentication. Its real proxy uses our UID.
    private fun trusted(controller: MediaSession.ControllerInfo) = mayUseSystemMediaCommands(
        Process.myUid(), controller.uid, controller.isTrusted,
        controller.isPackageNameVerified, controller.packageName)

    private fun commands(session: MediaSession, controller: MediaSession.ControllerInfo): SessionCommands {
        val result = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
        if (current() && trusted(controller)) {
            result.add(orderCommand)
            result.add(SessionCommand(favorites.state.value.commandId, Bundle.EMPTY))
        }
        return result.build()
    }

    override fun onConnectAsync(session: MediaSession, controller: MediaSession.ControllerInfo): ListenableFuture<MediaSession.ConnectionResult> {
        if (!current()) return Futures.immediateFuture(MediaSession.ConnectionResult.reject())
        return Futures.immediateFuture(MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
            .setAvailableSessionCommands(commands(session, controller))
            .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
            .setMediaButtonPreferences(buttons(session.player))
            .setSessionExtras(sessionExtras)
            .build())
    }

    override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
        if (!current() || !trusted(controller)) return Futures.immediateFuture(SessionResult(SessionError.ERROR_PERMISSION_DENIED))
        if (customCommand.customAction == SYSTEM_MEDIA_CYCLE_ORDER) {
            if (session.player.currentMediaItem == null) return Futures.immediateFuture(SessionResult(SessionError.ERROR_INVALID_STATE))
            cycleSystemMediaOrder(session.player)
            publish()
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
        // The opaque action is issued for one observed track/state. Request arguments cannot choose a song or account.
        if (customCommand.customAction != favorites.state.value.commandId ||
            favorites.state.value.songId != session.player.currentMediaItem?.mediaId)
            return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
        val result = SettableFuture.create<SessionResult>()
        val work = scope.launch {
            val outcome = favorites.perform(customCommand.customAction)
            if (!current()) { result.set(SessionResult(SessionError.ERROR_SESSION_DISCONNECTED)); return@launch }
            result.set(SessionResult(when (outcome) {
                SystemMediaFavoriteResult.SUCCESS -> SessionResult.RESULT_SUCCESS
                SystemMediaFavoriteResult.REJECTED -> SessionError.ERROR_INVALID_STATE
                SystemMediaFavoriteResult.FAILED -> SessionError.ERROR_IO
            }))
            if (outcome == SystemMediaFavoriteResult.FAILED)
                session.sendError(controller, SessionError(SessionError.ERROR_IO, context.getString(R.string.ft_system_favorite_failed)))
        }
        work.invokeOnCompletion { error ->
            if (!result.isDone) {
                if (error is CancellationException) result.cancel(false)
                else result.set(SessionResult(SessionError.ERROR_UNKNOWN))
            }
        }
        return result
    }

    fun attach(session: MediaSession) {
        this.session = session
        jobs += scope.launch { favorites.state.collect { publish() } }
        jobs += scope.launch {
            changes.updates.collect { update ->
                if (current() && update.connection.network === connection.network &&
                    update.connection.auth.account.id == connection.auth.account.id) favorites.adopt(update.detail)
            }
        }
        observePlayer(session.player)
    }

    fun observePlayer(player: Player) {
        if (!current()) return
        favorites.observeSong(player.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() })
        publish()
    }

    private fun publish() {
        val currentSession = session ?: return
        if (!current()) return
        currentSession.connectedControllers.forEach { controller ->
            currentSession.setAvailableCommands(controller, commands(currentSession, controller),
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
        }
        currentSession.setMediaButtonPreferences(buttons(currentSession.player))
    }

    private fun buttons(player: Player): List<CommandButton> {
        val order = systemMediaOrder(player.shuffleModeEnabled, player.repeatMode)
        val (modeIcon, modeText) = when (order) {
            SystemMediaOrder.SEQUENTIAL -> CommandButton.ICON_REPEAT_OFF to R.string.ft_system_order_sequential
            SystemMediaOrder.REPEAT_ALL -> CommandButton.ICON_REPEAT_ALL to R.string.ft_system_order_repeat_all
            SystemMediaOrder.REPEAT_ONE -> CommandButton.ICON_REPEAT_ONE to R.string.ft_system_order_repeat_one
            SystemMediaOrder.SHUFFLE -> CommandButton.ICON_SHUFFLE_ON to R.string.ft_system_order_shuffle
        }
        val favorite = favorites.state.value
        val favoriteText = when {
            favorite.loading -> R.string.ft_system_favorite_loading
            favorite.busy -> R.string.ft_system_favorite_saving
            favorite.failed && favorite.saved == null -> R.string.ft_system_favorite_retry_load
            favorite.failed -> R.string.ft_system_favorite_retry
            favorite.saved == true -> R.string.ft_system_favorite_remove
            else -> R.string.ft_system_favorite_add
        }
        return listOf(
            CommandButton.Builder(modeIcon).setSessionCommand(orderCommand)
                .setDisplayName(context.getString(modeText)).setSlots(CommandButton.SLOT_OVERFLOW)
                .setEnabled(player.currentMediaItem != null).build(),
            CommandButton.Builder(if (favorite.saved == true) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
                .setSessionCommand(SessionCommand(favorite.commandId, Bundle.EMPTY))
                .setDisplayName(context.getString(favoriteText)).setSlots(CommandButton.SLOT_OVERFLOW)
                .setEnabled(favorite.songId != null && !favorite.loading && !favorite.busy).build(),
        )
    }

    fun close() {
        closed = true
        jobs.forEach { it.cancel() }
        jobs.clear()
        favorites.close()
        session = null
    }
}
