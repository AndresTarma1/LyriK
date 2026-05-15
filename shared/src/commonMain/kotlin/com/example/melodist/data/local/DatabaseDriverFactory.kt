package com.example.melodist.data.local

import app.cash.sqldelight.db.SqlDriver

/**
 * Punto de extension multiplataforma para elegir el driver SQLDelight por target.
 */
expect object DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

