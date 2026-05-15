package com.example.melodist.data.account

import kotlinx.coroutines.flow.StateFlow

expect object AccountManager {
    val loginState: StateFlow<Boolean>
    val isLoggedIn: Boolean
    
    fun init()
    fun setCookie(cookie: String)
    fun clearCookie()
    fun getCookie(): String?
    fun diagnose(cookie: String): List<String>
}
