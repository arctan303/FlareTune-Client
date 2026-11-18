package com.arcinks.flaretune.model

/** A release-specific row must match both the media upload and the known album context. */
fun Song.isCurrentAlbumTrack(current: Song?, albumContextId: String): Boolean {
    if (id.isBlank() || albumContextId.isBlank() || current?.id != id) return false
    if (current.albumId != albumContextId) return false
    // Membership in an album detail supplies the context when legacy row metadata omits it.
    return albumId.isNullOrBlank() || albumId == albumContextId
}
