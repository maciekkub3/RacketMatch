package com.racketmatch.api.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:Email val email: String,
    @field:Size(min = 8) val password: String,
    @field:NotBlank val displayName: String,
    @field:NotBlank val city: String,
    val isCoach: Boolean = false,
    val hasPlayerProfile: Boolean = true,
    val sports: List<String> = emptyList()
)

data class LoginRequest(
    @field:Email val email: String,
    @field:NotBlank val password: String
)

data class RefreshRequest(
    @field:NotBlank val refreshToken: String
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto
)

data class UpdateProfileRequest(
    @field:NotBlank val displayName: String,
    @field:NotBlank val city: String,
    val bio: String? = null,
    val sports: List<String> = emptyList(),
    val password: String? = null,
    val dateOfBirth: String? = null,
    val activateCoach: Boolean? = null,
    val activatePlayerProfile: Boolean? = null
)
