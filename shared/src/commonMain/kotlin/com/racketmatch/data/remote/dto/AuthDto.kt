package com.racketmatch.data.remote.dto

import com.racketmatch.domain.model.AuthResult
import com.racketmatch.domain.model.Sport
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
    val subscriptionActive: Boolean = false,
    val sports: List<String> = emptyList(),
    val bio: String? = null,
    val dateOfBirth: String? = null,
    val wins: Int = 0,
    val losses: Int = 0,
    val hasPlayerProfile: Boolean = true
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
data class RefreshRequestDto(val refreshToken: String)

@Serializable
data class RegisterRequestDto(
    val email: String,
    val password: String,
    val displayName: String,
    val city: String,
    val isCoach: Boolean = false,
    val hasPlayerProfile: Boolean = true,
    val sports: List<String> = emptyList()
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
    subscriptionActive = subscriptionActive,
    sports = sports.mapNotNull { runCatching { Sport.valueOf(it.uppercase()) }.getOrNull() },
    bio = bio,
    dateOfBirth = dateOfBirth,
    wins = wins,
    losses = losses,
    hasPlayerProfile = hasPlayerProfile
)

fun AuthResponseDto.toDomain() = AuthResult(
    accessToken = accessToken,
    refreshToken = refreshToken,
    user = user.toDomain()
)
