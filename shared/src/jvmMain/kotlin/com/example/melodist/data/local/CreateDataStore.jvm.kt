package com.example.melodist.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import java.io.File
import com.example.melodist.platform.AppPaths

fun createDataStore(): DataStore<Preferences> = createDataStore(
    producePath = {
        val dir = File(AppPaths.dataRoot, "data")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, dataStoreFileName)
        file.absolutePath
    }
)