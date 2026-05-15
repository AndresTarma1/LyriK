package com.example.melodist.data.local

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object Keys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
    val MINIMIZE_TRAY = booleanPreferencesKey("minimize_tray")
}