package com.racketmatch.data.remote.dto

import com.racketmatch.domain.model.AuthResult
import com.racketmatch.domain.model.User
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val isCoach: Boolean = false,
    val city: String,
    val eloRating: Int = 1200,
    val isMaster: Boolean = false,
    val masterFee: Int? = null,
    val subscriptionActive: Boolean = false
)

@Serializable
data class AuthResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto
)

@Serializable
data class LoginRequestDto(val email: String, val password: String)

@Serializable
data class RegisterRequestDto(
    val email: String,
    val password: String,
    val displayName: String,
    val city: String,
    val isCoach: Boolean = false
)

fun UserDto.toDomain() = User(
    id = id,
    email = email,
    displayName = displayName,
    avatarUrl = avatarUrl,
    isCoach = isCoach,
    city = city,
    eloRating = eloRating,
    isMaster = isMaster,
    masterFee = masterFee,
    subscriptionActive = subscriptionActive
)

fun AuthResponseDto.toDomain() = AuthResult(
    accessToken = accessToken,
    refreshToken = refreshToken,
    user = user.toDomain()
)
