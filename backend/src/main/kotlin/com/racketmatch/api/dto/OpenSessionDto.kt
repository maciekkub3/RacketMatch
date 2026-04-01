package com.racketmatch.api.dto

import com.racketmatch.domain.entity.OpenSessionEntity
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class OpenSessionDto(
    val id: UUID,
    val courtId: UUID,
    val courtName: String,
    val userId: UUID,
    val userName: String,
    val userElo: Int,
    val userAvatarUrl: String?,
    val startsAt: Instant,
    val sport: String,
    val status: String,
    val matchType: String
)

data class CreateSessionRequest(
    @field:NotBlank val courtId: String,
    @field:NotBlank val sport: String,
    val startsAt: Instant,
    val matchType: String = "CASUAL"
)

fun OpenSessionEntity.toDto() = OpenSessionDto(
    id = id!!,
    courtId = court.id!!,
    courtName = court.name,
    userId = user.id!!,
    userName = user.displayName,
    userElo = user.eloRating,
    userAvatarUrl = user.avatarUrl,
    startsAt = startsAt,
    sport = sport,
    status = status,
    matchType = matchType
)
