package com.racketmatch.api.dto

import com.racketmatch.domain.entity.MatchEntity
import java.time.Instant
import java.util.UUID

data class MatchDto(
    val id: UUID,
    val challengerId: UUID,
    val challengedId: UUID,
    val challengerName: String,
    val challengerElo: Int,
    val challengedName: String,
    val challengedElo: Int,
    val type: String,
    val status: String,
    val sport: String,
    val scheduledAt: Instant?,
    val locationName: String?,
    val detailsProposedBy: String?,
    val reservedBy: String?,
    val scoreChallenger: Int?,
    val scoreChallenged: Int?,
    val proposedScoreChallenger: Int?,
    val proposedScoreChallenged: Int?,
    val proposedBy: String?,
    val eloChanges: Map<String, Int>?,
    val createdAt: Instant
)

data class CreateMatchRequest(
    val challengedId: UUID,
    val type: String,
    val sport: String = "TENNIS",
    val scheduledAt: Instant? = null,
    val locationName: String? = null
)

data class ProposeDetailsRequest(
    val locationName: String? = null,
    val scheduledAt: java.time.Instant? = null
)

data class SubmitResultRequest(
    val scoreChallenger: Int,
    val scoreChallenged: Int
)

fun MatchEntity.toDto() = MatchDto(
    id = id!!,
    challengerId = challenger.id!!,
    challengedId = challenged.id!!,
    challengerName = challenger.displayName,
    challengerElo = challenger.eloRating,
    challengedName = challenged.displayName,
    challengedElo = challenged.eloRating,
    type = type,
    status = status,
    sport = sport,
    scheduledAt = scheduledAt,
    locationName = locationName,
    detailsProposedBy = detailsProposedBy?.toString(),
    reservedBy = reservedBy?.toString(),
    scoreChallenger = scoreChallenger,
    scoreChallenged = scoreChallenged,
    proposedScoreChallenger = proposedScoreChallenger,
    proposedScoreChallenged = proposedScoreChallenged,
    proposedBy = proposedBy?.toString(),
    eloChanges = buildEloChanges(),
    createdAt = createdAt
)

private fun MatchEntity.buildEloChanges(): Map<String, Int>? {
    if (eloChangeChallenger == null && eloChangeChallenged == null) return null
    val map = mutableMapOf<String, Int>()
    eloChangeChallenger?.let { map[challenger.id!!.toString()] = it }
    eloChangeChallenged?.let { map[challenged.id!!.toString()] = it }
    return map
}
