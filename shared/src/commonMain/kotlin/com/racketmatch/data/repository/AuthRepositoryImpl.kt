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
        println("RacketMatch login: saving token=${response.accessToken.take(20)}...")
        tokenStorage.saveTokens(response.accessToken, response.refreshToken)
        tokenStorage.currentUserId = response.user.id
        tokenStorage.isCoach = response.user.isCoach
        tokenStorage.hasPlayerProfile = response.user.hasPlayerProfile
        if (response.user.isCoach && !response.user.hasPlayerProfile) tokenStorage.coachModeActive = true
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
        println("RacketMatch register: saving token=${response.accessToken.take(20)}...")
        tokenStorage.saveTokens(response.accessToken, response.refreshToken)
        tokenStorage.currentUserId = response.user.id
        tokenStorage.isCoach = response.user.isCoach
        tokenStorage.hasPlayerProfile = response.user.hasPlayerProfile
        if (response.user.isCoach && !response.user.hasPlayerProfile) tokenStorage.coachModeActive = true
        return response.toDomain()
    }

    override suspend fun logout() {
        tokenStorage.clear()
    }
}
