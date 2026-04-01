package com.racketmatch.data.remote

import kotlinx.coroutines.flow.StateFlow

interface TokenStorage {
    var accessToken: String?
    var refreshToken: String?
    var currentUserId: String?
    val loginVersionFlow: StateFlow<Int>
    val matchesVersionFlow: StateFlow<Int>
    fun saveTokens(access: String, refresh: String)
    fun incrementMatchesVersion()
    fun clear()
}

class InMemoryTokenStorage : TokenStorage {
    @Volatile override var accessToken: String? = null
    @Volatile override var refreshToken: String? = null
    @Volatile override var currentUserId: String? = null

    private val _loginVersionFlow = kotlinx.coroutines.flow.MutableStateFlow(0)
    override val loginVersionFlow: StateFlow<Int> = _loginVersionFlow

    private val _matchesVersionFlow = kotlinx.coroutines.flow.MutableStateFlow(0)
    override val matchesVersionFlow: StateFlow<Int> = _matchesVersionFlow

    override fun saveTokens(access: String, refresh: String) {
        accessToken = access
        refreshToken = refresh
        _loginVersionFlow.value++
    }

    override fun incrementMatchesVersion() {
        _matchesVersionFlow.value++
    }

    override fun clear() {
        accessToken = null
        refreshToken = null
        currentUserId = null
    }
}
