package com.racketmatch.domain.repository

import com.racketmatch.domain.model.AuthResult

interface AuthRepository {
    suspend fun login(email: String, password: String): AuthResult
    suspend fun register(email: String, password: String, displayName: String, city: String, isCoach: Boolean, hasPlayerProfile: Boolean = true, sports: List<com.racketmatch.domain.model.Sport> = emptyList(), ageConfirmed: Boolean = false): AuthResult
    suspend fun logout()
}
