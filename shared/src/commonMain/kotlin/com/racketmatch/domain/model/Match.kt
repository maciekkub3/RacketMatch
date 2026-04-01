package com.racketmatch.domain.model

enum class MatchType { CASUAL, RANKED, MASTER }
enum class MatchStatus { PENDING, SCHEDULED, RESULT_PROPOSED, COMPLETED, CANCELLED }

data class Match(
    val id: String,
    val challengerId: String,
    val challengedId: String,
    val challengerName: String = "",
    val challengerElo: Int = 1200,
    val challengedName: String = "",
    val challengedElo: Int = 1200,
    val type: MatchType,
    val status: MatchStatus,
    val sport: Sport,
    val scheduledAt: String? = null,
    val locationName: String? = null,
    val detailsProposedBy: String? = null,
    val reservedBy: String? = null,
    val eloChanges: Map<String, Int>? = null,
    val scoreChallenger: Int? = null,
    val scoreChallenged: Int? = null,
    val proposedScoreChallenger: Int? = null,
    val proposedScoreChallenged: Int? = null,
    val proposedBy: String? = null
)
