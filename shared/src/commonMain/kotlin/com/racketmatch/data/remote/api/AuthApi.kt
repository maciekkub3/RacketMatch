package com.racketmatch.data.remote.api

import com.racketmatch.data.remote.dto.AuthResponseDto
import com.racketmatch.data.remote.dto.LoginRequestDto
import com.racketmatch.data.remote.dto.RegisterRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
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
        isCoach: Boolean
    ): AuthResponseDto =
        client.post("api/auth/register") {
            setBody(RegisterRequestDto(email, password, displayName, city, isCoach))
        }.body()
}
