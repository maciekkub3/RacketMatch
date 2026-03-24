package com.racketmatch.domain.repository

import com.racketmatch.domain.model.AuthResult

interface AuthRepository {
    suspend fun login(email: String, password: String): AuthResult
    suspend fun register(email: String, password: String, displayName: String, city: String, isCoach: Boolean): AuthResult
    suspend fun logout()
}
