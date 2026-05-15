package com.example.melodist.db.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.example.melodist.db.MelodistDatabase
import com.example.melodist.db.entities.ArtistEntity
import com.example.melodist.db.entities.ArtistWithStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

private fun Long?.toLocalDateTime(): LocalDateTime? =
    this?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC) }

private fun LocalDateTime?.toEpochMillis(): Long? =
    this?.atZone(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()

private fun mapArtist(
    id: String, name: String, thumbnailUrl: String?, channelId: String?,
    lastUpdateTime: Long, bookmarkedAt: Long?, isLocal: Long
) = ArtistEntity(
    id = id, name = name, thumbnailUrl = thumbnailUrl, channelId = channelId,
    lastUpdateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastUpdateTime), ZoneOffset.UTC),
    bookmarkedAt = bookmarkedAt.toLocalDateTime(), isLocal = isLocal != 0L
)

private fun mapArtistWithStats(
    id: String, name: String, thumbnailUrl: String?, channelId: String?,
    lastUpdateTime: Long, bookmarkedAt: Long?, isLocal: Long,
    songCount: Long, timeListened: Long?
) = ArtistWithStats(
    artist = mapArtist(id, name, thumbnailUrl, channelId, lastUpdateTime, bookmarkedAt, isLocal),
    songCount = songCount.toInt(),
    timeListened = timeListened?.toInt() ?: 0
)

class ArtistDao(private val database: MelodistDatabase) {

    fun allArtists(): Flow<List<ArtistEntity>> =
        database.artistQueries.selectAllArtists(::mapArtist)
            .asFlow()
            .mapToList(Dispatchers.IO)

    fun artistsByPlayTime(): Flow<List<ArtistWithStats>> =
        database.artistQueries.artistsByPlayTime(::mapArtistWithStats)
            .asFlow()
            .mapToList(Dispatchers.IO)

    fun artistsByCreateDate(): Flow<List<ArtistWithStats>> =
        database.artistQueries.artistsByCreateDate(::mapArtistWithStats)
            .asFlow()
            .mapToList(Dispatchers.IO)

    fun artistsByName(): Flow<List<ArtistWithStats>> =
        database.artistQueries.artistsByName(::mapArtistWithStats)
            .asFlow()
            .mapToList(Dispatchers.IO)

    fun artistById(id: String): Flow<ArtistEntity?> =
        database.artistQueries.artistById(id, ::mapArtist)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)

    fun bookmarkedArtists(): Flow<List<ArtistEntity>> =
        database.artistQueries.bookmarkedArtists { id, name, thumbnailUrl, channelId,
                                                    lastUpdateTime, bookmarkedAt, isLocal ->
            mapArtist(id, name, thumbnailUrl, channelId, lastUpdateTime, bookmarkedAt, isLocal)
        }.asFlow().mapToList(Dispatchers.IO)

    suspend fun insertArtist(artist: ArtistEntity) = withContext(Dispatchers.IO) {
        database.artistQueries.insertArtist(
            id = artist.id, name = artist.name, thumbnailUrl = artist.thumbnailUrl,
            channelId = artist.channelId,
            lastUpdateTime = artist.lastUpdateTime.toEpochMillis() ?: System.currentTimeMillis(),
            bookmarkedAt = artist.bookmarkedAt.toEpochMillis(),
            isLocal = if (artist.isLocal) 1L else 0L
        )
    }

    suspend fun updateArtistBookmark(id: String, bookmarkedAt: LocalDateTime?) = withContext(Dispatchers.IO) {
        database.artistQueries.updateArtistBookmark(
            bookmarkedAt = bookmarkedAt.toEpochMillis(), id = id
        )
    }

    suspend fun deleteArtist(id: String) = withContext(Dispatchers.IO) {
        database.artistQueries.deleteArtist(id)
    }
}
