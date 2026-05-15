package com.example.melodist.db.entities

/**
 * A song with its related artists and album.
 * This replaces the Room @Relation-based Song class.
 */
data class SongWithRelations(
    val song: SongEntity,
    val artists: List<ArtistEntity> = emptyList(),
    val album: AlbumEntity? = null
) : LocalItem() {
    override val id: String get() = song.id
    override val title: String get() = song.title
    override val thumbnailUrl: String? get() = song.thumbnailUrl
    val romanizeLyrics: Boolean get() = song.romanizeLyrics
}

