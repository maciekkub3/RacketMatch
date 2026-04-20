package com.racketmatch.data.repository

import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.data.remote.api.AuthApi
import com.racketmatch.data.remote.dto.toDomain
import com.racketmatch.domain.model.AuthResult
import com.racketmatch.domain.model.Sport
import com.racketmatch.domain.repository.AuthRepository

class AuthRepositoryImpl(
    private val authApi: AuthApi,
    private val tokenStorage: TokenStorage
) : AuthRepository {

    override suspend fun login(email: String, password: String): AuthResult {
        val response = authApi.login(email, password)
        // Set identity fields *before* saveTokens() — saveTokens bumps
        // loginVersionFlow, and any VM that reacts to the bump (e.g. to
        // reload data) must read the new currentUserId/isCoach/…, not the
        // previous user's leftovers.
        tokenStorage.currentUserId = response.user.id
        tokenStorage.isCoach = response.user.isCoach
        tokenStorage.hasPlayerProfile = response.user.hasPlayerProfile
        if (response.user.isCoach && !response.user.hasPlayerProfile) tokenStorage.coachModeActive = true
        // Drop any cached bearer in the Auth plugin so the next authed call
        // re-reads tokens from storage (picks up the new tokens we just saved).
        authApi.clearBearerCache()
        tokenStorage.saveTokens(response.accessToken, response.refreshToken)
        return response.toDomain()
    }

    override suspend fun register(
        email: String,
        password: String,
        displayName: String,
        city: String,
        isCoach: Boolean,
        hasPlayerProfile: Boolean,
        sports: List<Sport>
    ): AuthResult {
        val response = authApi.register(email, password, displayName, city, isCoach, hasPlayerProfile, sports.map { it.name })
        tokenStorage.currentUserId = response.user.id
        tokenStorage.isCoach = response.user.isCoach
        tokenStorage.hasPlayerProfile = response.user.hasPlayerProfile
        if (response.user.isCoach && !response.user.hasPlayerProfile) tokenStorage.coachModeActive = true
        authApi.clearBearerCache()
        tokenStorage.saveTokens(response.accessToken, response.refreshToken)
        return response.toDomain()
    }

    override suspend fun logout() {
        tokenStorage.clear()
        authApi.clearBearerCache()
    }
}
