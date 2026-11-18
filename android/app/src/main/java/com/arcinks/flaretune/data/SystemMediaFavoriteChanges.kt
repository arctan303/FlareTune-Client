package com.arcinks.flaretune.data

import com.arcinks.flaretune.model.PlaylistDetail
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Process-local acknowledgements; no cookie, media URL or private playlist is written to disk. */
class SystemMediaFavoriteChanges {
    data class Update(val connection: ActiveConnection, val detail: PlaylistDetail)
    private val events = MutableSharedFlow<Update>(extraBufferCapacity = 8)
    val updates: SharedFlow<Update> = events.asSharedFlow()

    fun publish(connection: ActiveConnection, detail: PlaylistDetail) {
        if (connection.network.isActive && !connection.auth.mustChangePassword && detail.playlist.kind == "favorite")
            events.tryEmit(Update(connection, detail))
    }
}
