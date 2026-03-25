package com.racketmatch.api.dto

import com.racketmatch.domain.entity.MatchEntity
import java.time.Instant
import java.util.UUID

data class MatchDto(
    val id: UUID,
    val challengerId: UUID,
    val challengedId: UUID,
    val type: String,
    val status: String,
    val sport: String,
    val scheduledAt: Instant?,
    val locationName: String?,
    val scoreChallenger: Int?,
    val scoreChallenged: Int?,
    val createdAt: Instant
)

data class CreateMatchRequest(
    val challengedId: UUID,
    val type: String,
    val sport: String = "TENNIS",
    val scheduledAt: Instant? = null,
    val locationName: String? = null
)

fun MatchEntity.toDto() = MatchDto(
    id = id!!,
    challengerId = challenger.id!!,
    challengedId = challenged.id!!,
    type = type,
    status = status,
    sport = sport,
    scheduledAt = scheduledAt,
    locationName = locationName,
    scoreChallenger = scoreChallenger,
    scoreChallenged = scoreChallenged,
    createdAt = createdAt
)
