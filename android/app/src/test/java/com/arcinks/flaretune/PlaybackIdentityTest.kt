package com.arcinks.flaretune

import com.arcinks.flaretune.model.Song
import com.arcinks.flaretune.model.isCurrentAlbumTrack
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackIdentityTest {
    private val album = "yt:album:MPREb_first"
    private val otherAlbum = "yt:album:MPREb_second"
    private val track = Song(
        id = "yt:abcdefghijk", title = "Same title", artist = "Same artist", album = "First release",
        audioUrl = "https://music.example/media/audio", coverUrl = "https://music.example/media/first-cover",
        source = "youtube", albumId = album,
    )

    @Test fun differentUploadsWithTheSameTitleAreNotCurrent() {
        assertFalse(track.isCurrentAlbumTrack(track.copy(id = "yt:lmnopqrstuv"), album))
    }

    @Test fun theSameUploadInAnotherReleaseIsNotCurrent() {
        assertFalse(track.isCurrentAlbumTrack(track.copy(albumId = otherAlbum), album))
    }

    @Test fun matchingUploadAndReleaseStayCurrentWhenTheCoverChanges() {
        assertTrue(track.isCurrentAlbumTrack(track.copy(coverUrl = "https://music.example/media/new-cover"), album))
    }

    @Test fun missingPlaybackReleaseDoesNotClaimASpecificRelease() {
        assertFalse(track.isCurrentAlbumTrack(track.copy(albumId = null), album))
        assertFalse(track.isCurrentAlbumTrack(track.copy(albumId = ""), album))
        assertFalse(track.isCurrentAlbumTrack(null, album))
    }

    @Test fun authoritativeDetailMembershipCanSupplyLegacyRowContext() {
        assertTrue(track.copy(albumId = null).isCurrentAlbumTrack(track, album))
    }

    @Test fun contradictoryRowContextOrMissingIdentityNeverMatches() {
        assertFalse(track.copy(albumId = otherAlbum).isCurrentAlbumTrack(track, album))
        assertFalse(track.isCurrentAlbumTrack(track, otherAlbum))
        assertFalse(track.isCurrentAlbumTrack(track, ""))
        assertFalse(track.copy(id = "").isCurrentAlbumTrack(track.copy(id = ""), album))
    }
}
