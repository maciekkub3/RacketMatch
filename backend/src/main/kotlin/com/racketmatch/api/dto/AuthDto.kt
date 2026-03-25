package com.racketmatch.api.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:Email val email: String,
    @field:Size(min = 8) val password: String,
    @field:NotBlank val displayName: String,
    @field:NotBlank val city: String,
    val isCoach: Boolean = false
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
