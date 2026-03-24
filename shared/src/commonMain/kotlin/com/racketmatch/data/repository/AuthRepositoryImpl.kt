package com.racketmatch.data.repository

import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.data.remote.api.AuthApi
import com.racketmatch.data.remote.dto.toDomain
import com.racketmatch.domain.model.AuthResult
import com.racketmatch.domain.repository.AuthRepository

class AuthRepositoryImpl(
    private val authApi: AuthApi,
    private val tokenStorage: TokenStorage
) : AuthRepository {

    override suspend fun login(email: String, password: String): AuthResult {
        val response = authApi.login(email, password)
        tokenStorage.saveTokens(response.accessToken, response.refreshToken)
        return response.toDomain()
    }

    override suspend fun register(
        email: String,
        password: String,
        displayName: String,
        city: String,
        isCoach: Boolean
    ): AuthResult {
        val response = authApi.register(email, password, displayName, city, isCoach)
        tokenStorage.saveTokens(response.accessToken, response.refreshToken)
        return response.toDomain()
    }

    override suspend fun logout() {
        tokenStorage.clear()
    }
}
