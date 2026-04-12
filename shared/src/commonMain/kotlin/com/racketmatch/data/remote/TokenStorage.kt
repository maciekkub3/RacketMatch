package com.racketmatch.data.remote

import kotlinx.coroutines.flow.StateFlow

interface TokenStorage {
    var accessToken: String?
    var refreshToken: String?
    var currentUserId: String?
    var isNewUser: Boolean
    var isOnboardingComplete: Boolean
    var isDarkTheme: Boolean
    var isCoach: Boolean
    var coachModeActive: Boolean
    var hasPlayerProfile: Boolean
    val loginVersionFlow: StateFlow<Int>
    val matchesVersionFlow: StateFlow<Int>
    val profileVersionFlow: StateFlow<Int>
    val friendsVersionFlow: StateFlow<Int>
    val dmVersionFlow: StateFlow<Int>
    fun saveTokens(access: String, refresh: String)
    fun incrementMatchesVersion()
    fun incrementProfileVersion()
    fun incrementFriendsVersion()
    fun incrementDmVersion()
    fun clear()
}

open class InMemoryTokenStorage : TokenStorage {
    override var accessToken: String? = null
    override var refreshToken: String? = null
    override var currentUserId: String? = null
    override var isNewUser: Boolean = false
    override var isOnboardingComplete: Boolean = false
    override var isDarkTheme: Boolean = false
    override var isCoach: Boolean = false
    override var coachModeActive: Boolean = false
    override var hasPlayerProfile: Boolean = true

    private val _loginVersionFlow = kotlinx.coroutines.flow.MutableStateFlow(0)
    override val loginVersionFlow: StateFlow<Int> = _loginVersionFlow

    private val _matchesVersionFlow = kotlinx.coroutines.flow.MutableStateFlow(0)
    override val matchesVersionFlow: StateFlow<Int> = _matchesVersionFlow

    private val _profileVersionFlow = kotlinx.coroutines.flow.MutableStateFlow(0)
    override val profileVersionFlow: StateFlow<Int> = _profileVersionFlow

    private val _friendsVersionFlow = kotlinx.coroutines.flow.MutableStateFlow(0)
    override val friendsVersionFlow: StateFlow<Int> = _friendsVersionFlow

    private val _dmVersionFlow = kotlinx.coroutines.flow.MutableStateFlow(0)
    override val dmVersionFlow: StateFlow<Int> = _dmVersionFlow

    override fun saveTokens(access: String, refresh: String) {
        accessToken = access
        refreshToken = refresh
        _loginVersionFlow.value++
    }

    override fun incrementMatchesVersion() {
        _matchesVersionFlow.value++
    }

    override fun incrementProfileVersion() {
        _profileVersionFlow.value++
    }

    override fun incrementFriendsVersion() {
        _friendsVersionFlow.value++
    }

    override fun incrementDmVersion() {
        _dmVersionFlow.value++
    }

    override fun clear() {
        accessToken = null
        refreshToken = null
        currentUserId = null
        isCoach = false
        hasPlayerProfile = true
        coachModeActive = false
        isNewUser = false
        isOnboardingComplete = false
    }
}
