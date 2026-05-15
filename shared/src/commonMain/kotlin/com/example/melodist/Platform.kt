package com.example.melodist

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform