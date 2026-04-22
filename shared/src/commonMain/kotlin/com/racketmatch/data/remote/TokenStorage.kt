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
    /**
     * Comma-separated match IDs the current user has already seen the result
     * reveal for. Prevents the Today "nowy wynik" hero from re-showing for
     * the same match on every app open. Cleared on logout.
     */
    var seenResultMatchIds: String
    val loginVersionFlow: StateFlow<Int>
    val matchesVersionFlow: StateFlow<Int>
    val profileVersionFlow: StateFlow<Int>
    val friendsVersionFlow: StateFlow<Int>
    val dmVersionFlow: StateFlow<Int>
    val bookingsVersionFlow: StateFlow<Int>
    /** For login/register. Bumps loginVersionFlow → all ViewModels reload. */
    fun saveTokens(access: String, refresh: String)
    /**
     * For silent token refresh from the Ktor Auth plugin. Must NOT bump
     * loginVersionFlow — the user's identity didn't change, only the bearer
     * did. Bumping here triggered simultaneous full reloads across every
     * screen, which caused the cascade of load errors (ranking / explore /
     * today / matches all failing) that the user would see right after a
     * propose/withdraw, and that healed only via logout + login.
     */
    fun updateTokens(access: String, refresh: String)
    fun incrementMatchesVersion()
    fun incrementProfileVersion()
    fun incrementFriendsVersion()
    fun incrementDmVersion()
    fun incrementBookingsVersion()
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
    override var seenResultMatchIds: String = ""

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

    private val _bookingsVersionFlow = kotlinx.coroutines.flow.MutableStateFlow(0)
    override val bookingsVersionFlow: StateFlow<Int> = _bookingsVersionFlow

    override fun saveTokens(access: String, refresh: String) {
        accessToken = access
        refreshToken = refresh
        _loginVersionFlow.value++
    }

    override fun updateTokens(access: String, refresh: String) {
        accessToken = access
        refreshToken = refresh
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

    override fun incrementBookingsVersion() {
        _bookingsVersionFlow.value++
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
        // Note: seenResultMatchIds intentionally survives logout.
        // Match UUIDs are user-scoped (you only see your own matches), so
        // there's no data-leak risk, and clearing them meant the "Nowy
        // wynik" hero re-triggered on every re-login — treating every
        // already-seen result as brand-new.
    }
}
