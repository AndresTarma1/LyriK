package com.example.melodist.db.entities

/**
 * An artist with song count and listen time.
 */
data class ArtistWithStats(
    val artist: ArtistEntity,
    val songCount: Int = 0,
    val timeListened: Int? = 0
) : LocalItem() {
    override val id: String get() = artist.id
    override val title: String get() = artist.name
    override val thumbnailUrl: String? get() = artist.thumbnailUrl
}

