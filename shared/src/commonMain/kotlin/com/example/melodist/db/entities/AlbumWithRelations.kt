package com.example.melodist.db.entities

/**
 * An album with its related artists.
 */
data class AlbumWithArtists(
    val album: AlbumEntity,
    val artists: List<ArtistEntity> = emptyList(),
    val songCountListened: Int? = 0,
    val timeListened: Long? = 0
) : LocalItem() {
    override val id: String get() = album.id
    override val title: String get() = album.title
    override val thumbnailUrl: String? get() = album.thumbnailUrl
}

/**
 * An album with its artists and all songs.
 */
data class AlbumWithSongs(
    val album: AlbumEntity,
    val artists: List<ArtistEntity> = emptyList(),
    val songs: List<SongWithRelations> = emptyList()
)

