package com.racketmatch.data.remote.api

import com.racketmatch.data.remote.dto.AuthResponseDto
import com.racketmatch.data.remote.dto.LoginRequestDto
import com.racketmatch.data.remote.dto.RefreshRequestDto
import com.racketmatch.data.remote.dto.RegisterRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.plugin
import io.ktor.client.request.post
import io.ktor.client.request.setBody

class AuthApi(private val client: HttpClient) {

    suspend fun login(email: String, password: String): AuthResponseDto =
        client.post("api/auth/login") {
            setBody(LoginRequestDto(email, password))
        }.body()

    suspend fun register(
        email: String,
        password: String,
        displayName: String,
        city: String,
        isCoach: Boolean,
        hasPlayerProfile: Boolean = true,
        sports: List<String> = emptyList()
    ): AuthResponseDto =
        client.post("api/auth/register") {
            setBody(RegisterRequestDto(email, password, displayName, city, isCoach, hasPlayerProfile, sports))
        }.body()

    suspend fun refresh(refreshToken: String): AuthResponseDto =
        client.post("api/auth/refresh") {
            setBody(RefreshRequestDto(refreshToken))
        }.body()

    /**
     * Drops any cached bearer token inside Ktor's Auth plugin so the next
     * authenticated request will re-load tokens via `loadTokens { ... }`.
     * Call this after login/register so the new tokens are picked up.
     */
    fun clearBearerCache() {
        runCatching {
            client.plugin(Auth).providers
                .filterIsInstance<BearerAuthProvider>()
                .forEach { it.clearToken() }
        }
    }
}
