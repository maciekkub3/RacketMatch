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
    val challengerName: String = "",
    val challengerElo: Int = 1200,
    val challengedName: String = "",
    val challengedElo: Int = 1200,
    val type: String,
    val status: String,
    val sport: String,
    val scheduledAt: String? = null,
    val locationName: String? = null,
    val previousScheduledAt: String? = null,
    val previousLocationName: String? = null,
    val detailsProposedBy: String? = null,
    val reservedBy: String? = null,
    val eloChanges: Map<String, Int>? = null,
    val scoreChallenger: Int? = null,
    val scoreChallenged: Int? = null,
    val proposedScoreChallenger: Int? = null,
    val proposedScoreChallenged: Int? = null,
    val proposedBy: String? = null
)

@Serializable
data class CreateMatchRequestDto(
    val challengedId: String,
    val type: String,
    val sport: String,
    val locationName: String? = null,
    val scheduledAt: String? = null
)

@Serializable
data class ProposeDetailsRequestDto(
    val locationName: String? = null,
    val scheduledAt: String? = null
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
    challengerName = challengerName,
    challengerElo = challengerElo,
    challengedName = challengedName,
    challengedElo = challengedElo,
    type = MatchType.valueOf(type),
    status = runCatching { MatchStatus.valueOf(status) }.getOrDefault(MatchStatus.PENDING),
    sport = Sport.valueOf(sport),
    scheduledAt = scheduledAt,
    locationName = locationName,
    previousScheduledAt = previousScheduledAt,
    previousLocationName = previousLocationName,
    detailsProposedBy = detailsProposedBy,
    reservedBy = reservedBy,
    eloChanges = eloChanges,
    scoreChallenger = scoreChallenger,
    scoreChallenged = scoreChallenged,
    proposedScoreChallenger = proposedScoreChallenger,
    proposedScoreChallenged = proposedScoreChallenged,
    proposedBy = proposedBy
)
