package com.example.musicApp.di

import com.example.musicApp.data.local.createDataStore
import com.example.musicApp.data.repository.JvmConfigRepository
import com.example.musicApp.data.repository.UserPreferencesRepository
import org.koin.dsl.module

val dataStoreModule = module {
    single { createDataStore() }
    single { UserPreferencesRepository(get()) }
    single { JvmConfigRepository(get()) }
}