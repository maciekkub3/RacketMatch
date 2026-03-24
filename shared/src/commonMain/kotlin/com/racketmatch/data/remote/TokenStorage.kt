package com.racketmatch.data.remote

interface TokenStorage {
    var accessToken: String?
    var refreshToken: String?
    fun saveTokens(access: String, refresh: String)
    fun clear()
}

class InMemoryTokenStorage : TokenStorage {
    override var accessToken: String? = null
    override var refreshToken: String? = null

    override fun saveTokens(access: String, refresh: String) {
        accessToken = access
        refreshToken = refresh
    }

    override fun clear() {
        accessToken = null
        refreshToken = null
    }
}
