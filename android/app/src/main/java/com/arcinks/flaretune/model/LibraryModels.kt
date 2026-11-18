package com.arcinks.flaretune.model

data class PlaylistSummary(
    val id: String,
    val name: String,
    val kind: String,
    val description: String = "",
    val songCount: Int = 0,
    val revision: Long = 0,
    val previewCovers: List<String> = emptyList(),
    val customCoverUrl: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val coverRevision: Long = 0,
)

data class PlaylistSong(val song: Song, val sortOrder: Int = 0, val addedAt: Long = 0) {
    val canPlay: Boolean get() = song.canPlay
}

data class PlaylistDetail(val playlist: PlaylistSummary, val entries: List<PlaylistSong>) {
    val songs: List<Song> get() = entries.map { it.song }.filter { it.canPlay }
    val songIds: List<String> get() = entries.map { it.song.id }
}
