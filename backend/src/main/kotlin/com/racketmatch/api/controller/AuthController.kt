package com.racketmatch.api.controller

import com.racketmatch.api.dto.AuthResponse
import com.racketmatch.api.dto.LoginRequest
import com.racketmatch.api.dto.RefreshRequest
import com.racketmatch.api.dto.RegisterRequest
import com.racketmatch.config.JwtService
import com.racketmatch.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val jwtService: JwtService
) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: RegisterRequest): AuthResponse =
        authService.register(
            email = request.email,
            password = request.password,
            displayName = request.displayName,
            city = request.city,
            isCoach = request.isCoach,
            hasPlayerProfile = request.hasPlayerProfile,
            sports = request.sports
        )

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): AuthResponse =
        authService.login(request.email, request.password)

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): AuthResponse =
        authService.refresh(request.refreshToken)

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(authentication: Authentication) {
        val userId = UUID.fromString(authentication.name)
        authService.logout(userId)
    }
}
