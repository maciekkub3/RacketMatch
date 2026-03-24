package com.racketmatch

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform