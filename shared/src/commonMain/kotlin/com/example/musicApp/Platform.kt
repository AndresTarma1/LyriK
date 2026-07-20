package com.example.musicApp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform