package com.example.musicApp.data.account

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.StateFlow

expect object AccountManager {
    val loginState: StateFlow<Boolean>
    val isLoggedIn: Boolean
    
    fun init(dataStore: DataStore<Preferences>)
    fun setCookie(cookie: String)
    fun clearCookie()
    fun getCookie(): String?
    fun diagnose(cookie: String): List<String>
}
