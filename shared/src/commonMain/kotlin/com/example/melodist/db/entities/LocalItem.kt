package com.example.melodist.db.entities

/**
 * Base sealed class for all local items.
 */
sealed class LocalItem {
    abstract val id: String
    abstract val title: String
    abstract val thumbnailUrl: String?
}

