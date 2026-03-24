package com.racketmatch.data.remote.dto

import com.racketmatch.domain.model.Match
import com.racketmatch.domain.model.MatchStatus
import com.racketmatch.domain.model.MatchType
import com.racketmatch.domain.model.Sport
import kotlinx.serialization.Serializable

@Serializable
data class MatchDto(
    val id: String,
    val challengerId: String,
    val challengedId: String,
    val type: String,
    val status: String,
    val sport: String,
    val scheduledAt: String? = null,
    val eloChanges: Map<String, Int>? = null
)

@Serializable
data class CreateMatchRequestDto(
    val challengedId: String,
    val type: String,
    val sport: String
)

@Serializable
data class SubmitResultRequestDto(
    val scoreChallenger: Int,
    val scoreChallenged: Int
)

fun MatchDto.toDomain() = Match(
    id = id,
    challengerId = challengerId,
    challengedId = challengedId,
    type = MatchType.valueOf(type),
    status = MatchStatus.valueOf(status),
    sport = Sport.valueOf(sport),
    scheduledAt = scheduledAt,
    eloChanges = eloChanges
)
