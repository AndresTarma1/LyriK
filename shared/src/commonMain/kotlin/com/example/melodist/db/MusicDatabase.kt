package com.example.melodist.db

/**
 * Desktop-compatible MusicDatabase wrapper around SQLDelight's MelodistDatabase.
 * Replaces the Android Room-based MusicDatabase.
 */
class MusicDatabase(
    val database: MelodistDatabase
) {
    val dao: DatabaseDao = DatabaseDao(database)

    fun <T> transaction(block: MusicDatabase.() -> T): T {
        var result: T? = null
        database.transaction {
            result = block(this@MusicDatabase)
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    suspend fun <T> withTransaction(block: suspend MusicDatabase.() -> T): T {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            var result: T? = null
            database.transaction {
                result = kotlinx.coroutines.runBlocking {
                    block(this@MusicDatabase)
                }
            }
            @Suppress("UNCHECKED_CAST")
            result as T
        }
    }
}

