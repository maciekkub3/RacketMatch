package com.racketmatch.service

import com.racketmatch.api.dto.AuthResponse
import com.racketmatch.api.dto.toDto
import com.racketmatch.config.JwtConfig
import com.racketmatch.config.JwtService
import com.racketmatch.domain.entity.RefreshTokenEntity
import com.racketmatch.domain.entity.UserEntity
import com.racketmatch.domain.repository.RefreshTokenRepository
import com.racketmatch.domain.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtService: JwtService,
    private val jwtConfig: JwtConfig,
    private val passwordEncoder: PasswordEncoder
) {

    fun register(
        email: String,
        password: String,
        displayName: String,
        city: String,
        isCoach: Boolean
    ): AuthResponse {
        if (userRepository.existsByEmail(email)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Email already in use")
        }
        val user = userRepository.save(
            UserEntity(
                email = email,
                passwordHash = passwordEncoder.encode(password),
                displayName = displayName,
                city = city,
                isCoach = isCoach
            )
        )
        return buildAuthResponse(user)
    }

    fun login(email: String, password: String): AuthResponse {
        val user = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")
        if (!passwordEncoder.matches(password, user.passwordHash)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")
        }
        return buildAuthResponse(user)
    }

    fun refresh(token: String): AuthResponse {
        val stored = refreshTokenRepository.findByToken(token)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token")
        if (stored.expiresAt.isBefore(Instant.now())) {
            refreshTokenRepository.delete(stored)
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired")
        }
        refreshTokenRepository.delete(stored)
        return buildAuthResponse(stored.user)
    }

    fun logout(userId: UUID?) {
        refreshTokenRepository.deleteAllByUserId(userId)
    }

    private fun buildAuthResponse(user: UserEntity): AuthResponse {
        val accessToken = jwtService.generateAccessToken(user.id!!, user.email)
        val refreshToken = jwtService.generateRefreshToken(user.id!!, user.email)
        refreshTokenRepository.save(
            RefreshTokenEntity(
                user = user,
                token = refreshToken,
                expiresAt = Instant.now().plusSeconds(jwtConfig.refreshExpiry)
            )
        )
        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = user.toDto()
        )
    }
}
