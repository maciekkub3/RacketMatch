package com.racketmatch.config

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(private val jwtConfig: JwtConfig) {

    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtConfig.secret.toByteArray())
    }

    fun generateAccessToken(userId: UUID, email: String): String =
        buildToken(userId, email, jwtConfig.accessExpiry * 1000)

    fun generateRefreshToken(userId: UUID, email: String): String =
        buildToken(userId, email, jwtConfig.refreshExpiry * 1000)

    private fun buildToken(userId: UUID, email: String, expiryMs: Long): String =
        Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + expiryMs))
            .signWith(key)
            .compact()

    fun validateToken(token: String): Claims? = runCatching {
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
    }.getOrNull()

    fun extractUserId(token: String): UUID? =
        validateToken(token)?.subject?.let { UUID.fromString(it) }

    fun isTokenValid(token: String): Boolean = validateToken(token) != null
}
